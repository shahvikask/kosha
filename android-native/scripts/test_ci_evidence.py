import hashlib
import tempfile
import unittest
from pathlib import Path
from ci_evidence import create_summary


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
