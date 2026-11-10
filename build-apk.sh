#!/usr/bin/env bash
# Dependency-free command-line build. Requires JDK 17 and Android SDK Platform / Build Tools 36.
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
: "${sdk:?Set ANDROID_SDK_ROOT to the Android SDK}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${REELBACK_KEYSTORE:?Set REELBACK_KEYSTORE to your signing keystore}"
: "${REELBACK_STORE_PASSWORD:?Set REELBACK_STORE_PASSWORD}"
out="${1:-build/Reelback.apk}"
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$(dirname "$out")"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp"/{classes,dex,gen}
"$tools/aapt2" compile --dir app/src/main/res -o "$tmp/res.zip"
"$tools/aapt2" link -I "$platform" --manifest app/src/main/AndroidManifest.xml \
  --java "$tmp/gen" --min-sdk-version 30 --target-sdk-version 36 \
  -o "$tmp/base.apk" "$tmp/res.zip"
find app/src/main/java "$tmp/gen" -type f -name '*.java' > "$tmp/sources.txt"
javac --release 17 -encoding UTF-8 -classpath "$platform" -d "$tmp/classes" @"$tmp/sources.txt"
jar cf "$tmp/classes.jar" -C "$tmp/classes" .
"$tools/d8" --release --min-api 30 --lib "$platform" --output "$tmp/dex" "$tmp/classes.jar"
jar --update --no-compress --file "$tmp/base.apk" -C "$tmp/dex" classes.dex
"$tools/zipalign" -p -f 4 "$tmp/base.apk" "$tmp/aligned.apk"
"$tools/apksigner" sign --v4-signing-enabled false --ks "$REELBACK_KEYSTORE" --ks-key-alias "${REELBACK_KEY_ALIAS:-reelback}" \
  --ks-pass env:REELBACK_STORE_PASSWORD --out "$out" "$tmp/aligned.apk"
"$tools/apksigner" verify "$out"
printf 'Signed APK: %s\n' "$out"
