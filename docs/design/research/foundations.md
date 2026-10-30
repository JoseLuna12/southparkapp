# Android TV visual foundations for the South Park app

## Scope and design stance

This is visual-foundation research for a native Android TV app using Java Views; it does not propose a product-flow rewrite. Preserve the existing catalog, region/auth, ad/DRM, season browsing/pagination, durable Continue/history, remote playback, captions, two-stage Back, always-Fit, manual Next and content-end autoplay. Apply the tokens and states below to those screens without changing behavior. No emulator/device or project-file work was done.

The Android TV design hub separates foundations, style, layout and components. TV design adapts Material for a 10-foot, D-pad-controlled interface: legibility and prominent focus feedback take precedence over touch-first density. It is not a mandate to port a phone Material 3 theme or components unchanged. TV documentation defines its own 15-style type scale and focus model. The TV card guide links to Compose-only `androidx.tv.material3` implementation; its design patterns can be implemented in this Views app without a Compose migration.

## Color, semantic roles, surfaces and contrast

Android TV's color guide says it follows Material color guidelines, recommends a dark theme for a cinematic experience, and describes primary, secondary, tertiary, neutral/surface and outline roles. Primary supports prominent actions, active states and elevated-surface tint; secondary is less prominent; tertiary is discretionary. Android TV does not support wallpaper-generated user dynamic color. Content-derived color from poster/hero imagery is optional. Prefer a stable app-wide palette; if image-derived color is used on a hero, protect text and controls with a tested scrim rather than changing global controls with every poster.

A compact **starting token set** (hex values are app choices, not Google-prescribed values):

| Token | Example | Use |
|---|---|---|
| `background` | `#0D0F12` | Full-screen canvas; video itself stays unobscured |
| `surface` / `surfaceRaised` | `#191C22` / `#252A33` | Cards, panels, player chrome |
| `textPrimary` / `textSecondary` | `#F5F6F8` / `#C5CBD4` | Essential labels / brief metadata |
| `primary` / `onPrimary` | `#FFD166` / `#171717` | Brand accent, focus, progress / text on accent |
| `outline` / `error` | `#9299A4` / `#FF746C` | Neutral border / error, never sole state cue |

Use Material role names in XML resources rather than scattered color literals. Keep the canvas neutral and reserve the accent for active/focused elements. TV guidance describes tonal surface elevation: levels +1 to +5 receive primary-based overlays. Apply restrained tonal separation; distinguish raised panels from the currently focused control.

The TV color page links to TV accessibility guidance but does not specify contrast ratios. General Android accessibility guidance recommends at least 4.5:1 for text under 18sp (or bold text under 14sp) and 3:1 for other text. These are general Android thresholds, not TV-specific tokens. As a conservative app choice, target 4.5:1 for essential UI text and check focus rings/icons against both surfaces and varied poster art. Use a neutral scrim/gradient behind overlaid text; do not dim the whole page. Keep disabled elements visible and do not encode focus, error or selection through color alone.

## Typography and ten-foot legibility

TV typography guidance identifies Roboto as the default UI face, a 15-style TV type scale, and recommends larger, glanceable text, clear letterforms and avoiding decorative fonts for body copy. A distinctive brand face can work for a short hero heading; keep labels, descriptions and controls in a clear sans serif. Keep descriptions brief so cards scan at a glance.

A sensible starting app scale (sp, **app choices**, not official TV Material values): hero 48sp; screen/section heading 32sp; card/episode title 24sp; body 20sp; short metadata 16sp. The accessible TV typography text names roles but does not enumerate numeric sizes, so validate these values at viewing distance. Use flexible containers, deliberate wrapping/ellipsizing and avoid fixed-height text rows. TV accessibility guidance says to respect user text scaling, use `wrap_content`, and ensure layouts fit at larger scales; test around 1.2 font scale. Media subtitles must still use the user's caption settings, not decorative app text styling.

## Layout, spacing and safe areas

TV layout guidance uses a 16:9 canvas and recommends designing at 960×540dp at MDPI. Its 12-column grid uses 52dp columns and 20dp gutters. TV card widths are listed as 844dp (one), 412dp (two), 268dp (three), 196dp (four) and 124dp (five). These are templates, not required catalog counts. Choose 16:9, 1:1 or 2:3 artwork based on the content; start with 3–4 readable cards across rather than squeezing long titles into five narrow covers. Preserve room for focused-card scaling. Use horizontal movement within a row and vertical movement between rows; keep focus order logical when pagination appends content.

