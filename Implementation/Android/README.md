# Android

The Android phone client. First draft, built as a **standalone REST
client** rather than a Kotlin Multiplatform consumer of `Implementation/Core/`
— see "Why standalone" below. This deviates from the original plan in
[Design/Android/00-README.md](../../Design/Android/00-README.md) and
[Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md),
which assumed shared Core modules and real device-to-device mesh sync;
those documents describe the eventual destination, not the current state.

## What's implemented

- **Signup**: shown once, before anything else, independent of any server
  connection — just a name (`SignupScreen.kt`, stored via
  `data/ConnectionStore.kt`). There's no login; this name is reused
  everywhere as this device's identity (`data/IdentityResolver.kt`
  auto-resolves it into a member/participant id for every household/
  activity the device can see, the first time each is fetched — no
  per-screen "who are you" prompt).
- **Connect / join**: scanning the QR code shown in the Windows app's
  Household/Activity Settings ("+ Add" — see `JoinInvite.kt`) is the only
  way to connect in a release build. The server's *current* LAN address is
  found via mDNS discovery (`data/NsdDiscovery.kt`) rather than trusting a
  literal address, so it keeps working after the Windows machine's IP
  changes — see "Discovery and dynamic IPs" below. Manual `ip:port` entry
  still exists but only in debug builds (`BuildConfig.DEBUG`), as a
  developer convenience for when multicast doesn't work (e.g. an emulator).
- **Dark/light mode**: a switch in the drawer footer, this device's own
  choice, not synced with the Windows app's theme setting.
- **Navigation shell**: a hamburger-icon drawer (`KharchaApp.kt`) listing
  Households/Activities, mirroring the Windows app's sidebar sections, with
  "+" to create either and a "Scan QR to join" / "Change server" footer.
- **Landing/Summary screen** (`SummaryScreen.kt`): nothing is selected by
  default, so this shows each household's *monthly* budget only (no room
  for the full weekly breakdown on a phone screen), plus a rollup of what
  you're owed / you owe across activities.
- **Household screen** (`HouseholdScreen.kt`): two budget bars — this
  month, and the current week with prev/next arrows to browse other weeks
  — plus the expense list (add/edit/delete, date picker, category dropdown,
  paid-by as tappable chips).
- **Activity screen** (`ActivityScreen.kt`): overall budget bar, your
  balance and everyone's balances, expense list (add/edit/delete).

## Discovery and dynamic IPs

The Windows server advertises itself on the LAN via mDNS
(`Implementation/Windows/.../server/LanAdvertiser.kt`, using JmDNS —
matches the service type `_expensetracker._tcp.local.` already anticipated
in `Design/Windows/02-transport-implementation.md`). Android resolves it
live via `NsdManager` every time it needs to connect (`data/NsdDiscovery.kt`)
— nothing about the server's address is ever trusted from a cache alone.
This solves two things at once: production builds don't need a manual IP
field at all (just the QR, which identifies *which* household/activity,
not *where* the server is), and a DHCP lease change on the Windows machine
heals itself on the next connect instead of breaking a previously-scanned
QR. If discovery times out (multicast blocked on some networks, or the
Windows app isn't running), the QR's own embedded address
(`JoinInvitePayload.host`/`port`) is used as a fallback.

**Caveat:** mDNS/multicast is known to be unreliable on Android emulators
specifically (see [[testing-conventions]] in project memory) — this was
implemented and compile-verified, but the emulator available earlier in
this project's session was gone by the time this feature was built, so it
has **not** been runtime-verified end-to-end. Confirming it needs a real
phone and the Windows PC on the same wifi.

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

**Runtime-verified in an Android emulator** (first draft, before the
signup/dark-mode/discovery batch below): connect via manual `ip:port`,
drawer navigation, household screen's monthly + weekly bars (including the
calendar-day week math matching the Windows app's), identity selection,
and a full add-expense round trip — plus a real crash found and fixed
(unhandled network errors in a `LaunchedEffect` now show a retry screen,
`ConnectionErrorScreen.kt`, instead of taking down the app).

**Compile-verified only** (emulator was no longer available when this was
built): signup screen, automatic identity resolution replacing the old
per-screen prompt, the dark/light toggle, debug-gated manual entry, and
the mDNS/NSD discovery path. These follow the same patterns as what was
runtime-verified, but say so rather than claim more than was checked —
worth an actual on-device pass before relying on them.

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
server binds to all interfaces on port 47321 by default and now also
advertises itself via mDNS (`Implementation/Windows/.../server/Server.kt`,
`LanAdvertiser.kt`). The app's manifest sets `usesCleartextTraffic="true"`
since the connection is plain HTTP, not HTTPS, on the local network.
