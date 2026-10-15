# drs chat — Phase 1 Plan (Internet-Only Transport)

Status: **DRAFT — awaiting approval (Milestone 1 gate)**
Scope fence: Phase 1 delivers internet-only transport carrying the same encrypted envelope that all future transports (BLE mesh, Wi-Fi Direct, LoRa/Meshtastic, satellite NTN, DTN store-carry-forward) will use. Nothing from the future phases is implemented here — only the abstraction seams required so they can plug in with zero domain changes.

Companion docs:

* [ARCHITECTURE.md](./ARCHITECTURE.md) — module tree, dependency rules, contracts, data flow
* [ADR index](./adr/README.md) — decision records (ADR-0001..0003 drafted at M1)

---

## 1. Mission fence (non-goals restated)

Phase 1 explicitly excludes: BLE / Wi-Fi Direct / LoRa / satellite / DTN logic (abstraction only), group chat, attachments, media, voice, calls, phone/email registration, FCM push, server admin panel, cloud key backup. Any idea not in the Phase 1 spec is rejected on sight; ambiguities are resolved with the simplest secure option and recorded in an ADR.

## 2. Milestones

| # | Milestone | Deliverables | Exit criteria |
|---|-----------|--------------|---------------|
| M1 | Planning (this output) | Milestone plan, module tree, ADR drafts (0001–0003) | User approval — **no code before approval** |
| M2 | Scaffold + protocol + crypto | Gradle version catalogs, all modules compiling, `:core:protocol` (envelope codec, inner payload, fingerprint/safety-number, wordlist), `:core:crypto` (CryptoEngine facade: Ed25519 identity, X25519, prekeys, libsignal sessions, Keystore vault), ktlint+detekt wired, tests | Envelope round-trip + golden-bytes + tamper/version tests green; Alice↔Bob session flow green (JVM sim + instrumented); fingerprint & safety-number derivation tests green; `build ktlintCheck detekt` clean |
| M3 | Storage + relay | Room+SQLCipher schema (messages, contacts, sessions, outbox), repositories, outbox state machine (QUEUED/SENT/DELIVERED/READ + retry/backoff), Ktor WS relay (nonce auth, per-recipient store-and-forward, 7-day TTL purge, 64 KB cap) | Outbox state machine (pure) unit tests green; DAO tests green; relay tests green (auth ok/fail, forward, TTL purge, oversize reject, multi-client routing) |
| M4 | UI + end-to-end | Onboarding (keys + fingerprint + QR), contact add via QR/share-link, chat list, 1:1 chat with ticks, safety-number verification screen, settings (fingerprint, delete session), connection indicator, WebSocketTransport + TransportManager wired, foreground service, README run guide | Two devices/emulators exchange messages end-to-end through relay; statuses advance QUEUED→SENT→DELIVERED→READ; out-of-order + session-reset scenarios verified; README verified by fresh clone |

After each milestone: run builds/tests, report results, list assumptions.

## 3. Milestone 2 breakdown (scaffold + protocol + crypto)

1. Repo scaffold: `/android` (AGP + version catalog) and `/server` (JVM build) — two Gradle builds, rationale in §6/D-1.
2. `gradle/libs.versions.toml` with pinned versions (§7). ktlint + detekt configured once, inherited by all modules.
3. `:core:protocol` (pure Kotlin): `WireEnvelope` CBOR codec per ADR-0001, `InnerMessage`/`InnerReceipt` payload structs, fingerprint + keyId derivation, 60-digit safety number, 256-word-list encoder, size guards.
4. `:core:crypto` (Android lib): `CryptoEngine` facade implementing the domain port — identity generation (Ed25519 via BouncyCastle + X25519 via libsignal), prekey pool, X3DH session build, encrypt/decrypt, envelope signing/verify, Keystore AES-GCM vault (StrongBox fallback) per ADR-0003, libsignal store bridging via domain ports.
5. Tests: envelope round-trip/golden/tamper/version (JVM), session-flow scenarios in `:testing:signal-sim` (JVM, libsignal-java) + instrumented duplicates (androidTest), vault wrap/unwrap (instrumented).

## 4. Milestone 3 breakdown (storage + relay)

