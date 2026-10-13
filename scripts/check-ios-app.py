#!/usr/bin/env python3
"""Check the packaged iPhone app, not only the project settings."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("app", type=Path)
parser.add_argument("--platform", choices=("iPhoneOS", "iPhoneSimulator"), required=True)
parser.add_argument("--build", default="2")
parser.add_argument("--signed", action="store_true")
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
config = json.loads((root / "config/google-oauth.json").read_text())["ios"]
info = plistlib.loads((args.app / "Info.plist").read_bytes())

def require(condition, message):
    if not condition:
        raise SystemExit(message)

require(info["CFBundleIdentifier"] == config["bundleId"], "Wrong bundle identifier")
require(info["CFBundleShortVersionString"] == "0.2.0", "Wrong marketing version")
require(str(info["CFBundleVersion"]) == args.build, "Wrong build number")
require(info["CFBundleSupportedPlatforms"] == [args.platform], "Wrong device platform")
require(info["GoogleClientID"] == config["clientId"], "Wrong Google client ID")
schemes = [scheme for item in info["CFBundleURLTypes"] for scheme in item["CFBundleURLSchemes"]]
require(config["urlScheme"] in schemes, "Missing Google callback scheme")
require(info["ITSAppUsesNonExemptEncryption"] is False, "Unexpected export declaration")
require(int(info["DTSDKName"].split(args.platform.lower())[1].split(".")[0]) >= 26,
        "App Store Connect requires the iOS 26 SDK or newer")
require(info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon", {}).get("CFBundleIconName") == "AppIcon",
        "Missing compiled app icon")
require((args.app / "Assets.car").is_file(), "Missing compiled asset catalog")
for source in (root / "shared").iterdir():
    if source.is_file():
        require((args.app / "shared" / source.name).read_bytes() == source.read_bytes(),
                "Bundled resource mismatch: " + source.name)
if args.platform == "iPhoneOS":
    subprocess.run(["xcrun", "lipo", str(args.app / info["CFBundleExecutable"]),
                    "-verify_arch", "arm64"], check=True)
if args.signed:
    subprocess.run(["codesign", "--verify", "--deep", "--strict", str(args.app)], check=True)
    result = subprocess.run(["security", "cms", "-D", "-i", str(args.app / "embedded.mobileprovision")],
                            capture_output=True, check=True)
    profile = plistlib.loads(result.stdout)
    team = os.environ["APPLE_TEAM_ID"]
    require(profile["TeamIdentifier"] == [team], "Wrong signing team")
    require(profile["Entitlements"]["application-identifier"].endswith("." + config["bundleId"]),
            "Wrong provisioned application identifier")
    require(profile["Entitlements"].get("get-task-allow") is False, "Debug profile cannot be distributed")
    require("ProvisionedDevices" not in profile and not profile.get("ProvisionsAllDevices"),
            "TestFlight requires an App Store distribution profile")
print("Verified version, platform, SDK, Google OAuth, icons and bundled resources"
      + ("; distribution signature verified." if args.signed else ". This build is not signed for installation."))
