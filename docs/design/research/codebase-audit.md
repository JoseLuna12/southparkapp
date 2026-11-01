# Code Context

Read-only UI/UX audit of the supplied v0.4 tree. No app files were changed, and no build, emulator, device, install, or live smoke test was run. The existing behavior/test status below is taken from source and the task context; it is not a fresh validation run.

## Files Retrieved

1. `app/src/main/java/com/unofficial/southparktv/MainActivity.java` (lines 32–58, 59–148, 150–217, 219–259, 261–301, 303–347) — catalog screen, focus, history actions, episode cards, and async artwork.
2. `app/src/main/java/com/unofficial/southparktv/PlayerActivity.java` (lines 43–80, 82–150, 152–187, 189–250, 252–317, 319–354, 356–386) — player presentation, native controls, toolbar, next/autoplay, errors, persistence, and key/lifecycle behavior.
3. `app/src/main/java/com/unofficial/southparktv/Catalog.java` (lines 17–50, 51–91, 93–126) — catalog models/parsing, locked detection, and official-page URL validation.
4. `app/src/main/java/com/unofficial/southparktv/WatchHistory.java` (lines 10–39, 41–63) — durable last-watched record and completion/resume semantics.
5. `app/src/main/java/com/unofficial/southparktv/SiteClient.java` (lines 22–68, 70–106, 108–148) — all-season/episode/pagination retrieval, trusted playback session/ad flow, and HTTPS/host/redirect allowlist. Relevant to preserving provider restrictions while restyling.
6. `app/src/main/AndroidManifest.xml` (lines 1–20) — Leanback launch, TV orientation/features, permissions, and platform theme.
7. `app/build.gradle` (lines 1–31) — Java 17, Media3 dependencies, version 0.4.0; no Compose or AppCompat dependency.
8. `app/src/main/res/drawable/icon.xml` (lines 1–4) and `app/src/main/res/drawable/banner.xml` (lines 1–4) — only app resources found. There are no layout, values, style/theme, color, dimension, font, or focus-selector resources under `app/src/main/res`.
9. `scripts/smoke-test.py` (lines 1–76, 79–100, 101–134, 135–191, 192–208) — live TV smoke helper and current UX/functional assertions.
10. `README.md` (lines 3–47, 49–68) — user-visible behavior, official-service/privacy boundaries, and stated tests.
11. `VALIDATION.md` (lines 3–23, 25–43, 45–68) — passed/not-verified matrix and emulator caveats; note its qualification about the latest amended smoke run.

## Key Code

### Existing UI and interaction seams

- Catalog shell and all widgets are built imperatively in `MainActivity.onCreate()` (59–128). It applies fullscreen, a 32dp page inset, a fixed 170dp season sidebar, header/history row, 3-column `GridView`, and footer. Colors, sizes, text, layout spacing, card backgrounds, and focus ring are literals/helpers in this class (34–37, 130–148). The history row is initially gone and holds Continue/Watch again and Up next buttons (70–89).
- Season-list rows and async initial selection are in 150–179. Startup restores preference `season` (default 12), requests focus on the selected season, then loads its episodes. User-selected seasons load via 181–208; all episodes are fetched asynchronously, saved, and the grid is focused on success only for a user-triggered non-empty load. Empty-season messaging and retry dialog are in 197–216.
- History rendering and recommendation behavior are in 219–259. Continue/Watch again starts the stored episode; Up next resolves asynchronously through `SiteClient.next`, opens the next-episode details dialog, and can be in loading/unavailable/no-next states.
- Card click → details dialog → Watch episode or official browser is in 261–283. Locked items do not launch the in-app player. Menu reload and grid-focused Back behavior are in 285–301.
- `EpisodeAdapter.getView()` is lines 303–346. It creates a new `LinearLayout` card each bind, displays episode label/duration/title, and downloads an image asynchronously into an in-memory 12 MiB `LruCache`.
- Player root/controller customization is in `PlayerActivity.onCreate()` (82–150): fullscreen/keep-screen-on `PlayerView`, Fit mode, built-in subtitle button, hidden inactive playlist arrows, custom top Back/episode/Next toolbar, spinner, controller visibility callback, and initial focus. The toolbar focus listener disables its timeout while toolbar controls hold focus (132–147).
- Player transport/playback and lifecycle are in 189–250 and 356–386. Media3 keeps its native D-pad transport/settings/caption controls; dedicated media keys are explicitly routed to `PlayerView`, while D-pad keys go through normal view focus dispatch. Back hides visible controls first, then finishes playback on the next Back; the toolbar Back button finishes immediately. Pause/stop save progress and release player/ads.
- Next resolution and manual/automatic transition are in 252–317. Next uses a separate fresh official session; it does not use an ExoPlayer playlist. A locked next item is shown as unavailable and is not played/skipped around. A next lookup failure exposes Retry next; content end advances only if a resolved unlocked entry exists.

