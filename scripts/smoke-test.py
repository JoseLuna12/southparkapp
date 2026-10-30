#!/usr/bin/env python3
"""Live native-TV test. One connected TV emulator/device; network required.

Installs the debug APK, browses with D-pad keys, then plays the two supplied
Season 12 episodes, then verifies autoplay into E5. Never clears app data.
Set ANDROID_SERIAL when multiple devices are connected.
"""
import os
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
adb = str(sdk / "platform-tools/adb")
root = Path(__file__).resolve().parents[1]
package = "com.unofficial.southparktv"


def run(*args):
    return subprocess.check_output([adb, *map(str, args)], text=True, timeout=30)


def key(*keys):
    run("shell", "input", "keyevent", *keys)


def nodes():
    for _ in range(3):
        result = run("shell", "uiautomator", "dump", "--compressed", "/sdcard/southparktv-test.xml")
        if "dumped to" in result:
            return list(ET.fromstring(run("shell", "cat", "/sdcard/southparktv-test.xml")).iter("node"))
        time.sleep(1)
    raise RuntimeError("UI snapshot did not complete; refusing to read a stale dump")


def wait_for(check, message, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = check()
        if value:
            return value
        time.sleep(1)
    raise AssertionError(message)


def normalized(value):
    return re.sub(r"[\s·,:]+", " ", value).strip().casefold()


def has_text(value):
    wanted = normalized(value)
    return any(wanted in normalized(n.get("text", "") + " " + n.get("content-desc", "")) for n in nodes())


def matches_id(node, name):
    return node.get("resource-id", "").endswith(":id/" + name)


def focused_id(name):
    return any(matches_id(n, name) and n.get("focused") == "true" for n in nodes())


def logs():
    return run("logcat", "-d", "-s", "SouthParkTV")


def position(episode_id):
    matches = re.findall(r"NATIVE_PROGRESS episode=" + re.escape(episode_id) + r" contentMs=(\d+)", logs())
    return int(matches[-1]) if matches else 0


def play_selected(title, episode_id):
    key(23)
    assert has_text(title), f"Wrong episode selected; expected {title}"
    assert has_text("WATCH EPISODE"), "Free episode incorrectly marked unavailable"
    assert focused_id("button1"), "Episode dialog should focus its primary Watch action"
    key(23)  # Activate the primary dialog action with the remote.
    wait_for(lambda: f"NATIVE_FIRST_FRAME episode={episode_id}" in logs(), f"No native video frame for {title}")
    initial = position(episode_id)
    wait_for(lambda: position(episode_id) > initial + 1500, f"Playback did not advance for {title}")
    assert "NATIVE_ERROR" not in logs(), "Native playback error; inspect logcat"
    wait_for(lambda: f"NATIVE_AUDIO_STARTED episode={episode_id}" in logs(), f"Native audio did not start for {title}")
    assert "NATIVE_AUDIO_ERROR" not in logs(), "Native audio sink error"
    hal = run("logcat", "-d", "-s", "android.hardware.audio@7.1-impl.ranchu")
    assert not re.search(r"pcm_writei failed|creating NullSink|failed to create alsa sink|pcm_open failed", hal), "Emulator audio hardware failed or uses a silent NullSink"
    print(f"PASS: native video, audio pipeline and advancing playback for {title}")


run("wait-for-device")
run("install", "-r", root / "app/build/outputs/apk/debug/app-debug.apk")
run("shell", "am", "force-stop", package)
run("logcat", "-c")
run("shell", "am", "start", "-n", package + "/.MainActivity")
wait_for(lambda: has_text("seasons listed"), "Native catalog did not load")
assert any(matches_id(n, "catalog_reload") and n.get("enabled") == "true" for n in nodes()), "D-pad reload action is missing"
assert not any(n.get("class") == "android.webkit.WebView" for n in nodes()), "Catalog must be native"
# The catalog starts focused on its saved season. Navigate from the actual
# selection; MOVE_HOME is not consistently handled by Android's ListView.
# Wake D-pad selection after a previous time-bar tap left Android in touch mode.
key(20)
selected_season = next(n for n in nodes() if n.get("selected") == "true" and re.fullmatch(r"Season \d+", n.get("text", "")))
number = int(selected_season.get("text").split()[-1])
key(*([19 if number > 12 else 20] * abs(number - 12) + [23]))
wait_for(lambda: has_text("14 episodes"), "Season 12 did not load all 14 episodes")
assert has_text("29 seasons"), "Expected the currently published 29-season selector"
# MOVE_END and MOVE_HOME on GridView verify the second page without tapping.
key(123)
assert has_text("The Ungroundable"), "The last Season 12 episode is missing; pagination broken"
key(122)
assert has_text("Tonsil Trouble"), "Could not return to the first episode"
print("PASS: native catalog, 29 seasons, complete 14-episode Season 12, D-pad browsing")
# First row, third column.
key(22, 22)
major = "68c79aa4-ecfd-11e0-aca6-0026b9414f30"
play_selected("Major Boobage", major)
def is_playing(episode_id):
    matches = re.findall(r"NATIVE_PLAYING episode=" + re.escape(episode_id) + r" value=(true|false)", logs())
    return matches[-1] == "true" if matches else None


key(85)  # Media play/pause. Assert actual Player callbacks, not a stale UI dump over moving video.
wait_for(lambda: is_playing(major) is False, "Remote pause failed", 10)
key(85)
wait_for(lambda: is_playing(major) is True, "Remote resume callback missing", 10)
before = position(major)
wait_for(lambda: position(major) > before + 1000, "Remote resume failed", 20)
key(90)  # Media fast-forward, ten seconds.
before_seek = position(major)
wait_for(lambda: position(major) >= before_seek + 9000, "Remote seek did not advance playback", 20)
print("PASS: native remote pause, resume, and forward seek")
# Paused playback permits a stable accessibility snapshot for the visible toolbar.
key(85)
wait_for(lambda: is_playing(major) is False, "Could not pause for toolbar test", 10)
assert has_text("Major Boobage") and has_text("S12 • E3"), "Player must display episode metadata"
assert not has_text("PICTURE:"), "Removed picture toggle is still present"
wait_for(lambda: has_text("NEXT EPISODE"), "Player did not resolve its next episode")
# Insets apply to transport chrome, not to the video/IMA surface.
width, height = map(int, re.findall(r"(\d+)x(\d+)", run("shell", "wm", "size"))[-1])
density = int(re.findall(r"\d+", run("shell", "wm", "density"))[-1]) / 160
transport_nodes = nodes()
for name in ("player_back", "player_next", "exo_settings", "exo_progress"):
    control = next(n for n in transport_nodes if matches_id(n, name))
    left, top, right, bottom = map(int, re.findall(r"\d+", control.get("bounds")))
    assert left >= 48 * density and right <= width - 48 * density, f"{name} exceeds horizontal safe area"
    assert top >= 27 * density and bottom <= height - 27 * density, f"{name} exceeds vertical safe area"
# Native transient settings must dismiss before the player's two-stage Back.
for _ in range(6):
    current = nodes()
    if any(matches_id(n, "exo_settings") and n.get("focused") == "true" for n in current):
        break
    key(22 if any(matches_id(n, "exo_subtitle") and n.get("focused") == "true" for n in current) else 20)
else:
    raise AssertionError("Native settings are not D-pad-reachable")
key(23)
assert has_text("Speed") and has_text("Audio"), "Native settings panel did not open"
key(4)
assert has_text("BACK TO EPISODES"), "Settings Back hid parent controls or left the player"
if any(matches_id(n, "exo_subtitle") and n.get("enabled") == "true" for n in transport_nodes):
    key(21)
    assert focused_id("exo_subtitle"), "Native captions are not D-pad-reachable"
    key(23)
    assert has_text("English") and has_text("None"), "Expected official English caption choice missing"
    key(4)
    assert has_text("BACK TO EPISODES"), "Caption panel Back hid parent controls or left the player"
    print("PASS: native settings/caption menus and panel-first Back")
else:
    print("PASS: native settings menu and panel-first Back; no enabled caption control in this stream")
# Back hides controls without leaving playback, then exits when already hidden.
key(4)
assert not has_text("BACK TO EPISODES"), "First Back did not hide player controls"
assert "PlayerActivity" in run("shell", "dumpsys", "activity", "activities").split("mResumedActivity:")[-1], "First Back left playback"
key(4)
wait_for(lambda: has_text("14 episodes"), "Second Back did not return to catalog")
assert has_text("Last watched: S12 • E3 · Major Boobage"), "Last episode was not saved"
assert focused_id("episode_grid"), "Returning playback did not restore grid focus"
assert any(matches_id(n, "episode_card") and n.get("selected") == "true"
           and "Major Boobage" in n.get("content-desc", "") for n in nodes()), "Playback returned to the wrong card"
wait_for(lambda: has_text("Up next · S12 • E4 · Canada On Strike"), "Wrong next-episode suggestion")
print("PASS: episode metadata, no Fit/Zoom toggle, two-stage Back, restored card focus and saved history")
# Replay Major to test the native next arrow using the remote.
key(122, 22, 22)
run("logcat", "-c")
play_selected("Major Boobage", major)
key(85)
wait_for(lambda: is_playing(major) is False, "Could not pause for next-button test", 10)
wait_for(lambda: has_text("NEXT EPISODE"), "Next arrow unavailable")
# Move above the transport controls to a toolbar button.
for _ in range(6):
    current = nodes()
    if any(n.get("focused") == "true" and (matches_id(n, "player_back") or matches_id(n, "player_next")) for n in current):
        break
    key(19)
else:
    raise AssertionError("Player toolbar is not reachable with the remote")
if focused_id("player_back"):
    key(22)
assert focused_id("player_next"), "Next arrow is not remote-accessible"
key(23)
canada = "68c965dc-ecfd-11e0-aca6-0026b9414f30"
wait_for(lambda: f"NATIVE_ADVANCE episode={canada}" in logs(), "Next arrow did not select Canada")
wait_for(lambda: f"NATIVE_FIRST_FRAME episode={canada}" in logs(), "Next episode did not render")
wait_for(lambda: position(canada) > 1500, "Next episode did not play")
assert "NATIVE_ERROR" not in logs(), "Next episode failed"
key(85)
wait_for(lambda: is_playing(canada) is False, "Could not pause Canada", 10)
assert has_text("Canada On Strike") and has_text("S12 • E4"), "Metadata did not update after Next"
# Visible Back button remains a direct, remote-accessible way back to catalog.
for _ in range(6):
    current = nodes()
    if any(n.get("focused") == "true" and matches_id(n, "player_back") for n in current):
        break
    key(21 if any(n.get("focused") == "true" and matches_id(n, "player_next") for n in current) else 19)
else:
    raise AssertionError("Visible Back button is not reachable with the remote")
key(23)
print("PASS: remote next arrow starts Canada, updates metadata; visible Back returns to catalog")
wait_for(lambda: has_text("Last watched: S12 • E4 · Canada On Strike"), "Last watched did not update to Canada")
wait_for(lambda: has_text("Up next · S12 • E5"), "Next suggestion did not advance after Canada")
prefs = ET.fromstring(run("shell", "run-as", package, "cat", "shared_prefs/playback.xml"))
saved = json.loads(prefs.find("./string[@name='lastEpisode']").text)
assert saved["id"] == canada and saved["positionMs"] > 1500, "Actual playback position was not persisted"
run("shell", "am", "force-stop", package)
run("shell", "am", "start", "-n", package + "/.MainActivity")
wait_for(lambda: has_text("14 episodes"), "Catalog did not reload after process restart")
assert has_text("Last watched: S12 • E4 · Canada On Strike"), "History did not survive process restart"
# Season list first row -> the Continue button above it.
key(122, 19)
assert focused_id("continue_watching"), "Continue is not reachable with the remote"
run("logcat", "-c")
key(23)
wait_for(lambda: f"NATIVE_FIRST_FRAME episode={canada}" in logs(), "Continue did not launch native playback")
wait_for(lambda: position(canada) >= max(1000, saved["positionMs"] - 5000), "Continue restarted instead of resuming saved position")
wait_for(lambda: f"NATIVE_AUDIO_STARTED episode={canada}" in logs(), "Resumed episode has no native audio")
assert "pcm_writei failed" not in run("logcat", "-d", "-s", "android.hardware.audio@7.1-impl.ranchu"), "Emulator audio output failed"
print("PASS: durable last-watched history, updated next episode, remote Continue after process restart")
subprocess.run([sys.executable, str(root / "scripts/check-emulator-audio.py")], check=True)
# Seek through the normal player time bar to the last second, then let content end.
# This does not replay a captured stream or bypass the provider's ad handling.
key(85)
wait_for(lambda: is_playing(canada) is False, "Could not pause for end-of-episode test", 10)
wait_for(lambda: f"NATIVE_NEXT episode={canada} next=" in logs(), "Next episode lookup did not complete")
bar = next(n for n in nodes() if n.get("resource-id", "").endswith(":id/exo_progress"))
x1, y1, x2, y2 = map(int, re.findall(r"\d+", bar.get("bounds")))
density = int(re.findall(r"\d+", run("shell", "wm", "density"))[-1])
run("shell", "input", "tap", x2 - round(8 * density / 160) - 1, (y1 + y2) // 2)
key(85)
wait_for(lambda: "NATIVE_ADVANCE episode=" in logs().split(f"NATIVE_FIRST_FRAME episode={canada}")[-1], "Content ended without autoplay", 50)
wait_for(lambda: "S12 • E5" in run("shell", "run-as", package, "cat", "shared_prefs/playback.xml"), "Autoplay did not save episode 5", 30)
key(85)
assert has_text("S12 • E5"), "Autoplay did not update the player title to episode 5"
key(85)
assert "NATIVE_ERROR" not in logs(), "Autoplay encountered a playback error"
print("PASS: automatic advance after Canada ends; E5 metadata and history updated; E5 left playing")
