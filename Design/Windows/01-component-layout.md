# Component Layout

```mermaid
flowchart TB
    Tray[System tray controller]
    Ktor[Ktor server: REST + WebSocket]
    Sync[core-sync w/ JVM Transport impl]
    DB[(SQLDelight, JVM driver)]
    Analytics[Analytics queries — core-domain]
    Desktop[Compose Multiplatform Desktop UI]
    WebView[Thin HTML/JS view, same REST API]

    Tray --> Ktor
    Tray --> Sync
    Ktor --> DB
    Ktor --> Analytics
    Analytics --> DB
    Sync --> DB
    Desktop --> Ktor
    WebView --> Ktor
```

The Windows app is a peer in the sync mesh (via `core-sync`) plus a REST/
WebSocket API layer that both the native Compose UI and the thin web view
consume identically — neither UI talks to the database directly, which
keeps the two front ends genuinely interchangeable and means all
sync-awareness lives in one place (the API layer and below), not in either
UI.
