# ADR Index — drs chat

ADRs follow the format: Context → Decision → Alternatives → Consequences.
Status lifecycle: Proposed (planning gate) → Accepted (when its milestone's tests go green) → Superseded.

| # | Title | Status | Notes |
|---|-------|--------|-------|
| [0001](./ADR-0001-wire-envelope-format.md) | Wire envelope format v1 (CBOR, signed header, field semantics) | Proposed | Drafted at M1. Required by spec |
| [0002](./ADR-0002-transport-abstraction.md) | Transport abstraction (byte pipes, keyId routing, manager placement) | Proposed | Drafted at M1. Required by spec |
| [0003](./ADR-0003-key-generation-and-storage.md) | Identity keys, fingerprint, and key storage (Keystore vault + SQLCipher) | Proposed | Drafted at M1. Required by spec |

Planned (drafted with the milestone that implements them):

| # | Title | Planned milestone |
|---|-------|-------------------|
| 0004 | Relay trust model & signed-challenge auth (incl. why the server stays key-opaque) | M3 |
| 0005 | Invite format: QR/share-link CBOR bundle + 256-word list source | M2 |
| 0006 | Monorepo build layout: two Gradle builds, version catalogs | M2 |
| 0007 | Receipt & message status semantics (QUEUED/SENT/DELIVERED/READ) | M3 |
| 0008 | Outbox state machine & retry/backoff policy | M3 |
