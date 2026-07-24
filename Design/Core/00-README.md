# Core Logic — Design

Design for the shared Kotlin Multiplatform modules used by both apps:
`core-model`, `core-sync`, `core-domain` (named in
[Arch/06-tech-stack.md](../../Arch/06-tech-stack.md)). This is the one part
of the system that must behave *identically* on Android and Windows, so
it's designed once here rather than per-platform. Source code for this
subsystem lives in [`Implementation/Core/`](../../Implementation/Core/README.md).

Read in this order:

1. [01-module-boundaries.md](01-module-boundaries.md) — how `core-model`,
   `core-sync`, `core-domain` relate, and what each owns
2. [02-data-model.md](02-data-model.md) — entities, the `Operation` log
   record, the hybrid logical clock
3. [03-sync-protocol.md](03-sync-protocol.md) — `Transport`/`OperationStore`
   interfaces, the sync session state machine, wire messages, conflict
   resolution
4. [04-pairing-and-crypto.md](04-pairing-and-crypto.md) — device pairing
   and encryption of sync traffic
5. [05-domain-logic.md](05-domain-logic.md) — budget derivation/alerting
   and trip split/settlement algorithms
6. [06-testing-strategy.md](06-testing-strategy.md) — fake transport/store
   for simulating multi-peer sync in tests
