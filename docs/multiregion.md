# Regional sources MVP

Implements the architecture subset of [PR #3](https://github.com/JoseLuna12/southparkapp/pull/3). LATAM remains unverified. No LATAM endpoint was identified in that plan, so none is guessed here.

The catalog header shows “South Park en inglés”. The D-pad opens “Idioma” with “Inglés · Seleccionado” and a disabled “Español · No disponible” action. Only confirming English persists the enabled source ID. Selecting English or pressing Back restores focus to the language button. No geographic source names or technical configuration appear in this dialog. The remaining app controls stay in English. Spanish is unavailable until an official catalog and playback are verified; the UI does not present English metadata as a Spanish catalog.

RegionalSource owns the origin, catalog paths, session platform, service and image hosts, URL checks and preference filenames. SiteClient has an immutable source and validates redirects at every hop. Catalog parsing takes the source explicitly. The player receives its source ID in the launch Intent and retains it across Next, autoplay and activity recreation. Unknown or unavailable player sources are rejected before network or history access. No new playback system, dependency or auto-update is included.

US owns the existing playback and MainActivity preference files. This assigns all old positions, watched flags, last episode and selected season to US without copying or deleting data. Reopening is idempotent. Other IDs have separate filenames, including the unavailable LATAM stub. History URL decoding currently accepts only the enabled US profile. There is no cross-source resume matching.

## LATAM activation remains pending

Before enabling LATAM, identify official origins and paths from public official references, then verify paginated catalog, detail, fresh androidtv HLS/IMA sessions, rights, served ads and actual audio/caption tracks on a legitimate network. An es-419 preference is not proof of dubbing. Different authentication, DRM or advertising requires separate evaluation.

This MVP has no active source switching because only one source is verified. Before enabling a second profile, implement source-change cancellation and generation invalidation, empty prior catalog/history actions, scope delayed artwork/cache keys by source, restore the selected season from that source, and keep failed selections visible with Retry. Make history decoding profile-aware and test A-to-B delayed responses and same-ID histories with independent fixtures. Do not enable a second profile by merely filling in its origin.

The existing locked/auth gates, fresh sessions, privacy/ad parameters, Fit, remote controls, captions, Next and natural autoplay remain in place. No location spoofing or alternate source fallback is added.

## Validation of this change

Build, 26 JVM tests, lint and design-token checks pass for the language UI update. Emulator language navigation and screenshots are recorded in docs/evidence/5-codex-language/. The full playback smoke suite was not rerun for this UI-only update. No physical TV was used. LATAM playback, source switching, ad-break delivery and DRM remain unverified.
