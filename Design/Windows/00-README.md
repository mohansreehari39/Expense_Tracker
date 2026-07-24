# Windows App — Design

Design for the desktop server/dashboard app described in
[Arch/05-windows-server-architecture.md](../../Arch/05-windows-server-architecture.md).
Built on the `core-model` / `core-sync` / `core-domain` modules from
[Design/Core](../Core/00-README.md); this folder only covers what's
specific to the Windows app. Source code for this subsystem lives in
[`Implementation/Windows/`](../../Implementation/Windows/README.md).

Read in this order:

1. [01-component-layout.md](01-component-layout.md) — how the pieces fit
   together
2. [02-transport-implementation.md](02-transport-implementation.md) — the
   JVM `Transport` implementation (JmDNS + sockets)
3. [03-rest-api.md](03-rest-api.md) — REST/WebSocket API surface
4. [04-desktop-ui-and-web-view.md](04-desktop-ui-and-web-view.md) — Compose
   Desktop screens and the thin web dashboard
5. [05-tray-lifecycle-and-config.md](05-tray-lifecycle-and-config.md) —
   system tray, startup/shutdown, config and storage locations
