# Design

Detailed design that implements the architecture in [`Arch/`](../Arch/00-README.md):
API contracts, database schemas, sync wire formats, and screen-level UI
design for the Android and Windows apps — one subfolder per subsystem,
each with multiple docs.

Build order is Core → Windows → Android (see
[Arch/06-tech-stack.md](../Arch/06-tech-stack.md) and the rationale in each
folder below), so the shared core is validated on desktop before the
mobile-specific transport and UI are built.

1. [`Core/`](Core/00-README.md) — shared `core-model` / `core-sync` /
   `core-domain` modules used by both apps
2. [`Windows/`](Windows/00-README.md) — desktop server/dashboard app
3. [`Android/`](Android/00-README.md) — phone client
