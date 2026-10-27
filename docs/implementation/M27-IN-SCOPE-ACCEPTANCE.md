# M27 — in-scope holdings and immutable-event acceptance (first market release)

**2026-10-27 UTC status: SOURCE PARTIAL / NOT ACCEPTED.** Binding: specification v1.3 §§4.1–4.3, 6A, 12, 17, owner D57. This document does not change the frozen reference or approve a parity deviation.

## Original evidence that must be preserved

- Full in-scope M26/G14 automated source gate [40457888895](https://github.com/shahvikask/kosha/actions/runs/40457888895), tested `26ffcea1aa470af97565d3768540b19d58bf536c`: 144 JVM / 73 API35 passed; **not a current-SHA M27 gate or manual G14 signoff**.
- First M27 immutable account-provenance gate [40463137619](https://github.com/shahvikask/kosha/actions/runs/40463137619), tested `d6fadea9b1c8b5e6ae9bbff183673483b11a1da3`: 144 JVM / 3 API35 SQLCipher passed; ledger and even zero-unit reviewed statements prohibit custody reassignment.
- New M27 cost/review production commit [461c2d5](https://github.com/shahvikask/kosha/commit/461c2d5b36471b0f4b74555b6cc2e8fe78b8b11c), workflow correction [291f3bd](https://github.com/shahvikask/kosha/commit/291f3bd92acb7cf48db828931c330309afed9d7b). First focused [40466003658](https://github.com/shahvikask/kosha/actions/runs/40466003658) **FAILED before build or tests** due double-escaped `grep -E` parenthesis; fixed to single escape. Corrected [40466067201](https://github.com/shahvikask/kosha/actions/runs/40466067201) at exact `291f3bd92acb7cf48db828931c330309afed9d7b` was **RUNNING — NOT VERIFIED** during record creation. Check terminal plus original 2-case ADB, JVM XML, source marker and APK bytes/hash TXT before claiming pass.

## P09–P15 current acceptance trace

| Scope | Observed implemented source | Explicit remaining evidence / work |
|---|---|---|
| P09 taxonomy | `NativeAssetTaxonomy` contains eight exact categories and subcategories; JVM taxonomy assertion exists | Search/filters by every category and meaningful category-specific form states; actual G14 screen checks |
| P10 holding wizard | SQLCipher encrypted create/opening/unknown-cost test exists. New Compose form defaults cost to Unknown; Known requires separately entered verified price; review/confirm, revision invalidation, two fictional UI tests authored | Verify corrected focused workflow; category-first conditional wizard, actual cost-vs-value persistence through UI and restart, errors and full G14 |
| P11 specialist metadata | Original metadata preserved in `details_json`; explicit columns for rates, maturity, EMI, institution/location | Full original `HoldingDetails` key-by-key mapping, type-specific editors/read-back; raw JSON field is not full parity |
| P12 detail/edit/archive/close | Encrypted account-provenance guard 3/3 pass; zero position and immutable ledger remain distinct; archive requires zero | Full specialist holding detail, acquisition corrections, owner-targeted UI, close/confirm semantics and restart |
| P13 user money events | Ten user kinds exposed in a shared Compose form; append-only SQLCipher table and oversell rollback tested | Conditional kind-specific validation and screens, fee/total/quantity semantics, negative cases and owner boundaries |
| P14 split/correction/snapshots | JVM FIFO split, fractional rejection, correction-cycle and competing-correction cases; source-only SNAPSHOT_ADJUSTMENT blocked from user entry | Original golden vectors, registrar-only adjustment acceptance, wrong-owner/stale correction rejection, complete event semantic/chronology parity |
| P15 list/history | Owner filter, category/search, full event IDs in repository and read-only history screen | Closed/archived/account/sort filters, virtualized lists and cross-destination historical accessibility, owner/device acceptance |

## Mandatory next milestone gate

Close P09–P15 source and real Compose gaps, verify specialist data preservation and ten event types without inventing cost/price, run cheap JVM/compile and targeted encrypted instrumentation before **one** comprehensive exact-SHA M27 gate (API35/required API matrix, original XML/ADB/APK hashes, negative and restart). No skipped/deferred/older-SHA test can be counted as passed. Independent manual G14 owner review is later and separately required.

**D57:** P03/P28/P29 and financial recovery E05/E12 plus archive-only suites are **DEFERRED NEXT UPGRADE — NOT PASSED**. Preserve `android:allowBackup="false"`; normal CAS/PDF/CSV imports, offline account-password recovery, SQLCipher and Keystore remain in scope.

**Independent release blockers:** Genuine private issuer CAS P18/P19 consented physical ARM64 and local-only reconciliation/replay, full G14 manual TalkBack/contrast/owner visual, AppSec/device matrix, providers/Gmail OAuth/Billing, correct production package ID/signed minified AAB, broader G11 and owner release acceptance. Phase 3/market release NOT ACCEPTED.
