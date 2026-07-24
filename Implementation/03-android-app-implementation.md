# Android App — Implementation Plan

Build plan for the phone client per
[Design/03-android-app-design.md](../Design/03-android-app-design.md). This
is the **third** subsystem to implement, after `core-*` and the Windows
app — by this point the sync protocol has already been proven between two
desktop instances, so this phase is mainly about the mobile-specific
transport (NSD/Nearby), UI, and background scheduling, not re-litigating
the merge logic.

## Project structure

```
android-app/
  src/main/kotlin/et/android/
    MainActivity.kt
    di/AppModule.kt                  // Hilt bindings
    sync/NsdTransport.kt
    sync/NearbyTransport.kt
    sync/CompositeTransport.kt        // tries NSD, falls back to Nearby
    sync/SqlDelightOperationStore.kt
    sync/SyncWorker.kt                // WorkManager periodic job
    db/Database.kt                    // SQLDelight Android driver setup
    repository/SqlDelightRepository.kt
    ui/household/HomeScreen.kt, AddExpenseScreen.kt
    ui/trip/TripListScreen.kt, TripDetailScreen.kt,
       AddTripExpenseScreen.kt, SettleUpScreen.kt
    ui/devices/DevicesScreen.kt, PairingScanScreen.kt
    ui/settings/SettingsScreen.kt
    ui/components/BudgetStatusBanner.kt // shared OK/NEARING/OVER widget
    notifications/BudgetAlertNotifier.kt
  src/main/sqldelight/et/android/db/
    Schema.sq                         // same schema as windows-app's
  src/main/AndroidManifest.xml
  build.gradle.kts
```

`Schema.sq` is the same file content as the Windows app's — since
SQLDelight compiles per-target from shared `.sq` source, this should
actually live in a small shared Gradle source set both apps include
(e.g. `core/db-schema/`) rather than being copy-pasted; call this out
explicitly during M1 below so it doesn't silently drift into two files.

## Dependencies

- Jetpack Compose (BOM-managed), Compose Navigation.
- `androidx.hilt` for DI.
- `androidx.work:work-runtime-ktx` for background sync.
- `app.cash.sqldelight:android-driver`.
- `com.google.android.gms:play-services-nearby` (Nearby Connections).
- `androidx.camera:camera-camera2/lifecycle/view` + `com.google.mlkit:barcode-scanning`
  for QR pairing.
- Depends on `core:model`, `core:sync`, `core:domain` (android target).

## Build milestones

1. **M1 — Project scaffold + local-only household expenses.** Hilt/DI,
   Room→SQLDelight Android driver wired, Home + Add Expense screens
   functional with **no sync at all** — pure local CRUD through
   `core-domain` use cases. Also: move `Schema.sq` into a shared module at
   this point so both apps compile from one file going forward.
2. **M2 — Weekly budget alerting UI.** `BudgetStatusBanner` component,
   notification channel + `BudgetAlertNotifier`, verified against the
   `core-domain` `evaluateBudget` unit tests already written — this
   milestone is mostly UI wiring since the logic itself was already built
   and tested in the core-logic phase.
3. **M3 — Trip feature.** Trip list/detail, add trip expense with all four
   split modes, settle-up screen using the debt-simplification suggestions.
4. **M4 — Device pairing.** CameraX + ML Kit QR scan/display, two-step
   handshake per [Design/01-core-logic-design.md](../Design/01-core-logic-design.md#pairing--crypto).
5. **M5 — LAN sync (NSD).** `NsdTransport` implementing `core-sync`'s
   `Transport`; verify against a **running Windows app instance** on the
   same Wi-Fi — first real phone-to-desktop sync, exercising the exact
   protocol already proven between two desktop instances in the Windows
   milestone plan.
6. **M6 — Phone-to-phone + Nearby fallback.** `NearbyTransport`,
   `CompositeTransport` fallback ordering; verify two phones sync directly
   with Wi-Fi turned off/no shared network, and separately verify the
   gossip case (phone A syncs to server, then phone B syncs to server
   later and receives A's data without ever meeting A directly).
7. **M7 — Background scheduling.** `SyncWorker` periodic job, NSD
   discovery-callback-triggered sync, pull-to-refresh manual sync; battery/
   Doze-mode behavior checked on a real device, not just an emulator.
8. **M8 — Polish.** Settings screen, category management, error states,
   empty states.

## Verification approach

From M5 onward, verification requires the Windows app already built
(previous subsystem) and, from M6, at least two physical Android devices
or emulators with networking configured to simulate no-shared-LAN
conditions — call this out as a prerequisite before scheduling those
milestones, since it's the one place this plan depends on hardware rather
than pure unit/integration tests.
