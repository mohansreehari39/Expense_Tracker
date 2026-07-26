# Expense Tracker

An Android app + Windows app for tracking household and trip expenses.

## What it does

**Household expenses**
- Two or more household members each run the Android app and log day-to-day
  expenses (groceries, utilities, rent, etc.) against a shared monthly budget.
- A monthly budget is set at the start of each month and is automatically
  broken down into a weekly budget. As a week's spend nears its limit the app
  warns you; if you go over, the amounts are highlighted in red. Overspending
  is never blocked — the app tracks reality, it doesn't gate it.
- Phones sync directly with each other (not just through the server), so
  everyone sees every expense even when the Windows server is off for days.

**Trip / event expenses (Splitwise-style)**
- A trip (or any time-bound event) gets its own budget and participant list,
  fully separate from the household's monthly budget.
- Expenses can be split equally, by exact amount, by percentage, or by
  shares, and the app tracks who owes whom and lets you settle up.
- No weekly breakdown here — trips just get a single nearing/over alert
  against the trip's overall budget.

**Windows app (server + dashboard)**
- Runs on a personal machine, doesn't need to be on all the time — it's a
  peer in the same sync mesh as the phones, not a required hub.
- When it's running, it provides analytics and dashboards: category
  breakdowns, budget burn-down (including the weekly view), spending trends,
  trip settlement summaries, and simple spending suggestions.
- Expenses can also be entered directly from the Windows app.

## Pending for production

A living checklist, organized by milestone — check an item off (or delete
it) as it's fixed; add new ones as they're found, rather than letting them
live only in chat history. See `Implementation/Android/README.md` and
`Implementation/Windows/README.md` for the fuller technical writeup behind
each item.

### V1 — completed

**Status note:** everything below compiles and was reasoned through
carefully, but the planned end-to-end live-device test pass covering this
whole batch was interrupted by the installer bug (see "V1 — remaining"
below) before it could run — so "completed" here means "implemented,
compiles, not yet re-verified live this session." Re-run the full test
pass next session once the installer launches cleanly.

- [x] Categories created on Android never got pushed to the Windows
      server — fixed via `SyncEngine.pushPendingCategories`.
- [x] Rotating pairing-key flow (pair → remove on Windows → phone forgets
      it automatically on next sync) — re-verified end-to-end.
