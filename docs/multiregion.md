# Official language sources

The header opens a D-pad language dialog: English and Spanish are both selectable. Spanish now requests the published official Spanish catalog link, not a fabricated LATAM endpoint. It is not verified playable from this Mac. See [HTTPS and emulator evidence](evidence/5-codex-spanish/README.md).

## Discovery and current limit

The official [South Park homepage](https://southpark.cc.com/) links “Episodios en Español” to [the Spanish catalog](https://southpark.cc.com/es/seasons/south-park). That catalog's indexed official page links [Cartman consigue una sonda anal](https://southpark.cc.com/es/episodios/940f8z/south-america-cartman-consigue-una-sonda-anal-temporada-1-ep-1). Its terms link points to www.southpark.lat. These establish official provenance and public Spanish page paths, not audible Latin American dubbing or native streaming compatibility. The es-419 field is expected language only.

Real curl requests from this Mac, with certificate verification and normal network identity, return:

| Requested URL | Observed result |
| --- | --- |
| https://www.southpark.lat/ | 301 to https://www.southparkstudios.com/, then 200 English homepage |
| https://southpark.cc.com/es/seasons/south-park | 301 to https://www.southparkstudios.com/es/seasons/south-park, then 404 |
| https://www.southparkstudios.com/es/seasons/south-park | 404 |
| Official Spanish episode linked above | 301 to the same Spanish path on www.southparkstudios.com, then 404 |

A different fetch service sees Spanish metadata at the official cc.com link. That does not prove this Mac has access. The local 301/404 is compatible with regional routing or changed site configuration; no explicit geoblock response was received, so the cause is not established.

## Implementation

RegionalSource centralizes source origins, public page paths and network gates. The Spanish profile points to https://southpark.cc.com with /es/seasons/south-park and /es/episodios/. No Spanish pagination route, playback service host/path or platform is authorized because those contracts could not be observed here. If a future response supplies pagination or native playback, the app fails closed until those contracts are verified. This is a real selectable catalog connection with a visible failure on this network, not completed Spanish playback support.

Changing language invalidates catalog/recommendation generations, cancels pending catalog work and recreates MainActivity. Each activity owns its immutable source selection, fresh views, executors and artwork cache/binding state. Destroyed activity callbacks cannot update the new catalog. Failed Spanish loading retains the Spanish label, empty lists and Retry; it never silently loads English. Returning to English uses its own season/history state. Player launch carries the source explicitly through Next/autoplay and recreation.

US keeps the original playback and MainActivity preference files. Spanish uses playback:latam and catalog:latam. History decoding and saves validate against the supplied source; no positions are copied between sources, even for matching episode IDs.

Safe catalog request logging records only source ID and the public catalog URL. Rejected-redirect logging records source ID and destination host only. Session URLs, tokens, signed media URLs, cookies and response headers are not logged or published.

## Validation and pending work

assembleDebug, all 27 JVM tests, lintDebug and the 3 design-token checks pass. Tests cover published Spanish page resolution, rejection of English/cross-origin fallback, profile-specific history decoding and existing US parsing/history behavior.

On emulator-5554 with the installed current APK, D-pad selection makes a real request to the Spanish catalog. The official redirect to www.southparkstudios.com is rejected before following it. Spanish remains selected after process restart. No old English cards appear. D-pad return to English restores its real 14-episode Season 12 catalog. Captures and sanitized logs are in docs/evidence/5-codex-spanish/.

Spanish catalog JSON, complete pagination, session, actual Latin American audio, captions, ads and DRM remain unverified. No Spanish playback was attempted because there is no accessible Spanish episode catalog on this connection. Full playback/audio smoke and full TV UI regression suites were not rerun. No physical TV, VPN, proxy, geographic headers, alternate stream source, auth/DRM bypass, auto-update or merge was used.

To finish Spanish playback, obtain legitimate access to the published source, inspect its actual catalog and fresh session contract, authorize only the observed endpoints and add independent fixtures. Then test audible dubbing, real caption cues, ads, history/Continue/Next and cancellation under delayed two-source responses. The current change cannot honestly claim that Spanish playback works.
