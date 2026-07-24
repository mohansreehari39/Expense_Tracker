# Tray, Lifecycle, Config & Storage

## System tray / lifecycle

- Tray icon (Java AWT `SystemTray`) with menu: "Open Dashboard", "Sync Now",
  "Start on login" toggle, "Quit".
- On start: load config (port, household key path), start Ktor, start
  `core-sync` advertising/discovery, open the main window (or stay
  minimized to tray if launched via start-on-login).
- On quit: graceful shutdown — finish any in-flight sync session before
  closing sockets, so a quit mid-sync doesn't leave a peer's frontier
  update half-applied (sessions are already resumable per
  [Design/Core/03-sync-protocol.md](../Core/03-sync-protocol.md#session-state-machine),
  but a clean stop is nicer than relying on that).

## Config & storage

- Config file: `%APPDATA%/ExpenseTracker/config.json` (port, household id,
  start-on-login flag, nearing threshold overrides).
- Household key: `%APPDATA%/ExpenseTracker/household.key` (wrapped, not
  plaintext — see [Design/Core/04-pairing-and-crypto.md](../Core/04-pairing-and-crypto.md)).
- DB file: `%APPDATA%/ExpenseTracker/data.db` (SQLDelight/SQLite, single
  file, trivially backed up by copying it while the app is stopped).
