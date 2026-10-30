#!/usr/bin/env bash
# Tested macOS TV setup. Keep the real sound card; avoid the virtual H.264 crop bug.
# SOUTHPARK_EMULATOR can point to an updated SDK's emulator executable instead.
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-/tmp/southparktv-avds}"
exec "${SOUTHPARK_EMULATOR:-/tmp/southpark-emulator-37/emulator/emulator}" \
  -avd "${1:-SouthParkSmoke}" -no-snapshot -no-boot-anim \
  -audio coreaudio -gpu host -feature -HardwareDecoder
