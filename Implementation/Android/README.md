# Android

The Android phone client. First draft, built as a **standalone REST
client** rather than a Kotlin Multiplatform consumer of `Implementation/Core/`
— see "Why standalone" below. This deviates from the original plan in
[Design/Android/00-README.md](../../Design/Android/00-README.md) and
[Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md),
which assumed shared Core modules and real device-to-device mesh sync;
those documents describe the eventual destination, not the current state.

## What's implemented

- **Connect / join**: on first launch, connect to a Windows app instance
  either by scanning the QR code shown in that app's Household/Activity
  Settings ("+ Add" — see `JoinInvite.kt`), or by typing the machine's
  `ip:port` manually. The QR carries the server's address plus which
  household/activity to jump straight to.
- **Navigation shell**: a hamburger-icon drawer (`KharchaApp.kt`) listing
  Households/Activities, mirroring the Windows app's sidebar sections, with
  "+" to create either and a "Scan QR to join" / "Change server" footer.
- **Landing/Summary screen** (`SummaryScreen.kt`): nothing is selected by
  default, so this shows each household's *monthly* budget only (no room
  for the full weekly breakdown on a phone screen), plus a rollup of what
  you're owed / you owe across activities you've set an identity in.
- **Household screen** (`HouseholdScreen.kt`): two budget bars — this
  month, and the current week with prev/next arrows to browse other weeks
  — plus the expense list (add/edit/delete, date picker, category dropdown,
  paid-by as tappable chips).
- **Activity screen** (`ActivityScreen.kt`): overall budget bar, your
  balance and everyone's balances, expense list (add/edit/delete).
- **Identity**: there's no login. The first time you open a household/
  activity on this device, you pick which existing member/participant is
  "you" (or add yourself) — see `IdentityDialog.kt` and
  `data/ConnectionStore.kt`. This is what powers the owed/owe rollup and
  defaults who paid on a new expense.

## Why standalone (not shared Core modules)

`Implementation/Core/{model,domain,sync}` are Kotlin Multiplatform modules
with a `jvm()` target, consumed by Windows directly. Making this Android
app consume them the same way would need each Core module to also declare
an `androidTarget()` — real Android Gradle Plugin integration this project
had never exercised before, with no local emulator/device available in the
environment this first draft was built in to verify it actually built and
ran, only that Gradle resolved it. Given that risk, this app instead talks
to the Windows app's existing REST API (`Implementation/Windows/.../server/
Routes.kt`) over the LAN — the exact same API the Windows UI itself uses
against its own local server — with its own matching set of DTOs
(`data/Dto.kt`, kept in sync with `Implementation/Windows/.../server/Dto.kt`
by hand, not shared). This is a normal, decoupled client/server split, not
a shortcut that needs undoing later — consolidating into a shared `Core/api`
module is a nice-to-have if the DTOs drift, not a requirement.

## Why the QR isn't the real pairing handshake yet

`Core/sync/Pairing.kt` already designs a real two-step QR exchange (new
device shows its pubkey, existing device scans it and shows back a wrapped
household key). That flow needs a second device on the *other* end of the
handshake to build and test against — which is exactly this app, now that
it exists. The QR implemented here (`JoinInvite.kt` on both sides) is a
simpler, unauthenticated placeholder: server address + household/activity
id + name, JSON-encoded. It gets a real join code onto the screen and lets
this app act on it today; swapping in the real handshake is future work,
not a correctness bug in what's here.

## Verified so far

Built and run in an Android emulator (no physical device available) against
a live Windows app instance over the LAN:
- Connect via manual `ip:port` entry (QR scanning itself — the camera
  flow — could not be exercised without a real QR code to point a camera
  at, only that `ScanContract` launches without crashing).
- A first attempt crashed the whole app on a network failure (unhandled
  exception in a `LaunchedEffect` coroutine) — fixed by wrapping every
  screen's initial load in try/catch with a retry screen
  (`ConnectionErrorScreen.kt`); reconfirmed working afterwards.
- Drawer navigation, household screen's monthly + weekly bars (including
  the calendar-day week math matching the Windows app's), identity
  selection, and a full add-expense round trip (amount, category, paid-by,
  date → save → list and both bars update).

**Not yet interactively verified**: the activity/trip screen's balances UI,
edit/delete of an existing expense, and the actual QR camera scan (only
that the scanner launches) — these follow the same patterns as what was
verified and are expected to work, but say so rather than claim more than
was checked.

## Not implemented

- Real device-to-device sync / offline-first mesh (this app is only a
  thin client to a Windows server that must be reachable on the LAN).
- The real pairing/crypto handshake (see above).
- Sending a join link some other way (e.g. WhatsApp) — the user's own
  words: "not sure how it will work without a server, but will keep it for
  future."
- Editing a category, member, or participant's name in place — matches
  the Windows app's current limitation, not a new gap.
- WorkManager background sync, notifications, system tray equivalent —
  all aspirational per the original Design docs, not started.

## Running it

This is its own Gradle root (not part of the Windows/Core composite build)
— open `Implementation/Android/` directly in Android Studio, or:

```
./gradlew :app:assembleDebug
```

Needs an Android SDK with `compileSdk 36` / `build-tools 36.0.0` installed
and `local.properties` pointing `sdk.dir` at it (gitignored, per-machine).
The Windows app must be running and reachable on the same network; its
server binds to all interfaces on port 47321 by default
(`Implementation/Windows/.../server/Server.kt`), and the app's manifest
sets `usesCleartextTraffic="true"` since the connection is plain HTTP, not
HTTPS, on the local network.
