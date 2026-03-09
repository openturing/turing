#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="http://localhost:2700"
API_KEY=""
SITE="wknd"
LOCALE="en_US"
SPECS_FILE="$SCRIPT_DIR/sample_wknd_field_specs.json"
MODE_FLAG=""

usage() {
  echo "Usage: $(basename "$0") --api-key KEY [OPTIONS]"
  echo ""
  echo "Required:"
  echo "  --api-key KEY        Turing API key for authentication"
  echo ""
  echo "Optional:"
  echo "  --base-url URL       Base URL of the Turing server (default: http://localhost:2700)"
  echo "  --site NAME          Turing site name (default: wknd)"
  echo "  --locale LOCALE      Locale for indexing (default: en_US)"
  echo "  --specs-file PATH    Path to field specs JSON file (default: sample_wknd_field_specs.json)"
  echo "  --dry-run            Validate without indexing"
  echo ""
  echo "Example:"
  echo "  $(basename "$0") --api-key mykey123"
  echo "  $(basename "$0") --api-key mykey123 --base-url http://myserver:2700 --site mysite --locale pt_BR"
}

if [ $# -eq 0 ]; then
  usage
  exit 0
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --base-url)   BASE_URL="$2";   shift 2 ;;
    --api-key)    API_KEY="$2";    shift 2 ;;
    --site)       SITE="$2";       shift 2 ;;
    --locale)     LOCALE="$2";     shift 2 ;;
    --specs-file) SPECS_FILE="$2"; shift 2 ;;
    --dry-run)    MODE_FLAG="--dry-run"; shift ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

if [ -z "$API_KEY" ]; then
  echo "Error: --api-key is required."
  exit 1
fi

HAS_SPECS=""
if [ -f "$SPECS_FILE" ]; then
  HAS_SPECS="1"
fi

if ! command -v python &>/dev/null; then
  echo "Python not found in PATH."
  exit 1
fi

echo "Running Turing indexing test..."
echo "Base URL:  $BASE_URL"
echo "API Key:   $API_KEY"
echo "Site:      $SITE"
echo "Locale:    $LOCALE"
echo "JSON:      $SCRIPT_DIR/sample_wknd_document.json"
if [ -n "$HAS_SPECS" ]; then echo "Specs:     $SPECS_FILE"; else echo "Specs:     [none]"; fi
echo ""

PYTHON_ARGS=(
  "$SCRIPT_DIR/turing_index_test.py"
  --base-url "$BASE_URL"
  --header "Key: $API_KEY"
  --site "$SITE"
  --locale "$LOCALE"
  --json-file "$SCRIPT_DIR/sample_wknd_document.json"
)

if [ -n "$HAS_SPECS" ]; then
  PYTHON_ARGS+=(--specs-file "$SPECS_FILE")
fi

if [ -n "$MODE_FLAG" ]; then
  PYTHON_ARGS+=("$MODE_FLAG")
fi

python "${PYTHON_ARGS[@]}"
EXIT_CODE=$?

if [ "$EXIT_CODE" -ne 0 ]; then
  echo ""
  echo "Indexing failed. Exit code: $EXIT_CODE"
  exit $EXIT_CODE
fi

echo ""
echo "Indexing submitted successfully."
exit 0
