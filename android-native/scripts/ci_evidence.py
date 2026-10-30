#!/usr/bin/env python3
"""E17 truthful exact-SHA verification summary. Not a release-approval tool."""
import hashlib
import json
import os
import re
from pathlib import Path

KNOWN_APKS = {"app-debug.apk", "app-debug-androidTest.apk"}
HEX_SHA = re.compile(r"[0-9a-f]{40}\Z")
CHECKSUM = re.compile(r"[0-9a-f]{64}\Z")


def create_summary(source: str, tier: str, tests: str, preflight: str,
                   jvm: str, android: str, run_id: str, folder: Path | None = None):
    if not HEX_SHA.fullmatch(source) or tier not in ("T1", "T2", "T3"):
        raise ValueError("Exact source SHA and valid tier required")
    if not run_id.isdecimal() or preflight != "success" or jvm != "success":
        raise ValueError("Mandatory source, preflight or JVM verification missing")
    hashes = None
    if tier == "T1":
        if android != "skipped" or folder is not None:
            raise ValueError("T1 cannot report Android approval")
    else:
        if android != "success" or folder is None:
            raise ValueError("Android gate did not pass")
        sha_file = folder / "native-apks.sha256"
        if not sha_file.is_file():
            raise ValueError("Original APK hash manifest unavailable")
        hashes = {}
        for row in sha_file.read_text(encoding="utf-8").splitlines():
            parts = row.split()
            if len(parts) != 2 or not CHECKSUM.fullmatch(parts[0]):
                raise ValueError("Malformed original APK digest")
            relative = Path(parts[1])
            if relative.is_absolute() or ".." in relative.parts or relative.name not in KNOWN_APKS:
                raise ValueError("Untrusted APK artifact path")
            if relative.name in hashes:
                raise ValueError("Duplicate APK digest")
            archived = folder / "android-native" / relative
            if not archived.is_file():
                raise ValueError("Same-run original APK missing")
            if hashlib.sha256(archived.read_bytes()).hexdigest() != parts[0]:
                raise ValueError("APK bytes do not match original digest")
            hashes[relative.name] = parts[0]
        if set(hashes) != KNOWN_APKS:
            raise ValueError("Both app and test APKs required")
    return {
        "sha": source, "tier": tier, "tests": tests,
        "jvm_pass": True, "android_pass": android == "success",
        "apk_sha256": hashes, "run_id": run_id,
        "verification": "PASS", "releaseReady": False,
        "gates": {
            "sourcePreflight": "PASS", "jvmCompileAndUnit": "PASS",
            "api35Instrumentation": "PASS" if android == "success" else "NOT_RUN",
            "dependencySbom": "PARTIAL_DEBUG_ONLY",
            "externalAdvisoryScan": "NOT_RUN",
            "productionAabSigning": "BLOCKED_OWNER",
            "physicalDevice": "BLOCKED_OWNER",
            "independentPenTest": "BLOCKED_OWNER",
            "g14VisualOwnerSignoff": "BLOCKED_OWNER",
            "PlayPurchaseVerification": "BLOCKED_OWNER",
            "fullReleaseRegression": "NOT_RUN",
        }
    }


def validate_release_inventory(quality: dict, folder: Path, sha: str) -> str:
    """The same-run candidate has exact source binding, never production approval."""
    if quality.get("tested_sha") != sha:
        raise ValueError("Security evidence is not from the tested source")
    if quality.get("checks", {}).get("resolvedReleaseCandidateGraph") != "PARTIAL_SOURCE_ONLY":
        raise ValueError("Release candidate inventory is incomplete")
    path = folder / "release-candidate-sbom.json"
    maven_path = folder / "release-maven-sbom.json"
    if not path.is_file() or not maven_path.is_file():
        raise ValueError("Same-run release candidate inventory is absent")
    candidate = json.loads(path.read_text(encoding="utf-8"))
    maven = json.loads(maven_path.read_text(encoding="utf-8"))
    if candidate.get("metadata", {}).get("component", {}).get("version") != sha or (
        maven.get("metadata", {}).get("component", {}).get("version") != sha
    ):
        raise ValueError("Release inventory belongs to another commit")
    labels = candidate.get("metadata", {}).get("properties", [])
    if not isinstance(labels, list) or not any(
        isinstance(x, dict) and x.get("name") == "gud:coverage" and
        x.get("value") == "PARTIAL_NOT_PRODUCTION_AAB" for x in labels
    ):
        raise ValueError("A release candidate must disclose incomplete scope")
    components = candidate.get("components")
    source_components = maven.get("components")
    if not isinstance(components, list) or not isinstance(source_components, list) or (
        not source_components or len(components) != len(source_components) + 4
    ):
        raise ValueError("Release dependency records are missing or incomplete")
    wheels = components[len(source_components):]
    if components[:len(source_components)] != source_components or not all(
        isinstance(x, dict) and len(x.get("hashes", [])) == 1 and
        x["hashes"][0].get("alg") == "SHA-256" and
        CHECKSUM.fullmatch(x["hashes"][0].get("content", "")) for x in wheels
    ):
        raise ValueError("Unverified release wheel fingerprint evidence")
    if quality.get("releaseReady") is not False:
        raise ValueError("Unapproved release state")
    return "PARTIAL_RELEASE_CANDIDATE"


def main():
    e = os.environ
    folder = Path("original-emulator-artifact") if e["TIER"] != "T1" else None
    result = create_summary(e["TARGET_SHA"], e["TIER"], e["TESTS"],
                            e["PREFLIGHT"], e["JVM"], e["ANDROID"],
                            e["GITHUB_RUN_ID"], folder)
    evidence = Path("original-jvm-artifact/native-enterprise-evidence/quality-evidence.json")
    if not evidence.is_file():
        raise ValueError("Same-run JVM security evidence absent")
    quality = json.loads(evidence.read_text(encoding="utf-8"))
    if quality.get("tested_sha") != result["sha"]:
        raise ValueError("Security evidence belongs to a different source")
    advisory = quality.get("checks", {}).get("externalVulnerabilityAdvisoryScan")
    if advisory not in {"NOT_RUN", "NO_OSV_ADVISORIES_REPORTED",
                        "REVIEW_FINDINGS", "UNAVAILABLE_OR_INCOMPLETE", "INCOMPLETE"}:
        raise ValueError("Unrecognized advisory verification status")
    result["gates"]["externalAdvisoryScan"] = advisory
    result["gates"]["dependencySbom"] = validate_release_inventory(
        quality, evidence.parent, result["sha"])
    result["gates"]["networkPayloadPrivacy"] = "NOT_RUN"
    Path("native-summary.json").write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(json.dumps({"sha": result["sha"], "tier": result["tier"],
                      "verification": result["verification"], "releaseReady": False}))


if __name__ == "__main__":
    main()
