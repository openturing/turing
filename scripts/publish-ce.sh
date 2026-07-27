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
#   scripts/publish-ce.sh [--ref <tag|sha>] [--branch <name>] [--remote <url>]
#                         [--push] [--allow-no-scan] [--keep-temp]
#
#   --ref <tag|sha>   Release point to publish. Default: HEAD.
#                     If it is a `v*` tag the version label is derived from it;
#                     otherwise the label comes from <version> in pom.xml.
#   --branch <name>   turing-ce branch to publish to. Default ($CE_BRANCH env, or
#                     derived): MIRRORS the source branch name — a run on `2026.3`
#                     publishes to a `2026.3` branch on turing-ce, creating it if
#                     it does not exist yet. (Tags/SHAs with no branch → `main`.)
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
#   CE_BRANCH         Force the target branch (overridden by --branch).
#   CE_NOTES_OUT      Path to write the extracted CHANGELOG section to, so CI can
#                     reuse it verbatim as the GitHub Release body (T364). The
#                     same text is embedded in the squash-commit message.
#   GH_PKG_TOKEN      Used by CI (T360); not consumed here — auth is via the
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
TARGET_BRANCH="${CE_BRANCH:-}"   # turing-ce branch to publish to (empty = derive)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)           REF="$2"; shift 2 ;;
    --branch)        TARGET_BRANCH="$2"; shift 2 ;;
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

# --------------------------------------------------------------------------
# 0b. Build the human-readable release notes (T364)
#
# The public repo carries ONE squashed commit per release. Without a body that
# commit ("Release vX.Y.Z") destroys every trace of WHAT changed — the log reads
# as an abandoned drop-site, `--generate-notes` on the GitHub Release has no
# commits to summarise, and an outside contributor has no way to see whether
# their merged PR shipped. So the release section of the public CHANGELOG.md is
# lifted into the commit body AND exported for the GitHub Release notes.
# --------------------------------------------------------------------------
RELEASE_NOTES=""
if git cat-file -e "$REF:CHANGELOG.md" 2>/dev/null; then
  CHANGELOG_TMP="$(mktemp)"
  git show "$REF:CHANGELOG.md" > "$CHANGELOG_TMP"
  # Shared with publish.yml so the commit body and the GitHub Release match.
  RELEASE_NOTES="$(bash "$SCRIPT_DIR/changelog-section.sh" "$VERSION" "$CHANGELOG_TMP" || true)"
  rm -f "$CHANGELOG_TMP"
fi

if [[ -z "${RELEASE_NOTES//[[:space:]]/}" ]]; then
  warn "CHANGELOG.md has no section for $VERSION (or ${VERSION%.*}) — the release commit will have no change list. Add one before publishing."
  RELEASE_NOTES="See https://docs.viglet.org/turing/ for documentation."
fi

COMMIT_MSG="$(cat <<EOF
Release v${VERSION}

${RELEASE_NOTES}

---
Published from the Viglet Turing ES internal repository as a reviewed source
snapshot. Development history is squashed into this single commit by design;
issues and pull requests are handled here on turing-ce and ship in a following
release. See CONTRIBUTING.md.
EOF
)"

# Export the notes so CI can attach them verbatim to the GitHub Release
# (publish.yml: gh release create --notes-file). Optional: unset in local runs.
if [[ -n "${CE_NOTES_OUT:-}" ]]; then
  printf '%s\n' "$RELEASE_NOTES" > "$CE_NOTES_OUT"
  log "Release notes written to $CE_NOTES_OUT"
fi

# Append a section to the GitHub Actions job summary when running in CI.
summary() {
  [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] || return 0
  printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"
}

# Target branch on turing-ce: mirror the source BRANCH name (e.g. a run on
# `2026.3` publishes to a `2026.3` branch on turing-ce). Explicit --branch /
# CE_BRANCH wins; otherwise derive from the ref.
if [[ -z "$TARGET_BRANCH" ]]; then
  if [[ "$REF" == "HEAD" ]]; then
    TARGET_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"
  elif git rev-parse --verify --quiet "refs/heads/$REF" >/dev/null; then
    TARGET_BRANCH="$REF"
  else
    TARGET_BRANCH="main"   # ref is a tag/sha with no obvious branch
  fi
fi
[[ "$TARGET_BRANCH" == "HEAD" ]] && TARGET_BRANCH="main"   # detached HEAD guard

