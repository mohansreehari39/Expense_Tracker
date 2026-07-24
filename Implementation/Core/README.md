# Core

Source for the shared Kotlin Multiplatform modules used by both the
Windows and Android apps: `core-model`, `core-sync`, `core-domain`.

Design: [`Design/Core/`](../../Design/Core/00-README.md).

Not yet populated — no source code has been written yet. Expected layout
once implementation starts:

```
core/
  model/    # entities, Operation, Hlc — no I/O, no platform deps
  sync/     # Transport/OperationStore interfaces, session state machine
  domain/   # use cases: budget derivation, splits, settlement
```
