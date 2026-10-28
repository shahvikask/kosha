# M27 P09/P10 — requirement-level evidence checklist
**Checkpoint:** 2026-10-28. Source `6965c40cc0b92d88647c59f9e5485c025a2c342b`; focused run [40661694569](https://github.com/shahvikask/kosha/actions/runs/40661694569), original artifact `12804837207`.

This checklist intentionally separates implemented source, exact-SHA automated evidence, and full acceptance. It is **not** a full Phase 3 percentage denominator.

| Original category ID | Native category/subcategory selection | Real Compose review exercised on exact SHA | SQLCipher zero-position creation / encrypted reopen tested on exact SHA | Full source field/negative/G14 parity |
|---|---|---|---|---|
| EQUITY | SOURCE | PASS — equity default & Other | PASS — eight-category restart case | OPEN |
| MUTUAL_FUND | SOURCE | PASS — MF plan DIRECT | PASS — eight-category restart case | OPEN |
| GOLD_SGB | SOURCE | PASS — gold category | PASS — eight-category restart case | OPEN |
| FIXED_INCOME | SOURCE | PASS — Bank FD cumulative | PASS — eight-category restart case | OPEN |
| REAL_ESTATE | SOURCE | PASS — real estate | PASS — eight-category restart case | OPEN |
| SPECIALISED | SOURCE | PASS — specialised | PASS — eight-category restart case | OPEN |
| CASH_BANK | SOURCE | PASS — cash/bank | PASS — eight-category restart case | OPEN |
| LIABILITY | SOURCE | PASS — liability | PASS — eight-category restart case | OPEN |

**Evidence interpretation:** 8/8 category review paths are covered by API35 Compose tests; 8/8 zero-position records are checked together by one genuine SQLCipher restart case. These are two narrow evidence dimensions, not eight complete end-to-end specialist field audits. Invalid acquisition date is rejected before review and a corrected valid leap-day can be confirmed in the API35 run. User-selected explicit unknown cost is not inferred from valuation. Original tests also verify specialist metadata edit preservation and cross-owner linked-holding rejection.

**Required next acceptance dimensions (OPEN):** stepwise Compose wizard and full field-by-field source mapping for the original `HoldingDetails` keys; per-category positive and negative populated specialist field creation/edit/restart; custody account types, cross-owner protection and goal selection through UI; known acquisition cost validation and nonzero event/opening reconciliation; archived/edited detail negative cases; G14 360dp/light/dark/200%/TalkBack/contrast; owner signoff. Genuine P11 issuer/property authority, CAS P18/P19 and release gates remain independently OPEN.

**Formal status:** P09 PARTIAL; P10 PARTIAL; P09–P15 accepted 0/7. No milestone/Phase 3 acceptance implied. D57 backup/recovery deferral retained.
