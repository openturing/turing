#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# changelog-section.sh — print the CHANGELOG.md section for one version.
#
# Shared by the two places that need release notes for the public repo:
#   * scripts/publish-ce.sh  -> body of the squashed "Release vX.Y.Z" commit
#   * .github/workflows/publish.yml -> body of the GitHub Release on turing-ce
#
# Both must say the same thing: a public repo whose history is one commit per
# release only stays legible if that commit and its Release carry the change
# list. Keeping the extraction in one file keeps them from drifting.
#
# Usage:
#   scripts/changelog-section.sh <version> [changelog-file]
#
#   <version>         e.g. 2026.3.4. An exact "## 2026.3.4" heading wins; if
#                     absent, the minor series "## 2026.3" is used (the project
#                     groups its changelog by minor).
#   [changelog-file]  defaults to CHANGELOG.md in the current directory.
#
# Exits 1 with a message on stderr when no matching section exists, so callers
# can decide whether that is fatal.
# ---------------------------------------------------------------------------
set -euo pipefail

VERSION="${1:-}"
FILE="${2:-CHANGELOG.md}"

if [[ -z "$VERSION" ]]; then
  echo "usage: $(basename "$0") <version> [changelog-file]" >&2
  exit 2
fi
[[ -f "$FILE" ]] || { echo "changelog not found: $FILE" >&2; exit 1; }

section() {
  awk -v h="## $1" '
    $0 == h { inside = 1; next }
    inside && /^## / { exit }
    inside { print }
  ' "$FILE"
}

# Strip leading and trailing blank lines from the captured block.
trim_blank_lines() {
  sed -e '/./,$!d' -e :a -e '/^\n*$/{$d;N;ba' -e '}'
}

NOTES="$(section "$VERSION" | trim_blank_lines)"
if [[ -z "${NOTES//[[:space:]]/}" && "$VERSION" == *.*.* ]]; then
  NOTES="$(section "${VERSION%.*}" | trim_blank_lines)"
fi

if [[ -z "${NOTES//[[:space:]]/}" ]]; then
  echo "no CHANGELOG section for '$VERSION' (or '${VERSION%.*}') in $FILE" >&2
  exit 1
fi

printf '%s\n' "$NOTES"