The layout page recommends keeping important controls within an overscan margin, but conflicts numerically: the introduction says 48dp left/right and 27dp top/bottom at 960×540; a later section says 58dp and 28dp, and notes modern TVs rarely overscan. Treat the values as guidance, not compliance constants. App choice: begin with ~56dp horizontal/~28dp vertical insets for focusable UI, then test TVs. Let backgrounds/edge artwork bleed; keep text, focus bounds and actions inside. Use an 8/16/24/32/48dp spacing rhythm, retaining the documented 20dp grid gutter where suitable.

## Shape, icons, focus and state matrix

The TV pages do not prescribe a textual universal corner-radius scale. Choose one restrained set (e.g. 12dp cards, 8dp buttons, pill chips); these are app choices. Align image crops and focus outlines to the same shape. The card guide treats each card as one subject, offers standard/classic/compact/wide variants and recommends short descriptions; compact text over imagery should have a semi-transparent black gradient. Do not turn one card into nested action surfaces.

Do not confuse launcher-icon requirements with in-app glyphs. Android TV's launcher guide covers manifest `icon`/`banner` and adaptive layers; it says themed launcher icons are unsupported, but does not set an in-app icon system. For player controls use simple, consistent vector icons with adequate contrast, pairing less obvious actions with brief labels and accessible descriptions.

TV focus is a primary navigation state: D-pad movement should land on a visible focusable element, one at a time. The focus guide distinguishes default, focused and pressed states; enabled/disabled/selected form additional combinations. It permits scale, outline, glow and color indication. Reference scales are 1.025×, 1.05× and 1.1×; glow levels span 2–32dp. These are options, not a demand to apply every effect. App choice: start at 1.05× for large cards, 1.025× for compact controls, and a clear ~2dp accent outline. Keep gutters so focus does not clip or overlap. Make select/pressed response immediate. Keep selected (e.g. chosen season/caption) distinct from focused (navigation location), and show both when needed. Disabled controls should be recognizable without disappearing.

For Java Views, define reusable state resources/selectors for focused, pressed, selected and enabled states (`state_focused`, `state_pressed`, `state_selected`, `state_enabled`); make focusability and directional navigation predictable, and re-check focus after row/page updates.

## Motion, reduced motion and validation

The TV focus guide supports scale/glow but does not prescribe animation timing or a TV-specific reduced-motion policy. Keep motion functional: subtle lift can explain focus, but avoid long transitions, large zooms, parallax or delaying a D-pad action. A 120–180ms focus transition is an **app choice**, not a sourced mandate. Preserve a clear border/color state when motion is reduced or disabled; no important action may depend on animation completion. Test with system animator duration scale set to zero and avoid decorative motion over video.

Validate with D-pad-only navigation through catalog, paginated rows, seasons/episodes, playback, seeking, captions and Next. Confirm focus never vanishes or clips, card scale does not overlap, and focused/pressed/selected/disabled states differ. Verify two-stage Back and existing playback behavior remain intact. Check 720p/1080p/4K, enlarged text, bright poster art, and animator scale zero; use accessibility/TalkBack review for labels and state. Do not infer compatibility from emulator screenshots alone: real-TV overscan, focus, density and media contrast need device checks.

## Sources (official)

- [TV design hub](https://developer.android.com/design/ui/tv) — overview and guide links.
- [Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv) — 10-foot and D-pad principles.
- [Color system](https://developer.android.com/design/ui/tv/guides/styles/color-system) — roles, dark theme recommendation, dynamic color.
- [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography) — Roboto, TV type roles, legibility.
- [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system) — states, scale/glow and tonal elevation.
- [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) — grid, overscan and templates.
- [Cards](https://developer.android.com/design/ui/tv/guides/components/cards) — variants, spacing and image scrims; implementation link is Compose.
- [TV accessibility](https://developer.android.com/training/tv/accessibility) and [Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) — scaling and contrast guidance.
- [TV app icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) — launcher assets, not in-app iconography.

## Research limitations

Official Android TV pages above returned content; actual guide paths were discovered through the hub/page navigation. The speculative `/design/ui/tv/guides/foundations` URL returned HTTP 404 and was discarded in favor of the live linked `/guides/foundations/design-for-tv`. The linked Material color-role and accessibility-basics candidates on `m3.material.io` also returned HTTP 404 through the text reader; no claims here depend on those reads. TV pages do not prescribe numeric in-app shape radii, icon sizes or focus-animation timing; the proposed values are explicitly app choices. Safe-area numbers conflict as noted. Browser final title: “Design for TV | Android Developers”; URL: https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines. Screenshot: not saved (unnecessary for text research; no screenshot artifact/size).
