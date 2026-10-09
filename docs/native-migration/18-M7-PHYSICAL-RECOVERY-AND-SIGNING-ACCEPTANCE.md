# GrowUrDhan M7 — Physical-device, Backup Recovery, and Release Signing Acceptance Protocol

**Status:** **OPEN — reproducible acceptance plan, not results.**  
**Branch for code and CI:** `GUD-Android-ChatGPT` only.  
**Document purpose:** Distinguish source/CI implementation evidence from testing that requires an owner-controlled Android phone, real consented statements, upload-key custody, or external AppSec testers.

## 1. Scope and evidence separation

The latest native code introduces encrypted SQLCipher schema v3 and account/ISIN/folio mapping. The production on-device CAS adapter must bind a parsed CAS row to an existing family owner, supported registrar account, exact ISIN and exact folio, without creating a fresh trade. Reconciliation approval must remain explicit and require recent Android device credential confirmation. Existing schema v1/v2 books, legacy backup formats and the append-only ledger must remain intact.

Automated CI exercises JVM domain tests, SQLite migration preflight, Android API 26/30/35/36 emulators and disposable signed-AAB smoke tests. **None** is proof of a device-specific biometric prompt, ARM64 PDFium behavior, 16 KiB ELF support, passphrase recovery across phones, or a real owner-upload-key AAB.

Record evidence for each target **without serial numbers, PAN, folio, account numbers, PDFs, passwords, raw database files, screenshots of holdings, private signing material or other identifying data**. Use synthetic fund names/ISINs/folios and test-only encrypted vaults. Never reset an existing personal vault as part of testing.

## 2. Minimum physical-device test matrix

| Target | Device class | Required checks |
| --- | --- | --- |
| P1 | ARM64 Android 8–10, API 26–29 | SQLCipher + Keystore creation, vault reopen, foreground/background lock, SAF PDF chooser, non-destructive failure and backup |
| P2 | ARM64 Android 11–13, API 30–33 | OS credential confirmation, process stop/restart, app-switch during challenge, screen capture block, encrypted export |
| P3 | ARM64 Android 14–15, API 34–35 | Biometric fallback to PIN/password, state cleanup and prompt cancellation, CAS native parser/PDFium |
| P4 | ARM64 Android 16 / API 36, preferably a real 16 KiB-page device | Native PDFium/SQLCipher load, parser error handling, signed bundle-generated APK, page-size compatibility |
| P5 | Second fresh phone with **different Android Keystore** | Restore a password-protected portable backup; verify all family, account, holding, CAS identity and event records match |

If some class cannot be sourced, explicitly record **NOT TESTED**, not PASS. Emulators may supplement but do not substitute for device and OEM diversity.

## 2a. Reproducible disposable physical-device test runner

The optional [physical-device QA runner](../../android-native/scripts/run_physical_qa.sh) runs the same synthetic SQLCipher/Compose instrumentation tests on **one** authorized, clean real Android handset, with schema/security preflights. It refuses devices where GrowUrDhan is already installed and does not inspect an existing vault or log financial contents.

From the repository root, on the **owner's own development machine**, with Java 17, Gradle 8.13, Android SDK/adb and one disposable unlocked physical handset:

```bash
GUD_QA_DISPOSABLE_DEVICE=I_CONFIRM bash android-native/scripts/run_physical_qa.sh
```

Only set this consent flag for a clean **disposable QA device**. The Gradle runner installs debug/test APKs, which is why the refusal is deliberate. The script prints only API, ABI, page size and aggregate synthetic JUnit results; underlying Gradle logs may contain device identifiers, so redact before sharing. No such device run has been performed in this document.

## 3. Test preconditions and safe collection

1. Use a **disposable QA handset or isolated clean test profile** with no personal vault; record Android API level, architecture, page size, install variant and date only. Check locally with `adb shell getprop ro.build.version.sdk`, `adb shell getprop ro.product.cpu.abi` and `adb shell getconf PAGESIZE`. Do not paste `adb devices -l` output or serials into CI evidence.
2. Unlock once through the Android credential screen. Verify app creation does not occur without explicit user action, and that cancel/back/background denies continuing a pending write.
3. Test with and without a configured secure screen lock. With no screen lock, opening an existing protected native vault must be denied without erasing the encrypted database.
4. Enable screenshots/screen recorder and Android recents only on a **test vault**. Confirm financial screens are blocked by `FLAG_SECURE`; record simply PASS/FAIL, not screenshot pixels.
5. When verifying URI/provider failures, select only disposable synthetic PDF files and cancel or revoke the document provider. No app-private PDF bytes, PAN, parser traceback or password should be logged or included in CI artifacts.

## 4. Authenticated onboarding and parser-to-holding verification cases

