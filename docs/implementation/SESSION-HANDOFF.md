# GrowUrDhan — mandatory freshest handoff

**UTC date:** 2026-10-16. **Remote:** `https://github.com/shahvikask/kosha.git`. **Working branch:** `GUD-Android-Redevelopment` only. **Starting HEAD:** `7a1bd1cd2e51ac0b09c7832e824dfbce950482bc`. After committing, resolve GitHub live SHA rather than assuming the current document includes its own hash. **Frozen branch:** `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`; original React/Tauri/parser/brand untouched. Git working tree CLEAN/dirty **UNVERIFIED** via API-only editing.

## Required start for every subsequent prompt

Read `AGENTS.md`, full v1.2 spec (G01–G15/M24/Section 17), `STATUS.md`, this handoff, `CI-RUNS.md`, `DECISION-LOG.md`, `parity-register.csv`, and all THREE `docs/native-migration/24-M24-*` checkpoints. Verify live HEAD, frozen reference and CI. Print Section 17.3 startup receipt *before* editing. M00 OWNER ACCEPTED with limitations. M24 STARTED / NOT ACCEPTED. Do not start M25 absent owner approval.

## This source-only authorized M24 increment

- Original as-built `src/lib/backup.ts` GUDVLT01 8-byte magic + 4-byte big-endian 600000 iterations + 16 salt + 12 IV + AES-256-GCM encrypted JSON; email-normalized PBKDF2 passphrase. Older `KOSHA1` JSON/base64 ciphertext is password-only. LocalStorage migration `src/db/migrate.ts` verifies amounts/counts before renaming to `__pre_sqlite_backup` but the original process is not fully atomic.
- Added **test-only** `FrozenLegacyBackupEnvelopeGoldenTest.kt` with **5 new JUnit methods**. Java-JCE wire golden, large integer strings, owner/opaque JSON, bad keys/tampering, old KOSHA1 envelope, proposed in-memory identity-gated replay oracle. These do NOT implement production replay, account verification or restore.
- Hardened disposable JVM `TestOnlyV9Migration.kt` fail-closed preflight: both append-only trigger names/semantics and account→entity FK must exist. Added **2 JUnit methods** to `TestOnlyV9MigrationTest.kt`: unknown legacy column/value retention through v8→v9 and encrypted reopen, missing/inert same-name ledger trigger rejection without mutating rows/version. Previously passing six methods unchanged.
- **Actual verification**: frozen source hashes read, static Kotlin lexical preflight passed, separate local Python PBKDF2-SHA256/AES-GCM wire-format/tamper smoke passed, local standalone `kotlinc` synthetic quote snippet passed. **Seven new JUnit methods NOT COMPILED/NOT RUN** under Gradle. No GitHub Actions dispatched or authorized; source commit requests `[skip ci]`.
- **Last exact-source CI**: [M24 JVM run 38958783180](https://github.com/shahvikask/kosha/actions/runs/38958783180) **PASS 13/13** on earlier `646e76573e533df0a9db1d41faed3cc84770fbd3`. Last legacy [Kosha CI 38958783184](https://github.com/shahvikask/kosha/actions/runs/38958783184) PASS on same older source. Those results are **NOT transferable to this increment**.

## Blockers and next authorized prompt

- Authentic captured historical SQLite/backup bytes and exporter-generated cross-language vectors remain unavailable; synthetic source-derived fixtures are not proof of actual old backup compatibility.
- Production Android Keystore + SQLCipher, full locked restore, replay protection, empty-target import, rollback under low-storage faults, PAN/DOB/security-answer gates and device security are NOT IMPLEMENTED/NOT ACCEPTED.
- To get new evidence, owner must explicitly authorize *one targeted exact-SHA* `:core:database:test` native JVM workflow cycle. Verify 20 tests with XML (13 previous + 7 new). Fix actual failures without weakening tests; update CI-RUNS/STATUS/handoff and stay on M24.
- M24 remains OPEN; no M25, emulator/AAB, original source or protected CASPARSER changes. Report A–U and next prompt starting `Re-read AGENTS.md`.

Loop: no

## 2026-10-16 — verified 20-test M24 JVM handoff (supersedes earlier NOT RUN line)

Verified GitHub remote `https://github.com/shahvikask/kosha.git`; frozen reference `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Owner-authorized exact-SHA [M24 run 38961697701](https://github.com/shahvikask/kosha/actions/runs/38961697701) **SUCCESS** on source commit `e583fb38d04ee4c23cd0cb8ba1c9fc51468f2e13` (workflow XML gate hardening); test Kotlin compilation and Gradle `:core:database:test` passed. In-job JUnit XML: `total=20 new=7 failures=0 errors=0 skipped=0`. [Evidence ZIP 12072663218](https://github.com/shahvikask/kosha/actions/runs/38961697701/artifacts/12072663218) SHA256 `bf291c9f4b50f4f43a922476a7399f253dd8883176143ab3929a5a0c010a2566`. Historical first failed 2/13 [38958340108](https://github.com/shahvikask/kosha/actions/runs/38958340108), corrected 13/13 [38958783180](https://github.com/shahvikask/kosha/actions/runs/38958783180) preserved. No test-source repair rerun necessary. GitHub API does not verify any local working-tree state. Documentation-only commits after tested SHA do not inherit exact-SHA native verification.

**CURRENT:** M24 STARTED, NOT ACCEPTED. Original legacy sources, approved logo, frozen CASPARSER, production SQLCipher/Keystore/Chaquopy untouched. Synthetic GUDVLT01/KOSHA1 test vectors and proposed replay oracle are not authentic exported backup bytes or production restore. **Next safe M24 step:** obtain/construct independently exporter-produced fixtures grounded in authentic frozen exporter; implement Android production encrypted persistence + transactional backup/restore guards in a separately authorized bounded source increment; audit Keystore, identity, ownership, rollback, append-only triggers and foreign keys. Do not start M25 or Android emulator/AAB without separate authorization. Re-read root AGENTS.md and the full v1.2 spec at the next session. Documentation HEAD must be resolved after the final commit.
