# ADR-0003 — Identity Keys, Fingerprint, and Key Storage

Status: **Proposed** (M1 gate) — auto-promoted to Accepted at M2 exit if tests green.
Date: 2026-10-15 · Deciders: Phase 1 engineering

## Context

drs chat identities have no phone number and no email. The spec fixes: generate an **Ed25519 identity key** plus **X25519** and Signal prekeys on first launch; store private keys in Android Keystore (StrongBox when available); identity = SHA-256 fingerprint of the public key rendered as a word list + QR; relay auth = client signs a nonce with the identity key.

Three platform facts constrain the design:

1. Android Keystore could not generate or import Ed25519/X25519 keys before API 33, and even on API 33+ support varies by OEM/Tee. On minSdk 26 we cannot assume it anywhere in our support range.
2. libsignal (Rust) performs X3DH + Double Ratchet with raw key bytes it keeps in its own protocol store — keys must be handed to it in memory regardless of where they rest.
3. Signal Protocol's identity key for X3DH is X25519 (Curve25519); the spec's Ed25519 identity key serves signing (envelopes, relay challenge, fingerprint). These are related but distinct key types.

## Decision

### 1. Key inventory (per user, generated once at onboarding)

| Key | Type | Purpose |
|-----|------|---------|
| `identityEd25519` | Ed25519 (32 B) | **The identity.** Signs envelopes, relay challenge; source of fingerprint/safety number |
| `identityX25519` | X25519 (32 B) | Signal Protocol X3DH root identity |
| `signedPrekey` | X25519 + Ed25519 sig | X3DH medium-term (rotate ~30 days, Phase 1: on refill) |
| `oneTimePrekeys` | X25519 ×25 | X3DH ephemeral; refill when pool < 10 |

The X25519 identity key is **cross-signed** by the Ed25519 identity key inside the invite bundle, so one identity attests both key halves.

### 2. Fingerprint and keyId derivation (domain-separated, versioned)

```
fp = SHA-256( 0x01 || "drs-id-v1" || ed25519Pub(32) || x25519Pub(32) )   // 32 bytes
keyId          = fp[0..15]    // 16 B — envelope routing + local contact lookup
word rendering = fp[0..7]     // 8 B → 8 words from a curated 256-word list (1 word/byte)
QR             = full invite bundle (ADR-0005 will pin exact CBOR invite format)
```

Safety number (per-contact, 60 digits like Signal): `SHA-256(sorted(ed25519Pub_me, ed25519Pub_them))[0..23]` → 12 groups of 5 digits (each group = two bytes big-endian mod 100000). Verification screen shows 60 digits in 12 groups with a "mark verified" action.

### 3. Storage: Keystore-wrapped vault + SQLCipher as the key store

Android Keystore **cannot hold our protocol keys** (fact 1), so keys do not live in Keystore. Instead, two layers:

* **L1 — Vault key:** an AES-256-GCM key generated *inside* Android Keystore, `strongBoxBacked(true)` when the device offers it, falling back to the TEE-backed default. Never extractable. No user-auth binding in Phase 1 (offline-first must unlock without interaction after reboot-free restarts).
* **L2 — Encrypted realm:** SQLCipher database whose passphrase is a random 32-byte value; the passphrase is stored only wrapped by L1 (blob in app-private storage). All protocol keys live *inside* this database through our Room-backed implementation of libsignal's `SignalProtocolStore` (session records, prekeys, identity keys — opaque blobs to `:core:data` via the `SignalRecordStore` port).

Signing/decryption happens in-process: keys are unwrapped from the DB into libsignal memory at use time. This is the same pragmatic trade Signal Android makes (SQLCipher + Keystore-wrapped secret), because fact 2 makes in-memory key material unavoidable anyway.

**Residual risks, accepted and documented:** a rooted device with the DB unlocked can read key material; memory scraping during use is theoretically possible. Hardware security still protects the *vault*, so at-rest compromise requires both root and the app running. Full Keystore-resident signing would require an EC P-256 identity — breaking the spec's Ed25519 mandate and splitting key models by OS version — and is rejected.

### 4. Ed25519 provider

BouncyCastle (`bcprov-jdk18on`) uniformly across API 26–35 for Ed25519 sign/verify, isolated strictly inside `:core:crypto`. Rationale: `java.security` Ed25519 appears only on API 33+ (Conscrypt), which would fork the code path by OS version; BC gives one tested path everywhere. Keystore Ed25519 (API 33+) remains a future optimization behind the same facade. libsignal supplies X25519/X3DH/ratchet.

### 5. Rejected alternatives

* **Keystore EC P-256 identity + X25519 for Signal:** violates the spec's Ed25519 identity; two identity stories confuse fingerprint derivation and relay auth.
* **Keystore Ed25519 on API 33+ with BC fallback below:** two key models, two test matrices, subtle behavioral drift — not worth it in Phase 1.
* **EncryptedSharedPreferences for key blobs:** deprecated by the Android team and redundant — everything it would protect lives in SQLCipher already.
* **Storing protocol keys in a file encrypted by the vault key (outside the DB):** splits key material across two stores and complicates the libsignal store; the DB is the single encrypted home.

## Consequences

* (+) Uniform behavior across minSdk 26 → latest; StrongBox used where present.
* (+) One encrypted home for all protocol state; backups of the DB are useless without the Keystore vault (matches "no cloud key backup" non-goal).
* (+) Fingerprint binds both key halves; a QR carrying only the X25519 half cannot impersonate the identity.
* (−) Private keys transit app memory (unavoidable given libsignal); documented, not hidden.
* (−) Lost device = lost identity, by design (no recovery is a non-goal); invite bundles re-establish contacts on the new identity.
* (−) Prekey exhaustion on a stale bundle falls back to signed-prekey-only X3DH (libsignal supports it) — test-covered.