1. `:core:data`: Room entities/DAOs — `contacts`, `messages`, `outbox`, `signal_records` (session/prekey/identity blobs), SQLCipher `SupportOpenHelperFactory` keyed by vault-wrapped passphrase.
2. Outbox state machine as **pure Kotlin** (no Android deps) so it is unit-testable in the sandbox: QUEUED → IN_FLIGHT → SENT (transport ack) → terminal on receipts (DELIVERED/READ); failure → exponential backoff re-queue; poison → FAILED.
3. Relay (`/server`): Ktor 3 (Netty) WebSocket; CBOR control frames (auth challenge/response, send, ack); per-recipient queue persisted to SQLite (survives restart); fixed 7-day retention sweeper; 64 KB envelope cap; rate-limited auth. Server never parses envelopes — it verifies the auth signature and routes by `recipientKeyId` (16 bytes).
4. Tests: relay integration tests (ktor-server-test-host), state machine table-driven tests, DAO tests.

## 5. Milestone 4 breakdown (UI + end-to-end)

1. `:transport:websocket`: Ktor client (OkHttp engine) WebSocketTransport + TransportManagerImpl (Phase 1 home — see ARCHITECTURE §5), reconnect with exponential backoff (1 s → 60 s, jitter).
2. `:feature:chat`: Compose M3 screens — Onboarding, ChatList, Conversation, VerifySafetyNumber, Settings; single-Activity + Navigation Compose.
3. `:app`: Hilt wiring, MainActivity, `DataSync`-typed foreground service holding the WS alive, notification state (connected / queued).
4. End-to-end pass: onboarding two identities, QR pairing, message exchange, receipts, out-of-order delivery, session reset from Settings, kill/restore of relay (store-and-forward), 7-day TTL simulation (shortened TTL test profile).

## 6. Key decisions (summary — full rationale in ADRs)

