# Windows

Source for **Kharcha**'s Windows desktop server/dashboard app: Ktor REST
API, the JVM `Transport` implementation (JmDNS + sockets), the Compose
Multiplatform Desktop UI, and the thin web dashboard view. Depends on the
modules in [`Implementation/Core/`](../Core/README.md) via a Gradle
composite build (`includeBuild("../Core")` in `settings.gradle.kts`), so
Core stays independently buildable.

Design: [`Design/Windows/`](../../Design/Windows/00-README.md).

## Status: v0 — households + activities working end-to-end

Run it:

```
./gradlew :app:run
```

This opens a Compose Desktop window titled "Kharcha" and starts the Ktor
API on `localhost:47321`. There's no seed data — create a household (or an
activity) from the UI to get started, or populate it in one shot for UI
testing:

```powershell
# with the app already running, from the repo root in PowerShell
.\Test\Windows\seed-data.ps1
```

If that fails with "running scripts is disabled on this system" — the
default PowerShell execution policy on most Windows installs blocks all
local scripts, unrelated to this one — run it via
`powershell -ExecutionPolicy Bypass -File .\Test\Windows\seed-data.ps1`
instead, or allow local scripts for your user once, going forward, with
`Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`.

(`Test/Windows/seed-data.sh` is the same thing for WSL/Linux/macOS — see
[`Test/README.md`](../../Test/README.md).) Both talk to the same REST API
the UI uses — nothing special, just automated clicking. They create two
households (one deliberately near its weekly limit, to see the amber
status; one comfortably under) and two activities (a 3-person trip with
mixed balances and settle-up suggestions, plus a simple 2-person one),
dated across the current week/month so the weekly
chart and category breakdown aren't empty either.

What works right now:
- SQLDelight persistence (`app/src/main/sqldelight/et/windows/db/sql/Schema.sq`)
  — the same two-tier design as Design/Core: an `operationLog` table plus
  materialized entity tables. Every write also appends to the log so a
  later sync pass doesn't need a schema change.
- **Categories** are mutable per household, not a fixed list: the Add
  Expense dialog's category field (`CategoryPicker.kt`) is a searchable
  create-or-select dropdown — typing filters existing categories, and a
  name with no case-insensitive match offers "+ Create". Actual dedup
  safety is server-side (`core-domain`'s `AddCategory` reuses an existing
  category if the trimmed, case-insensitive name already matches — so
  "Eating out" reuses "Eating Out" instead of creating a near-duplicate;
  verified via curl). Categories aren't managed from Settings — they're
  create-only from the Add Expense dropdown, with no removal UI (soft-delete
  support still exists server-side via `ArchiveCategory` if needed later).
- **Members** (household) and **participants** (activity) are managed from
  the household/activity Settings popup: a scrollable list with a "✕
  Remove" per row (soft-deleted via `ArchiveMember`/`ArchiveTripParticipant`,
  so past expenses referencing one by id stay intact), plus a "+ Add"
  button. Add shows a QR code (`QrCodeImage.kt`) alongside a name field —
  the QR encodes a plain, unauthenticated `JoinInvitePayload`
  (`JoinInvite.kt`), **not** the real pairing handshake designed in
  `Core/sync/Pairing.kt`. That two-step exchange needs a second device to
  show its own pubkey first, so it can't be wired up (or tested) until the
  Android app exists — the QR here is a placeholder to swap out then. Typing
  a name and clicking Add works today regardless of the QR.
- The sidebar and whichever detail screen is open **auto-refresh** every
  4 seconds (`DashboardApp.kt`), so data written by something other than
  this window's own actions — e.g. the `Test/Windows/seed-data` scripts —
  shows up on its own instead of only after the next manual reload.