### Data, official-site, and persistence seams

- Catalog types and official data base are `Catalog.java:17–25`. Seasons sort numerically and episode entries are marked locked if the official `lockedLabel` is present or the URL is not `/episodes/` (`35–48`, `72–91`). Season 29’s clips-only empty state is intentionally handled (`51–70`). `officialPage()` resolves only HTTPS `www.southparkstudios.com` pages and rejects userinfo/unsupported ports (`103–109`).
- `SiteClient.episodes()` follows all public pagination pages with a cap/repetition checks (`46–68`). `SiteClient.next()` picks the next listed episode, and only crosses to the next published season when current is the final listed episode (`29–39`).
- Playback is restricted to unlocked episodes, non-auth-required episode metadata, HTTPS `topaz.paramount.tech/topaz/api/`, fresh UUID session IDs, and supported Google DAI HLS (`SiteClient.java:70–87`). Supplied ad parameters are preserved; unresolved macros are omitted and privacy flags are added without fabricating consent (`88–105`). Network redirects must remain HTTPS on the explicit South Park/Paramount host allowlist (`108–148`). Do not change these gates as part of UI work.
- Player creates `ImaServerSideAdInsertionMediaSource` and passes the supplied CMS/video/auth/ad parameters through Media3’s IMA builder (191–202, 240–248). The player always uses Fit/scale-to-fit (`PlayerActivity.java:99–103`, 201–202); no picture-mode toggle should be reintroduced.
- `WatchHistory.snapshot()` marks complete on natural end, or when the duration is at least one minute and the playhead has reached the earlier of 80% or two minutes remaining (never before halfway). It saves position zero for completed entries. It stores one JSON `lastEpisode` plus per-episode positions and validates saved episode/season paths through `Catalog.officialPage()` when decoding.

### Current focus/navigation inventory

**Catalog screen** (`MainActivity.java:59–148, 150–175, 181–216, 219–295`):

1. After season data arrives, startup selects the saved season (12 by default), focuses the season list, and loads episodes. Season-list OK invokes season loading; the GridView is reached through ordinary Android focus navigation (no explicit season-list-to-grid focus ID). On a user season change, the non-empty grid gets focus after the network response; reselecting an already-loaded current season immediately focuses the grid.
2. D-pad navigation within the three-column grid is ordinary `GridView` navigation. OK opens the episode detail `AlertDialog`; its positive action plays an unlocked episode or opens the official page for a locked one; negative Back dismisses. Main-screen error dialogs expose Retry/Cancel.
3. The top history row appears after a saved history entry exists. Continue watching/Watch again starts the stored episode directly. Up next opens the suggested episode’s detail dialog. Both history buttons explicitly route Down to the grid (`115–116`); other directions are framework-derived and not explicitly mapped.
4. Back while grid-focused explicitly requests season-list focus (`292–294`); otherwise it delegates to Activity Back. Menu reloads the selected season or retries season loading (`285–290`). There is no explicit focus graph for all controls or a grid selection restoration contract.
5. Focus cues are ListView/GridView selectors using one transparent 3dp accent stroke (`142–148`); season selected-state is a separate row background (`162–168`). The cards themselves use a flat background, without an explicit focus-state background/scale/elevation.

