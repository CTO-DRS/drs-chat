# ADR-0002 — Transport Abstraction (byte pipes + universal routing token)

Status: **Proposed** (M1 gate) — auto-promoted to Accepted at M2 exit if tests green.
Date: 2026-10-15 · Deciders: Phase 1 engineering

## Context

Phase 1 has exactly one transport (WebSocket to a dumb relay), but the entire premise of drs chat is that BLE mesh, Wi-Fi Direct, LoRa/Meshtastic, satellite NTN, and DTN store-carry-forward arrive later. The architecture rules demand: a `TransportManager` interface in `:transport:api` with one implementation now; messages must never know which transport carried them; Ble/LoRa transports must plug in with zero domain changes.

The trap to avoid is a transport layer that leaks its physics upward: connection-orientation assumptions, address formats (IP:port, BLE MAC, LoRa node id), push-vs-pull semantics, or delivery guarantees.

## Decision

### 1. Transports are byte pipes

A transport's entire contract is: take opaque envelope bytes toward a `recipientKeyId`, and yield opaque envelope bytes that arrived. No transport may add meaning to the bytes it carries.

```kotlin
interface Transport {
    val id: TransportId
    val linkState: StateFlow<LinkState>          // DOWN / CONNECTING / UP
    val incoming: Flow<TransportMessage>         // raw envelope bytes + opaque transport metadata
    suspend fun start()
    suspend fun stop()
    suspend fun send(bytes: ByteArray): SendReceipt   // SENT / REJECTED(reason) — per attempt
}

interface TransportManager {
    val connectionState: StateFlow<ConnectionState>   // CONNECTED / QUEUED (aggregated for UI)
    val incomingEnvelopes: Flow<ByteArray>
    suspend fun send(envelopeBytes: ByteArray): SendResult
    suspend fun start(scope: CoroutineScope)
    suspend fun stop()
}
```

### 2. `recipientKeyId` is the universal routing token

Every transport addresses peers by the 16-byte `recipientKeyId` (fingerprint prefix of the Ed25519 identity key — ADR-0001). Rationale: it is stable across all future transports (no MAC churn, no IP dependence, works on LoRa where addresses barely exist), it is already inside every envelope, and it doubles as the local contact lookup. How a given transport *resolves* that token to a physical path is that transport's private concern:

* WebSocket (Phase 1): the relay maps keyId → authenticated connection.
* BLE later: advertise the keyId prefix in scan responses / GATT.
* LoRa/DTN later: keyId is the DTN destination EID suffix.

### 3. Selection policy lives in the manager, not the domain

`TransportManager` holds an ordered list of transports and a policy hook (Phase 1: `[WebSocket]`, priority = "only"). Later policies rank transports by cost/battery/latency (e.g., BLE first, then WS, LoRa only for small TTL-tolerant envelopes) without any change to domain code, because domain calls one method and reads one state Flow.

### 4. Reliability model is explicit and layered

Transports are **best-effort, at-most-once per attempt**. End-to-end reliability comes from layers above:

1. local outbox persistence (message survives process death),
2. relay store-and-forward with 7-day TTL (recipient-side durability),
3. DTN custody transfer (future phase, same envelope).

Therefore `SendReceipt.SENT` means "the transport accepted the bytes" (WS: frame flushed + server ack) — it is *not* a delivery guarantee; DELIVERED comes only from the peer's encrypted receipt.

### 5. Envelope stays transport-blind

No transport fields in `WireEnvelope`. Transport-specific telemetry (RSSI, hop count, carrier id) attaches as *out-of-band metadata* on the receive path (`TransportMessage.meta`), consumed only by logging/UX — never persisted inside the message row's protocol-relevant fields.

### 6. Module placement and the Phase 1 shortcut

* `:transport:api` — pure Kotlin interfaces and value types only.
* `:transport:websocket` — `WebSocketTransport` (Ktor client, OkHttp engine) **plus** `TransportManagerImpl`, because exactly one transport exists and a manager-only module would be empty ceremony. When transport #2 lands, `TransportManagerImpl` moves to a new `:transport:core` in a refactor that touches zero domain files (they reference only `:transport:api`).
* `:app` owns lifecycle: the foreground service starts/stops the manager; reconnect loop is exponential backoff 1 s → 60 s with jitter, inside the transport, hidden behind `linkState`.

### 7. Future plug-in contract (design check, not Phase 1 code)

