# Core Logic — Implementation Plan

Build plan for `core-model`, `core-sync`, `core-domain` per
[Design/01-core-logic-design.md](../Design/01-core-logic-design.md). This is
the first subsystem to implement — both apps depend on it.

## Project structure

```
core/
  model/
    src/commonMain/kotlin/et/core/model/
      Household.kt, Member.kt, Category.kt, MonthlyBudget.kt,
      HouseholdExpense.kt, Trip.kt, TripParticipant.kt, TripExpense.kt,
      ExpenseSplit.kt, Settlement.kt, Device.kt,
      Operation.kt, EntityType.kt, OpType.kt, Hlc.kt, HlcClock.kt
    src/commonTest/kotlin/et/core/model/
  sync/
    src/commonMain/kotlin/et/core/sync/
      Transport.kt, SyncChannel.kt, OperationStore.kt, PeerHandle.kt,
      SyncSession.kt, WireMessage.kt, Crypto.kt, Pairing.kt
    src/commonTest/kotlin/et/core/sync/
      FakeTransport.kt, FakeOperationStore.kt, SyncSessionTest.kt,
      GossipConvergenceTest.kt
  domain/
    src/commonMain/kotlin/et/core/domain/
      Repository.kt (interface),
      RecordHouseholdExpense.kt, SetMonthlyBudget.kt,
      CreateTrip.kt, AddTripExpenseWithSplit.kt, SettleUp.kt,
      WeeklyBudget.kt, EvaluateBudget.kt, DebtSimplification.kt
    src/commonTest/kotlin/et/core/domain/
  build.gradle.kts (root, shared version catalog references)
```

Three separate Gradle modules (`core:model`, `core:sync`, `core:domain`),
each a `kotlin("multiplatform")` module targeting `jvm()` and `android()`
(the Windows app consumes the `jvm` target, the Android app the `android`
target — same source, two compiled artifacts).

## Dependencies (via a Gradle version catalog, `gradle/libs.versions.toml`)

- `kotlinx-serialization-json` + `kotlinx-serialization-cbor` — operation
  payloads and wire messages.
- `kotlinx-coroutines-core` — suspend functions in `Transport`/`SyncSession`.
- `kotlinx-datetime` — month/week date math for budget derivation.
- `kotlinx-io` or plain `ByteArray` — wire framing (start minimal, add
  `kotlinx-io` only if manual framing gets unwieldy).
- Crypto: `org.lighthousegames:crypto` or hand-rolled AES-GCM via
  `javax.crypto` on JVM + `androidx.security.crypto`-backed equivalent on
  Android — evaluate at implementation time; interface (`Crypto`) is
  defined in `core-sync` regardless so the choice is swappable without
  touching call sites.
- Test-only: `kotlin-test`, `kotlinx-coroutines-test` (for
  `runTest`/virtual time in sync tests).

No SQLDelight dependency inside `core-*` itself — `OperationStore` and
`Repository` are interfaces; the concrete SQLDelight schema lives in each
platform app (see the Windows and Android implementation docs), not here,
since the *shape* of persistence is a platform concern (SQLDelight driver
setup differs per target) even though the *schema* is shared via a
`.sqldelight` file each app module includes.

## Build milestones

1. **M1 — `core-model` entities + `Operation`/`Hlc`.** Pure data classes,
   `kotlinx.serialization` annotations, unit tests for `Hlc.compareTo` and
   `HlcClock.tick`/`receive` ordering properties.
2. **M2 — `core-sync` interfaces + in-memory fakes.** `Transport`,
   `SyncChannel`, `OperationStore` interfaces; `FakeTransport`/
   `FakeOperationStore` for testing before any real network code exists.
3. **M3 — Sync session state machine against fakes.** Implement
   `SyncSession` per the state diagram in the design doc, prove two fake
   peers converge, then three peers with one acting purely as a relay
   (gossip test) — this is the highest-value test to get right early,
   independent of any real transport.
4. **M4 — Conflict resolution + tombstones.** Field-level merge, delete-
   wins-over-update, deterministic ordering; property-based tests
   (generate random operation orderings, assert identical end state
   regardless of order).
5. **M5 — Pairing/crypto.** `Crypto` AES-GCM wrap/unwrap, QR payload
   encode/decode, two-step pairing handshake logic (platform apps supply
   the actual QR rendering/scanning).
6. **M6 — `core-domain` budget logic.** `weekAllocation`, `evaluateBudget`,
   unit tests covering month-boundary partial weeks and the
   nearing/over thresholds.
7. **M7 — `core-domain` trip logic.** Split validation (all four modes),
   debt simplification algorithm, unit tests including edge cases (three-
   way circular debt, already-settled trip).

Each milestone ships with its own test suite in the same PR — this module
has no UI to eyeball, so tests are the only verification available and
should not be deferred to a later pass.

## Versioning / compatibility note

`Operation.patch` uses `JsonElement` specifically so that a future field
added by a newer app version round-trips unmodified through an older
version acting as a relay (it doesn't need to understand a field to store
and forward it). Bump a `schemaVersion` constant only on breaking changes
(e.g. renaming an existing field), not on additive ones.
