#!/usr/bin/env bash
# Recompile Pomelo TV (Zen Home modifié) et le signe avec la clé de ce dossier.
# Usage : ANDROID_HOME=<sdk> JAVA_HOME=<jdk> ./compiler.sh
# Le SDK doit contenir build-tools/<version>/ et platforms/android-<niveau>/android.jar.
set -euo pipefail
cd "$(dirname "$0")"
ICI=$PWD

export PATH="$JAVA_HOME/bin:$PATH"
export ZEN_VERSION=2.1.0 ZEN_VERSION_CODE=3

# Les fonds d'écran ne sont pas versionnés : sans l'APK d'origine, build.sh les génère (Python 3 et Pillow requis).
if [ -z "$(ls source/launcher/assets/wallpapers/*.jpg 2>/dev/null)" ] && [ -f apk/zen-home-origine-1.0.1.apk ]; then
  unzip -qo apk/zen-home-origine-1.0.1.apk 'assets/wallpapers/*' -d source/launcher
fi
mkdir -p apk

mkdir -p signature
if [ ! -f signature/pomelo-tv.jks ]; then
  (umask 077; head -c 24 /dev/urandom | base64 | tr -d '/+=' > signature/mot-de-passe.txt)
  keytool -genkeypair -keystore signature/pomelo-tv.jks -storepass "$(cat signature/mot-de-passe.txt)" \
    -alias zen -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pomelo TV local" 2>/dev/null
fi

export ZEN_KEYSTORE="$ICI/signature/pomelo-tv.jks" ZEN_KEY_ALIAS=zen
ZEN_KEYSTORE_PASS=$(cat signature/mot-de-passe.txt)
export ZEN_KEYSTORE_PASS ZEN_KEY_PASS=$ZEN_KEYSTORE_PASS

source/launcher/build.sh
cp source/dist/pomelo-tv.apk apk/pomelo-tv.apk
echo "APK : $ICI/apk/pomelo-tv.apk"
