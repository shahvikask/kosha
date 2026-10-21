# Native GUDNAT01 typed archive v1 — implementation contract

**Status:** Test-backed internal primitive, NOT a production recovery/export acceptance or authorization to automatically back up.

**Format:** Binary `GUDNAT01` is distinct from legacy `GUDVLT01` and `KOSHA1`. Header: 8-byte ASCII magic; 4-byte big-endian PBKDF2-SHA256 rounds (600,000); 16-byte random salt; 12-byte random nonce. AES-256-GCM authenticates the 40-byte header as AAD and ciphertext with 128-bit tag. PBKDF2 input domain is `GrowUrDhan-Native-v1\n` + trimmed lowercase owner email + `\n` + **separate owner-chosen backup password**, distinct from Android account password. No password or source plaintext is persisted by this codec.

Authenticated payload `GV9SNAP1` + schema version 9 contains bounded schema object definitions (including triggers/indexes and opaque SQL metadata that is never executed), table/column descriptions, full typed v9 physical cells. SQLite storage classes NULL/INTEGER/TEXT/BLOB/REAL are distinct; integers use 64-bit big-endian, REAL uses exact IEEE-754 raw bits; text rejects malformed UTF-8. Total plaintext bound 64 MiB; internal row/object/cell limits are enforced before decode/allocation.

**Restore:** Only an isolated, new, empty SQLCipher v9 candidate with identical trusted physical schema is eligible; source object/column metadata and ALL cell values must match a re-read after SQL insert, with full foreign-key/SQLCipher integrity and rollback on mismatch. Unknown historical/extension metadata is retained in encrypted archive but a candidate lacking its schema must fail closed; it is **not** auto-executed or silently discarded.

**Required integration before UI/SAF:** owner/PAN/DOB/security-answer/Terms proof; durable single-use claim; independent Keystore candidate; locked no-overwrite key/database publication; crash/power loss and low-storage recovery; full real-device and old encrypted fixture proof; lifecycle-safe password clearance; separate user consent to destination/retention. No legacy downgrade or automatic SAF on-open/CAS triggers are permitted until accepted.

**Evidence:** [source `7900afa9`](https://github.com/shahvikask/kosha/commit/7900afa9830a75089a668b8e252cb9422f471df5), [targeted CI run #39591866125](https://github.com/shahvikask/kosha/actions/runs/39591866125) 5 JVM archive tests, 3 synthetic Android SQLCipher tests; original [artifact](https://github.com/shahvikask/kosha/actions/runs/39591866125/artifacts/12341618809). M24/P03/P04/P28/P29 remain not accepted.
