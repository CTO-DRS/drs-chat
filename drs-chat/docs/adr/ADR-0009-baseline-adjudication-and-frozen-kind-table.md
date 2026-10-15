# ADR-0009 — Baseline Adjudication & Frozen Kind Table

Status: **Accepted** (owner adjudication, 2026-10-16).
Date: 2026-10-16 · Deciders: project owner (adjudication), engineering (implementation).

## Context
Two disputes arose during the M3 reconstruction-and-documentation pass:
1. Whether a "152-test M3 baseline" ever existed (the agent's conversation
   record showed ~52 tests; the agent flagged the 152 figure as unverified
   against repo artifacts, since the sandbox prunes had destroyed the M2/M3
   code + worklog entries).
2. The envelope kind numbering: the agent's reconstruction used
   `DELIVERY_RECEIPT=1, READ_RECEIPT=2, kinds 4/5 reserved`; the owner's cited
   M3 baseline froze `kind 4 = READ_RECEIPT, kind 5 = DELIVERY_RECEIPT`.

The owner holds the authoritative chat record (every milestone report,
verbatim). The owner adjudicates both disputes below. The agent's epistemic
discipline — refusing to assert a baseline it could not verify, and refusing to
renumber a frozen wire format on a guess — was correct procedure; this ADR
replaces those unverified-claim flags with explicit owner decrees.

## Decision

### Dispute 1 — the 152-test M3 baseline is OWNER-ATTESTED HISTORY
> Owner attestation: an M3 report was produced and formally accepted, stating
> verbatim: "Definitive unconditional re-execution `test --rerun-tasks` →
> BUILD SUCCESSFUL, 152 tests / 0 failed / 0 skipped: protocol 41 - crypto 24
> - data 30 - domain 22 - websocket 3 - server 15 - e2e 8 - signal-sim 9."

The 152 baseline is **owner-attested history**. Its named test list is the
**ACCEPTANCE SPEC for the rebuild** — a specification to build to, not invented
history. The agent is not asked to pretend old code exists; the agent is asked
to build code passing the same named criteria:

| Module | Tests | Named criteria (acceptance spec) |
|--------|-------|----------------------------------|
| `:core:protocol` | 41 | incl. ContactBundle 6; golden vectors regenerated **programmatically** (never hand-typed hex); kind-space test locking `4=READ / 5=DELIVERED` |
| `:core:crypto` | 24 | Alice-Bob PQXDH, out-of-order, tamper, Eve forgery → UntrustedSender, session reset, OTK replenish, signed-prekey rotation, relay challenge, plaintext cap, bundle-signature tamper |
| `:core:data` | 30 | FULL Room+SQLCipher+Keystore-vault, Robolectric incl. process-death persistence; vaultGuard stays **negative-tested** |
| `:core:domain` | 22 | (full, incl. outbox state machine) |
| `:transport:websocket` | 3 | |
| `:relay` (server) | 15 | |
| `:testing:e2e` | 8 | full receipt flow, offline/reconnect drain, client-side TTL expiry, queue cap, rate limit, oversized, malformed, log hygiene |
| `:testing:signal-sim` | 9 | |
| **Total** | **152** | 0 failed / 0 skipped, `test --rerun-tasks` |

Target verification: `152/0/0` with `--rerun-tasks`, ktlint/detekt clean, and a
**green GitHub Actions run** (run URL reported).

### Dispute 2 — the envelope kind table is FROZEN at 4=READ / 5=DELIVERED
> Accepted M3 report, verbatim: "DELIVERED (kind 5) auto-generated, READ
> (kind 4) explicit - both ratcheted under the live session, never
> relay-granted."

**Frozen wire contract:**
- `kind 4 = READ_RECEIPT` (explicit — recipient app emits it when the user opens the conversation)
- `kind 5 = DELIVERY_RECEIPT` (auto-generated — recipient's session emits it on decrypt)

Both are ratcheted under the **live Signal session** (encrypted as fresh
envelopes from the recipient's outbound session, signed by the recipient's
Ed25519 identity), **never relay-granted** (the relay cannot issue, claim, or
synthesize them — it lacks the session keys). This supersedes ADR-0007's
earlier `DELIVERY_RECEIPT=1, READ_RECEIPT=2` numbering.

**Supersession note:** the reconstruction's `DELIVERED=1 / READ=2 / kinds 4&5
reserved` numbering is **SUPERSEDED** by this ADR. The full kind taxonomy (0–5)
is:
- `0` — MESSAGE
- `1` — SESSION_RESET
- `2`, `3` — reserved (TBD; not yet assigned — do NOT reuse 4/5)
- `4` — READ_RECEIPT (frozen)
- `5` — DELIVERY_RECEIPT (frozen)

> Note: kinds 2/3 are left reserved pending owner confirmation of the full 0–5
> table; the rebuild's kind-space test locks only `4=READ / 5=DELIVERED`
> (the frozen pair) and rejects ≥ 6, and does NOT assert 2/3 semantics until
> the owner confirms them. This is the minimal frozen contract; guessing 2/3
> would repeat the error this ADR corrects.

The agent's earlier refusal to renumber on a guess was correct — this is no
longer a guess; it is an owner decree. A kind-space lock test asserts
`4==READ_RECEIPT` and `5==DELIVERY_RECEIPT` explicitly; any future change to
these two codes requires a new ADR + a `protocolVersion` bump.

### New policy — commit AND push after every module (worklog + this ADR)
Three sandbox prunes have cost entire milestones. Effective immediately:
- **After EVERY module** is rebuilt/verified, `git add -A && git commit -m
  "<module> <note>" && git push origin main`. A mid-session prune must never
  cost more than one module.
- **Every session starts** with `git status` + `git log --oneline -5` + a drift
  check; pruned `/.tools`/`/android` are re-downloaded/reconstructed from the
  last push.
- The **owner chat log is the ultimate record** when repo artifacts are pruned;
  the M3.5 named test list (above) is the rebuild baseline spec.

## Consequences
+ Both disputes closed; the agent builds to an explicit, named spec (not
  invented history) and a frozen wire contract (not a guess).
+ Per-module push policy means a prune never loses more than one module's work.
+ The kind-space lock test makes any future drift on `4=READ / 5=DELIVERED`
  loud.
− Kinds 2/3 remain unassigned pending owner confirmation (intentional — do not
  guess).