**Player screen** (`PlayerActivity.java:82–150, 356–386`):

1. Startup requests focus on `PlayerView`; controller auto-show is enabled with a 4s timeout. The custom toolbar is visible only while the Media3 controller is visible. The PlayerView has only an explicit upward next-focus target to the Back toolbar button. Back and Next are focusable; the title is not a button.
2. Normal D-pad focus dispatch is deliberately not sent to PlayerView first, so the custom toolbar remains reachable. Media play/pause/play/pause/fast-forward/rewind/stop keys are explicitly sent through PlayerView. D-pad transport, captions, settings, and media controls remain Media3’s native controls when offered.
3. Focusing Back or Next shows the controller and prevents its auto-hide; when neither is focused, timeout returns to 4 seconds. Back hides controls if visible and exits only when already hidden; activating the visible Back to episodes button exits directly. During Next, focus returns to PlayerView before reloading the new episode.
4. Error dialog has Retry, Back to episodes, Official site, and cancel-to-finish paths (`319–333`). A loading spinner, disabled/loading Next state, no-next state, next-unavailable state, and retry-next state are all user-visible.

### Resource/theme/layout deficiencies relevant to a redesign

- The only `res` files are two tiny launcher vector drawables (icon/banner), both a dark rectangle with a green play triangle. No application UI/layout/theme/token resources exist. Manifest line 11 points directly at framework `Theme.Material.NoActionBar`.
- Main screen’s palette is four inline constants; additional backgrounds and text colors are separate RGB literals. Player independently hardcodes black/white and a translucent black toolbar. Main and player buttons use platform defaults, while text and cards are manually styled. This makes visual changes inconsistent and state behavior difficult to reuse.
- Text sizes (12–32sp) and geometry (e.g. 170dp fixed sidebar, 3 columns, 32dp page insets, 48dp minimum controls) are scattered and not expressed as TV/readability/layout tokens. The small metadata/status/footer labels and fixed 3-column/fixed-sidebar geometry need deliberate 10-foot and 720p/1080p review rather than a blind global enlargement.
- There are no XML layouts or reusable screen/card components; screen trees and card subtrees live in Activity code. There are no stateful focus/selected/disabled drawables or shared TextAppearances/styles. Current selector affordance does not give all cards and toolbar buttons an intentionally consistent, prominent focus state.
- System `AlertDialog` and platform `Button` visual behavior differs from the custom catalog cards. Platform theme defaults can uppercase/stylize controls differently (the smoke test currently observes uppercase button text).
- The app is already Java 17, native Android Views, and Media3; a Compose/AppCompat migration is not needed to add a small reusable system. Preserve native Media3 controller behavior and caption/settings affordances rather than replacing it with a custom transport UI.

## Architecture

Keep the existing separation: `MainActivity` and `PlayerActivity` own screen state and user actions; `Catalog`/`SiteClient` remain the source of official-site data and provider policy; `WatchHistory` remains persistence logic; Media3/IMA remain the player. Introduce presentation-only shared Views/resources and bind the existing models/actions into them. Do not move networking/session/persistence policy into styling classes, and do not change URL gates or ad flow.

A practical small Java-Views system:

