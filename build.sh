#!/usr/bin/env bash
# Собирает Golub.apk без Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Нужен ANDROID_HOME с platforms;android-34 и build-tools;35.0.0.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?set ANDROID_HOME}"
BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-34/android.jar"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" \
  --manifest app/AndroidManifest.xml -A app/assets \
  --java "$OUT/gen" "$OUT/res.zip"

javac --release 8 -nowarn -cp "$JAR" -d "$OUT/classes" \
  $(find app/src "$OUT/gen" -name '*.java') 2>&1 | grep -v 'release 8' || true
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -u ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=${KEYSTORE:-golub.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias golub -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass golub123 -keypass golub123 \
    -dname "CN=Golub, O=Golub, C=RU" 2>/dev/null
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:golub123 --key-pass pass:golub123 \
  --out Golub.apk "$OUT/aligned.apk"
"$BT/apksigner" verify Golub.apk
echo "OK: $(pwd)/Golub.apk"
