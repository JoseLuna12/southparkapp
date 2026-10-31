# Official-site protocol findings

This records the public requests inspected while building the native client. These are undocumented website interfaces, not a promised public API. They can change. Session URLs, signatures, cookies, consent strings and HAR captures are deliberately not included.

## Catalog

Entry page: https://www.southparkstudios.com/seasons/south-park

Example season: https://www.southparkstudios.com/seasons/south-park/gfstdd/season-12

The server returns a script assigning JSON to `window.__DATA__`. Its nested component props contain:

- A season selector with `label`, `seasonNumber` and `url`. The selected season's `url` is JSON null; use the current page path for that entry.
- A video guide with `isEpisodes`, `items`, `filters` and sometimes `loadMore.url`.
- Episode cards with public IDs, official page paths, titles, descriptions, season/episode labels, durations, artwork and availability labels.

Pagination URL shape observed on the site:

```text
https://www.southparkstudios.com/api/context/{encoded season mgid}/episode/{page}/{pageSize}
```

Page 1 normally has ten cards. The API response contains `items`; the client follows subsequent page numbers until it receives fewer than the requested page size. Duplicate pages and excessive pagination produce an error rather than silently returning a truncated catalog.

The live survey followed all listed season links and their full-episode pagination. It found 331 full-episode cards across Seasons 1–28. The selector also includes Season 29, but its current guide has `isEpisodes=false`, an All Videos filter, and clips instead of full episodes. This is treated as an empty full-episode season, not an API error. These are current listings, not a claim that every produced episode is free or present.

Android's bundled `org.json` can stringify JSON null as the literal `"null"`, unlike the JVM test dependency. Explicit null handling is essential for both the selected season and `lockedLabel`. This was caught and corrected in emulator testing.

## Playback

Examples inspected:

- https://www.southparkstudios.com/episodes/4r4367/south-park-major-boobage-season-12-ep-3
- https://www.southparkstudios.com/episodes/u1yv8w/south-park-canada-on-strike-season-12-ep-4

Episode page data contains `videoDetail`, including `authRequired` and `videoServiceUrl`.

The official player calls a URL shaped like:

```text
https://topaz.paramount.tech/topaz/api/{episode mgid}/mica.json
```

The service accepted `clientPlatform=androidtv` directly. The app identifies itself as Android TV with its own user agent, requests limited ad tracking, and uses an ephemeral session ID. It does not pretend to be a desktop browser or change the country returned by the service.

The MICA response includes:

- Expiry information. A fresh response is obtained on each playback initialization.
- `stitchedstream` with the format and `adstitchingprovider`.
- `imasdk` with the content source ID, video ID, short-lived authorization token and provider ad parameters.

Browser traffic showed the official player sending that session through Google IMA DAI, obtaining an ad-aware HLS stream and timed ad metadata. The native app does the equivalent with Media3's IMA DAI integration instead of replaying a captured manifest. It preserves concrete provider ad parameters, drops unresolved template macros, requests non-personalized ads and limited tracking, and does not manufacture consent signals.

A native Media3 `ImaServerSideAdInsertionUriBuilder` passes the current session to `ImaServerSideAdInsertionMediaSource`. Google's SDK owns DAI registration, stream resolution and ad playback reporting. The catalog/playback app flow does not contain a website WebView. Google's IMA SDK can use web components internally.

Authentication-required episodes are stopped before requesting playback. Unsupported service types and native player errors are surfaced with Retry, Back and optional official-site fallback. No raw-stream fallback is used to evade ad or access requirements. DRM requirements remain provider/device dependent.

## Official implementation references

- [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3), stable 1.11.1 during implementation.
- [ExoPlayer server-side ad insertion](https://developer.android.com/media/media3/exoplayer/ad-insertion#server-side-ad-insertion).
- [IMA DAI URI builder source](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_ima/src/main/java/androidx/media3/exoplayer/ima/ImaServerSideAdInsertionUriBuilder.java).

## Remaining limits

Only the two supplied full episodes have been playback-tested in the emulator. No ad break was served during those checks, so successful ad delivery is not yet validated. Captions, physical-TV audio/output, DRM-protected episodes, other regions and long-session expiry behavior need separate acceptance testing. Metadata surveying does not establish playback availability for each listed episode.

## Historical Spanish source discovery

The official cc.com homepage links “Episodios en Español” to https://southpark.cc.com/es/seasons/south-park. Its indexed official catalog links Spanish episode pages under /es/episodios/, including https://southpark.cc.com/es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1. On this Mac both URLs redirect to the same paths on www.southparkstudios.com and return 404. https://www.southpark.lat/ redirects to the English homepage. No Spanish catalog JSON, native playback session or audio was accessible locally; no CDN endpoint is inferred. See [exact checks and emulator evidence](evidence/5-codex-spanish/README.md) and [implementation limits](multiregion.md).

The experimental language selector and regional implementation have been withdrawn. This discovery record does not describe a supported app source.
