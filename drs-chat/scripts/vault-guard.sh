#!/usr/bin/env bash
# vault-guard.sh — CI guard for the Keystore vault policy (ADR-0003).
#
# HARD RULE: the vault key MUST NOT bind user-authentication / biometric.
# Rationale: a lockscreen change must not destroy on-disk message history.
# This script greps the :core:data Keystore code for forbidden patterns and
# fails the build on any hit. Comment lines are skipped to avoid matching
# our own documentation strings. Test fixtures are exempt.
#
# Exits 0 if clean; 1 on violation. Idempotent, no side effects.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Production Keystore code lives only in :core:data (per ARCH §2 — single
# crypto home). Grep that subtree; if it does not yet exist (pre-M3), pass.
DATA_DIR="$REPO_ROOT/android/core/data/src/main"
GUARD_NAME="vault-guard"

if [[ ! -d "$DATA_DIR" ]]; then
  echo "[$GUARD_NAME] $DATA_DIR not present yet (pre-M3) — pass."
  exit 0
fi

# Match (in NON-comment lines):
#   - setUserAuthenticationRequired(true)   — the canonical violation
#   - setUserAuthenticationParameters(<n>, BIOMETRIC_*) — biometric binding
#
# Skip lines that start with optional whitespace then // or * (comment /
# Javadoc continuation).
VIOLATIONS=$(find "$DATA_DIR" -name '*.kt' -print0 \
  | xargs -0 grep -rnE \
      -e 'setUserAuthenticationRequired\s*\(\s*true\s*\)' \
      -e 'setUserAuthenticationParameters\s*\(\s*[0-9]+\s*,\s*[A-Z_]+BIOMETRIC' \
  2>/dev/null \
  | grep -vE '^[^:]+:[0-9]+:\s*(//|\*)' \
  || true)

if [[ -n "$VIOLATIONS" ]]; then
  echo "[$GUARD_NAME] FAIL — vault key binds user-auth/biometric:"
  echo "$VIOLATIONS"
  echo "[$GUARD_NAME] ADR-0003 forbids this: a lockscreen change would destroy history."
  exit 1
fi

echo "[$GUARD_NAME] OK — no user-auth/biometric binding in $DATA_DIR."
exit 0
