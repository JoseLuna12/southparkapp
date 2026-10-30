#!/usr/bin/env python3
"""Live D-pad/design regression checks. Emulator ONLY; restores settings in finally.

Requires installed v0.5 APK and live official catalog. Never clears app data.
Outputs screenshots/XML to /tmp/southpark-design-review by default.
"""
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

serial = os.environ.get("ANDROID_SERIAL", "emulator-5554")
if not serial.startswith("emulator-"):
    raise SystemExit("This check changes temporary settings: use an emulator, not a physical TV.")
sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
adb = str(sdk / "platform-tools/adb")
package = "com.unofficial.southparktv"
artifacts = Path(os.environ.get("TV_UI_ARTIFACT_DIR", "/tmp/southpark-design-review"))
artifacts.mkdir(parents=True, exist_ok=True)


def run(*args):
    return subprocess.check_output([adb, "-s", serial, *map(str, args)], text=True, timeout=30)


def key(*keys):
    run("shell", "input", "keyevent", *keys)


def nodes():
    for _ in range(3):
        result = run("shell", "uiautomator", "dump", "--compressed", "/sdcard/design-check.xml")
        if "dumped to" in result:
            return list(ET.fromstring(run("shell", "cat", "/sdcard/design-check.xml")).iter("node"))
        time.sleep(1)
    raise RuntimeError("UI snapshot failed; refusing to read stale state")


def by_id(name, current=None):
    return [n for n in (nodes() if current is None else current)
            if n.get("resource-id", "").endswith(":id/" + name)]


def wait_text(text):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        current = nodes()
        if any(text in n.get("text", "") for n in current):
            return current
        time.sleep(1)
    raise AssertionError(f"Did not find {text!r}")


def launch():
    run("shell", "am", "force-stop", package)
    run("shell", "am", "start", "-n", package + "/.MainActivity")
    wait_text("seasons listed")


def capture(name):
    xml = run("shell", "uiautomator", "dump", "--compressed", "/sdcard/design-check.xml")
    assert "dumped to" in xml
    (artifacts / (name + ".xml")).write_text(run("shell", "cat", "/sdcard/design-check.xml"))
    with (artifacts / (name + ".png")).open("wb") as output:
        subprocess.run([adb, "-s", serial, "exec-out", "screencap", "-p"], stdout=output, check=True, timeout=20)


def selected_card():
    return next((n for n in by_id("episode_card") if n.get("selected") == "true"), None)


def focus_is_safe():
    current = nodes()
    selected = selected_card()
    assert selected is not None, "Grid selection is invisible to the accessibility tree"
    left, top, right, bottom = map(int, re.findall(r"\d+", selected.get("bounds")))
    width, height = map(int, re.findall(r"(\d+)x(\d+)", run("shell", "wm", "size"))[-1])
    density = int(re.findall(r"\d+", run("shell", "wm", "density"))[-1]) / 160
    assert left >= 48 * density and right <= width - 48 * density, "Focused card exceeds horizontal safe area"
    assert top >= 27 * density and bottom <= height - 27 * density, "Focused card is clipped vertically"
    for name in ("catalog_reload", "continue_watching", "next_episode"):
        for node in by_id(name, current):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            assert y2 - y1 >= 48 * density, f"{name} fell below the app's target-height contract"


font = run("shell", "settings", "get", "system", "font_scale").strip()
animator = run("shell", "settings", "get", "global", "animator_duration_scale").strip()
try:
    # Explicitly make the normal-scale capture reproducible, then restore the user's value.
    run("shell", "settings", "put", "system", "font_scale", "1.0")
    launch()
    key(20)  # Leave touch mode if a previous time-bar test used a tap.
    key(122)  # First season, regardless of saved selection.
    key(*([20] * 11 + [23]))
    wait_text("14 episodes")
    key(4)  # Grid -> season rail, with the chosen season still 12.
    key(20)  # Navigate to 13 WITHOUT activating it.
    assert any(n.get("text") == "Season 13" and n.get("selected") == "true" for n in nodes())
    wait_text("14 episodes")
    capture("redesign-season-focus")
    key(19, 23, 122, 22, 22)
    assert "Major Boobage" in selected_card().get("content-desc", "")
    focus_is_safe()
    capture("redesign-catalog")
    key(23)
    wait_text("Watch episode")
    assert any(n.get("focused") == "true" for n in by_id("button1")), "Primary dialog action did not get focus"
    capture("redesign-details")
    key(4)
    assert "Major Boobage" in selected_card().get("content-desc", ""), "Dialog dismissed to the wrong episode"
    key(4, 122, 19)  # First rail item -> Continue/Reload above it.
    for _ in range(4):
        if any(n.get("focused") == "true" for n in by_id("catalog_reload")):
            break
        key(19)
    assert any(n.get("focused") == "true" for n in by_id("catalog_reload")), "Reload needs a Menu/touch key"
    key(23)
    wait_text("14 episodes")
    assert any(n.get("focused") == "true" for n in by_id("catalog_reload")), "Async reload stole focus"
    print("PASS: focus versus season selection, safe card/targets, dialog return, D-pad reload without focus theft")
    key(20, 4, 123, 23)  # Reload -> grid -> Back to rail -> published Season 29.
    wait_text("No full episodes")
    assert any(n.get("focused") == "true" for n in by_id("season_list")), "Empty season trapped focus"
    capture("redesign-empty-season")
    key(122, *([20] * 11), 23)
    wait_text("14 episodes")
    print("PASS: clip-only Season 29 has an honest full-episode empty state and recoverable focus")

    # Font scale may recreate the activity. Static focus must survive disabled animation.
    run("shell", "settings", "put", "system", "font_scale", "1.2")
    run("shell", "settings", "put", "global", "animator_duration_scale", "0")
    launch()
    key(23, 122, 22, 22)
    focus_is_safe()
    capture("redesign-font-scale-120")
    print("PASS: 1.2 font scale, safe focused card and targets with animator scale zero")
finally:
    for scope, name, value in (("system", "font_scale", font), ("global", "animator_duration_scale", animator)):
        run("shell", "settings", "delete", scope, name) if value == "null" else run("shell", "settings", "put", scope, name, value)
    launch()
print(f"Artifacts: {artifacts}")
