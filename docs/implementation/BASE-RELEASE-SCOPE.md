# GrowUrDhan base market release — approved feature scope (2026-10-26)

**Owner decision D57. Active release contract, superseding conflicting 2026-10-13 specification v1.2 text.** Read with the updated native redevelopment specification (v1.3), AGENTS.md, parity register, and handoff.

## IN — first market release
- Secure on-device account registration and login, Android credential check, protected SQLCipher data, account password rotation and independent offline account recovery code (P02). An **account password-reset code is not a financial-data backup or vault restore**.
- Family/ownership/custody/nominees/goals, ledger/holdings/transactions, financial calculations, statements and approved CAS/CSV/PDF imports, prices, dashboards, privacy and legal UI, security and release verification.
- User-directed document picks for **statement imports** (Android SAF) remain permitted. "Disable backup SAF" does not mean disabling statement imports, Downloads or Android system pickers for unrelated content.
- License/purchase **entitlement restoration** remains in scope; it does not restore the financial database.

## OUT — future upgrade only (disabled, not silently removed from history)
- All user-facing financial vault backup/export, backup-file picker, legacy KOSHA1/GUDVLT01 imports, native GUDNAT01 recovery, encrypted archive staging/preview/owner-approved publication, manual crash resume, backup schedule/retention, auto/on-open/on-CAS backup and external SAF archive writer.
- P03 (financial backup import), P28, P29, E05 (cross-device financial recovery lifecycle), E12; M24/M25 archive-restore subgates, M26 archive roundtrip, M31 backup-policy subgate, M34 two-phone backup/restore subgate. Mark **DEFERRED BY OWNER — NEXT UPGRADE**, never PASSED.
- This is a market-release scope exemption, **not** authorization to bypass encryption, Keystore, admission, SQLCipher integrity, data-loss rejection or existing security tests of in-scope features.

## Enforcement and history
1. Production MainActivity must provide no archive restoration state, archive ActivityResult picker, archive publication callback or restore navigation; login must show no Restore encrypted backup action.
2. AndroidManifest must keep `android:allowBackup="false"`; do not enable cloud/device auto-backup. No background backup scheduler may be active.
3. Historical archive implementation and tests are retained in source for separate future-upgrade work, not included as passing base-release proof. Deferred tests may be excluded **explicitly** from the base-release gate, never quietly counted as passed. A separate future-upgrade acceptance project must re-enable and verify them.
4. All surviving release requirements remain binding: owner-scoped encrypted CRUD, CAS positive genuine private acceptance (P18/P19), payment/provider and real-device limits, G14 design, exact-SHA build/security gates. M26 family/ownership acceptance still requires its in-scope tests, but **not** the deferred archive roundtrip.
5. Product communication must warn that **the first release does not support exporting, backing up or transferring financial vault data. Device loss/uninstall can cause permanent local data loss.** Do not imply a backup or recovery option exists.
6. The legacy branch, frozen parser, archived original evidence, past CI outcomes and archived requirement language are preserved as historical records. This scope amendment controls active market-release checklists wherever historical documents differ.

## Next upgrade requirements (not release commitments)
Design, secure implementation and independent tests for archive interoperability, explicit owner/device proof, immutable restoration claims, replay resistance, no-overwrite, low-storage/crash cases, physical cross-device end-to-end acceptance, SAF scope, data-loss policy, and verified export-before-publish.
