#!/usr/bin/env python3
"""Publish verified Antigravity APKs and direct deltas to a versioned R2 catalog."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
from shutil import copyfileobj
import subprocess
import tempfile
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen

PACKAGE_NAME = "me.rerere.rikkahub.debug"
ABIS = ("arm64-v8a", "x86_64", "universal")
PREFIX = "antigravity-updates/"
KEEP_BUILDS = 10
MAX_APK_SIZE = 512 * 1024 * 1024
CATALOG_LIMIT = 1024 * 1024
SIGNATURE_TOOL = Path(__file__).with_name("UpdateCatalogSignature.java")
APK = "application/vnd.android.package-archive"
HDIFF = "application/octet-stream"


def digest(path):
    with path.open("rb") as data:
        return hashlib.file_digest(data, "sha256").hexdigest()


def json_bytes(value):
    return json.dumps(value, separators=(",", ":"), ensure_ascii=True).encode()


def sign(payload):
    result = subprocess.run(["java", str(SIGNATURE_TOOL), "sign"], input=payload, stdout=subprocess.PIPE, check=True)
    return json_bytes({
        "payload": base64.b64encode(payload).decode(),
        "signature": base64.b64encode(result.stdout).decode(),
    })


def read_signed_catalog(envelope):
    document = json.loads(envelope)
    payload = base64.b64decode(document["payload"], validate=True)
    subprocess.run(["java", str(SIGNATURE_TOOL), "verify", document["signature"]], input=payload, check=True)
    catalog = json.loads(payload)
    old_builds = catalog["builds"]
    if (catalog["schemaVersion"] != 1 or catalog["packageName"] != PACKAGE_NAME or len(old_builds) > KEEP_BUILDS):
        raise ValueError("Unexpected update catalog")
    codes = [build["versionCode"] for build in old_builds]
    if codes != sorted(set(codes), reverse=True):
        raise ValueError("Update catalog versions must be unique and descending")
    return old_builds


def apk_key(version, apk):
    return f"builds/{version}/{apk['sha256']}/{apk['abi']}.apk"


def delta_key(version, apk, patch):
    return f"builds/{version}/{apk['sha256']}/{apk['abi']}-from-{patch['baseSha256']}-{patch['sha256']}.hdiff"


def catalog_envelope(builds):
    payload = json_bytes({"schemaVersion": 1, "packageName": PACKAGE_NAME, "builds": builds})
    envelope = sign(payload)
    if len(envelope) > CATALOG_LIMIT:
        raise ValueError("Update catalog exceeds the size limit")
    return envelope


def obsolete_keys(builds, keys):
    newest_version = builds[0]["versionCode"]
    retained = set()
    for build in builds:
        version = build["versionCode"]
        for apk in build["apks"]:
            retained.add(PREFIX + apk_key(version, apk))
            for patch in apk["patches"]:
                retained.add(PREFIX + delta_key(version, apk, patch))
    for key in keys:
        if key in retained:
            continue
        match = re.fullmatch(
            PREFIX + r"builds/([0-9]{1,10})/[a-f0-9]{64}/(?:arm64-v8a|x86_64|universal)"
            r"(?:\.apk|-from-[a-f0-9]{64}-[a-f0-9]{64}\.hdiff)", key,
        )
        # Leave objects from a newer publisher alone, even if it is still uploading.
        if match and int(match[1]) <= newest_version:
            yield key


def generate_patch(tools, base, target, patch, rebuilt, *, min_savings=10):
    subprocess.run(
        [str(tools / "hdiffz"), "-s-64", "-p-1", "-c-zlib-6", "-d", str(base), str(target), str(patch)],
        check=True, stdout=subprocess.DEVNULL, timeout=300,
    )
    if min_savings is not None and patch.stat().st_size * 100 > target.stat().st_size * (100 - min_savings):
        patch.unlink()
        return False
    subprocess.run([str(tools / "hpatchz"), str(base), str(patch), str(rebuilt)],
                   check=True, stdout=subprocess.DEVNULL, timeout=300)
    if digest(rebuilt) != digest(target):
        raise ValueError(f"Delta round-trip failed for {target.name}")
    rebuilt.unlink()
    return True


class R2Store:
    def __init__(self, client, bucket):
        self.client = client
        self.bucket = bucket

    def read_catalog(self):
        from botocore.exceptions import ClientError
        try:
            item = self.client.get_object(Bucket=self.bucket, Key=PREFIX + "catalog.json")
        except ClientError as error:
            if error.response["Error"]["Code"] in ("NoSuchKey", "404"):
                return [], None
            raise
        with item["Body"] as body:
            envelope = body.read(CATALOG_LIMIT + 1)
        if len(envelope) > CATALOG_LIMIT:
            raise ValueError("Existing update catalog exceeds the size limit")
        return read_signed_catalog(envelope), item["ETag"]

    def get_file(self, key, destination):
        self.client.download_file(self.bucket, PREFIX + key, str(destination))

    def put_file(self, key, path, content_type):
        self.client.upload_file(str(path), self.bucket, PREFIX + key, ExtraArgs={
            "ContentType": content_type,
            "CacheControl": "public, max-age=3600, immutable",
        })

    def write_catalog(self, builds, etag):
        condition = {"IfMatch": etag} if etag else {"IfNoneMatch": "*"}
        self.client.put_object(Bucket=self.bucket, Key=PREFIX + "catalog.json", Body=catalog_envelope(builds),
                               ContentType="application/json", CacheControl="no-store", **condition)

    def prune(self, builds):
        pages = self.client.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=PREFIX + "builds/")
        keys = (item["Key"] for page in pages for item in page.get("Contents", []))
        old_keys = [{"Key": key} for key in obsolete_keys(builds, keys)]
        for start in range(0, len(old_keys), 1000):
            response = self.client.delete_objects(
                Bucket=self.bucket, Delete={"Objects": old_keys[start:start + 1000], "Quiet": True},
            )
            if response.get("Errors"):
                raise RuntimeError(f"R2 retention failed: {response['Errors']}")
        print(f"Pruned {len(old_keys)} obsolete objects")


class WorkerR2Store:
    def __init__(self, endpoint, token):
        url = urlsplit(endpoint)
        if not (url.scheme == "https" and url.hostname and not url.username
                and not url.password and not url.query and not url.fragment):
            raise ValueError("Invalid R2 publisher URL")
        self.endpoint = endpoint.rstrip("/")
        self.token = token

    def request(self, method, path, *, data=None, headers=None):
        request = Request(f"{self.endpoint}/{path}", method=method, data=data, headers=headers or {})
        request.add_unredirected_header("Authorization", f"Bearer {self.token}")
        request.add_header("User-Agent", "RikkaHub-APK-Publisher/1.0")
        return urlopen(request, timeout=90)

    def read_catalog(self):
        try:
            with self.request("GET", "catalog.json") as response:
                envelope = response.read(CATALOG_LIMIT + 1)
                etag = response.headers["ETag"]
        except HTTPError as error:
            if error.code == 404:
                return [], None
            raise
        if not etag or len(envelope) > CATALOG_LIMIT:
            raise ValueError("Invalid existing update catalog headers or size")
        return read_signed_catalog(envelope), etag

    def get_file(self, key, destination):
        with self.request("GET", key) as response, destination.open("wb") as output:
            copyfileobj(response, output)

    def put_file(self, key, path, content_type):
        with path.open("rb") as source:
            with self.request("PUT", key, data=source, headers={
                "Content-Type": content_type,
                "Content-Length": str(path.stat().st_size),
            }):
                pass

    def write_catalog(self, builds, etag):
        headers = {"If-Match": etag} if etag else {"If-None-Match": "*"}
        with self.request("PUT", "catalog.json", data=catalog_envelope(builds), headers=headers):
            pass

    def list_keys(self):
        path = "builds/"
        while True:
            with self.request("GET", path) as response:
                page = json.load(response)
            yield from page["keys"]
            if not page["cursor"]:
                return
            path = "builds/?" + urlencode({"cursor": page["cursor"]})

    def prune(self, builds):
        old_keys = list(obsolete_keys(builds, self.list_keys()))
        for key in old_keys:
            with self.request("DELETE", key.removeprefix(PREFIX)):
                pass
        print(f"Pruned {len(old_keys)} obsolete objects")


def prepare_asset(store, tools, workspace, options, abi, old_builds):
    (path,) = options.apk_dir.glob(f"*-{abi}-debug.apk")
    size = path.stat().st_size
    if not 0 < size <= MAX_APK_SIZE:
        raise ValueError(f"Unsupported APK size for {path}")
    apk = {"abi": abi, "size": size, "sha256": digest(path), "patches": []}
    store.put_file(apk_key(options.version_code, apk), path, APK)
    base = workspace / "base.apk"
    patch = workspace / "delta.hdiff"
    rebuilt = workspace / "rebuilt.apk"
    seen_bases = set()
    for old_build in old_builds:
        (base_apk,) = (item for item in old_build["apks"] if item["abi"] == abi)
        if base_apk["sha256"] in seen_bases:
            continue
        seen_bases.add(base_apk["sha256"])
        store.get_file(apk_key(old_build["versionCode"], base_apk), base)
        if base.stat().st_size != base_apk["size"] or digest(base) != base_apk["sha256"]:
            raise ValueError(f"Archived APK differs from its signed catalog entry: {old_build['versionCode']}/{abi}")
        if generate_patch(tools, base, path, patch, rebuilt):
            delta = {
                "baseVersionCode": old_build["versionCode"], "baseSha256": base_apk["sha256"],
                "size": patch.stat().st_size, "sha256": digest(patch),
            }
            store.put_file(delta_key(options.version_code, apk, delta), patch, HDIFF)
            apk["patches"].append(delta)
            print(f"{abi} {old_build['versionCode']} → {options.version_code}: "
                  f"{delta['size'] / size:.1%} of full APK ({delta['size']:,} bytes)")
            patch.unlink()
        base.unlink()
    return apk


def publish(store, options):
    old_builds, etag = store.read_catalog()
    if old_builds and options.version_code <= old_builds[0]["versionCode"]:
        print(f"Build {options.version_code} is already published or has been superseded")
        store.prune(old_builds)
        return
    with tempfile.TemporaryDirectory(dir=options.work_dir) as temp:
        workspace = Path(temp)
        abi_dirs = [workspace / abi for abi in ABIS]
        for directory in abi_dirs:
            directory.mkdir()
        with ThreadPoolExecutor(max_workers=3) as executor:
            jobs = [executor.submit(prepare_asset, store, options.hdiff_tools, directory, options, abi, old_builds)
                    for abi, directory in zip(ABIS, abi_dirs, strict=True)]
            apks = [job.result() for job in jobs]

    build = {
        "versionCode": options.version_code,
        "versionName": options.version_name,
        "buildNumber": options.build_number,
        "publishedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "commit": options.commit,
        "apks": apks,
    }
    retained = [build, *old_builds][:KEEP_BUILDS]
    store.write_catalog(retained, etag)
    store.prune(retained)
    print(f"Published build {options.build_number}, {len(retained)} builds indexed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument("--hdiff-tools", required=True, type=Path)
    parser.add_argument("--work-dir", type=Path)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--build-number", required=True, type=int)
    parser.add_argument("--commit", required=True)
    options = parser.parse_args()
    if not (100_001 <= options.version_code <= 2_100_000_000 and options.build_number > 0
            and 1 <= len(options.version_name) <= 80 and re.fullmatch("[0-9a-f]{40}", options.commit)):
        parser.error("Invalid catalog build metadata")

    if os.environ.get("R2_UPDATE_PUBLISH_URL"):
        publish(WorkerR2Store(os.environ["R2_UPDATE_PUBLISH_URL"], os.environ["R2_UPDATE_PUBLISH_TOKEN"]), options)
        return

    import boto3
    from botocore.config import Config
    account = os.environ["R2_UPDATE_ACCOUNT_ID"]
    if not re.fullmatch("[0-9a-f]{32}", account):
        parser.error("Invalid R2 account ID")
    client = boto3.client(
        "s3", endpoint_url=f"https://{account}.r2.cloudflarestorage.com", region_name="auto",
        aws_access_key_id=os.environ["R2_UPDATE_ACCESS_KEY_ID"],
        aws_secret_access_key=os.environ["R2_UPDATE_SECRET_ACCESS_KEY"],
        config=Config(request_checksum_calculation="when_required", response_checksum_validation="when_required"),
    )
    publish(R2Store(client, os.environ["R2_UPDATE_BUCKET"]), options)


if __name__ == "__main__":
    main()
