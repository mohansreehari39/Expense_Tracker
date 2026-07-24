# Technology Stack Decisions

## Guiding factor

The single riskiest, most novel piece of this system is the **offline-first mesh sync engine** ([03-sync-protocol.md](03-sync-protocol.md)). Whatever else changes, that logic should be written once and shared by both the Android app and the Windows app — reimplementing merge/conflict logic twice in two languages is where subtle, hard-to-debug divergence bugs would come from. This one factor drives most of the choices below.

## Core decision: Kotlin Multiplatform (KMP) for shared logic

- `core-model`, `core-sync`, `core-domain` (see [04-android-app-architecture.md](04-android-app-architecture.md)) are KMP modules with no Android- or JVM-only dependencies in the shared code.
- **Android app**: Kotlin, Jetpack Compose (UI), Room (local persistence), WorkManager (background sync scheduling), NSD + Nearby Connections (transport).
- **Windows app**: Kotlin/JVM, running the *same* `core-sync`/`core-model`/`core-domain` binaries. Persistence via SQLite (JDBC) or embedded Postgres if richer analytics queries warrant it later — SQLite is enough for a household's data volume and keeps deployment to "copy a file."

## Windows app framework

- **Server/API**: Ktor (Kotlin) — lightweight, embeds easily in a desktop app, natural fit since everything else is Kotlin.
- **Dashboard UI**: recommend **Compose Multiplatform Desktop** for the main window (native feel, same UI paradigm/skills as the Android Compose UI, some composables potentially shareable) *plus* Ktor serving a minimal read-only web view of the same API for convenience (checking analytics from a phone browser without needing the phone app open). The desktop app is primary; the web view is a bonus, not a second UI to maintain in parallel — it can literally be a thin HTML page hitting the same REST endpoints.
- **Background/tray behavior**: standard Windows tray icon via a small JNA/AWT SystemTray integration, start-on-login optional setting.

## Transport for sync

- **LAN**: plain TCP/WebSocket, discovered via NSD (Android) — trivial to implement identically on the JVM side (Java has NSD-equivalent via JmDNS, or simply a fixed port + broadcast).
- **No shared LAN (phone-to-phone)**: Android Nearby Connections API. This is Android-specific, sitting behind the `Transport` interface in `core-sync` so it doesn't leak into shared code.
- **Encryption**: household pre-shared symmetric key (from QR pairing) wrapping all sync payloads; no PKI/cloud auth needed given the trust model (see [03-sync-protocol.md](03-sync-protocol.md#pairing--trust)).

## Persistence

- **Android**: Room, one set of tables for the append-only operation log, one set of read-optimized materialized tables (rebuilt/updated as operations are applied). SQLCipher optional if at-rest encryption on the phone is wanted later.
- **Windows**: SQLite for the same two-tier schema, via the same KMP data-access code path (e.g., SQLDelight, which supports both Android/Room-equivalent and JVM targets from shared `.sq` schema files — recommended over Room specifically because Room is Android-only and would break the "one shared implementation" goal for the log/materialization layer too).

  *(Adjustment to note: since SQLDelight covers both platforms, prefer it over Room for the log/materialized tables so persistence code is also shared, not just the sync/domain logic above it.)*

## Summary table

| Concern | Choice | Shared across platforms? |
|---|---|---|
| Data model, HLC | Kotlin (KMP) | Yes |
| Sync protocol/merge logic | Kotlin (KMP) | Yes |
| Domain/use-case validation | Kotlin (KMP) | Yes |
| Local DB schema + queries | SQLDelight (KMP) | Yes |
| Android UI | Jetpack Compose | Android only |
| Windows UI | Compose Multiplatform Desktop | Windows only (paradigm shared with Android) |
| Windows API | Ktor | Windows only |
| LAN transport | NSD/sockets (JmDNS equiv. on JVM) | Interface shared, impl per-platform |
| Phone-to-phone transport | Nearby Connections | Android only |
| Pairing/encryption | Pre-shared household key | Yes (logic shared) |

## What this buys, concretely

The hardest bug class in this whole system — two devices disagreeing about state after a sync — gets one implementation, one test suite (see future `Test/` folder), and one place to fix issues, instead of a Kotlin version and a C#/.NET version that can silently drift apart in behavior.
