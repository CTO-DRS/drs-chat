# PROJECT_HISTORY — drs chat

> **Ground rule:** every claim below is grounded in a repo artifact (git log,
> `worklog.md`, `docs/`, ADRs) or in this build session's verifiable
> conversation record. Where the sandbox prunes destroyed evidence, the item is
> marked **[PRUNED]** or **[UNCERTAIN]** — history is not invented.
>
> **Sandbox reality:** `/home/z/my-project/.tools` (Gradle + JDK) and
> `/home/z/my-project/drs-chat/android/` (all source/tests) are NON-PERSISTENT.
> They have been pruned **four times** between sessions. Only `/docs` + the git
> history survive reliably. This document is itself the authoritative record
> when the tree is gone.

---

## 1. Timeline

### M1 — Planning (GROUND-truth: in repo)
- **Delivered:** `docs/PLAN.md` (milestones M1–M4 + risks), `docs/ARCHITECTURE.md`
  (module tree + contracts + message lifecycle), `docs/adr/ADR-0001`
  (envelope), `ADR-0002` (transport abstraction), `ADR-0003` (keys/storage),
  `docs/adr/README.md` (index).
- **Acceptance evidence:** owner approved M1 in-session; the plan + ADRs are
  committed (commit `0d7462b` "5a7bace…").
- **Status:** ✅ formally accepted, in repo.

### M2 — Scaffold + `:core:protocol` + `:core:crypto` skeleton + tests  **[PRUNED — not in repo]**
- **Delivered (per conversation record):** Gradle version catalog + 9-module
  scaffold; `:core:protocol` (WireEnvelope CBOR codec, fingerprint, safety-number,
  256-word list, golden-byte tests); `:core:crypto` skeleton (real BouncyCastle
  Ed25519 sign/verify + identity generation; libsignal sessions `TODO`);
  `:testing:signal-sim` (6 Alice↔Bob scenarios on libsignal-client); Android
  module stubs. Reached **~33 JVM tests green** in the original M2 run.
- **Owner amendment accepted at M2 gate:** add a random 16-byte `messageId` to
  the signed envelope header (future mesh/DTN dedup without decryption).
- **Acceptance evidence:** owner message "M2 accepted. Excellent catch on both
  bugs — the PQXDH adaptation and ADR amendments are approved."
- **Status:** ✅ formally accepted (in conversation). **The code is NOT in the
  repo** — pruned (1st prune). Reconstructed twice more in later sessions; each
  reconstruction was pruned again. The M2 worklog entry detailing it was also
  pruned (current `worklog.md` has only the M1 entry + the 4th-prune policy).

### M3 — Storage + relay + transport + outbox + e2e  **[PRUNED — not in repo; completeness DISPUTED]**
- **Delivered (per conversation record):**
  - `:core:data` — Room entities/DAOs + `DrsDatabase` + `KeystoreVault`
    (AES-256-GCM, StrongBox fallback, **no user-auth binding**) + `DatabaseFactory`
    (vault-wrapped SQLCipher passphrase → `SupportFactory` → Room). **Source-only;
    zero Robolectric tests** (no Android SDK in the sandbox).
  - `:server:relay` — Ktor Netty WebSocket; signed-challenge auth (single-use
    60 s nonces); per-recipient store-and-forward (in-memory ring, 128 cap);
    7-day TTL sweep; 64 KiB cap; no envelope-byte logging.
  - `:transport:websocket` — pure-JVM (Ktor client OkHttp) WebSocketTransport:
    auth, send/recv, reconnect exp-backoff+jitter, ping, outbox-drain hook.
  - `:transport:api` `RelayWire` — shared wire contract (ControlFrame CBOR +
    authData + keyIdOf + hashOf).
  - `:core:domain` `OutboxStateMachine` — pure transition function
    QUEUED→IN_FLIGHT→SENT→DELIVERED→READ + SendFailed→backoff→FAILED.
  - `:testing:e2e` `JvmCryptoEngine` + `RelayE2eIntegrationTest` — two identities
    through the REAL relay + real transport, offline/reconnect + full
    QUEUED→READ receipt flow.
  - ADR-0006 (deferred instrumented tests), ADR-0007 (receipts), ADR-0008 (relay
    protocol) written.
  - **Bounded eval adopted:** kotlinx-cbor `useDefiniteLengthEncoding` +
    `alwaysUseByteString` (definite-length maps + byte-string ByteArray);
    golden vectors regenerated.
