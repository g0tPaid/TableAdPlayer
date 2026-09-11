#!/usr/bin/env bash
set -euo pipefail
# Bootstrap a command-line Android SDK sufficient to assemble TableAdPlayer.
ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}"
ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
mkdir -p "$ROOT/cmdline-tools"
if [[ ! -x "$ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  tmp="$(mktemp -d)"
  curl -L --retry 4 -o "$tmp/tools.zip" "$ZIP_URL"
  python3 - "$tmp" <<'PY'
import sys, zipfile
from pathlib import Path
tmp = Path(sys.argv[1])
zipfile.ZipFile(tmp / "tools.zip").extractall(tmp / "unpack")
PY
  rm -rf "$ROOT/cmdline-tools/latest"
  mv "$tmp/unpack/cmdline-tools" "$ROOT/cmdline-tools/latest"
  chmod +x "$ROOT/cmdline-tools/latest/bin/"*
fi
export ANDROID_HOME="$ROOT"
export PATH="$ROOT/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
# AGP looks for platforms/android-37; the SDK package is android-37.0
if [[ -d "$ROOT/platforms/android-37.0" && ! -e "$ROOT/platforms/android-37" ]]; then
  ln -s android-37.0 "$ROOT/platforms/android-37"
fi
echo "ANDROID_HOME=$ANDROID_HOME"
echo "Write local.properties: sdk.dir=$ANDROID_HOME"
