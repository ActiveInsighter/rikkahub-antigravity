#!/usr/bin/env python3
"""
RikkaHub AnyWorkflow E2E Visual & API Verification Script.

Supports:
  --mode api      : Verify PocketBase + Cloudflare Worker APIs with a test account (or --mock)
  --mode android  : Install APK on connected adb device/emulator, login, capture screenshots & logcat
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
import shlex
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

DEFAULT_PB_URL = "https://pb.any1.tech"
DEFAULT_REMOTE_URL = "https://anyworkflow-remote.2212148739lbw.workers.dev"
PACKAGE_ID = "me.rerere.rikkahub.debug"
MAIN_ACTIVITY = "me.rerere.rikkahub.RouteActivity"
MOCK_PORT = 18090
MOCK_IDENTITY = "test@anyworkflow.local"
MOCK_PASSWORD = "test-password-123"


def redact(text: str, secrets: list[str]) -> str:
    out = text
    for s in secrets:
        if s and len(s) >= 4:
            out = out.replace(s, "***REDACTED***")
    out = re.sub(r"(Bearer\s+)[A-Za-z0-9._-]+", r"\1***REDACTED***", out, flags=re.IGNORECASE)
    return out


class MockPocketBaseServer:
    """Lightweight local PocketBase + AnyWorkflow mock server for offline/secretless visual verification."""

    def __init__(self, port: int = MOCK_PORT) -> None:
        self.port = port
        self.token = "mock-pb-token-visual-verify"
        self.user_id = "mock_user_001"
        self.server: ThreadingHTTPServer | None = None
        self.thread: threading.Thread | None = None

    def _sample_data(self) -> dict[str, Any]:
        now = "2026-11-17T08:00:00.000Z"
        uid = self.user_id
        return {
            "aw_dispatch_runs": [
                {
                    "id": "run_demo_001",
                    "owner": uid,
                    "title": "每日 AI 论文摘要与归档",
                    "planText": "step fetch_arxiv: 抓取当日 cs.AI 最新论文\nstep summarize: 生成中文摘要并写入资料库",
                    "familyId": "fam_demo_001",
                    "versionNumber": 2,
                    "versionMajor": 2,
                    "versionMinor": 0,
                    "parentRun": "",
                    "origin": "manual",
                    "executionMode": "sequential",
                    "maxConcurrency": 1,
                    "status": "completed",
                    "requestedAction": "none",
                    "commandVersion": 1,
                    "scheduledAt": "",
                    "totalTasks": 2,
                    "completedTasks": 2,
                    "lastError": "",
                    "startedAt": now,
                    "endedAt": now,
                    "created": now,
                    "updated": now,
                },
                {
                    "id": "run_demo_002",
                    "owner": uid,
                    "title": "仓库 CI 状态巡检",
                    "planText": "step check_ci: 检查最近一次构建与测试结果",
                    "familyId": "fam_demo_002",
                    "versionNumber": 1,
                    "versionMajor": 1,
                    "versionMinor": 0,
                    "parentRun": "",
                    "origin": "schedule",
                    "executionMode": "parallel",
                    "maxConcurrency": 2,
                    "status": "running",
                    "requestedAction": "none",
                    "commandVersion": 1,
                    "scheduledAt": "",
                    "totalTasks": 3,
                    "completedTasks": 1,
                    "lastError": "",
                    "startedAt": now,
                    "endedAt": "",
                    "created": now,
                    "updated": now,
                },
            ],
            "aw_dispatch_tasks": [
                {
                    "id": "task_demo_001",
                    "owner": uid,
                    "run": "run_demo_001",
                    "runIndex": 0,
                    "executorKind": "agent",
                    "title": "抓取当日 cs.AI 最新论文",
                    "prompt": "Fetch top 5 cs.AI papers",
                    "status": "completed",
                    "outputText": "已抓取 5 篇论文并提取摘要。",
                    "created": now,
                    "updated": now,
                },
                {
                    "id": "task_demo_002",
                    "owner": uid,
                    "run": "run_demo_001",
                    "runIndex": 1,
                    "executorKind": "agent",
                    "title": "生成中文摘要并写入资料库",
                    "prompt": "Summarize in Chinese",
                    "status": "completed",
                    "outputText": "已生成 Markdown 报告并归档。",
                    "created": now,
                    "updated": now,
                },
            ],
            "aw_dispatch_events": [],
            "aw_history_messages": [],
            "aw_history_events": [],
            "aw_history_acts": [],
            "aw_run_schedules": [
                {
                    "id": "sched_demo_001",
                    "owner": uid,
                    "title": "每天 09:00 自动执行论文摘要",
                    "cron": "0 9 * * *",
                    "timezone": "Asia/Shanghai",
                    "enabled": True,
                    "planText": "step fetch: 抓取并总结论文",
                    "nextRunAt": "2026-11-18T01:00:00.000Z",
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_run_favorites": [
                {
                    "id": "fav_demo_001",
                    "owner": uid,
                    "run": "run_demo_001",
                    "title": "每日 AI 论文摘要与归档",
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_templates": [
                {
                    "id": "tpl_demo_001",
                    "owner": uid,
                    "title": "深度研究报告模板",
                    "description": "多步骤检索 + 交叉验证 + Markdown 导出",
                    "planText": "step research: 检索资料\nstep verify: 核验引用\nstep write: 输出报告",
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_library_folders": [
                {
                    "id": "folder_demo_001",
                    "owner": uid,
                    "name": "论文归档",
                    "scope": "template",
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_file_conversion_configs": [
                {
                    "id": "conv_cfg_001",
                    "owner": uid,
                    "name": "默认 PDF 转 Markdown",
                    "model": "gemini-2.5-flash",
                    "isDefault": True,
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_pdf_to_md_jobs": [
                {
                    "id": "conv_job_001",
                    "owner": uid,
                    "fileName": "attention-is-all-you-need.pdf",
                    "status": "completed",
                    "totalPages": 15,
                    "completedPages": 15,
                    "created": now,
                    "updated": now,
                }
            ],
            "aw_user_prefs": [],
        }

    def start(self) -> None:
        data = self._sample_data()
        token = self.token
        user_id = self.user_id

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, format: str, *args: Any) -> None:
                return

            def _send(self, status: int, payload: Any) -> None:
                body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self) -> None:
                parsed = urllib.parse.urlparse(self.path)
                path = parsed.path
                if path == "/api/health":
                    self._send(200, {"code": 200, "message": "API is healthy (mock).", "data": {}})
                    return
                if path == "/api/anyworkflow/models/saved":
                    self._send(200, {"items": [{"id": "gemini-2.5-flash", "label": "Gemini 2.5 Flash"}]})
                    return
                if path == "/api/anyworkflow/models/catalog":
                    self._send(200, {"items": [{"id": "gemini-2.5-flash", "label": "Gemini 2.5 Flash"}]})
                    return
                if path == "/api/anyworkflow/start-links":
                    self._send(200, {"items": []})
                    return
                if path == "/api/anyworkflow/runtime-defaults":
                    self._send(200, {"model": "gemini-2.5-flash", "maxConcurrency": 2})
                    return

                m_list = re.match(r"^/api/collections/([^/]+)/records$", path)
                if m_list:
                    col = m_list.group(1)
                    items = data.get(col, [])
                    self._send(200, {"page": 1, "perPage": 30, "totalItems": len(items), "totalPages": 1, "items": items})
                    return

                m_item = re.match(r"^/api/collections/([^/]+)/records/([^/]+)$", path)
                if m_item:
                    col, rec_id = m_item.group(1), m_item.group(2)
                    for item in data.get(col, []):
                        if item.get("id") == rec_id:
                            self._send(200, item)
                            return
                    self._send(404, {"code": 404, "message": "Not found"})
                    return

                self._send(200, {"items": []})

            def do_POST(self) -> None:
                parsed = urllib.parse.urlparse(self.path)
                path = parsed.path
                length = int(self.headers.get("Content-Length", "0") or 0)
                raw = self.rfile.read(length) if length > 0 else b"{}"
                try:
                    req_json = json.loads(raw.decode("utf-8"))
                except Exception:
                    req_json = {}

                if path == "/api/collections/aw_clients/auth-with-password":
                    self._send(
                        200,
                        {
                            "token": token,
                            "record": {
                                "id": user_id,
                                "email": req_json.get("identity") or MOCK_IDENTITY,
                                "name": "Visual Verify Test",
                            },
                        },
                    )
                    return

                self._send(200, {"ok": True, "id": "mock_created_001"})

            def do_PATCH(self) -> None:
                self._send(200, {"ok": True})

            def do_DELETE(self) -> None:
                self._send(204, {})

        self.server = ThreadingHTTPServer(("127.0.0.1", self.port), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
            self.server = None


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
    use_mock: bool = False,
) -> dict[str, Any]:
    out_dir.mkdir(parents=True, exist_ok=True)
    log_lines: list[str] = []
    results: list[dict[str, Any]] = []
    secrets = [password]
    mock_srv: MockPocketBaseServer | None = None

    if use_mock:
        mock_srv = MockPocketBaseServer()
        mock_srv.start()
        pb_url = f"http://127.0.0.1:{mock_srv.port}"
        identity = identity or MOCK_IDENTITY
        password = password or MOCK_PASSWORD
        secrets.append(password)

    try:
        def record(name: str, ok: bool, status: int, detail: str = "") -> None:
            safe_detail = redact(detail, secrets)
            results.append({"name": name, "ok": ok, "status": status, "detail": safe_detail})
            marker = "PASS" if ok else "FAIL"
            line = f"[{marker}] {name} (HTTP {status}) {safe_detail}".strip()
            log_lines.append(line)
            print(line)

        status, payload = http_json(f"{pb_url}/api/health")
        record("pb.health", status == 200, status)

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

            if not use_mock:
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
    finally:
        if mock_srv is not None:
            mock_srv.stop()

    (out_dir / "api-verify.log").write_text("\n".join(log_lines) + "\n", encoding="utf-8")
    summary = {
        "pbUrl": pb_url,
        "remoteUrl": remote_url,
        "mock": use_mock,
        "passed": sum(1 for r in results if r["ok"]),
        "total": len(results),
        "results": results,
    }
    (out_dir / "verify-report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return summary


def adb(args: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    res = subprocess.run(["adb", *args], text=True, capture_output=True)
    if check and res.returncode != 0:
        print(f"[ADB ERROR] cmd=adb {' '.join(args)}\nstdout={res.stdout}\nstderr={res.stderr}", file=sys.stderr)
        raise subprocess.CalledProcessError(res.returncode, ["adb", *args], output=res.stdout, stderr=res.stderr)
    return res


def dump_ui_xml() -> ET.Element | None:
    adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"], check=False)
    xml_res = adb(["shell", "cat", "/sdcard/window_dump.xml"], check=False)
    if xml_res.returncode == 0 and "<hierarchy" in xml_res.stdout:
        try:
            return ET.fromstring(xml_res.stdout)
        except ET.ParseError:
            return None
    return None


def capture_screen(out_dir: Path, name: str, save_xml: bool = True) -> ET.Element | None:
    screenshots_dir = out_dir / "screenshots"
    ui_dir = out_dir / "ui-dumps"
    screenshots_dir.mkdir(parents=True, exist_ok=True)
    ui_dir.mkdir(parents=True, exist_ok=True)

    png_bytes = subprocess.check_output(["adb", "exec-out", "screencap", "-p"])
    (screenshots_dir / f"{name}.png").write_bytes(png_bytes)

    adb(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"], check=False)
    xml_res = adb(["shell", "cat", "/sdcard/window_dump.xml"], check=False)
    if xml_res.returncode == 0 and "<hierarchy" in xml_res.stdout:
        if save_xml:
            (ui_dir / f"{name}.xml").write_text(xml_res.stdout, encoding="utf-8")
        try:
            return ET.fromstring(xml_res.stdout)
        except ET.ParseError:
            return None
    return None


def parse_bounds(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_node_center(root: ET.Element | None, text_match: str, exact: bool = False) -> tuple[int, int] | None:
    if root is None:
        return None
    for node in root.iter("node"):
        text = (node.attrib.get("text") or "").strip()
        desc = (node.attrib.get("content-desc") or "").strip()
        matched = (text == text_match or desc == text_match) if exact else (text_match in text or text_match in desc)
        if matched:
            pos = parse_bounds(node.attrib.get("bounds", ""))
            if pos:
                return pos
    return None


def find_edit_texts(root: ET.Element | None) -> list[tuple[int, int]]:
    if root is None:
        return []
    out: list[tuple[int, int]] = []
    for node in root.iter("node"):
        cls = node.attrib.get("class", "")
        if "EditText" in cls:
            pos = parse_bounds(node.attrib.get("bounds", ""))
            if pos:
                out.append(pos)
    return out


def dismiss_anr_if_present(root: ET.Element | None) -> bool:
    if root is None:
        return False
    if find_node_center(root, "isn't responding") is not None:
        wait_pos = find_node_center(root, "Wait", exact=True)
        if wait_pos:
            print("[INFO] Dismissing Android ANR dialog ('Wait')")
            adb(["shell", "input", "tap", str(wait_pos[0]), str(wait_pos[1])], check=False)
            time.sleep(1.0)
            return True
    return False


def wait_for_any_label(labels: list[str], timeout_sec: float = 35.0, poll_sec: float = 1.5) -> ET.Element | None:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        root = dump_ui_xml()
        if dismiss_anr_if_present(root):
            continue
        for label in labels:
            if find_node_center(root, label) is not None:
                return root
        time.sleep(poll_sec)
    return dump_ui_xml()


def hide_keyboard_if_shown() -> None:
    res = adb(["shell", "dumpsys", "input_method"], check=False)
    if "mInputShown=true" in res.stdout:
        adb(["shell", "input", "keyevent", "4"], check=False)
        time.sleep(0.5)


def input_text_into_field(pos: tuple[int, int], value: str, clear_existing: bool = False) -> None:
    adb(["shell", "input", "tap", str(pos[0]), str(pos[1])])
    time.sleep(0.4)
    if clear_existing:
        adb(["shell", "input", "keyevent", "123"], check=False)  # KEYCODE_MOVE_END
        del_events = " ".join(["67"] * 45)
        adb(["shell", f"input keyevent {del_events}"], check=False)
        time.sleep(0.2)
    adb(["shell", f"input text {shlex.quote(value)}"])
    time.sleep(0.4)
    hide_keyboard_if_shown()


def tap_and_capture(out_dir: Path, label: str, capture_name: str, wait_sec: float = 2.0, exact: bool = False) -> bool:
    root = dump_ui_xml()
    if dismiss_anr_if_present(root):
        root = dump_ui_xml()
    pos = find_node_center(root, label, exact=exact)
    if not pos:
        print(f"[WARN] UI node '{label}' not found for '{capture_name}'")
        return False
    adb(["shell", "input", "tap", str(pos[0]), str(pos[1])])
    time.sleep(wait_sec)
    after_root = dump_ui_xml()
    if dismiss_anr_if_present(after_root):
        time.sleep(1.0)
    capture_screen(out_dir, capture_name)
    return True


def run_android_verification(
    apk_path: str | None,
    out_dir: Path,
    pb_url: str,
    identity: str,
    password: str,
    use_mock: bool = False,
) -> dict[str, Any]:
    devices = adb(["devices"]).stdout
    if "\tdevice" not in devices:
        raise RuntimeError(f"No connected adb device/emulator found:\n{devices}")

    mock_srv: MockPocketBaseServer | None = None
    if use_mock:
        mock_srv = MockPocketBaseServer()
        mock_srv.start()
        adb(["reverse", f"tcp:{mock_srv.port}", f"tcp:{mock_srv.port}"], check=False)
        pb_url = f"http://127.0.0.1:{mock_srv.port}"
        identity = identity or MOCK_IDENTITY
        password = password or MOCK_PASSWORD
        print(f"[INFO] Started mock PocketBase server at {pb_url} (adb reverse enabled)")

    captured: list[str] = []
    try:
        adb(["logcat", "-c"], check=False)
        if apk_path:
            print(f"Installing APK: {apk_path}")
            for attempt in range(1, 5):
                for _ in range(30):
                    pm_res = adb(["shell", "pm", "path", "android"], check=False)
                    sm_res = adb(["shell", "sm", "list-volumes"], check=False)
                    if (
                        pm_res.returncode == 0
                        and "package:" in pm_res.stdout
                        and sm_res.returncode == 0
                        and "mounted" in sm_res.stdout
                    ):
                        break
                    time.sleep(2)
                res = adb(["install", "-r", "-d", "-t", apk_path], check=False)
                if res.returncode == 0:
                    break
                print(f"[WARN] adb install attempt {attempt} failed: {res.stdout} {res.stderr}")
                if attempt == 4:
                    raise subprocess.CalledProcessError(res.returncode, ["adb", "install", apk_path], output=res.stdout, stderr=res.stderr)
                time.sleep(10)

        # Clear app state for a deterministic cold-start login & screenshot run
        adb(["shell", "pm", "clear", PACKAGE_ID], check=False)
        adb(["shell", "am", "start", "-n", f"{PACKAGE_ID}/{MAIN_ACTIVITY}"])

        root = wait_for_any_label(["Messages", "工作流", "连接工作流服务"], timeout_sec=45.0)
        dismiss_anr_if_present(root)
        capture_screen(out_dir, "01_chat_home")
        captured.append("01_chat_home.png")

        # Open ChatDrawer via top-left Menu button ("Messages") or edge swipe
        menu_pos = find_node_center(root, "Messages")
        if menu_pos:
            adb(["shell", "input", "tap", str(menu_pos[0]), str(menu_pos[1])])
        else:
            adb(["shell", "input", "swipe", "10", "800", "750", "800", "250"])
        time.sleep(1.5)
        root = capture_screen(out_dir, "02_chat_drawer")
        captured.append("02_chat_drawer.png")

        # Navigate to WorkflowPage
        if tap_and_capture(out_dir, "工作流", "03_workflow_entry", wait_sec=2.0, exact=True):
            captured.append("03_workflow_entry.png")

        root = wait_for_any_label(["连接工作流服务", "重复", "收藏"], timeout_sec=10.0)
        if find_node_center(root, "连接工作流服务") is not None and identity and password:
            fields = find_edit_texts(root)
            if len(fields) >= 3:
                if pb_url and pb_url != DEFAULT_PB_URL:
                    input_text_into_field(fields[0], pb_url, clear_existing=True)
                input_text_into_field(fields[1], identity, clear_existing=False)
                input_text_into_field(fields[2], password, clear_existing=False)
                adb(["shell", "input", "keyevent", "111"], check=False)  # KEYCODE_ESCAPE to dismiss IME if open
                time.sleep(0.5)
                capture_screen(out_dir, "04_workflow_connect_filled")
                captured.append("04_workflow_connect_filled.png")

                root_after_fill = dump_ui_xml()
                btn_pos = find_node_center(root_after_fill, "连接工作流服务")
                if btn_pos is None:
                    adb(["shell", "input", "keyevent", "4"], check=False)  # KEYCODE_BACK to close soft keyboard
                    time.sleep(0.6)
                    root_after_fill = dump_ui_xml()
                    btn_pos = find_node_center(root_after_fill, "连接工作流服务")
                if btn_pos:
                    adb(["shell", "input", "tap", str(btn_pos[0]), str(btn_pos[1])])
                    wait_for_any_label(["重复", "收藏", "资料库", "转换"], timeout_sec=20.0)
                    time.sleep(1.5)
            else:
                print(f"[WARN] Expected 3 EditText fields on ConnectWorkflowView, found {len(fields)}")

        # Capture all 5 Workflow tabs
        capture_screen(out_dir, "05_tab_runs")
        captured.append("05_tab_runs.png")

        if tap_and_capture(out_dir, "新建工作流", "06_create_run_dialog", wait_sec=1.5):
            captured.append("06_create_run_dialog.png")
            adb(["shell", "input", "keyevent", "4"], check=False)
            time.sleep(1.0)

        for idx, tab_label in enumerate(["重复", "收藏", "资料库", "转换"], start=7):
            name = f"{idx:02d}_tab_{tab_label}"
            if tap_and_capture(out_dir, tab_label, name, wait_sec=2.0, exact=True):
                captured.append(f"{name}.png")

        if tap_and_capture(out_dir, "工作流设置", "11_workflow_settings", wait_sec=1.5):
            captured.append("11_workflow_settings.png")
            adb(["shell", "input", "keyevent", "4"], check=False)
            time.sleep(0.8)

        logs_dir = out_dir / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)
        logcat = adb(["logcat", "-d", "-v", "time"], check=False).stdout
        secrets = [password]
        (logs_dir / "logcat.txt").write_text(redact(logcat, secrets), encoding="utf-8")

        summary = {
            "pbUrl": pb_url,
            "mock": use_mock,
            "screenshots": captured,
            "count": len(captured),
        }
        (out_dir / "visual-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Saved {len(captured)} screenshots & logcat to {out_dir}")
        return summary
    finally:
        if mock_srv is not None:
            adb(["reverse", "--remove", f"tcp:{mock_srv.port}"], check=False)
            mock_srv.stop()


def main() -> int:
    parser = argparse.ArgumentParser(description="RikkaHub AnyWorkflow E2E verification")
    parser.add_argument("--mode", choices=["api", "android", "all"], default="api")
    parser.add_argument("--pb-url", default=os.environ.get("WORKFLOW_PB_URL", DEFAULT_PB_URL))
    parser.add_argument("--remote-url", default=os.environ.get("WORKFLOW_REMOTE_API_URL", DEFAULT_REMOTE_URL))
    parser.add_argument("--identity", default=os.environ.get("WORKFLOW_TEST_IDENTITY", ""))
    parser.add_argument("--password", default=os.environ.get("WORKFLOW_TEST_PASSWORD", ""))
    parser.add_argument("--write-test", action="store_true", help="Perform upload/read/delete markdown roundtrip")
    parser.add_argument("--mock", action="store_true", help="Use built-in local mock PocketBase server")
    parser.add_argument(
        "--mock-if-no-creds",
        action="store_true",
        help="Fallback to built-in mock PocketBase server when WORKFLOW_TEST_IDENTITY/PASSWORD are empty",
    )
    parser.add_argument("--apk", default=None, help="Path to debug APK for android mode")
    parser.add_argument("--out-dir", default="artifacts/verify")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    exit_code = 0
    use_mock = args.mock or (args.mock_if_no_creds and not (args.identity and args.password))

    if args.mode in ("api", "all"):
        summary = run_api_verification(
            pb_url=args.pb_url.rstrip("/"),
            remote_url=args.remote_url.rstrip("/"),
            identity=args.identity,
            password=args.password,
            write_test=args.write_test,
            out_dir=out_dir,
            use_mock=use_mock,
        )
        print(f"API verification: {summary['passed']}/{summary['total']} passed (mock={use_mock})")
        if (use_mock or (args.identity and args.password)) and summary["passed"] < summary["total"]:
            exit_code = 1

    if args.mode in ("android", "all"):
        run_android_verification(
            apk_path=args.apk,
            out_dir=out_dir,
            pb_url=args.pb_url.rstrip("/"),
            identity=args.identity,
            password=args.password,
            use_mock=use_mock,
        )

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
