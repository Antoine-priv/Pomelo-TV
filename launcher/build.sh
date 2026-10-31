#!/usr/bin/env bash
# Builds Pomelo TV without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs: Android SDK (build-tools + a platform >= 30), JDK 11+.
# Output: ../apk/pomelo-tv.apk
# Lancé par compiler.sh, qui fournit la version et la clé de signature.
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
[ -d "$SDK" ] || { echo "Android SDK not found (set ANDROID_HOME)"; exit 1; }
BT=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -1)
OUT=build
DIST=../apk
echo "build-tools: $BT"
echo "platform:    $JAR"

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 24 --target-sdk-version 30 --version-code "$POMELO_VERSION_CODE" \
  --version-name "$POMELO_VERSION" --java "$OUT/gen" -o "$OUT/unsigned.apk" "$OUT/res.zip"
javac -nowarn --release 11 -classpath "$JAR" -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 24 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

"$BT/apksigner" sign --ks "$POMELO_KEYSTORE" --ks-pass "pass:$POMELO_KEYSTORE_PASS" \
  --ks-key-alias pomelo --out "$DIST/pomelo-tv.apk" "$OUT/aligned.apk"
rm -f "$DIST/pomelo-tv.apk.idsig"
echo "OK -> $(cd "$DIST" && pwd)/pomelo-tv.apk"
