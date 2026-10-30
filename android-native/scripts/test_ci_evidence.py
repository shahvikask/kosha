import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from ci_evidence import create_summary, validate_release_inventory


class NativeQualitySummaryTest(unittest.TestCase):
    def test_same_run_emulator_hashes_and_fail_closed_mutation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            lines = []
            for path in ("debug/app-debug.apk",
                         "androidTest/debug/app-debug-androidTest.apk"):
                p = root / "android-native/app/build/outputs/apk" / path
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(path.encode())
                lines.append(hashlib.sha256(p.read_bytes()).hexdigest() +
                             "  app/build/outputs/apk/" + path)
            (root / "native-apks.sha256").write_text("\n".join(lines) + "\n")
            record = create_summary("a"*40, "T2", "Synthetic", "success",
                                    "success", "success", "123", root)
            self.assertTrue(record["android_pass"])
            self.assertFalse(record["releaseReady"])
            self.assertEqual(2, len(record["apk_sha256"]))
            (root / "android-native/app/build/outputs/apk/debug/app-debug.apk").write_bytes(b"bad")
            with self.assertRaises(ValueError):
                create_summary("a"*40, "T2", "Synthetic", "success",
                               "success", "success", "123", root)
            with self.assertRaises(ValueError):
                create_summary("a"*40, "T2", "Synthetic", "success",
                               "success", "failed", "123", root)

    def test_release_candidate_shares_source_and_never_approves_production(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sha = "a" * 40
            maven = {"metadata": {"component": {"version": sha}},
                     "components": [{"name": "maven", "version": "1"}]}
            candidate = {
                "metadata": {"component": {"version": sha},
                             "properties": [{"name": "gud:coverage",
                                            "value": "PARTIAL_NOT_PRODUCTION_AAB"}]},
                "components": maven["components"] + [
                    {"name": str(i), "hashes": [
                        {"alg": "SHA-256", "content": hashlib.sha256(
                            str(i).encode()).hexdigest()}]} for i in range(4)]
            }
            quality = {"tested_sha": sha, "releaseReady": False,
                       "checks": {"resolvedReleaseCandidateGraph": "PARTIAL_SOURCE_ONLY"}}
            (root / "release-maven-sbom.json").write_text(json.dumps(maven))
            (root / "release-candidate-sbom.json").write_text(json.dumps(candidate))
            self.assertEqual("PARTIAL_RELEASE_CANDIDATE",
                validate_release_inventory(quality, root, sha))
            with self.assertRaises(ValueError):
                validate_release_inventory(quality, root, "b" * 40)
            quality["releaseReady"] = True
            with self.assertRaises(ValueError):
                validate_release_inventory(quality, root, sha)
            quality["releaseReady"] = False
            candidate["metadata"]["properties"][0]["value"] = "COMPLETE"
            (root / "release-candidate-sbom.json").write_text(json.dumps(candidate))
            with self.assertRaises(ValueError):
                validate_release_inventory(quality, root, sha)
            (root / "release-candidate-sbom.json").unlink()
            with self.assertRaises(ValueError):
                validate_release_inventory(quality, root, sha)

    def test_t1_stays_out_of_android_and_owner_approval(self):
        r = create_summary("b"*40, "T1", "", "success",
                           "success", "skipped", "42")
        self.assertIsNone(r["apk_sha256"])
        self.assertFalse(r["releaseReady"])
        self.assertEqual("BLOCKED_OWNER", r["gates"]["productionAabSigning"])
        with self.assertRaises(ValueError):
            create_summary("b"*40, "T1", "", "success",
                           "success", "success", "42")
        with self.assertRaises(ValueError):
            create_summary("b"*39, "T1", "", "success",
                           "success", "skipped", "42")


if __name__ == "__main__":
    unittest.main()