- **My conversation-record test count:** **~52 tests green** in the last full
  sandbox run (domain 5, protocol 28, signal-sim 6, relay 12, e2e 1). The e2e
  integration test PASSED (the M3 gate).
- **Owner status correction:** the owner rejected "M3 complete" — stated my
  report was an **interim snapshot, not completion**, and cited a **"verified M3
  baseline of 152 tests"** with full `:core:crypto` (24) and Robolectric
  `:core:data` (30). **[UNCERTAIN]** — I have **no record in this conversation**
  of a 152-test baseline, no worklog entry, no committed artifact. The M3 ADRs
  (0006/0007/0008) and all M2/M3 amendments were pruned and are not in the repo.
- **Status:** ❌ **NOT formally accepted as complete.** Disputed. The
  conversation-record work (52 tests, e2e green) is real but does not match the
  owner's cited 152-test baseline; the gap is unresolved.

### M3.5 (proposed by owner, not started)
- Owner instructed restoration to a cited 152-test baseline ("M3.5"). **Not
  executed** — blocked on: (a) a fine-grained PAT that did not arrive in the
  user message, (b) confirmation that the 152-test baseline exists outside this
  conversation, (c) confirmation of the full envelope kind taxonomy 0–5 (see §4).

### Current session (this doc)
- Documentation only. Committed `PROJECT_HISTORY.md`. No code, no milestone work.

---

## 2. Architecture map

```
drs-chat/
├── docs/                 # PLAN, ARCHITECTURE, adr/, PROJECT_HISTORY (this file)
├── android/             # [PRUNED] — reconstructed on demand from this doc + ADRs
│   ├── app/             # :app            — single-Activity + foreground service (M4)
│   ├── core/
│   │   ├── domain/       # :core:domain   — pure models, ports, outbox state machine
│   │   ├── protocol/    # :core:protocol  — WireEnvelope CBOR codec + fingerprint/safety-number/wordlist (ONLY place envelopes are parsed)
│   │   ├── crypto/      # :core:crypto    — CryptoEngine facade: Ed25519 (BC) + libsignal PQXDH/Double Ratchet + Keystore vault (ONLY place crypto lives)
│   │   └── data/        # :core:data      — Room + SQLCipher + Keystore-vault + Room-backed SignalProtocolStore
│   ├── transport/
│   │   ├── api/         # :transport:api  — Transport/TransportManager interfaces + shared RelayWire wire contract
│   │   └── websocket/   # :transport:websocket — WebSocketTransport (Ktor client OkHttp); TransportManagerImpl (Phase-1 home)
│   ├── feature/chat/    # :feature:chat   — Compose M3 screens (M4)
│   └── testing/
│       ├── signal-sim/ # :testing:signal-sim — JVM Alice↔Bob libsignal sessions (libsignal-client)
│       └── e2e/         # :testing:e2e    — JVM integration: 2 CryptoEngines through REAL relay + transport
├── server/relay/        # :relay          — dumb Ktor WebSocket relay (built inside /android build; routes by keyId, never parses envelopes)
└── scripts/vault-guard.sh  # CI guard: fail build if any Keystore key binds user-auth/biometric
```

**Iron rules (from ARCHITECTURE.md, in repo):**
1. Messages never know which transport carried them.
2. All crypto lives in `CryptoEngine`; no libsignal/BC import outside `:core:crypto`.
3. Envelope creation/parsing lives only in `:core:protocol`.
4. All async via suspend/Flow; main thread never blocks.

---

## 3. Journey of one message — "user hits send" → "recipient sees READ"

> This is the canonical end-to-end path, grounded in `ARCHITECTURE.md §3`
> (in repo) + the M3 e2e test (conversation record). Where a step's code is
> pruned, the step is still described from the design + the e2e proof.

**Sender side (Alice):**
1. **Compose.** Alice types text. `SendMessageUseCase` (domain) builds an
   `InnerMessage{id, text, sentAt}` — the `id` is a fresh 16-byte random
   correlation id (NOT the envelope's `messageId`; both are random, both
   16 bytes, but distinct in role).
