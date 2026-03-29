#!/usr/bin/env bash
# --------------------------------------------------------------------------
# Start Viglet Turing ES.
#
# Usage:  ./bin/turing.sh [JVM options...] [-- Spring options...]
#
# Examples:
#   ./bin/turing.sh
#   ./bin/turing.sh -Xmx2g
#   ./bin/turing.sh -Xmx2g -- --server.port=2701
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

JAR="$BASE_DIR/server/viglet-turing.jar"
PROPS="$BASE_DIR/server/viglet-turing.properties"

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found." >&2
  exit 1
fi

# Separate JVM args from Spring args (split on --)
JVM_ARGS=()
SPRING_ARGS=()
AFTER_SEPARATOR=false
for arg in "$@"; do
  if [ "$arg" = "--" ]; then
    AFTER_SEPARATOR=true
    continue
  fi
  if [ "$AFTER_SEPARATOR" = true ]; then
    SPRING_ARGS+=("$arg")
  else
    JVM_ARGS+=("$arg")
  fi
done

# Build the command
CMD=(java)
CMD+=(-Xmx1g -Xms1g)
CMD+=("${JVM_ARGS[@]+"${JVM_ARGS[@]}"}")
CMD+=(-jar "$JAR")

if [ -f "$PROPS" ]; then
  CMD+=(--spring.config.additional-location="file:$PROPS")
fi

CMD+=("${SPRING_ARGS[@]+"${SPRING_ARGS[@]}"}")

echo "Starting Viglet Turing ES..."
echo "  JAR:    $JAR"
[ -f "$PROPS" ] && echo "  Config: $PROPS"
echo ""

exec "${CMD[@]}"
