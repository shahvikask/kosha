#!/usr/bin/env python3
"""E15/E17/E18 offline, privacy-safe build evidence; never a release signoff.

Reads only source and public dependency coordinates. Do not feed user statements,
account data, private keys, Gradle environment variables, or signer credentials.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

MODULE = re.compile(r"(?<![A-Za-z0-9_.-])([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([0-9][A-Za-z0-9_.+-]*)")
HEX_SHA = re.compile(r"[0-9a-f]{40}\Z")
FINGERPRINT = re.compile(r"(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}\Z")
DYNAMIC = re.compile(r"^(?:latest\.|[+]|.*\+)$", re.I)
# Strong indicators only; no secret value is ever printed or copied to artifacts.
SECRETS = [
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"ghp_[A-Za-z0-9]{36}"),
    re.compile(r"AIza[A-Za-z0-9_\-]{35}"),
]
EGRESS_CALLS = re.compile(
    r"\.openConnection\s*\(|\.openStream\s*\(|BillingClient\.newBuilder\s*\(|"
    r"OkHttpClient\s*\(|\bSocket\s*\(|\bDatagramSocket\s*\(|"
    r"\.loadUrl\s*\(|\bRetrofit\.Builder\s*\(|\bHttpClient\.newHttpClient\s*\("
)
EGRESS_FILES = {
    "NativeAmfiNavCatalog.kt": "AMFI manual NAV GET",
    "NativeExchangeEodDownloader.kt": "NSE/BSE manual close GET",
    "NativePlayCatalogProbe.kt": "Play Billing SDK catalog (read-only)",
}


def resolved_components(report: str) -> list[dict]:
    components: dict[tuple[str, str, str], dict] = {}
    for line in report.splitlines():
        match = MODULE.search(line)
        if not match or not any(x in line for x in ("--- ", "\\--- ", "+--- ")):
            continue
        group, name, requested = match.groups()
        if DYNAMIC.match(requested):
            raise ValueError("Unpinned runtime dependency")
        remainder = line[match.end():]
        version = requested
        resolved = re.match(r"\s*->\s*([0-9][A-Za-z0-9_.+-]*)", remainder)
        if resolved:
            version = resolved.group(1)
        if DYNAMIC.match(version):
            raise ValueError("Dynamic resolved dependency")
        if (group, name, version) not in components:
            components[group, name, version] = {
                "type": "library", "group": group, "name": name,
                "version": version,
                "purl": "pkg:maven/" + group + "/" + name + "@" + version,
            }
    if not components:
        raise ValueError("Missing Gradle debug runtime resolution evidence")
    return [components[key] for key in sorted(components)]


# Release inventory is a candidate only: Gradle runtime metadata and four exact
# native wheel bytes do not prove installed pip transitive dependencies, bundled
# Python runtime, or final production-signed AAB contents.
RELEASE_WHEELS = {
    ("pydantic_core", "2.41.5", "arm64_v8a"),
    ("pydantic_core", "2.41.5", "x86_64"),
    ("pypdfium2", "5.14.0", "arm64_v8a"),
    ("pypdfium2", "5.14.0", "x86_64"),
}
WHEEL_DIGEST = re.compile(r"([0-9a-f]{64})\s+([A-Za-z0-9_.+-]+\.whl)\Z")
WHEEL_PLATFORM = re.compile(
    r"^(pydantic_core|pypdfium2)-(2\.41\.5|5\.14\.0)-.*android_\d+_(arm64_v8a|x86_64)\.whl$"
)
PIP_DECLARATION = re.compile(r'install\("([^"\n]+)"\)')


def verified_release_wheels(folder: Path) -> list[dict]:
    manifest = folder / "SHA256SUMS"
    if not manifest.is_file():
        raise ValueError("Release wheel SHA256SUMS manifest missing")
    lines = manifest.read_text(encoding="utf-8").splitlines()
    if len(lines) != 4:
        raise ValueError("Exactly four pinned Android wheel digests required")
    seen: set[tuple[str, str, str]] = set()
    names: set[str] = set()
    result = []
    for line in lines:
        match = WHEEL_DIGEST.fullmatch(line.strip())
        if match is None:
            raise ValueError("Malformed Android wheel checksum record")
        digest, name = match.groups()
        parsed = WHEEL_PLATFORM.fullmatch(name)
        if parsed is None:
            raise ValueError("Unapproved Android wheel artifact")
        package, version, abi = parsed.groups()
        if (package, version, abi) not in RELEASE_WHEELS or (
            package, version, abi
        ) in seen or name in names:
            raise ValueError("Duplicate or mismatched release ABI wheel")
        seen.add((package, version, abi))
        names.add(name)
        artifact = folder / name
        if not artifact.is_file() or hashlib.sha256(artifact.read_bytes()).hexdigest() != digest:
            raise ValueError("Android wheel bytes differ from checksum")
        result.append({
            "type": "library", "name": package, "version": version,
            "hashes": [{"alg": "SHA-256", "content": digest}],
            "properties": [{"name": "gud:android_abi", "value": abi},
                           {"name": "gud:scope", "value": "wheel-bytes-not-installed-runtime"}],
        })
    if seen != RELEASE_WHEELS or set(p.name for p in folder.glob("*.whl")) != names:
        raise ValueError("Missing or unexpected release wheel")
    return sorted(result, key=lambda x: (x["name"], x["properties"][0]["value"]))


def release_inventory(root: Path, release_report: Path, wheel_dir: Path,
                      sha: str) -> tuple[dict, dict]:
    graph = release_report.read_text(encoding="utf-8")
    if "releaseRuntimeClasspath" not in graph or "FAILED" in graph or "UNRESOLVED" in graph:
        raise ValueError("Unverified release runtime dependency resolution")
    maven = resolved_components(graph)
    wheels = verified_release_wheels(wheel_dir)
    gradle = (root / "android-native/app/build.gradle.kts").read_text(encoding="utf-8")
    declared = sorted(set(PIP_DECLARATION.findall(gradle)))
    required = {"pydantic", "pydantic-core", "pypdfium2", "python-dateutil",
                "click", "colorama", "rich"}
    names = {re.split(r"[<>=!~]", item, 1)[0] for item in declared}
    if names != required or len(declared) != len(required):
        raise ValueError("Unexpected or missing declared parser Python packages")
    maven_bom = {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
        "metadata": {"component": {"type": "application",
                       "name": "GrowUrDhan-native-release-maven", "version": sha}},
        "components": maven,
    }
    candidate = {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
        "metadata": {"component": {"type": "application",
                       "name": "GrowUrDhan-native-release-candidate", "version": sha},
                     "properties": [
                         {"name": "gud:coverage", "value": "PARTIAL_NOT_PRODUCTION_AAB"},
                         {"name": "gud:limitations", "value":
                          "Unresolved installed Python transitives/Chaquopy runtime; final signed AAB not inspected"},
                     ]},
        "components": maven + wheels,
        "properties": [{"name": "gud:declared_python_requirements",
                        "value": ",".join(declared)}],
    }
    return maven_bom, candidate


def production_sources(root: Path):
    native = root / "android-native"
    for path in sorted(native.rglob("*.kt")):
        if "/src/main/" in path.as_posix():
            yield path
    for path in sorted(native.rglob("build.gradle.kts")):
        yield path


def audit_sources(root: Path) -> dict:
    manifest = (root / "android-native/app/src/main/AndroidManifest.xml").read_text()
    if 'android:allowBackup="false"' not in manifest:
        raise ValueError("Financial backup must stay disabled (D57)")
    if 'android:usesCleartextTraffic="true"' in manifest:
        raise ValueError("Plaintext network mode rejected")
    observed: list[dict] = []
    for path in production_sources(root):
        code = path.read_text(encoding="utf-8")
        if any(pattern.search(code) for pattern in SECRETS):
            raise ValueError("High-confidence hard-coded secret in production source (path redacted)")
        for match in EGRESS_CALLS.finditer(code):
            mechanism = EGRESS_FILES.get(path.name)
            if not mechanism:
                raise ValueError("Unreviewed egress entrypoint in production source (path redacted)")
            observed.append({"source": path.name, "mechanism": mechanism})
    required = set(EGRESS_FILES)
    if not required.issubset({row["source"] for row in observed}):
        raise ValueError("Missing expected egress inventory implementation")
    # Keep a source-class inventory only. No network request, URL parameters, data.
    return {
        "manifestAllowBackup": False, "cleartext": False,
        "networkEgressEntrypoints": sorted(observed, key=lambda x: (x["source"], x["mechanism"])),
        "secretScan": "PASS", "externalSecretsScan": "NOT_RUN",
        "networkPayloadInspection": "NOT_RUN",
    }


def normalized_fingerprint(value: str) -> str:
    if not FINGERPRINT.fullmatch(value):
        raise ValueError("Expected full SHA-256 certificate fingerprint required")
    return value.replace(":", "").lower()


def verify_aab_signer(aab: Path, fingerprint: str,
                      runner=subprocess.run) -> dict:
    if not aab.is_file() or aab.suffix.lower() != ".aab":
        raise ValueError("Actual AAB artifact required")
    expected = normalized_fingerprint(fingerprint)
    check = runner(["jarsigner", "-verify", "-strict", str(aab)],
                   capture_output=True, text=True, check=False, timeout=90)
    if check.returncode:
        raise ValueError("AAB signature verification failed")
    result = runner(["keytool", "-printcert", "-jarfile", str(aab)],
                    capture_output=True, text=True, check=False, timeout=90)
    if result.returncode:
        raise ValueError("AAB signing certificate unavailable")
    match = re.search(r"SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})",
                      result.stdout)
    if not match or normalized_fingerprint(match.group(1)) != expected:
        raise ValueError("AAB signer fingerprint mismatch")
    return {"signerMatch": True, "aabSha256": hashlib.sha256(aab.read_bytes()).hexdigest()}


def write_audit(root: Path, report: Path, sha: str, out: Path,
                release_report: Path | None = None,
                wheel_dir: Path | None = None) -> None:
    if not HEX_SHA.fullmatch(sha):
        raise ValueError("Exact 40-character tested source SHA required")
    components = resolved_components(report.read_text(encoding="utf-8"))
    source = audit_sources(root)
    if (release_report is None) != (wheel_dir is None):
        raise ValueError("Release graph and wheel evidence must be supplied together")
    release = None
    if release_report is not None and wheel_dir is not None:
        release = release_inventory(root, release_report, wheel_dir, sha)
    out.mkdir(parents=True, exist_ok=True)
    sbom = {
        "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
        "metadata": {"component": {"type": "application", "name": "GrowUrDhan-native",
            "version": sha}, "properties": [
            {"name": "gud:evidence_scope",
             "value": "Resolved Gradle debugRuntimeClasspath only; NOT full APK/AAB SBOM"},
            {"name": "gud:release_signer", "value": "BLOCKED_OWNER"},
        ]},
        "components": components,
    }
    evidence = {
        "tested_sha": sha, "source": "GitHub Actions exact-SHA native CI",
        "checks": {
            "offlineSourceGuard": "PASS",
            "resolvedDebugDependencyGraph": "PASS",
            "resolvedReleaseCandidateGraph": "PARTIAL_SOURCE_ONLY" if release else "NOT_RUN",
            "localSecretPatternScan": "PASS",
            "externalVulnerabilityAdvisoryScan": "NOT_RUN",
            "independentMASVSPenTest": "BLOCKED_OWNER",
            "productionAabSigner": "BLOCKED_OWNER",
            "deviceAcceptance": "BLOCKED_OWNER",
            "PlayPurchaseVerification": "BLOCKED_OWNER",
            "privacySafeMetrics": "SOURCE_ONLY",
        },
        "dependencyCount": len(components),
        "privacy": source,
        "releaseReady": False,
        "noHouseholdBackend": "Source inventory only; independent runtime review required",
    }
    (out / "debug-runtime-sbom.json").write_text(json.dumps(sbom, sort_keys=True, indent=2) + "\n")
    if release is not None:
        (out / "release-maven-sbom.json").write_text(
            json.dumps(release[0], sort_keys=True, indent=2) + "\n")
        (out / "release-candidate-sbom.json").write_text(
            json.dumps(release[1], sort_keys=True, indent=2) + "\n")
    (out / "quality-evidence.json").write_text(json.dumps(evidence, sort_keys=True, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    cmd = parser.add_subparsers(dest="action", required=True)
    gate = cmd.add_parser("audit")
    gate.add_argument("--root", type=Path, required=True)
    gate.add_argument("--dependencies", type=Path, required=True)
    gate.add_argument("--sha", required=True)
    gate.add_argument("--release-dependencies", type=Path)
    gate.add_argument("--wheel-dir", type=Path)
    gate.add_argument("--out", type=Path, required=True)
    signer = cmd.add_parser("verify-signer")
    signer.add_argument("--aab", type=Path, required=True)
    signer.add_argument("--expected-sha256-fingerprint", required=True)
    args = parser.parse_args()
    if args.action == "audit":
        write_audit(args.root, args.dependencies, args.sha, args.out,
                    args.release_dependencies, args.wheel_dir)
        print("E15/E17/E18 offline source and debug-runtime evidence PASS; release gates remain BLOCKED.")
    else:
        print(json.dumps(verify_aab_signer(args.aab,
            args.expected_sha256_fingerprint), sort_keys=True))


if __name__ == "__main__":
    main()