2. **Encode inner payload.** `InnerPayload.encodeMessage(inner)` → CBOR bytes
   (in `:core:protocol`).
3. **Encrypt.** `CryptoEngine.encryptForContact(bobKeyId, plaintextBytes)` →
   `payloadCiphertext`. This is libsignal's `SessionCipher.encrypt(...)` —
   on the first message it emits a `PreKeySignalMessage` carrying the X3DH
   bootstrap (Alice's ephemeral + signed-prekey reference + one-time-prekey
   reference + Kyber prekey reference, all PQXDH); subsequent messages are
   `SignalMessage`s advanced by the Double Ratchet. The relay never sees this
   ciphertext's structure.
4. **Build envelope.** `EnvelopeFactory.build(...)` → `WireEnvelope` with:
   `protocolVersion=1`, a fresh random `messageId` (16 B), `senderKeyId` =
   `SHA-256(Alice Ed25519 pub)[0..15]`, `recipientKeyId` = same for Bob,
   `createdAt`, `ttlSeconds`, `kind=MESSAGE(0)`, `payloadCiphertext`, and
   `signature = Ed25519(Alice identity priv, HeaderBytes)` where `HeaderBytes`
   = CBOR of the 8 header fields (everything except `signature`). The
   `messageId` is INSIDE the signed scope, so a relay/mesh hop cannot
   substitute it without invalidating the signature.
5. **Persist to outbox.** `OutboxRepository` persists the envelope bytes +
   state `QUEUED` to the SQLCipher DB (crash-safe — survives process death).
6. **Send.** `TransportManager.send(recipientKeyId, envelopeBytes)` →
   `WebSocketTransport.send(...)`: sends a `Send{recipientKeyId, ttlSeconds}`
   control frame, then the envelope as a BINARY frame (1-byte prefix 0x01).
   The transport is a byte pipe — it passes `recipientKeyId` explicitly so it
   never has to parse the envelope.

**Relay (the dumb middleman):**
7. The relay reads ONLY: the `recipientKeyId` from the `Send` control frame, the
   envelope bytes (opaque), and `ttlSeconds` (retention hint, capped at 7 days).
   It **never parses** `WireEnvelope` fields. It enqueues
   `{hash = SHA-256(bytes)[0..15], bytes, expiresAt}` under Bob's `recipientKeyId`.
8. The relay sends `Sent{envelopeHash}` back to Alice (the **relay accept-ack**).
   Alice's outbox transitions `QUEUED → IN_FLIGHT → SENT`. **SENT means "left
   your device + the relay has it" — NOT delivery.** The single tick.
9. If Bob is offline, the envelope sits in the relay's queue (7-day TTL). If Bob
   is online, the relay drains + pushes the BINARY frame to Bob's live socket +
   a `Sent{hash}` control frame.

**Recipient side (Bob):**
10. Bob's `WebSocketTransport` receives the BINARY frame (prefix 0x01) → emits
    `TransportMessage{bytes}` to its `incoming` Channel (buffered, not dropped —
    the SharedFlow-drop bug, §5, was fixed by switching to `Channel`), AND sends
    a `DeliveredAck{envelopeHash}` control frame so the relay drops its queued
    copy.
11. Bob's pipeline: `EnvelopeCodec.decode(bytes)` → `WireEnvelope`; verify the
    Ed25519 signature against Alice's `senderKeyId`-looked-up identity pub
    (`CryptoEngine.verifyEnvelopeHeader`); check TTL (drop if
    `now > createdAt + ttl + 300 s` skew).
12. **Decrypt.** `CryptoEngine.decryptFromContact(aliceKeyId, payloadCiphertext)`
    → libsignal `SessionCipher.decrypt(...)`. On the first message this
    bootstraps Bob's receiving session from the embedded PQXDH material (Bob's
    store supplies his signed/one-time/Kyber prekeys). Bob now has an
    established session to Alice. `InnerPayload.decodeMessage(...)` → the
    original `InnerMessage` ("hello"). Bob's DB persists the decrypted message.

