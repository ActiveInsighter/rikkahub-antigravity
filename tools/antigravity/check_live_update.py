"""Exercise the released APK's update UI on a disposable Android emulator."""
import os
import re
import subprocess
import time
from xml.etree import ElementTree

PACKAGE = "me.rerere.rikkahub.debug"
BASE_APK = os.path.join(os.environ["RUNNER_TEMP"], "base.apk")
TARGET_VERSION = 100000 + int(os.environ["TARGET_BUILD"])


def adb(*args, timeout=55):
    return subprocess.run(
        ["adb", *args], text=True, capture_output=True, check=True, timeout=timeout,
    ).stdout


def version():
    package = adb("shell", "dumpsys", "package", PACKAGE)
    match = re.search(r"\bversionCode=(\d+)", package)
    return int(match[1]) if match else None


def tap_matching(predicate, timeout):
    deadline = time.monotonic() + timeout
    last_nodes = []
    while time.monotonic() < deadline:
        try:
            output = adb("shell", "uiautomator", "dump", "/sdcard/ota-smoke.xml", timeout=12)
            if "dumped to" not in output:
                time.sleep(1)
                continue
            xml = adb("exec-out", "cat", "/sdcard/ota-smoke.xml", timeout=5)
            last_nodes = list(ElementTree.fromstring(xml).iter("node"))
        except (subprocess.SubprocessError, ElementTree.ParseError):
            time.sleep(1)
            continue
        for node in last_nodes:
            if node.get("enabled") == "true" and predicate(node):
                left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
                adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
        time.sleep(1)
    visible = [(node.get("text"), node.get("content-desc"), node.get("package")) for node in last_nodes]
    raise RuntimeError(f"Expected control not found. Last visible controls: {visible}")


print(adb("install", BASE_APK), flush=True)
assert version() == 100118, version()
adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow")
adb("logcat", "-c")
adb("shell", "monkey", "-p", PACKAGE, "1")
tap_matching(lambda node: node.get("content-desc") == "Downloads and updates", timeout=45)
tap_matching(lambda node: node.get("text", "").startswith("Incremental"), timeout=55)
print("Selected the incremental update from Build 118.", flush=True)
tap_matching(
    lambda node: "packageinstaller" in node.get("package", "")
    and node.get("text", "").casefold() in {"install", "update"},
    timeout=120,
)
for _ in range(45):
    if version() == TARGET_VERSION:
        break
    time.sleep(2)
else:
    raise RuntimeError(f"Android still reports versionCode {version()}")
log = adb("logcat", "-d", "-s", "ApkUpdates:W")
assert "trying full APK" not in log, log
print(f"Android installed versionCode {TARGET_VERSION} from the live delta.", flush=True)
