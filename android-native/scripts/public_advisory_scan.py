#!/usr/bin/env python3
"""E15 public OSV advisory lookup for the PARTIAL debug-runtime Maven SBOM.

Only public dependency coordinates are transmitted. Missing network, pagination
or malformed responses are recorded as NOT_VERIFIED; no release is approved.
"""
import argparse
import json
import urllib.request
from pathlib import Path

API = "https://api.osv.dev/v1/querybatch"


def public_scan(components, send):
    seen = set()
    queries = []
    for item in components:
        group, name, version = item.get("group"), item.get("name"), item.get("version")
        if not all(isinstance(v, str) and v for v in (group, name, version)):
            return {"status": "INCOMPLETE", "checked": 0, "findings": []}
        coordinate = (group, name, version)
        if coordinate not in seen:
            seen.add(coordinate)
            queries.append({"package": {"ecosystem": "Maven", "name": group + ":" + name},
                            "version": version})
    if not queries:
        return {"status": "INCOMPLETE", "checked": 0, "findings": []}
    found = []
    checked = 0
    try:
        for index in range(0, len(queries), 40):
            block = queries[index:index + 40]
            payload = json.dumps({"queries": block}).encode()
            result = json.loads(send(payload).decode())
            pages = result.get("results")
            if not isinstance(pages, list) or len(pages) != len(block):
                raise ValueError("OSV batch result shape mismatch")
            for query, page in zip(block, pages):
                if not isinstance(page, dict) or page.get("next_page_token"):
                    raise ValueError("OSV pagination requires separate complete review")
                vulns = page.get("vulns", [])
                if not isinstance(vulns, list):
                    raise ValueError("Malformed advisory entry")
                ids = sorted({x["id"] for x in vulns if isinstance(x, dict) and
                              isinstance(x.get("id"), str) and x["id"]})
                if len(ids) != len({str(x.get("id")) for x in vulns}):
                    raise ValueError("Unparseable advisory record")
                if ids:
                    found.append({"dependency": query["package"]["name"],
                                  "version": query["version"], "advisories": ids})
            checked += len(block)
    except (OSError, TimeoutError, ValueError, KeyError, UnicodeError):
        return {"status": "UNAVAILABLE_OR_INCOMPLETE", "checked": checked,
                "totalDependencies": len(queries), "findings": found}
    return {"status": "REVIEW_FINDINGS" if found else "NO_OSV_ADVISORIES_REPORTED",
            "checked": checked, "totalDependencies": len(queries), "findings": found}


def send_osv(payload: bytes) -> bytes:
    req = urllib.request.Request(API, data=payload, headers={
        "Content-Type": "application/json", "Accept": "application/json"},
        method="POST")
    with urllib.request.urlopen(req, timeout=25) as response:
        data = response.read(3 * 1024 * 1024 + 1)
        if len(data) > 3 * 1024 * 1024:
            raise ValueError("OSV response exceeds bounded limit")
        return data


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sbom", type=Path, required=True)
    p.add_argument("--quality", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    args = p.parse_args()
    components = json.loads(args.sbom.read_text())["components"]
    report = public_scan(components, send_osv)
    args.out.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    quality = json.loads(args.quality.read_text())
    quality["checks"]["externalVulnerabilityAdvisoryScan"] = report["status"]
    quality["releaseReady"] = False
    args.quality.write_text(json.dumps(quality, sort_keys=True, indent=2) + "\n")
    print("E15 public advisory status: " + report["status"] +
          "; this partial scan is NOT release/pen-test approval.")


if __name__ == "__main__":
    main()
