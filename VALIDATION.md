# Native client validation

## Current version: 0.5.1 (versionCode 6)

Subtitle-only presentation update: 22sp regular soft-white text, drop shadow, transparent background/window. Existing safe margins, native CC selection and transport avoidance remain. Enabled Android accessibility caption settings override this default.

- Build, 21 Java tests and lint pass: **0 errors / 11 warnings**. All 3 design-token checks pass.
- Full emulator smoke and TV UI checks pass again, including manual Next, natural autoplay, history/Continue, caption/settings menus, both Back paths, measured non-silent audio (**-22.1 dBFS**), focus/selection, empty season, 1.2 font scale and zero animator scale. A transient UIAutomator null root recovered through fresh-snapshot retry.
- Selected English manually on S12 E5 and visually inspected multiple actual caption cues: smaller soft-white letters, shadow, no black boxes. [Softer captions](docs/screenshots/subtitles-soft.png).
- Temporarily enabled Android captions with preset 0 (white on black) and font scale 1.35, restarted playback and selected English manually. Confirmed the larger boxed system preference wins: [system-caption override](docs/screenshots/subtitles-system-preference.png). Restored the original secure settings in `finally`, restarted the app and reran the UI checks. Enabling Android captions alone did not automatically select the English track; track auto-selection was not changed by this update.
- Reproduce the visual check after the full smoke: select English through CC on S12 E5, play several dialogue cues, pause, and hide/show controls. Compare the softer default against the enabled Android caption preset/font-scale after reopening playback; save and restore any changed emulator settings. Other caption formats, bright/dark scene coverage and physical-TV viewing distance remain unverified.
- This APK has only been validated/installed on the emulator. The physical TV received v0.5.0 earlier; v0.5.1 has not yet been installed there.

## v0.5.0 redesign baseline (versionCode 5)

Validated on **emulator-5554 only**, Android TV API 36 ARM64, 1920×1080 at density 320. The physical TV subsequently received this version but was not used for playback testing.

- Build, all **21 Java tests** and lint pass; lint has **0 errors / 11 warnings** (dependency/target notices, vector-banner sizing, orientation/backup metadata and unused spacing tokens).
- `python3 scripts/check-design-tokens.py`: **3 checks pass**, including text contrast ≥4.5:1, focus accent ≥3:1 and app minimum target heights.
- Full `ANDROID_SERIAL=emulator-5554 python3 -u scripts/smoke-test.py` passes against the new APK: complete Season 12 pagination, playback/pause/resume/seek, metadata, no picture toggle, two-stage Back, restored Major Boobage card focus, manual Next to Canada, direct toolbar Back, process restart/Continue, measured audio **-22.1 dBFS**, and natural content-end autoplay into S12 E5. It uses the normal time bar, not an injected ended event. Native settings and English caption-choice menus open by D-pad, and Back dismisses each panel before hiding the parent controls.
- `scripts/check-tv-ui.py` passes focus versus selected season, safe focused-card/action bounds, positive dialog focus and cancel return, D-pad-only Reload, no reload focus theft, the published Season 29 empty state/recovery, and **1.2 font scale with animator scale zero**. Temporary emulator settings are restored. UI checks leave the catalog open after stopping playback. A transient UIAutomator null-root diagnostic recovered through the fresh-snapshot retry; stale dumps are rejected.
- Inspected emulator screenshots for catalog focus, simultaneous season selection/different focus, details, empty state, scaled type and full-frame player chrome. Transport bounds are checked inside app safe insets; video/IMA remain full bleed. After selecting English on S12 E5, visually confirmed actual caption text with controls visible and hidden. Caption viewport moves above visible transport, then returns to the safe bottom when chrome hides. Artwork is placeholder-reset/key-guarded on reuse; no claim of a fault-injected delayed-image test.
- `Catalog.java`, `SiteClient.java` and `WatchHistory.java` are byte-identical to the pre-redesign source archive. Player changes are presentation/resources and next-action accessibility labels; session, ad gating, save/release and autoplay logic are retained.
- [Design-system contract](docs/design-system.md), [agent guidance](AGENTS.md) and [research archive](docs/design/research/README.md) are persisted in the project. Research shelf/countdown/scale suggestions were not silently added.

