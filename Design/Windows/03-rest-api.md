# REST & WebSocket API

Base path `/api/v1`. All mutating endpoints go through the same
`core-domain` use cases the Android app calls locally — the API is a thin
HTTP wrapper, not a parallel business-logic path.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/household` | Household + members + categories |
| `GET` | `/budgets/{year}/{month}` | Monthly budget + derived weekly breakdown + status |
| `POST` | `/budgets` | Set/update a monthly budget |
| `GET` | `/expenses?from=&to=&category=` | Household expenses, filtered |
| `POST` | `/expenses` | Record a household expense |
| `GET` | `/trips` | List trips (open + closed) |
| `POST` | `/trips` | Create a trip |
| `GET` | `/trips/{id}` | Trip detail: participants, expenses, balances, status |
| `POST` | `/trips/{id}/expenses` | Record a trip expense + split |
| `POST` | `/trips/{id}/settlements` | Record a real settle-up payment |
| `GET` | `/analytics/categories?window=` | Category breakdown for a time window |
| `GET` | `/analytics/trends` | Month-over-month deltas |
| `GET` | `/analytics/suggestions` | Rule-based spending suggestions |
| `GET` | `/devices` | Paired devices list |
| `POST` | `/devices/pairing/start` | Begin a new-device pairing flow, returns QR payload |
| `WS` | `/ws/changes` | Push notification whenever any entity changes (sync-applied or local write) |

Every `POST` returns the resulting entity plus its `BudgetEvaluation` where
relevant (e.g. `POST /expenses` returns the new expense *and* the updated
weekly status — see [Design/Core/05-domain-logic.md](../Core/05-domain-logic.md#budget-status-evaluation)),
so the dashboard can update its red/nearing highlighting without a second
round-trip.
