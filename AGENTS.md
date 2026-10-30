# Working on South Park TV

## Start here

Read `docs/design-system.md` before changing UI. It is the implemented contract; `docs/design/research/` is supporting research, not a feature backlog. Read `README.md`, `VALIDATION.md` and `docs/site-protocol.md` for behavior and evidence limits.

Native Android TV: Java Views, ListView/GridView and Media3/IMA. Reuse resource tokens, `TvUi` and `EpisodeCard`. Keep network/history/provider logic out of presentation helpers. Do not introduce Compose, another UI/player framework or a dependency without a demonstrated need and approval.

## Do not regress

- Official-source URL gates, fresh provider sessions, ads, authentication, DRM and regional restrictions. No alternate stream source, captured credentials or bypass.
- All season pages, clip-only empty states, Continue/resume/completion and sequential next suggestions.
- Always Fit; remote pause/resume/ten-second seek; native captions/settings; direct toolbar Back plus two-stage remote Back.
- Immediate manual Next and natural content-end autoplay; no skipping locked entries or unrequested countdown.
- Async cancellation/generation guards, recycled-image binding keys, and save/release before changing episode identity.
- Focus versus selected season, D-pad-only Reload/Retry, dialog/caller focus recovery and safe bounds.

Use `strings.xml` and dp/sp resources; no activity-local palettes or raw app dialogs. Extend existing components only for real shared uses. Test behavior against independent expected values, not a reimplementation of the production algorithm.

## Verification

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
python3 scripts/check-design-tokens.py
ANDROID_SERIAL=emulator-5554 python3 scripts/smoke-test.py
ANDROID_SERIAL=emulator-5554 python3 scripts/check-tv-ui.py
```

Live checks need the official network and an installed/current APK; smoke installs it. UI checks temporarily change only emulator font/animator settings and restore them in `finally`. They leave the catalog open. The smoke test includes waveform validation: callbacks alone do not prove sound. Describe any skipped/failed checks honestly; emulator success does not establish physical-TV, ad-break, DRM, caption or accessibility support.

## Environment and safety

This workspace currently has no Git repository. Preserve a source backup/diff before broad edits; one writer per cwd. Do not delegate implementation without explicit authorization.

Use **only `emulator-5554`** for current validation. Do not connect to or test the physical TV unless newly authorized. Physical installation also requires its current Wireless debugging connection address.

The tested emulator launcher is `scripts/run-tv-emulator.sh`: CoreAudio, host GPU and `-feature -HardwareDecoder`. **Never disable VirtioSndCard** on this ARM64 AVD: NullSink is silent failure. The video software-decoder workaround is emulator-only; keep physical-TV hardware decoding. Do not overwrite the normal SDK, change global Mac audio or stop unrelated processes/devices.

Never commit/publish HAR files, tokens, signed media URLs or sensitive log/recording data. Keep temporary recordings ephemeral. Existing `/tmp` emulator/baseline artifacts are not durable dependencies.
