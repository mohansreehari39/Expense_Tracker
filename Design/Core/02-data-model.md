# Data Model (`core-model`)

## Entities

Kotlin `data class` per entity in
[Arch/02-data-model.md](../../Arch/02-data-model.md#household-domain):
`Household`, `Member`, `Category`, `MonthlyBudget`, `HouseholdExpense`,
`Trip`, `TripParticipant`, `TripExpense`, `ExpenseSplit`, `Settlement`,
`Device`. All IDs are `String` (UUIDv4). Money is represented as a `Long`
minor-unit integer (paise/cents) plus a `String` ISO 4217 currency code —
never floating point, to keep sync-merged sums exact.

## `Operation`

```kotlin
data class Operation(
    val opId: String,          // UUID, globally unique, dedup key
    val entityType: EntityType, // enum: HOUSEHOLD_EXPENSE, TRIP_EXPENSE, ...
    val entityId: String,
    val opType: OpType,         // CREATE, UPDATE, DELETE
    val patch: Map<String, JsonElement>, // changed fields only, for UPDATE
    val authorDeviceId: String,
    val hlc: Hlc,
    val receivedFrom: String? = null, // last relay hop, audit only
)
```

`patch` uses `kotlinx.serialization`'s `JsonElement` so the log format is
storage-agnostic and forward-compatible (unknown fields from a newer app
version are preserved and re-transmitted even if this version doesn't
understand them — important since phones and the server won't always be on
the same app version).

`Operation.patch` uses `JsonElement` specifically so that a future field
added by a newer app version round-trips unmodified through an older
version acting as a relay (it doesn't need to understand a field to store
and forward it). Bump a `schemaVersion` constant only on breaking changes
(e.g. renaming an existing field), not on additive ones.

## Hybrid Logical Clock

```kotlin
data class Hlc(val physical: Long, val counter: Int, val deviceId: String) :
    Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int = compareValuesBy(
        this, other, Hlc::physical, Hlc::counter, Hlc::deviceId,
    )
}

class HlcClock(private val deviceId: String) {
    private var last = Hlc(0, 0, deviceId)
    fun tick(): Hlc { /* local event: advance vs wall clock + last */ }
    fun receive(remote: Hlc): Hlc { /* merge on incoming operation */ }
}
```

Standard HLC algorithm (Kulkarni et al.): `tick()` takes
`max(wallClockNow, last.physical)`, bumping the counter only when the
physical time didn't advance; `receive()` additionally folds in the remote
timestamp so causality across devices is preserved. `deviceId` is the final
tiebreaker for `compareTo`, giving a total order with no ties.
