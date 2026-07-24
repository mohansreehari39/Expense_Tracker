# Windows App — Implementation Plan

Build plan for the desktop app per
[Design/02-windows-app-design.md](../Design/02-windows-app-design.md). This
is the **second** subsystem to implement, right after `core-*`, and
deliberately *before* the Android app — it's the fastest way to exercise
the shared core end-to-end (real persistence, real sockets, real UI) since
desktop iteration doesn't need an emulator or device, and multiple
instances can be run locally to prove multi-peer sync actually works
before a phone is involved.

## Project structure

```
windows-app/
  src/jvmMain/kotlin/et/windows/
    Main.kt                       // entry point, tray setup
    tray/TrayController.kt
    server/Routes.kt               // Ktor routing, /api/v1/*
    server/WebSocketHub.kt         // /ws/changes broadcast
    sync/JvmTransport.kt           // JmDNS + socket Transport impl
    sync/SqlDelightOperationStore.kt
    db/Database.kt                 // SQLDelight driver setup
    repository/SqlDelightRepository.kt // implements core-domain Repository
    ui/DashboardApp.kt             // Compose Desktop root
    ui/screens/HouseholdScreen.kt, TripsScreen.kt, TripDetailScreen.kt,
       AnalyticsScreen.kt, DevicesScreen.kt, SettingsScreen.kt
    config/Config.kt               // %APPDATA% json load/save
  src/jvmMain/sqldelight/et/windows/db/
    Schema.sq                      // log table + materialized tables
  src/jvmMain/resources/web/
    index.html, app.js             // thin web view
  build.gradle.kts
```

## Dependencies

- `io.ktor:ktor-server-netty`, `ktor-server-content-negotiation`,
  `ktor-server-websockets`, `ktor-serialization-kotlinx-json`.
- `app.cash.sqldelight:sqlite-driver` (JVM/SQLite driver).
- `org.jmdns:jmdns` for LAN discovery.
- `org.jetbrains.compose` (Compose Multiplatform Desktop).
- Depends on `core:model`, `core:sync`, `core:domain` (jvm target).

## SQLDelight schema (`Schema.sq`)

Two tiers per [Design/01-core-logic-design.md](../Design/01-core-logic-design.md):
- `operationLog` table: `op_id TEXT PRIMARY KEY`, `entity_type`,
  `entity_id`, `op_type`, `patch_json`, `author_device_id`, `hlc_physical`,
  `hlc_counter`, `received_from` — append-only, indexed on
  `(author_device_id, hlc_physical, hlc_counter)` for frontier queries.
- Materialized tables mirroring each entity in
  [Arch/02-data-model.md](../Arch/02-data-model.md) (`household_expense`,
  `trip`, `trip_expense`, `expense_split`, `settlement`, etc.), rebuilt
  incrementally as operations are applied (`SqlDelightRepository` re-runs
  the fold from [Design/01-core-logic-design.md](../Design/01-core-logic-design.md#conflict-resolution-implementation-of-the-arch-rule)
  only for the affected `entity_id`, not the whole table).

## Build milestones

1. **M1 — Project scaffold + DB wired.** Gradle module set up, SQLDelight
   schema compiles, `SqlDelightOperationStore` + `SqlDelightRepository`
   pass the same contract tests written against `core-sync`'s
   `FakeOperationStore` (reuse the interface's test suite against the real
   implementation — cheap way to catch driver-specific bugs early).
2. **M2 — Ktor REST API, no sync yet.** All `/api/v1/*` endpoints from the
   design doc, backed by the real DB, manually exercised via `curl`/REST
   client. This alone is already a usable single-machine expense tracker.
3. **M3 — Compose Desktop UI shell.** Household + Add Expense screens
   hitting the REST API (not the DB directly, per design). Prove the
   weekly budget status banner renders and updates live via the
   `/ws/changes` socket.
4. **M4 — `JvmTransport` (JmDNS + sockets).** Wire `core-sync`'s
   `SyncSession` to a real transport; run **two instances of the Windows
   app on one machine** (different ports/data dirs) and confirm they
   discover each other and converge — this is the first real-network
   proof point, no phone needed yet.
5. **M5 — Trips + Analytics + Devices screens.** Round out the UI to match
   the full design doc, including the settle-up suggestion panel and the
   pairing QR flow (generation side only — scanning happens on Android, but
   the Windows app must still be able to display an invite QR).
6. **M6 — Tray/lifecycle + packaging.** `SystemTray` integration,
   start-on-login, graceful shutdown; package via `jpackage` into a
   Windows `.exe`/installer for actually running this outside a dev
   environment.
7. **M7 — Thin web view.** Static HTML/JS page hitting the same API,
   served at `/`, for phone-browser access to the dashboard.

## Manual verification per milestone

Since there's no Android app yet at this stage, M4's multi-peer proof uses
two local instances (or one instance + a `core-sync` integration test
harness that spins up a bare `JvmTransport` without the full UI) — this
substitutes for "sync with a real phone" until the Android subsystem
exists, and should give high confidence the protocol itself is sound
before mobile-specific transport quirks (Nearby Connections, Doze mode,
etc.) enter the picture.
