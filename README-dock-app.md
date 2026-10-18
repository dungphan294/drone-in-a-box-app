# Dock control app (KMP)

Kotlin Multiplatform client for the drone-in-a-box dock controller. The shared
module carries the parts that must not drift from the firmware: the canonical
form, the signature, the nonce handling and the BLE framing.

> **Not compiled.** This module was written without a Gradle toolchain
> available, so it has never been built or run. Treat it as a reviewed draft:
> open it in Android Studio, let the sync fix up plugin and dependency
> versions, then run `commonTest` first — those tests are the fastest way to
> find anything that did not survive transcription.

## Why the crypto is in `commonMain`

`DockCrypto` implements SHA-256 and HMAC-SHA256 in pure Kotlin rather than
delegating to `javax.crypto` and CryptoKit through `expect`/`actual`.

That costs ~120 lines and buys something worth more: **the entire signing path
runs in `commonTest` on the JVM**, with no device, no simulator and no dock.
It is the same reasoning that keeps the firmware's `auth` component free of
ESP-IDF headers — put the logic that can be got wrong by reasoning somewhere a
test can reach it.

## Shared test vectors

`DockCryptoTest` asserts the **same vectors** as the firmware's `test_auth.c`
and `tools/dock_cli.py`:

| Vector | Where it also appears |
| --- | --- |
| `4:POST8:/api/cmd3:c=e1:7` | `test_canonical_is_length_prefixed`, `dock_cli.canonical()` |
| `3:BLE14:/ble/provision10:net\0secret1:1` | `test_blob_covers_bytes_after_a_nul`, `dock_cli.canonical_blob()` |
| 41-byte BLE frame (8 + 32 + 1) | `BLE_WRITE_HEADER` in `ble_link.c`, `dock_cli.ble_write()` |

Three implementations of one wire format, pinned by one set of vectors. If any
drifts, its own tests fail — rather than the mismatch surfacing as a mysterious
401 on the bench, which is exactly how the `%2B` bug behaved.

## Getting the device key

The key is generated on the dock and never leaves except through the pairing
window:

1. Press `P` on the dock's serial console — the window opens for 60 s.
2. The app connects over BLE, bonds, and reads the pairing characteristic.
3. The window closes on that first successful read.
4. Store the key in the platform keystore — **never** in `SharedPreferences`
   or `UserDefaults`.

`K` on the serial console prints the same key for bench testing; the pairing
window is the path the product uses.

## Platform work still to do

`DockBle` is an `expect class` with no `actual` yet. Beyond implementing it:

| Platform | Requirement | Why |
| --- | --- | --- |
| Android | network security config allowing cleartext to the dock | plain HTTP is blocked by default |
| Android | `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | runtime permissions since Android 12 |
| Android | `WifiNetworkSpecifier` | join the dock's AP from inside the app |
| Android | `ConnectivityManager.bindProcessToNetwork()` | keep cellular alive for other apps |
| iOS | `NSAllowsLocalNetworking` | ATS blocks plain HTTP |
| iOS | `NSLocalNetworkUsageDescription` | iOS 14+ requires consent for local network access |
| iOS | `NSBluetoothAlwaysUsageDescription` | required to use CoreBluetooth |
| iOS | `NEHotspotConfiguration` | join the dock's AP from inside the app |

These are platform policy, not bugs. They are the same class of obstacle as the
Safari HTTPS-Only error that blocked the browser UI.

## Keeping the heartbeat

`DockClient.stream()` polls every 400 ms, and **that poll is the dead-man
switch**. If it stops while the motor is running, the dock stops the actuator
after ~1.5 s. Do not pause it to save battery while a move is in progress, and
do not treat a dropped poll as cosmetic.