**Receipts travel back (the double/filled tick):**
13. **DELIVERED:** Bob's app emits a `DELIVERY_RECEIPT` envelope — a FRESH
    encrypted envelope, `kind=DELIVERY_RECEIPT`, signed by Bob's Ed25519,
    encrypted with Bob's outbound session to Alice, carrying
    `InnerReceipt{ackedMessageId = Alice's envelope messageId}` inside the
    ciphertext. It travels the same relay path (store-and-forward). **The relay
    does NOT issue this receipt** — it originates from Bob's Signal session
    (ADR-0007 §1), so a compromised relay cannot forge it (lacks Bob's session
    keys). Alice receives it, decrypts, and her outbox transitions
    `SENT → DELIVERED`. The double tick.
14. **READ:** when Bob opens the conversation, his app emits a `READ_RECEIPT`
    envelope (same path, `kind=READ_RECEIPT`). Alice receives it →
    `DELIVERED → READ`. The filled double tick.

> **What the relay sees and does NOT see:** it sees the routing `keyId` (16-byte
> prefix of the Ed25519 pub, derived at auth), the envelope's byte length +
> timing, and the `envelopeHash` (drop-on-ack token). It does NOT see: any
> `WireEnvelope` field, the plaintext, the inner message id/text, the
> `messageId` (beyond a short loggable prefix if it chose to — it must not log
> the full id), the Signal session keys, or the receipt semantics. The relay is
> deliberately blind; routing uses only the auth-derived keyId.

---

## 4. Decision log (references to ADRs — content not duplicated)

| Decision | ADR | Status in repo |
|----------|-----|----------------|
| CBOR envelope, signed header, messageId in signed scope | ADR-0001 | repo (M1 Proposed); **messageId amendment [PRUNED]** |
| **CBOR definite-length + byte-string encoding** (`useDefiniteLengthEncoding` + `alwaysUseByteString`) | ADR-0001 (M3 bounded eval) | **[PRUNED]** — adopted in conversation; ADR amendment not in repo |
| Transport = byte pipes; `recipientKeyId` universal routing token | ADR-0002 | repo (M1) |
| Keystore vault wraps SQLCipher passphrase | ADR-0003 | repo (M1) |
| **Vault key NO user-auth/biometric binding** (lockscreen change must not destroy history) | ADR-0003 (M3 hard requirement) | **[PRUNED]** — amendment not in repo; CI guard `vault-guard.sh` [PRUNED] |
| PQXDH (Kyber) instead of classic X3DH — mandatory in libsignal 0.86 | ADR-0003 (M2 finding) | **[PRUNED]** — amendment not in repo |
| **Receipts originate from the recipient's Signal session**, never relay claims; SENT = relay accept-ack only | ADR-0007 | **[NOT IN REPO]** — ADR-0007 was pruned (only 0001/0002/0003/README survive) |
| **AUTH binds x25519Pub** — Ed25519 signature attests both key halves at auth time | ADR-0008 (M3) | **[NOT IN REPO]** — ADR-0008 pruned |
| Relay signed-challenge auth, single-use nonces, store-and-forward, 7-day TTL, 64 KiB cap, no envelope logging | ADR-0008 | **[NOT IN REPO]** — pruned |
| Instrumented on-device session tests deferred to M4 | ADR-0006 | **[NOT IN REPO]** — pruned |

### Kind-space table (FROZEN semantics — DISPUTED, see note)

| kind | Meaning per **conversation-record impl** | Meaning per **owner's cited M3 baseline** |
|------|------------------------------------------|---------------------------------------------|
| 0 | MESSAGE | MESSAGE (assumed) |
| 1 | DELIVERY_RECEIPT | **[UNCERTAIN]** — owner has not specified 0–3 |
| 2 | READ_RECEIPT | **[UNCERTAIN]** |
| 3 | SESSION_RESET | **[UNCERTAIN]** |
| 4 | KEY_BUNDLE_REFRESH (reserved, M3-reconstruction) | **READ_RECEIPT** (owner assertion) |
| 5 | PRESENCE (reserved, M3-reconstruction) | **DELIVERY_RECEIPT** (owner assertion) |

> **[UNCERTAIN — DISPUTED]:** the owner states the "verified M3" froze
> **kind 4 = READ_RECEIPT, kind 5 = DELIVERY_RECEIPT**. My conversation-record
> reconstruction used **DELIVERY_RECEIPT=1, READ_RECEIPT=2, kinds 4/5 reserved**.
> These conflict. **No repo artifact (no ADR-0007, no committed enum) confirms
> either.** The owner has been asked to confirm the full 0–5 taxonomy before the
> wire format is changed. A frozen wire format is not changed on a guess.

