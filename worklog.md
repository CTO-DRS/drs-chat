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

---
Task ID: 4 (4th-prune interim + workflow policy)
Agent: main (Super Z)
Task: STEP 0 (commit as-is, push/bundle) + STEP 1 (permanent workflow policy). Status correction acknowledged; honest constraints surfaced.

== PERMANENT WORKFLOW POLICY (binding from 2026-10-15) ==
Three sandbox prunes have wiped /android + /.tools between sessions. To stop the bleed:
1. EVERY SESSION STARTS with: `cd /home/z/my-project && git status && git log --oneline -5 && find drs-chat -maxdepth 3 -type d | head`. Drift (pruned tree) is reported BEFORE any work.
2. EVERY SESSION ENDS with: `git add -A && git commit -m "<milestone> interim/snapshot <note>" && git push --all` (or, if push egress is blocked, `git bundle create ../drs-chat-<date>.bundle --all` + report the path).
3. The /.tools (Gradle + JDK) and /android tree are NON-PERSISTENT in this sandbox — assume they are pruned on every restart. Re-download + reconstruct from the last commit/bundle.
4. Never claim a milestone "complete" unless the test count + CI green are verified IN THIS SESSION (post-reconstruction). Prior-session numbers are not transferable across prunes.

== THIS SESSION (4th prune) ==
- Drift: 4th prune confirmed. /.tools GONE, /android tree GONE, /docs reverted to M1 "Proposed" (ADR-0001/0002/0003 at M1; M3 ADRs 0006/0007/0008 GONE; M2/M3 amendments to 0001/0002/0003 GONE). worklog reverted to 24 lines (M1).
- TOKEN: the user said a new fine-grained PAT was "embedded below" but NO token appeared in the message. NOT used, NOT invented. Cannot push. Produced a git bundle instead (path below).
- STATUS CORRECTION: acknowledged — the prior "M3 complete" report was an interim snapshot, not completion. HONEST PUSH-BACK: my conversation record shows M3 work reaching 52 tests, with :core:crypto (real Ed25519 BC, libsignal sessions TODO) and :core:data (Room+SQLCipher+vault source, ZERO Robolectric tests). I have NO record in this conversation of a 152-test baseline (full :core:crypto 24 + Robolectric :core:data 30). I will NOT claim to "restore" a baseline I cannot verify existed — that would be fabrication. Sandbox constraints: Robolectric :core:data needs the Android SDK (android.jar); full :core:crypto needs AGP + libsignal-android AAR (won't run on host JVM). 152/0/0 with --rerun-tasks is NOT achievable in this sandbox.
- WIRE FORMAT (STEP 3): the user states the "verified M3" froze kind 4 = READ_RECEIPT, kind 5 = DELIVERY_RECEIPT. My current (pruned) impl had DELIVERY_RECEIPT=1, READ_RECEIPT=2, +4/5 reserved. These CONFLICT. I have no record of the 4=READ/5=DELIVERED baseline. Requested confirmation of the FULL kind taxonomy (0-5) before changing the wire format (a frozen wire format must not be guessed).
- kotlinx 1.9.0 (STEP 4): to be probed once .tools restored.
- Action this session: committed the surviving /docs (M1 state) + this worklog as "interim snapshot post-4th-prune"; produced a git bundle (no token to push). Did NOT reconstruct /android (would be pruned; no token to push). Did NOT start M4.

Stage Summary:
- On-disk: M1 docs only (post-4th-prune). Committed + bundled.
- BLOCKERS needing user input: (1) re-send the fine-grained PAT (it did not arrive); (2) confirm whether a 152-test M3 baseline actually exists outside this conversation (I have no record); (3) confirm the full envelope kind taxonomy 0-5 for STEP 3.
- NOT started M4 per instruction.
