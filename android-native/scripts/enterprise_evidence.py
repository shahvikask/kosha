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
EGRESS_CALLS = re.compile(r"\.openConnection\s*\(|BillingClient\.newBuilder\s*\(|OkHttpClient\s*\(")
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


def write_audit(root: Path, report: Path, sha: str, out: Path) -> None:
    if not HEX_SHA.fullmatch(sha):
        raise ValueError("Exact 40-character tested source SHA required")
    components = resolved_components(report.read_text(encoding="utf-8"))
    source = audit_sources(root)
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
    (out / "quality-evidence.json").write_text(json.dumps(evidence, sort_keys=True, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    cmd = parser.add_subparsers(dest="action", required=True)
    gate = cmd.add_parser("audit")
    gate.add_argument("--root", type=Path, required=True)
    gate.add_argument("--dependencies", type=Path, required=True)
    gate.add_argument("--sha", required=True)
    gate.add_argument("--out", type=Path, required=True)
    signer = cmd.add_parser("verify-signer")
    signer.add_argument("--aab", type=Path, required=True)
    signer.add_argument("--expected-sha256-fingerprint", required=True)
    args = parser.parse_args()
    if args.action == "audit":
        write_audit(args.root, args.dependencies, args.sha, args.out)
        print("E15/E17/E18 offline source and debug-runtime evidence PASS; release gates remain BLOCKED.")
    else:
        print(json.dumps(verify_aab_signer(args.aab,
            args.expected_sha256_fingerprint), sort_keys=True))


if __name__ == "__main__":
    main()