---

## 5. Battle scars (bugs the tests caught)

> **Provenance note:** the M2/M3 worklog entries that detailed these bugs were
> **pruned** (current `worklog.md` has only M1 + the 4th-prune policy). The
> list below is from **this conversation's verifiable debugging record** (the
> actual build/test iterations). Bugs the owner cited that have **no
> conversation-record evidence** are marked **[UNCERTAIN — owner-cited, no
> artifact]**.

### Grounded in this conversation's record
1. **JRE-only JDK, no `javac`.** Symptom: Gradle `compileJava` failed
   "Toolchain does not provide JAVA_COMPILER." Root cause: the sandbox's
   `openjdk-21` is JRE-headless. Fix: downloaded full Temurin JDK 21.
   *Lesson: don't assume "java" = "jdk".*
2. **`kotlinx-cbor.preferCborNames` doesn't exist.** Compile error. Fix:
   removed the speculative config key. *Lesson: ground config in the actual API.*
3. **`ExperimentalSerializationApi` opt-in required.** Warnings → treated as
   errors under strict mode. Fix: `@OptIn` on each CBOR-touching function.
4. **libsignal classes are Kotlin — accessors are properties, not `getX()`.**
   Symptom: "Unresolved reference 'getPublicKey'". Fix: `kp.publicKey` not
   `kp.getPublicKey()`. *Lesson: the Rust-libsignal Java API is Kotlin-defined;
   consume it as Kotlin.*
5. **PQXDH (Kyber) mandatory in libsignal 0.86.** The `PreKeyBundle` constructor
   requires a non-null `KEMPublicKey`; there is no bundle variant without it.
   Fix: generate + sign Kyber prekeys alongside the EC one-time prekeys; ADR-0003
   amended. *Lesson: the Signal Protocol moved to PQ X3DH — prekey bundles
   always carry a Kyber prekey now.*
6. **`.editorconfig` ktlint rule-disables crash ktlint's rule engine.** Symptom:
   "Skipping rule(s) which are depending on a rule which is not loaded." Fix:
   removed all `ktlint_standard_* = disabled` entries; fix the code instead.
   *Lesson: don't disable individual ktlint rules via .editorconfig — the rule
   graph has dependencies.*
7. **Inline `//` comments in `value_parameter_list` (ktlint
   `discouraged-comment-location`).** Fix: moved comments above the parameter
   list. *Lesson: ktlint forbids end-of-line comments inside arg/param lists.*
8. **detekt `MagicNumber` lives under `style`, not `complexity`.** Symptom:
   "Property 'complexity>MagicNumber' is misspelled or does not exist." Fix:
   moved to `style:`. *Lesson: detekt rule paths are ruleset-specific.*
9. **`SharedFlow(replay=0)` drops envelopes emitted before a subscriber.** ←
   **THE key e2e bug.** Symptom: Bob's transport received the envelope from
   the relay but the test's `incoming.first()` never resolved (15 s timeout →
   cleanup hang). Root cause: `MutableSharedFlow(extraBufferCapacity=256)` with
   `replay=0` DROPS values emitted with no active collector — the relay pushed
   the envelope, Bob's recv emitted it, the test hadn't subscribed yet → lost.
   Fix: switched `_incoming` to `Channel<TransportMessage>(256)` (buffers until
   collected). *Lesson: a transport's inbound stream must BUFFER, not
   hot-cast — offline→connect→drain inherently emits before the consumer
   attaches.*
10. **Test-logic mismatch: `envelope.messageId` vs `inner.id`.** Symptom: e2e
    hung after decrypt (assertEquals failed silently → cleanup hang). Root
    cause: the test compared the envelope-level `messageId` (dedup token)
    against the inner `id` (correlation id) — two different random 16-byte
    values. Fix: assert `inner.id == decryptedInner.id`. *Lesson: the
    envelope messageId and the inner id are distinct in role; tests must not
    conflate them.*
11. **Cleanup hang: non-daemon Netty/OkHttp threads.** Symptom: test passed
    but Gradle's test JVM never exited (timeout 100–150 s). Root cause:
    `relay.stop()` + `httpClient.close()` weren't in `finally`/bounded. Fix:
    bounded `relay.stop(500, 2000)` + `httpClient.close()` in `finally`.
    *Lesson: integration tests with real servers must hard-stop all
    non-daemon thread sources in a bounded finally.*
