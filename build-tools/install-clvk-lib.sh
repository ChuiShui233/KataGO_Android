#!/usr/bin/env bash
# Install clvk libOpenCL.so into jniLibs
# Usage:
#   ./install-clvk-lib.sh /path/to/libOpenCL-clvk-arm64-v8a.zip
#   ./install-clvk-lib.sh /path/to/libOpenCL.so
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# support both KataGO_Android and legacy kataA
if [ -d "$ROOT/KataGO_Android" ]; then DEST="$ROOT/KataGO_Android/app/src/main/jniLibs/arm64-v8a/libOpenCL.so"
else DEST="$ROOT/kataA/app/src/main/jniLibs/arm64-v8a/libOpenCL.so"; fi

usage() {
    echo "usage: $0 <artifact.zip | libOpenCL.so>" >&2
    exit 1
}

[ $# -eq 1 ] || usage
SRC="$1"
[ -e "$SRC" ] || { echo "not found: $SRC" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [[ "$SRC" == *.zip ]]; then
    unzip -q -o "$SRC" -d "$TMP"
    SO="${TMP%/}/libOpenCL.so"
    [ -e "$SO" ] || { echo "artifact zip does not contain libOpenCL.so" >&2; exit 1; }
else
    SO="$SRC"
fi

# basic ELF check
if ! head -c4 "$SO" | grep -q $'\x7fELF'; then
    echo "not an ELF file: $SO" >&2
    exit 1
fi

mkdir -p "$(dirname "$DEST")"
cp -f "$SO" "$DEST"
echo "installed: $DEST"
size() { ls -l "$1" | awk '{print $5}'; }
echo "size: $(size "$DEST") bytes"

echo
if [ -d "$ROOT/KataGO_Android" ]; then echo "next: cd $ROOT/KataGO_Android && ./build.sh assembleDebug"
else echo "next: cd $ROOT/kataA && ./build.sh assembleDebug"; fi
