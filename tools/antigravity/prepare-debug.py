#!/usr/bin/env python3
"""Prepare an offline Firebase placeholder for the standalone debug APK.

This is deliberately not a real Firebase project or a Gemini API key. Debug
manifest flags disable Analytics and Crashlytics collection. A syntactically
valid configuration keeps upstream's Firebase initialization working.
"""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
config = {
    "project_info": {
        "project_number": "123456789012",
        "project_id": "rikkahub-antigravity-local",
        "storage_bucket": "rikkahub-antigravity-local.invalid",
    },
    "client": [{
        "client_info": {
            "mobilesdk_app_id": "1:123456789012:android:0123456789abcdef012345",
            "android_client_info": {"package_name": "me.rerere.rikkahub.debug"},
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
print("Prepared debug-only Firebase placeholder; collection is disabled.")
