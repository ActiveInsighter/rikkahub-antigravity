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
import subprocess
import tempfile

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
        payload = json_bytes({"schemaVersion": 1, "packageName": PACKAGE_NAME, "builds": builds})
        envelope = sign(payload)
        if len(envelope) > CATALOG_LIMIT:
            raise ValueError("Update catalog exceeds the size limit")
        condition = {"IfMatch": etag} if etag else {"IfNoneMatch": "*"}
        self.client.put_object(Bucket=self.bucket, Key=PREFIX + "catalog.json", Body=envelope,
                               ContentType="application/json", CacheControl="no-store", **condition)

    def prune(self, oldest_version):
        prefix = PREFIX + "builds/"
        old_keys = []
        for page in self.client.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=prefix):
            for item in page.get("Contents", []):
                version, separator, _ = item["Key"][len(prefix):].partition("/")
                if separator and re.fullmatch("[0-9]{1,10}", version) and int(version) < oldest_version:
                    old_keys.append({"Key": item["Key"]})
        for start in range(0, len(old_keys), 1000):
            response = self.client.delete_objects(
                Bucket=self.bucket, Delete={"Objects": old_keys[start:start + 1000], "Quiet": True},
            )
            if response.get("Errors"):
                raise RuntimeError(f"R2 retention failed: {response['Errors']}")
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
        if len(old_builds) == KEEP_BUILDS:
            store.prune(old_builds[-1]["versionCode"])
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
    if len(retained) == KEEP_BUILDS:
        store.prune(retained[-1]["versionCode"])
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
