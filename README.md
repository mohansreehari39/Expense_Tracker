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
