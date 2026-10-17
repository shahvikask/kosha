# M24 — Frozen backup envelopes, unknown-field preservation and migration fail-closed checkpoint

**Date:** 2026-10-16. **Working repository/branch:** `shahvikask/kosha/GUD-Android-Redevelopment`. **Parent:** `7a1bd1cd2e51ac0b09c7832e824dfbce950482bc`. Resolve final SHA from GitHub after commit. **M24 remains STARTED / NOT ACCEPTED.**

## Immutable original contracts actually inspected

Reference **`GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`**; authoritative Git blob SHAs:

| Frozen file | Git blob SHA | Source observation |
|---|---|---|
| `src/lib/backup.ts` | `5643af9eeb4ec00dfbf919b58cf998ffab67e858` | `GUDVLT01` v2 binary format; `KOSHA1` legacy JSON decryption; verified account requirement |
| `src/lib/vault.ts` | `fa311299c08f3a327a465e1175664d778229bb3b` | Older JSON AES-GCM/PBKDF2 format and PAN masking |
| `src/db/migrate.ts` | `a8d0ffeaa6066b15016655d66d46f97ff487d3c7` | LocalStorage-to-SQLite migration and `__pre_sqlite_backup` source keys; checks events, holdings and exact folded value before marking done |
| `src/db/repository.ts` | `444764c2739e8b8d3627e847d882693b45361e00` | `initialise()` executes `SCHEMA_V1` before checking `vault_meta.schema_version`, then applies versioned steps; no all-step database transaction in that function |
| `src/db/schema.ts` | `50b896eb1b017b9ec6426e108c9e13a5ae10f002` | Version 9 schema; 18 tables; 210 base columns + 7 v8/v9 entity additions = 217 final expected |

The original `GUDVLT01` binary layout is **offset 0–7** ASCII `GUDVLT01`, **8–11** big-endian unsigned PBKDF2 iteration count (current exporter 600000), **12–27** random 16-byte salt, **28–39** random 12-byte nonce, **40 onward** AES-256-GCM ciphertext + authentication tag. The KDF is PBKDF2-SHA256 of `GrowUrDhan\n${email.trim().toLowerCase()}\n${password}`. JSON payload includes `kind=GUDVAULT`, `version=2`, `exportedAt`, `account`, `familyMembers`, `holdings`, `ledgerEvents`, `accounts`, `goals`, `casLatest`.

The older **`KOSHA1`** envelope is JSON with `magic`, `kdf`, `iterations`, `saltB64`, `ivB64`, `cipherB64`; uses the **password alone** for PBKDF2 and requires an embedded `account` after decryption to perform restore. Unlocked JSON backups are explicitly refused by frozen `unpackBackup`. **No backup or real account passwords, PAN, or private financial contents were copied into tests.**

**Source safety observations:** `Repository.initialise` runs `SCHEMA_V1` before inspecting schema version, so future/corrupt vault version rejection is not guaranteed before a potential CREATE. `migrateFromLocalStorage` verifies counts and exact folded amount before moving each key to `__pre_sqlite_backup`, but per-key move and pre-verification SQL inserts are not one atomic transaction. No original persistent anti-replay backup receipt is present. The stronger JVM test-only transaction, replay preflight and fail-closed checks are proposed protections, **not accepted deviations or a production restore implementation**.

## This increment's actual source

1. **Added test-only** `android-native/core/database/src/test/kotlin/in/growurdhan/database/FrozenLegacyBackupEnvelopeGoldenTest.kt`: five synthetic JUnit methods **AUTHORED / NOT RUN**. Frozen source contract assertions; Java JCE PBKDF2-SHA256/AES-256-GCM v2 binary format, email canonicalization, unknown nested JSON and >2^53 integer-string fidelity; wrong email/password, tamper, truncated/unsupported round-count preflight; KOSHA1 password-only envelope semantics and mandatory embedded account; proposed two-phase in-memory replay gate (stages not approved are never committed; duplicate logical backup even with new salt/nonce refused). **The replay gate is only a test oracle; original and production application do not implement it.**
2. **Strengthened existing test-only** `TestOnlyV9Migration.kt`: refuse a v1–v8 fixture missing `ledger_event` UPDATE/DELETE immutability triggers, an inert same-name trigger without `BEFORE UPDATE/DELETE` + `RAISE(ABORT)`, or the account-to-entity foreign-key declaration. It does not mutate a rejected source.
3. **Added two migration JUnit methods** in `TestOnlyV9MigrationTest.kt` (**AUTHORED / NOT RUN**): preserve an unrecognized physical `holding.legacy_opaque` TEXT field and its exact nested JSON through v8→v9 and encrypted reopen (218 physical columns = 217 v9-defined + 1 surviving unknown); reject missing and same-name inert ledger triggers while preserving the original version, rows and schema. Retain all six previously CI-passing migration test methods.

