#!/usr/bin/env bash
# --------------------------------------------------------------------------
# package-dist.sh
# Builds Turing ES from source and creates distribution packages:
#   - turing-install.zip    (portable, works on Linux and Windows)
#   - Turing-ES.AppImage    (Linux self-contained executable)
#   - turing-install.exe    (Windows installer, if makensis is available)
#
# Usage:  ./scripts/package-dist.sh [--skip-build]
# --------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true; shift ;;
    *)            echo "Unknown option: $1"; exit 1 ;;
  esac
done

cd "$PROJECT_ROOT"

# --------------- Build ---------------
if [ "$SKIP_BUILD" = false ]; then
  echo "==> Building Turing ES..."
  ./mvnw clean install -DskipTests -Dgpg.skip=true -Dturing.open-browser=false
fi

# =====================================================================
#  Shared: copy artifacts into a staging area
# =====================================================================
echo "==> Staging artifacts..."

DIST_NAME="turing-install"
STAGE="${PROJECT_ROOT}/target/dist-stage/${DIST_NAME}"
rm -rf "$STAGE"
mkdir -p "$STAGE/server"
mkdir -p "$STAGE/utils"
mkdir -p "$STAGE/bin"

# Server JAR
cp "turing-app/target/viglet-turing.jar" "$STAGE/server/"

# Utils (Solr configs, scripts)
if [ -f "turing-utils/target/turing-utils.zip" ]; then
  (cd "$STAGE/utils" && unzip -qo "$PROJECT_ROOT/turing-utils/target/turing-utils.zip")
elif [ -d "turing-utils/src/main/resources" ]; then
  cp -r turing-utils/src/main/resources/* "$STAGE/utils/"
fi

# Scripts
cp "$SCRIPT_DIR/bin/turing.sh"            "$STAGE/bin/"
cp "$SCRIPT_DIR/bin/turing.bat"           "$STAGE/bin/"
chmod +x "$STAGE/bin/"*.sh 2>/dev/null || true

# Config and docs
cp "$SCRIPT_DIR/config/viglet-turing.properties" "$STAGE/server/"
cp "$SCRIPT_DIR/config/README.txt" "$STAGE/"

# Example exports
echo "==> Packaging example exports..."
chmod +x "$SCRIPT_DIR/package-examples.sh" 2>/dev/null || true
"$SCRIPT_DIR/package-examples.sh"
EXAMPLES_OUT="${PROJECT_ROOT}/target/examples"
mkdir -p "$STAGE/examples"
for name in mythical-creatures space-missions vinyl-records; do
  if [ -f "$EXAMPLES_OUT/${name}.zip" ]; then
    cp "$EXAMPLES_OUT/${name}.zip" "$STAGE/examples/"
  fi
done

# =====================================================================
#  1. Zip
# =====================================================================
ZIP_OUTPUT="${PROJECT_ROOT}/target/${DIST_NAME}.zip"
echo "==> Creating zip: $ZIP_OUTPUT"
mkdir -p "$(dirname "$ZIP_OUTPUT")"
(cd "$(dirname "$STAGE")" && zip -r "$ZIP_OUTPUT" "$(basename "$STAGE")")
echo "    $(du -h "$ZIP_OUTPUT" | cut -f1)"

# =====================================================================
#  2. AppImage
# =====================================================================
echo "==> Preparing AppImage..."
APPDIR="${PROJECT_ROOT}/target/Turing-ES.AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/lib/turing"

# Copy staged artifacts into AppDir
cp -r "$STAGE/server" "$APPDIR/usr/lib/turing/"
cp -r "$STAGE/utils"  "$APPDIR/usr/lib/turing/"

# AppRun entry point
cp "$SCRIPT_DIR/installer/appimage/AppRun" "$APPDIR/AppRun"
chmod +x "$APPDIR/AppRun"

# Desktop file and icon
cp "$SCRIPT_DIR/installer/appimage/turing.desktop" "$APPDIR/turing.desktop"
cp "$SCRIPT_DIR/installer/appimage/turing.png" "$APPDIR/turing.png"

# Get appimagetool
ARCH="$(uname -m)"
APPIMAGETOOL="${PROJECT_ROOT}/target/appimagetool"
if [ ! -f "$APPIMAGETOOL" ]; then
  echo "    Downloading appimagetool..."
  TOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH}.AppImage"
  curl -fSL -o "$APPIMAGETOOL" "$TOOL_URL"
  chmod +x "$APPIMAGETOOL"
fi

APPIMAGE_OUTPUT="${PROJECT_ROOT}/target/Turing-ES-${ARCH}.AppImage"
ARCH="$ARCH" "$APPIMAGETOOL" "$APPDIR" "$APPIMAGE_OUTPUT"
chmod +x "$APPIMAGE_OUTPUT"
echo "    $(du -h "$APPIMAGE_OUTPUT" | cut -f1)"

# =====================================================================
#  3. Windows Installer (NSIS) — only if makensis is available
# =====================================================================
if command -v makensis &>/dev/null; then
  EXE_OUTPUT="${PROJECT_ROOT}/target/${DIST_NAME}.exe"
  echo "==> Creating Windows installer: $EXE_OUTPUT"
  makensis -V2 -DOUTDIR="../../target" -DSTAGE="$STAGE" "$SCRIPT_DIR/installer/turing.nsi"
  echo "    $(du -h "$EXE_OUTPUT" | cut -f1)"
else
  echo "==> Skipping Windows installer (makensis not found)"
fi

# =====================================================================
echo ""
echo "==> All done. Outputs in target/:"
ls -lh "${PROJECT_ROOT}/target/${DIST_NAME}".{zip,exe} "${PROJECT_ROOT}/target/"Turing-ES-*.AppImage 2>/dev/null || true
