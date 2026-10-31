# South Park TV design system — v0.5

## Intent and scope

A content-first, ten-foot interface: charcoal canvas, readable episode art/titles and restrained amber focus. Browse with a remote, understand the current season, resume quickly, then let the video fill the screen.

Native Java Views remain the implementation. `ListView`/`GridView` own directional selection; Media3/IMA own transport, tracks and ads. No new UI dependency, custom playback engine or parallel design framework. The provider, not this UI, decides availability.

This document describes **implemented app choices**, not universal Android TV requirements. See the [research archive](design/research/README.md) for sources and rejected proposals, and [validation](../VALIDATION.md) for actual evidence/limits.

## Foundations

Source of truth: `app/src/main/res/values/{colors,dimens,strings,styles}.xml` and state drawables. Change shared resources before adding activity-local styling.

| Foundation | Contract |
| --- | --- |
| Color | `tv_background` #0E1014; `tv_surface` #1A1D23; raised #252932; selected #302A1D. Accent #FFD166 with #171717 text. Primary/secondary/tertiary text #F5F6F8/#C5CBD4/#A7AFBA. Separate outline, error, disabled and artwork roles. Do not recolor the app from individual posters. |
| Contrast | Essential text targets ≥4.5:1, even for large text. Focus accent targets ≥3:1 against app surfaces. `scripts/check-design-tokens.py` checks these pairs, not arbitrary video frames. Text over video needs the chrome scrim; IMA-owned UI is not recolored. |
| Typography | System sans; 24sp brand, 23sp heading/player metadata, 18sp card title, 16sp actions/body, 14sp card metadata. Secondary brand context is 12sp; footer hints 13sp. These small roles must never contain the sole essential instruction. Bold only for hierarchy. |
| Units | Geometry in dp; type resources in sp. `TvUi.text()` reads the sp resource as scaled pixels once—do not apply a second sp conversion. Avoid fixed text-container heights. Cards reserve two title lines, then ellipsize; details expose the full title and synopsis. |
| Spacing | 8/16/24/32/48dp base rhythm; 16dp card gutters. Small optical/structural exceptions belong to the component, not a second scale. |
| Layout | Reference 960×540dp/16:9. 48dp horizontal and 27dp vertical content insets, with backgrounds/video bleeding to edges. Rail width 156dp, three episode columns. Insets are app choices approximating 5% on this reference, not an exact vendor mandate. Reassess against focused bounds on other displays. |
| Shape | 12dp cards/dialogs, 10dp buttons/rail, 4dp artwork. Artwork radius = card radius minus 8dp inset. Keep image clipping and focus geometry aligned. Borders convey structure/state; no decorative elevation stack. |
| Targets/icons | App buttons ≥52dp high, season rows ≥48dp. Use text for app actions; keep Media3's familiar transport/caption/settings icons and descriptions. No extra icon package. Do not replace established ten-second seek icons with unexplained custom symbols. |
| Motion | App focus/pressed feedback is immediate and static; no scale, parallax, autoplay countdown or required animation. No new animator/dependency. Native transport behavior remains Media3-owned. Focus must remain visible with animator scale zero. |
| Imagery | Official trusted thumbnails, 16:9 `CENTER_CROP` **for cards only**. Stable neutral mountain placeholder on every bind, including failed/missing images. Playback is always **Fit**, never card-style crop. |

## States: focus is not selection

- **Focused card/rail target:** persistent 3dp amber outline, with no geometry shift. Grid selection is the D-pad target; the grid owns focus, not nested card children.
- **Selected season:** warm fill plus a leading amber bar, whether or not the rail has focus. Merely focusing another season never loads it. Press OK to select it.
- **Focused/pressed action:** amber fill, dark text and contrasting outline; disabled actions use separate muted surface/text and cannot activate. Native Media3 controls retain their own focus behavior.
- **Loading:** preserve the shell, available actions and focus; show loading text/placeholder. Never move focus when artwork arrives.
- **Empty:** explain that no full episodes are listed. Clips do not become episodes. Rail/Reload remain usable.
- **Error:** readable error text plus a visible D-pad-reachable Retry. Do not rely on the error color alone. Retry the failed operation, not a different source or bypass.
- **Completion/history:** Cards prepend a text checkmark and Watched label to metadata and include watched state in their accessible description. Details expose a manual watched/unwatched action using the shared dialog. Watched flags persist locally and use the existing completion threshold. Continue includes position; a completed episode offers Watch again. Up next can be loading, available, absent or retryable. Do not enable actions before a valid target exists.

## Shared building blocks

| File | Ownership / use |
| --- | --- |
| `TvUi.java` | Resource-scaled text, shared action-button styling and native `AlertDialog` presentation. `showDialog()` bounds width to the safe area, styles/spaces actions and focuses the positive action. Use it rather than naked `.show()` for app dialogs. It owns no requests or player state. |
| `EpisodeCard.java` | One accessible grid item: decorative artwork, metadata, two-line title and one combined description including locked state. `bind()` resets the placeholder and binding key; `showArtwork(key, bitmap)` rejects delayed images belonging to another episode. No fetching, navigation or playback here. |
| `bg_*` drawables / `color/tv_button_text.xml` | Normal, pressed, focused, activated and disabled visuals. Do not duplicate selectors in activities. |
| `ids.xml` | Stable app control IDs for focus links and tests. Repeated `episode_card` IDs are intentional adapter-item IDs; identify an episode by its content description/ID too. |
| `MainActivity.java` | Screen assembly, catalog requests, adapter recycling, history actions and focus restoration. Keep existing request-generation/cancellation guards. |
| `PlayerActivity.java` | Full-bleed PlayerView, safe-inset app toolbar and app-owned error dialog. `styleTransport()` insets the existing bottom bar/timebar and colors `DefaultTimeBar` through setters. Caption on/off and language/role preferences survive player replacement within the activity session; episode-specific text overrides are translated to language/role before release. Native captions get safe margins, with their viewport ending above the timebar while controls are visible. Text captions default to 22sp regular sans-serif, soft-white `tv_text_primary`, a black drop shadow and transparent background/window. `styleSubtitles()` suppresses embedded text styling for this default; caption wording, timing, language selection and placement remain provider-owned. When Android accessibility captions are enabled, native system style/font-scale preferences take priority instead. Reapply on activity start so changes made in Settings take effect. Bitmap captions cannot be restyled this way. Video and IMA stay full bleed. Media3 IDs/layout are version-sensitive: recheck these bounds and the full smoke test when upgrading Media3. |

