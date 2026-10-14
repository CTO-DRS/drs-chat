# Worklog — drs chat

Shared multi-agent log. Append-only; each section starts with `---`.

---
Task ID: 1
Agent: main (Super Z)
Task: Milestone 1 — milestone plan + module tree + ADR drafts for Phase 1 of "drs chat" (offline-first encrypted messenger, internet-only transport). Explicit gate: STOP for user approval before writing any code.

Work Log:
- Analyzed Phase 1 spec; resolved ambiguities autonomously per instructions (Ed25519 vs X25519 identity split, CBOR choice, TTL semantics vs dumb relay, libsignal JVM-testability constraint) — each recorded in ADRs/PLAN.
- Created /home/z/my-project/drs-chat/docs/ tree.
- Wrote docs/PLAN.md: M1–M4 milestones with exit criteria, M2/M3/M4 breakdowns, 12 key decisions (D-1..D-12), version pins, testing strategy incl. sandbox limitations, risks, assumptions, quality gates.
- Wrote docs/ARCHITECTURE.md: module tree (8 spec modules + :app + :testing:signal-sim deviation note), dependency rules, message lifecycle, port/contract sketches, relay control-plane surface, threading notes.
- Wrote docs/adr/ADR-0001-wire-envelope-format.md (CBOR + golden-byte determinism + signature scope + field semantics).
- Wrote docs/adr/ADR-0002-transport-abstraction.md (byte-pipe contract, recipientKeyId universal routing, TransportManager placement).
- Wrote docs/adr/ADR-0003-key-generation-and-storage.md (Ed25519 identity + X25519 signal identity, Keystore AES-GCM vault wrapping SQLCipher, BC Ed25519 provider, residual risks).
- Wrote docs/adr/README.md (ADR index + planned ADRs 0004–0008).
- Did NOT scaffold code — honoring the M1 approval gate.

Stage Summary:
- Deliverable: complete M1 planning set under /home/z/my-project/drs-chat/docs/.
- Key decisions awaiting user approval: CBOR envelope w/ golden-byte pinning; Ed25519 signing identity + separate X25519 Signal identity bound in one fingerprint; Keystore vault wraps SQLCipher where all protocol keys live; transports as byte pipes routed by 16-byte keyId; two Gradle builds (/android, /server); :testing:signal-sim JVM module because libsignal-android natives can't run in host-JVM tests.
- Next: on approval → M2 (scaffold + :core:protocol + :core:crypto + tests).
