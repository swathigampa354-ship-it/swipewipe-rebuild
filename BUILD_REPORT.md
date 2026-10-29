# BUILD_REPORT.md

**Project:** Swipewipe Rebuild — native Android reimplementation of `com.afternoonproducts.swipewipe` v2.19.00, derived purely from static analysis (see [swipewipe-static-analysis](https://github.com/swathigampa354-ship-it/swipewipe-static-analysis)).

## Build result

| Item | Value |
|---|---|
| Build path | GitHub Actions (android runner, JDK 17 Temurin, Gradle 8.10.2) |
| Repo/commit | `swathigampa354-ship-it/swipewipe-rebuild` @ `b90ff55` |
| CI run (green) | https://github.com/swathigampa354-ship-it/swipewipe-rebuild/actions/runs/40841215206 |
| Artifact | `swipewipe-rebuild-debug-apk` (workflow artifact) + release asset on tag `build-b90ff55…` |
| APK | `app-debug.apk`, 64,577,392 bytes |
| sha256 | `c3f1fa8aba2f6216f17d85ed8838fef49b0b6b7d94b0adfee55ecf98a524a9e9` |
| Public download | https://github.com/swathigampa354-ship-it/swipewipe-rebuild/releases/download/build-b90ff5544bb0c2068b33c1c71b9912f960965707/app-debug.apk |
| Local mirror | `~/Downloads/swipewipe-rebuild-app-debug.apk` on the build device (sha256 verified by `sha256sum`) |

Build identity: `com.swipewipe.rebuild`, versionName 1.0.0, versionCode 1, minSdk 32, targetSdk 36, debug (unminified) build type. It installs side-by-side with any original Swipewipe install — nothing is overwritten.

## CI iteration log (honest)

- Run 1 (`a52da33`) — FAILED, 54 Kotlin errors (missing imports, DAO name clashes, suspend/signature types, two typos, an OkHttp deprecated API). Fixed in `57cb18b`.
- Run 2 (`57cb18b`) — FAILED, 2 errors (second DAO `forSource` JVM clash, a stray root-package `GraphManager` import). Fixed in `828bea8`.
- Run 3 (`828bea8`) — FAILED, 1 error (third DAO `observe` clash). Fixed in `589cfb9`.
- Run 4 (`589cfb9`) — FAILED, 1 error (JVM-signature clash on `upsertAll` across three DAOs — renamed to `upsertCloudItems`/`upsertAggregates`). Fixed in `b90ff55`.
- Run 5 (`b90ff55`) — **SUCCESS**, debug APK produced, uploaded as artifact and attached to a `build-<sha>` release tag.

## What the APK contains (feature parity summary)

Deck swipe (delete/keep/bookmark, confirm-delete funnel, undo), MediaStore-backed real deletion, bookmarks screen, per-source stats + size-freed + monthly completed-period bookkeeping, incremental perceptual-hash duplicate grouping + one-tap group delete, photo-compression batch worker with cancel receiver and progress channel, streak/cooldown/similar reminder scheduling (WorkManager periodic + boot reschedule), same five notification channel ids/names as the original (`Swipewipe`, `streak`, `cooldown`, `similar_images`, `media_tools_progress`), same Room table names as recovered from decompiled code, biometric app lock, settings surface, OneDrive via Microsoft Graph (PKCE, refresh, `$batch` delete, month aggregates) with `force_full_resync`.

## What is NOT identical (documented gaps)

1. **UI composition** — no design assets were recoverable statically; the screens are a faithful *behavioral* rebuild (flows, states, actions, ids), not a pixel/inventory clone.
2. **OneDrive sign-in requires YOUR Microsoft client id** — register a public-client app (redirect `swipewipe-rebuild://graph-auth`), paste the id in Settings. The rebuild deliberately ships no credential.
3. **Expo/EAS** — no Expo token was ever provided, so EAS is unused; GitHub Actions is the build path.
4. **Ad/monetization/telemetry SDKs excluded by design** — AppLovin MAX, ironSource, Moloco, Vungle, BidMachine, Fyber, Facebook, AppsFlyer, Firebase, GMS-ads, Play Billing, `com.mwm.sdk.*` white-label kit, remote-config FUSE experiments. None are rebound; no fake shims were added.
5. **Runtime behavior beyond compile** is not claimed: this device cannot run an emulator, so assertions are limited to "code compiles, resources link, APK assembles and is signed(esque) for debug". Feature runtime should be exercised on a real device; the phrase "feature-tested" is reserved accordingly.
6. **Streak/reminder cadence** is a best-effort approximation of the original's scheduling (exact remote-config parameters were never recovered).

## Build instructions (anyone with the repo)

```bash
git clone https://github.com/swathigampa354-ship-it/swipewipe-rebuild
cd swipewipe-rebuild
gradle assembleDebug        # Gradle 8.10.2, JDK 17, Android SDK 36
adb install -r app/build/outputs/apk/debug/*.apk
```

Every push to `master` re-runs the workflow and re-publishes `build-<sha>` release assets automatically.

## Security / credential hygiene

- The GitHub token used for pushing lives only in the shell environment of the push commands; it was never written to any file, `.git/config`, remote URL, workflow, or log.
- No Google API key, Microsoft client id, or ad-SDK key is present in the repo (the redaction in the analysis repo's decompiled `strings.xml` was preserved).
- `network` config: the rebuild does **not** replicate the original's cleartext-traffic allowance.
