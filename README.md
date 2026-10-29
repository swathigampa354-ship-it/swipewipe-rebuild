# Swipewipe Rebuild

A faithful, from-scratch native Android rebuild of the Swipewipe gallery
cleanup app (`com.afternoonproducts.swipewipe` v2.19.00), produced by pure
static analysis: 52/52 classes decompiled and behavior mapped in the
[swipewipe-static-analysis](https://github.com/swathigampa354-ship-it/swipewipe-static-analysis)
evidence repo.

**No original code, assets, or third-party SDK payloads are copied.** This is a
reimplementation: all logic here was rewritten against the observed behavior
(schema strings, worker names, notification channels, PM2.5 analytics surface
equivalents, Graph protocol flow). Only factual identifiers (Room table names,
`force_full_resync` key, channel ids) were reused as required for behavioral
parity.

## What it does

- **Deck** — swipe full-screen photo/video cards: left = delete, right = keep,
  with optional per-delete confirmation, MediaStore-backed deletion, undo stack,
  and decision bookkeeping in a local Room DB.
- **Groups** — incremental perceptual-hash (average hash, Hamming distance ≤ 16)
  duplicate-groups index, deletable whole-group.
- **Bookmarks** — per-source media bookmark table matching the recovered schema
  (`bookmarks(mediaId, mediaSource, createdAt)`).
- **Stats** — per-source swiped/deleted/`size-freed` tracking, monthly
  completed-period bookkeeping, OneDrive month aggregates surface.
- **Settings** — deck sort order, confirm-delete, dark theme, app lock
  (biometric), JPEG compress quality, OneDrive scope, maintenance rescans.
- **Workers** — WorkManager jobs for similar-image scans, cloud sync
  (`force_full_resync` data flag), photo compression with cancel receiver,
  nightly reminders, boot receiver to re-schedule.
- **OneDrive** — real Microsoft Graph flow: PKCE OAuth via the system browser
  (`swipewipe-rebuild://graph-auth`), token refresh, `$batch` item deletion,
  month aggregation into the recovered tables.

## Requirements to build

- JDK 17 (CI uses Temurin 17)
- Gradle 8.10.2 (used directly in CI; no wrapper committed)
- Android SDK 36, platform tools

## Build it

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/*.apk
```

CI (`.github/workflows/build.yml`) builds a debug APK on every push to
`master`/`main` and exposes it as both a workflow artifact and an untagged
release asset under the `build-<sha>` tag.

## First-run setup

1. Grant Photos/Notifications permissions (the app asks on open and limits
   itself to the photo picker route — no `MANAGE_EXTERNAL_STORAGE`).
2. Register your own Microsoft Entra app (Public client, redirect URI
   `swipewipe-rebuild://graph-auth`) and paste the client id in Settings →
   OneDrive, then tap "Sign in to OneDrive".
3. Compress/scan/sync run on demand; allow WorkManager dismissal of system
   dialogs if you use the "confirm in system dialog" deletion route.

## Known gaps / credentials needed

- **Expo / EAS build** — no Expo token was provided, so EAS is unused. CI
  (GitHub Actions) is the documented build path instead.
- **Microsoft Graph client id** — not shipped by design; user-supplied.
- **Ad/monetization SDKs intentionally excluded** — AppLovin MAX, ironSource,
  Moloco, Vungle, BidMachine, Fyber, AppsFlyer, Facebook, Firebase,
  GMS-ads, Billing are not rebound here.
- Streak / cooldown notification cadence is a best-effort approximation of
  the original's reminder scheduling.
- UI is a behavioral rebuild, not a pixel-accurate clone; no original design
  assets were recovered from static analysis.

## License

MIT — see `LICENSE`.