- [x] Household/activity settings (rename, budget, settlement toggle)
      editable from Android, not just Windows — Settings gear now lives
      in the Android drawer (matching Windows' sidebar), backed by
      `LocalRepository.updateHouseholdConfig`/`updateActivityConfig` and a
      `SyncEngine` push step.
- [x] Member/participant list + removal on Android (view + "✕ Remove"
      only — adding a member from Android is still Windows-only, by
      design, since it doesn't need the join/pairing machinery add would).
- [x] **Weekly budget rollover** — `WeeklyBudget.rolloverAdjustedAllocations`
      (Core domain) + hand-ported Android twin in `BudgetMath.kt`: a
      closed week's surplus/deficit splits equally across the weeks still
      open; a week in progress never shifts its own budget mid-week.
- [x] **Weekly budget figures on Windows**, matching Android — clicking a
      week segment on `MonthlyBudgetChart` shows that week's own figures.
- [x] **Budget bars redesigned**: Spent (amber) / Remaining (green, red
      past 90% spent) / Total (neutral) shown directly under every
      progress bar, both apps, replacing the old single-line caption.
- [x] **Per-person settlement for households**, opt-in via household
      settings — `Household.settlementEnabled`, `HouseholdBalances.netBalances`
      (Core domain, equal-split), a toggle + Balances/Suggested
      Settlements section on both apps.
- [x] **Settle button** — records an actual `HouseholdSettlement` (new
      Core model + Windows table/route `POST /households/{id}/settlements`),
      updates balances immediately on both apps. (Trip/activity settlement
      recording is still not wired up — see V2 list below.)
- [x] Text wrapping fixes (Edit/Delete buttons, budget figure rows) on
      Android.
- [x] **In-app upgrade check** — `UpdateChecker` hits GitHub's public
      REST API directly over HTTPS (`GET /repos/{owner}/{repo}/releases/
      latest`) — no `gh` CLI dependency, since that only exists in this
      agent's dev environment, never on an end-user machine. "Check for
      Updates" row in the sidebar opens `UpdateCheckDialog`; if a newer
      `.exe` asset is published, `UpdateInstaller` downloads it to a temp
      file, launches it as an independent process, and exits. Not yet
      live-tested end-to-end (needs an actual published GitHub Release to
      check against — untestable until the first one exists), but the
      "already up to date" / "check failed silently" paths work today.
- [x] **Data directory moved to `%LOCALAPPDATA%\Kharcha\data.db`** (was
      `~/.kharcha`, a Unix-style dotfolder) — `KharchaConfig.dataDir()`,
      `Local` not `Roaming` since a SQLite file shouldn't sync across
      machines via a roaming profile.
- [x] **Separate prod/dev port + data directory, no env vars.**
      `KharchaConfig` reads a `kharcha.dev` **JVM system property** (never
      an OS environment variable) to pick between the real port/data-dir
      (`47321`, `%LOCALAPPDATA%\Kharcha`) and a dev pair (`47399`,
      `%LOCALAPPDATA%\Kharcha-dev`); the installed app never sets it and
      needs zero configuration. New `gradlew.bat :app:runDev` Gradle task
      passes the property for side-by-side dev testing.
- [x] **Server identified by computer name.** `KharchaConfig.
      serverDisplayName()` (reads `%COMPUTERNAME%`) replaces the hardcoded
      `"Kharcha"` in the QR pairing payload, the mDNS advertised service
      name, and the "Add Android Device" dialog — e.g. "Kharcha —
      DESKTOP-AB12CD". The app's own window title/branding is unchanged.
- [x] **Real crypto handshake for QR pairing — scoped down from the full
      design.** The full asymmetric two-step handshake in `Core/sync/
      Pairing.kt` (`Design/Core/04-pairing-and-crypto.md`) needs a
      genuinely new two-scan UX on both apps and remains a larger,
      separate follow-up (see V2). What shipped instead, closing the two
      concrete holes that actually mattered: (1) **`PairingSession`** — a
      single-use secret Windows mints fresh every time any QR is shown;
      `POST /devices` now rejects registration without it, closing the
      gap where any device that could merely reach the server's HTTP port
      could silently pair without ever scanning a QR. (2) **Every
      `/api/v1` route now requires the paired device's id + pairingKey**
      (`X-Device-Id`/`X-Pairing-Key` headers, checked by a new
      `deviceAuthPlugin` interceptor) except pairing/heartbeat themselves
      and requests from this same machine (the Windows app's own
      dashboard UI) — previously every household/expense/etc. route had
      zero authentication at all; any device on the LAN that knew the
      port could read or write anything. Android's `ApiClient` attaches
      both headers automatically once paired.
- [x] **Real Room migrations on Android — critical, no destructive
      wiping, ever.** `fallbackToDestructiveMigration()` removed entirely
      from `AppDatabase.kt` (not even for debug builds) — every version
      bump from here on ships an explicit `Migration(old, new)` object, or
      the app fails loudly on upgrade instead of silently wiping data.
      `exportSchema` turned on with a committed schema history
      (`app/schemas/`) so future migrations can be tested against the real
      previous schema. Windows' existing `migrateExistingDatabase` pattern
      already satisfied this and needed no change.
- [x] **Windows-only spending trends screen** — `TrendsDialog`, reachable
      via a "📈 Trends" button on the household screen: last 6 months'
      total spend (bar chart) plus a category breakdown aggregated across
      that range. New `GET /households/{id}/trend?months=N` route built
      on the already-flexible `Repository.householdExpensesBetween`.
      Android-side: intentionally excluded, Windows-only by design.
- [x] **Fix: households/activities created on Android never synced to
      Windows.** `SyncEngine.syncAll` now pushes any purely-local
      (unlinked) household/activity to every currently-paired server via
      the already-existing (previously unused) `ApiClient.createHousehold`/
      `createTrip`, then links the local row — existing members/
      participants are pushed and matched back by name so they resolve to
      the same remote row instead of duplicating.

### V1 — remaining

- [ ] **Windows installer (`setup.exe`) — builds, but the installed app
      doesn't launch yet; needs a clean re-verify.** `compose.desktop.
      application.nativeDistributions` was already configured;
      `gradlew.bat :app:packageExe` (WiX Toolset v3.14 on this machine)
      produces `Kharcha-0.1.0.exe`, and running it does install (currently
      to `%LOCALAPPDATA%\Kharcha` — see the next item, that's changing).
      But the installed `runtime\bin\` is missing `java.exe` — most likely
      because a `Stop-Process -Force -Name java` cleanup step (run
      routinely between Gradle builds in this session) killed the JDK's
      `jlink` subprocess mid-way through `createRuntimeImage`, corrupting
      that task's cached output without Gradle noticing (its up-to-date
      check doesn't verify the runtime image's actual completeness, just
      that the output path exists). Next session:
      `Remove-Item -Recurse -Force app\build\compose` before re-running
      `packageExe`, and going forward never kill `java.exe` while a
      Windows packaging task is running.
- [ ] **Installer: app installs like a normal Windows app; only the
      database defaults to `%LOCALAPPDATA%`, and even that should be
      user-overridable at install time.** Correction to last session's
      work — `%LOCALAPPDATA%\Kharcha` was only ever meant for the
      *database* (see the completed item below), but `build.gradle.kts`'s
      `windows { perUserInstall = true }` also put the *app itself* there
      instead of a normal machine-wide location. Fix:
      - Set the app install location back to a standard one (e.g.
        `C:\Program Files (x86)\Kharcha`, i.e. `perUserInstall = false` or
        removed, accepting the UAC elevation prompt that implies) — `dirChooser
        = true` already lets the user pick a different install folder for
        the app itself, same as any normal Windows installer.
      - Additionally offer a **separate** directory prompt during install
        specifically for the *database* location, defaulting to
        `%LOCALAPPDATA%\Kharcha` if the user doesn't change it — like the
        "install path" vs "data path" split some installers offer. This
        needs custom WiX authoring beyond jpackage's default template
        (jpackage/Compose Desktop's `dirChooser` only prompts once, for
        the app's own install directory) — likely via jpackage's
        `--resource-dir` override to supply a custom WiX UI dialog
        sequence, or a post-install custom action.
      - The chosen database path then needs to reach the running app —
        e.g. the installer writes it to a small config file or registry
        value (`HKCU\Software\Kharcha` or similar) at install time, and
        `KharchaConfig.dataDir()` checks for that override before falling
        back to its current hardcoded `%LOCALAPPDATA%\Kharcha` default.
- [ ] **Full live-device test pass** for the entire V1 batch above,
      blocked on the installer item directly above — pairing with the new
      one-time secret, computer-name QR display, Android-created
      household syncing to Windows, spending trends screen, settle
      button, budget figure rows.

### V2 — planned

- [ ] **Direct Android-to-Android pairing/sync** — the headline V2
      feature. Join a household/activity phone-to-phone without going
      through a Windows server at all. Depends on finishing the *full*
      two-step asymmetric handshake (`Core/sync/Pairing.kt`/`Design/Core/
      04-pairing-and-crypto.md`) as its trust foundation — V1 only shipped
      a scoped-down interim version (single-use pairing secret + required
      device credentials on every request), not the full design.
- [ ] Trip/activity settlement recording — `SettleUp` already exists in
      Core domain but has no route or UI (households got this in V1,
      trips didn't).
- [ ] Split-mode picker UI (exact/percentage/weighted) — `SplitCalculator`
      already supports all four modes, both app UIs are equal-only.
- [ ] Rename support for categories/members/participants (create +
      archive/remove only today, on both apps).
- [ ] Windows category management UI (rename/archive; currently
      create-only, from the Add Expense picker).
- [ ] Android background sync via WorkManager, replacing today's in-app
      coroutine loop tied to process lifetime (stops when Android
      reclaims a backgrounded/killed process).
- [ ] Notifications on either platform (budget alerts, sync events).
- [ ] Windows system tray / start-on-login.
- [ ] Conflict resolution beyond "server wins, local queues pushes" —
      concurrent edits to the same expense from two synced devices aren't
      reconciled, just last-pull-wins.
- [ ] The full binary `Transport`/`SyncChannel` operation-log sync
      protocol from `Design/Windows/02-transport-implementation.md` isn't
      wired up — REST is used as an interim sync transport instead; mDNS
      discovery exists just for address-finding.

## Repository layout

| Folder | Contents |
|---|---|
| [`Arch/`](Arch/00-README.md) | System architecture — sync protocol, data model, subsystem designs, tech stack |
| [`Design/`](Design/README.md) | Detailed design (API contracts, schemas, screen designs) implementing the architecture |
| [`Implementation/`](Implementation/README.md) | Application source code |
| [`Test/`](Test/README.md) | Test plans and test code |

Start with [`Arch/00-README.md`](Arch/00-README.md) for the full architecture writeup.

## Contributing

See [`CLAUDE.md`](CLAUDE.md) for commit message conventions and diagram
conventions used throughout this repo.
