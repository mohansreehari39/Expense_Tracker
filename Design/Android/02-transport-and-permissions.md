# Transport Implementation & Permissions

## Platform `Transport` implementation

- **Primary (same LAN)**: `NsdManager` advertises/discovers
  `_expensetracker._tcp`, then opens a plain `Socket`/`ServerSocket` on a
  fixed port — same wire protocol and framing as the Windows JVM transport
  in [Design/Windows/02-transport-implementation.md](../Windows/02-transport-implementation.md),
  so any two implementations of `Transport`/`SyncChannel` interoperate
  without either side knowing what platform is on the other end.
- **Fallback (no shared LAN)**: Nearby Connections API (`Strategy.P2P_STAR`
  or `P2P_CLUSTER`), used when NSD discovery finds nothing after a short
  timeout — common mid-trip (hotel Wi-Fi with client isolation, no shared
  network at all). `SyncChannel` wraps Nearby's payload API so `core-sync`
  is unaware which transport is underneath.
- Both implementations live in an `android-app`-only source set; `core-sync`
  itself has zero Android imports.

## Permissions

| Permission | Why | Notes |
|---|---|---|
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | NSD requires multicast | Standard, no runtime prompt |
| `ACCESS_FINE_LOCATION` | Required by Nearby Connections pre-Android 12 | Runtime prompt, requested lazily only when NSD fails and Nearby fallback is attempted |
| `NEARBY_WIFI_DEVICES` | Nearby Connections on Android 13+ | Runtime prompt, same lazy trigger |
| `BLUETOOTH_ADVERTISE`/`BLUETOOTH_CONNECT` | Nearby Connections | Same lazy trigger |
| `POST_NOTIFICATIONS` | Budget alerts (Android 13+) | Requested once, on first budget entry, with rationale shown |
| `CAMERA` | QR scan for device pairing | Requested only when the user opens "Add a device" |

Requesting the Nearby/location permissions lazily (only on first fallback
attempt, not at app launch) avoids asking for location access up front for
an app that mostly only needs LAN discovery.