log "Publishing ref $REF ($REF_SHA) as \"$COMMIT_MSG\" -> turing-ce branch '$TARGET_BRANCH'"

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
  || die "ce-exclude.txt (T356 boundary manifest) missing at ref $REF — refusing to publish without the rule. (This ref predates Block P; publish a ref/tag that contains ce-exclude.txt, e.g. the current release branch.)"
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

summary "## CE snapshot — v${VERSION}"
summary ""
summary "| | |"
summary "|---|---|"
summary "| Source ref | \`${REF}\` (\`${REF_SHA}\`) |"
summary "| Target | \`openviglet/turing-ce\` branch \`${TARGET_BRANCH}\` |"
summary "| Mode | $($DO_PUSH && echo '**push**' || echo 'dry run') |"
summary "| Paths stripped by \`ce-exclude.txt\` | ${removed} |"
summary ""
if [[ -n "$DENIED" ]]; then
  summary "<details><summary>Stripped paths (${removed})</summary>"
  summary ""
  summary '```'
  summary "$DENIED"
  summary '```'
  summary ""
  summary "</details>"
  summary ""
fi

# Public README: a curated README.ce.adoc replaces the internal README.adoc
# (§XIX.2 / T359). The internal one is already excluded by the denylist.
if [[ -f "$SRC_TREE/README.ce.adoc" ]]; then
  mv -f "$SRC_TREE/README.ce.adoc" "$SRC_TREE/README.adoc"
  log "Promoted README.ce.adoc -> README.adoc"
else
  warn "No README.ce.adoc at ref — public tree has no README yet (T359 curates it)"
fi

# --------------------------------------------------------------------------
# 3. Secret-scan gate — fails CLOSED (T358, §XIX.4)
#
# Two layers run over the *assembled* tree (after the denylist already removed
# the known-private paths):
#   a) a built-in, always-on pattern scan for things the entropy scanners do
#      NOT know about — internal hostnames + customer names — plus a few
#      high-signal secret markers (PEM blocks, cloud keys). Patterns live in an
#      external, itself-denylisted file so the maintainer can curate names
#      without editing this script.
#   b) gitleaks / trufflehog for entropy + known credential formats. A real run
#      of one of these is REQUIRED before --push (override: --allow-no-scan).
# Any hit in either layer aborts the publish. Matches are REDACTED in output so
# a secret never re-leaks through a CI log.
# --------------------------------------------------------------------------
PATTERN_FILE="${CE_DENY_PATTERNS:-$PROJECT_ROOT/scripts/ce-deny-patterns.txt}"

redact() {
  # Keep the file:line locator and a short head/tail of the match; mask the rest
  # so the scanner output itself can't leak the secret into a CI log.
  awk -F: '{
    loc = $1 ":" $2;
    val = substr($0, index($0, $3));
    if (length(val) > 12) val = substr(val,1,4) "…REDACTED…" substr(val,length(val)-3);
    print "    " loc "  " val;
  }'
}

