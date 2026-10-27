# Phase 3 base-market-release acceptance register — 2026-10-26

**Status: NOT ACCEPTED.** This is the acceptance decision register for the current native first-market-release scope, **not a declaration of completion**. It resolves two distinct decisions:
1. **M26 focused engineering milestone** may be accepted only when all first-release family/owner/custody/nominee/goal cases and exact-SHA UI/security acceptance pass.
2. **Phase 3 / market-release completion** requires all *in-scope* M24–M34, P01–P39, E01–E18, G00–G15 mandatory acceptance (as applicable), genuine CAS P18/P19, provider/physical-device/security/owner release approval. Green M26 tests alone cannot close Phase 3.

## Binding exclusion: owner-approved D57
Financial vault backup/export/import, automated SAF financial archive and financial-data recovery (P03/P28/P29, financial-recovery E05/E12, archive-only M24/M25/M26/M31/M34 tests) are **DEFERRED TO NEXT UPGRADE, never PASS**. They MUST NOT be silently reintroduced to satisfy an old gate. Offline account-password reset, normal CAS/CSV/PDF statement imports, SQLCipher, Keyguard and Play entitlement decisions remain IN SCOPE.

## Currently verified evidence
- M26 API35 [40442928626](https://github.com/shahvikask/kosha/actions/runs/40442928626) on SHA `8f5016def021e401065faf107d0b57c1583572cc`: original 144 JVM / 13 Android tests PASS; source+APK hashes and two synthetic 360px light/dark screenshots verified. **Independent visual inspection found unreadable dark owner-lens status text; therefore G14 visual acceptance did NOT PASS.**
- Repair candidate [40445609639](https://github.com/shahvikask/kosha/actions/runs/40445609639) on `7ff4d7c3a8618409507639a2c228cef0a3e3f641`: app and instrumentation compiled; original 144 JVM, 13 Android tests including light/dark synthetic screenshot regression PASS; **both additional real-books compact-screen tests FAILED** at Home navigation <48dp measured hit target (original evidence artifact `12708268011`). Not accepted.
- Targeted repair raised real native navigation min-size to 56dp without lowering the 48dp assertion; focused exact-SHA [40451375447](https://github.com/shahvikask/kosha/actions/runs/40451375447) on `cb3c5753c58a9e037efada2f0785845f58c7082c` **initially RUNNING—NOT VERIFIED**. This is a **two-case** targeted run; even if green, the final same-source 15-case or comprehensive gate still must pass.

## Latest focused diagnosis — 2026-10-27 UTC (supersedes early touch-height hypothesis)
- First two-case targeted [40451375447](https://github.com/shahvikask/kosha/actions/runs/40451375447), exact `cb3c5753c58a9e037efada2f0785845f58c7082c`, **FAILED 2/2** in actual real-books UI, despite app/test compile/JVM PASS. Original artifact `12710837714` and log verified: **Home bounds height=0.0px** in both themes, NOT a measured undersized rendered button. At 200% font the whole nav starts *off the viewport*, and Compose reports a clipped semantics node as zero. Thus prior assertion did **not** establish an actual small hit area; the 56dp minimum is retained as a safety margin, not claimed as the demonstrated root-cause fix.
- Test now explicitly calls `performScrollTo()` for **each** real native destination before checking horizontal bounds and >=48dp height, then scrolls back to the Whole household picker before selecting a member. Assertion threshold remains unchanged and diagnostic measured height remains visible. Replacement focused run [40452172431](https://github.com/shahvikask/kosha/actions/runs/40452172431), exact source `093cf507836e0404e92c6fc46fa9b3c3e27d6702`, **RUNNING/NOT VERIFIED** at creation. Wait for terminal + original ADB/JVM/APK provenance; only then schedule one 15-case M26/G14 gate. This is a test-correctness repair, not a bypass of accessibility.

## Acceptance closure criteria (unwaivable without explicit owner-approved change)
| Scope | Pass rule | Current stage |
|---|---|---|
| M26 owner/family/custody/nominees/goals | Exact-SHA full in-scope encrypted CRUD, edit/delete/close, guardian/minor/HUF, goals/nominees, restart/reopen and no cross-owner mutation, plus owner-lens cross-destination real-screen tests, original XML/ADB | Focused partial; real 360dp tests FAILED on previous SHA |
| E01–E03 / P33 / G14 | Real Material 3 light/dark screens, 360dp and large-screen, 200% text, 48dp targets, actual foreground-background contrast measurements >=4.5:1 normal and >=3:1 large/nontext, TalkBack navigation/order, real-device screenshots (synthetic-only public artifacts), verified official brand fidelity, **owner visual approval** | Partial synthetic; physical/manual/owner evidence OPEN |
| P18/P19, M28 genuine CAS | Original **real private issuer** document independently checked on consented **physical ARM64** device: issuer/folio/DP+client/PAN/date/rows/total, owner reconciliation, atomic reviewed SQLCipher persistence and restart, exact replay rejection and rollback; original financial values NEVER enter public CI/Git | NOT VERIFIED / private owner-device input needed |
| M27/M29/M30 and other P/E | Complete holdings/events and FIFO math, golden numeric parity, prices/official catalogs and UI, statements/interest/EMI, negative and restart tests, no guessed prices/costs | NOT FULLY ACCEPTED |
| M31–M33 | Settings/privacy/legal/data loss warning, background lifecycle, Gmail consent/multi-account, Play Billing trial/entitlement and release-signing preparation | Real OAuth/Billing/provider tests owner credentials required; OPEN |
| M34 final technical + external | ONE consolidated exact-source **G11/G14** full in-scope regression after fixes, no P0, checked original XML/ADB/APK/AAB and independent AppSec; Android API/ABI/device matrix incl physical ARM64, low-storage, lock/restart, security/privacy; owner signs release | NOT RUN / external signoff OPEN |

## Gate discipline
- Never call a queued, skipped, failed, older-source, or visually defective gate “PASS” for the new source.
- Preserve frozen legacy source and CASPARSER byte-for-byte; do not change another branch.
- Before dispatching an expensive emulator gate, run matching static checks and one focused two-case repair; avoid running full/duplicate gates just to poll for green.
- Real private data is verified locally with owner consent only. Public GitHub CI must receive no actual CAS/PAN/account, backup keys, vault passwords or screenshots of genuine financial data.
- Verify latest exact source separately from any later documentation-only commits; approval can only cite precisely scoped test evidence and provenance.

## Next required action
Inspect [40451375447](https://github.com/shahvikask/kosha/actions/runs/40451375447) terminal outcome, original 2-case ADB, 144 JVM XML and both APK SHA. If failing, diagnose measured node height and repair. If passing, dispatch one full **in-scope** corrected 15-case M26/G14 exact-source gate; inspect both synthetic PNGs and actual dark/light screen results. Then build/execute the remaining G14 matrix, genuine CAS device/owner acceptance, AppSec/provider/device/release gate as documented. Do not mark Phase 3 complete without those independent approvals.
