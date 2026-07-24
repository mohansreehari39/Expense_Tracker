# Design

Detailed design that implements the architecture in [`Arch/`](../Arch/00-README.md):
API contracts, database schemas, sync wire formats, and screen-level UI
design for the Android and Windows apps.

Build order is Core Logic → Windows App → Android App (see
[Arch/06-tech-stack.md](../Arch/06-tech-stack.md) and the rationale in each
doc below), so the shared core is validated on desktop before the
mobile-specific transport and UI are built.

1. [01-core-logic-design.md](01-core-logic-design.md) — shared
   `core-model` / `core-sync` / `core-domain` modules used by both apps
2. [02-windows-app-design.md](02-windows-app-design.md) — desktop
   server/dashboard app
3. [03-android-app-design.md](03-android-app-design.md) — phone client
