#!/usr/bin/env python3
"""Sign, validate, and optionally upload. Secrets and builds stay on the disposable runner."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import plistlib
import re
import secrets
import shlex
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
BUNDLE = "com.natibiton.babytogether"
SECRET_NAMES = ("IOS_CERTIFICATE_P12_BASE64", "IOS_CERTIFICATE_PASSWORD",
                "IOS_PROFILE_BASE64", "ASC_KEY_ID", "ASC_ISSUER_ID", "ASC_PRIVATE_KEY")

def require(condition, message):
    if not condition:
        raise ValueError(message)

def validate_config(env):
    missing = [name for name in ("APPLE_TEAM_ID",) + SECRET_NAMES if not env.get(name)]
    require(not missing, "Missing GitHub configuration: " + ", ".join(missing))
    require(re.fullmatch(r"[A-Z0-9]{10}", env["APPLE_TEAM_ID"]), "Invalid APPLE_TEAM_ID")
    require(re.fullmatch(r"[A-Z0-9]{10}", env["ASC_KEY_ID"]), "Invalid ASC_KEY_ID")
    require(re.fullmatch(r"[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}", env["ASC_ISSUER_ID"]),
            "Invalid ASC_ISSUER_ID")
    require("-----BEGIN PRIVATE KEY-----" in env["ASC_PRIVATE_KEY"]
            and "-----END PRIVATE KEY-----" in env["ASC_PRIVATE_KEY"]
            and "\n" in env["ASC_PRIVATE_KEY"],
            "ASC_PRIVATE_KEY must be the complete .p8 file with real line breaks")
    for name in ("IOS_CERTIFICATE_P12_BASE64", "IOS_PROFILE_BASE64"):
        try:
            decoded = base64.b64decode("".join(env[name].split()), validate=True)
        except Exception:
            raise ValueError(name + " is not valid base64") from None
        require(decoded, name + " is empty")
    build = env.get("BUILD_NUMBER") or str(100 + int(env.get("GITHUB_RUN_NUMBER", "1")))
    require(re.fullmatch(r"[1-9][0-9]{0,3}", build), "BUILD_NUMBER must be an integer from 1 to 9999")
    return build

def validate_profile(profile, team):
    require(profile.get("TeamIdentifier") == [team], "Provisioning profile belongs to another Apple team")
    entitlements = profile.get("Entitlements", {})
    app_id = entitlements.get("application-identifier", "")
    require(app_id.endswith("." + BUNDLE) and "*" not in app_id, "Wrong provisioned Bundle ID")
    require(entitlements.get("get-task-allow") is False, "Development profiles cannot be used for TestFlight")
    require("ProvisionedDevices" not in profile and not profile.get("ProvisionsAllDevices"),
            "Choose an App Store Connect profile, not Ad Hoc or Enterprise")
    require(entitlements.get("beta-reports-active") is True, "Profile is not enabled for TestFlight")
    expiry = profile.get("ExpirationDate")
    require(isinstance(expiry, datetime), "Profile has no expiration date")
    require(expiry.replace(tzinfo=timezone.utc) > datetime.now(timezone.utc), "Provisioning profile has expired")
    require(re.fullmatch(r"[A-Fa-f0-9-]{36}", profile.get("UUID", "")), "Invalid provisioning profile UUID")
    require(profile.get("DeveloperCertificates"), "Profile has no signing certificates")
    return profile["UUID"]

def command(args, quiet=False):
    result = subprocess.run([str(arg) for arg in args], cwd=ROOT,
                            stdout=subprocess.PIPE if quiet else None,
                            stderr=subprocess.PIPE if quiet else None)
    if result.returncode:
        # Do not include command arguments: security import receives a secret password.
        raise RuntimeError(Path(str(args[0])).name + " failed; exit " + str(result.returncode))
    return result.stdout

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("validate", "upload"), default="validate")
    parser.add_argument("--check-config", action="store_true")
    args = parser.parse_args()
    build = validate_config(os.environ)
    if args.check_config:
        print("Required configuration is present. Apple account access is checked during release.")
        return
    require(sys.platform == "darwin", "The release requires macOS and Xcode")
    work = Path(tempfile.mkdtemp(prefix="baby-together-ios-", dir=os.environ.get("RUNNER_TEMP")))
    work.chmod(0o700)
    keychain = work / "signing.keychain-db"
    installed_profiles, original_keychains = [], []
    keychain_created = keychain_list_changed = False
    team = os.environ["APPLE_TEAM_ID"]
    try:
        certificate = work / "distribution.p12"
        certificate.write_bytes(base64.b64decode("".join(os.environ["IOS_CERTIFICATE_P12_BASE64"].split())))
        certificate.chmod(0o600)
        profile_path = work / "distribution.mobileprovision"
        profile_path.write_bytes(base64.b64decode("".join(os.environ["IOS_PROFILE_BASE64"].split())))
        profile_path.chmod(0o600)
        profile = plistlib.loads(command(["security", "cms", "-D", "-i", profile_path], quiet=True))
        profile_uuid = validate_profile(profile, team)
        # altool searches ./private_keys relative to its working directory.
        key_dir = work / "private_keys"
        key_dir.mkdir(mode=0o700)
        api_key = key_dir / ("AuthKey_" + os.environ["ASC_KEY_ID"] + ".p8")
        api_key.write_text(os.environ["ASC_PRIVATE_KEY"].strip() + "\n")
        api_key.chmod(0o600)
        command(["openssl", "pkey", "-in", api_key, "-noout"], quiet=True)
        password = secrets.token_urlsafe(32)
        original_keychains = shlex.split(command(["security", "list-keychains", "-d", "user"], quiet=True).decode())
        command(["security", "create-keychain", "-p", password, keychain], quiet=True)
        keychain_created = True
        command(["security", "set-keychain-settings", "-lut", "21600", keychain], quiet=True)
        command(["security", "unlock-keychain", "-p", password, keychain], quiet=True)
        command(["security", "import", certificate, "-k", keychain,
                 "-P", os.environ["IOS_CERTIFICATE_PASSWORD"], "-T", "/usr/bin/codesign",
                 "-T", "/usr/bin/security"], quiet=True)
        command(["security", "set-key-partition-list", "-S", "apple-tool:,apple:,codesign:",
                 "-s", "-k", password, keychain], quiet=True)
        command(["security", "list-keychains", "-d", "user", "-s", keychain, *original_keychains], quiet=True)
        keychain_list_changed = True
        identities = command(["security", "find-identity", "-v", "-p", "codesigning", keychain], quiet=True).decode()
        imported = set(re.findall(r"\b[A-F0-9]{40}\b", identities.upper()))
        allowed = {hashlib.sha1(cert).hexdigest().upper() for cert in profile["DeveloperCertificates"]}
        matching = imported & allowed
        require(len(matching) == 1, "The .p12 private key must match a valid certificate in the profile")
        identity = matching.pop()
        for directory in (Path.home() / "Library/MobileDevice/Provisioning Profiles",
                          Path.home() / "Library/Developer/Xcode/UserData/Provisioning Profiles"):
            directory.mkdir(parents=True, exist_ok=True)
            destination = directory / (profile_uuid + ".mobileprovision")
            require(not destination.exists(), "Refusing to replace an existing local provisioning profile")
            shutil.copyfile(profile_path, destination)
            installed_profiles.append(destination)

        command(["xcodegen", "generate", "--spec", "ios/project.yml"])
        archive = work / "BabyTogether.xcarchive"
        command(["xcodebuild", "-quiet", "-project", "ios/BabyTogether.xcodeproj",
                 "-scheme", "BabyTogether", "-configuration", "Release", "-sdk", "iphoneos",
                 "-destination", "generic/platform=iOS", "-archivePath", archive,
                 "CODE_SIGN_STYLE=Manual", "DEVELOPMENT_TEAM=" + team,
                 "CODE_SIGN_IDENTITY=" + identity, "PROVISIONING_PROFILE_SPECIFIER=" + profile_uuid,
                 "CURRENT_PROJECT_VERSION=" + build, "archive"])
        command([sys.executable, "scripts/check-ios-app.py",
                 archive / "Products/Applications/BabyTogether.app",
                 "--platform", "iPhoneOS", "--build", build, "--signed"])
        export_options = work / "ExportOptions.plist"
        export_options.write_bytes(plistlib.dumps({
            "method": "app-store-connect", "destination": "export", "teamID": team,
            "signingStyle": "manual", "signingCertificate": identity,
            "provisioningProfiles": {BUNDLE: profile_uuid},
            "manageAppVersionAndBuildNumber": False, "uploadSymbols": True, "stripSwiftSymbols": True
        }))
        output = work / "export"
        command(["xcodebuild", "-quiet", "-exportArchive", "-archivePath", archive,
                 "-exportPath", output, "-exportOptionsPlist", export_options])
        ipas = list(output.glob("*.ipa"))
        require(len(ipas) == 1, "Export did not produce one IPA")
        unpacked = work / "verified-ipa"
        command(["ditto", "-x", "-k", ipas[0], unpacked])
        command([sys.executable, "scripts/check-ios-app.py", unpacked / "Payload/BabyTogether.app",
                 "--platform", "iPhoneOS", "--build", build, "--signed"])
        operations = ["--validate-app"] + (["--upload-app"] if args.mode == "upload" else [])
        for operation in operations:
            result = subprocess.run(["xcrun", "altool", operation, "--file", str(ipas[0]), "--type", "ios",
                                     "--apiKey", os.environ["ASC_KEY_ID"],
                                     "--apiIssuer", os.environ["ASC_ISSUER_ID"]], cwd=work)
            require(result.returncode == 0, "Apple " + operation + " failed; see the delivery errors above")
        message = ("Uploaded to App Store Connect. Wait for processing, then add the build to a TestFlight group."
                   if args.mode == "upload" else "Apple validation passed. Run with mode upload to submit.")
        print(message)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write("Baby Together 0.2.0 (" + build + ")\n\n" + message + "\n")
    finally:
        if keychain_list_changed:
            subprocess.run(["security", "list-keychains", "-d", "user", "-s", *original_keychains],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if keychain_created:
            subprocess.run(["security", "delete-keychain", str(keychain)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for path in installed_profiles:
            path.unlink(missing_ok=True)
        shutil.rmtree(work, ignore_errors=True)

if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
