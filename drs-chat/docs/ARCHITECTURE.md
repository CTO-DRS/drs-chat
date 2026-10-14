# drs chat — Phase 1 Architecture

Status: DRAFT — awaiting approval (Milestone 1 gate). See [PLAN.md](./PLAN.md) for milestones and [ADR index](./adr/README.md) for decisions.

## 1. Module tree

```
drs-chat/
├── docs/                                  # PLAN, ARCHITECTURE, adr/
│
├── android/                               # Android Gradle build (AGP + version catalog)
│   ├── settings.gradle.kts
│   ├── gradle/libs.versions.toml
│   ├── app/                               # :app — MainActivity, Hilt wiring, navigation,
│   │                                      #   ForegroundService (dataSync), notification state
│   ├── core/
│   │   ├── domain/                        # :core:domain — PURE Kotlin: models, ports, use cases
│   │   ├── protocol/                      # :core:protocol — PURE Kotlin: WireEnvelope CBOR codec,
│   │   │                                  #   InnerMessage/InnerReceipt, keyId/fingerprint,
│   │   │                                  #   60-digit safety number, 256-word-list encoder
│   │   ├── crypto/                        # :core:crypto — Android lib: CryptoEngine facade
│   │   │                                  #   (libsignal + BouncyCastle Ed25519 + Keystore vault)
│   │   └── data/                          # :core:data — Room + SQLCipher, repository impls,
│   │                                      #   SignalRecordStore port impl (opaque blobs only)
│   ├── transport/
│   │   ├── api/                           # :transport:api — PURE Kotlin: Transport, TransportManager,
│   │   │                                  #   LinkState, SendReceipt (interfaces only)
│   │   └── websocket/                     # :transport:websocket — WebSocketTransport (Ktor/OkHttp)
│   │                                      #   + TransportManagerImpl (Phase 1 home, see §5)
│   ├── feature/
│   │   └── chat/                          # :feature:chat — Compose M3: onboarding, chat list,
│   │                                      #   conversation, verification, settings
│   └── testing/
│       └── signal-sim/                    # :testing:signal-sim — PURE Kotlin JVM tests of session
│                                          #   scenarios on libsignal-java (natives-free CI)
└── server/                                # JVM Gradle build (JDK 21)
    ├── settings.gradle.kts
    └── relay/                             # :relay — Ktor 3 Netty WS relay: nonce auth, per-recipient
                                           #   store-and-forward (SQLite), 7-day TTL sweeper, 64 KB cap
```

## 2. Dependency rules (enforced by module graph + detekt)

| Module | May depend on | Must never see |
|--------|---------------|----------------|
| `:core:domain` | nothing (pure Kotlin) | Android, Ktor, libsignal, Room, CBOR codec internals |
| `:core:protocol` | pure Kotlin + kotlinx-serialization | libsignal, Room, Android |
| `:core:crypto` | domain (ports), protocol | Room/DAOs (blobs via ports), transport |
| `:core:data` | domain, protocol (ids only) | libsignal, Ktor, transport |
| `:transport:api` | pure Kotlin | everything concrete |
| `:transport:websocket` | transport:api, domain (models) | crypto, data, UI |
| `:feature:chat` | domain, transport:api (state only) | crypto, data, protocol internals |
| `:app` | everything (composition root) | — |
| `:relay` (server) | Ktor, kotlinx-serialization, sqlite-jdbc | envelope contents (opaque bytes!), client crypto libs |

Iron rules:

1. **Messages never know the transport.** Domain and UI consume `TransportManager` from `:transport:api` only.
2. **All crypto lives in `CryptoEngine`.** No libsignal/BC import outside `:core:crypto` (detekt rule + package check).
3. **Envelope creation/parsing lives only in `:core:protocol`.**
4. **All async is suspend/Flow.** No blocking calls on the main thread; repositories and transports expose Flows.
5. Domain defines **ports** (`CryptoEngine`, `SignalRecordStore`, `DrRepository`…); adapters live in crypto/data/crypto-adjacent modules; Hilt binds them in `:app`.

## 3. Message lifecycle (compose → read)

```
User types text (Compose)
  └─ SendMessageUseCase (:core:domain)
       ├─ 1. InnerMessage{id, text, sentAt} → CBOR bytes (:core:protocol)
       ├─ 2. CryptoEngine.encrypt(contact) → payloadCiphertext  (Signal session: X3DH/Double Ratchet)
       ├─ 3. ProtocolFactory.buildEnvelope(...) → WireEnvelope    (signs header via CryptoEngine)
       ├─ 4. OutboxRepository.persist(status=QUEUED)              (Room, SQLCipher)
       └─ 5. TransportManager.send(envelopeBytes)                 (best-effort, fire-and-forget)

TransportManager (:transport:api contract, WS impl Phase 1)
  └─ WebSocketTransport → wss://relay  [control frame: SEND + binary envelope]

Relay (:relay) — sees opaque bytes only
  └─ verify auth (already done at challenge) → route by recipientKeyId (16 raw bytes)
       ├─ recipient online → push binary frame → delete from queue on socket ack
       └─ recipient offline → SQLite queue, TTL sweeper deletes after 7 days

Recipient
  └─ TransportManager.incoming → EnvelopePipeline (:core:domain use cases)
       ├─ parse + verify signature (:core:protocol + CryptoEngine.verify)
       ├─ TTL check (drop if expired, ±300 s skew)
       ├─ CryptoEngine.decrypt → InnerMessage → persist (Room) → UI Flow
       ├─ auto-send DELIVERY_RECEIPT (if MESSAGE)
       └─ dedup by inner message id (relay may redeliver on reconnect)

READ tick: peer UI opens chat → ReadMarkerUseCase → READ_RECEIPT envelope (same pipeline)
```