scan_builtin_patterns() {
  local tree="$1"
  if [[ ! -f "$PATTERN_FILE" ]]; then
    warn "pattern file $PATTERN_FILE not found — skipping built-in host/customer-name scan"
    return 0
  fi
  # Build grep -e args from the file, dropping blank lines and '#' comments
  # (grep -f would treat comment lines as literal patterns).
  local -a pats=()
  local line npat=0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"                       # tolerate CRLF
    [[ -z "${line//[[:space:]]/}" ]] && continue
    [[ "${line#"${line%%[![:space:]]*}"}" == \#* ]] && continue
    pats+=(-e "$line")
    npat=$((npat + 1))
  done < "$PATTERN_FILE"

  if [[ $npat -eq 0 ]]; then
    warn "pattern file $PATTERN_FILE has no active patterns — built-in scan is a no-op"
    return 0
  fi

  log "Built-in pattern scan ($npat pattern(s) from ${PATTERN_FILE##*/})"
  local hits
  hits="$(grep -rInE -I "${pats[@]}" "$tree" 2>/dev/null \
          | sed "s#^$tree/##" || true)"
  if [[ -n "$hits" ]]; then
    warn "built-in scan matched forbidden patterns (internal host / customer name / secret marker):"
    printf '%s\n' "$hits" | redact >&2
    die "secret scan FAILED — assembled tree matched ce-deny-patterns.txt; aborting (§XIX.4)"
  fi
}

run_secret_scan() {
  local tree="$1"

  # (a) always-on built-in scan
  scan_builtin_patterns "$tree"

  # (b) entropy / known-format scanners
  local scanned_external=false
  if command -v gitleaks >/dev/null 2>&1; then
    log "Secret scan (gitleaks) over assembled tree"
    local gl=(detect --no-git --source "$tree" --redact)
    # Allowlist known false positives (Liquibase/Hibernate object names, etc.)
    [[ -f "$PROJECT_ROOT/scripts/gitleaks.toml" ]] && gl+=(--config "$PROJECT_ROOT/scripts/gitleaks.toml")
    gitleaks "${gl[@]}" \
      || die "secret scan FAILED — gitleaks found a secret; aborting (§XIX.4)"
    scanned_external=true
  fi
  if command -v trufflehog >/dev/null 2>&1; then
    log "Secret scan (trufflehog) over assembled tree"
    trufflehog filesystem "$tree" --fail --no-update --no-verification \
      || die "secret scan FAILED — trufflehog found a secret; aborting (§XIX.4)"
    scanned_external=true
  fi

  # The denylist is the policy; the entropy scanner is the backstop (§XIX.4).
  # A real gitleaks/trufflehog run is mandatory before pushing to a public,
  # indexed, un-deletable mirror — refuse otherwise unless explicitly overridden.
  if ! $scanned_external; then
    if $DO_PUSH && ! $ALLOW_NO_SCAN; then
      die "no entropy scanner (gitleaks/trufflehog) installed — refusing to --push. Install one, or pass --allow-no-scan to publish on the built-in pattern scan alone."
    fi
    warn "no entropy scanner installed — relying on the built-in pattern scan only (backstop disabled)"
  fi
}
run_secret_scan "$SRC_TREE"

# --------------------------------------------------------------------------
# 4. Land ONE squashed commit on turing-ce and (optionally) push
# --------------------------------------------------------------------------
PUB_TREE="$(mktemp -d)"
log "Preparing public repo working copy at $PUB_TREE (branch: $TARGET_BRANCH)"

if git ls-remote --exit-code "$REMOTE" >/dev/null 2>&1; then
  if git ls-remote --exit-code --heads "$REMOTE" "$TARGET_BRANCH" >/dev/null 2>&1; then
    # Target branch already exists → APPEND one commit (one commit per release,
    # §XIX.3 / T359).
    git clone --depth 1 --branch "$TARGET_BRANCH" "$REMOTE" "$PUB_TREE"
    find "$PUB_TREE" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  else
    # Repo exists but this branch does not → create it fresh as an ORPHAN, so it
    # does not inherit another branch's history (keeps the snapshot leak-proof
    # and the one-commit-per-release model on the new branch).
    log "Branch '$TARGET_BRANCH' not found on turing-ce — creating it"
    git clone --depth 1 "$REMOTE" "$PUB_TREE"
    git -C "$PUB_TREE" checkout -q --orphan "$TARGET_BRANCH"
    git -C "$PUB_TREE" rm -rfq --cached . >/dev/null 2>&1 || true
    find "$PUB_TREE" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  fi
else
  warn "remote $REMOTE has no refs yet — initialising a fresh public repo"
  git -C "$PUB_TREE" init -q -b "$TARGET_BRANCH"
  git -C "$PUB_TREE" remote add origin "$REMOTE"
fi

cp -a "$SRC_TREE/." "$PUB_TREE/"

cd "$PUB_TREE"
git add -A
if git diff --cached --quiet; then
  log "No changes versus current public tree — nothing to publish."
  exit 0
fi
git -c user.name="${GIT_AUTHOR_NAME:-Viglet Release}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-release@viglet.com}" \
    commit -q -m "$COMMIT_MSG"

FILE_COUNT="$(git ls-files | wc -l | tr -d ' ')"
log "Snapshot committed: \"Release v${VERSION}\" ($FILE_COUNT files)"

summary "✅ Secret scan clean · **${FILE_COUNT}** files in the public tree."
summary ""
summary "<details><summary>Release notes that will ship with this snapshot</summary>"
summary ""
summary "$RELEASE_NOTES"
summary ""
summary "</details>"

if $DO_PUSH; then
  log "Pushing to $REMOTE ($TARGET_BRANCH)"
  git push origin "HEAD:$TARGET_BRANCH"
  log "Done. Public repo updated."
else
  warn "DRY RUN — not pushing. Re-run with --push to publish."
  log "Inspect the assembled snapshot at: $PUB_TREE"
  KEEP_TEMP=true   # leave the dry-run tree for inspection
fi
