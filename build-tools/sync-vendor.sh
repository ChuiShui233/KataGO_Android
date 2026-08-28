#!/usr/bin/env bash
set -euo pipefail
# Sync vendor copies from upstream (arm64-v8a super-repo)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fetch_and_replace() {
  local name="$1"
  local url="$2"
  local dest="$3"
  echo "=== sync $name ==="
  echo "url: $url"
  echo "dest: $dest"
  tmp=$(mktemp -d)
  zip="$tmp/src.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o "$zip" "$url" || { echo "[warn] curl failed for $name"; rm -rf "$tmp"; return 0; }
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$zip" "$url" || { echo "[warn] wget failed for $name"; rm -rf "$tmp"; return 0; }
  else
    echo "[warn] need curl or wget for $name"
    rm -rf "$tmp"
    return 0
  fi
  # replace dest
  rm -rf "$dest"
  mkdir -p "$dest"
  # unzip: archive has top-level dir like KataGo-main
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip" -d "$tmp"
    top=$(find "$tmp" -maxdepth 1 -type d -name "*-main" | head -n1)
    if [ -z "$top" ]; then top=$(find "$tmp" -maxdepth 1 -type d | tail -n1); fi
    if [ -d "$top" ]; then
      # move content
      shopt -s dotglob 2>/dev/null || true
      mv "$top"/* "$dest"/ 2>/dev/null || cp -r "$top"/* "$dest"/
      shopt -u dotglob 2>/dev/null || true
    fi
  else
    echo "[warn] unzip not found, keep zip at $zip"
  fi
  rm -rf "$tmp"
  echo "[done] $name -> $dest"
}

# KataGo main
fetch_and_replace "KataGo" "https://github.com/lightvector/KataGo/archive/refs/heads/main.zip" "$ROOT/KataGo"

# clvk main
fetch_and_replace "clvk" "https://github.com/kpet/clvk/archive/refs/heads/main.zip" "$ROOT/vulkan-build/clvk"

# OpenCL-Headers main
fetch_and_replace "OpenCL-Headers" "https://github.com/KhronosGroup/OpenCL-Headers/archive/refs/heads/main.zip" "$ROOT/build-tools/opencl-headers"

echo "=== done ==="
echo "review changes:"
git status --short || true
echo "if ok: git add -A && git commit -m \"vendor: sync upstream main\""
