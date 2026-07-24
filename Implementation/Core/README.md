# Core

Source for the shared Kotlin Multiplatform modules used by both the
Windows and Android apps: `model`, `sync`, `domain`.

Design: [`Design/Core/`](../../Design/Core/00-README.md).

A Gradle multiplatform project, buildable standalone from this directory:

```
Implementation/Core/
  model/    # entities, Operation, Hlc — no I/O, no platform deps
  sync/     # Transport/OperationStore interfaces, session state machine,
            # conflict resolution, AES-GCM crypto, QR pairing payloads
  domain/   # use cases: budget derivation/alerting, splits, settlement
```

Run the test suite:

```
./gradlew allTests
```

## Status

Only a `jvm()` target is enabled so far (used by the Windows app). An
`android()` target will be added to each module's `build.gradle.kts` once
`Implementation/Android` starts consuming these modules — the code itself
is written to be platform-agnostic in `commonMain`/`commonTest` already,
so that should be additive, not a rewrite.

`sync`'s `Crypto` is `expect`/`actual`; only the JVM `actual` (via
`javax.crypto`) exists yet, for the same reason.

`Pairing`'s QR key-wrapping is a documented simplification (see the
kdoc on `Pairing.kt`): it derives an AES key directly from the scanned
pubkey bytes rather than performing a real X25519 ECDH exchange. Replace
before this is used by real devices — see
[Design/Core/04-pairing-and-crypto.md](../../Design/Core/04-pairing-and-crypto.md).
