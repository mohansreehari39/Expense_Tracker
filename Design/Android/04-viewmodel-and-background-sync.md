# ViewModel Pattern & Background Sync

## ViewModel pattern

One `ViewModel` per screen, exposing a single `StateFlow<ScreenState>`.
`ScreenState` is a sealed class (`Loading`, `Content`, `Error`) so Compose
UI is a straightforward `when` over state, no partial/nullable field
soup. ViewModels call `core-domain` use cases directly (constructor
injected via Hilt); no business logic lives in the ViewModel itself beyond
mapping domain results to display strings/colors.

## Background sync integration

- `NsdManager.discoverServices` callback → on peer found, launch a sync
  session immediately via `core-sync`, scoped to a foreground-safe
  coroutine scope tied to a lightweight foreground service only while a
  session is actively transferring (not held continuously).
- `WorkManager` periodic unique work (~every 15–30 minutes) as the fallback
  net described in [Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md#background-sync-triggers),
  attempting NSD discovery then Nearby if nothing found.
- Manual "Sync now" (pull-to-refresh on Home) runs the same code path
  synchronously with a visible spinner/result toast.
