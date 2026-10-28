# Phase 3 — genuine CAS P18/P19 acceptance contract (first market release)

**State: OPEN / NOT ACCEPTED (2026-10-26).** This is in-scope under spec v1.3 and owner D57; encrypted financial archive backup/restore remains deferred and is NOT a substitute for statement imports.

## Trust and evidence boundaries
- Preserve frozen `src/casparser-main/**` and protected `scripts/cas_parse_server.py` byte-for-byte. Native `NativeCasPdfEngine` / `NativeCasOutputReviewGate` may parse and validate; they may not infer an absent account, PAN, price, cost basis, owner or historic trade.
- Synthetic JSON/SQLCipher, one fresh emulator success or a complete:true parser flag is **not** proof of a genuine private issuer CAS. Actual CDSL, NSDL, CAMS, KFINTECH and NPS variants require coverage separately where supported. NPS/other issuer limitations must be recorded, not hidden.
- Actual private PDFs, PANs, credentials, account identifiers, keys and any financial cell **must never** be committed, uploaded to public CI, logged, copied into prompts or included in GitHub artifacts. Genuine private device-only acceptance should retain only sanitized PASS/FAIL counters and binary provenance, with financial values compared locally under owner review.
- User-directed Android document selection is for **statement import**, not financial archive restoration (D57).

## Minimum independent positive acceptance
1. Capture an original actual issuer CAS on a separately approved **physical ARM64 Android device**, with explicit owner consent, current device admission and current versioned Terms. Observe real Android SAF document picker and frozen Chaquopy CASPARSER invocation. Demonstrate source file is bounded/read once and raw bytes are not retained/logged in public evidence.
2. Independently verify its issuer, statement date/window, PAN and exact constituent demat DP/client or mutual-fund folio identities against the original local document — no email/name guessing or PAN+ISIN-only account assignment. For mixed demat/folio CAS, require an exact row-specific source custody key; multiple ambiguous sources must be rejected.
3. Reconcile **every original holding** (including 0/nil closed rows), ISIN/folio, unit quantity, reported value and document totals to the locally viewed statement. Compare independently calculated expected totals before owner approval. No silent dropped rows, price/date inventions, cost-basis assumptions or mutations before approval.
4. Verify on-device owner choice and preview, immutable source bytes/review digest, exact matched SQLCipher owner/custody/holding and receipt. Approve and persist atomically to encrypted database. Independently reopen the vault and compare post-import rows, histories and ownership. No other owner's rows or append-only trade ledger may change.
5. Replay the **same source** and verify idempotent duplicate detection, no new financial row/ledger event, no change to imported snapshot, and correct original evidence. Test an altered source, wrong PAN, missing/duplicate DP/client/folio, incomplete parser totals, stale approval token, revoked OS admission and cross-owner match as separately rejected, database unchanged.
6. Test process stop/restart, locks and interrupted SQLCipher transaction. Read original instrumentation evidence, source SHA, parser hashes and app/test APK hashes on the **same** SHA. Physical-device, AppSec and owner signoff remain independent.

## Development / validation lanes
- **Automatable with synthetic artifacts:** frozen parser format contract, reject/accept boundaries for structurally valid output, exact account-specific SQLCipher staging, snapshot/lot invariants, replay and rollback. Run preflight + targeted CI first, with exact SHA and original XML/ADB.
- **Cannot be automated through public GitHub with private customer data:** independent genuine CAS positive acceptance and financial ground truth. Record `PRIVATE OWNER DEVICE VERIFICATION PENDING`, not `PASS`. Use a local-only owner-controlled evidence worksheet for issuer/date, per-account counts, per-account mismatch count, total discrepancy status and approval status; do not upload underlying values.
- **Next bounded task after M26 in-scope gate:** check whether any issuer-specific genuine CAS can be verified on a private device. If unavailable, continue synthetic negative/owner-isolation improvements and do not claim P18/P19 fully accepted.

## Outstanding release evidence
G14 accessible 360dp/200% font/TalkBack and multi-theme screenshots, independent encrypted CRUD restart/no cross-owner writes, P18/P19 genuine issuer CAS, physical ARM64/API matrix, AppSec, privacy/legal, billing/Gmail provider credentials and owner signoff remain OPEN. Deferred financial backup tests are neither blockers nor passed evidence for this base release.
