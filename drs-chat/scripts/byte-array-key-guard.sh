#!/usr/bin/env bash
# byte-array-key-guard.sh — structural guard for the ByteArray-as-key bug family.
#
# BUG FAMILY HISTORY: `HashMap<ByteArray, V>` / `HashSet<ByteArray>` lookups
# use ByteArray.equals (which is referential, not content-based) → silent
# misses → bug resurrections:
#   - M2 InMemoryKeyWrapService
#   - M3 trustedIdentities (initial)
# Instance fixes are not enough. This guard prevents the family at source.
#
# HARD RULE: production sources MUST NOT use `Map<ByteArray`, `Set<ByteArray`,
# `MutableMap<ByteArray`, or `MutableSet<ByteArray` as a key type. Use a
# content-based key (String hex / a value class with proper equals) instead.
# Test fixtures are exempt (they may legitimately construct such maps to
# verify the guard itself).
#
# Exits 0 if clean; 1 on violation. Idempotent, no side effects.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GUARD_NAME="byte-array-key-guard"

# Production source roots across all modules. Each module follows
#   android/<path>/src/main/kotlin/...
# We grep only src/main (never src/test — test fixtures are exempt).
MAPS_ROOT="$REPO_ROOT/android"

if [[ ! -d "$MAPS_ROOT" ]]; then
  echo "[$GUARD_NAME] $MAPS_ROOT not present — pass."
  exit 0
fi

# Find all src/main kotlin files, then grep for forbidden key types.
# Tolerate whitespace between `Map<` and `ByteArray` (e.g. `Map<ByteArray, V>`
# and `Map< ByteArray, V>`). The trailing `,` or `>` distinguishes key
# position (Map<ByteArray, V>) from value position (Map<K, ByteArray>) —
# we flag key position only, since that's where the bug bites.
VIOLATIONS=$(find "$MAPS_ROOT" -type d -name "main" -path "*/src/*" -print0 \
  | xargs -0 grep -rnE \
    -e 'Map<\s*ByteArray\s*,' \
    -e 'Set<\s*ByteArray\s*>' \
    -e 'MutableMap<\s*ByteArray\s*,' \
    -e 'MutableSet<\s*ByteArray\s*>' \
    2>/dev/null || true)

if [[ -n "$VIOLATIONS" ]]; then
  echo "[$GUARD_NAME] FAIL — ByteArray used as Map/Set key (referential equals bug):"
  echo "$VIOLATIONS"
  cat <<'MSG'
[$GUARD_NAME] ByteArray.equals is referential → HashMap<ByteArray, V> silently misses
lookups. Use a content-based key (String hex via toHex(), or a value class with
proper equals). See ADR-0003 (storage) and the bug-family history above.
MSG
  exit 1
fi

echo "[$GUARD_NAME] OK — no ByteArray-as-key usage in production sources."
exit 0
