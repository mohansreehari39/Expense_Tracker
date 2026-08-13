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

## Data safety policy

V1's schema is not frozen forever — V2 and later will add tables/columns
as features grow. What's frozen is the guarantee: **no schema change may
ever destroy a user's existing data.** Concretely:
- Android: no `fallbackToDestructiveMigration()`, ever. Every version
  bump ships a real, additive `Migration(old, new)` object (new
  tables/columns only — never drop/rename a column that might hold real
  data), tested against the committed schema history in `app/schemas/`.
- Windows: every new table/column gets a matching `CREATE TABLE IF NOT
  EXISTS` / `addColumnIfMissing` line in `Database.kt`'s
  `migrateExistingDatabase`, so an existing install upgrades in place
  instead of failing or wiping.
- If a future change genuinely can't be done additively, that's a stop
  point — ask before writing it, don't silently wipe rows to make the
  new schema fit.

## Pending for production

A living checklist, organized by milestone — check an item off (or delete
it) as it's fixed; add new ones as they're found, rather than letting them
live only in chat history. See `Implementation/Android/README.md` and
`Implementation/Windows/README.md` for the fuller technical writeup behind
each item.

### V1 — completed

**Status note:** the full V1 batch below has been live-device tested —
pairing, sync, budgets, settlement, subcategories, beneficiary/
contributor splits, dependents, and the installer all verified on a real
Android phone and a native Windows install.

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
- [x] **Windows installer (`setup.exe`) now launches successfully.** The
      earlier "missing `java.exe`" theory was wrong — jpackage runtime
      images deliberately never include `bin\java.exe`/`javaw.exe` (the
      native `Kharcha.exe` launcher loads `jli.dll` directly instead), so
      that was a red herring. The real error, only visible by running
      `Kharcha.exe` from a shell instead of double-clicking (the GUI
      dialog just says "Failed to launch JVM" with no detail): `java.lang.
      NoClassDefFoundError: java/sql/DriverManager`. Compose Desktop's
      automatic `jlink` module detection (static `jdeps` analysis of the
      app's jars) missed `java.sql` because the SQLite JDBC driver only
      reaches `DriverManager` via reflection/`ServiceLoader`, which isn't
      visible to static analysis. Fix: added an explicit
      `modules("java.sql", "java.naming")` to `nativeDistributions` in
      `build.gradle.kts`. Rebuilt clean and verified: installer runs,
      installs to `%LOCALAPPDATA%\Kharcha`, and `Kharcha.exe` launches to
      a full working window. (Still installs under `%LOCALAPPDATA%`
      rather than a normal Program Files location — see "V1 — remaining"
      below, that's a separate, deliberate follow-up.)
- [x] **Subcategories.** Each category can have user-defined
      subcategories (`Core/model` `Subcategory`, keyed by `categoryId`,
      not household/trip), managed identically to categories — same
      dedup-by-name-on-create rule, same soft-delete-via-archive, same
      typeahead-create picker pattern, on both apps. `HouseholdExpense`/
      `TripExpense` gained an optional `subcategoryId`. Windows exposes
      subcategories nested under each category in `GET /households/{id}`
      (`CategoryDto.subcategories`) plus `POST`/`DELETE .../categories/
      {categoryId}/subcategories[/{id}]`; Android syncs them the same way
      categories already sync (push pending, pull-and-reconcile by
      `remoteId`), gated behind a new Room migration (`5 → 6`, additive
      only — new table + nullable column, no existing data touched).
      Live-device tested on both apps.
- [x] **Expense beneficiaries ("who's it for") + contributors ("who
      chipped in"), household + activity — live-device tested.** Two new
      Core models — `HouseholdExpenseBeneficiary`/`HouseholdExpense
      Contribution` (household) and the pre-existing `ExpenseSplit` plus
      new `TripExpenseContribution` (trip, reusing the split table that
      already existed for trip beneficiaries). New `HouseholdDependent`
      (pet/kid/parent, user-named per household, `DependentCategory`
      enum) — a beneficiary category that can receive spend but never
      contributes; excluded from equal-split defaults and from "who
      chipped in" entirely. Defaults: beneficiaries split equally across
      active members/participants (never dependents unless explicitly
      picked); contributions default 100% to whoever's entering the
      expense — computed live and shown up front, not hidden behind a
      mode picker. **No separate "Paid by" selector** — it was redundant
      with the contribution split, so it's gone; the stored payer is
      derived from whichever contributor ends up with the largest share.
      Each split is a **sub-dialog** (`SplitEditorDialog`, both apps),
      opened from a one-line summary row on the add-expense form (e.g.
      "Split equally among 3", "100% Sreehari") rather than shown inline —
      opens pre-filled with the live default, editable via a single
      ₹/% slide toggle (same visual language as the dark/light mode
      switch) rather than four separate mode buttons. Percentage entry is
      UI-only, always resolved to concrete minor-units before anything
      reaches a database — via `SplitCalculator` on Windows, a
      hand-ported equal-split/percentage resolver on Android, matching
      the existing Core-vs-Android split established by subcategories/
      budget math — the DB never stores a percentage. Windows: new
      SQLDelight tables + `POST`/`DELETE .../households/{id}/
      dependents[/{id}]`, `RecordExpenseRequest`/`AddTripExpenseRequest`
      gained `beneficiarySplit`/`contributionSplit` (a `SplitModeDto`,
      always sent as resolved `EXACT` amounts from the UI), household/
      trip expense responses carry resolved beneficiary/contribution
      lists, dependent management added to Household Settings. Android:
      mirrored Room tables (migration `6 → 7`), `LocalRepository`/
      `SyncEngine` push pending dependents/splits and pull-reconcile them
      the same way categories/members already do (delete-and-reinsert
      per expense). Household/Activity Settings' Members/Dependents/
      Participants lists are now **collapsible, collapsed by default**
      for households (member/dependent lists can get long), **expanded
      by default** for activities (just participants, usually a handful).
- [x] **Trip/activity settlement recording — live-device tested.**
      `SettleUp` (Core domain) already existed but had no route or UI —
      new `POST /trips/{id}/settlements` route (Windows) mirroring the
      household settlement route, new `TripSettlementsResponse`/
      `RecordTripSettlementRequest` DTOs. Both apps' trip/activity
      screens gained a "Settle" button next to each suggested settlement
      (identical placement to the household screens' existing one),
      calling the new route and refreshing balances immediately.
      Android's trip balances/suggestions now come from the server
      (`api.trip(remoteId).suggestedSettlements`) when linked, falling
      back to the existing local naive equal-split fold when unlinked/
      unreachable — same pattern the household screen already used.
- [x] **Installer: app installs like a normal Windows app — live-device
      tested.** `perUserInstall` flipped from `true` to `false` in
      `build.gradle.kts` — the app now installs to `C:\Program Files\
      Kharcha\` like any normal Windows app; `dirChooser = true` still
      lets the user redirect that. No UAC prompt actually appeared during
      testing on the dev machine (session already had sufficient rights)
      — installer wizard, install, and launch from Program Files all
      confirmed working end to end. **Data-location override implemented
      as an in-app setting instead of an installer-time WiX dialog** — a
      deliberate scope change from the original plan (a second WiX
      directory-chooser dialog), since authoring/verifying custom WiX UI
      sequences reliably was the bigger risk. New "Data Location" row in
      the sidebar opens `DataLocationDialog` — browse/type a folder,
      defaults to `%LOCALAPPDATA%\Kharcha`, takes effect next launch.
      `KharchaConfig.dataDir()` follows a pointer file
      (`%LOCALAPPDATA%\Kharcha\datadir.cfg`, a fixed well-known location
      whose *contents* name the real, possibly-elsewhere data directory).

**V1 is feature-complete and live-device tested** — pairing, sync,
budgets, settlement, subcategories, beneficiary/contributor splits,
dependents, and the installer have all been verified on a real Android
phone + a native Windows install. Any further issues found in normal use
get folded into V1.5 (below) rather than tracked here; V2/V2.5 are the
next deliberate scope.

### V1.5 — bug-fix track (planned release: end of this month)

Not V2 scope — V1.5 is where bugs found from actually using v0.1.0/v0.1.1
day-to-day get collected and released together, roughly monthly, as a
single point release (`0.1.x`). V2 above stays reserved for deliberate
new features. Add items here as they're found in normal use; they move
into a dated sub-list below once actually shipped.

**v0.1.1 (2026-08-13) — shipped:**
- [x] **Fixed a real data-integrity bug found in production**: after
      removing and rejoining a household member (the normal recovery path
      after reinstalling/repairing a phone), that person's expense
      history fragmented across two member ids that displayed as two
      different people. Root cause: `AddMember`/`AddTripParticipant` only
      checked *active* members for a name match before minting a new id,
      so an archived (removed) member with the same name was invisible to
      the dedup check. Fixed by matching in order of confidence — exact
      `deviceId`, then name + email/phone, then name alone as a
      last-resort fallback — and un-archiving/reusing the match instead
      of always creating a fresh row. `Member` gained `deviceId`/`email`/
      `phone`, self-healing on every reinstall. Android's signup screen
      now requires phone + email (previously optional), since they're
      what makes the stronger matches possible.
- [x] **Sync failures were completely silent.** `SyncEngine.syncAll`
      only ever distinguished a clean device-revocation (410) from
      success — any other failure (network blip, a stale key returning
      401, a server-side exception) failed identically and invisibly,
      forever, with no way to tell what was wrong short of re-installing.
      Every failure is now logged to a local rolling file, surfaced in
      the Android drawer next to "Offline", **and pushed to the Windows
      server** (`POST /devices/{id}/logs`, deliberately reachable with no
      valid pairing key required, since a broken key is exactly the
      failure this needs to report) — so a real phone in daily use is
      diagnosable by checking the Windows machine, without ever needing
      adb.
- [x] Read-only "My Profile" view added to the Android drawer — signup
      data was previously write-only, with no way to see it again.
- [x] Age and gender made mandatory at signup too, alongside phone/email
      — there's no "edit profile" screen yet (planned for V1.5, see
      below), so signup is currently the only chance to capture a
      complete profile.

**v0.1.2 (2026-08-13) — shipped:**
- [x] **Windows still showed "v0.1.0" after installing v0.1.1.**
      `et.windows.APP_VERSION` is a hand-maintained constant (Compose
      Desktop has no AGP-style `BuildConfig` to read `packageVersion`
      from automatically) that didn't get bumped alongside
      `build.gradle.kts`'s `packageVersion` when v0.1.1 shipped. Beyond
      the wrong label in the sidebar, this broke "Check for Updates"
      correctness too — it would have kept reporting "0.1.1 available"
      forever, even on a fully up-to-date install. Fixed, and both
      `packageVersion`/`APP_VERSION` bumped together going forward.

**Planned for V1.5, not yet shipped:**
- [ ] Edit profile on Android (name/age/gender/phone/email) — signup is
      currently the only entry point; the read-only view shipped in
      v0.1.1 above, editing is the natural next step.

### V2 — planned

V2 has one focus: **direct Android-to-Android pairing/sync** — join a
household/activity phone-to-phone without going through a Windows server
at all. Everything else previously listed under "V2" has moved to V2.5
below so this milestone stays scoped to that one feature.

- [ ] Direct Android-to-Android pairing/sync. Depends on finishing the
      *full* two-step asymmetric handshake (`Core/sync/Pairing.kt`/
      `Design/Core/04-pairing-and-crypto.md`) as its trust foundation — V1
      only shipped a scoped-down interim version (single-use pairing
      secret + required device credentials on every request), not the
      full design.

### V2.5 — planned

Everything else on the roadmap, deliberately deferred behind V2's
Android-to-Android focus.

- [ ] Weighted split mode UI — `SplitCalculator`/`SplitEditor` support
      Equal/Percentage/Exact today (see the beneficiary/contributor split
      item in V1), `SplitMode.Weighted` exists in Core domain but has no
      UI on either app.
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
