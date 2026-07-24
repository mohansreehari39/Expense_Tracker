# Windows

Source for the Windows desktop server/dashboard app: Ktor REST API, the
JVM `Transport` implementation (JmDNS + sockets), the Compose Multiplatform
Desktop UI, and the thin web dashboard view. Depends on the modules in
[`Implementation/Core/`](../Core/README.md) via a Gradle composite build
(`includeBuild("../Core")` in `settings.gradle.kts`), so Core stays
independently buildable.

Design: [`Design/Windows/`](../../Design/Windows/00-README.md).

## Status: v0 — vertical slice working end-to-end

Run it:

```
./gradlew :app:run
```

This opens a Compose Desktop window and starts the Ktor API on
`localhost:47321`. On first run it bootstraps a default household + five
starter categories so the dashboard isn't empty.

What works right now:
- SQLDelight persistence (`app/src/main/sqldelight/et/windows/db/sql/Schema.sq`)
  — the same two-tier design as Design/Core: an `operationLog` table plus
  materialized entity tables. Every write also appends to the log so a
  later sync pass doesn't need a schema change.
- REST API: `GET/POST /api/v1/household`, `/budgets/{year}/{month}`,
  `/budgets`, `/expenses` — verified with curl end-to-end, including the
  weekly budget math (proportional partial-week allocation) and the
  OK/NEARING/OVER status from `core-domain`.
- Compose Desktop UI: Household Dashboard (weekly budget status banner,
  colored OK/NEARING/OVER per Design/Core/05-domain-logic.md) + an Add
  Expense dialog, both talking only to the REST API, never the DB directly.

**Known gap:** rendering the Compose window could not be visually verified
in the sandboxed environment this was built in — Skiko (Compose's renderer)
threw `Cannot create Linux GL context` there, which looks like a
sandbox/GPU-passthrough limitation rather than an app bug, since the app
compiles cleanly and the exact same JVM process's Ktor server came up and
served real, correct data over HTTP in that same run. Verify the window
itself renders when running `./gradlew :app:run` on a real machine (WSLg
with GPU passthrough, or native Windows/Linux) — if the same GL error shows
up there, that's the next thing to debug.

## Building a double-click .exe

`app/build.gradle.kts` configures `compose.desktop.application.nativeDistributions`
with `TargetFormat.Exe`. This **must be run on Windows itself** — `jpackage`
(which does the packaging) builds for whatever OS it's running on; it can't
cross-build a Windows installer from Linux/WSL.

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

The installer lands in `app\build\compose\binaries\main\exe\ExpenseTracker-0.1.0.exe`.
Double-clicking it installs the app (Start Menu shortcut + desktop shortcut,
per-user, no admin rights needed per the `windows { }` block in
`app/build.gradle.kts`) and launches a normal double-click desktop app from
then on — the Ktor server and Compose window both start together from
`Main.kt`, same as `:app:run`.

If you'd rather not install WiX yet, `.\gradlew.bat :app:createDistributable`
produces a runnable folder (`app\build\compose\binaries\main\app\ExpenseTracker\`)
with an `ExpenseTracker.exe` launcher inside — double-clickable, just not a
proper installer.

## Not implemented yet (later passes)

- Trips, analytics, devices/pairing screens, and their REST endpoints.
- The JVM `Transport` (JmDNS + sockets) from
  [Design/Windows/02-transport-implementation.md](../../Design/Windows/02-transport-implementation.md)
  — nothing syncs with another device yet; this app only talks to itself.
- System tray / start-on-login.
- `%APPDATA%`-based config (currently just `~/.expense-tracker/`).
- The thin web dashboard view.
- App icon (packaging currently uses the jpackage default).