## Verification actually performed

- GitHub frozen source read and immutable SHA identity check: **PASS**; verified v2/older layout and source migration order.
- Static lexical Kotlin brackets/quotes/source-presence checks: **PASS** for all 3 changed Kotlin files; **not a compiler/test pass**.
- Independent local **Python cryptography** smoke: v2 byte offsets, PBKDF2-SHA256, AES-GCM roundtrip, GCM tamper rejection **PASS**. Local `kotlinc` micro-snippet for multiline KOSHA1 synthetic JSON quoting **PASS**; **not compiled or executed full new JUnit source**.
- **No GitHub Actions dispatched** (owner explicitly prohibited). **Seven newly authored tests NOT RUN** (5 envelope + 2 migration). Prior exact-source [M24 JVM #38958783180](https://github.com/shahvikask/kosha/actions/runs/38958783180) **PASSED 13/13** at `646e76573e533df0a9db1d41faed3cc84770fbd3`. That result **does not cover these three changed Kotlin files**. Expected combined total if all tests run in a later cycle: **20 test methods**, not accepted until actual JUnit XML confirms it.

## Open M24/M25 boundary

- No **captured genuine old SQLite layout** or real `GUDVLT01`/`KOSHA1` test vector produced by the TypeScript exporter and consumed by Kotlin; only synthetic envelopes derived from frozen source are tested by independent JCE oracle. Need cross-language golden vector and typed payload/schema validation before production compatibility claims.
- Actual secure portable restore requires owner identity checks (email/password and PAN, DOB, security answers on new device), replay/replacement policy, empty-target restore, authenticated ledger and owner joins, file and KDF resource limits, tamper failure isolation, and transactional swap of fully verified data. These belong to the later production M25+ stages; new test oracles do **not** deliver them.
- Production Android Keystore/SQLCipher unopened, on-device storage/migrations and low-storage recovery untested; Android emulator, physical phone and independent security signoff outstanding.
- Keep original React/Tauri, parser and approved branding byte-unchanged. No new CI run without next owner approval. **M24 stays open.**

## Superseding status at 2026-10-16 — authentic exporter-originated *synthetic* interoperability verified

Historical statements above that frozen exporter-produced synthetic envelopes had not been executed were accurate when originally written, but have since been superseded. The preceding M24 increment executed the exact Git-blob-pinned original `packBackup`/`encryptJson` functions, produced synthetic GUDVLT01/KOSHA1 vectors, and validated both with committed Kotlin/JCE. In this session the attached original-exporter synthetic fixture (SHA256 `84801ce2e9486324d9b19e6997acb3938e5cd2605661814cf872970d8e1da378`) was re-decrypted with exact committed Kotlin reader and recovery gate: 2 formats, 1 valid recovery, 7 negatives PASS, plaintext SHA256 `e7296e657cecc3bcbe4006bcfd824a4c73a5bba40f081d343d3adb00c2a9060c`. The TypeScript producer was NOT re-executed in this session. These fixtures do **not** establish historical customer-archive compatibility or authorize row-level restore. Android SQLCipher transaction, replay integration, device and AppSec gates remain open. **M24 NOT ACCEPTED**.

## 2026-10-16 strict financial payload compatibility boundary

The earlier frozen TypeScript exporter-generated fixture (SHA256 `84801ce2e9486324d9b19e6997acb3938e5cd2605661814cf872970d8e1da378`) remains valid **cryptographic** evidence only. This session inspected its decrypted JSON: synthetic holdings omit `name`, `category`, `subCategory`, `symbolOrFolio`, `currentPriceMinor`, `lastPriceUpdated` and `priceSource`; ledger events omit `id`, `holdingId`, `holdingName`, `eventType`, `eventDate`, `unitPriceMinor`, `createdAt`. The new `RestoreV9Plan` intentionally rejects those incomplete records instead of inventing trades or prices, even though GCM decryption succeeds. Original exporters **NOT rerun** this session. Production mapping/restore/device gates remain open; **M24 NOT ACCEPTED**.
