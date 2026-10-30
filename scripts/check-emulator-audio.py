#!/usr/bin/env python3
"""Fail if a playing emulator produces silent output. Requires adb and ffmpeg.

Start an episode before running. Captures eight seconds through the emulator's
own recorder, measures its audio samples, and deletes the temporary recording.
Does not capture the Mac microphone or change host audio settings.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
adb = str(sdk / "platform-tools/adb")
ffmpeg = shutil.which("ffmpeg")
if not ffmpeg:
    raise SystemExit("ffmpeg is required to check actual emulator audio samples")


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT, timeout=30)


hal = run(adb, "logcat", "-d", "-s", "android.hardware.audio@7.1-impl.ranchu")
if re.search(r"pcm_writei failed|creating NullSink|failed to create alsa sink|pcm_open failed", hal):
    raise SystemExit("FAIL: emulator audio hardware failed or fell back to NullSink. Player callbacks alone do not prove sound.")

with tempfile.TemporaryDirectory(prefix="southpark-audio-check-") as directory:
    recording = Path(directory) / "output.webm"
    started = False
    try:
        response = run(adb, "emu", "screenrecord", "start", "--time-limit", "8", str(recording))
        if "OK" not in response or "KO:" in response:
            raise RuntimeError("Emulator recorder did not start: " + response)
        started = True
        time.sleep(9)
        analysis = run(ffmpeg, "-hide_banner", "-i", str(recording), "-vn", "-af", "volumedetect", "-f", "null", "-")
        peak = re.search(r"max_volume: (-?[\d.]+|inf|-inf) dB", analysis)
        if not peak or float(peak.group(1)) <= -70:
            raise RuntimeError("Emulator output is silent or too quiet. Ensure the episode is playing during this check.")
        print(f"PASS: actual emulator audio samples are non-silent, peak {peak.group(1)} dBFS")
    finally:
        if started:
            # A time-limited recording normally stops itself; explicitly stop on failures too.
            subprocess.run([adb, "emu", "screenrecord", "stop"], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=15, check=False)
