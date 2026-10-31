# Publish an app update

The catalog's **App updates** button opens a manual check. The app calls `https://api.github.com/repos/JoseLuna12/southparkapp/releases/latest` without credentials. It selects the `update.json` asset, then requires its APK URL and size to match an asset in that same stable release. Drafts and prereleases are excluded. Missing releases, API rate limits and network failures show a retryable error. Checks never block catalog loading.

This implements the proposal in PR #4 with José's changes: public GitHub API, manual checks, and FileProvider plus Android's installer instead of PackageInstaller sessions. No custom server or updater service. FileProvider comes from the AndroidX Core dependency already supplied by Media3.

## Build with a permanent signing key

1. Keep the application ID `com.unofficial.southparktv`. Increase `versionCode` for every release; this build is code 7, name 0.6.0. The integer code, not the tag or version name, determines eligibility.
2. Keep a permanent keystore and secure backup outside the repository. Set `SPTV_KEYSTORE` to its absolute path, `SPTV_STORE_PASSWORD`, `SPTV_KEY_ALIAS` and `SPTV_KEY_PASSWORD` in the publishing environment. Do not put secrets in commands, commits or logs. Release signing fails if these are missing.
3. Run `./gradlew :app:assembleRelease`. Verify `app/build/outputs/apk/release/app-release.apk` using the SDK's `apksigner verify --print-certs`. Compare the certificate with the installed app before rollout.

Existing debug installations can only update with the same debug certificate. A differently signed release requires an explicit manual migration; uninstalling can erase history and preferences. The updater never uninstalls or rotates keys. Bootstrap the updater with the intended permanent key before relying on it for future updates.

## Publish a stable GitHub release

1. Create a draft release in `JoseLuna12/southparkapp`, for example tag `v0.6.0`.
2. Upload the complete signed APK as `southpark-tv-0.6.0.apk`. Do not upload an AAB or split APKs.
3. Compute SHA-256 and byte count from that final signed file, using `shasum -a 256` and `wc -c`. Create `update.json` with those values and the APK's actual package/version/minSdk:

```json
{
  "schemaVersion": 1,
  "applicationId": "com.unofficial.southparktv",
  "versionCode": 7,
  "versionName": "0.6.0",
  "minSdk": 26,
  "apkUrl": "https://github.com/JoseLuna12/southparkapp/releases/download/v0.6.0/southpark-tv-0.6.0.apk",
  "sha256": "REPLACE_WITH_64_HEX_DIGITS_FROM_FINAL_APK",
  "sizeBytes": 12345678
}
```

4. Replace the example size and hash. Upload `update.json` to the same release. Verify both assets before publishing as the latest stable release. Keep published assets immutable; fix mistakes with a higher version code.
5. On an older same-signed installation, choose **App updates**, **Check for update**, **Download update**, then **Install**. Grant this app permission in Android settings if requested, return and select Install again. Confirm installation in Android's system UI.

## Verification and recovery

Metadata is bounded to 64 KiB, the API response to 1 MiB and APKs to 200 MiB. TLS is mandatory. Downloads allow at most five redirects to GitHub or its release-assets host, with connection/read timeouts and a ten-minute APK transfer limit. The APK is kept under private cache and only that directory is exposed through the non-exported FileProvider with temporary read grants. No shared-storage permission is needed.

Before installation the app checks exact size, SHA-256, package, strictly greater installed version code, metadata version/name/minSdk, device SDK and exact installed signing certificates. Signature rotation is intentionally unsupported. Android also verifies the package at installation. Opening the installer does not count as success; the next manual check reads the installed package version again.

Back or leaving the update screen cancels current work and discards stale callbacks. Downloads do not survive process death. A later download overwrites any previous cache file; Android may evict cache at any time. Returning from settings requires another explicit Install action. Cancelled installation can be retried while the screen remains alive; after recreation, check/download again. Download status is indeterminate, with Back available to cancel.

No release or permanent key is created by this change. The current repository has no latest stable release, so a real update cannot yet complete. Physical-TV installer support, D-pad behavior of vendor settings, same-signature upgrade and history preservation require device testing. No physical TV was used.