A `BleTransport` must: implement `Transport`, derive `recipientKeyId` routing from advertisements, define its own MTU fragmentation of the *envelope byte stream* (transport framing wraps, never mutates, envelope bytes), and report `LinkState` honestly. If any future transport cannot meet this contract, that is an architecture-level ADR discussion — not a domain change.

## Alternatives considered

* **Transport-aware message objects** (envelope carries `transportHint`/`via` fields): rejected — couples domain to physics, pollutes signatures, and invites routing logic into clients.
* **Reactive transport registry with dynamic discovery now**: over-engineering for one transport; the ordered-list policy hook is enough and extensible.
* **Putting store-and-forward in the client-side TransportManager** (DTN-lite early): rejected — Phase 1 scope fence; the outbox already provides device-side persistence, and DTN custody is a later ADR.

## Consequences

* (+) Domain and UI are provably transport-agnostic (module dependency graph enforces it).
* (+) Adding BLE/LoRa is additive: new module implementing one interface + policy update.
* (−) The manager is slightly anemic in Phase 1 (manages one transport) — accepted as the price of a clean seam.
* (−) `SendReceipt.SENT` semantics can be misread as delivery by future contributors; the ADR and status ticks (QUEUED/SENT/DELIVERED/READ) keep the vocabulary unambiguous.

---

## Amendment — CryptoEnginePort + KeyId relocated to :core:protocol (M3 push)

**Status:** Accepted (owner decree, M3 push).
**Resolves:** circular dependency `:core:domain ↔ :core:protocol` introduced
when `:core:domain`'s receive pipeline + drainer needed `WireEnvelope`,
`EnvelopeFactory`, `InnerMessage`, etc. (M3).

### Issue

Before M3, `:core:protocol` declared `api(project(":core:domain"))` because
its `EnvelopeFactory.build(...)` took a `CryptoEnginePort` parameter, and
`CryptoEnginePort` + `KeyId`/`ContactId` lived in `:core:domain`. The M3
receive pipeline + drainer (in `:core:domain`) need `WireEnvelope`,
`EnvelopeFactory`, `InnerMessage` — i.e. `:core:domain` needs
`:core:protocol`. The two `api()` deps form a cycle that Gradle refuses to
schedule.

### Decision

Move the cross-cutting crypto port and the universal routing token OUT of
`:core:domain` INTO `:core:protocol`:

* `drs.chat.protocol.KeyId` (was `drs.chat.domain.model.KeyId`)
* `drs.chat.protocol.ContactId` typealias (was `drs.chat.domain.model.ContactId`)
* `drs.chat.protocol.CryptoEnginePort` (was `drs.chat.domain.port.CryptoEnginePort`)
* `drs.chat.protocol.toBadge()`, `KeyId.toHex()`, `KeyId.toShortHex()`

`:core:protocol` is now self-contained — no project dependencies.
`:core:domain` declares `api(project(":core:protocol"))` and imports
`KeyId`/`ContactId`/`CryptoEnginePort` from there.
`:core:crypto` already depended on `:core:protocol`; it now also gets the
port + key types from there (no new dep).

### What STAYS in :core:domain

Domain-specific types remain: `MessageId`, `TransportId`, `OutboxEntry`,
`OutboxState`, `Contact`, `MessageRecord`, `MessageStatus`, `Direction`,
`VerificationLevel`. These are referenced only inside `:core:domain` and
its consumers (the `:app` UI), not by `:core:protocol`. No need to move
them.

### Rationale

Placing `CryptoEnginePort` + `KeyId` in `:core:protocol` is defensible
because:

1. `:core:protocol` is the **lowest** non-trivial module (it owns the wire
   format). Both `:core:domain` and `:core:crypto` already depend on it.
2. `KeyId` is the universal routing token whose byte layout is pinned by
   `EnvelopeConstants.KEY_ID_BYTES` (a protocol constant). The type is
   semantically part of the wire contract.
3. `CryptoEnginePort` is the contract `EnvelopeFactory.build` calls into.
   Co-locating the port with the factory keeps the dependency graph
   strictly acyclic.

### Alternative considered (rejected)

A new `:core:common` module holding only the shared value types + ports.
This is cleaner architecturally but adds an extra module + Gradle config
for marginal benefit in Phase 1. The :core:protocol placement is the
pragmatic Phase 1 answer; if `:core:protocol` grows to absorb many ports
that aren't wire-format concerns, the split becomes worth the cost.

### Audit hook

`./gradlew :core:protocol:dependencies` shows zero project dependencies.
`./gradlew :core:domain:dependencies` shows `:core:protocol` (transitively
`:core:crypto` via `testImplementation(project(":core:crypto"))`). No
cycle.
