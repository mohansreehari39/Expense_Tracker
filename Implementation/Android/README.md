# Android

The Android phone client. Built as a **local-first standalone app**: every
household/activity is created and used entirely on-device via Room, with
zero server ever required. A device can optionally pair with a Windows
instance and join specific households/activities to it, at which point
background sync starts reconciling that data — but nothing in the app
depends on a server existing. This is a deliberate architecture correction
from an earlier draft that required a server connection before anything
worked at all; see "Why local-first" below.

This deviates from the original plan in
[Design/Android/00-README.md](../../Design/Android/00-README.md) and
[Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md),
which assumed shared Core modules and real device-to-device mesh sync;
those documents describe the eventual destination, not the current state.
Android↔Android sync is explicitly out of scope for now — only Android↔Windows
sync (via a paired server) is implemented.

## What's implemented

- **Signup** (`SignupScreen.kt`): shown once, before anything else,
  independent of any server — name (required), age/gender/phone/email
  (all optional). Stored in Room (`data/local/ProfileEntity.kt`) via
  `LocalRepository.saveProfile`. There's no login; this profile's name is
  reused as this device's identity (`isMe` flag) in every household/activity
  it creates or joins.
- **Home screen after signup**: goes straight to an empty drawer/NavHost
  shell — no forced connect/pairing gate of any kind. "No households yet /
  no activities yet — add one from the drawer" until the user creates or
  joins one.
- **Create household/activity**: fully local, no server involved — writes
  directly to Room via `LocalRepository.createHousehold`/`createActivity`,
  auto-seeding default categories and this device's own member/participant
  row.
- **Join Household/Activity** (QR, drawer action): scans the same
  household/activity QR shown in the Windows app's Settings ("+ Add" — see
  `JoinInvite.kt`). Pulls that household/activity's current state into a
  new or existing *linked* local copy (`SyncEngine.joinHousehold`/
  `joinActivity`) and auto-pairs with the server embedded in the QR if not
  already paired.
- **Connect to Server** (QR, drawer action): a *separate* device-level
  pairing action, independent of any specific household/activity — scans
  the QR from the Windows app's new "Add Android Device" button
  (`PairAndroidDeviceDialog.kt`). Registers a `PairedServerEntity` keyed by
  `host:port`. Pairing alone does not join or sync anything by itself; it
  just makes a server available for the join flow and for background sync
  of anything already linked to it.
- **Background sync**: once at least one household/activity is linked to a
  paired server, a loop in `KharchaApp.kt` calls `SyncEngine.syncAll` every
  15s whenever that server is reachable — "server wins, local queues
  pushes": offline-created/edited/deleted items are pushed first, then the
  server's current state is pulled and treated as authoritative. A
  household/activity never joined to a server is completely untouched by
  this — see "Sync semantics" below.
- **Dark/light mode**: a switch in the drawer footer, this device's own
  choice, not synced with the Windows app's theme setting.
- **Navigation shell** (`KharchaApp.kt`): a hamburger-icon drawer listing
  Households/Activities (from Room, reactively via `Flow`), "+" to create
  either locally, and the two QR actions above.
- **Landing/Summary screen** (`SummaryScreen.kt`): nothing is selected by
  default, so this shows each household's *monthly* budget (computed
  on-device via `BudgetMath.kt`, no server needed) plus a rollup of what
  you're owed / you owe across activities.
- **Household screen** (`HouseholdScreen.kt`): two budget bars — this
  month, and the current week with prev/next arrows to browse other weeks
  — plus the expense list (add/edit/delete, date picker, category dropdown,
  paid-by as tappable chips). All reads/writes go through `LocalRepository`.
- **Activity screen** (`ActivityScreen.kt`): overall budget bar, your
  balance and everyone's balances (computed via `LocalRepository.activityBalances`,
  equal-split), expense list (add/edit/delete).

## Local persistence (Room)

`data/local/Entities.kt` + `Daos.kt` + `AppDatabase.kt` define the on-device
schema: `ProfileEntity` (singleton), `PairedServerEntity`, and per-household/
activity entities (`HouseholdEntity`, `CategoryEntity`, `MemberEntity`,
`HouseholdExpenseEntity`, `ActivityEntity`, `ParticipantEntity`,
`ActivityExpenseEntity`). A household/activity's `pairedServerId`/`remoteId`
are non-null only once it's been linked to a server — null means purely
local and always will be, unless joined later. Expense rows additionally
track `pendingSync`/`pendingDelete` for offline edits awaiting push.

