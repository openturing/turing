#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# publish-ce.sh — one-way open-core publish (private `turing` -> public `turing-ce`)
#
# Block P / T357. Assembles a clean tree at a release ref, applies the
# `ce-exclude.txt` boundary manifest (T356), and lands it as ONE squashed
# "Release vX.Y.Z" commit on the public repo's default branch (§XIX.3).
#
# The flow is strictly one-way: private is the only source of truth; the public
# repo is a generated artifact (§XIX.1). Each release == exactly one public
# commit — no private history is mirrored, so a commit title can never leak.
#
# Usage:
#   scripts/publish-ce.sh [--ref <tag|sha>] [--remote <url>] [--push]
#                         [--allow-no-scan] [--keep-temp]
#
#   --ref <tag|sha>   Release point to publish. Default: HEAD.
#                     If it is a `v*` tag the version label is derived from it;
#                     otherwise the label comes from <version> in pom.xml.
#   --remote <url>    turing-ce git remote. Default: $CE_REMOTE env, else
#                     git@github.com:openviglet/turing-ce.git
#   --push            Actually push to the public repo. WITHOUT this flag the
#                     script is a DRY RUN: it assembles + scans + commits to a
#                     throwaway local repo and stops before pushing.
#   --allow-no-scan   Proceed even if no secret scanner is installed. Without a
#                     scanner AND without this flag, --push is refused (§XIX.4).
#   --keep-temp       Do not delete the temp work dirs (for inspection).
#
# Env:
#   CE_REMOTE         Default remote (overridden by --remote).
#   CE_PUSH_TOKEN     Used by CI (T360); not consumed here — auth is via the
#                     remote URL / ambient git credentials.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REF="HEAD"
REMOTE="${CE_REMOTE:-git@github.com:openviglet/turing-ce.git}"
DO_PUSH=false
ALLOW_NO_SCAN=false
KEEP_TEMP=false
CE_BRANCH="${CE_BRANCH:-main}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)           REF="$2"; shift 2 ;;
    --remote)        REMOTE="$2"; shift 2 ;;
    --push)          DO_PUSH=true; shift ;;
    --allow-no-scan) ALLOW_NO_SCAN=true; shift ;;
    --keep-temp)     KEEP_TEMP=true; shift ;;
    -h|--help)       grep -E '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)               echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

cd "$PROJECT_ROOT"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------
# 0. Resolve the release ref and version label
# --------------------------------------------------------------------------
git rev-parse --verify "$REF^{commit}" >/dev/null 2>&1 || die "ref not found: $REF"
REF_SHA="$(git rev-parse --short "$REF")"

REF_NAME="$REF"
if [[ "$REF" == "HEAD" ]]; then
  REF_NAME="$(git describe --tags --exact-match "$REF" 2>/dev/null || true)"
fi
if [[ "$REF_NAME" == v* ]]; then
  VERSION="${REF_NAME#v}"
else
  # No v-tag at this ref: read the Maven project version from the ref itself.
  VERSION="$(git show "$REF:pom.xml" | grep -m1 -oP '(?<=<version>)[^<]+' || true)"
fi
[[ -n "${VERSION:-}" ]] || die "could not resolve version label for ref $REF"
COMMIT_MSG="Release v${VERSION}"
log "Publishing ref $REF ($REF_SHA) as \"$COMMIT_MSG\""

# --------------------------------------------------------------------------
# 1. Extract the tree at the ref + the versioned denylist from the same ref
# --------------------------------------------------------------------------
SRC_TREE="$(mktemp -d)"
EXCLUDE_FILE="$(mktemp)"
cleanup() { $KEEP_TEMP || rm -rf "$SRC_TREE" "$EXCLUDE_FILE" "${PUB_TREE:-}"; }
trap cleanup EXIT

log "Extracting tree at $REF -> $SRC_TREE"
git archive --format=tar "$REF" | tar -x -C "$SRC_TREE"

git cat-file -e "$REF:ce-exclude.txt" 2>/dev/null \
  || die "ce-exclude.txt (T356 boundary manifest) missing at ref $REF — refusing to publish without the rule"
git show "$REF:ce-exclude.txt" > "$EXCLUDE_FILE"

# --------------------------------------------------------------------------
# 2. Apply the denylist using git's own ignore engine (honours `!` re-includes)
# --------------------------------------------------------------------------
log "Applying ce-exclude.txt boundary"
DENIED="$(git ls-tree -r --name-only "$REF" \
  | git -c core.excludesFile="$EXCLUDE_FILE" check-ignore --no-index --stdin || true)"

