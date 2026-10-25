#!/usr/bin/env bash
# Collects everything a play-tester needs into one folder (default: build/playtest):
#   report.md      bots playing every scored game (app/src/test/.../playtest)
#   panel/*.png    the control panel as users see it (Paparazzi, landscape phone)
#   *.txt          focused probes (Simon trace, lava camper, accidental starts): see TraceTest.kt
#   scenes/*.png   every scene and season rendered from the real shaders (headless WebGL2)
#   games/*.png    key moments of each game, rendered from the real game shaders
# Usage: tools/playtest/run.sh [output dir]
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="${1:-build/playtest}"
mkdir -p "$OUT/panel" "$OUT/scenes" "$OUT/games"

./gradlew :app:testDebugUnitTest --tests '*PlaytestTest*' --tests '*TraceTest*' --tests '*PanelScreenshotTest*' --console=plain -q
cp app/build/playtest/report.md app/build/playtest/*.txt "$OUT/"

# Paparazzi names images by hash; copy them out under their snapshot names.
python3 - "$OUT/panel" <<'PY'
import glob, json, re, shutil, sys, os
base = "app/build/reports/paparazzi/debug"
for run in glob.glob(f"{base}/runs/*.js"):
    text = open(run).read()
    for entry in json.loads(re.sub(r"^window\.runs\[[^]]*\] = ", "", text.strip()).rstrip(";")):
        shutil.copy(os.path.join(base, entry["file"]), os.path.join(sys.argv[1], entry["name"] + ".png"))
PY

# Scene previews need Playwright + Chromium (preinstalled in Claude Code cloud sessions).
PW="${PW:-$(npm root -g 2>/dev/null)/playwright}"
if [ -d "$PW" ]; then
  PW="$PW" NODE_PATH="$(dirname "$PW")" node tools/shader-preview/check.js app/src/main/assets/shaders "$OUT/scenes" > "$OUT/scenes/check.log" 2>&1 || echo "check.js failed, see $OUT/scenes/check.log"
  PW="$PW" NODE_PATH="$(dirname "$PW")" node tools/shader-preview/check-seasons.js app/src/main/assets/shaders "$OUT/scenes" > "$OUT/scenes/check-seasons.log" 2>&1 || echo "check-seasons.js failed, see $OUT/scenes/check-seasons.log"
  PW="$PW" NODE_PATH="$(dirname "$PW")" node tools/shader-preview/check-scary.js app/src/main/assets/shaders "$OUT/scenes" > "$OUT/scenes/check-scary.log" 2>&1 || echo "check-scary.js failed, see $OUT/scenes/check-scary.log"
  PW="$PW" NODE_PATH="$(dirname "$PW")" node tools/playtest/game-frames.js app/src/main/assets/shaders app/build/playtest/frames "$OUT/games" > "$OUT/games.log" 2>&1 || echo "game-frames.js failed, see $OUT/games.log"
else
  echo "Playwright not found; skipping scene previews (set PW=/path/to/playwright)."
fi
echo "Play-test material in $OUT"
ls -R "$OUT" | head -80
