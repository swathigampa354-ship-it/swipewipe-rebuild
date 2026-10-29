# PROJECT_RESEARCH.md — Swipewipe (com.afternoonproducts.swipewipe) recovery

_Date: 2026-10-29. Everything below was read back from real artifacts produced by this
project line; nothing is claimed from memory of documents I could not inspect._

## Sources actually inspected (this line of work)

| source | location | status |
|---|---|---|
| Base APK sha256 `cfe3d01eed1351737b0894ea2cba85bea8888dd8038b5cfe71cbc4fc36380e22` (92,066,971 B), v2.19.00 (21900), minSdk 32 / targetSdk 36 | decoded on device, `~/rea/swipewipe/base.apk` → `~/rea/swipewipe/apktool_out/`, `~/rea/swipewipe/mcp_out/` | VERIFIED, full tree |
| 52/52 in-package classes decompiled (48 Java + 4 smali-fallback) via jadx-headless-mcp 0.7.1 | `~/rea/swipewipe/mcp_out/classes/` | VERIFIED |
| GitHub `swathigampa354-ship-it/APK_swipewipe_lm_arena` — conversation archive, prior reports: `swipewipe-final-report.md`, `swipewipe-investigation-report.md` | fetched raw over HTTPS, main branch | VERIFIED |
| GitHub `swathigampa354-ship-it/swipewipe-recon` — earlier recon snapshot (evidence vault: logs, `static/network_security_rules.xml`, `static/strings.xml`, MCP transcript) | tree listing verified; individual files NOT re-fetched unless cited | VERIFIED (structure) |
| GitHub `swathigampa354-ship-it/swipewipe-static-analysis` — our 52-class decompilation package | pushed this session | VERIFIED |

## Things I did NOT have (honest gaps)

- **No screenshots, Figma files, or design assets** of the original app were ever produced by the
  research line (static-only analysis; UI code in `wv2.l`/`zd0.a`-style obfuscated out-of-package
  classes was explicitly out of scope). The visual identity below is *inferred* from Compose
  artifacts, resource names, and Material conventions, not copied from screenshots.
- **No Expo token** was made available in this conversation (only a GitHub token). EAS branches are
  therefore not executable — the build path is GitHub Actions (the app is native Kotlin, not Expo).
- Out-of-package engine classes (`defpackage.z44` = the actual OneDrive sync engine,
  `ss/av3/m10/qm3/u92/i22/h70` DAO impls) were never decompiled; their behavior is inferred from
  the in-package interfaces they implement.
- Ad/monetization kit internals (`com.mwm.sdk.*`, AppLovin MAX, ironSource, AppsFlyer, Firebase)
  are documented from the manifest only and are **intentionally not rebuilt**.

## Verified product facts

### Identity & stack
- Native Kotlin app (Jetpack Compose, Hilt DI, Room, WorkManager, Glide, Retrofit for Microsoft
  Graph). No React Native/Flutter (`no index.android.bundle`, `no flutter_assets`).
- Local-first media-triage ("swipe-to-delete") gallery with cloud sync **only for OneDrive**.

### Persistence — exact schema recovered from the built APK
`apktool_out/smali/be.smali:34-58` (Room open delegate) — seven tables, re-created verbatim here:

```sql
CREATE TABLE IF NOT EXISTS `bookmarks` (`mediaId` TEXT NOT NULL, `mediaSource` TEXT NOT NULL,
  `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`mediaId`, `mediaSource`));
CREATE TABLE IF NOT EXISTS `stats` (`mediaSource` TEXT NOT NULL, `swipedCount` INTEGER NOT NULL,
  `deletedCount` INTEGER NOT NULL, `totalSize` INTEGER NOT NULL, `timesSeenCount` INTEGER NOT NULL,
  PRIMARY KEY(`mediaSource`));
CREATE TABLE IF NOT EXISTS `cloudMediaItems` (`id` TEXT NOT NULL, `mediaSource` TEXT NOT NULL,
  `name` TEXT NOT NULL, `displayUri` TEXT, `/* facets + size/timestamps columns */`);
CREATE TABLE IF NOT EXISTS `similarImages` (`id` TEXT NOT NULL, `signatureBytes` BLOB NOT NULL,
  `groupId` TEXT, `modifiedMs` INTEGER NOT NULL, /* + mediaId/mediaSource columns */);
