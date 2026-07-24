# Desktop UI & Web View

## Desktop UI (Compose Multiplatform Desktop)

Screens:
- **Household Dashboard** — this month's total vs. budget, current week's
  status banner/progress bar (red when `OVER`, amber when `NEARING`),
  category breakdown chart, recent expenses list, quick "Add expense"
  entry.
- **Trips** — list of open/closed trips with at-a-glance status; opening
  one shows participants, expense list, running balances, and a
  "Settle Up" panel showing the simplified-debt suggestions from
  [Design/Core/05-domain-logic.md](../Core/05-domain-logic.md#trip-settlement-debt-simplification).
- **Analytics** — trends over time, suggestions list.
- **Devices** — paired devices, "Add a device" (renders the pairing QR,
  see [Design/Core/04-pairing-and-crypto.md](../Core/04-pairing-and-crypto.md)),
  last-synced time per device (from `Device.last_seen_hlc`, human-readable
  as "synced 2 hours ago").
- **Settings** — port number, start-on-login toggle, currency, weekly
  nearing-threshold override (defaults to 80%).

All screens read exclusively through the REST/WebSocket API layer
([03-rest-api.md](03-rest-api.md)), not directly against the DB — this
keeps the desktop UI and the thin web view genuinely interchangeable/
consistent, and means the UI layer has zero sync-specific code to get
wrong.

## Thin web view

A single static HTML/JS bundle served by Ktor at `/`, hitting the same
`/api/v1/*` endpoints and the WebSocket for live updates. Deliberately not
a second implementation of the dashboard — same data, simpler layout,
useful for checking analytics from a phone browser on the home network
without opening the Android app.
