# South Park Android TV — reconciled design and implementation contract

## Scope and precedence

This is a read-only reconciliation of the four supplied reports, not a codebase audit: no current route, provider integration, or widget implementation is asserted. Apply it to the existing native Java Views app while retaining its product behavior. **Do not rewrite to Compose.** Official TV-specific design/navigation guidance takes precedence over generic phone-era patterns; general Android accessibility advice supplements TV guidance. Values not specified by Android are marked as app choices.

**Evidence labels:** **TV baseline** = official TV checklist/navigation/usability criterion; **TV guidance** = official recommendation, not a mandated implementation; **Android guidance** = general Android accessibility recommendation; **app choice** = concrete starting value/structure to validate.

## Decisions and rejected alternatives

| Decision | Contract / rejected alternative | Basis |
|---|---|---|
| TV-first, Java Views | Use 10-foot legibility, D-pad-first focus, predictable spatial navigation and remote-only operation. Keep the existing Views architecture; RecyclerView/native Views are suitable. Compose TV examples are layout/interaction references, not a migration requirement. Do not start a new Leanback implementation merely because it is legacy TV technology. | [TV design hub](https://developer.android.com/design/ui/tv), [Foundations](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv), [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [RecyclerView](https://developer.android.com/reference/androidx/recyclerview/widget/RecyclerView), [Leanback migration](https://developer.android.com/training/tv/playback/leanback/migrate-to-compose) |
| Favor TV focus/layout over phone density | Use shelves, readable episode cards, visible focus and generous safe space. Reject touch-only affordances, pull-to-refresh, phone spinner/sheet season selection and compact five-across artwork as defaults. Do not transplant a phone Material theme unchanged. | [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [Cards](https://developer.android.com/design/ui/tv/guides/components/cards), [TV navigation](https://developer.android.com/training/tv/get-started/navigation) |
| Stable dark app palette | Use semantic roles and stable app-wide colors; Android TV does not support wallpaper-generated dynamic color. Image-derived hero color is optional but cannot reduce text/control contrast. Reject per-poster global recoloring. | [TV color system](https://developer.android.com/design/ui/tv/guides/styles/color-system) |
| Resolve safe-area disagreement conservatively | Start at **48dp horizontal / 27dp vertical** content insets on the 960×540dp reference canvas (about 5%); let backgrounds bleed. The TV layout page also gives 58/28dp elsewhere and another report proposed 56/28dp. These conflicting figures are not exact requirements; expand if focus bounds or device checks demand it. | [TV layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [TV quality checklist](https://developer.android.com/docs/quality-guidelines/tv-app-quality) |
| Focus is not selection | Focus is the temporary D-pad target; selected season/track and playing/progress are persistent states. Show these independently; focus movement never activates playback or changes selection. Retain a static focus cue when motion is disabled. | [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [TV accessibility](https://developer.android.com/training/tv/accessibility) |
| Preserve player/provider boundaries | Keep the existing Media3/IMA/DRM/auth path where present; customize existing PlayerView/controller only if needed. No second engine, provider/region/auth/entitlement/DRM bypass, ad-skipping seek/Next, or re-skinning IMA-owned controls. Keep always-Fit; reject fill/crop/picture-mode choices. | [Media3 Views](https://developer.android.com/media/media3/ui/playerview), [customization](https://developer.android.com/media/media3/ui/customization), [TV playback](https://developer.android.com/training/tv/playback/controls), [IMA](https://developer.android.com/media/media3/exoplayer/ad-insertion), [DRM](https://developer.android.com/media/media3/exoplayer/drm) |
| App Continue is not system Watch Next | Continue/history and in-app Up next stay app-owned. If platform Watch Next is already supported, follow its insertion/eligibility rules; do not imitate Play Next with a launcher channel or replace history with a transient player queue. | [Watch Next guidelines](https://developer.android.com/training/tv/discovery/guidelines-app-developers), [Watch Next insertion](https://developer.android.com/training/tv/discovery/watch-next-add-programs) |
| Predictable two-stage player Back | With no transient panel open, Back #1 hides visible controls; Back #2 with controls hidden leaves the player and returns to its caller. Caption/settings panel closes first, then the normal two-stage sequence applies. No confirmation loop or trapped route. | [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [Media3 Android TV](https://developer.android.com/media/media3/ui/androidtv) |

## Coverage matrix

| Category | Reconciled contract | Status and source |
|---|---|---|
| Color / theme / surfaces | Dark cinematic canvas; semantic background, surface, raised surface, text, accent, outline and error roles. Reserve accent for active/focused state; distinguish surfaces tonally. Use art scrims behind text. | Dark theme/roles are **TV guidance**; concrete colors below are **app choices**. [Color](https://developer.android.com/design/ui/tv/guides/styles/color-system) |
| Contrast / non-color states | Never encode focus, selected, disabled or error only by color. Conservatively target 4.5:1 for essential UI text and test focus/icons against surfaces and changing art. Android's general thresholds are 4.5:1 for smaller text and 3:1 for larger text; not TV-specific tokens. | **Android guidance** + stricter **app choice**. [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) |
| Typography / scaling | Roboto/clear sans for UI; brief labels/descriptions; `sp`, flexible height/wrapping, localized strings and ellipsizing that does not hide essential meaning. Test 1.2 font scale and long text. | Roles are **TV guidance**; numeric sizes below are **app choices**. [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography), [TV accessibility](https://developer.android.com/training/tv/accessibility) |
| Layout / grid / safe area | 16:9 and 960×540dp MDPI reference; horizontal shelves and vertical category movement; preserve safe focus margins. Start episode grid at 3 columns, tune only after viewing-distance/focus validation. | Reference is **TV guidance**; column count/insets are **app choices**. [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) |
| Spacing / density | 8/16/24/32/48dp rhythm; use documented 20dp gutters where the 12-column grid applies. Allow focus enlargement without clipping/overlap. | Rhythm is **app choice**; 20dp is documented template gutter. [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) |
| Cards / imagery | One subject/action per card; 16:9 episode art; short episode title/number/progress, not synopsis-heavy cards. Scrim over image text; avoid nested independent actions inside one card. | **TV guidance**. [Cards](https://developer.android.com/design/ui/tv/guides/components/cards) |
| Shape | Align art crop and outline. Start 12dp cards, 8dp buttons, pill selector chips; Android TV does not prescribe a universal radius scale. | **App choice**. [Cards](https://developer.android.com/design/ui/tv/guides/components/cards) |
| Focus / pressed / selected / disabled | Differentiate default, focused, pressed, selected, enabled and disabled. Start large cards at 1.05×, compact controls at 1.025×, with ~2dp accent outline; preserve cue without motion and reserve halo room. | State/effects are **TV guidance**; scale/stroke are **app choices**. [Focus](https://developer.android.com/design/ui/tv/guides/styles/focus-system) |
| Icons / targets | Consistent simple vector icons; pair less-obvious controls with text/accessibility description. General Android recommends at least 48×48dp focusable interaction areas; this is not a TV-specific remote minimum. | **TV guidance** + general **Android guidance**. [TV accessibility](https://developer.android.com/training/tv/accessibility), [Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views) |
| Motion | Brief focus animation is supplementary; retain static focus at animator scale zero. 120–180ms is a starting choice, not a sourced mandate; no action depends on animation completion. | Effects are **TV guidance**; timing is **app choice**. [Focus](https://developer.android.com/design/ui/tv/guides/styles/focus-system) |
| Accessibility / localization | D-pad and TalkBack are distinct traversal models. Label icon-only actions; avoid duplicate descriptions on text; hide decorative art from accessibility; announce meaningful changes, not every seek tick; use start/end for RTL; respect system caption settings. | **Android/TV guidance**. [TV accessibility](https://developer.android.com/training/tv/accessibility), [TalkBack](https://developer.android.com/training/tv/accessibility/talkback), [Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views), [languages](https://developer.android.com/training/basics/supporting-devices/languages) |
| Navigation / Back | Native directional focus first; use `nextFocus*` only to correct demonstrated bad routes. Every action works with D-pad/select/Back; no touch/Menu-key dependency. Restore sensible focus after navigation/data changes. | **TV baseline/guidance**. [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [TV checklist](https://developer.android.com/docs/quality-guidelines/tv-app-quality) |
| Player / controls / captions | One PlayerView/player path; supported play/pause, seek and Next; separate D-pad-reachable audio/caption settings. Render from player/tracks/events; don't enable unsupported commands. | **Media3 guidance**. [PlayerView](https://developer.android.com/media/media3/ui/playerview), [customization](https://developer.android.com/media/media3/ui/customization), [track selection](https://developer.android.com/media/media3/exoplayer/track-selection) |
| Async/loading/error/empty | Stable shell/focus while loading; placeholders non-focusable unless actionable. Keep Retry local/reachable; preserve scroll/focus on error; distinguish empty Continue from empty catalog/results. | Product/UI choice consistent with TV focus guidance. [TV navigation](https://developer.android.com/training/tv/get-started/navigation) |

## Coherent screen, focus and state plan

### Home and catalog

- Retain the existing primary-navigation model and all existing destinations; do not invent route changes before the separate code audit. Keep account/auth, region and provider routes D-pad discoverable. In content, arrange short-titled horizontal shelves in a vertical list: **Continue watching** when populated, **Up next**/recommendations where applicable, then existing official catalog/category shelves. Continue is app history, not platform Watch Next.
- Launch focus goes to first Continue card if present, else first item in the first populated catalog shelf. Left/right traverse a shelf; up/down move between shelves; leaving the first card returns to primary navigation. Use default geometric focus unless testing shows ambiguity; async art cannot steal focus.
- Use stable content IDs. Page append preserves focused item/row and scroll. Loading/error/empty states retain a meaningful reachable target and do not strand focus.

### Series / season / episode browse

- Series detail: title, concise metadata/description, valid Resume/Play, labeled season selector and episode grid. Use focusable chips for a few seasons or a D-pad menu for many; reject a touch-only phone spinner/sheet.
- Start at 3 columns on 960dp; use 16:9 episode art, readable episode number/title and progress/duration. Focus order is Resume/Play → season selector → first episode; grid arrows follow episodes and up returns to the preceding row/header. Preserve server pagination with a focusable inline **Load more** item or safe prefetch, never pull-to-refresh.
- Season change retains selector focus and restores stable episode ID or first episode. Return from playback restores season, episode focus and scroll. Page append never moves focus to the new item.

### Player / remote / Back / state

- Full-screen landscape video, always **Fit** (uncropped/undistorted); no picture-mode UI. Keep existing playback engine/PlayerView. If needed, use documented PlayerView custom-controller slot bound to the same player; do not use PlayerControlView standalone or a second player.
- Primary controls: play/pause, elapsed/remaining position and seeking, available Next; caption/audio and secondary settings in a separate D-pad-reachable panel. Honor player-supported commands/tracks; unavailable actions absent or clearly disabled. Preserve existing remote/media-key semantics and prevent double handling. TV guidance recommends center play/pause/reveal, left/right seek, up/down peek where compatible; do not assume dedicated transport keys exist.
- Back precedence: close caption/settings panel; otherwise Back with controls visible hides them without stopping playback; next Back with controls hidden returns to caller and restores episode focus. Later Back returns to prior destination; root exits normally. No confirmation gate.
- Model catalog/auth/region resolution, preparing/buffering, playing, paused, ad, settings/tracks, recoverable error, terminal auth/DRM error, content-end Up next/countdown. Keep title context during buffering/errors; retry is bounded and only after auth/license prerequisites. Follow existing player lifecycle/release contract.

### Next, autoplay and durable history

- Provider catalog, region, authentication, entitlement, ad, DRM-license and pagination rules remain authoritative. Manual Next is independently focusable; invoke immediately only if catalog says next is valid, available and authorized. It cannot bypass an active ad or gate.
- Content-end autoplay remains a separate one-shot flow: show Up next title/countdown and a reachable, announced cancel/stop action; resolve/start only after expiry and through existing authorization/ad/DRM. Ignore ad-state and stale/duplicate callbacks. Preserve current timing/cancellation behavior unless the product/code audit explicitly changes it.
- Continue/history keeps the existing durable data path and write cadence. Save before transitions and at existing meaningful-position points; do not replace it with transient playlist state. Resolve visible title/next data from authoritative catalog/item identity, not guessed stream metadata.
- IMA-owned controls remain provider-rendered and uncustomized; avoid app overlays over them. If app-owned overlay during ads is necessary, register via supported ad-view provider. Preserve caption/audio selection and system caption preferences; handle unavailable/off tracks accurately.

## Semantic tokens and reusable Java Views/resources

These are **starting app values**, not Android-prescribed values. Centralize them instead of scattering literals through Java.

| Resource | Starting values / use |
|---|---|
| `res/values/colors.xml` | `colorBackground #0D0F12`, `colorSurface #191C22`, `colorSurfaceRaised #252A33`, `colorTextPrimary #F5F6F8`, `colorTextSecondary #C5CBD4`, `colorPrimary #FFD166`, `colorOnPrimary #171717`, `colorOutline #9299A4`, `colorError #FF746C`. Accent for active/focused state, not every surface. |
| `res/values/dimens.xml` | `safe_horizontal 48dp`, `safe_vertical 27dp`, `grid_gutter 20dp`, spacing 8/16/24/32/48dp, card radius 12dp, button radius 8dp, focus outline 2dp. |
| `res/values/styles.xml` / text appearances | Hero 48sp, screen title 32sp, card title 24sp, body 20sp, metadata 16sp; Roboto/clear sans, scalable and wrap-content. |
| `res/color` + `res/drawable` | XML state selectors/layers for focused, pressed, selected, enabled/disabled; high-contrast outline/surface, vector controls, black image scrims/gradients. Focus is never color-only. |
| `res/values/strings.xml` | Localized control/status/empty/error/retry/Next/countdown/cancel/caption labels and a11y announcements. |

Reusable Java/View pieces, adapted rather than introduced wholesale:

- `TvActionButton` / focus-aware card View: single action, XML state selectors, accessible name, focus padding and independent selected/disabled state.
- `ShelfRowView`: horizontal `RecyclerView` with stable-ID adapter; enclosing catalog list controls vertical row order and focus restoration.
- `EpisodeGridView`: `RecyclerView` + `GridLayoutManager`, stable episode IDs, inline pagination and saved scroll/focus key.
- `SeasonSelectorView`: D-pad chips/menu, selected state separate from focus.
- `CatalogStateView` / `InlineStatusView`: non-focusable loading/empty/error presentation plus only the relevant Browse/Retry/sign-in action.
- Keep `PlayerView`; only if needed add XML custom controller with required `@id/exo_controller`, bound to the same player. A small Java controller observes `Player.Listener`/tracks and delegates to supported commands; preserve current Media3/IMA/DRM setup.
- A narrow focus/return-state helper may save screen key, row/episode ID and scroll offset. Avoid a new navigation framework or parallel store.

## Functionality-preservation checklist

- [ ] Official provider catalog/restrictions remain authoritative; preserve region, auth/entitlement, pagination and return destinations. No unauthorized detail/play/Next path.
- [ ] Browse existing series, seasons and episodes; pagination remains incremental and focus-safe.
- [ ] Continue watching/history retain durable semantics and existing persistence/write cadence; Up next resolves from authoritative catalog data.
- [ ] Preserve playback, pause/resume, all existing remote/media-key behavior, seek limits/forward/rewind, position and lifecycle restoration.
- [ ] Preserve captions/subtitle and audio-track selection, system caption preferences and unavailable/no-track behavior.
- [ ] Preserve IMA, skip timing, DRM/license, auth refresh/errors. No overlay obscures provider controls; no seek/Next through active ads.
- [ ] Preserve two-stage player Back (visible controls → hide; hidden → return), with settings/caption dismissal first; restore caller episode focus.
- [ ] Preserve always-Fit; no picture mode, crop/fill toggle or source distortion.
- [ ] Preserve distinct focusable manual Next and content-end autoplay; authorized manual action; end-only, cancelable, single-fire autoplay.
- [ ] Keep app Continue separate from optional platform Watch Next; follow official system-row rules if integration exists.
- [ ] No Compose rewrite or touch-only control.

## Validation matrix

| Area | Validation | Pass condition |
|---|---|---|
| TV shell / focus | D-pad walkthrough of launch, navigation, shelves/row edges, details, dialogs/returns; inspect async art, refresh, page append. | Every action reachable; focus visible/unclipped; focus movement does not select; no focus theft/trap. |
| Browse / pagination | Series → seasons → episode pages → Load more; season change and return from player; loading/empty/error. | Provider data/pagination retained; stable card/scroll restoration; meaningful focus target survives. |
| Legibility / layout | 720p, 1080p, 4K/target densities; long/localized titles; light/dark art; safe margins and focus at viewing distance. | Essential UI readable; no clipping/overlap; scrim and contrast remain sufficient. |
| Typography / locale | Default and ~1.2+ font scale, long strings, RTL and mixed-direction names. | Text reflows; labels/controls remain visible/focusable; physical seek direction and chronology remain correct. |
| Motion / accessibility | Animator scale zero; TalkBack from first launch through browse/play; accessibility-tree inspection. | Static focus remains obvious; no action depends on motion; no duplicate/per-tick speech; meaningful state changes announced. |
| Playback / remote | Center/select, arrows, media keys, transport, seek bounds, timeout/control reveal. | Existing semantics; no double trigger; all controls reachable; supported commands only; video behavior unchanged. |
| Captions / audio | Multiple tracks, off/unavailable, preference/style/language changes, panel dismissal and continuity. | Correct selected state; no false track; system settings respected; focus returns. |
| Back / lifecycle | Back with panel open, controls visible, controls hidden; return from player; Home/background/reattach/release. | Panel closes first; controls Back then exit Back works; correct caller focus/position; no duplicate/leaked player. |
| Ads / auth / DRM | Ad start/skip/end; active-ad seek/Next; expired auth; region denial; license denial/refresh; offline/HTTP/decoder errors. | Provider gates and IMA UI intact; no ad bypass; bounded actionable error path; no unauthorized/endless retry. |
| Next / history | Manual Next valid/unavailable; countdown cancel/expiry; duplicate callbacks/ad-state end; process recreation/app exit. | Manual and auto flows distinct; autoplay once at content end only; cancel works; history/Continue durable. |
| Fit / display | Wide/narrow source aspects and captions/control positions at display edges. | Always-Fit remains uncropped/undistorted; controls/focus inside safe area. |

## Unknowns for the separate codebase audit

- Current routes/screens, focus restoration behavior, season/pagination implementation and artwork ratios.
- Actual player/UI versions and PlayerView/controller customization, key dispatch, timeout/seek implementation and lifecycle ownership.
- Exact provider restrictions, auth/region/entitlement rules, ad overlay registration, DRM retry/refresh contract, and whether platform Watch Next exists.
- Continue/history storage/write cadence; manual Next eligibility source; autoplay duration/cancel semantics/end-event guard.
- Caption/audio persistence and target TV/OEM support; whether MediaSession/background playback exists and must remain unchanged.
- Product-approved brand/type/color and physical TV/remote matrix. Proposed tokens, grid and focus effects are starting values, not approved requirements.

No source inspection or runtime test is claimed here; the separate codebase audit and real-TV validation should resolve these before implementation. No emulator/device/install/settings action was performed.

## Research sources

- Android TV: [design hub](https://developer.android.com/design/ui/tv), [foundations](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv), [color](https://developer.android.com/design/ui/tv/guides/styles/color-system), [typography](https://developer.android.com/design/ui/tv/guides/styles/typography), [focus](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [cards](https://developer.android.com/design/ui/tv/guides/components/cards).
- Navigation/accessibility: [TV navigation](https://developer.android.com/training/tv/get-started/navigation), [TV checklist](https://developer.android.com/docs/quality-guidelines/tv-app-quality), [TV accessibility](https://developer.android.com/training/tv/accessibility), [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback), [Android Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).
- Media/discovery: [TV playback controls](https://developer.android.com/training/tv/playback/controls), [Media3 PlayerView](https://developer.android.com/media/media3/ui/playerview), [Media3 customization](https://developer.android.com/media/media3/ui/customization), [track selection](https://developer.android.com/media/media3/exoplayer/track-selection), [IMA](https://developer.android.com/media/media3/exoplayer/ad-insertion), [DRM](https://developer.android.com/media/media3/exoplayer/drm), [Watch Next developer guidelines](https://developer.android.com/training/tv/discovery/guidelines-app-developers).