| ID | Decision | Choice |
|----|----------|--------|
| D-1 | Monorepo build layout | Two Gradle builds: `/android` (8 modules) and `/server` (`:relay`). Server never parses envelopes, so no code sharing is needed; avoids AGP/JVM plugin mixing. Composite build only if sharing becomes necessary. *(planned ADR-0006)* |
| D-2 | Wire encoding | CBOR via kotlinx-serialization, schema-pinned determinism + golden-bytes tests. *(ADR-0001)* |
| D-3 | Envelope signature scope | Ed25519 over the CBOR re-encoding of all fields except `signature`. *(ADR-0001)* |
| D-4 | Identity model | Ed25519 signing identity = **the** identity (fingerprint, safety number, relay auth); separate X25519 identity pair for Signal Protocol X3DH; both bound in one fingerprint. *(ADR-0003)* |
| D-5 | Key storage | Keystore AES-256-GCM vault key (StrongBox when available) wraps SQLCipher passphrase; all protocol keys live in the encrypted DB. *(ADR-0003)* |
| D-6 | Transport contract | Transports are byte pipes; `recipientKeyId` is the universal routing token; TransportManager aggregates + prioritizes. *(ADR-0002)* |
| D-7 | Ed25519 provider | BouncyCastle `bcprov-jdk18on` uniformly across API 26–35, isolated inside `:core:crypto` (Keystore Ed25519 only exists on API 33+ and would split key models). *(ADR-0003)* |
| D-8 | libsignal | `org.signal:libsignal-android` (Rust lineage, `org.signal.libsignal.*` API). Exact version pinned at M2 scaffold after verifying minSdk/ABI matrix of current stable. |
| D-9 | QR scanning | ZXing core + CameraX (no Play Services dependency — matches the project's independence ethos); ML Kit rejected. |
| D-10 | Receipts | Envelope kinds: MESSAGE(0), DELIVERY_RECEIPT(1), READ_RECEIPT(2), SESSION_RESET(3); receipts encrypted like messages; correlation ids live inside the encrypted inner payload. *(ADR-0001)* |
| D-11 | Relay persistence | SQLite on the relay for the store-and-forward queue (survives restart); retention sweeper hourly. |
| D-12 | Foreground service | `foregroundServiceType="dataSync"` (API 34+ requirement), started after onboarding, sticky. |

Deviation note vs. the fixed module list: we add two modules the list implies but doesn't name — `:app` (composition root) and `:testing:signal-sim` (JVM-only session tests using `libsignal-java`; needed because `libsignal-android` AAR natives cannot load in host-JVM unit tests). No other module additions; if this is unwanted, drop `:testing:signal-sim` and rely on instrumented tests only.

## 7. Version pins (verify at M2 scaffold)

Kotlin 2.1.x · AGP 8.7.x · Gradle 8.10+ · JDK 17 (android) / 21 (server) · compose-bom latest stable · Material 3 · Navigation Compose · Hilt 2.5x · Room 2.6.x · SQLCipher `net.zetetic:sqlcipher-android` 4.6.x · libsignal-android latest stable (pin at M2) · bcprov-jdk18on 1.78+ · Ktor 3.x (client OkHttp, server Netty) · kotlinx-serialization 1.7.x (CBOR) · coroutines 1.9.x · zxing core 3.5.3 · CameraX 1.4.x · ktlint-gradle 12.x · detekt 1.23.x · JUnit5 + Turbine + ktor-server-test-host + coroutines-test. minSdk 26, target/compile = latest stable (35).

## 8. Testing strategy

* **JVM (runs in this sandbox):** envelope round-trip/golden/tamper/version, fingerprint/safety-number, outbox state machine, relay auth/forward/TTL/size-cap, TransportManager aggregation logic (fake transports).
* **JVM sim (`:testing:signal-sim`):** full Alice↔Bob X3DH + Double Ratchet scenarios on `libsignal-java` — same API surface as the Android artifact: basic flow, out-of-order (deliver 1,3,5,2,4), ratchet rotation, session reset/re-establish, prekey exhaustion fallback.
* **Instrumented (androidTest, needs emulator — user machine/CI):** identical session scenarios against `:core:crypto` on-device, Keystore vault wrap/unwrap incl. StrongBox fallback, SQLCipher open/migrate.
* **Never log plaintext:** enforced by convention + grep-style CI script that fails on logging of message models; CryptoEngine exposes no toString of plaintext.

## 9. Environment & verification reality

This sandbox has no Android SDK/emulator preinstalled. Verification plan per milestone:

* Pure-JVM modules (`:core:protocol`, `:core:domain`, `:testing:signal-sim`, `/server`) + their tests build and run **fully in-sandbox**.
* Android modules: attempt SDK install in-sandbox to at least `compileDebugKotlin` them; if network/size limits prevent it, M2 ships with exact `./gradlew` commands for your machine plus a proposed GitHub Actions workflow (optional — say the word and it's included).
* Instrumented/emulator-dependent checks (crypto on-device, UI E2E) are listed as a manual verification script in the README of M4.

## 10. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| libsignal-android API churn (Rust rewrite moved packages/versions) | Pin exact version at M2; isolate every libsignal call inside `CryptoEngine`; API-shape smoke test in CI |
| libsignal natives unavailable in JVM unit tests | `:testing:signal-sim` on libsignal-java + androidTest on-device (D-deviation note above) |
| CBOR determinism depends on library behavior | Golden-bytes tests lock encoding; any drift ⇒ fail build ⇒ bump `protocolVersion` |
| QR bundle size (prekey bundle ~1–2 KB) | Compact invite: identity keys + signed prekey + ≤10 one-time prekeys; fallback to signed-prekey-only X3DH; share-link carries full bundle |
| Keystore cannot hold Ed25519/X25519 | Vault-key design (ADR-0003); residual in-memory exposure documented and accepted for Phase 1 |
| Foreground service limits (API 34+) | dataSync type, runtime permission flow documented, WS heartbeats tuned to Doze |
| Relay abuse (queue flooding) | Auth-gated send (must complete signed challenge before SEND), 64 KB cap, per-recipient queue cap (128 messages, oldest dropped first) |

## 11. Assumptions log

1. One relay URL is baked into settings (allow user override in Settings later if desired — out of Phase 1 scope).
2. "DELIVERED" = relay successfully pushed the envelope to the recipient's live connection; "READ" = recipient app's read receipt. Relay transport-ack deletes from server queue (client dedups by inner message id on redelivery).
3. Identity keys are permanent in Phase 1; `senderKeyId`/`recipientKeyId` are stable fingerprint prefixes; key rotation is future work enabled by the versioned envelope.
4. No multi-device support in Phase 1 (one identity = one device).
5. Server trust model: TLS (wss) + client signs nonce; server identity pinning deferred (planned ADR-0004).
6. Clock skew tolerance ±300 s on TTL enforcement.
7. Text messages capped at 4,096 chars (inner payload), envelope capped at 64 KiB (spec).

## 12. Quality gates

`./gradlew build ktlintCheck detekt` green on both builds before any milestone is reported done. No crypto code outside `CryptoEngine`. No plaintext logging. All async via suspend/Flow; main thread never blocked. Every ambiguous choice lands in an ADR before merge.
