# Architecture Docs — Index

This folder contains the system architecture for the Expense Tracker project. Read in this order:

1. [01-system-overview.md](01-system-overview.md) — actors, components, topology, why this shape
2. [02-data-model.md](02-data-model.md) — entities for household expenses, trip expenses, budgets
3. [03-sync-protocol.md](03-sync-protocol.md) — how phones sync with each other and the server, offline-first
4. [04-android-app-architecture.md](04-android-app-architecture.md) — internal layering of the Android client
5. [05-windows-server-architecture.md](05-windows-server-architecture.md) — sync hub, API, analytics engine, dashboard
6. [06-tech-stack.md](06-tech-stack.md) — concrete technology choices and why

Sibling folders (populated in later passes, not this one):
- `Design/` — detailed component/API/schema design that implements this architecture
- `Implementation/` — actual source code
- `Test/` — test plans and test code