Example for a new app-owned action:

```java
Button action = new Button(this);
action.setId(R.id.your_stable_action); // Add in values/ids.xml.
action.setText(R.string.your_action); // Add in values/strings.xml.
TvUi.styleButton(this, action);
action.setOnClickListener(v -> performExistingAction());
```

Extract a new presentation component only after another real use needs the same structure. Do not turn `TvUi` into a navigation/network/state manager.

## Remote navigation and recovery

- Initial focus is the remembered season. OK loads its episodes; nonempty, user-initiated results enter the grid only if the rail still has focus.
- Grid Up reaches history actions (or Retry/Reload as appropriate). History actions and Reload have Down routes into the grid. Native spatial navigation handles ordinary horizontal movement; add explicit links only for demonstrated bad routes.
- Back from the grid returns to the season rail. Reload is reachable with arrows alone; Menu is an optional shortcut, not a requirement.
- Details open with Watch episode/Official site focused. Back dismisses to the same selected card. Playback return restores the launching grid item/offset when still applicable. Loading/reloading must not steal focus from another valid control.
- While playback controls are visible, Back hides them; Back with controls hidden exits. Visible Back to episodes exits directly. Native settings/caption panels close first; this sequence is covered by the live smoke test.
- Next starts immediately through the existing ad-gated path and fresh official session. Content-end autoplay remains immediate. Do not silently add a countdown, skip unavailable episodes or change the sequential order.

## Accessibility, copy and localization

Use resource strings and start/end padding/margins. Do not build translated sentences by concatenation. Episode titles/synopses are provider data; retain full accessible text when the visible label ellipsizes. Use locale-aware/bidi wrapping when adding mixed-direction translations; strings/start-end geometry alone are not proof of RTL support.

Cards expose one clickable accessibility node; artwork and duplicate child labels are decorative. Ordinary buttons already expose their text. Add/update descriptions for icon-only or stateful controls; loading/retry descriptions must not retain the previous next episode. Status updates use a polite live region, never per-second playback announcements. Do not label a paused player as proof of sound or announce progress continuously.

English UI only at present. 1.2 font scale and animator scale zero were tested on the reference emulator. TalkBack traversal, RTL/localized long strings, larger scales, narrow displays and physical viewing distance are **not certified**. Test them before claiming support or adding a locale. Preserve native caption/audio-track selection and system caption preferences. One English caption track was visually checked on S12 E5, with controls visible and hidden; The v0.5.1 softer default and Android-enabled white-on-black preset at 1.35 caption font scale were visually verified on the emulator, restoring all temporary settings afterward. Alternate audio tracks and other caption formats remain unverified. Native Media3 menu popups can extend to the screen edge even though their launch controls are inset; verify their text/focus on the physical TV before claiming overscan safety for those panels.

## Playback/security boundaries

`Catalog`, `SiteClient` and `WatchHistory` now use the regional-source configuration for the current US source. The catalog header language dialog uses `TvUi`, restores caller focus and offers English and Spanish, with the current language selected. Spanish requests the published official link; a failed request retains Spanish with Retry and no English cards. Geography and provider configuration remain internal; see [regional MVP limits](multiregion.md). Keep their URL gates, pagination, official ordering, completion boundaries and existing history fields. Watched flags are additive per-episode preferences. Keep PlayerActivity's fresh MICA/IMA sessions, ad-aware seeks/Next, async generation/active checks, old-identity save/release order and Fit mode. Never theme IMA-owned controls or add provider/DRM/auth/region workarounds.

## Change checklist

1. Read this contract and existing component before writing a new surface. Prefer tokens/components over local colors, sizes or raw dialogs.
2. Walk D-pad focus, activation, Back, dialog cancel, reload/retry, empty and loading states. Focus alone must never select a new season or start playback. Inspect focused bounds, not just unfocused screenshots.
3. Check long titles, font scaling, visible versus selected state, async image reuse and caller focus return. Preserve non-color state cues and static focus.
4. Run build, 21 Java tests, lint, token checks, UI checks and full live smoke; commands are in [README](../README.md#build-and-test). Retain waveform audio verification and do not weaken independent expected outcomes to make a redesign pass.
5. Update this guide, screenshots and validation limits when the implementation changes. Research suggestions are not unapproved feature requirements.

## Primary references

[Android TV design](https://developer.android.com/design/ui/tv), [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [focus](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [remote navigation](https://developer.android.com/training/tv/get-started/navigation), [TV accessibility](https://developer.android.com/training/tv/accessibility), [Media3 Views/customization](https://developer.android.com/media/media3/ui/customization). Detailed source/uncertainty records are in the research archive.
