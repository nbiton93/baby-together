"""Release safety gates, with synthetic profiles only. No Apple account calls."""
import base64
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("release_ios", Path(__file__).resolve().parents[1] / "scripts/release-ios.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

class ReleaseValidation(unittest.TestCase):
    def setUp(self):
        self.env = {
            "APPLE_TEAM_ID": "ABCDE12345", "ASC_KEY_ID": "12345ABCDE",
            "ASC_ISSUER_ID": "11111111-2222-3333-4444-555555555555",
            "ASC_PRIVATE_KEY": "-----BEGIN PRIVATE KEY-----\nsynthetic\n-----END PRIVATE KEY-----",
            "IOS_CERTIFICATE_PASSWORD": "synthetic-password",
            "IOS_CERTIFICATE_P12_BASE64": base64.b64encode(b"synthetic-certificate").decode(),
            "IOS_PROFILE_BASE64": base64.b64encode(b"synthetic-profile").decode(),
            "GITHUB_RUN_NUMBER": "7",
        }
        self.profile = {
            "TeamIdentifier": ["ABCDE12345"],
            "Entitlements": {"application-identifier": "ABCDE12345." + release.BUNDLE,
                             "get-task-allow": False, "beta-reports-active": True},
            "ExpirationDate": datetime.now(timezone.utc) + timedelta(days=30),
            "UUID": "11111111-2222-3333-4444-555555555555",
            "DeveloperCertificates": [b"synthetic-certificate"],
        }

    def test_valid_configuration_and_profile(self):
        self.assertEqual(release.validate_config(self.env), "107")
        self.assertEqual(release.validate_profile(self.profile, "ABCDE12345"), self.profile["UUID"])

    def test_missing_secrets_fail_without_exposing_values(self):
        self.env.pop("IOS_PROFILE_BASE64")
        with self.assertRaises(ValueError) as context:
            release.validate_config(self.env)
        self.assertIn("IOS_PROFILE_BASE64", str(context.exception))
        self.assertNotIn("synthetic-password", str(context.exception))

    def test_invalid_build_numbers(self):
        for value in ("0", "-1", "10000", "1; echo secret", "1.2"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.validate_config({**self.env, "BUILD_NUMBER": value})

    def test_wrong_app_and_wildcard_rejected(self):
        for app in ("ABCDE12345.com.example.other", "ABCDE12345.*"):
            with self.subTest(app=app), self.assertRaises(ValueError):
                profile = deepcopy(self.profile)
                profile["Entitlements"]["application-identifier"] = app
                release.validate_profile(profile, "ABCDE12345")

    def test_other_team_and_expired_profile_rejected(self):
        with self.assertRaises(ValueError):
            release.validate_profile(self.profile, "OTHER12345")
        self.profile["ExpirationDate"] = datetime.now(timezone.utc) - timedelta(days=1)
        with self.assertRaises(ValueError):
            release.validate_profile(self.profile, "ABCDE12345")

    def test_development_adhoc_and_enterprise_profiles_rejected(self):
        for key, value in (("get-task-allow", True), ("beta-reports-active", False)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                profile = deepcopy(self.profile)
                profile["Entitlements"][key] = value
                release.validate_profile(profile, "ABCDE12345")
        for key, value in (("ProvisionedDevices", ["fake-device"]), ("ProvisionsAllDevices", True)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.validate_profile({**self.profile, key: value}, "ABCDE12345")

    def test_malformed_credentials_rejected(self):
        for key, value in (("IOS_PROFILE_BASE64", "not-base64!"), ("ASC_KEY_ID", "../wrong"),
                           ("ASC_PRIVATE_KEY", "one line"), ("APPLE_TEAM_ID", "bad-team")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.validate_config({**self.env, key: value})

if __name__ == "__main__":
    unittest.main()
