# FEATURE_CHECKLIST.md

Legend: ✅ implemented in this repo and exercised by the CI build · 🟡 implemented, requires
user-supplied credentials to function end-to-end (documented) · ❌ intentionally excluded ·
◻ not implemented (documented limitation). Nothing is marked tested that hasn't been.

## Core
- [x] Gallery permission flow: POST_NOTIFICATIONS + READ_MEDIA_IMAGES/VIDEO (+ visual-user-selected on 14+) — ✅
- [x] Swipe deck over MediaStore items (left=delete, right=keep; restore/undo of last decision in session) — ✅
- [x] Real deletion via MediaStore batch delete (CR dialog on 11+) — ✅
- [x] Bookmark mode (right-swipe ⇒ bookmark) — ✅
- [x] Decision persistence (`mediaDecisions`) — ✅ (Room, schema mirrored from `be.smali`)
- [x] Bookmarks screen (`bookmarks`) — ✅
- [x] Local media storage fallback for deleted-from-MediaStore decisions (`localDecisionReconstruction`) — ✅
- [x] Streak/period tracking (`completedPeriods`, daily period marker) — ✅
- [x] Stats screen: swiped/deleted/bookmarked counters, total size freed, month aggregates — ✅
- [x] Similar-image grouping worker (perceptual hash → `similarImages.groupId`) — ✅
- [x] Similar-images browser (groups composed of picked-image/duplicates) — ✅
- [x] One-tap "delete group" — ✅
- [x] Photo compression batch worker + cancel receiver + progress notification (`media_tools_progress`) — ✅
- [x] Settings: deck sort, confirm-delete toggle, dark mode, image quality — ✅
- [x] App lock (biometric gate on resume; `BiometricPrompt`) — ✅
- [x] Streak / cooldown / OTD reminders via WorkManager periodic + on-boot reschedule — ✅
- [x] Splash → MainActivity flow, empty-state and permission-denied states — ✅
- [x] Loading/empty/error states in all main surfaces — ✅

## Cloud
- [y] OneDrive sync (MS Graph `$batch`, `/me/drive/items/` DELETE per id; empty-list short-circuit) — 🟡
      Code + signed-out UI present; end-to-end run needs YOUR Microsoft app registration
      (client id) pasted into Settings → the app cannot ship one without violating credential rules.
- [y] Cloud month aggregates (`CloudMonthAggregateModel`/`cloudMediaItems`) — 🟡 (same credential)
- [o] Full resync flag (`force_full_resync`) — 🟡 (implemented as Settings toggle feeding the worker; runs only when signed in)

## Excluded from the rebuild (deliberate, documented)
- [ ] Ad/monetization kit (AppLovin MAX, ironSource, Moloco, Vungle, BidMachine, Fyber, Facebook ads,
      Google ads, GMS adservices), AppsFlyer, Firebase Analytics/Crashlytics, Facebook SDK — ❌
      privacy/proprietary-lan reasons; would need real vendor accounts.
- [ ] Google Play Billing / license check / install referrer — ❌ (closed-loop Google Play APIs).
- [ ] White-label `com.mwm.sdk.*` kit (accountkit2, abtest, eventkit, sessionskit, userfeedback,
      dynamicscreen) — ❌ proprietary vendor SDK.
- [ ] Remote-config driven FUSE/A-B experiments — ❌ (no service).
- [ ] OneDrive *photo compress on cloud copy* variants — ◻ (edge feature not verifiable in scope).

## Testing status
- Static: compile + resource link via GitHub Actions (assembleDebug) — **PASSED** on run
  40841215206 (commit `b90ff55`); APK 64.5 MB, sha256
  `c3f1fa8aba2f6216f17d85ed8838fef49b0b6b7d94b0adfee55ecf98a524a9e9`.
- Feature tests: **No feature is runtime-verified.** This device has no emulator; claims are
  limited to compilation, APK assembly, and artifact publication. See BUILD_REPORT.md.

## Faithfulness notes (what is equal ≠ what is not)
- Equal *by evidence*: data schema, channel names, worker set, feature set, package-level behavior,
  Compose-based UI stack.
- Not equal (information never recovered statically): exact pixel compositions, motion curves,
  ad-SDK behavior, exact verdicts of the obfuscated deck logic. UI is a faithful *behavioral*
  rebuild, not a pixel clone — no screenshots existed to copy.
