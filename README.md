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

A living checklist — check an item off (or delete it) here as it's fixed;
add new ones here as they're found, rather than letting them live only in
chat history. See `Implementation/Android/README.md` and
`Implementation/Windows/README.md` for the fuller technical writeup behind
each item.

**Bugs (fix these first):**

- [ ] Categories created on Android never get pushed to the Windows
      server — `LocalRepository.addCategory` only writes locally, so
      `SyncEngine.pushHouseholdPending`'s category lookup finds
      `remoteId == null` and silently skips that expense forever. Fix
      needs a "push pending categories" step mirroring the member-push
      fix, run before pushing expenses that reference them. Categories
      created on Windows already sync down to Android fine — only the
      Android→Windows direction is broken.
- [ ] Re-verify the rotating pairing-key flow end-to-end after last
      night's implementation: pair a device → remove it from Windows →
      confirm the phone actually stops syncing (gets a `410` and forgets
      the pairing) instead of silently reconnecting.
- [ ] Household/activity settings (rename, budget, etc.) can't be edited
      from Android at all right now — only from Windows. Once a
      household/activity has synced to the phone, editing its
      configuration should be allowed from Android too, not just
      viewing/adding expenses.

**Requested features (not started):**

- [ ] **Weekly budget rollover.** Today each week's allocation
      (`BudgetMath.weekAllocation`) is a fixed proportional slice of the
      month, independent of other weeks. Change so under/overspend in a
      week carries forward: if week 1's budget is ₹200 and only ₹100 is
      spent, the remaining ₹100 should be split equally across the
      *remaining* weeks of the month (raising their effective budgets);
      if week 1 spends ₹300 against a ₹200 budget, the ₹100 overspend
      should be split equally and deducted from the remaining weeks'
      budgets instead. Per the user: this rollover is computed once, "only
      on the 1st day of next week, when the balances are computed" — not
      continuously recalculated intra-week — so a week's own progress bar
      stays stable while it's still in progress, and only shifts once it's
      closed out. Needed on both Android (`BudgetMath.kt`) and Windows
      (`core-domain`'s `WeeklyBudget`/`EvaluateBudget`) — keep them in
      sync by hand as usual.
- [ ] **Show the weekly budget numbers, not just month.** The household
      weekly progress bar (both apps) currently doesn't surface the
      week's own budget/spent/remaining figures the way the monthly bar
      does — only a status color. Add the same "₹X of ₹Y" /
      remaining-or-over caption to the weekly bar that the monthly bar
      already has.
- [ ] **Per-person settlement for households**, opt-in via household
      settings — mirrors the existing activity/trip balance-and-settle-up
      feature (equal-split net balance per member, "who owes whom"), but
      for an ongoing household rather than a one-off trip. User's own
      framing: useful for "multiple bachelors... splitting the cost of
      items" where, unlike a family sharing one pot, each person's net
      contribution should be tracked and settled. Needs a toggle in
      Household Settings (both apps), the balance computation itself
      (can likely reuse/adapt the existing trip `TripBalances`/
      `activityBalances` logic against household expenses+members instead
      of activity expenses+participants), and a display surface for it
      (probably alongside the existing budget bars on the household
      screen, only when enabled).

**Known gaps (v0 placeholders, not yet real):**

- [ ] Real device-to-device (Android↔Android) sync — phones only sync via
      a paired Windows instance today, not directly with each other.
- [ ] Real QR pairing/crypto handshake (`Core/sync/Pairing.kt`) — all QR
      flows on both apps currently use an unauthenticated placeholder
      payload (`JoinInvitePayload`), not a real handshake.
- [ ] Conflict resolution beyond "server wins, local queues pushes" —
      concurrent edits to the same expense from two synced devices aren't
      reconciled, just last-pull-wins.
- [ ] Trip expense splits are equal-only in both UIs — `core-domain`'s
      `SplitCalculator` already supports exact/percentage/weighted, no
      dialog for picking a mode yet.
- [ ] Settling up: suggested settlements are computed and shown, but
      there's no button yet to actually record a `Settlement`.
- [ ] Editing a category/member/participant's name in place isn't
      supported on either app (only create + archive/remove).
- [ ] Windows category management has no rename/archive UI (create-only
      from the Add Expense picker).
- [ ] Android's background sync is a simple in-app coroutine loop tied to
      the process lifetime — no WorkManager, so it stops when the app is
      killed/backgrounded long enough for Android to reclaim it.
- [ ] No notifications on either platform (budget alerts, sync events).
- [ ] Windows: no system tray / start-on-login.
- [ ] Windows: no proper installer/packaging — dev-only via Gradle.
- [ ] The full binary `Transport`/`SyncChannel` operation-log sync
      protocol from `Design/Windows/02-transport-implementation.md` isn't
      wired up — the REST API is used as an interim sync transport
      instead; mDNS discovery exists just for address-finding.

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
