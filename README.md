# Dock remote

A remote control for the drone-in-a-box dock, built with Compose Multiplatform.
One shared UI runs on **Android, iOS, desktop (macOS/Windows/Linux) and the
web**. It talks to the dock's own webserver
(`firmware/lift_motor/components/webserver` in drone-in-a-box), the same API
the board's built-in page uses:

| Endpoint | Used for |
| --- | --- |
| `GET /api/status` | polled every 400 ms. **This poll is the dead-man switch.** |
| `POST /api/cmd?c=X` | Home `h`, Top `t`, Step `+`/`-`, Extend `e`, Retract `r`, STOP `s`, speed `5 7 9 0`, decay `d` |
| `GET /api/net` | where the dock is on the network |
| `GET /api/scan`, `POST /api/wifi` | find and join a hotspot, from Settings |
| `GET /api/pair`, `GET /api/nonce` | the device key and signing, for when the dock's auth check is back on |

## Screens

* **Control**: the lift gauge, Position (Home, Step, Top), Speed, Activity, and
  Retract / STOP / Extend pinned to the bottom on phones.
* **Settings** (gear, top right): connection details and Disconnect, Dock Wi-Fi
  (scan and join), motor readings with the decay toggle, and Appearance
  (System, Light or Dark, remembered between launches). STOP stays pinned here
  too, and the dead-man poll keeps running while Settings is open.

## Layout

| Module | What it holds |
| --- | --- |
| [`wire`](./wire) | canonical form, HMAC, BLE framing. No dependencies; pinned by shared test vectors |
| [`client`](./client) | `DockClient`: HTTP to the dock, with a Ktor engine per platform |
| [`shared`](./shared) | the app: `DockSession` (polling, commands, scan, Wi-Fi join) and the Compose screens |
| [`androidApp`](./androidApp), [`desktopApp`](./desktopApp), [`webApp`](./webApp), [`iosApp`](./iosApp) | thin entry points that host `App()` |

`iosApp/DockApp` is the earlier SwiftUI diagnostic screen over the `DockKit`
framework. It is kept, but `iosApp/iosApp` is the app that matches the others.

## Running

Join the dock's Wi-Fi (`drone-in-a-box`), or a network the dock has joined.
The app connects to `192.168.4.1` by default; `dock.local` works when you
share a network with the dock.

```bash
./gradlew :androidApp:installDebug
```

```bash
./gradlew :desktopApp:run
```

```bash
DOCK_URL=http://192.168.4.1 ./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme.

### Without the board

`tools/mock_dock.py` copies the firmware's API, its timings and its dead-man,
using only the Python standard library:

```bash
python3 tools/mock_dock.py
```

Then point the app at it: `localhost:8000` from desktop or the iOS simulator,
`10.0.2.2:8000` from the Android emulator. For the browser, run the web app
with `DOCK_URL=http://localhost:8000`.

## Platform notes

* **Web.** The dock sends no CORS headers, so a browser can only read its
  answers from the same origin. The dev server proxies `/api` to `DOCK_URL`
  (see `webApp/webpack.config.d/dock-proxy.js`), and the app defaults to the
  page's own origin. To use the web app against a dock directly, serve the
  built bundle from the dock, or add CORS to the firmware.
* **Android 17.** The app targets SDK 37, so it needs `ACCESS_LOCAL_NETWORK`
  to reach any LAN address. The app asks for it at launch. Without it, every
  poll times out silently.
* **iOS.** `NSAllowsLocalNetworking` lets plain HTTP through ATS. iOS asks
  for local-network access the first time the app connects.
* **Backgrounding stops the poll**, and so, by design, the dock stops a
  running actuator about 1.5 s later.

## Tests

```bash
./gradlew :wire:allTests :client:jvmTest :shared:jvmTest
```
