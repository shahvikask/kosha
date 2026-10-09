# GrowUrDhan M7 — Encrypted Custody/Mutual-Fund Onboarding and Strict CAS Mapping

**As-built on branch:** `GUD-Android-ChatGPT`  
**Status:** Implementation and synthetic regression tests added; **new native CI / API matrix results pending independent verification**.  
**Date:** 2026-10-09  
**Type:** Pre-release incremental development. **NOT** Google Play readiness or security certification.

## 1. Functional journey, as implemented

1. Open an existing encrypted SQLCipher vault after Android secure-device credential confirmation. Vault → **Manage accounts & holdings**, or after creating a family member choose **Add account and holding**. Statements with no CAS enrollment also offers this route.
2. Pick an existing family owner. Add a `CAMS` or `KFINTECH` account using a short, distinct **nickname only**; never supply PAN, account numbers, secrets or passwords in that field.
3. Select the registrar account under the same owner and enter a mutual-fund scheme's precise name, 12-character ISIN and registrar folio. The UI displays previously enrolled folios **masked**, shows the ISIN, and does not invent a transaction.
4. Staging is **read-only**. A separate successful Android device-credential challenge **after staging** is required for each account/holding write. Cancellation, session expiration and backgrounding clear the pending form.
5. Repository registration is one SQLCipher transaction. `registerCustodyAccount` generates a non-identifying UUID account ID. `registerCasHolding` inserts the empty `holding` and matching `holding_cas_identity` atomically; neither writes ledger events. New holdings have **zero** units and **unknown** historical cost.
6. Statements displays only owners with enrolled CAS identities. The on-device Python parser remains the **immutable frozen CASPARSER**; its normalized CAMS/KFINTECH output is checked against actual SHA-256 of selected PDF bytes, the existing family owner, exact registrar type, ISIN, folio, matched fund name and asset category. An unknown, cross-owner, wrong-registrar, or ambiguous identity is **rejected with no statement or ledger write**. The existing explicit HMAC-reviewed approval pipeline remains the only commit path.

## 2. New v3 persistent data model

**Schema:** `VaultSchema.VERSION = 3`, same device-bound Keystore/SQLCipher vault location and key alias as the previous v2 builds. It does **not** change the local encryption key or decrypt the file with an app login password. `VaultSchema.initializeOrValidate` upgrades fresh/v1/v2 databases to v3 in a single transaction. The old ledger table, v2 columns, existing holdings and snapshot rows remain in place and append-only.

The new `holding_cas_identity` table records a scoped link:

| Column | Semantics |
| --- | --- |
| `holding_id` | Primary key referencing the enrolled holding |
| `owner_id` | Required existing family member |
| `account_id` | Required corresponding registrar custody account |
| `isin` | Uppercase 12-character security identifier |
| `folio` | Exact registrar folio, length 1–64; SQLCipher-encrypted on disk |

A unique `(owner_id, isin, folio)` prevents duplicate enrollment of an owner's same CAS position. Composite ownership foreign keys prohibit cross-family mixing, an INSERT trigger verifies the existing holding's account and `MUTUAL_FUND` category and the account's CAMS/KFINTECH type. UPDATE and DELETE triggers prevent silent reassignment of an identity after import. Data repairs/re-enrollment require a deliberate future audited migration, not a hidden SQL edit.

`VaultRepository.readBook()` atomically returns family members, custody accounts, holdings, ledger events and CAS identities. Separate authenticated `accounts()`/`casIdentities()` reads and the two new registration methods have explicit production `VaultAuthorization` checks. The statement-review book fingerprint now covers the identity table, making approvals stale after a book/mapping change.

## 3. Backup compatibility and recovery

The portable logical backup **payload format 3** contains `holding_cas_identity` and its owning account/holding rows. It remains inside the existing authenticated AES-256-GCM, PBKDF2-based **encrypted backup envelope version 1**; changing the inner logical format does not mean changing the outer cryptographic envelope. The backup parser accepts legitimate logical **formats 1, 2 and 3** into a fresh empty schema-v3 vault. Older exports lack this table and are restored **without inventing ISIN or folio**. Foreign keys and SQL triggers validate restored identity rows.