## 4. Key contracts (sketches pinned at M2/M3)

```kotlin
// :core:domain — ports
interface CryptoEngine {
    suspend fun generateIdentity(): Identity
    suspend fun identityFingerprint(): Fingerprint          // 8 words + raw
    suspend fun safetyNumber(with: ContactId): SafetyNumber // 12 groups × 5 digits
    suspend fun inviteBundle(maxOneTime: Int): InviteBundle // QR / share-link payload
    suspend fun consumeInviteBundle(bundle: InviteBundle): ContactId
    suspend fun encrypt(contact: ContactId, plaintext: ByteArray): ByteArray
    suspend fun decrypt(sender: ContactId, ciphertext: ByteArray): DecryptResult
    suspend fun signEnvelope(headerBytes: ByteArray): ByteArray
    suspend fun verifyEnvelopeSignature(senderKeyId: KeyId, headerBytes: ByteArray, sig: ByteArray): Boolean
    suspend fun resetSession(contact: ContactId)             // archive + SESSION_RESET notice
}

interface Transport {                                          // :transport:api
    val id: TransportId
    val linkState: StateFlow<LinkState>                        // DOWN / CONNECTING / UP
    val incoming: Flow<TransportMessage>                       // raw envelope bytes (+ opaque meta)
    suspend fun start(); suspend fun stop()
    suspend fun send(bytes: ByteArray): SendReceipt            // SENT / REJECTED(reason)
}

interface TransportManager {
    val connectionState: StateFlow<ConnectionState>            // CONNECTED / QUEUED
    val incomingEnvelopes: Flow<ByteArray>
    suspend fun send(envelopeBytes: ByteArray): SendResult
    suspend fun start(scope: CoroutineScope); suspend fun stop()
}

interface OutboxRepository {
    val pending: Flow<List<OutboxEntry>>
    suspend fun enqueue(envelope: ByteArray, to: KeyId): MessageId
    suspend fun markSent(id: MessageId); suspend fun markFailed(id: MessageId, err: SendError)
    suspend fun nextRetryBatch(now: Instant): List<OutboxEntry> // backoff-aware
}

// Outbox state machine (pure, in :core:domain for JVM testability)
// QUEUED → IN_FLIGHT → SENT ──(DELIVERY_RECEIPT)──> DELIVERED ──(READ_RECEIPT)──> READ
//    ▲         │                    any failure → QUEUED (backoff) ; attempts > N → FAILED
//    └─────────┘
```

Statuses surfaced in UI: QUEUED (clock), SENT (single tick), DELIVERED (double tick), READ (double tick filled). Failed messages show a retry affordance.

## 5. TransportManager placement (Phase 1 note)

`TransportManagerImpl` (aggregation + priority selection) ships inside `:transport:websocket` for Phase 1 because exactly one transport exists; the composition root binds `TransportManager → TransportManagerImpl(webSocketTransport)`. When transport #2 (BLE) arrives, the impl moves to a new `:transport:core` in a pure refactor — domain and UI never notice, because they only reference `:transport:api`. This is recorded in ADR-0002.

## 6. Relay wire surface (control plane — CBOR, separate from drs envelopes)

```
S→C  AuthChallenge { nonce: 32B, ts }
C→S  AuthResponse  { keyId: 16B, ed25519Pub: 32B, sig: Ed25519(nonce‖ts‖"drs-auth-v1") }
S→C  AuthOk {} | AuthFail { reason }
C→S  Send { ttlSeconds }  + binary frame = WireEnvelope bytes (≤ 64 KiB)
S→C  Envelope          + binary frame = WireEnvelope bytes
C→S  DeliveredAck { envelopeHash }   → server drops queued copy
C→S  Ping / S→C Pong                 → keepalive (4 min), reconnect w/ exp backoff on client
```

The server reads: keyId (routing), auth signature, ttlSeconds (retention hint, default 7 days). It never touches envelope fields — the 64 KB cap is a frame-size limit.

## 7. Threading & persistence notes

* Room + SQLCipher: single `SupportSQLiteOpenHelper.Factory` wired with the vault-unwrapped passphrase; DAOs are suspend/Flow; WAL enabled.
* Foreground service (`dataSync`) owns the `TransportManager` scope; UI observes `connectionState` via a shared singleton (Hilt).
* Process death: outbox rows survive; `TransportManager.start()` re-drains retries with backoff.
* Keys: generated once at onboarding; vault unwrap happens at process start; passphrase zeroed on background trim is best-effort (documented residual risk, ADR-0003).
