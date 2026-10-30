import tempfile
import hashlib
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from enterprise_evidence import (
    audit_sources, resolved_components, verify_aab_signer, write_audit,
    release_inventory, verified_release_wheels,
)


class EvidenceTests(unittest.TestCase):
    def test_dependency_graph_is_deduped_and_resolved(self):
        report = """debugRuntimeClasspath
+--- org.example:alpha:1.0 -> 1.2
\\--- org.example:alpha:1.2 (*)
+--- org.example:beta:2.0
"""
        rows = resolved_components(report)
        self.assertEqual(2, len(rows))
        self.assertEqual(["alpha", "beta"], [r["name"] for r in rows])
        self.assertEqual("1.2", rows[0]["version"])
        with self.assertRaises(ValueError):
            resolved_components("not a resolution report")

    def test_egress_scan_rejects_new_path_and_unsafe_manifest(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            app = root / "android-native/app/src/main/kotlin/test"
            app.mkdir(parents=True)
            manifest = root / "android-native/app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True, exist_ok=True)
            manifest.write_text('<application android:allowBackup="false"/>')
            for name, entry in (("NativeAmfiNavCatalog.kt", "val x = url.openConnection()"),
                                ("NativeExchangeEodDownloader.kt", "val x = url.openConnection()"),
                                ("NativePlayCatalogProbe.kt", "BillingClient.newBuilder(ctx)")):
                (app / name).write_text(entry)
            self.assertEqual(3, len(audit_sources(root)["networkEgressEntrypoints"]))
            (app / "UnexpectedUploader.kt").write_text("url.openConnection()")
            with self.assertRaises(ValueError):
                audit_sources(root)
            (app / "UnexpectedUploader.kt").unlink()
            # Alternate HTTP/socket/WebView patterns must not silently evade E18.
            for source in ("url.openStream()", 'Socket("example.org", 443)',
                           "DatagramSocket(4040)", "WebView.loadUrl(address)",
                           "Retrofit.Builder()", "HttpClient.newHttpClient()",
                           "OkHttpClient()"):
                (app / "UnexpectedUploader.kt").write_text(source)
                with self.assertRaises(ValueError):
                    audit_sources(root)
            (app / "UnexpectedUploader.kt").unlink()
            manifest.write_text('<application android:allowBackup="true"/>')
            with self.assertRaises(ValueError):
                audit_sources(root)

    def test_audit_manifest_marks_release_and_security_gates_blocked(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            app = root / "android-native/app/src/main/kotlin/test"
            app.mkdir(parents=True)
            manifest = root / "android-native/app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True, exist_ok=True)
            manifest.write_text('<application android:allowBackup="false"/>')
            for name, entry in (("NativeAmfiNavCatalog.kt", "url.openConnection()"),
                                ("NativeExchangeEodDownloader.kt", "url.openConnection()"),
                                ("NativePlayCatalogProbe.kt", "BillingClient.newBuilder(ctx)")):
                (app / name).write_text(entry)
            deps = root / "graph.txt"
            deps.write_text("+--- org.example:a:1.0")
            write_audit(root, deps, "a" * 40, root / "out")
            import json
            e = json.loads((root / "out/quality-evidence.json").read_text())
            self.assertFalse(e["releaseReady"])
            self.assertEqual("BLOCKED_OWNER", e["checks"]["productionAabSigner"])
            self.assertEqual(1, e["dependencyCount"])

    def test_release_candidate_inventory_is_explicitly_partial_and_sha_pinned(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            wheels = root / "android-native/app/python-wheels"
            wheels.mkdir(parents=True)
            names = []
            for package, version in (("pydantic_core", "2.41.5"),
                                     ("pypdfium2", "5.14.0")):
                for abi in ("arm64_v8a", "x86_64"):
                    name = f"{package}-{version}-cp313-cp313-android_26_{abi}.whl"
                    payload = name.encode()
                    (wheels / name).write_bytes(payload)
                    names.append(hashlib.sha256(payload).hexdigest() + "  " + name)
            (wheels / "SHA256SUMS").write_text("\n".join(names) + "\n")
            build = root / "android-native/app/build.gradle.kts"
            build.parent.mkdir(parents=True, exist_ok=True)
            build.write_text("\n".join('install("' + p + '")' for p in (
                "pydantic==2.12.5", "pydantic-core==2.41.5",
                "pypdfium2==5.14.0", "python-dateutil>=2.8.2,<3",
                "click>=8,<10", "colorama>=0.4.6,<1", "rich>=13.5.2,<16")))
            graph = root / "release.txt"
            graph.write_text("releaseRuntimeClasspath\n+--- org.example:lib:1.0\n")
            maven, candidate = release_inventory(root, graph, wheels, "a" * 40)
            self.assertEqual(1, len(maven["components"]))
            self.assertEqual(5, len(candidate["components"]))
            self.assertEqual("PARTIAL_NOT_PRODUCTION_AAB",
                candidate["metadata"]["properties"][0]["value"])
            self.assertEqual("a" * 40, candidate["metadata"]["component"]["version"])
            (wheels / names[0].split()[-1]).write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                verified_release_wheels(wheels)
            (wheels / names[0].split()[-1]).write_bytes(names[0].split()[-1].encode())
            (wheels / "rogue.whl").write_bytes(b"rogue")
            with self.assertRaises(ValueError):
                verified_release_wheels(wheels)
            (wheels / "rogue.whl").unlink()
            (wheels / "SHA256SUMS").write_text("0" * 64 + "  ../outside.whl\n")
            with self.assertRaises(ValueError):
                verified_release_wheels(wheels)
            (wheels / "SHA256SUMS").write_text("\n".join(names) + "\n")
            graph.write_text("releaseRuntimeClasspath\n+--- org.example:lib:1.0 FAILED\n")
            with self.assertRaises(ValueError):
                release_inventory(root, graph, wheels, "a" * 40)
            graph.write_text("releaseRuntimeClasspath\n+--- org.example:lib:1.0\n")
            build.write_text('install("pydantic==2.12.5")')
            with self.assertRaises(ValueError):
                release_inventory(root, graph, wheels, "a" * 40)

    def test_signer_missing_or_invalid_cannot_pass_with_mocked_tool(self):
        with tempfile.TemporaryDirectory() as d:
            fake_path = Path(d) / "missing.aab"
            with self.assertRaises(ValueError):
                verify_aab_signer(fake_path, ":".join(["AA"] * 32))
            fake_path.write_bytes(b"fixture")
            with self.assertRaises(ValueError):
                verify_aab_signer(fake_path, "NOT-A-FINGERPRINT")
            def unsigned(argv, **_):
                return SimpleNamespace(returncode=1, stdout="")
            with self.assertRaises(ValueError):
                verify_aab_signer(fake_path, ":".join(["AA"] * 32), unsigned)

    def test_signer_verification_fails_on_mismatched_fingerprint(self):
        with tempfile.TemporaryDirectory() as d:
            aab = Path(d) / "test.aab"
            aab.write_bytes(b"not real aab fixture")
            fingerprint = ":".join(["AA"] * 32)
            def fake(argv, **_):
                if argv[0] == "jarsigner":
                    return SimpleNamespace(returncode=0, stdout="")
                return SimpleNamespace(returncode=0,
                    stdout="SHA256: " + ":".join(["BB"] * 32))
            with self.assertRaises(ValueError):
                verify_aab_signer(aab, fingerprint, fake)


if __name__ == "__main__":
    unittest.main()