Visual evidence: [catalog](docs/screenshots/redesign-catalog.png), [different season focus/selection](docs/screenshots/redesign-season-focus.png), [details](docs/screenshots/redesign-details.png), [empty season](docs/screenshots/redesign-empty-season.png), [1.2 scale](docs/screenshots/redesign-font-scale-120.png), [player](docs/screenshots/redesign-player.png), [English captions with chrome hidden](docs/screenshots/redesign-captions-clean.png). These are emulator observations, not accessibility or physical-TV certification.

## Preserved behavior / earlier validation

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- Twenty-one local tests: nine catalog/security tests, eight history/completion/recommendation tests and four shared next-episode resolver tests. Lint has zero errors; warnings remain.
- `python3 scripts/smoke-test.py` on Android TV API 36 ARM64, 1920×1080.
- Native catalog contains no website WebView.
- Native season selector shows the current 29 published entries.
- Season 12 loads all 14 episodes. The remote reaches the paginated last episode, The Ungroundable, and returns to the first episode.
- Major Boobage and Canada on Strike render native Media3 video frames and advance playback.
- Both episodes report native audio-position advancement, but the initial audio acceptance check was insufficient: NullSink can advance positions while discarding all sound. See the corrected audio validation below.
- Remote pause, resume and ten-second forward seek pass against actual native Player callbacks/progress.
- Visible **Back to episodes** button is reachable with the D-pad and returns to the catalog.
- Video stays in Fit mode without a picture toggle. Current controls display the episode title and season/episode label.
- Back hides controls on the first press and returns to the catalog on the second. The separate visible Back button remains remote-accessible.
- Remote **Next episode ›** advances Major Boobage to Canada On Strike and updates the displayed metadata and saved history.
- After seeking through the normal time bar to Canada's last second, natural content end autoplays S12 E5 and updates metadata/history. No direct stream replay or ad bypass is used.
- Last-watched metadata and content position update after actual video renders.
- After Major Boobage, Up next suggests Canada on Strike. After Canada, it advances to S12 E5.
- History survives force-stopping and reopening the app without clearing app data. The remote reaches Continue, starts native playback, resumes the saved content position and starts native audio.
- The complete version 0.4 smoke test passed, including actual non-silent audio at -22.3 dBFS. It leaves S12 E5 playing on the emulator. The physical-device update is pending a new Wireless debugging connection port.
- Merged manifest retains only INTERNET, ACCESS_NETWORK_STATE and WAKE_LOCK. Advertising-ID and AdServices permissions are removed.

## Corrected audio diagnosis and validation

The initial emulator could decode an audio track but its Android audio HAL repeatedly logged:

```text
pcmWrite:259 pcm_writei failed with 'cannot read/write stream data: I/O error'
```

Android's output track was active and unmuted, but the emulator's virtual audio device was failing. Its media volume was also just 3/15. Restarting with `-audio coreaudio` alone did not resolve the write failures.

The earlier `-feature -VirtioSndCard` workaround was wrong. It removed the ARM64 guest's sound card, so the HAL logged `failed to create alsa sink ... creating NullSink instead`. This avoided write errors by discarding audio, not by fixing it. The original test failed to detect that fallback.

An eight-second emulator recording while the episode was actually playing confirmed digital silence, peak -91.0 dBFS. The Mac's default speakers were unmuted at 75% volume; Android media volume was 11/15. The fault was inside the emulator output path, not just a muted volume control.

Installed the official stable emulator 37.2.12 separately under `/tmp/southpark-emulator-37`, leaving the normal SDK installation intact. The archive's SHA-1 matched Google's repository metadata: `3af4fe44ce82b3d88ae5678a53735f27ad729c15`. Relaunched the owned AVD with its virtual sound card enabled, `-audio coreaudio -gpu host`, `hw.audioOutput=yes` and `hw.audioInput=no`.