CREATE TABLE IF NOT EXISTS `mediaDecisions` (`mediaSource` TEXT NOT NULL, `mediaId` TEXT NOT NULL,
  `decision` TEXT NOT NULL, PRIMARY KEY(`mediaId`, `mediaSource`));
CREATE TABLE IF NOT EXISTS `localDecisionReconstruction` (`id` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `completedPeriods` (`mediaSource` TEXT NOT NULL, `year` INTEGER NOT NULL,
  `month` INTEGER NOT NULL, PRIMARY KEY(`mediaSource`, `year`, `month`));
```
(Full column lists verified in `apktool_out/smali/be.smali:34-58` and mirrored into
`rebuild/app/src/main/kotlin/…/db`. DB identity hash `d1b7c74f2565dd8ee4c3edcc34f395e5`.) 

### Feature surface (from class inventory + manifest permission set + channel strings)
1. **Swipe deck** over local gallery photos/videos: left = delete/decline, right = keep/bookmark,
   per-source deck (`swipeable_card.SwipeableCardState`, `MainActivity`, `SplashActivity`).
2. **Decisions persisted** (`mediaDecisions`, `bookmarks`, `localDecisionReconstruction`,
   `completedPeriods` staging) — deletions happen via scoped `MediaStore` delete
   (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`).
3. **Stats** (`stats`, streak + cooldown notification channels; month aggregates
   `CloudMonthAggregateModel`, `completedPeriods`).
4. **Similar-image grouping** on-device (`image_grouping.ImageGroupingWorker`,
   `SimilarImageModel.signatureBytes` — byte-signature based, `similar_images` channel).
5. **Local reminders** (`push_local.OtdReminderWorker`, `SimilarImagesReminderWorker`,
   `push_local_schedule.AlarmReceiver`, `boot.BootReceiver`; channels `Swipewipe`, `streak`,
   `cooldown`, `similar_images`, `media_tools_progress` — channel names VERIFIED in
   `application.SwipeApp.java:343-351`).
6. **Photo compression batch tool** (`photo_compress_batch.PhotoCompressBatchWorker` +
   `PhotoCompressBatchCancelReceiver`, progress channel `media_tools_progress`).
7. **OneDrive cloud sync + month aggregates** (`one_drive_data_source.*`, hand-written MS Graph
   REST client: `/me/drive/items/`, batch `$batch` requests
   — `OneDriveDataSourceManagerImpl.smali:2167+`; empty-list inputs short-circuit to success
   without network, smali:2389-2400). Gated by `force_full_resync` worker input
   (`CloudProviderSyncCoroutineWorker.java:349-370`). **Requires a Microsoft app registration
   (client id) — proprietary credential we do not have.**
8. **App lock** (`USE_BIOMETRIC`, `USE_FINGERPRINT` permissions).
9. **Boot-resilient scheduling** (`RECEIVE_BOOT_COMPLETED`, periodic works named
   `similar_images_reminder_periodic` etc., `SwipeApp.java:401`).
10. Ads/attribution/consent kit (AppLovin MAX, ironSource, AppsFlyer, GMS, Billing, Google UMP,
    Facebook, install-referrer, adservices) — **present in the original, intentionally excluded
    from the rebuild** (third-party ad SDKs are proprietary, need real account credentials, and
    an open-source rebuild should not ship adware). Documented limitation, not a silent omission.

### Security findings carried into the rebuild (do not repeat)
- Cleartext HTTP override for `10.0.2.2`/`0.0.0.0` in release (`res/xml/network_security_rules.xml:3-5`)
  — the rebuild ships **no** cleartext exception.
- `google_api_key` in `res/values/strings.xml:860` — the rebuild must never ship real keys;
  user-supplied configuration (datastore/secret) only.
- `allowBackup=false` retained.

## License/attribution obligations for the rebuild
- The original app's code is proprietary; **no proprietary code is copied**. Only facts (schema
  DDL read from smali strings, feature names, channel names) — facts are not copyrightable and are
  preserved verbatim in the fresh code.
- Third-party dependencies in the rebuild are permissive (Apache-2.0/AndroidX) and carry their
  licenses via the standard NOTICE; repo will include a LICENSE (MIT) for the rebuild's own code.