An encrypted-backup **repository API** exists. **Consumer SAF backup/export and restore file pickers are not yet implemented**, and cross-device recovery with a second Keystore has **not** been completed. A raw `wealth.db` file or Android Keystore key must **never** be transplanted as a recovery solution. The separate acceptance plan is [18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md](18-M7-PHYSICAL-RECOVERY-AND-SIGNING-ACCEPTANCE.md).

## 4. Parser security contract and remaining gaps

Production `NativeCasPdfReader` now **requires a non-null enrolled identity registry**; name-only fallback continues only for older pure domain reference tests and is not passed from the Android PDF reader. Fuzzy scheme-name matches are never accepted. CAMS/KFINTECH mutual funds are the only newly enrolled categories. Demat/NSDL/CDSL/NPS remain unsupported by the strict registry, even though the frozen parser can produce some such statement types.

The frozen CASPARSER may label a mutual-fund position as `EQUITY` or `FIXED_INCOME` based on portfolio exposure; strict matching deliberately compares the fund's enrolled scheme name and exact ISIN/folio/registrar, **not** those derived asset categories. This behavior has two added pure-Kotlin regression cases.

The parsed report's `investorName` may be missing/unreliable and its `investorPan` is intentionally null. The currently checked owner is a **user-selected family member cross-checked with enrolled securities/folio**; the implementation does **not** independently prove the human named in a PDF is that person, and does not import or store PAN for this purpose. ISIN is syntax-checked and matched exactly, not validated against external depository/registrar services. Scheme names must agree after whitespace/case normalization; legitimate renamed schemes may fail closed. This is **strict holding identity matching**, **not full real-world account-holder verification**.

The validated PDF is reviewed separately: opening units are an observation, not a BUY, unknown historical purchase price stays unknown, and all later ledger deltas require explicit fresh approval. No automatic CAS import.

## 5. Automated tests added (NOT yet passing evidence)

**New JVM cases** (total **30**): 10 `CustodyHoldingOnboardingTest` cases, 11 `CasIdentityMatchingTest` cases, 6 `CustodyHoldingViewModelTest` cases and 3 new `StatementReviewViewModelTest` cases.

**New Android cases** (total **11**): 7 `NativeCustodyHoldingInstrumentedTest` cases against actual SQLCipher/Keystore on an Android emulator, and 4 `CustodyHoldingShellTest` Compose cases. The existing `NativeAuthorizationInstrumentedTest` also checks four new repository entry points (two fresh-auth writes, two session-guarded reads).

The additional encrypted tests cover registrar/holding persistence across reopen, zero-unit/no-ledger creation, duplicate and cross-owner rollback, invalid identities, v2→v3 upgrade retaining ledger rows, v3 portable encrypted backup roundtrip, and legacy logical format-2 restore with no fabricated mapping.

Existing M6 baseline: [M6 verification report](16-M6-FAMILY-ONBOARDING-VERIFICATION.md) confirms **102 JVM and 35 API-35 instrumentation**, plus the same 35 Android cases on each API 26/30/36 emulator **on the earlier M6 implementation SHA**. These M7 additions are **not** verified merely because M6 passed.

Run CI against the final M7 commit: host schema v1/v2→v3 preflight, static authorization policy, JDK/Gradle JVM tests, debug APK build, API-35 SQLCipher and Compose emulator suites, API 26/30/36 matrix, a throwaway CI-signed/minified AAB smoke, and the untouched CASPARSER guard. Inspect **JUnit XML and artifact ZIP digests** before marking any new case PASS.

## 6. Explicit commercialization/physical-security boundaries

**Must remain OPEN:** real ARM64 native Python/PDFium parse evidence with synthetic PDFs; owner-held physical devices across APIs and 16 KiB page sizes; user-facing encrypted export/restore file pickers and second-device recovery; password/biometric trust-model review; independent OWASP MASVS testing; real customer consent; Play policy/privacy declarations and protected upload-key signing. The existing manual protected [native-signed-aab.yml](../../.github/workflows/native-signed-aab.yml) requires the owner's secrets, environment approval and explicit acknowledgement. A disposable CI-signed AAB is NOT a production-signed Play bundle and is never uploaded automatically.

**Never edit** `src/casparser-main/**`, `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py` or `scripts/requirements-cas.txt`. Do not modify `GUD-Android-Gmail` or `GUD-Android`, and do not delete the legacy React/Tauri implementation before approved parity verification.
