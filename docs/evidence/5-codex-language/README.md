# Language UI review

Captured from the current debug APK on emulator-5554, Android TV API 36 ARM64, 1920x1080, density 320. Dedicated SouthParkReview AVD uses the installed SDK image with CoreAudio, host GPU and HardwareDecoder disabled via scripts/run-tv-emulator.sh. No physical TV was used.

- 5-codex-01-english-catalog.png: English catalog, Season 12, 14 episodes and 29 seasons listed.
- 5-codex-02-language-selector.png: language dialog, English selected and focused, Spanish unavailable and disabled. No geographic profile names are visible in the selector.
- 5-codex-03-english-confirmed.png: catalog after confirming English, with focus restored to the language button.

D-pad checks used adb input keyevent only. From the season list, move Up to the first season and then Reload, Left to the language button, and OK to open. UIAutomator independently confirmed language_english focused and language_spanish disabled. Back returned focus to catalog_source. Open again and confirm English: the dialog closes and focus returns to catalog_source. Screenshots were visually inspected. English catalog loads all 14 Season 12 cards; Spanish has no catalog mode because its official source is unverified.

assembleDebug, all 26 JVM tests, lintDebug and 3 design-token checks pass. This session did not rerun the full playback/audio smoke or full TV UI regression suite. Screenshots establish layout and the targeted navigation only, not playback, dubbing, ads or DRM support.

Live loading also caught rejection of percent-encoded colons in official pagination IDs. The URL gate now permits these IDs while rejecting encoded dot, slash and backslash path escapes. An explicit expected pagination URL is covered in RegionalSourceTest.
