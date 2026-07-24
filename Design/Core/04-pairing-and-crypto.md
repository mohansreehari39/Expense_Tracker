# Pairing & Crypto

- Household creator generates an X25519 keypair; the household symmetric
  key is a random 256-bit key, generated once, wrapped for each new device
  during pairing.
- QR payload (JSON, then base64): `{ "householdId", "wrappedKey" (sealed
  box to the new device's ephemeral pairing pubkey shown as a second,
  short-lived QR/manual code the new device displays), "bootstrapPeer" (IP
  hint if on same LAN, optional) }`. Two-step pairing (new device shows its
  pubkey first, existing device scans it, then shows the wrapped-key QR)
  avoids ever transmitting the raw household key unencrypted, even locally.
- All `SyncChannel` bytes are wrapped in AES-256-GCM using the household
  key, with a random nonce per message; this is independent of whatever
  transport-level security the LAN/Bluetooth link may or may not have.
- Implemented as a small `Crypto` object in `core-sync` (platform-agnostic;
  see [Implementation/Core/README.md](../../Implementation/Core/README.md)
  for the concrete library choice).

## Where each side of pairing happens

- **Generation side** (showing a QR to invite a new device) can happen on
  either app — see [Design/Windows/04-desktop-ui-and-web-view.md](../Windows/04-desktop-ui-and-web-view.md)
  and [Design/Android/03-navigation-and-screens.md](../Android/03-navigation-and-screens.md).
- **Scanning side** (reading a QR with a camera) is Android-only in
  practice — the Windows app has no camera, so a new phone is always the
  one scanning, whether the inviting device is another phone or the
  Windows app itself.
