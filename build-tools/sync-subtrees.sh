#!/usr/bin/env bash
set -euo pipefail
# Sync subtree remotes
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== sync KataGo (lightvector/KataGo main) ==="
git subtree pull --prefix=KataGo https://github.com/lightvector/KataGo.git main --squash -m "subtree: pull KataGo main $(date +%Y-%m-%d)" || \
  echo "[warn] KataGo pull failed (offline?)"

echo "=== sync clvk (kpet/clvk main) ==="
git subtree pull --prefix=vulkan-build/clvk https://github.com/kpet/clvk.git main --squash -m "subtree: pull clvk main $(date +%Y-%m-%d)" || \
  echo "[warn] clvk pull failed"

# clvk external submodules
if [ -f "vulkan-build/clvk/.gitmodules" ]; then
  echo "=== init clvk external submodules ==="
  git -C vulkan-build/clvk submodule update --init --recursive || echo "[warn] submodule init failed (offline?)"
  git add -f vulkan-build/clvk/external || true
fi

echo "=== sync OpenCL-Headers (KhronosGroup) ==="
git subtree pull --prefix=build-tools/opencl-headers https://github.com/KhronosGroup/OpenCL-Headers.git main --squash -m "subtree: pull OpenCL-Headers main $(date +%Y-%m-%d)" || \
  echo "[warn] OpenCL-Headers pull failed"

echo "=== done ==="
git status --short