The guest now has real `/dev/snd/controlC0` and `pcmC0D0p` devices. White People Renovating Houses plays without NullSink or PCM hardware failures. Two independent eight-second recordings measured non-silent audio, peaks -18.2 and -18.0 dBFS. `python3 scripts/check-emulator-audio.py` passes and deletes its temporary recording.

The main smoke test now rejects NullSink/device-open failures and invokes the waveform check. Subsequent complete v0.4 and v0.5 smoke runs passed with this repaired emulator, including recorded waveform verification. No APK change was needed for the emulator repair. The user subsequently confirmed audible Mac speaker output. Physical-TV output remains untested.

## Corner-crop reproduction and fix

Reproduced the reported corner magnification on White People Renovating Houses. It persisted with the picture button explicitly set to Fit, so it was not normal Zoom behavior or merely a provider-cropped source. Logcat identified `c2.goldfish.h264.decoder` as the active virtual hardware decoder.

Restarted only the owned emulator with the same working audio setup and `-feature -HardwareDecoder`. The active video decoder changed to `c2.android.avc.decoder`. The same bedroom sequence now shows the full scene instead of just an enlarged speaker and wall. [Before](docs/screenshots/corner-crop-before.png) and [after](docs/screenshots/corner-crop-after.png) are nearby frames from that sequence, not pixel-identical timestamps.

Observed a live adaptive switch from 768×432 to 1920×1080 after resuming playback. Visually checked the full frame at both resolutions. Actual emulator audio still passes the eight-second waveform check, peak -18.2 dBFS. The current episode is left playing. `scripts/run-tv-emulator.sh` preserves the tested flags for future launches; physical-TV hardware decoding is unchanged.

## Site inspection and picture limits

The site survey found 331 full-episode cards across Seasons 1–28. Season 29 currently offers clips only; the client shows a full-episode empty state. Counts and availability may change.

Native playback obtains new short-lived credentials from the episode's official service, then uses Google's IMA DAI integration. No captured stream token is embedded in source or app resources.

The player explicitly applies Fit and scale-to-fit. Version 0.3's optional Fit/Zoom button has been removed in 0.4; video now always stays in Fit. Sample streams adapt between sizes including 768×432 and 1920×1080 with square pixels, both 16:9 delivered frames. The player cannot restore picture edges already cropped by the provider's encode or change a close-up in the source footage.

## Not verified

- Playback of every catalog entry or an entire episode to completion.
- Ad delivery/transitions. The tested portions did not serve ads; the implementation retains the ad-aware SDK path.
- Caption formats/episodes beyond the one English S12 E5 sample, system caption-preference changes, alternate audio tracks, physical-TV caption/audio output, or DRM compatibility.
- Regions other than the current host/emulator network.
- Long-session credential expiry and network reconnection behavior.
- Live cross-season next-episode selection. Same-season recommendations and pure next-entry boundary behavior are covered.
- TalkBack traversal/announcements, RTL or translated long strings, font scales above 1.2, other display geometries and physical-TV viewing distance/overscan.
- Redesigned Retry behavior under injected network failures. Controls/provider paths are retained, but source inspection is not runtime verification.
- Overscan safety of native Media3 popup menus: runtime menus work and dismiss correctly, but can extend to the screen edge beyond the app's toolbar/timebar safe insets. Physical-TV validation remains necessary.

## Captions and watched status change

The player retains text-track preferences across player replacement for Next, content-end autoplay, retry and stop/start within the same activity instance. Native track overrides become language/role preferences rather than stale track groups. Missing languages remain subject to Media3 selection and the provider tracks.

Watched flags use the existing completion threshold, survive partial rewatch and have a manual details-dialog toggle. Unit tests cover durable flags across new history entries, manual changes, completion migration and unchanged resume positions.

Live caption transitions and the new details action remain unverified: emulator-5554 was unavailable for this change. No physical TV was used.