12. **`RelayStore.sweep` — `removeIf` returns Boolean, not a count.** Symptom:
    compile error "None of the following candidates is applicable" (Int +=
    Boolean). Fix: `val before = q.size; q.removeIf{...}; removed += before -
    q.size`. *Lesson: Java `Collection.removeIf` returns boolean.*
13. **`reserved-kinds` test: `4u` (UInt) vs `code` (UByte) type mismatch.**
    Symptom: `assertEquals(4u, kind.code)` failed (UInt vs UByte). Fix:
    `4u.toUByte()`. *Lesson: Kotlin unsigned types are not interchangeable.*
14. **Long `PreKeyBundle(...)` constructor one-liners exceed 140-char
    max-line-length (ktlint can't auto-fix).** Fix: manual multi-line break.

### Owner-cited bugs with **no conversation-record or repo artifact**  **[UNCERTAIN]**
The owner listed: "hex sliding-window, PQXDH unacknowledged sessions,
TransportManager never observing state, clock-epoch mismatch, relay logging
raw key ids, HashMap<ByteArray> identity bug." Of these, **only
"SharedFlow inbound drop" matches a bug in this conversation's record**
(#9 above). The others have **no worklog entry (pruned) and no
conversation-record evidence** — they may have occurred in a prior session whose
artifacts were pruned, OR in the owner's cited 152-test baseline (also not in
this conversation's record). They are recorded here as **owner-reported; details
UNCERTAIN — not invented.** If they are real, the survival guide (§9) ensures
the next reconstruction re-catches them via the test suite.

> Notable design *avoidances* (bugs that did NOT happen because of explicit
> choices, worth recording): `ConcurrentHashMap<String>` with `toHex` keys is
> used everywhere instead of `HashMap<ByteArray>` — because `ByteArray.equals`
> is referential, a `HashMap<ByteArray, _>` would silently never find keys.
> This is the "HashMap<ByteArray> identity bug" the owner cited — it was
> **avoided by construction**, not caught by a test.

---

## 6. Current state snapshot (as of writing this doc)

| Artifact | State | Source |
|----------|-------|--------|
| `docs/PLAN.md`, `ARCHITECTURE.md` | M1, in repo | git `0d7462b` |
| `docs/adr/ADR-0001/0002/0003`, `README.md` | M1 Proposed (amendments pruned) | git `0d7462b` |
| ADR-0006/0007/0008 | **NOT in repo** (pruned) | conversation record only |
| `worklog.md` | 49 lines: M1 entry + 4th-prune policy | git `70fe037`/`d641b50` |
| `/android` tree (all modules, source, tests) | **GONE** (4th prune) | not in repo |
| `/.tools` (Gradle + JDK) | **GONE** (4th prune) | not in repo |

**Test counts per module — CURRENT: 0 (nothing to run; tree pruned).**

| Module | Last conversation-record count | FULL / STUB / deferred | Notes |
|--------|-------------------------------|------------------------|-------|
| `:core:domain` | 5 (outbox SM) | partial (M3 added outbox SM) | pruned |
| `:core:protocol` | 28 (envelope×14, fp×6, safety×4, wordlist×4) | FULL (JVM-verified) | pruned; golden vectors were regenerated for definite-length encoding |
| `:core:crypto` | 0 tests (stub) | **STUB** (real Ed25519 BC; libsignal sessions `TODO`) | owner cites 24 tests (full impl) — **[UNCERTAIN]** |
| `:core:data` | 0 tests (source-only) | source-only (no Robolectric) | owner cites 30 Robolectric tests — **[UNCERTAIN]** |
| `:transport:websocket` | 0 tests (impl exists) | impl, untested | owner cites 3 tests — **[UNCERTAIN]** |
| `:relay` | 12 (auth×6, store×6) | FULL (JVM-verified) | pruned |
| `:testing:signal-sim` | 6 | FULL (JVM-verified) | pruned |
| `:testing:e2e` | 1 (the M3 gate) | FULL (JVM-verified, PASSED) | pruned |
| **Total (conversation-record)** | **~52** | | owner cites 152 — **[UNCERTAIN]** |

**Pinned versions (from conversation record; version catalog [PRUNED]):**
Kotlin 2.1.0 · AGP 8.7.3 · Gradle 8.10.2 · JDK 21 (Temurin) ·
kotlinx-serialization **1.8.0** (owner wants **1.9.0** — available on Maven
Central, adoptable) · kotlinx-coroutines 1.10.2 · Ktor 3.0.3 · BouncyCastle
1.86 · libsignal-android/libsignal-client **0.86.5** · Room 2.6.1 ·
sqlcipher-android 4.19.1 · minSdk 26 / target 35.

**APK status:** none — no Android build has been produced (no AGP run in-sandbox;
`:app` is a stub).

**CI status:** `.github/workflows/android.yml` exists in conversation record
(ktlintCheck detekt vaultGuard test assembleDebug) but is **[PRUNED]** from the
repo. **No GitHub Actions run has been executed** — no git remote is configured
and no PAT has been received. The interim git bundle
(`/home/z/my-project/drs-chat-interim-2026-10-15.bundle`, committed) is the
push-egress fallback.

---

## 7. Deviations & open debts

| Item | Reference | Where closed |
|------|-----------|---------------|
| 4 sandbox prunes wiped `/android` + `/.tools` + M2/M3 docs | worklog (policy) | permanent policy: commit+push per session; needs a real remote + PAT |
| No git remote configured | — | owner provides repo URL + PAT |
| PAT never arrived (twice) | worklog session-4 | owner re-sends via a secure channel |
| "152-test M3 baseline" unverified | §1, §6 | owner confirms it exists outside this conversation, OR re-baseline to the conversation-record 52 |
| Kind taxonomy 0–5 disputed (4=READ/5=DELIVERED vs 4/5 reserved) | §4 | owner confirms full table; then regenerate golden vectors programmatically |
| kotlinx 1.8.0 vs owner's 1.9.0 | §6 | bump on next reconstruction (1.9.0 is available) |
| `:core:crypto` stub (libsignal sessions TODO) | §6 | M4 (or M3.5 if baseline restored) |
| `:core:data` zero Robolectric tests | §6 | needs Android SDK / CI (not achievable in-sandbox) |
| Instrumented on-device session tests | ADR-0006 (pruned) | M4 two-device E2E |
| Relay queue in-memory (restart loses queued envelopes) | ADR-0008 §3 (pruned) | production hardening = SQLite-backed queue |
| `:transport:websocket` untested (owner cites 3 tests) | §6 | add transport unit tests on reconstruction |
| CI workflow not in repo; no Actions run | §6 | commit workflow on reconstruction; first push runs it |

---

## 8. Glossary (plain language, for a non-expert owner)

- **Envelope** — the sealed, signed, versioned "package" every message travels
  in. Like a tamper-evident courier pouch: the sender signs the outside, the
  inside is encrypted, and the pouch carries its own routing address.
- **Ratchet (Double Ratchet)** — the algorithm that generates a fresh
  encryption key for every message, so even if one key leaks, past + future
  messages stay safe. Like a combination lock that scrambles itself after each
  use.
- **X3DH** — "Extended Triple Diffie-Hellman": the first-message handshake
  (Alice + Bob's prekeys combine to derive a shared secret). The old
  pre-quantum version.
- **PQXDH** — "Post-Quantum Extended Diffie-Hellman": X3DH + a Kyber
  (post-quantum) key, so the handshake is resistant to a future quantum
  computer. libsignal 0.86 makes this mandatory.
- **OTK (one-time prekey)** — a single-use key Bob publishes; Alice "spends" one
  to start a session. Like a one-time-pad coupon.
- **Prekey** — a public key Bob publishes in advance so Alice can start an
  encrypted session without him being online. Signed prekey = medium-term;
  one-time prekey = single-use; Kyber prekey = the post-quantum one.
- **Outbox** — the local persisted queue on the sender's device. A message is
  saved to the outbox BEFORE it's sent, so if the app crashes the message
  survives and retries. Like a mailbox that holds letters until the postman
  picks them up.
- **Store-and-forward** — the relay keeps a message for an offline recipient
  (up to 7 days) and delivers it when they connect. Like a poste-restante.
- **DTN (Delay-Tolerant Networking)** — a network where messages hop across
  devices that may not have a live path to each other (a phone carries a
  message, hands it to the next phone, etc.). Future Phase 2+.
- **Safety number** — a 60-digit number both users compute from each other's
  identity keys; if both see the same number, nobody has swapped their keys.
  Like comparing fingerprints over a phone call.
- **Fingerprint** — a short hash of a user's identity public key, rendered as 8
  words (e.g. "anchor · apple · arrow…") for reading aloud.
- **TTL (time-to-live)** — how long a message is valid; after it, the recipient
  drops it. Like an expiration date on a coupon.
- **Relay** — the dumb server that routes opaque envelopes by recipient address.
  It cannot read messages; it only forwards. Like a sorting office that reads
  only the zip code.
- **Mesh** — devices relay for each other (BLE mesh, Wi-Fi Direct). Phase 2+.
- **LoRa** — long-range, low-bandwidth radio (Meshtastic). A future transport.

---

## 9. Survival guide (CRITICAL — if the sandbox prunes again)

> This is the authoritative rebuild recipe. The GitHub repo URL is
> **[UNKNOWN — no remote is configured in the sandbox]**; until one is set,
> **this document + the committed `worklog.md` + the git bundle
> (`drs-chat-interim-2026-10-15.bundle`)** are the authoritative baseline.

### Step 0 — tools (download; ~350 MB, ~5 min)
```bash
mkdir -p /home/z/my-project/.tools && cd /home/z/my-project/.tools
curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip" && unzip -q gradle.zip
curl -sL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" && tar xzf jdk21.tar.gz
export JAVA_HOME=/home/z/my-project/.tools/$(ls -d jdk-21*) PATH="$JAVA_HOME/bin:$PATH"
```

### Step 1 — reconstruct the module tree
The full tree is in §2. Recreate it under `drs-chat/android/` + `drs-chat/server/relay/`.
The version catalog pins (§6): Kotlin 2.1.0, AGP 8.7.3, kotlinx-serialization
**1.9.0** (adopt on reconstruction), Ktor 3.0.3, BC 1.86, libsignal 0.86.5,
Room 2.6.1, sqlcipher-android 4.19.1.

### Step 2 — baseline test counts to re-verify (conversation-record)
| Module | Target | Notes |
|--------|--------|-------|
| `:core:domain` | 5 | outbox state machine |
| `:core:protocol` | 28 | incl. golden bytes (regenerate programmatically — never hand-type hex) |
| `:testing:signal-sim` | 6 | libsignal-client sessions |
| `:relay` | 12 | auth (incl. x25519-substitution) + store |
| `:testing:e2e` | 1 | the M3 gate (real relay + transport, QUEUED→READ) |
| **JVM total** | **~52** | owner cites 152 incl. :core:crypto 24 + :core:data 30 — **[UNCERTAIN], needs Android SDK/CI** |

### Step 3 — verify (sandbox, pure-JVM subset)
```bash
cd /home/z/my-project/drs-chat/android
export JAVA_HOME=/home/z/my-project/.tools/jdk-21.* PATH="$JAVA_HOME/bin:$PATH"
./gradlew -c settings-sandbox.gradle.kts clean ktlintCheck detekt test --rerun-tasks
# expect: ~52 tests, 0 failed, 0 skipped, ktlint clean, detekt clean, BUILD SUCCESSFUL
```

### Step 4 — vault-key guard
```bash
./scripts/vault-guard.sh   # must print "OK" (no setUserAuthenticationRequired(true) / biometric binding)
```

### Step 5 — full Android build (needs Android SDK; CI only)
```bash
./gradlew clean ktlintCheck detekt test assembleDebug
```

### Step 6 — survive the next prune
```bash
cd /home/z/my-project
git add -A && git commit -m "<milestone> <note>" && git push --all   # or:
git bundle create /home/z/my-project/drs-chat-<date>.bundle --all   # fallback
```

### Authoritative baseline lives at:
- **GitHub repo:** [UNKNOWN — owner must provide the remote URL + a PAT]
- **This document:** `docs/PROJECT_HISTORY.md` (committed)
- **Git bundle:** `drs-chat-interim-2026-10-15.bundle` (committed, complete history)
- **Worklog:** `worklog.md` (M1 + 4th-prune policy; M2/M3 detail pruned)
