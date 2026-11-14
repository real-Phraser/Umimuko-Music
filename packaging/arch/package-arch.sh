#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build/compose/binaries/main/app"
OUT_DIR="$PROJECT_DIR/build/compose/binaries/main/arch"

APP_SRC=""
if [ -d "$BUILD_DIR/Umimuko Music" ]; then
    APP_SRC="$BUILD_DIR/Umimuko Music"
elif [ -d "$BUILD_DIR/Umimuko Music.app" ]; then
    APP_SRC="$BUILD_DIR/Umimuko Music.app"
fi

if [ -z "$APP_SRC" ]; then
    echo "App distributable not found. Run ./gradlew createDistributable first."
    exit 1
fi

mkdir -p "$OUT_DIR"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Staging Arch Linux package files..."
cp "$SCRIPT_DIR/PKGBUILD" "$WORK_DIR/"
cp "$SCRIPT_DIR/umimuko-music.desktop" "$WORK_DIR/"
cp "$SCRIPT_DIR/../umimuko.png" "$WORK_DIR/"
cp -a "$APP_SRC" "$WORK_DIR/Umimuko Music"

cd "$WORK_DIR"
if command -v makepkg >/dev/null 2>&1; then
    echo "Building Arch package using makepkg..."
    makepkg -f --nodeps
    mv *.pkg.tar.zst "$OUT_DIR/"
    echo "Arch package generated at $OUT_DIR"
else
    echo "makepkg not found (building outside Arch Linux). Packaging tarball with PKGBUILD..."
    ARCH_PKG_NAME="umimuko-music-0.23.0-1-x86_64.pkg.tar.zst"
    if command -v zstd >/dev/null 2>&1; then
        tar --zstd -cf "$OUT_DIR/$ARCH_PKG_NAME" -C "$WORK_DIR" .
    else
        tar -czf "$OUT_DIR/umimuko-music-0.23.0-1.tar.gz" -C "$WORK_DIR" .
    fi
    echo "Arch package bundle created in $OUT_DIR"
fi
