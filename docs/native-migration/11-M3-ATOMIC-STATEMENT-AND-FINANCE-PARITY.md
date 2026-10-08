# GrowUrDhan — M3 Follow-On: Atomic Statement Approval and Financial Parity

**Date:** 2026-10-08  
**Source branch:** `GUD-Android-ChatGPT`  
**Status:** Source implementation committed; **new GitHub CI and Android instrumentation results pending**.  
**Previously verified baseline:** `8eecc195` (documentation-only commit) with prior v2 ledger verification and 46 passing JVM/Android tests. This document does NOT assert that the new additions have passed.

## 1. Purpose and trust boundaries

The preexisting `PreviewStatementReconciliation` use case presented differences between a custodian statement and the current portfolio, but separate reads and writes created a **time-of-check/time-of-use risk**. A family member could see a proposed change, another operation could modify holdings or events, and the stale proposal could then be written.

The new **explicit two-phase review and approval contract** retains CASPARSER as an untouched black box, does not auto-import documents or schedule background writes, and does not claim user/session authentication exists yet:

1. `PrepareStatementReview` calls `VaultRepository.prepareStatementReview(snapshotId, ownerId, sourceSha256, lines)`, taking a coherent SQL read transaction.
2. The review computes a SHA-256 digest of *all seven whitelisted vault data tables* in stable row order. It runs the pure Kotlin `SnapshotReconciler` and constructs a `ReviewedStatement` containing the observed source lines, source SHA, snapshot ID, full plan, and book digest.
3. The repository adds a per-process random **HMAC-SHA256 seal**, binding the receipt to the plan, source data, owner and book digest. A receipt copied or modified by a caller cannot be accepted with an altered proposal. The seal deliberately expires across application process restarts; another review is required.
4. `CommitReviewedStatement` is a **separate explicit operation**. Under the repository mutex and **one SQL transaction**, it verifies the HMAC, handles same-document/same-owner idempotent replay, recomputes the current vault digest, re-reads all members/holdings/events, reruns the same planner, verifies plan equality and ownership, and rejects unresolved line conflicts or chronological regressions.
5. Once validated, it inserts the complete `snapshot_import` source identity, its `statement_position` observations, and all proposed opening/correction/snapshot-delta `ledger_event` rows **in the same SQL transaction**. Any failure rolls back all inserts, including the SHA record; partial statement imports are not left behind.

**User consent boundary:** These use cases are backend capabilities only. The native UI does **not** yet expose a production-quality consent screen or password/biometric session gate. Approval is not automatically invoked by preview. An HMAC-bound review is an integrity receipt, **not** a substitute for authenticating the person using the device.

**Source authenticity boundary:** The caller still supplies the SHA-256 and normalized statement lines; until the immutable CASPARSER bridge validates PDF origin and normalizes parser output, this API does **not** prove those values came from the actual PDF. The project does not modify CASPARSER source or Python wrappers.

## 2. Financial accuracy hardening

- A holdings-only CAS may print quantity and today's price, but omit the **historical purchase cost**. `SnapshotReconciler` no longer converts current market quote into an invented opening purchase cost. Without an actual printed contribution, an opening balance holds units with zero/unknown basis; the portfolio flags `unknownInvestedCost` and does not display a fabricated gain.
- Changes of quantity inferred from a custodian snapshot are explicitly **not purchases or sales**. New `SNAPSHOT_ADJUSTMENT` rows carry zero purchase cost and no false proceeds; their market-value effect is still estimated separately.
- Unknown-cost warnings apply only to **active, unsuperseded** opening/adjustment tax lots, so a later verified cost correction can resolve the warning.
- A single printed cumulative contribution is **insufficient** to redistribute purchase cost across separate acquisition lots. `COST_UPDATE` now yields a blocked `CONFLICT` when multiple active lots require individual evidence.
- A zero-unit snapshot with no existing units becomes an observation only and never creates an invalid zero-quantity opening ledger event.
- No old ledger rows are rewritten. The existing v2 SQLCipher triggers, owner controls, full-precision scaled integers and encrypted format-1/format-2 backup compatibility remain intact.

## 3. Additional pure Kotlin parity ports

