#!/usr/bin/env bash
# Run beside the unsigned APK and apksigner.jar from the trusted build job.
set -euo pipefail
umask 077
: "${BT_KEYSTORE:?Set BT_KEYSTORE to a private .jks path}"
: "${BT_STORE_PASSWORD:?Set BT_STORE_PASSWORD without putting it in command history}"
: "${BT_KEY_ALIAS:=baby-together}"
if [ ! -f "$BT_KEYSTORE" ]; then
  keytool -genkeypair -keystore "$BT_KEYSTORE" -storepass:env BT_STORE_PASSWORD -keypass:env BT_STORE_PASSWORD -alias "$BT_KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Baby Together' -storetype PKCS12
fi
java -jar apksigner.jar sign --ks "$BT_KEYSTORE" --ks-pass env:BT_STORE_PASSWORD --key-pass env:BT_STORE_PASSWORD --ks-key-alias "$BT_KEY_ALIAS" --out baby-together.apk baby-together-unsigned.apk
java -jar apksigner.jar verify --verbose --print-certs baby-together.apk
# Back up the keystore privately. Future updates MUST use the same key.
# Register the printed signing certificate SHA-1 in the Android OAuth client.