removed=0
if [[ -n "$DENIED" ]]; then
  while IFS= read -r rel; do
    [[ -z "$rel" ]] && continue
    rm -f "$SRC_TREE/$rel"
    removed=$((removed + 1))
  done <<< "$DENIED"
fi
# Prune now-empty directories left behind by the deletions.
find "$SRC_TREE" -mindepth 1 -type d -empty -delete 2>/dev/null || true
log "Denylist removed $removed path(s)"

# Public README: a curated README.ce.adoc replaces the internal README.adoc
# (§XIX.2 / T359). The internal one is already excluded by the denylist.
if [[ -f "$SRC_TREE/README.ce.adoc" ]]; then
  mv -f "$SRC_TREE/README.ce.adoc" "$SRC_TREE/README.adoc"
  log "Promoted README.ce.adoc -> README.adoc"
else
  warn "No README.ce.adoc at ref — public tree has no README yet (T359 curates it)"
fi

# --------------------------------------------------------------------------
# 3. Secret-scan gate (T358 plugs its scanner in here; fail-closed)
# --------------------------------------------------------------------------
run_secret_scan() {
  local tree="$1"
  if command -v gitleaks >/dev/null 2>&1; then
    log "Secret scan (gitleaks) over assembled tree"
    gitleaks detect --no-git --source "$tree" --redact \
      || die "secret scan FAILED — assembled tree contains a secret; aborting (§XIX.4)"
    return 0
  fi
  if command -v trufflehog >/dev/null 2>&1; then
    log "Secret scan (trufflehog) over assembled tree"
    trufflehog filesystem "$tree" --fail --no-update \
      || die "secret scan FAILED — assembled tree contains a secret; aborting (§XIX.4)"
    return 0
  fi
  # No scanner available. The denylist is the policy; the scanner is the
  # backstop (§XIX.4). Refuse to PUSH unscanned unless explicitly overridden.
  if $DO_PUSH && ! $ALLOW_NO_SCAN; then
    die "no secret scanner (gitleaks/trufflehog) found — refusing to --push unscanned. Install one, or pass --allow-no-scan."
  fi
  warn "no secret scanner found — skipping scan (backstop disabled)"
}
run_secret_scan "$SRC_TREE"

# --------------------------------------------------------------------------
# 4. Land ONE squashed commit on turing-ce and (optionally) push
# --------------------------------------------------------------------------
PUB_TREE="$(mktemp -d)"
log "Preparing public repo working copy at $PUB_TREE"

if git ls-remote --exit-code "$REMOTE" >/dev/null 2>&1; then
  # Existing public repo: shallow-clone so we APPEND one commit per release
  # (public history = one commit per release, §XIX.3 / T359).
  git clone --depth 1 --branch "$CE_BRANCH" "$REMOTE" "$PUB_TREE" 2>/dev/null \
    || git clone --depth 1 "$REMOTE" "$PUB_TREE"
  # Replace the entire tracked tree with the freshly assembled snapshot.
  find "$PUB_TREE" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
else
  warn "remote $REMOTE has no refs yet — initialising a fresh public repo"
  git -C "$PUB_TREE" init -q -b "$CE_BRANCH"
  git -C "$PUB_TREE" remote add origin "$REMOTE"
fi

cp -a "$SRC_TREE/." "$PUB_TREE/"

cd "$PUB_TREE"
git checkout -q -B "$CE_BRANCH"
git add -A
if git diff --cached --quiet; then
  log "No changes versus current public tree — nothing to publish."
  exit 0
fi
git -c user.name="${GIT_AUTHOR_NAME:-Viglet Release}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-release@viglet.com}" \
    commit -q -m "$COMMIT_MSG"

FILE_COUNT="$(git ls-files | wc -l | tr -d ' ')"
log "Snapshot committed: \"$COMMIT_MSG\" ($FILE_COUNT files)"

if $DO_PUSH; then
  log "Pushing to $REMOTE ($CE_BRANCH)"
  git push origin "HEAD:$CE_BRANCH"
  log "Done. Public repo updated."
else
  warn "DRY RUN — not pushing. Re-run with --push to publish."
  log "Inspect the assembled snapshot at: $PUB_TREE"
  KEEP_TEMP=true   # leave the dry-run tree for inspection
fi
