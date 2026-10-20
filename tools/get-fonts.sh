#!/usr/bin/env bash
# دانلود فونت وزیرمتن (مجوز SIL OFL) به پوشه‌ی منابع برنامه
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
BASE="https://raw.githubusercontent.com/rastikerdar/vazirmatn/master/fonts/ttf"
mkdir -p "$DIR"
[ -s "$DIR/vazirmatn_regular.ttf" ] || curl -fsSL "$BASE/Vazirmatn-Regular.ttf" -o "$DIR/vazirmatn_regular.ttf"
[ -s "$DIR/vazirmatn_bold.ttf" ]    || curl -fsSL "$BASE/Vazirmatn-Bold.ttf"    -o "$DIR/vazirmatn_bold.ttf"
echo "فونت‌ها آماده شدند."