`data/LocalRepository.kt` is the single facade the UI talks to — it is the
only source of truth the UI reads/writes; `SyncEngine` never bypasses it.

`data/BudgetMath.kt` is a local Kotlin port of Core's weekly/monthly budget
evaluation (calendar-day weeks: 1-7, 8-14, 15-21, 22-28, then a short final
week; proportional week allocation; OK/NEARING/OVER thresholds) — kept in
sync with `Implementation/Core/domain/.../WeeklyBudget.kt`/`EvaluateBudget.kt`
by hand, since this app can't depend on the Core KMP modules (see "Why
standalone" below) and must compute budgets with zero network dependency.

## Sync semantics ("server wins, local queues pushes")

This is v0, deliberately simple, with no real conflict resolution:

1. Any offline-created/edited/deleted expense on a linked household/activity
   is pushed to the paired server first (`SyncEngine.pushHouseholdPending`/
   `pushActivityPending`).
2. The server's current state for that household/activity is then pulled
   and used to overwrite local *synced* (non-pending) rows
   (`pullHousehold`/`pullActivity` + `LocalRepository.replaceSynced*`).
3. A household/activity never linked to a server (`pairedServerId == null`)
   is untouched by any of this.

`SyncEngine.resolveApiClient` re-resolves a paired server's live LAN address
via mDNS (`data/NsdDiscovery.kt`) on every sync attempt rather than trusting
a cached IP, falling back to the last-known address if discovery times out.

## Why local-first (not server-required)

An earlier draft of this app required a server connection before any
household/activity could be created or viewed at all — effectively a thin
REST client with a hard "Connect" gate. That inverted the project's actual
premise (the app should work standalone, with sync as an optional add-on),
so it was rebuilt: Room became the primary data store, the Windows REST API
became sync transport only, and pairing/joining became two separate,
explicit, optional actions instead of a mandatory first step.

## Why standalone (not shared Core modules)

`Implementation/Core/{model,domain,sync}` are Kotlin Multiplatform modules
with a `jvm()` target, consumed by Windows directly. Making this Android
app consume them the same way would need each Core module to also declare
an `androidTarget()` — real Android Gradle Plugin integration this project
had never exercised before. Given that risk, this app instead talks to the
Windows app's existing REST API (`Implementation/Windows/.../server/
Routes.kt`) purely as sync transport, with its own matching set of DTOs
(`data/Dto.kt`, kept in sync with `Implementation/Windows/.../server/Dto.kt`
by hand, not shared) and its own local port of the budget math (`BudgetMath.kt`).

## Why the QR isn't the real pairing handshake yet

`Core/sync/Pairing.kt` already designs a real two-step QR exchange (new
device shows its pubkey, existing device scans it and shows back a wrapped
household key). The QR implemented here (`JoinInvite.kt` on both sides) is a
simpler, unauthenticated placeholder shared by both QR flows: `kind`
("household" | "activity" | "server") + id + name + host + port,
JSON-encoded. It gets a real join/pair code onto the screen and lets this
app act on it today; swapping in the real handshake is future work, not a
correctness bug in what's here.

## Not implemented

- Real device-to-device (Android↔Android) sync — explicitly deferred; only
  Android↔Windows sync via a paired server is implemented.
- The real pairing/crypto handshake (see above).
- Conflict resolution beyond "server wins" — concurrent edits to the same
  expense from two paired devices are not reconciled, just last-pull-wins.
- Editing a category, member, or participant's name in place — matches
  the Windows app's current limitation, not a new gap.
- WorkManager-based background sync (currently a simple in-app coroutine
  loop, only runs while the app process is alive), notifications, system
  tray equivalent — all aspirational per the original Design docs.

## Running it

This is its own Gradle root (not part of the Windows/Core composite build)
— open `Implementation/Android/` directly in Android Studio, or:

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Prefer `assembleDebug` + a separate `adb install -r` over `installDebug` —
they're equivalent, but splitting them makes it obvious whether a slow run
is the build or an adb hiccup, rather than one bundled task stalling
silently on either half.

Needs an Android SDK with `compileSdk 36` / `build-tools 36.0.0` installed
and `local.properties` pointing `sdk.dir` at it (gitignored, per-machine).
The app works fully standalone with no Windows app running at all. To use
sync, the Windows app must be running and reachable on the same network;
its server binds to all interfaces on port 47321 by default and advertises
itself via mDNS (`Implementation/Windows/.../server/Server.kt`,
`LanAdvertiser.kt`). The app's manifest sets `usesCleartextTraffic="true"`
since the sync connection is plain HTTP, not HTTPS, on the local network.