- **Resources:** `res/values/colors.xml` for semantic palette tokens (background/surface/elevated surface/text/accent/focus/locked); `dimens.xml` for safe insets, spacing, card gutters, control heights and type sizes; `styles.xml`/`themes.xml` for app theme, TextAppearances and Button defaults. Keep a dark TV-first palette explicit. Add selector/background drawables with separate `state_focused`, `state_selected`, `state_enabled`, and pressed states; distinguish season selection from current focus. Add an artwork placeholder drawable. If using layouts, add `activity_catalog.xml`, a reusable `item_episode.xml`/card view and optionally a player toolbar layout; the PlayerView can remain managed by Java if that avoids awkward Media3 integration.
- **Shared Java presentation:** a compact package-private `TvUi`/`TvDesignSystem` for density/token access and common typography/control/focus styling; reusable `TvButton`/button factory, `SeasonRow` and `EpisodeCard`/holder, plus reusable status/empty/error presentation. Keep model/action callbacks passed in by Activities. Use ordinary Java Views and `LayoutInflater`; retain `ListView`/`GridView` unless a specific demonstrated requirement justifies an additional collection dependency.
- **Adapters/screens:** move season and episode adapters out of the Activity or keep them nested but bind common components/styles. A card component should own a consistent focus background, image placeholder/crop, metadata/title typography, content description, and locked/selected state. Keep the current row/grid semantics, history actions, details dialogue choices, and all player lifecycle logic.
- **Media3 integration:** theme the existing PlayerView/controller as supported by Media3 and style the custom top bar/buttons with the same token/component resources. Keep built-in seek controls, subtitles, settings, ad restrictions, and the Fit-only picture behavior. Use shared common widgets for dialog/error/retry surfaces where feasible without masking native controller accessibility.
- **Focus/accessibility contract:** every focusable control gets a clear focused state and stable semantic label; selected season, disabled Next, and focused item are visually distinct. Define only necessary directional focus IDs to prevent traps and preserve a predictable route between history, seasons, grid, dialog actions, toolbar and Media3 controls. Do not make card text/image children separately focusable if the whole card is the action. Keep card content descriptions informative (episode/season and unavailable/locked state where relevant), and keep button labels/click actions discoverable.

## Async artwork and focus/recycling risks

- `EpisodeAdapter.getView(position, recycled, ...)` ignores `recycled` and always allocates a new card/image/text subtree (`MainActivity.java:307–328`); season adapter also ignores its recycled parameter (`162–169`). This avoids the classic wrong-image-on-a-reused-view bug today, but creates avoidable allocation/layout work and leaves detached card trees referenced by pending async tasks.
- Each artwork request captures a direct `ImageView` and sets it when finished (`329–342`), without a holder identity/address check, cancellation, request de-duplication, per-season generation, or attachment check. A completed request can update an offscreen/detached old view; if the card is converted to true recycling without adding an identity guard, it can paint image A into the reused card for episode B. Clear/rebind the placeholder immediately and validate the bound image key (and lifecycle/generation) before applying a result.
- The two-worker image pool can accumulate obsolete requests while users rapidly browse/change seasons. Downloads have bounded byte size/timeouts through `SiteClient.download`; keep these limits and trusted-host redirect validation. Decoding is full `BitmapFactory.decodeByteArray` with no sample-size cap; actual returned image dimensions could use more memory than the 480×270 request suggests. Retain the bounded LRU, decode to target size, handle failed/empty artwork with a stable placeholder, and avoid cache/request races. Current image URL suffix unconditionally uses `&width=...` (`330`), so confirm query construction if URL formats change.
- A future recycled-card implementation should use a `ViewHolder` and bind token/image URL comparison before applying async bitmaps; cancel or deprioritize obsolete work where practical. Avoid changing adapter item/focus identity while the user holds focus. Season reload replaces the adapter/data and all IDs; preserve selected season/card focus intentionally, not just a transient GridView position.
- History row visibility changes from `GONE` to `VISIBLE` after async `onResume`/catalog completion (`219–249`), changing the vertical layout while focus may exist. Test focus retention and ensure no unexpected focus jump when Last watched/Next appears or its label changes. Async enabling of Player Next should not steal focus or make the auto-hidden toolbar inaccessible.
- Because focus is implemented partly by `ListView`/`GridView` selectors and partly by platform widget defaults, a resource/theme change can accidentally remove focus contrast or alter uppercase/accessibility behavior even if all click handlers still work. Validate focused, selected, pressed and disabled states on an actual TV-sized UI, not screenshots of unfocused cards alone.

## Smoke expectations to preserve or update

**Preserve all existing functional assertions** in `scripts/smoke-test.py`:

