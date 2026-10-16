# Decision log

An entry here is an owner decision only when it says so. Observations and Grok assumptions are not approvals.

| Date | ID | Status | Statement | Consequence |
|---|---|---|---|---|
| 2026-10-16 | D00 | OBSERVED, not an owner decision | Specification v1.2 names baseline `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`. At initialization, `origin/GUD-Android-Gmail` was `21d8ffbd48ed65c1d6eb02d09dfba571c7b4ef75`. `af96dcc` is that commit plus `.chatgpt-write-access-check-2026-10-08.txt`. | `GUD-Android-Redevelopment` was created at `af96dcc` and not pushed onto the reference branch. Baseline stays there until the owner changes it. |
| 2026-10-16 | D01 | OBSERVED, not an owner decision | Parser and brand bytes at `af96dcc` were hashed. `mark.svg` git blob matches Section 6A.1. Schema comment says SQLCipher; specification 1.4 and `encryptedStore.ts` describe a Keystore-wrapped WASM snapshot instead. | Hashes are frozen in `legacy-parser-sha256.txt`. The schema comment was not changed. No branding or security deviation was approved. |
