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
activity) from the UI to get started.

What works right now:
- SQLDelight persistence (`app/src/main/sqldelight/et/windows/db/sql/Schema.sq`)
  — the same two-tier design as Design/Core: an `operationLog` table plus
  materialized entity tables. Every write also appends to the log so a
  later sync pass doesn't need a schema change.
- **Households** (`/api/v1/households/...`): create multiple households,
  each with its own categories, monthly budget, and derived weekly
  breakdown — verified end-to-end including partial-week proportional
  allocation and the OK/NEARING/OVER status from `core-domain`.
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
  right there in the sidebar, which opens a rename/budget-edit popup
  (`HouseholdSettingsDialog.kt`/`TripSettingsDialog.kt`, backed by
  `PUT /households/{id}` and `PUT /trips/{id}`) — settings live in the
  sidebar, not in the main content header. The main content header has
  only a single **➕ Add Expense** button, scoped to whichever household/
  activity is selected.
- The selected item's main content is a **Dashboard** (the red/amber/green
  budget-status card, per Design/Core/05-domain-logic.md) plus an
  **Analytics** section: a dependency-free Canvas-based bar chart
  (`SimpleBarChart.kt`) — spend-by-category for households, per-
  participant balances for activities.
- **Custom title bar** (`WindowTitleBar.kt`): the window is undecorated
  (`Main.kt`) so the default OS minimize/maximize/close buttons — which
  read as flat and generic — are replaced with colored, hover-responsive
  circular buttons in the app's indigo/teal/amber/rose palette. Dragging
  the bar moves the window (`WindowDraggableArea`); the light/dark toggle
  also lives here now.
- App identity: a generated wallet/coin icon (indigo → teal, matching the
  UI theme) wired into the runtime window, title bar, and the installer,
  plus an indigo/teal Material3 theme (`Theme.kt`) instead of default
  colors.
- Light/dark toggle in the custom title bar (sun/moon button) — choice is
  remembered across launches in `~/.kharcha/theme.txt`
  (`ThemePreference.kt`). Defaults to light on first run; no OS-preference
  auto-detection yet.

**Known gap:** rendering the Compose window could not be visually verified
in the sandboxed environment this was built in — Skiko (Compose's renderer)
threw `Cannot create Linux GL context` there, which looks like a
sandbox/GPU-passthrough limitation rather than an app bug, since the app
compiles cleanly and the exact same JVM process's Ktor server came up and
served real, correct data over HTTP in that same run, in every pass so far.
If you hit the same GL error running `./gradlew :app:run` on your own
machine, tell me and I'll dig into it.

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
- The JVM `Transport` (JmDNS + sockets) from
  [Design/Windows/02-transport-implementation.md](../../Design/Windows/02-transport-implementation.md)
  — nothing syncs with another device yet; this app only talks to itself.
- System tray / start-on-login.
- `%APPDATA%`-based config (currently just `~/.kharcha/`).
- The thin web dashboard view.
- OS dark-mode preference auto-detection (the manual toggle works; there's
  just no "match system" default yet).
- Windows Aero Snap / edge-snap-to-resize — undecorating the window to
  draw a custom title bar means the OS no longer owns window-chrome
  gestures. Basic drag-to-move and edge resizing work; snapping a window
  to half the screen by dragging it to an edge does not, yet.
