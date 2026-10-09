# GrowUrDhan — M8 As Built: Portable Account Recovery and CAS Parser-to-Holding Bridge

**Date:** 2026-10-09  
**Permitted source branch:** `GUD-Android-ChatGPT`  
**Verified preceding baseline:** `376f2887e1b4f8110770d3ae3a76217c7419e848` with Native Android [#37894920090](https://github.com/shahvikask/kosha/actions/runs/37894920090), API matrix [#37894920089](https://github.com/shahvikask/kosha/actions/runs/37894920089) and Kosha CI [#37894920140](https://github.com/shahvikask/kosha/actions/runs/37894920140) reported passing. The tests in this M8 increment require their own independent JUnit and artifact verification; **do not count them as passed until the workflow results are examined**.

**Status:** Source implemented; pending fresh Native Android CI and a separate physical-device acceptance signoff. Release remains **pre-production**. No Google Play publication, owner upload-key signing or real-device CAS parsing is claimed.

## 1. Existing M7 owner/account/holding boundary used by this increment

A newly created owner can now navigate **Vault → Manage accounts & holdings**, add a nickname-only CAMS/KFINTECH registrar account (after a new system credential challenge), and enroll a mutual fund using an exact owner + registrar + ISIN + folio association. The repository inserts the empty zero-unit holding and its CAS identity together, transactionally, into an SQLCipher v3 vault. It does not fabricate purchases, acquisition dates or cost basis. A registrar identity cannot be linked to a holding or account of a different family owner. The schema's identity mapping and ledger triggers remain immutable.

The **Statements** destination will offer document selection to enrolled owners only. Android's Storage Access Framework selects a PDF; the existing Kotlin bridge validates its byte-level SHA-256, calls the frozen parser through a release-only on-device Python/PDFium bridge, and refuses any parsed line without a unique enrolled owner/registrar/ISIN/folio identity. User consent then goes through the pre-existing HMAC-sealed, stale-book-aware, append-only statement approval transaction. Unknown purchase cost must remain unknown.

**Important limitations:** The Kotlin contract and encrypted mapping are tested with synthetic fixtures, but the app has *not* yet passed a real ARM64 frozen-PDFium parsing test with consented CAMS/KFin statements. The release Python module is ABI-dependent. The parser may omit ISIN/folio, show issuer-specific account names, classify a fund as EQUITY rather than MUTUAL_FUND, or include unverified joint-owner information; fail closed rather than silently equating schemes by name. A user-selected owner is not independent proof of that person's legal identity.

## 2. M8 changes: Android parser-shaped contract tests

New `NativeCasIdentityBridgeInstrumentedTest` supplies synthetic **frozen-parser-shaped JSON** to the actual `NativeCasPdfReader`, using a local test PDF byte stream and fake Python engine. Five checks cover:

1. SHA-256 calculated from the selected PDF bytes, and exact owner/registrar/ISIN/folio matching to the enrolled fund.
2. Wrong family owner and absent enrollment registry refusing any name-only fallback.
3. Wrong ISIN, folio, registrar or fund name blocking import.
4. Incomplete parsed totals and parser warnings blocking import.
5. Mutated input bytes changing the calculated fingerprint despite unchanged parser text.

This proves the Kotlin/Android **boundary contract**, not the authenticity or completeness of the immutable Python engine itself; the tests do **not** load ARM64 native PDFium or use a real investor statement. Never label them as full CAS ingestion acceptance or an independent security audit.

## 3. M8 changes: password-protected portable account recovery

The native Vault screen now offers **Backup & recovery**, including a restore entry point when no native vault exists. `PortableRecoveryScreen` and `PortableRecoveryViewModel` use the already implemented `VaultRepository.exportEncryptedBackup` and `restoreEncryptedBackup` methods:

- **Export:** A user with an open vault chooses the destination through SAF. Leaving for the chooser revokes the old vault session. Only afterward does the app request a new portable backup password (12–1024 characters, with confirmation), demand a fresh Android device credential, reopen the SQLCipher vault, create the password-encrypted portable backup and write **only ciphertext** to the selected URI.
- **Restore:** The user selects a portable backup file through SAF. A new backup password and fresh Android device credential are required after file selection. The file is read with a bounded reader; only `GUDNBACK` headers and at most 34 MiB are accepted for further authenticated decryption. The repository validates password/AES-GCM, format, SQL constraints and foreign keys and restores transactionally **only when every financial table is empty**. A nonempty financial book must never be silently overwritten or combined.
- **Security:** The backup password is not the Keystore-wrapped on-device database key, never goes to logs, disk preferences or SavedStateHandle, and the Activity/operation attempts to zero char arrays when cancelled or destroyed. Kotlin `String` input and ephemeral framework copies cannot be reliably erased; full password hygiene and race testing are pending. Source/destination URI is retained only during the current Activity flow, never as a durable file permission. `FLAG_SECURE` remains enabled.
- **Failure mode:** A corrupted or incorrect-password backup leaves an existing nonempty financial book intact. On a new phone, opening a new empty vault before a failed restore can leave that **empty** vault initialized; retry is allowed, and no old phone vault is erased. A partially written export document may be unusable but contains no plaintext; the owner must verify recovery from a separate device before retiring the old phone.

The new `PortableBackupBytesTest` JVM cases check header/short-input rejection, oversize stream rejection, stalled readers, and immutable production size caps. The new `PortableRecoveryShellTest` Compose cases cover recovery navigation without vault creation and password confirmation gating. M8 adds **5 parser Android tests + 5 backup-reader JVM tests + 2 Compose UI tests**; all retained golden financial, authorization, SQLCipher, backup-format and CAS identity tests remain required CI gates. These tests do **not** automatically demonstrate the system screen-lock dialog or provider URI permissions on a physical device.

## 4. Replacement-phone recovery procedure: manual acceptance protocol

**Prerequisites:** The owner has safely copied a previously validated portable encrypted backup and knows its **separate backup password**. The replacement phone has a configured secure Android screen lock. No original SQLCipher device key is transported; the backup carries logical financial data encrypted under a different password.

1. On the original authorized phone, unlock the native vault, navigate to **Backup & recovery → Export encrypted backup**, choose a private destination, enter and confirm a long backup password, and confirm the Android credential. Check the backup file is nonempty. Keep the password out of the same backup location.
2. On a separate **test** phone with no real financial vault, install the same or a newer supported native build. From **No native vault found**, choose **Recover from encrypted backup**. Select the file, enter the backup password and authenticate with that phone's device lock. Never restore into an existing nonempty financial book.
3. Verify **every owner, account nickname, ISIN/folio mapping, holding, ledger event, snapshot import, statement position and correction lineage** against an offline redacted comparison. Check SQLCipher integrity, foreign-key count zero, and that unknown cost remains unknown. Reopen the app and verify persistence after a process kill.
4. Deliberately attempt a wrong password, truncated ciphertext, tampered GCM tag, unavailable ContentResolver URI, provider cancellation and insufficient disk. Assert no modification to an existing financial vault and no plaintext artifacts, PAN or password in screenshots, clipboard, logcat or backup directory.
5. Test locking and rotating the phone during the picker, credential dialog, long KDF, file copy and restore. Confirm that expired authorization cannot resume a write without fresh consent. Record any race as a release blocker and fix/retest.
6. **Only after** full replacement-phone validation may the owner consider discarding or wiping the previous device. A lost Keystore key without a valid portable backup is **not recoverable** by GrowUrDhan; no custodial recovery server exists.

**Evidence required:** device brand/model, Android API, ABI, page size, build SHA and signing certificate **fingerprint only**, test timestamps, pass/fail/issue links, redacted before/after counters, vault integrity results and auditor signatures. Never upload actual financial PDFs, raw PANs, restore passwords, encryption keys or signed user-specific backup files to CI/chat.

## 5. Physical-device acceptance matrix (NOT EXECUTED BY THIS ASSISTANT)

| Test class | Minimum target | Status / evidence |
| --- | --- | --- |
| Baseline security + locked/expired session | Physical Android 9–10 ARM64 phone | **NOT RUN** |
| Scoped-storage SAF URI access and external picker resume | Physical Android 12–13 ARM64 phone | **NOT RUN** |
| Biometric enrollment changes, screen-lock revoke and Keystore | Physical Android 14–15 ARM64 phone | **NOT RUN** |
| 16 KiB memory page compatibility and ARM64 native PDFium loader | Compatible physical Android 15/16 device with 16 KiB pages | **NOT RUN** |
| End-to-end genuine CAMS and KFINTECH CAS fixtures to enrolled holdings | Compatible ARM64 **release-candidate** build; explicit owner's consent, redacted expectations | **NOT RUN** |
| Wrong owner/folio/ISIN, duplicate statements and missing historical cost | Same physical devices | **NOT RUN** |
| Secure recovery of v1/v2/v3 portable formats to replacement hardware | Two distinct compatible physical phones | **NOT RUN** |
| Independent OWASP MASVS/MASTG dynamic assessment and follow-up retest | Qualified external reviewer | **NOT RUN** |

API 26/30/35/36 **emulator** CI results are useful but do not satisfy these physical-hardware, 16 KiB, biometric/KDF performance or native PDFium requirements. Debug emulator builds do not package the release-only ARM64 Python runtime.

## 6. Production signing: existing guarded pipeline, owner approval required

The existing [manual Play-upload AAB workflow](../../.github/workflows/native-signed-aab.yml) is configured to require `workflow_dispatch` acknowledgement `BUILD_SIGNED_AAB`, a second input `approved_commit_sha` naming the exact 40-character owner-approved Git SHA, protected GitHub `play-release` environment, pinned certificate SHA-256, a non-`-dev` versionName, post-build AAB signer verification, and all four upload-signing environment variables. It does **not** publish to Google Play. The app's Gradle configuration fails closed if `GUD_UPLOAD_KEYSTORE_PATH`, `GUD_UPLOAD_KEYSTORE_PASSWORD`, `GUD_UPLOAD_KEY_ALIAS` or `GUD_UPLOAD_KEY_PASSWORD` is absent; the GitHub workflow provides the keystore via protected `GUD_UPLOAD_KEYSTORE_BASE64` and pins `GUD_UPLOAD_CERT_SHA256`. No signing secret is stored in Git or accepted in chat.

**Owner-operated release checklist (all OPEN):**

1. Approve final app version/versionCode, switch from `0.3.0-dev` to a release version, verify package `in.growurdhan.app`, Play privacy and backup declarations, financial claims and licence/billing.
2. Create or authorize the production **upload key** using secure key management. Verify its *public* SHA-256 certificate fingerprint matches the fingerprint registered with Play App Signing. Protect `play-release` environment with required reviewer(s) and restrict signing to approved commits/tags.
3. Run the [Native M3 tests](../../.github/workflows/native-android.yml), [API matrix](../../.github/workflows/native-device-matrix.yml), immutable CASPARSER guard, full parser real-phone acceptance, backup migration and an independent MASVS audit with no unresolved Critical/High findings.
4. The owner manually runs the protected AAB workflow only against the approved release candidate. Confirm its certificate pin and `jarsigner -verify`, externally validate AAB via `bundletool validate`, inspect targeted ABI and Play 16 KiB page-size compliance, and record AAB SHA-256 and certificate fingerprint. Do **not** include secrets or backup contents in artifacts.
5. Conduct internal testing on at least one replacement phone and multiple Android versions before proposing a staged Play release. Google Play submission is a **separate owner authorization and workflow**, not completed by a signed AAB build alone.

**Do not** run the owner signing pipeline or upload/store production key material until the owner expressly configures and approves it. An ephemeral CI test-signed bundle is never a production-signed artifact.

## 7. Residual risks / release disposition

- The added Android bridge tests are synthetic; **real PDF parser-to-holding verification remains OPEN** until ARM64 hardware tests with redacted truth data pass.
- Account naming/identity enrolment is user-declared. Exact ISIN/folio matching prevents fuzzy accidents, not identity fraud or joint-CAS legal ownership ambiguity.
- Password fields necessarily touch ephemeral Kotlin Strings and Java heap; process death and provider lifecycle behavior need independent review.
- Device credential != GrowUrDhan-specific password; an independent application-login and session security specification is still required.
- The existing restore engine is non-destructive and uses AES-GCM, but **cross-phone recovery acceptance has not yet been observed**.
- Neither physical security testing nor owner's Play signing can be asserted complete from this repository change. Security review, financial/tax parity, privacy validation, versioning and Play release approval remain OPEN.

**Next required verification:** Inspect the new Native Android and Kosha CI runs and download the JUnit archives. Explicitly count the five parser-bridge Android cases, five backup-reader JVM cases, two Compose recovery cases and all pre-existing golden/SQLCipher/CAS enrolment, migration and backup recovery regression suites. Verify physical-device results separately and do not infer them from emulator tests.