| Case | Expected result |
| --- | --- |
| Correct authenticated first family owner + CAMS/KFintech account + distinct test nickname | New account persists after close/reopen; owner association correct |
| Unauthorized or expired device session | Repository rejects account/holding creation before database writes |
| Cancel OS confirmation after form staging | No account, holding or identity row persists |
| Register known synthetic ISIN and folio with same owner/account | Empty holding plus exactly one persisted identity mapping; zero units and zero invented invested cost |
| Unknown owner, cross-owner custody account or unsupported registrar | Atomic rejection and unchanged book |
| Duplicate ISIN + folio for same owner across two accounts | Fail closed; no ambiguous identity |
| Parse a **consented, synthetic** CAS PDF with exact owner, registrar, ISIN, folio and scheme name | Only a REVIEW is prepared, never auto-import or BUY |
| Different account/folio/ISIN, duplicate mapping, wrong owner or fund-name mismatch | Fail closed; no statement records or ledger changes |
| Source file tamper, unexpected parsed row, unsupported engine, oversized PDF | Reject without leak; preserve book |
| Approve a valid review after new device confirmation | All observation rows and delta ledger events commit atomically, with idempotent repeat handling |
| Change the book after review or expire the session | Stale receipt rejected; review again |
| API 26/30/35/36 and a real ARM64 release build | Explicitly note which ABIs ship the frozen CASPARSER/PDFium runtime; unsupported builds MUST fail closed |

Currently supported enrolled CAS registrars are **CAMS and KFINTECH**. Do not represent NSDL/CDSL/NPS as verified strict account-mapping flows until their parser fields, account identities and device tests are implemented and passed.

## 5. Portable recovery drill — no raw Keystore/database transplant

1. On a disposable source vault, create one synthetic primary member, a separate test family member, one CAMS account and a mapped holding with synthetic ISIN/folio. Confirm the holding initially has zero purchased units.
2. Import an approved **synthetic** statement using the strict mapped path. Record only counts and a deterministic **test-only** event-ID digest (not private values).
3. Export via the user-initiated, password-encrypted portable backup flow when that UI is implemented. The file must start with the backup magic/header and be authenticated via AES-GCM + PBKDF2. Never display, upload or log the passphrase. **The current native app still needs a consumer backup file-picker flow**; repository API tests alone do not complete this case.
4. Restore to a fresh, empty, **different-device** native vault using the user's export passphrase; confirm family owners, accounts, identity bindings, holdings and append-only events match logically. Keys are device-bound and MUST NOT be copied.
5. Wrong password, one-byte ciphertext tamper, incomplete archive, stale backup format and attempted restore into a non-empty target must all fail, leave target unchanged and offer a recovery explanation.
6. Test older pre-release logical backup **formats 1 and 2** on schema v3, noting that an older backup has no CAS mapping and must not invent one. Test current format 3 with complete mapping and explicit owner/folio associations.
7. Simulate missing Keystore key and missing database separately on a synthetic vault. Neither failure may silently initialize a fresh replacement vault; recovery requires a legitimate portable backup.
8. Independently verify that users are warned: **forgetting the backup passphrase or losing the backup means the app cannot restore an unrecoverable Keystore-bound database on a replacement device.**

**Required release gate:** Functional backup export/restore UI with consent, file-size bounds, fresh authentication, and cross-device evidence. Do not claim account recovery complete until the drill is actually executed.

## 6. Protected production AAB — owner action only

The checked-in [manual protected signing workflow](../../.github/workflows/native-signed-aab.yml) is a **build-only** workflow. It is not a Play publish action and requires protected environment `play-release` plus exact acknowledgement `BUILD_SIGNED_AAB`.

The owner must privately create and custody the Play upload key, configure environment reviewers, set the four `GUD_UPLOAD_*` GitHub secrets and pinned `GUD_UPLOAD_CERT_SHA256` variable, and verify the upload certificate against their Play Console record. **Do not put the key, passwords, full certificate, keystore archive or raw signing environment in this chat or a repository commit.**

Before any signing run, require:
- A new non-development versionCode/versionName, correct application ID, policy/privacy declarations and reviewed permissions.
- Green tests for the exact production source commit, plus API emulators and the physical-device cases above.
- Verified ARM64 CAS runtime and production ABI packaging, including **16 KiB page-size** checking where required.
- Owner approval of the immutable source SHA, release notes, dependency/SBOM and third-party mobile security assessment.
- Manual protected workflow run; download the short-retention signed AAB privately, inspect signer and certificate fingerprint and verify the SHA-256 archive hash. Preserve build provenance in a restricted release record.
- **Separate** Play Console internal test, staged rollout, rollback plan, privacy/data-safety review and final owner signoff. No automatic promotion from CI or this document.

The existing CI ephemeral-keystore AAB smoke is **not** a real production-signed bundle and must not be distributed.

## 7. Independent AppSec verification required

Require an independent OWASP MASVS/MASTG assessment with documented threat model, static source and binary audit, rooted/locked-device tests, app component/exported-intent checks, Keystore/SQLCipher extraction attempts, backup tamper/replay, URI/PDF fuzzing, native PDFium crash handling, abuse of device-credential callbacks, OS background and process-death race tests, supply-chain review, 16 KiB ELF checks and issue remediation retests.

Record findings with severity, evidence, fix commit, retest evidence and explicit disposition. A green CI run or assistant code review is **not** a substitute for independent penetration testing.

## 8. Owner-facing acceptance record template

Use one record per device; keep all test input synthetic:

```text
Test date:
Source commit (full SHA):
APK/build artifact reference:
Android API / ABI / page size (no serial or IMEI):
Case IDs executed:
Results: PASSED / FAILED / NOT TESTED
Sanitized evidence reference (no private data):
Defects + issue links:
Tester approval:
```

**M7 acceptance remains conditional until all required evidence exists.** Never mark the app production-ready on emulator-only evidence, unsigned or disposable-signing builds, or missing recovery/physical security results.
