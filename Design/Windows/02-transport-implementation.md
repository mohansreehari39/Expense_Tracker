# Transport Implementation (JVM)

Implements `core-sync`'s `Transport`/`SyncChannel` interfaces (see
[Design/Core/03-sync-protocol.md](../Core/03-sync-protocol.md)) for the JVM:

- **Discovery**: JmDNS advertises/browses `_expensetracker._tcp.local.`,
  matching the Android NSD side of the same protocol.
- **Channel**: plain TCP socket on a fixed configurable port (default
  `47321`), one connection per sync session, framed with a 4-byte
  length-prefix per message (matches the wire messages in
  [Design/Core/03-sync-protocol.md](../Core/03-sync-protocol.md#session-state-machine)).
- **`OperationStore`**: SQLDelight-backed; `append` does an
  `INSERT OR IGNORE` keyed on `opId` (idempotency for free at the DB
  layer), `opsSince` is an indexed query on `(author_device_id, hlc)`.

Because both this implementation and the Android NSD implementation
(see [Design/Android/02-transport-and-permissions.md](../Android/02-transport-and-permissions.md))
speak the exact same framing and wire messages, neither side needs to know
what platform is on the other end of a sync session.
