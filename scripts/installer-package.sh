#!/usr/bin/env bash
set -euo pipefail
# This disposable key signs only the SEPARATE installer package.
# The Baby Together application's existing key is never generated or replaced here.
helper_key="$RUNNER_TEMP/offline-helper-signing.p12"
helper_password_file="$RUNNER_TEMP/offline-helper-password.txt"
trap 'rm -f "$helper_key" "$helper_password_file"' EXIT
umask 077
openssl rand -hex 24 > "$helper_password_file"
keytool -genkeypair -keystore "$helper_key" -storetype PKCS12 -storepass:file "$helper_password_file" -keypass:file "$helper_password_file" -alias offline-helper -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=Baby Together Offline Installer"
mkdir -p out/installer-release
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$helper_key" --ks-key-alias offline-helper --ks-pass "file:$helper_password_file" --key-pass "file:$helper_password_file" --out out/installer-release/baby-together-0.3.0-installer.apk installer/build/outputs/apk/release/installer-release-unsigned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose out/installer-release/baby-together-0.3.0-installer.apk
python3 - <<'PY'
import pathlib, zipfile, subprocess, os, hashlib
apk = pathlib.Path("out/installer-release/baby-together-0.3.0-installer.apk")
metadata = subprocess.check_output([os.environ["ANDROID_HOME"] + "/build-tools/35.0.0/aapt", "dump", "badging", str(apk)], text=True)
assert "name='com.natibiton.babytogether.installer'" in metadata
assert "versionCode='1'" in metadata
assert "android.permission.INTERNET" not in metadata
assert "application-debuggable" not in metadata
with zipfile.ZipFile(apk) as z:
    assert hashlib.sha256(z.read("assets/baby-together-unsigned.apk")).hexdigest() == os.environ["UPDATE_SHA256"]
    assert not any(n.endswith((".p12", ".jks", "synthetic-backup.zip", "keystore-password.txt")) for n in z.namelist())
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(apk.parent / "SHA256SUMS.txt").write_text(digest + "  " + apk.name + "\n")
print("Verified separate installer identity, release signature, no network permission and no private signing files.")
PY
