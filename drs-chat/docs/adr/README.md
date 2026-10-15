# ADR Index — drs chat

ADRs follow: Context → Decision → Alternatives → Consequences.
Status: Proposed → Accepted (when its milestone's tests go green) → Superseded.

| # | Title | Status |
|---|-------|--------|
| [0001](./ADR-0001-wire-envelope-format.md) | Wire envelope v1 (CBOR; messageId; definite-length + byte-string) | Accepted (M2/M3) |
| [0002](./ADR-0002-transport-abstraction.md) | Transport abstraction (byte pipes, keyId routing) | Accepted |
| [0003](./ADR-0003-key-generation-and-storage.md) | Identity/fingerprint/storage (vault; no user-auth binding; PQ Kyber) | Accepted (M2/M3) |
| [0006](./ADR-0006-deferred-instrumented-session-tests.md) | Instrumented on-device session tests deferred to M4 (deviation) | Accepted (M3) — **[PRUNED from repo; to be re-committed on rebuild]** |
| [0007](./ADR-0007-receipts-and-status-semantics.md) | Receipts originate from recipient session; kind-space | Accepted (M3) — **[PRUNED; kind numbering SUPERSEDED by ADR-0009]** |
| [0008](./ADR-0008-relay-trust-model-and-protocol.md) | Relay signed-challenge auth; AUTH binds x25519Pub | Accepted (M3) — **[PRUNED; to be re-committed]** |
| [0009](./ADR-0009-baseline-adjudication-and-frozen-kind-table.md) | **Baseline adjudication (152-test spec) + frozen kind table (4=READ/5=DELIVERED) + per-module push policy** | Accepted (2026-10-16, owner adjudication) |
