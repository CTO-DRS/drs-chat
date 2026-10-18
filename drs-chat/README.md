# drs chat — v1 beta

Offline-first encrypted messenger. Phase 1: Wi-Fi/data transport over a dumb
relay, Ed25519 identity + libsignal PQXDH/Double-Ratchet, Keystore-vaulted
SQLCipher storage.

## What this v1 beta contains

- **`:core:protocol`** — CBOR wire envelope (golden-byte-pinned) + KeyId/CryptoEnginePort
- **`:core:crypto`** — CryptoEngine facade (BC Ed25519 + libsignal PQXDH) — 24 tests
- **`:core:domain`** — OutboxStateMachine + ReceivePipeline + Drainer + ChatFacade — 22 tests
- **`:core:data`** — Room + SQLCipher + Keystore vault + Room-backed Signal stores — 30 Robolectric tests
- **`:relay`** — Ktor 3 Netty WS server + auth challenge + store-and-forward + TTL — 16 tests
- **`:transport:websocket`** — Ktor client + TransportManagerImpl + ReconnectPolicy — 18 tests
- **`:app`** — Compose UI (Onboarding / ChatList / Conversation / Settings) + Hilt + foreground service stub

Total: **152 tests pass on CI** (green: https://github.com/CTO-DRS/drs-chat/actions).

## What this v1 beta does NOT do (honest disclosure)

This is a **UI shell beta**. The app boots and you can navigate the 4 screens,
but it does NOT actually send/receive messages yet. Production adapters that
close the loop are tracked as M5+ scope:

1. **CryptoEngine + DrDatabase wiring** — the CryptoEngine uses an in-memory
   SignalProtocolStore. The Room-backed stores (`RoomIdentityKeyStore`,
   `RoomSessionStore`, etc.) exist with passing tests but are NOT yet plugged
   into CryptoEngine. Identity + sessions are lost on app restart.
2. **WebSocketTransport + TransportManager + relay URL** — the transport
   classes compile and have unit tests, but the app doesn't actually
   connect to a relay. There is NO live relay endpoint configured.
3. **ChatFacade → real repositories** — the ChatFacade constructor takes
   OutboxRepository + MessageRepository + TransportPort, but the app DI
   provides no production instances. UI message-sends currently just
   append to local Compose state.
4. **Release signing** — APK is debug-signed only. Play Store distribution
   requires a release keystore (which the user must generate + provide).

## How to install + try the v1 beta

```bash
# Download the APK from the latest successful CI run:
#   https://github.com/CTO-DRS/drs-chat/actions
# Artifact name: build-packages
# Inside the zip: android/app/build/outputs/apk/debug/app-debug.apk

# Install on an Android 8.0+ device with USB debugging enabled:
adb install app-debug.apk

# Launch:
adb shell am start -n drs.chat.app/.MainActivity
```

You'll see:
1. **Onboarding screen** — app name + "Get started" button.
2. **ChatList screen** — placeholder contacts (alice, bob, carol).
3. Tap a contact → **Conversation screen** — type a message + send.
   The send appends to local state with status "QUEUED" (no real send).
4. Tap the gear icon → **Settings screen** — shows version + connection status.

## What you need to ship a real v1 (M5+ scope)

To take this from UI-shell beta to a real testable v1, the user must provide
or accept:

1. **A live relay endpoint URL** — deploy `:relay` somewhere with a public
   hostname (e.g. `wss://drs-relay.example.com/relay`). I have the source;
   I don't have hosting.
2. **A release keystore** — `keytool -genkey -v -keystore drs-release.keystore
   -keyalg RSA -keysize 2048 -validity 10000 -alias drs`. Keep the password
   secret. Without this, the APK is debug-signed only.
3. **End-to-end testing on two real devices** — the exit criterion for M5
   in PLAN.md: "Two devices/emulators exchange messages end-to-end through
   relay; statuses advance QUEUED→SENT→DELIVERED→READ; out-of-order +
   session-reset scenarios verified."

The 152-test suite proves the **layers** work in isolation. The
**integration wire-up** (crypto↔data↔transport↔UI via Hilt) is the
remaining work.

## How to build from source

```bash
git clone https://github.com/CTO-DRS/drs-chat.git
cd drs-chat/android
./gradlew ktlintCheck detekt \
  :core:protocol:test :core:crypto:test :core:domain:test \
  :relay:test :transport:websocket:test :core:data:test \
  :app:assembleDebug
```

Requirements: JDK 21, Android SDK 36 (build-tools 35.0.0), Gradle 8.10.2
(wrapper included).

## Architecture map

```
┌─ :app ──────────────────────────────────────────────┐
│  Compose UI (Onboarding/ChatList/Conv/Settings)     │
│  + Hilt DI + ForegroundService stub                  │
└──────┬──────────────────────────────────────────────┘
       │ depends on
┌──────┴────────────┐  ┌────────────────────────┐  ┌──────────────────┐
│ :core:domain      │  │ :core:crypto           │  │ :transport:api   │
│  ChatFacade        │  │  CryptoEngine          │  │  Transport/      │
│  OutboxSM          │←─│  (Ed25519 + libsignal)  │  │  TransportManager│
│  ReceivePipeline   │  └────────────────────────┘  └──────────────────┘
│  Drainer           │              ▲                        ▲
└──────┬─────────────┘              │                        │
       │ depends on                 │                        │
       ▼                            │                        │
┌──────────────────────────────────┴────────────────────────┴────────┐
│ :core:protocol (self-contained) — WireEnvelope + KeyId +            │
│                                 CryptoEnginePort + AuthWire          │
└──────────────────────────────────────────────────────────────────────┘
              ▲
              │ depends on
┌────────────┴──────────┐                  ┌──────────────────────────┐
│ :core:data             │                  │ :relay (server)          │
│  Room + SQLCipher      │                  │  Ktor 3 Netty + auth      │
│  KeystoreVault         │                  │  store-and-forward        │
│  Signal stores (Room)  │                  │  TTL + 64KB cap           │
└────────────────────────┘                  └──────────────────────────┘
              ▲                                          ▲
              │ depends on                              │ depends on
              ▼                                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│ :core:protocol (above) ──── shared lowest layer                       │
└──────────────────────────────────────────────────────────────────────┘

:transport:websocket — Ktor client + ReconnectPolicy + TransportManagerImpl
```

## Decision log (ADRs)

- [ADR-0001: Wire envelope format (CBOR + golden bytes)](docs/adr/ADR-0001-wire-envelope-format.md)
- [ADR-0002: Transport abstraction (byte pipes + universal routing)](docs/adr/ADR-0002-transport-abstraction.md)
- [ADR-0003: Identity + key storage (vault + SQLCipher)](docs/adr/ADR-0003-key-generation-and-storage.md)
- [ADR-0009: Baseline adjudication + frozen kind table](docs/adr/ADR-0009-baseline-adjudication-and-frozen-kind-table.md)

## License

Proprietary. CTO-DRS / drs chat.