- **Households** (`/api/v1/households/...`): create multiple households,
  each with its own categories and budget. Budget has two layers: a
  household-level **default monthly budget** (`Household.defaultMonthlyBudget`,
  set from the sidebar's gear icon → `HouseholdSettingsDialog.kt`) and an
  optional **per-month override** (a `MonthlyBudget` row, set from the ✏️
  next to the monthly bar in the main content →
  `MonthlyBudgetOverrideDialog.kt`) that applies to that month only.
  `core-domain`'s `resolveMonthlyBudget` picks the override if one exists,
  else the default, else nothing — verified end-to-end for all three
  cases via curl. The weekly breakdown is always derived from whichever
  amount resolves, never stored — verified including partial-week
  proportional allocation and the OK/NEARING/OVER status.
- **Activities** (`/api/v1/trips/...`, labeled "Activities" in the UI):
  create a trip/event with participants and a budget, add expenses split
  equally among participants, and see derived balances plus
  `DebtSimplification`'s minimal settle-up suggestions — verified
  end-to-end, including a 2-person debt resolving to the correct single
  transfer.
- Compose Desktop UI: a **Slack-style sidebar** (`Sidebar.kt`), not tabs.
  Two persistent grouped sections, "HOUSEHOLD" and "ACTIVITIES", each with
  a "+" to create and a small status dot per row (green/amber/red, from
  the same budget evaluation as the dashboard). Clicking a row selects it
  as the single main-content view on the right (`DashboardApp.kt`) — like
  clicking a channel in Slack. Hovering a row reveals a **⚙ gear** icon
  right there in the sidebar, which opens a rename/**default**-budget-edit
  popup (`HouseholdSettingsDialog.kt`/`TripSettingsDialog.kt`, backed by
  `PUT /households/{id}` and `PUT /trips/{id}`) — household/activity-level
  settings live in the sidebar, not in the main content header. The main
  content header has only a single **➕ Add Expense** button, scoped to
  whichever household/activity is selected. The one exception is a
  household's **month-specific override**, which is inherently tied to
  whichever month is currently showing, so its ✏️ edit affordance lives
  next to the monthly bar in the main content instead of the sidebar.
- **Add Android Device** (sidebar, below the household/activity lists):
  device-level pairing, separate from joining any specific household or
  activity — opens `PairAndroidDeviceDialog.kt`, showing a QR
  (`joinInviteForServerPairing()` in `JoinInvite.kt`, `kind = "server"`)
  that the Android app scans via its own "Connect to Server" action. This
  only registers "this phone talks to this server" for background sync;
  it doesn't join or share any household/activity data by itself — that's
  still the separate per-household/activity "+ Add" QR described above.
- The selected item's main content is a **Dashboard** plus an
  **Analytics** section. For households, the dashboard is
  `MonthlyBudgetChart.kt`: a full-length monthly progress bar with that
  month's ~4–5 weeks shown as proportionally-sized segments underneath it
  (each week's width is its `allocated` share of the month, so the
  segments' combined width equals the monthly bar's width) — this
  replaced an earlier "This Week" banner + button that the user found
  confusing (it looked like it edited the weekly budget; weekly budgets
  were never directly editable, only derived). Activities keep the
  existing single-bar `BudgetStatusBanner.kt` (no weekly split — a trip
  has one overall budget, not a monthly/weekly one). Both plus a
  dependency-free Canvas bar chart (`SimpleBarChart.kt`) below — spend-
  by-category for households, per-participant balances for activities.
- **Custom title bar** (`WindowTitleBar.kt`): the window is undecorated
  (`Main.kt`) so the default OS minimize/maximize/close buttons — which
  read as flat and generic — are replaced with colored, hover-responsive
  circular buttons in the app's indigo/teal/amber/rose palette. Maximize
  respects the Windows taskbar (`WindowGeometry.kt` computes usable
  bounds via `Toolkit.getScreenInsets`, recomputed fresh every time
  against whichever monitor the window is currently on — undecorated AWT
  windows don't pick up the work area on their own the way natively-
  decorated ones do, and computing it once at startup instead of per-use
  would give the wrong bounds after dragging to a different monitor).
  Dragging the title bar moves the window (a custom AWT
  `MouseMotionListener`-based implementation mirroring Compose Desktop's
  own `WindowDraggableArea`, since the snap gesture below needed a
  drag-end hook that component doesn't expose) and also **snaps**: drag
  to the top edge to maximize, drag to the left/right edge for a
  half-screen split — the native Windows Aero Snap gestures, which
  undecorated windows don't get for free.
- App identity: a generated icon — two stacked gold coins with a ₹ symbol,
  on the indigo → teal gradient background matching the UI theme — wired
  into the runtime window, title bar, and the installer, plus an
  indigo/teal Material3 theme (`Theme.kt`) instead of default colors.
- Light/dark toggle at the bottom of the sidebar (`ThemeToggleSwitch.kt`)
  — a slide switch, not a button: dark on the left, light on the right,
  thumb position shows which is active. On first run (no saved choice
  yet) it follows the OS theme — `SystemTheme.kt` checks the
  `AppsUseLightTheme` registry value on Windows (`defaults read -g
  AppleInterfaceStyle` on macOS; unrecognized platforms, including this
  dev sandbox, fall back to light). Once you use the switch, that explicit
  choice is remembered across launches in `~/.kharcha/theme.txt`
  (`ThemePreference.kt`) and always wins over the OS theme after that.

Confirmed working end-to-end on a real Windows machine (this was built in
a sandbox that can't render a GL/Compose window, so only the backend was
directly verified here each pass — the window itself checks out fine).

**Schema note:** the `household` table gained two nullable columns
(`defaultBudgetAmountMinorUnits`, `defaultBudgetCurrency`). `WindowsDatabase.Schema.create`
only runs against a brand-new SQLite file, so an existing `data.db` from
before this change won't have them, and every query touching `household`
fails with `no such column: household.defaultBudgetAmountMinorUnits`
(visible in the console now that `slf4j-simple` is wired in — previously
silent, see below). Delete the whole data directory to pick up the new
schema — **its location is OS-specific and not literally `~/.kharcha` on
Windows**: it's `System.getProperty("user.home")` + `/.kharcha`, which on
native Windows resolves to `%USERPROFILE%\.kharcha`
(`C:\Users\<you>\.kharcha`), a different folder from any WSL/Linux home.
In PowerShell: `Remove-Item -Recurse -Force "$env:USERPROFILE\.kharcha"`.
Fine for v0 with no real migration story yet; flag if this becomes
disruptive.

## Building a setup.exe installer

`app/build.gradle.kts` configures `compose.desktop.application.nativeDistributions`
with `TargetFormat.Exe`, using `app/icon.ico` as the installer/app icon.
The resulting installer is **fully self-contained** — `jpackage` bundles a
private, trimmed JRE (via `jlink`) into it, so the *installed* app needs no
Java on the machine it's installed on. It behaves like any normal Windows
installer: double-click, click through, get a Start Menu + Desktop
shortcut and an "Add or Remove Programs" entry.

The JDK/WiX prerequisites below are only needed on **the machine building
the installer** — once you have `Kharcha-0.1.0.exe`, it's portable; copy
it anywhere and running it needs nothing else installed.

This build step **must run on Windows itself** — `jpackage` builds for
whatever OS it's running on; it can't cross-build a Windows installer from
Linux/WSL.

Prerequisites on the Windows machine doing the build:
1. A JDK (21+) installed on Windows, with `JAVA_HOME` set — e.g.
   [Eclipse Temurin 21](https://adoptium.net/) or the
   [Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/download).
   The installer usually sets `JAVA_HOME` for you; if not:
   `[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.x.x.x-hotspot", "User")`,
   then restart PowerShell.
2. [WiX Toolset v3](https://wixtoolset.org/docs/wix3/) installed and on
   `PATH` — `jpackage` needs it to build the `.exe` installer (not needed
   for `./gradlew :app:run`, only for packaging).

Then, from `Implementation/Windows` in PowerShell:

```powershell
.\gradlew.bat :app:packageExe
```

The installer lands in `app\build\compose\binaries\main\exe\Kharcha-0.1.0.exe`.
Double-clicking it installs the app (Start Menu shortcut + desktop shortcut,
per-user, no admin rights needed per the `windows { }` block in
`app/build.gradle.kts`) and launches a normal double-click desktop app from
then on — the Ktor server and Compose window both start together from
`Main.kt`, same as `:app:run`.

If you'd rather not install WiX yet, `.\gradlew.bat :app:createDistributable`
produces a runnable folder (`app\build\compose\binaries\main\app\Kharcha\`)
with a `Kharcha.exe` launcher inside — double-clickable, just not a
proper installer.

## Not implemented yet (later passes)

- Devices/pairing screen and its REST endpoints.
- Category management (rename/add/archive) has no UI yet — the settings
  popup only covers rename + budget.
- Trip expense splits are equal-only in the UI for now — `core-domain`'s
  `SplitCalculator` already supports exact/percentage/weighted, just no
  dialog for picking a mode yet.
- Settling up: suggestions are computed and shown, but there's no button
  yet to actually record a `Settlement` from a suggestion.
- The full JVM `Transport`/`SyncChannel` binary sync protocol from
  [Design/Windows/02-transport-implementation.md](../../Design/Windows/02-transport-implementation.md)
  — nothing does real operation-log sync with another device yet; this app
  only talks to itself. What *does* exist: this server advertises itself
  on the LAN via mDNS (`server/LanAdvertiser.kt`, JmDNS, same service type
  `_expensetracker._tcp.local.` the design doc anticipated) purely so the
  Android app can find its current address without a manual IP — a much
  lighter thing than the full sync protocol.
- Real QR device pairing — both the Add Member/Participant QR and the Add
  Android Device QR are the same placeholder `JoinInvitePayload` (see
  above), not the `Core/sync/Pairing.kt` handshake. The Android app now
  does act on them for real (joining pulls a household/activity's data
  into a local copy and starts background sync; pairing registers the
  server for that sync) — see `Implementation/Android/README.md` — just
  not with the real crypto handshake yet.
- Android↔Android sync — only Android↔(paired Windows) sync exists; two
  phones cannot yet sync with each other directly.
- System tray / start-on-login.
- `%APPDATA%`-based config (currently just `~/.kharcha/`).
- The thin web dashboard view.
- Category/member/participant rename — all three support add and remove
  (archive) but not editing a name in place.
- Real push updates (`/ws/changes` from Design/Windows/03-rest-api.md is
  aspirational, not implemented) — the sidebar and open detail screen
  currently catch up via polling every 4s (`DashboardApp.kt`) rather than
  a live push, so there's a few seconds of lag after an external write
  (e.g. a seed-data script, or eventually another synced device).
