# Legacy source inventory — M00, not a native implementation

Observed from `shahvikask/kosha` commit `af96dcc8967ab595e1d9acee78e29e500cb99a9e` (tree `1aa27c066362a4f1bed52b51c3e6a9eb35d91323`). That commit is the parent `21d8ffbd48ed65c1d6eb02d09dfba571c7b4ef75` plus `.chatgpt-write-access-check-2026-10-08.txt`. `GUD-Android-Redevelopment` adds only instruction files on top of it. No `src/` or `src-tauri/` file was edited for this inventory.

Status of every product requirement remains **NOT STARTED**. This document is source evidence for later milestones. It is not M00 acceptance. Native scaffold, Compose screens, and brand exports are not created.

## Screens and gates

Primary tabs in `src/App.tsx`: `HOME`, `HOLDINGS`, `ACCOUNTS`, `DOCUMENTS`, `FAMILY`.

| Surface | File | Role |
|---|---|---|
| Licence wait | `src/App.tsx` | “Checking licence…” before any screen |
| Closed | `ClosedScreen` in `src/components/screens/TermsGate.tsx` | Terms declined |
| Database error | `src/App.tsx` | Vault open failure |
| Onboarding | `src/components/screens/OnboardingScreen.tsx` | First-run, including Hindi path present in source |
| Licence | `src/components/screens/LicenseScreen.tsx` | Trial and SKU purchase UI |
| Auth | `src/components/screens/AuthScreen.tsx` | Register, login, recovery |
| Home | `src/components/screens/HomeScreen.tsx` | Dashboard |
| Holdings | `src/components/screens/HoldingsScreen.tsx` | List, filter, history entry |
| Accounts | `src/components/screens/AccountsScreen.tsx` | Custody accounts |
| Documents | `src/components/screens/DocumentsScreen.tsx` | Statements, tax, records |
| Family | `src/components/screens/FamilyScreen.tsx` | Members, goals, settings, Gmail switches |
| CAS review | `src/components/screens/CasConfirmPanel.tsx` | Import confirmation |
| Feedback | `src/components/screens/FeedbackPrompt.tsx` | Rating prompt |
| Terms | `src/components/screens/TermsGate.tsx` | Terms and privacy gate |
| Add holding | `src/components/modals/AddHoldingModal.tsx` | Eight-category wizard |
| Add record | `src/components/modals/AddRecordModal.tsx` | User money events |
| Holding detail | `src/components/modals/HoldingDetailModal.tsx` | Read, edit, close |
| Emergency sheet | `src/components/modals/EmergencySheetModal.tsx` | Plaintext family dossier |
| Brand | `src/components/common/BrandLogo.tsx` | Wordmark and mark |
| Errors | `src/components/common/ErrorBoundary.tsx` | Render failure |
| Privacy | `src/components/common/PrivacyValue.tsx` | Masked amounts |

Eight asset categories in `src/types.ts`: `EQUITY`, `MUTUAL_FUND`, `GOLD_SGB`, `FIXED_INCOME`, `REAL_ESTATE`, `SPECIALISED`, `CASH_BANK`, `LIABILITY`. Subcategories are the arrays in `ASSET_SUBCATEGORIES` in that file. Eleven event types, matching the ledger check constraint: `OPENING_BALANCE`, `SNAPSHOT_ADJUSTMENT`, `BUY`, `SELL`, `BONUS`, `SPLIT`, `DIVIDEND`, `INTEREST`, `VALUATION_UPDATE`, `REPAYMENT`, `CORRECTION`.

Field-level mapping of every form input is **not finished** in this session. M24 and M27 still have to enumerate each `HoldingDetails` key. Do not treat this screen list as complete field parity.

## Commands

Android plugin methods in `GrowUrDhanPlugin.kt` and the desktop `#[tauri::command]` functions in `src-tauri/src/lib.rs` use the same 25 names:

`diag_log`, `vault_blob_load`, `vault_blob_save`, `play_licence_status`, `play_purchase`, `play_restore`, `play_review`, `play_open_listing`, `cas_health`, `cas_parse`, `gmail_connect`, `gmail_disconnect`, `gmail_status`, `gmail_search`, `gmail_fetch`, `gmail_advance`, `prices_refresh`, `diag_status`, `diag_set`, `backup_pick_folder`, `backup_clear_folder`, `backup_write_scheduled`, `backup_save`, `backup_open`, `app_exit`.

