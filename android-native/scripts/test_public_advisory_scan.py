import json
import unittest
from public_advisory_scan import public_scan


class AdvisoryScanTest(unittest.TestCase):
    component = {"group": "org.example", "name": "demo", "version": "1.2"}

    def test_empty_batch_claims_no_advisories_only(self):
        def send(data):
            q = json.loads(data)["queries"]
            self.assertEqual("org.example:demo", q[0]["package"]["name"])
            return json.dumps({"results": [{} for _ in q]}).encode()
        result = public_scan([self.component], send)
        self.assertEqual("NO_OSV_ADVISORIES_REPORTED", result["status"])
        self.assertEqual(1, result["checked"])

    def test_known_advisories_require_review_without_suppression(self):
        def send(_):
            return b'{"results":[{"vulns":[{"id":"GHSA-EXAMPLE-1"}]}]}'
        report = public_scan([self.component], send)
        self.assertEqual("REVIEW_FINDINGS", report["status"])
        self.assertEqual(["GHSA-EXAMPLE-1"], report["findings"][0]["advisories"])

    def test_malformed_pagination_and_offline_fail_closed(self):
        for fn in (
            lambda _: b'{"results":[{"next_page_token":"more"}]}',
            lambda _: b'{"results":[]}',
            lambda _: (_ for _ in ()).throw(OSError("offline")),
        ):
            self.assertEqual("UNAVAILABLE_OR_INCOMPLETE",
                public_scan([self.component], fn)["status"])
        self.assertEqual("INCOMPLETE", public_scan([], lambda _: b"")["status"])


if __name__ == "__main__":
    unittest.main()
