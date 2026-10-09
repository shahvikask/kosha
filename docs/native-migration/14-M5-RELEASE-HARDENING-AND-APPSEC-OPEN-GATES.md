# GrowUrDhan Native — M5 Parser Integration, Financial Parity, Release and AppSec Gates

**Date:** 2026-10-08  
**Working branch:** `GUD-Android-ChatGPT` (only permitted edit target)  
**Source baseline before this increment:** `f63c7913ecf898be67f1ef47c74854c4d726a312`, native run [#37869471922](https://github.com/shahvikask/kosha/actions/runs/37869471922) with 62 JVM and 24 Android cases passing, and Kosha CI [#37869471940](https://github.com/shahvikask/kosha/actions/runs/37869471940) passing.  
**This increment:** SOURCE IMPLEMENTATION COMMITTED; **new CI verification is required**. A signed, commercially release-ready artifact has **not** been approved or published.

## 1. What was implemented

### Native CASPARSER integration

- A new Compose Statements flow shows existing family members and requires the person to choose an owner, a PDF using Android's Storage Access Framework, and an optional masked document password. Sensitive input is held in memory, not in a vault table or SavedStateHandle. Leaving for the external picker revokes the earlier session; opening the document requires **fresh system device-credential confirmation**.
- `NativeCasPdfReader` reads the selected URI on the device with a strict **30 MiB** size limit and `%PDF-` header check; it rejects oversized, unreadable or stalled content provider streams. It calculates SHA-256 **from actual PDF bytes** instead of trusting a caller-supplied source fingerprint, and it always overwrites its temporary PDF byte buffer on return. Base64/Python strings cannot be reliably zeroed in the JVM and remain an attack-surface limitation. No PDF is sent to the developer sidecar.
- The frozen Python entrypoint `scripts/cas_android.py` is called via an Android Chaquopy bridge `gud_cas_bridge.py`; **no frozen parser Python code is changed**. A separate release-only `:casparser-runtime` module attempts to bundle frozen source plus Chaquopy 17.0, Python 3.13, pypdfium2 5.13.0 and python-dateutil. This runtime is presently **ARM64 release only**, because the published PDFium Android wheel has an ARM64 target; x86_64 debug-emulator builds deliberately lack a PDF runtime and fail closed when requested.
- `CasImportContract` rejects unknown Python engine identity, unknown issuers, parser warnings, incomplete reports, missing/future statement dates, invalid/oversized monetary fields, negative prices or quantities, mismatched family owners and ambiguous holding identities. It **only** matches a unique, exact normalized holding-name plus category already present in the unlocked owner's book, because the native v2 schema still lacks stable ISIN, folio and custody-account identity fields. New/unmapped holdings deliberately cannot be imported.
- The accepted statement lines are passed to the pre-existing **HMAC-sealed, atomic, explicitly user-approved statement reviewer**. A statement snapshot is never treated as a BUY. A reopened/reimported identical document is idempotent.
- **12 new parser-contract JVM tests** and **five malicious-PDF-input tests** were added. They are not credited as passed until the new CI reports are inspected.

**Important end-to-end limits:** This is a guarded initial integration path, **not verified complete CAS feature coverage**. No real CAMS/KFin/NSDL/CDSL/NPS encrypted PDF has been parsed on a supported ARM64 phone in this increment. Name-only matching is restrictive and not a substitute for ISIN/folio/account matching, as required for true multi-custodian onboarding. Parser-side metadata and dated statement completeness remain dependent on the immutable black box; some valid documents may be rejected instead of guessed. The release pipeline must demonstrate that the Python/Android PDFium wheel is actually installed and the ARM64 ABI can load it.

### Financial product parity

- `SavingsInterestEngine` now produces immutable review-only interest catch-up plans, explicit 31-March FY credit dates, quarterly FD/RD compounding estimates, PPF/EPF/SSY simple accruals and SGB/SCSS/POMIS/FRSB payouts, with maturity limits and already-posted-date suppression.
- Inputs continue to use the legacy app's July–September 2026 small-savings rates and FY 2025–26 EPF rate; no historical-rate database is installed. FD/RD loans/compounding/day-count variations and daily-balance PPF/SSY interest are **not exact account/passbook proofs**. Estimated credits are NOT automatically appended to the ledger.
- **10 new JUnit vectors** cover compounding, maturity, duplicate-date handling and scheme payouts. Additional tax, cost basis, net worth and look-through presentation parity remains a separate gate.

### Enforced vault authorization and privacy

- Production Hilt binding `DefaultVaultRepository` now checks `VaultAuthorization` for every financial read/write as well as its UI. Read/unlock requires an authenticated in-memory device session; writes, backup export/restore and statement approval additionally require a **fresh (30-second) credential**.
- Three new Android instrumentation cases assert that unauthorized repository reads/writes and review requests fail before any SQLCipher operation. The low-level `SqlCipherVaultRepository` remains directly instantiable for instrumentation, so a malicious or compromised in-process component is a **residual threat**, especially before proper app-specific authentication is implemented.
- Ledger export is explicitly ordered by `rowid` so a correction predecessor is restored before the correction; backup restore remains foreign-key checked and all-or-nothing.
- `android-native/scripts/security_preflight.py` fails CI on manifest backup/cleartext/export privacy regressions, missing `FLAG_SECURE`, missing OS credential gate, missing repository authorization, missing release signing guard and missing ledger integrity gates. **This is a static policy smoke check, not an independent AppSec assessment.**

### Broader Android and signed release build gates

- A separate [native API matrix](../../.github/workflows/native-device-matrix.yml) tests the existing two instrumentation modules under Google APIs x86_64 emulator **API 26, 30 and 36**. The ordinary Native Android M3 workflow still covers **API 35**. Each API records its own test reports; this increases emulator API coverage only once all jobs pass.
- Native workflow now attempts a minified **release AAB packaging smoke test** using an ephemeral CI-only RSA keystore and destroys the key and bundle afterward. This is not a Play artifact and the test certificate must never be distributed.
- The Gradle release configuration refuses `:app:bundleRelease` and `:app:assembleRelease` when any of four `GUD_UPLOAD_*` signing variables or the keystore file is missing. No upload private key or password is stored in this repository.
- [Native signed upload AAB workflow](../../.github/workflows/native-signed-aab.yml) is manual-only, requires exact acknowledgement `BUILD_SIGNED_AAB`, protected `play-release` environment and **owner-provided** four signing secrets plus pinned upload certificate SHA256. It checks the certificate, builds the minified bundle and uploads it as a short-retention artifact. This workflow **has not been run with production credentials** and does NOT submit to Google Play.

## 2. AppSec preliminary code-review findings

The findings below result from a code-oriented assistant review and automated regression tests, **not an independent penetration test**. Severity indicates prospective release risk rather than exploit demonstration.

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| GUD-SEC-01 | High | Native vault data operations initially could bypass UI-only login/session checks via repository injection | **Mitigated in this increment** by guarded production repository binding; Android tests and CI pending |
| GUD-SEC-02 | High | Native SQLite schema lacks durable ISIN+folio/account mapping, making arbitrary CAS holder mapping unsafe | **Fail closed** for unknown/ambiguous names; product mapping and migration still OPEN |
| GUD-SEC-03 | High | Cryptographic key belongs to Android Keystore, not an app-specific login password; device credential is not independent user identity | **OPEN — production authentication design** |
| GUD-SEC-04 | High | Real ARM64 PDFium/CASPARSER, encrypted PDF and multi-issuer correctness on real hardware have not been verified; malicious PDFs are untrusted native input | **OPEN — isolated parser fuzzing + ARM64 dynamic testing** |
| GUD-SEC-05 | Medium | App retains PDF password/Base64 copies in non-zeroable managed memory briefly while invoking Python | **Partially mitigated** with transient UI state, wiped CharArray/PDF byte buffers; minimize copies and heap exposure before release |
| GUD-SEC-06 | Medium | API 26–36 variations, 16 KiB memory page native ELF compatibility and backup/Keystore behavior vary by vendor | **OPEN — API emulator and physical device evidence required** |
| GUD-SEC-07 | Medium | Release-signing configuration previously had no owner-controlled fail-closed/pinned certificate mechanism | **Mitigated in source**, signed AAB smoke and protected signing pipeline pending |
| GUD-SEC-08 | Medium | Snapshot financial adjustments cannot prove purchase date/cost when source omits it | **Fail-closed for gains**; manual evidence/onboarding and statutory financial parity OPEN |
| GUD-SEC-09 | High | No independent third-party OWASP MASVS/MSTG dynamic mobile penetration test has been commissioned or completed | **OPEN**; do not market as security certified |

### Current provisional rating (source implementation, not an independent security score)

**Encryption at rest:** strong design intent, previously CI tested; **authentication and authorization:** partial, pending fresh device tests; **financial integrity:** append-only + transactional strong design intent, unknown investment/cost still open; **supply chain/PDF parser:** high residual risk until ARM64 hardware/fuzzing; **release/build:** protected configuration but unsigned real Play artifact untested. **Overall release security verdict: NOT APPROVED.**

## 3. Reproducible evidence needed before approval

1. **Native CI:** new financial and CAS JVM tests; production repository-access denial instrumentation, encrypted vault/backup/statement regression, new Compose owner/file-selection controls and signed release smoke must all pass. Download and count JUnit XML per suite; do not substitute the workflow status alone.
2. **API matrix:** API 26/30/35/36 emulator jobs must pass, including SQLCipher migration, Keystore, backup, session lock and Compose; identify emulator-only limitations.
3. **Real hardware:** Test at least an ARM64 Android 9/10 device, modern Android 13/14, Android 15/16, and a device with **16 KiB pages** where available. Test rotation/background/OS credential picker, process death, biometric enrollment change, unavailable Keystore, revoke document URI, insufficient space, cancelled PDF and encrypted PDF password, repeated import and backup recovery on a different device. Record OS, ABI, device OEM, output and JUnit/manual evidence.
4. **Parser acceptance:** Use consented, redacted or synthetic **real PDF fixtures**, including CAMS, KFin, NSDL, CDSL, NPS, wrong PDF passwords, oversized/malformed objects, malicious fonts/images and adversarial date/cost rows. No real PAN or statement content belongs in CI artifacts. Require strict issuer/date/owner/ISIN/folio reconciliation; implement enrolment/mapping for new holdings before declaring consumer CAS ingestion finished.
5. **Independent AppSec tester:** commission a separate auditor experienced in **OWASP MASVS / MASTG** for reverse engineering/static analysis, Android exported components, intent/URI injection, dynamic instrumentation, device unlocked/rooted/backup/clipboard tests, Keystore/SQLCipher key exfiltration, PDFium input fuzzing, injection attempts, session race testing, backup tamper/replay and signing/build provenance. Obtain external signed report, PoC evidence, retest and closure of all Critical/High findings.
6. **Play signing:** Owner creates and stores upload key in protected key manager, configures GitHub `play-release` environment reviewers and secrets (`GUD_UPLOAD_KEYSTORE_BASE64`, `GUD_UPLOAD_KEYSTORE_PASSWORD`, `GUD_UPLOAD_KEY_ALIAS`, `GUD_UPLOAD_KEY_PASSWORD`, `GUD_UPLOAD_CERT_SHA256`). Owner must verify cert match in Play Console and approve build. Never paste/upload key in chat.
7. **Policy/product:** Complete app-specific session protection, backed-up credential recovery, disclosure/privacy controls, complete financial product parity and a non-`-dev` release version before Play submission.

## 4. Explicit limits and handoff

- **Frozen CASPARSER protected:** `src/casparser-main/**`, existing Python invocation wrappers and pinned requirements are never edited. `casparser-runtime` references the same code at build time without modifying it.
- **No GrowUrDhan holdings or PDFs leave the device**, apart from Android document chooser reading a provider URI explicitly selected by the user. No finance-cloud backend is introduced.
- **Not all user-requested gates can be completed by code commits:** real signing credentials, real physical hardware and an independent external security report require the owner or authorized independent providers.
- **Do not mark commercial readiness complete** until verified ARM64 parser, all CI jobs, signed owner-key AAB, physical-device results and independent AppSec signoff.
