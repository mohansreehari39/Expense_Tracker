# Test

Test plans and test code, including dedicated coverage for the sync/merge
engine described in [`Arch/03-sync-protocol.md`](../Arch/03-sync-protocol.md) —
the highest-risk part of the system and the one most worth testing
thoroughly (conflict resolution, gossip convergence, interrupted syncs).

Mostly not yet populated (the real test suites live alongside the code
they test — see `Implementation/Core/*/src/commonTest`), but manual/UI
test tooling that doesn't belong in production source lives here:

- [`Windows/seed-data.ps1`](Windows/seed-data.ps1) (and `seed-data.sh` for
  WSL/Linux/macOS) — populates a running Windows app with realistic sample
  households and activities via its own REST API, for visually testing the
  UI without manually clicking through create/add-expense flows. See
  [`Implementation/Windows/README.md`](../Implementation/Windows/README.md)
  for usage.
