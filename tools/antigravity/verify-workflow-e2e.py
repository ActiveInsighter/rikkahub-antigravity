#!/usr/bin/env python3
"""
RikkaHub AnyWorkflow E2E Visual & API Verification Script.

Supports:
  --mode api      : Verify PocketBase + Cloudflare Worker APIs with a test account
  --mode android  : Install APK on connected adb device/emulator, capture screenshots & logcat
  --mode all      : Run both API and Android visual verification

Environment variables (never printed or logged):
  WORKFLOW_PB_URL          PocketBase base URL (default: https://pb.any1.tech)
  WORKFLOW_REMOTE_API_URL  Remote Worker URL (default: https://anyworkflow-remote.2212148739lbw.workers.dev)
  WORKFLOW_TEST_IDENTITY   Test account email / username
  WORKFLOW_TEST_PASSWORD   Test account password
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

DEFAULT_PB_URL = "https://pb.any1.tech"
DEFAULT_REMOTE_URL = "https://anyworkflow-remote.2212148739lbw.workers.dev"
PACKAGE_ID = "me.rerere.rikkahub.debug"
MAIN_ACTIVITY = "me.rerere.rikkahub.RouteActivity"


def redact(text: str, secrets: list[str]) -> str:
    out = text
    for s in secrets:
        if s and len(s) >= 4:
            out = out.replace(s, "***REDACTED***")
    out = re.sub(r"(Bearer\s+)[A-Za-z0-9._-]+", r"\1***REDACTED***", out, flags=re.IGNORECASE)
    return out


def http_json(
    url: str,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: bytes | None = None,
    timeout: int = 20,
) -> tuple[int, dict[str, Any] | list[Any] | str]:
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("User-Agent", "RikkaHub-E2E-Verifier/1.0")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def run_api_verification(
    pb_url: str,
    remote_url: str,
    identity: str,
    password: str,
    write_test: bool,
    out_dir: Path,
) -> dict[str, Any]:
    out_dir.mkdir(parents=True, exist_ok=True)
    log_lines: list[str] = []
    results: list[dict[str, Any]] = []
    secrets = [password]

    def record(name: str, ok: bool, status: int, detail: str = "") -> None:
        safe_detail = redact(detail, secrets)
        results.append({"name": name, "ok": ok, "status": status, "detail": safe_detail})
        marker = "PASS" if ok else "FAIL"
        line = f"[{marker}] {name} (HTTP {status}) {safe_detail}".strip()
        log_lines.append(line)
        print(line)

    # 1. PocketBase health check
    status, payload = http_json(f"{pb_url}/api/health")
    record("pb.health", status == 200, status)

    # 2. Auth with password (if credentials provided)
    token = ""
    user_id = ""
    if identity and password:
        auth_body = json.dumps({"identity": identity, "password": password}).encode("utf-8")
        status, payload = http_json(
            f"{pb_url}/api/collections/aw_clients/auth-with-password",
            method="POST",
            headers={"Content-Type": "application/json"},
            body=auth_body,
        )
        if status == 200 and isinstance(payload, dict) and payload.get("token"):
            token = str(payload["token"])
            secrets.append(token)
            user_id = str((payload.get("record") or {}).get("id", ""))
            record("pb.auth", True, status, f"userId={user_id}")
        else:
            record("pb.auth", False, status, str(payload)[:200])
    else:
        record("pb.auth", False, 0, "Skipped: WORKFLOW_TEST_IDENTITY / WORKFLOW_TEST_PASSWORD not set")

    if token:
        pb_headers = {"Authorization": token}
        remote_headers = {
            "Authorization": f"Bearer {token}",
            "X-PocketBase-Url": pb_url,
        }

        pb_endpoints = [
            ("pb.runs", f"/api/collections/aw_dispatch_runs/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.schedules", f"/api/collections/aw_run_schedules/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.library_folders", f"/api/collections/aw_library_folders/records?page=1&perPage=20&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.favorites", f"/api/collections/aw_run_favorites/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.templates", f"/api/collections/aw_templates/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.converter_configs", f"/api/collections/aw_file_conversion_configs/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.converter_jobs", f"/api/collections/aw_pdf_to_md_jobs/records?page=1&perPage=5&filter={urllib.parse.quote(f'owner=\"{user_id}\"')}"),
            ("pb.saved_models", "/api/anyworkflow/models/saved"),
            ("pb.start_links", "/api/anyworkflow/start-links"),
            ("pb.runtime_defaults", "/api/anyworkflow/runtime-defaults"),
        ]
        for name, path in pb_endpoints:
            st, pl = http_json(f"{pb_url}{path}", headers=pb_headers)
            detail = ""
            if isinstance(pl, dict) and "items" in pl and isinstance(pl["items"], list):
                detail = f"items={len(pl['items'])}"
            elif isinstance(pl, list):
                detail = f"items={len(pl)}"
            record(name, 200 <= st < 300, st, detail if 200 <= st < 300 else str(pl)[:180])

        # Remote Worker endpoints
        st, pl = http_json(f"{remote_url}/api/library/files/import", method="POST", headers=remote_headers)
        record("remote.library_import", st == 200, st, str(pl)[:180])

        st, pl = http_json(f"{remote_url}/api/library/folders", headers=remote_headers)
        detail = f"folders={len(pl.get('items', []))}" if isinstance(pl, dict) else str(pl)[:180]
        record("remote.library_folders", st == 200, st, detail)

        st, pl = http_json(f"{remote_url}/api/library/files?limit=100", headers=remote_headers)
        detail = f"files={len(pl.get('items', []))}" if isinstance(pl, dict) else str(pl)[:180]
        record("remote.library_files", st == 200, st, detail)

        st, pl = http_json(f"{remote_url}/api/file-conversion/dispatcher", headers=remote_headers)
        record("remote.converter_dispatcher", st == 200, st, str(pl)[:180])

        if write_test:
            md_name = f"verify-{int(time.time())}.md"
            md_body = b"# AnyWorkflow E2E Verify\n\nTemporary verification file.\n"
            st, pl = http_json(
                f"{remote_url}/api/library/files/markdown",
                method="PUT",
                headers={
                    **remote_headers,
                    "Content-Type": "text/plain; charset=utf-8",
                    "X-File-Name": urllib.parse.quote(md_name),
                },
                body=md_body,
            )
            uploaded_id = pl.get("id", "") if isinstance(pl, dict) else ""
            record("remote.library_upload_md", st in (200, 201) and bool(uploaded_id), st, f"id={uploaded_id}")
            if uploaded_id:
                st_r, pl_r = http_json(f"{remote_url}/api/library/files/{uploaded_id}/text", headers=remote_headers)
                ok_r = st_r == 200 and isinstance(pl_r, dict) and "Temporary verification file" in str(pl_r.get("content", ""))
                record("remote.library_read_md", ok_r, st_r)

                st_d, _ = http_json(
                    f"{remote_url}/api/library/files/{uploaded_id}",
                    method="DELETE",
                    headers=remote_headers,
                )
                record("remote.library_delete_md", st_d in (200, 204), st_d)

    (out_dir / "api-verify.log").write_text("\n".join(log_lines) + "\n", encoding="utf-8")
    summary = {
        "pbUrl": pb_url,
        "remoteUrl": remote_url,
        "passed": sum(1 for r in results if r["ok"]),
        "total": len(results),
        "results": results,
    }
    (out_dir / "verify-report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def adb(args: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    res = subprocess.run(["adb", *args], text=True, capture_output=True)
    if check and res.returncode != 0:
        print(f"[ADB ERROR] cmd=adb {' '.join(args)} rc={res.returncode}")
        if res.stdout:
            print(f"[ADB STDOUT]\n{res.stdout.strip()}")
        if res.stderr:
            print(f"[ADB STDERR]\n{res.stderr.strip()}")
        raise subprocess.CalledProcessError(res.returncode, ["adb", *args], res.stdout, res.stderr)
    return res


def adb_input_text(value: str) -> None:
    """Type ASCII text via adb shell input text with safe shell escaping."""
    escaped = re.sub(r"([\\'\";|&<>()$`*?!#~ \t])", r"\\\1", value)
    adb(["shell", "input", "text", escaped])


def dump_ui_and_screenshot(out_dir: Path, name: str) -> ET.Element | None:
    screenshots_dir = out_dir / "screenshots"
    ui_dir = out_dir / "ui-dumps"
    screenshots_dir.mkdir(parents=True, exist_ok=True)
    ui_dir.mkdir(parents=True, exist_ok=True)

    png_bytes = subprocess.check_output(["adb", "exec-out", "screencap", "-p"])
    (screenshots_dir / f"{name}.png").write_bytes(png_bytes)

    adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"], check=False)
    xml_res = adb(["shell", "cat", "/sdcard/window_dump.xml"], check=False)
    if xml_res.returncode == 0 and "<hierarchy" in xml_res.stdout:
        (ui_dir / f"{name}.xml").write_text(xml_res.stdout, encoding="utf-8")
        try:
            return ET.fromstring(xml_res.stdout)
        except ET.ParseError:
            return None
    return None


def parse_bounds(bounds: str) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return x1, y1, x2, y2


def find_node_center(root: ET.Element | None, text_match: str) -> tuple[int, int] | None:
    if root is None:
        return None
    for node in root.iter("node"):
        text = (node.attrib.get("text") or "").strip()
        desc = (node.attrib.get("content-desc") or "").strip()
        if text_match in (text, desc) or text_match in text or text_match in desc:
            b = parse_bounds(node.attrib.get("bounds", ""))
            if b:
                x1, y1, x2, y2 = b
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_edit_fields(root: ET.Element | None) -> list[tuple[str, tuple[int, int]]]:
    fields: list[tuple[str, tuple[int, int]]] = []
    if root is None:
        return fields
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        if "EditText" in cls or node.attrib.get("password") == "true":
            b = parse_bounds(node.attrib.get("bounds", ""))
            if b:
                x1, y1, x2, y2 = b
                hint = (node.attrib.get("text") or node.attrib.get("hint") or "").strip()
                fields.append((hint, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return fields


def tap_label(
    out_dir: Path,
    label: str,
    capture_name: str,
    wait_sec: float = 2.0,
    root: ET.Element | None = None,
) -> ET.Element | None:
    if root is None:
        root = dump_ui_and_screenshot(out_dir, f"{capture_name}_before")
    pos = find_node_center(root, label)
    if not pos:
        print(f"[WARN] UI node '{label}' not found")
        return None
    adb(["shell", "input", "tap", str(pos[0]), str(pos[1])])
    time.sleep(wait_sec)
    return dump_ui_and_screenshot(out_dir, capture_name)


def run_android_verification(
    apk_path: str | None,
    out_dir: Path,
    pb_url: str,
    identity: str,
    password: str,
) -> dict[str, Any]:
    devices = adb(["devices"]).stdout
    if "\tdevice" not in devices:
        raise RuntimeError(f"No connected adb device/emulator found:\n{devices}")

    adb(["logcat", "-c"], check=False)
    for _ in range(15):
        pm_check = adb(["shell", "pm", "path", "android"], check=False)
        if pm_check.returncode == 0 and "package:" in pm_check.stdout:
            break
        time.sleep(2)
    adb(["shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0"], check=False)
    if apk_path:
        print(f"Installing APK: {apk_path}")
        adb(["install", "-r", "-d", apk_path])

    adb(["shell", "am", "start", "-W", "-n", f"{PACKAGE_ID}/{MAIN_ACTIVITY}"])
    time.sleep(4)
    home_root = dump_ui_and_screenshot(out_dir, "01_chat_home")

    # Open navigation drawer via "Messages" button or left-edge swipe
    drawer_root = tap_label(out_dir, "Messages", "02_chat_drawer", wait_sec=2.0, root=home_root)
    if drawer_root is None or find_node_center(drawer_root, "工作流") is None:
        adb(["shell", "input", "swipe", "20", "800", "750", "800", "250"])
        time.sleep(2.0)
        drawer_root = dump_ui_and_screenshot(out_dir, "02_chat_drawer")

    # Navigate to Workflow page
    workflow_root = tap_label(out_dir, "工作流", "03_workflow_entry", wait_sec=2.5, root=drawer_root)
    logged_in = False

    if workflow_root is not None and find_node_center(workflow_root, "重复") is not None:
        logged_in = True
    elif workflow_root is not None and find_node_center(workflow_root, "连接工作流服务") is not None:
        dump_ui_and_screenshot(out_dir, "03_workflow_connect")
        if identity and password:
            fields = find_edit_fields(workflow_root)
            email_pos = find_node_center(workflow_root, "name@example.com") or (fields[1][1] if len(fields) >= 2 else None)
            pwd_pos = find_node_center(workflow_root, "输入密码") or (fields[2][1] if len(fields) >= 3 else None)

            if email_pos and pwd_pos:
                print("[INFO] Entering workflow test credentials...")
                adb(["shell", "input", "tap", str(email_pos[0]), str(email_pos[1])])
                time.sleep(0.6)
                adb_input_text(identity)
                time.sleep(0.6)

                adb(["shell", "input", "tap", str(pwd_pos[0]), str(pwd_pos[1])])
                time.sleep(0.6)
                adb_input_text(password)
                time.sleep(0.6)

                adb(["shell", "input", "keyevent", "111"], check=False)  # dismiss soft keyboard if open
                time.sleep(0.8)

                after_fill = dump_ui_and_screenshot(out_dir, "03b_workflow_connect_filled")
                connect_pos = find_node_center(after_fill, "连接工作流服务")
                if connect_pos:
                    adb(["shell", "input", "tap", str(connect_pos[0]), str(connect_pos[1])])
                    for _ in range(8):
                        time.sleep(2.0)
                        check_root = dump_ui_and_screenshot(out_dir, "04_workflow_after_login")
                        if find_node_center(check_root, "重复") is not None:
                            logged_in = True
                            workflow_root = check_root
                            break
                else:
                    print("[WARN] Connect button not found after filling credentials")
            else:
                print(f"[WARN] Could not locate email/password fields (found {len(fields)} EditTexts)")
        else:
            print("[INFO] WORKFLOW_TEST_IDENTITY / WORKFLOW_TEST_PASSWORD not set; skipping login")

    captured_tabs: list[str] = []
    if logged_in:
        tabs = [
            ("工作流", "05_tab_runs"),
            ("重复", "06_tab_schedules"),
            ("收藏", "07_tab_library"),
            ("资料库", "08_tab_files"),
            ("转换", "09_tab_converter"),
        ]
        current_root = workflow_root
        for label, shot_name in tabs:
            res_root = tap_label(out_dir, label, shot_name, wait_sec=2.5, root=current_root)
            if res_root is not None:
                captured_tabs.append(label)
                current_root = res_root

        settings_root = tap_label(out_dir, "工作流设置", "10_workflow_settings", wait_sec=2.0, root=current_root)
        if settings_root is not None:
            adb(["shell", "input", "keyevent", "4"], check=False)  # Back

    logs_dir = out_dir / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)
    logcat = adb(["logcat", "-d", "-v", "time"], check=False).stdout
    secrets = [password]
    (logs_dir / "logcat.txt").write_text(redact(logcat, secrets), encoding="utf-8")

    screenshots = sorted(p.name for p in (out_dir / "screenshots").glob("*.png"))
    report = {
        "packageId": PACKAGE_ID,
        "mainActivity": MAIN_ACTIVITY,
        "pbUrl": pb_url,
        "loggedIn": logged_in,
        "capturedTabs": captured_tabs,
        "screenshots": screenshots,
    }
    (out_dir / "android-verify-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Saved {len(screenshots)} screenshots & logcat to {out_dir}")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="RikkaHub AnyWorkflow E2E verification")
    parser.add_argument("--mode", choices=["api", "android", "all"], default="api")
    parser.add_argument("--pb-url", default=os.environ.get("WORKFLOW_PB_URL", DEFAULT_PB_URL))
    parser.add_argument("--remote-url", default=os.environ.get("WORKFLOW_REMOTE_API_URL", DEFAULT_REMOTE_URL))
    parser.add_argument("--identity", default=os.environ.get("WORKFLOW_TEST_IDENTITY", ""))
    parser.add_argument("--password", default=os.environ.get("WORKFLOW_TEST_PASSWORD", ""))
    parser.add_argument("--write-test", action="store_true", help="Perform upload/read/delete markdown roundtrip")
    parser.add_argument("--apk", default=None, help="Path to debug APK for android mode")
    parser.add_argument("--out-dir", default="artifacts/verify")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    exit_code = 0

    if args.mode in ("api", "all"):
        summary = run_api_verification(
            pb_url=args.pb_url.rstrip("/"),
            remote_url=args.remote_url.rstrip("/"),
            identity=args.identity,
            password=args.password,
            write_test=args.write_test,
            out_dir=out_dir,
        )
        print(f"API verification: {summary['passed']}/{summary['total']} passed")
        if args.identity and args.password and summary["passed"] < summary["total"]:
            exit_code = 1

    if args.mode in ("android", "all"):
        report = run_android_verification(
            apk_path=args.apk,
            out_dir=out_dir,
            pb_url=args.pb_url.rstrip("/"),
            identity=args.identity,
            password=args.password,
        )
        if args.identity and args.password and not report["loggedIn"]:
            print("[FAIL] Credentials were provided but Android workflow login did not succeed")
            exit_code = 1

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
