#!/usr/bin/env bash
# Recompile Pomelo TV et le signe avec la clé de ce dossier.
# Usage : ./compiler.sh
# Utilise le JDK et le SDK du dossier outils/, sauf si JAVA_HOME ou ANDROID_HOME sont définis.
# Le SDK doit contenir build-tools/<version>/ et platforms/android-<niveau>/android.jar.
set -euo pipefail
cd "$(dirname "$0")"
ICI=$PWD

export JAVA_HOME=${JAVA_HOME:-$ICI/outils/jdk} ANDROID_HOME=${ANDROID_HOME:-$ICI/outils/sdk}
export PATH="$JAVA_HOME/bin:$PATH"
export POMELO_VERSION=2.6.9 POMELO_VERSION_CODE=36

mkdir -p apk

mkdir -p signature
if [ ! -f signature/pomelo-tv.jks ]; then
  (umask 077; head -c 24 /dev/urandom | base64 | tr -d '/+=' > signature/mot-de-passe.txt)
  keytool -genkeypair -keystore signature/pomelo-tv.jks -storepass "$(cat signature/mot-de-passe.txt)" \
    -alias pomelo -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pomelo TV local" 2>/dev/null
fi

export POMELO_KEYSTORE="$ICI/signature/pomelo-tv.jks"
POMELO_KEYSTORE_PASS=$(cat signature/mot-de-passe.txt)
export POMELO_KEYSTORE_PASS

launcher/build.sh
echo "APK : $ICI/apk/pomelo-tv.apk"
