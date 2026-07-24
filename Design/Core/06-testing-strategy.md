# Testing Strategy

`core-sync` ships an in-memory `FakeTransport` + `FakeOperationStore` pair
specifically so `core-sync`'s own test suite (see
[`Test/`](../../Test/README.md)) can spin up 3+ simulated peers in a single
JVM test process and assert:

- **Convergence** — two peers exchanging operations in either order end up
  with identical materialized state.
- **Gossip propagation** — a third peer acting purely as a relay correctly
  forwards operations it didn't originate, without ever connecting the two
  original peers directly.
- **Interrupted-sync resumption** — a session cut off mid-`OpsBatch` and
  retried later ends in the same converged state as an uninterrupted one,
  with no duplicated or lost operations.
- **Conflict resolution properties** — property-based tests (random
  operation orderings) assert the folded end state is identical regardless
  of the order operations are replayed in.

This is the same reasoning as
[Arch/06-tech-stack.md](../../Arch/06-tech-stack.md#what-this-buys-concretely):
one implementation, one place to prove it correct, without needing a real
phone, a real server, or real network hardware to test the hardest part of
the system.
