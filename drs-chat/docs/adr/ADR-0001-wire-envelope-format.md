# ADR-0001 — Wire Envelope Format v1 (CBOR)

Status: **Proposed** (M1 gate) — auto-promoted to Accepted at M2 exit if tests green.
Date: 2026-10-15 · Deciders: Phase 1 engineering

## Context

Every message travels in a transport-agnostic envelope: WebSocket today; BLE mesh, Wi-Fi Direct, LoRa (Meshtastic), satellite NTN, and DTN store-carry-forward later. The envelope must serialize to a raw byte stream, be signed and encrypted, carry versioning, and stay small (64 KiB hard cap). The relay is deliberately dumb: it must never parse, log, or analyze envelope contents. The spec fixes the field set: `protocolVersion, senderKeyId, recipientKeyId, createdAt, ttlSeconds, kind, payloadCiphertext, signature`.

Two forces shape the format:

1. **Verifiable signatures require deterministic bytes.** The recipient must re-derive the exact byte sequence that the sender signed. Any encoding ambiguity (map key order, float forms, string normalization) breaks verification or, worse, invites malleability.
2. **No code sharing with the server.** The server is dumb by design, so whatever we pick must not force the relay to link a shared codec — another reason the envelope stays opaque to it.

## Decision

**Encode the envelope with CBOR (RFC 8949) using kotlinx-serialization, with schema-pinned determinism locked by golden-byte tests.**

### Schema (single source of truth, `:core:protocol`)

```kotlin
@Serializable
data class WireEnvelope(
    val protocolVersion: UByte,        // 1
    val senderKeyId: ByteArray,        // 16 B = SHA-256(sender Ed25519 identity pub)[0..15]
    val recipientKeyId: ByteArray,     // 16 B = SHA-256(recipient Ed25519 identity pub)[0..15]
    val createdAt: Long,               // Unix ms UTC (sender clock)
    val ttlSeconds: Int,               // sender intent; see semantics below
    val kind: UByte,                   // 0 MESSAGE, 1 DELIVERY_RECEIPT, 2 READ_RECEIPT, 3 SESSION_RESET
    val payloadCiphertext: ByteArray,  // libsignal SessionCipher output for the recipient session
    val signature: ByteArray           // 64 B Ed25519 over HeaderBytes (see below)
)
```

### Signature scope and determinism strategy

* The signer builds `HeaderBytes` = CBOR encoding of a header struct containing exactly the seven fields above minus `signature`.
* `signature = Ed25519(identityPriv, HeaderBytes)`.
* The verifier parses the full envelope, **re-encodes the seven header fields with the same pinned schema class and codec instance**, and verifies the signature against those bytes.
* Determinism contract: kotlinx-serialization CBOR writes map keys in declaration order with definite lengths; both peers compile against the same schema class, so byte-for-byte equality holds in practice. **Golden-byte unit tests pin the exact encoding**; if a dependency upgrade changes the bytes, the test fails and `protocolVersion` must be bumped before any wire compatibility changes ship.

### Field semantics

* **keyIds** are stable 16-byte fingerprint prefixes of the Ed25519 identity key. They double as (a) the relay routing token and (b) the contact lookup key. Identity keys are permanent in Phase 1; the versioned envelope leaves room for rotation later.
* **No envelope-level message id.** Correlation ids (message uuid, receipt acks) live *inside* the encrypted inner payload (`InnerMessage{id,text,sentAt}`, `InnerReceipt{ackedId}`). This keeps the envelope minimal, prevents the server from correlating anything beyond routing size/timing, and lets clients dedup relay redeliveries by inner id.
* **ttlSeconds** is sender intent, enforced *client-side* on receive: drop (silently, log metadata only) if `now > createdAt + ttlSeconds + 300 s` skew allowance. The relay never reads this field; it applies a fixed 7-day retention from receipt time (spec) — so a lying `ttlSeconds` can only shorten/lengthen what the *recipient* accepts, never extend server retention.
* **kind** is the only envelope field a receiving client branches on before decryption; everything else is treated as bytes until `CryptoEngine.decrypt`.

### Limits

* Envelope ≤ 64 KiB total (sender guard + relay frame cap; oversize = reject, never truncate).
* Inner text payload ≤ 4,096 chars in Phase 1 (text-only).

### Versioning policy

* `protocolVersion` major mismatch ⇒ discard + metadata-only log (a future `VERSION_MISMATCH` control frame may notify).
* Minor additions (new kinds, new optional trailing fields) must be additive and tolerated by older clients.

## Alternatives considered

* **Protobuf**: mature and compact, but forces codegen into the critical path for no size win at these scales, and we would still need the sign-the-canonical-header trick. Rejected for Phase 1 simplicity; the versioned envelope keeps the door open.
* **JSON**: human-readable but binary-hostile (payloadCiphertext), verbose on LoRa-sized budgets later, and canonicalization is a minefield. Rejected.
* **Hand-rolled canonical binary layout** (like Signal's own wire formats): maximal determinism control, but ~300 lines of hand-maintained encoding code is exactly the kind of bug farm we don't want in a milestone where the codec must be provably round-trip clean. Revisit only if cross-implementation interop (e.g., a Meshtastic firmware decoder) ever demands it.

## Consequences

* (+) One codec serves every transport; the same bytes flow over WS today and BLE/LoRa later with zero domain changes.
* (+) Golden tests make encoding drift loud instead of silent.
* (−) Determinism leans on library behavior rather than a spec we wrote; mitigated as described.
* (−) CBOR debuggability is weaker than JSON; we accept CBOR diagnostic tooling.
* (−) No envelope-level dedup id means the relay may redeliver after reconnect; clients dedup by inner id (cheap, tested).
