#!/usr/bin/env bash
# --------------------------------------------------------------------------
# package-examples.sh
# Builds export ZIPs for each turing-example app.
# Each ZIP follows the Turing import format:
#   - export.json           (SN site configuration)
#   - *_content.json        (indexed content, if present)
#   - app/                  (compiled SPA template)
#
# Output: target/examples/{name}.zip
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
EXAMPLES_DIR="$PROJECT_ROOT/frontend/apps/marketplace"
OUTPUT_DIR="$PROJECT_ROOT/target/examples"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

echo "==> Packaging example site exports..."

for name in mythical-creatures space-missions vinyl-records; do
    echo ""
    echo "--- $name ---"

    EXAMPLE="$EXAMPLES_DIR/$name"
    EXPORT_SRC="$EXAMPLE/export"
    APP_SRC="$EXAMPLE/app"
    WORK="$OUTPUT_DIR/${name}-work"

    if [ ! -f "$EXPORT_SRC/export.json" ]; then
        echo "   Skipping $name: no export/export.json found"
        continue
    fi

    mkdir -p "$WORK"

    # Copy export.json and content files
    cp "$EXPORT_SRC/export.json" "$WORK/"
    for f in "$EXPORT_SRC"/*_content.json; do
        [ -f "$f" ] && cp "$f" "$WORK/"
    done

    # Build the SPA app if needed
    if [ -f "$APP_SRC/package.json" ]; then
        echo "   Building SPA..."
        (cd "$APP_SRC" && npm run compile 2>/dev/null) || true
    fi

    # Copy compiled SPA to app/ folder
    if [ -d "$APP_SRC/dist" ]; then
        echo "   Including SPA template in app/"
        mkdir -p "$WORK/app"
        cp -r "$APP_SRC/dist/"* "$WORK/app/"
    fi

    # Create ZIP
    echo "   Creating ${name}.zip"
    (cd "$WORK" && zip -r "$OUTPUT_DIR/${name}.zip" . -q)

    # Cleanup
    rm -rf "$WORK"
    echo "   Done: target/examples/${name}.zip"
done

echo ""
echo "==> Example packages created in target/examples/"
