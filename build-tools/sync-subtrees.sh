#!/usr/bin/env bash
set -euo pipefail
# Deprecated: super-repo uses vendor copy (see sync-vendor.sh)
# Kept for reference if you switch to subtree
echo "[info] super-repo is vendor copy mode"
echo "[info] use ./build-tools/sync-vendor.sh to sync from upstream zips"
echo "[info] or switch to subtree: git subtree add --prefix=KataGo https://github.com/lightvector/KataGo.git main --squash"
exit 0