| Existing React/TypeScript reference | New Kotlin engine | As-built behavior |
| --- | --- | --- |
| `src/lib/emi.ts` | `domain/finance/LoanEmiEngine.kt` | Month-clamped EMI schedules, PAYING/PAUSED/STOPPED state, due-date catch-up plans, exclusion of posted dates and outstanding balance capped by final payment |
| `src/lib/interest.ts` | `domain/finance/SavingsInterestEngine.kt` | Fixed 2026 small-savings/FY25-26 EPF rate inputs, category recognition, fiscal-year boundaries, whole-month arithmetic, truncating integer accruals, simple periodic coupons and maturity-limited dates |
| `src/lib/lookthrough.ts` | `domain/finance/ExposureSplitEngine.kt` | Disclosed split takes priority; category fallback is marked *estimated*; exact 10,000-basis-point apportioning with remainder to largest slice and no double-counting |

All these engines are **pure domain calculation code** and currently produce plans or valuations. They do not automatically write interest/EMI transactions or replace the existing statement/Python parsers.

**Important parity limits:** `SavingsInterestEngine` currently covers dates and simple interest/coupons, **not** the full product-specific FD/RD compounding/catch-up automation from `interest.ts`. EMI currently mirrors the reference's gross repayment deduction, not bank-style interest-versus-principal amortization. Tax notices and interest rates must be verified against applicable government sources before commercial tax calculations. `growth.ts`, `returnMetrics.ts`, some `costBasis.ts` subtleties and full taxonomy/tree integration remain open.

## 4. New test coverage (awaiting independent CI)

- `NativeStatementApprovalInstrumentedTest.kt`: **6** new Android SQLCipher+Keystore cases—review does not write; approved statement is persisted once; stale post-review financial changes refuse commit; altered input/plan rejects HMAC; cross-owner conflict cannot import; zero-unit observation stays zero; later statements append only the quantity difference and old snapshots cannot rewind the vault.
- `FinanceParityExtensionsTest.kt`: **19** JUnit5 scenarios—EMI dates/payments/states, interest defaults and dates, exact scaled-integer scheme interest and overflow, small-savings recognition, estimated/disclosed asset splits with precise remainder accounting, absence of fake opening cost, blocked multi-lot repricing, unknown additional units and zero-balance observations.
- `AtomicBookUseCaseTest.kt`: **2 additional** JVM cases verify preview does not call approval and the explicit commit use case calls the vault exactly once.

These are **added tests, not passing results** until a complete new CI run has been inspected. All existing financial golden tests, v2 encrypted migration tests, backup tests, Compose tests, CASPARSER guards and build artifacts remain required.

## 5. Outstanding commercialization gates

1. **Native user authorization:** A reviewed statement must be presented in a clear consent UI with explicit approval and active vault-user session/biometric gate. The HMAC receipt is not device authentication.
2. **Parser contract:** Immutable CASPARSER must be wired to a sanitized Kotlin statement-line adapter; validate owner, account, source SHA and output boundaries, without editing the Python parser.
3. **Mixed/large imports:** Validate recovery from split accounts, partial statements, failed line-level metadata and thousands of positions on physical devices. Currently an unresolved conflict intentionally rejects the full commit.
4. **Corporate/tax-lot history:** Missing purchase dates, multi-lot cost allocations and partially known investment basis must not silently produce trusted taxable gains.
5. **Remaining TypeScript finance parity:** Finish FD/RD/PPF/SSY catch-up details, `growth.ts`, `returnMetrics.ts`, `costBasis.ts`, comprehensive equity/fixed-income tax treatment and look-through UI.
6. **Release readiness:** Android API 26–36 matrix, real hardware and backup restore, Play Billing/licensing, Gmail authorization, 16 KiB native libraries, signed release AAB, OWASP MASVS security validation, and Play privacy policy remain pending.

## 6. CI verification and change policy

Only `GUD-Android-ChatGPT` may be modified. Verify the diff against `GUD-Android-Gmail` for the frozen parser paths, then run the `GrowUrDhan Native Android M3` and existing `Kosha CI` workflows.

After the final commit, give the owner the exact commit SHA and both workflow run IDs. **Do not poll repeatedly**; await the owner's pass/fail confirmation. Document actual JVM, instrumentation and artifact results afterward. Do not mark the new statement approval or parity scope complete until all relevant tests succeed.
