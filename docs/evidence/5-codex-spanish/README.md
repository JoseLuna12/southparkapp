# Spanish connection evidence

Current debug APK installed on emulator-5554, Android TV API 36 ARM64 at 1920x1080, density 320. Only this emulator was used.

- 5-codex-04-spanish-choice.png: Spanish focused and selectable in the language dialog.
- 5-codex-05-spanish-official-error.png: Spanish selected, actual official catalog request fails closed, empty catalog and visible Retry.
- 5-codex-06-english-recovery.png: D-pad return to English loads its catalog again.
- network-evidence.txt: source-tagged catalog requests and rejected redirect host, filtered from actual emulator logcat. No tokens, signed media URLs or sensitive network data.
- https-evidence.txt: real curl requests from the Mac, final status and URL only, with exact invocation. No cookies, headers or request identity changes.

Official provenance: https://southpark.cc.com/ exposes an Episodios en Español navigation link to https://southpark.cc.com/es/seasons/south-park. The indexed official catalog exposes /es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1 and a legal link to https://www.southpark.lat/legal/351njy/terminos-de-uso. Indexed metadata is discovery evidence only. Mac HTTPS evidence is the availability result.

D-pad steps: navigate Up from seasons to Reload, Left to the language button, OK, Down to Spanish, OK. Independent UIAutomator checks verify the Spanish button is focused, Spanish label persists on the catalog, an error is visible, and no episode_card from English survives. Force-stop and start the app; Spanish remains selected. Open language dialog again; Spanish initially has focus. Up and OK choose English, whose real cards load again. Captures visually inspected.

Spanish requests https://southpark.cc.com/es/seasons/south-park. A redirect to www.southparkstudios.com is rejected by the source gate. Separate curl checks follow that public redirect and observe 404. This is not proof of geoblocking specifically, nor successful Spanish catalog/playback or audible Latin American dubbing. No source/CDN bypass was attempted.

Validation: assembleDebug, 27 JVM tests, lintDebug and 3 token checks pass. Full playback/audio smoke and full TV UI regression suites were not rerun. Older screenshots in the sibling language directory record the previous disabled-Spanish UI and are historical.
