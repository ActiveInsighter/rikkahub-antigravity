#!/usr/bin/env python3
"""Prepare an offline Firebase placeholder for the standalone debug APK.

This is deliberately not a real Firebase project or a Gemini API key. Debug
manifest flags disable Analytics and Crashlytics collection. A syntactically
valid configuration keeps upstream's Firebase initialization working.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

BASE_DEBUG_PACKAGE = "me.rerere.rikkahub.debug"
root = Path(__file__).resolve().parents[2]


def resolve_branch_name() -> str:
    for env_key in ("RIKKA_BRANCH_NAME", "GITHUB_REF_NAME"):
        val = os.environ.get(env_key, "").strip()
        if val:
            return val
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except OSError:
        pass
    return ""


def is_main_branch(branch_name: str) -> bool:
    return not branch_name or branch_name in ("HEAD", "main")


def branch_package_segment(branch_name: str) -> str:
    hash6 = hashlib.sha256(branch_name.encode("utf-8")).hexdigest()[:6]
    raw_slug = re.sub(r"[^a-z0-9]+", "_", branch_name.lower()).strip("_")[:24].rstrip("_")
    if not raw_slug:
        raw_slug = "branch"
    return f"b_{raw_slug}_{hash6}"


def resolve_debug_package(branch_name: str) -> str:
    if is_main_branch(branch_name):
        return BASE_DEBUG_PACKAGE
    return f"{BASE_DEBUG_PACKAGE}.{branch_package_segment(branch_name)}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare debug Firebase placeholder")
    parser.add_argument("--print-package-name", action="store_true", help="Print resolved debug package name and exit")
    parser.add_argument("--print-branch-slug", action="store_true", help="Print branch package segment (empty on main) and exit")
    args = parser.parse_args()

    branch_name = resolve_branch_name()
    package_name = resolve_debug_package(branch_name)

    if args.print_package_name:
        print(package_name)
        return
    if args.print_branch_slug:
        print("" if is_main_branch(branch_name) else branch_package_segment(branch_name))
        return

    config = {
        "project_info": {
            "project_number": "123456789012",
            "project_id": "rikkahub-antigravity-local",
            "storage_bucket": "rikkahub-antigravity-local.invalid",
        },
        "client": [{
            "client_info": {
                "mobilesdk_app_id": "1:123456789012:android:0123456789abcdef012345",
                "android_client_info": {"package_name": package_name},
            },
            "oauth_client": [],
            "api_key": [{"current_key": "AIzaSy000000000000000000000000000000000"}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }],
        "configuration_version": "1",
    }
    target = root / "app/google-services.json"
    if target.exists():
        raise SystemExit(f"Refusing to overwrite existing {target}")
    target.write_text(json.dumps(config, indent=2) + "\n")
    print(f"Prepared debug-only Firebase placeholder for {package_name}; collection is disabled.")


if __name__ == "__main__":
    main()
