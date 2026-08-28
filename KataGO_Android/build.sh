#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

TASK=${1:-assembleDebug}

# resolve gradle binary
if [ -n "${GRADLE:-}" ] && [ -x "$GRADLE" ]; then
  GRADLE_BIN="$GRADLE"
elif [ -x "./gradlew" ]; then
  GRADLE_BIN="./gradlew"
else
  GRADLE_BIN="/media/sd/Kata-android/gradle-8.11.1/bin/gradle"
fi

# fix for aapt2 on aarch64 host
if [ -d "/opt/x86_64-sysroot/lib/x86_64-linux-gnu" ]; then
  export LD_LIBRARY_PATH="/opt/x86_64-sysroot/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
fi

echo "[build.sh] building $TASK for arm64-v8a only (abiFilters=arm64-v8a)"

exec "$GRADLE_BIN" "$TASK" --no-daemon --console=plain
