# Android App — Design

Design for the phone client described in
[Arch/04-android-app-architecture.md](../../Arch/04-android-app-architecture.md).
Built on `core-model` / `core-sync` / `core-domain` from
[Design/Core](../Core/00-README.md); this folder only covers what's
specific to Android. Source code for this subsystem lives in
[`Implementation/Android/`](../../Implementation/Android/README.md).

Note: this subsystem is designed and built third, after the shared core
and the Windows app, per the sequencing decision to validate the sync
protocol on desktop first (faster iteration, easy multi-peer simulation)
before building the mobile-specific transport and UI.

Read in this order:

1. [01-component-layout.md](01-component-layout.md) — how the pieces fit
   together
2. [02-transport-and-permissions.md](02-transport-and-permissions.md) —
   NSD/Nearby `Transport` implementation and the Android permissions it
   requires
3. [03-navigation-and-screens.md](03-navigation-and-screens.md) — screen
   map and what each screen does
4. [04-viewmodel-and-background-sync.md](04-viewmodel-and-background-sync.md)
   — ViewModel pattern and background sync scheduling
5. [05-notifications.md](05-notifications.md) — budget alert notifications