TypeScript calls them through `nativeCommand` in `src/lib/license.ts`, not a second command set. `play_licence_status` is called from `src/lib/license.ts`. `play_review` is called from `src/lib/feedback.ts`. Gmail commands are called from `src/lib/gmailSync.ts`. There is no separate Gmail command beyond those six.

Kotlin files beside the plugin, at the same commit: `Diag.kt`, `GmailClient.kt`, `PriceFetcher.kt`, plus `network_security_config.xml` and `data_extraction_rules.xml`.

## Schema version 9

`src/db/schema.ts` sets `SCHEMA_VERSION = 9`. SHA-256 of that file is `8b98666f1d4954b1f3bb09e199dd504dd23b1155cf81becfcbd6fcbdbcb7e3a1` (15,121 bytes).

Money comments in that file: amounts are integers scaled by 10,000; quantities are integers scaled by 100,000,000. The ledger check allows only the eleven event types above. `tg_ledger_event__no_update` and `tg_ledger_event__no_delete` abort updates and deletes. `tg_tax_rule_set__no_update` aborts tax-rule updates. `LEDGER_NO_DELETE_TRIGGER` is the same delete trigger, recreated after a permitted family-member cascade.

Tables created by version 9:

| Table | Introduced | Notes |
|---|---|---|
| `vault_meta` | v1 | schema version and metadata |
| `entity` | v1, columns through v9 | family member; v9 adds `cas_id_source`, `pran_source`, `cas_sync_after`, `pran_sync_after` |
| `account` | v1 + v3 ids | demat/folio/bank custody |
| `instrument` | v1 + v3 | ISIN, AMC, RTA, scheme |
| `holding` | v1 through v7 | prices, EMI, `details_json`, `acquired_on` |
| `ledger_event` | v1 | append-only |
| `price_observation` | v1 | dated prices |
| `import_batch` | v1 + v3 | statement import header |
| `source_document` | v1 | file hash |
| `statement_position` | v1 + v3 | snapshot rows |
| `tax_rule_set` | v1 | immutable once installed |
| `liability` | v1 | loans |
| `goal` | v1 | targets |
| `setting` | v1 | key/value |
| `egress_log` | v1 | network audit |
| `listed_security` | v5 | NSE/BSE/AMFI catalog |
| `vault_account` | v6 | local login; PBKDF2 fields and two question hashes |
| `gmail_seen` | v8 SQL | primary key `(message_id, attachment_id)` |

v8 entity columns, applied before v9: `cas_id`, `pran`, `gmail_sync`. Migrations are in `src/db/repository.ts` `initialise()`. A new vault is created at version 9. This inventory does not open a database and did not run the migration tests.

`schema.ts` comments say the Android database is SQLCipher. Specification section 1.4 says the legacy on-device snapshot is **not** SQLCipher and that the app password does not decrypt `vault.enc`. `src/db/encryptedStore.ts` shows an AES-GCM envelope (`GUDENC01`) and an Android Keystore file store around the WASM snapshot. The specification controls that interpretation. The comment in `schema.ts` was not edited.

## Frozen parser and brand hashes

SHA-256 of every protected parser file, plus the brand files named in Section 6A, is in [legacy-parser-sha256.txt](legacy-parser-sha256.txt). The manifest of those 39 path-and-hash lines is `1b19b126557b3054503139c7e7980528f14f18cde8688b139272b596685dc6d4`.

`src/casparser-main/` has 28 files. Protected scripts: `scripts/cas_android.py`, `scripts/android_casparser_isin.py`, `scripts/android_pydantic.py`, `scripts/cas_parse_server.py`. None of these bytes were changed.

Git blob of `public/brand/mark.svg` is `52fd0aa1a9262e6d2ba8ab431c2955c6df5c417c`. That matches the SHA named in Section 6A.1. File SHA-256 of the same SVG is `d612b12009911a06796e2fb2c75fa92247db87151a6344ae850acf04af823efc`. `docs/design/BRAND-INVENTORY.md` and native logo exports were **not** created. Section 6A still requires those before any design change.

## Explicitly not done

No Kotlin or Compose code. No `android-native/` scaffold. No financial calculation port. No GitHub Actions run. `GUD-Android-Gmail` was not committed to. `vikaskunjbiharishah-ui/GUD` was not opened.
