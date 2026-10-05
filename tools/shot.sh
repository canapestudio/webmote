#!/bin/bash
# Captures the emulator screen as a Play Store phone screenshot (24-bit PNG, no alpha).
# Usage: tools/shot.sh <locale> <file name without extension>
# Needs Pillow in build/venv (see graphics.py) and the emulator at 1080x1920 (adb shell wm size 1080x1920).
set -euo pipefail
out="fastlane/metadata/android/$1/images/phoneScreenshots/$2.png"
adb -s emulator-5554 exec-out screencap -p > build/raw-shot.png
build/venv/bin/python - "$out" <<'EOF'
import sys
from PIL import Image
Image.open("build/raw-shot.png").convert("RGB").save(sys.argv[1], optimize=True)
EOF
echo "$out"