- Native catalog/no website WebView; dynamic published season count; all 14 Season 12 episodes including paginated The Ungroundable and return to Tonsil Trouble (79–100).
- Episode details and free/locked handling, player metadata, no picture toggle/Fit-only behavior, player remote pause/resume/10s seek, two-stage Back, visible direct Back, and D-pad access to Next (101–171).
- Last-watched and Up next labels, actual playback position persistence, force-stop/reopen durability, D-pad Continue and resume from saved position (172–191).
- Normal time-bar seek to content end, automatic advance to E5, updated metadata/history, and no player error (192–208). Preserve ad-aware native playback/audio checks in 63–76 and audio waveform checker at 191.

**Update test coupling to presentation:** `has_text()` currently checks visible `text` only (50–51), while default Buttons produce the uppercase assertions `WATCH EPISODE`, `BACK TO EPISODES`, and `NEXT EPISODE` (63–67, 123–152). A redesigned TextAppearance/button may change casing without changing semantics. Prefer stable view resource IDs/content descriptions and update the node helper to match `resource-id`/`content-desc` (or deliberately retain the same accessible labels); do not make cosmetic capitalization the contract. Keep user-visible meaning for episode title/label, Last watched, Continue/Watch again, Up next, loading/empty/unavailable/retry, Back, and Next.

The current smoke exercises some focus paths but does not assert the visual focus ring, selected-vs-focused distinction, every toolbar/controller direction, focus retention after async row visibility/Next enablement, card accessibility labels, dialog focus, locked card route, empty/error states, or image correctness during rapid scroll/season switching. Add focused checks/screenshots for the shared focus states and those paths, while keeping the test remote-driven and avoiding flaky pixel checks where resource IDs/accessibility are stronger. Current count assertions (29 seasons, 14 episodes) are observed-site fixtures and should remain updateable when official catalog availability changes (README.md:31, 67).

**Documentation consistency item:** `VALIDATION.md:22` states a complete v0.4 smoke passed with measured non-silent audio; `VALIDATION.md:43` says the amended full smoke sequence was not rerun after emulator replacement and the new checker was run independently. Clarify which exact test/code/emulator run the “full smoke passed” claim describes when updating UX validation. README says the smoke performs actual audio-sample checks (67), and current script invokes `check-emulator-audio.py` at 191; preserve truthful reporting and do not treat a Player audio callback alone as proof of audible output.

## Acceptance checklist

- [ ] One tokenized TV visual system is used consistently by catalog, episode/history cards, dialogs, player toolbar and Media3 controller where supported; no stray hardcoded UI colors/type/spacing remain in the touched presentation code.
- [ ] Focus has an obvious, high-contrast appearance; selected season differs from focus; disabled, loading and unavailable buttons are clear; text and focus survive 720p/1080p/overscan and long labels.
- [ ] Remote navigation is manually checked end-to-end: launch → saved season → seasons ↔ episodes → details actions → player/controller/toolbar → Next/direct Back/two-stage Back → history Continue/Watch again/Up next. No focus trap/jump during async loading, row visibility, or Next resolution.
- [ ] Episode cards expose useful accessible labels and preserve full-card D-pad activation. Rapid scrolling/season switching never shows artwork for the wrong episode; missing/failed artwork still leaves a usable card.
- [ ] Catalog, all pagination, locked/external official-page handling, saved season, Continue/resume, completion, next recommendations and autoplay remain behaviorally identical.
- [ ] Provider policy is unchanged: official HTTPS host/path allowlists and redirect checks, no auth/locked bypass, fresh official session, only supported Google IMA DAI HLS with supplied parameters, no invented consent, no proxy/stream capture/ad stripping, no picture-mode toggle, Fit remains enforced.
- [ ] Existing 21 unit tests pass; assemble/lint remain clean aside from documented warnings; updated emulator smoke covers existing playback/audio/history scenarios plus new focus/artwork assertions; README/VALIDATION accurately identify what was rerun.

## Start Here

Open `app/src/main/java/com/unofficial/southparktv/MainActivity.java:59–148` first. It is the catalog screen’s entire composition and shows the centralized refactor opportunity (inline tokens, custom focus ring, fixed geometry, direct widget construction). Next inspect `PlayerActivity.java:82–150` to align player chrome without disturbing its Media3 controller and focus contract.

## Supervisor coordination

No decision or coordination was required; this was a read-only discovery task.
