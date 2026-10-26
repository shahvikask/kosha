# GrowUrDhan — Software Build Document

> **GrowUrDhan first market release — owner decision D57 (2026-10-26):** Financial vault backup/export, automated SAF backup, backup-file import and financial-data recovery/restore are **not available in the initial release**. They belong to the next upgrade. This note supersedes any older proposed screens, flows, requirements or tests in this document; those descriptions are retained only as legacy/future-upgrade references. Offline account-password recovery and standard CAS/CSV/PDF statement imports remain in scope. No backup or recovery must be advertised as active. See [base-release scope](implementation/BASE-RELEASE-SCOPE.md) and spec v1.3.

**Status:** As-built. This describes the code that is on the branch, not a future design.
**Product:** GrowUrDhan (internal name Kosha), on-device Indian family wealth tracker.
**Application id:** `in.growurdhan.app`
**Version:** 0.2.0, Android `versionCode` 2002
**Source branch:** `GUD-Android` at commit `95461c482bfdce65dbb2bbdd1315f73968fc43db`
**What that commit contains:** every product change that was on `GUD-Android-APK`, merged in, plus the Play AAB build fix (`bundle.targets` set back to `"all"` because Tauri does not accept `"aab"` as a desktop bundle target).
**Document date:** 28 September 2026
**Audience:** the owner of the app, and anyone who has to maintain, review, or ship it.

The older files `docs/KOSHA-ARCHITECTURE.md`, `docs/KOSHA-DATA-MODEL.md`, and `docs/KOSHA-SECURITY.md` are Phase 0 baselines. Where they disagree with this document, this document is the one that matches the code. In particular, the running app does **not** use SQLCipher, and the vault password does **not** decrypt the on-phone database file.

This document has three parts, as requested:

1. **Technical specification** — internal design, algorithms, components.
2. **As-built system architecture** — structure, data flows, database, as they exist after development.
3. **API documentation** — every command another part of the program can call, and the only HTTP service (a developer machine sidecar, not a public server).

---

## 0. What the app is, in one page

GrowUrDhan is a phone app. The books live on the phone. There is no GrowUrDhan server that stores holdings, statements, PAN, or passwords.

The screen is a React application inside an Android WebView. The books are a SQLite database running in WebAssembly in that WebView. On Android, a snapshot of that database is saved as `vault.enc` and wrapped by the Android Keystore (`EncryptedFile`, AES-256-GCM). The user’s password is a lock on the screen and a key for backup files. It is not the key of `vault.enc`.

The same React code also runs in a desktop browser for development. On a phone, statement PDFs are parsed by Python (casparser  via Chaquopy) inside the app. Prices, when the user asks, are last close and NAV files from NSE, BSE, and AMFI. Nothing in those requests identifies the person.

Two products are sold on Google Play:

| Product | SKU | Price shown in the app | Rule |
|---|---|---|---|
| Lifetime licence | `gud_lifetime` (one-time) | ₹249 | Required after a 30-day trial |
| Annual support | `gud_support_annual` (subscription, offer `standalone`) | ₹299 / year | Optional, and only after the lifetime licence |

Family members are unlimited. There is no per-seat charge.

Two build channels exist:

| Branch | What GitHub builds | Who it is for |
|---|---|---|
| `GUD-Android` | Signed-or-debug **AAB** (`GrowUrDhan-Android-AAB`) | Google Play |
| `GUD-Android-APK` | **Debug APK** with `VITE_GUD_TEST_APK=1` | Sideload testing on a phone |

A sideload APK is not a Play install. The Play build does not set the test-APK flag. Successful Play AAB from this commit: `https://github.com/shahvikask/kosha/actions/runs/36303835882/artifacts`. The upload keystore secret and the Play service-account secret were not set on that run, so the file is the CI debug-signed bundle and it was **not** uploaded to the Play internal track.

---

# Part A — Technical specification

## A1. Design constraints that the code actually follows

1. The ledger of money events is append-only. A database trigger aborts `UPDATE` and `DELETE` on `ledger_event`.
2. Money and quantity are scaled integers. The schema has no `REAL` columns for money.
3. A statement import is a snapshot of what the registrar reported. It is not treated as a fresh buy of the same units.
4. Re-importing the same file is meant to be idempotent (SHA-256 of the source, and a duplicate-event check).
5. The app must show a portfolio with no network. Network is used only when the user refreshes prices, buys or restores a licence, or opens the Play listing.
6. Diagnostics are off by default. When they are on, they must not write the password, the PDF bytes, or a PAN.
7. The interest engine uses one published rate per product (the July–September 2026 small-savings rates, and the FY 2025-26 EPF rate of 8.25%). It does not store a rate history.

## A2. Technology stack

| Layer | What is used |
|---|---|
| UI | React 19, TypeScript, Vite 6, Tailwind 4 |
| Shell | Tauri 2 (`@tauri-apps/api` 2.11, CLI 2.11). Window size in config: 390×844 |
| Android | minSdk 26, target platform android-36, build-tools 36.0.0, NDK 27.2.12479018, ABI `arm64-v8a` only |
| Language of the shell | Rust crate `growurdhan` 0.2.0. Dependencies: `tauri` 2, `serde`, `serde_json`, `base64` 0.22. No SQL in Rust |
| On-device database | `@sqlite.org/sqlite-wasm`. Snapshot persisted by the shell |
| Statement parser | Vendored Python casparser under `src/casparser-main`, run by Chaquopy 17 / Python 3.12 on the phone. `pypdfium2==5.13.0` |
| Play | Billing `billing-ktx:7.1.1`, Play Integrity 1.4.0, in-app review `review-ktx:2.0.2`, `security-crypto`, `documentfile` |
| Tests | `npm test` (`src/lib/kosha.test.ts`), `npm run test:db` (`src/db/db.test.ts`), `tsc --noEmit`. CI workflow `Kosha CI` on every branch |

Content-Security-Policy (`src-tauri/tauri.conf.json`) allows scripts from self, `wasm-unsafe-eval` (required by SQLite WASM), worker blobs, and `connect-src` only to self, the Tauri IPC host, and `https://play.google.com`. Cleartext HTTP is disabled in the Android network security config.

## A3. Process architecture

```
┌─────────────────────────────────────────────────────────────┐
│ Android WebView                                             │
│  React UI  →  KoshaContext  →  domain libs (interest, tax,  │
│  lots, prices, CAS mapping)  →  Repository                  │
│       │                            │                        │
│       │ nativeCommand()            │ SQL                    │
│       │                            ▼                        │
│       │                     SQLite WASM (memory)            │
│       │                            │ snapshot bytes         │
└───────┼────────────────────────────┼────────────────────────┘
        ▼                            ▼
┌───────────────────┐      ┌────────────────────────┐
│ Tauri Rust        │      │ GrowUrDhanPlugin.kt    │
│ 19 app commands   │─────▶│ Keystore vault file    │
│                   │      │ Play Billing / Review  │
│                   │      │ SAF backup folder      │
│                   │      │ Chaquopy casparser     │
│                   │      │ PriceFetcher (HTTPS)   │
└───────────────────┘      └────────────────────────┘
```

JavaScript never imports Kotlin. It calls a Tauri command. Rust either handles it or forwards it with `run_mobile_plugin` to the class `in.growurdhan.app.GrowUrDhanPlugin`.

Which channel is tried first is deliberate (`src/lib/license.ts`):

- Statement, diagnostics, and price commands try the **Rust app command first**, then the plugin channel. Rust forwards those to Kotlin. This exists because an earlier build rejected `plugin:growurdhan|cas_*` as “plugin not found” and hid the real parser error.
- Vault, Play, backup, and exit try the **plugin channel first**, then the Rust command. The Rust bodies for Play are desktop stubs. The Rust body for the vault file is a plain `fs::write` with no Keystore. On a real phone the plugin channel is the one that must win.

## A4. Component map

### A4.1 Screens

The gate order in `src/App.tsx` is fixed:

1. Licence still loading.
2. Terms declined → closed screen.
3. Database failed to open.
4. “Opening your records” while SQLite starts.
5. Onboarding, only when there is no vault account and onboarding has not been dismissed.
6. Licence screen, when the trial is over and there is no lifetime licence (or the install is not from Play).
7. Auth screen, when there is no account, the vault is locked, or the password is older than 180 days.
8. The shell.

Shell screens (`AppScreen`):

| Screen | Role | Writes? |
|---|---|---|
| Home | Net worth, charts, books-health, “Coming up” | No, except dismissing a health note in `localStorage` |
| Holdings | Open positions, search, sort by value | Opens add / detail |
| Accounts | Demat accounts and folios created by imports | Read-only list |
| Documents | Read a statement, tax summary, record book | Imports |
| Family | Members, goals, backup, diagnostics, Play rating, privacy/terms | Yes |

Modals: add holding, holding detail, add a record, emergency sheet.

Auto-lock is 30 seconds after the app leaves the foreground (`AUTO_LOCK_MS` in `src/lib/autoLock.ts`). A system file picker (statement PDF, backup file, backup folder) counts as still in the app and does not start the lock. Lock clears the in-memory password. It does not wipe the SQLite image already in memory; the auth screen is what stops the user seeing it.

### A4.2 Domain libraries (where the rules live)

| File | Responsibility |
|---|---|
| `src/lib/interest.ts` | Scheme interest and REIT monthly income |
| `src/lib/emi.ts` | Loan EMI catch-up |
| `src/lib/taxRules.ts` | Assessment-year gain classes |
| `src/lib/lots.ts` | FIFO lots, realised gain |
| `src/lib/positions.ts` | Fold the ledger into quantity, cost, value |
| `src/lib/holdingView.ts` | Display value, certificate-SGB price, upcoming dates |
| `src/lib/costBasis.ts` | “Invested” correction events |
| `src/lib/returns.ts` | XIRR |
| `src/lib/growth.ts`, `returnMetrics.ts` | CAGR and dashboard figures |
| `src/lib/casPython.ts`, `casParser.ts` | PDF → canonical records → commit |
| `src/lib/eodPrices.ts` | Apply NSE/BSE/AMFI rows onto holdings |
| `src/lib/backup.ts` | Encrypt and decrypt a `.kosha` file |
| `src/lib/auth.ts` | Password and security-answer hashes |
| `src/db/repository.ts` | The only SQL writer the UI uses |

The UI is not supposed to invent a second copy of these rules. Interest, tax class, and FIFO all live in the files above.

### A4.3 Android overlay

Copied into the generated Tauri Android project by `scripts/android_prepare.mjs`:

- `GrowUrDhanPlugin.kt` — every native command.
- `PriceFetcher.kt` — NSE, BSE, AMFI.
- `Diag.kt` — optional log file.
- `proguard-rules.pro` — keeps the plugin, `PriceFetcher`, `Diag`, and `com.chaquo.python.**` so release minify does not strip them.
- Python: vendored casparser, `cas_android.py`, `cas_parse_server.py`, `eod_prices.py`, a pydantic shim, `casparser_isin.py`.

`android_prepare.mjs` also injects the Gradle dependencies, Chaquopy, `arm64-v8a` only, release minify and resource shrinking, `allowBackup=false`, and `INTERNET` plus `BILLING`. `BuildConfig.GUD_TEST_APK` is written only when the environment variable is `1`. The Play workflow does not set it.

## A5. Money and quantity

From `src/lib/money.ts`:

- **Money scale:** 10,000. ₹1 = 10,000 minor units. 1 paisa = 100 minor units.
- **Quantity scale:** 100,000,000. Eight decimal places (shares, mutual-fund units, gold grams).
- Rates stored on a holding are basis points: `round(percent × 100)`. 7.1% is stored as `710`.
- JavaScript `number` is used only at the edge (`Math.round` when the user types rupees). After that, arithmetic that must be exact uses `bigint`. The quarterly-compound formula is the exception: it uses IEEE float and then rounds (see A6.4).

Market value of an asset:

```
valueMinor = quantityScaled × unitPriceMinor / 100_000_000
```

A loan’s “value” is the outstanding amount stored in `current_price_minor`, not quantity times price.

**Quantity and average cost are not columns.** `rowToHolding` always returns `quantityScaled = '0'` and `averageCostPerUnitMinor = '0'`. Both are folded from the ledger when the screen draws. The holding row stores the **unit price** (for a bank FD or PPF, that “price” is the rupee balance, and the opening buy uses quantity 1).

## A6. Interest engine

Source of truth: `src/lib/interest.ts`. Header comment in that file states the rate vintage: July–September 2026 small savings, FY 2025-26 EPF.

Catch-up runs in three places:

- Immediately after a holding is added.
- When coupon, interest rate, issue price, or acquired date changes.
- Every time the vault is opened, as of today’s date.

Already posted dates are skipped. Event ids are deterministic (`ev_int_<holdingId>_<date>`, `ev_inc_<holdingId>_<date>`), and the writer re-reads events before inserting, so a second catch-up in the same session does not duplicate rows.

### A6.1 Which product is which

`interestKindOf` returns nothing for liabilities, for NPS (name or subcategory), and for physical or digital gold. “Other” gold does not get the 2.5% SGB coupon. A fixed-income subcategory of exactly `other` becomes kind `OTHER` only when the user typed a rate above zero.

| Kind | How it is recognised | Default rate | Mode | What the rate is applied to | When it posts | Does the balance grow? |
|---|---|---|---|---|---|---|
| SGB | Sovereign Gold Bond subcategory or a name containing SGB, and not physical/digital | 2.5% (or the coupon stored on the holding) | Payout | Quantity × **issue price**, not today’s market price and not the secondary purchase price | Every 6 months from `acquiredOn`, stop after maturity | No. Cash interest only |
| PPF | subcategory PPF | 7.1% | Compound | The rupee balance | 31 March | Yes |
| EPF / VPF | subcategory EPF | 8.25% | Compound | The rupee balance | 31 March (note says passbook date) | Yes. EPS is not modelled |
| SSY | Sukanya | 8.2% | Compound | The rupee balance | 31 March | Yes |
| Bank FD, cumulative | compounding = `CUMULATIVE` | User’s rate. **No compounding value → no plan at all** | Compound, quarterly | The rupee balance | 31 March | Yes |
| Bank FD, payout | MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY | User’s rate | Payout | The amount deposited, not a growing balance | Every 1, 3, 6, or 12 months from `acquiredOn` | No |
| SCSS | | 8.2% | Payout | Balance entered | Quarterly from `acquiredOn` | No |
| POMIS | | 7.4% | Payout | Balance entered | Monthly from `acquiredOn` | No |
| NSC | | 7.7% | Compound | Balance | 31 March. Note says paid at maturity; the app still accrues into the value | Yes |
| KVP | | 7.5% | Compound | Balance | 31 March. Same maturity note. The app does not separately enforce “doubles in 115 months” | Yes |
| Post Office RD | | 6.7% | Compound, quarterly | The balance the user entered. It does not rebuild a monthly instalment ledger | 31 March | Yes |
| RBI floating-rate bond | | 8.05% (NSC + 0.35) | Payout | Face / balance | **1 January and 1 July**, not the anniversary of purchase | No. Face stays |
| Bond / GOI | | No default. User must type a rate | Payout | Quantity × face value, else quantity × price | Half-yearly from `acquiredOn` | No |
| Other fixed income | subcategory `other` and a rate | User’s rate | Compound, annual, same shape as PPF | Balance | 31 March | Yes |

NPS is a single current-value field. It is not interest. REIT and InvIT are not interest. Their optional cash is A6.6.

### A6.2 Formulas

Dates are `YYYY-MM-DD`. Month arithmetic uses UTC and does **not** clamp an overflowing day (31 January plus one month rolls). This is not Actual/365 and not a daily balance.

Simple annual credit, used for PPF, EPF, SSY, NSC, KVP, and Other:

```
interest = principal × round(ratePercent × 100) × months / (10_000 × 12)
```

`months` is months held in that financial year: 12 if the holding was already there on 1 April, otherwise whole months from the later of 1 April and `acquiredOn` through 31 March, capped at 12. A deposit on 2 April earns 11 months to the next 31 March. A deposit dated 31 March does not earn on that same day.

One payout period (SGB half-year, SCSS quarter, POMIS month, FD payout, bond coupon, FRSB half):

```
interest = principal × round(ratePercent × 100) / (10_000 × periodsPerYear)
```

The length of the calendar period does not change the amount. Each date pays one full period.

Quarterly compound, used only for a cumulative FD and for RD:

```
interest = round(principal × (1 + rate/100/4) ^ (months/3)) − principal
```

`months` is again “months held in the financial year”, not “exactly one quarter since the last posting”. The power is computed in floating point and then rounded to minor units.

### A6.3 Historical catch-up versus a typed balance

When the user adds a PPF, SSY, EPF, NSC, KVP, RD, cumulative FD, or Other from an earlier year, the add form copies “amount deposited” into both cost and current value, and sets the price source to `Manual Initial Entry`.

That source does **not** contain the words `Manual Verified`, so catch-up is allowed to run.

If all of the following are true, the engine **replays from the deposit**, including earlier 31 March dates:

- the product compounds,
- price source does not match `/Manual Verified/`,
- `acquiredOn` is set,
- invested amount is greater than zero,
- invested amount equals the current balance (the user has not typed a different today’s value).

It rolls the principal through years that already have an interest row, and it writes a row only for years that do not. If the grown balance equals the balance already stored, the whole plan is thrown away, including any new rows. That is what keeps a second open of the app from inserting duplicates, and it is what leaves a passbook balance alone.

Worked example that the tests lock in: a PPF whose current value is ₹19.4 lakh, cost ₹14.5 lakh, and `lastPriceUpdated` 31 August 2026, planned as of 15 September 2026, produces **no** new credit. Planned as of 31 March 2027, it posts one 31 March credit on the stored balance. It does not rewind to the original ₹14.5 lakh.

If the user later edits **current value**, the save sets price source to `Manual Verified Entry` only when the number actually changed. Invested can still be corrected (that writes a `CORRECTION` event; it does not overwrite an old row). While the source matches `Manual Verified`, compound catch-up returns null, so the typed value stays. Payout products still post cash; the lock stops the balance from being rewritten, it does not stop a coupon record.

`applyInterestPlan` writes the new balance, sets `lastPriceUpdated` to now, and keeps the existing price source if one is already set.

### A6.4 What the interest engine deliberately does not do

- **PPF and SSY “lowest balance from the 5th to month-end”.** That needs every deposit date. The app does not keep a monthly deposit ledger. If a single `annualContributionMinor` and `contributionOn` happen to be stored, a deposit on day 1–5 of a date inside that financial year is added to the base before that year’s interest, and a deposit on day 6 or later is added after. The simplified add form no longer asks for “this FY contribution”, so on new rows this branch usually does not run. The annual credit then uses the deposited amount.
- **No rate history.** One rate is applied to every year being replayed. FY 2026-27 EPF has not been declared; the engine still uses 8.25% until the stored rate or the default is changed.
- **TDS is a note, not a balance change.** Bank FD can store `tdsApplicable`. The app does not subtract 10% (or the senior-citizen threshold). Interest events are gross. Tax on that interest is not computed inside the interest engine; it remains “taxable on accrual” as a product fact, not a ledger deduction.
- **Employee PF above ₹2.5 lakh / ₹5 lakh** is not split into a taxable slice. The balance is not changed for that.
- **SGB redemption price** is not fetched. Demat SGB market value uses the NSE close when the user refreshes prices. Certificate SGB display price uses a stored IBJA rupee-per-gram figure if one already exists in preferences, otherwise the stored benchmark, otherwise the stored price. There is **no Family-screen control** to type that IBJA rate. The gold-bonds card was removed because one family switch cannot describe one member’s demat SGB and another member’s RBI certificate.
- **Maturity does not auto-redeem.** It only stops further dates. NSC and KVP are not converted into a cash payout on the maturity date.
- **Blank FD compounding** produces no interest plan. The add form defaults Bank FD to Cumulative so a new FD is not blank. “Not sure” was removed.

### A6.5 Opening the app again

On open, holdings are loaded from SQL **before** the ledger is folded, so `quantityScaled` on those objects is `'0'`. Catch-up therefore does not see units unless the in-memory object still has them (the add-holding path does, because the form still holds quantity).

Consequence, as built:

- Balance products (PPF, FD, and the rest that were saved as quantity 1) keep working on later opens, because the rupee balance is `current_price_minor`, which **is** stored. The historical replay from the original deposit happens at add time. Later opens only add financial-year ends after `lastPriceUpdated`.
- SGB and listed bonds compute principal as quantity × issue or face price. After a restart that quantity is 0 on the raw row, so **no further SGB coupon is planned on open** until a code path supplies quantity. Coupons written at add time remain in the book.

This is current behaviour, not a second design. Screens still show SGB quantity correctly, because the screen folds the ledger. Only the open-time interest pass uses the raw row.

### A6.6 REIT and InvIT monthly income

Not interest. Fields on `HoldingDetails`: `monthlyIncomeMinor`, `incomeDay` (clamped to 1–28), `incomeStart`, `incomeEnd`.

`planMonthlyIncome`:

- Needs an amount above zero and a start date (`incomeStart`, otherwise `acquiredOn`).
- First payday is that day in the start month, or the next month if that day is before the start.
- Then every month through today, and it stops once the date is after `incomeEnd`.
- Each missing date becomes a `DIVIDEND` event whose note starts with `Monthly income`. Real dividends that do not have that prefix are not treated as already-posted income.
- Quantity and price do not change.

“Coming up” on Home lists the next interest date and the next income date. The list shows two rows, then “Show N more”.

### A6.7 Loans

A liability is not an interest kind. EMI catch-up (`src/lib/emi.ts`) posts `REPAYMENT` events and reduces `current_price_minor` by the EMI amount. It does not split principal and interest. Status `PAUSED` or `STOPPED` skips catch-up. `PAYING` runs on open.

## A7. Ledger, positions, and tax

### A7.1 Event types

`OPENING_BALANCE`, `SNAPSHOT_ADJUSTMENT`, `BUY`, `SELL`, `BONUS`, `SPLIT`, `DIVIDEND`, `INTEREST`, `VALUATION_UPDATE`, `REPAYMENT`, `CORRECTION`.

A `CORRECTION` row is legal only when `supersedes_event_id` is set. The old row stays. Folding ignores superseded ids.

FIFO (`src/lib/lots.ts`):

- `BUY` and `OPENING_BALANCE` open a lot. Cost is quantity × unit price / scale, plus charges.
- `SELL` consumes oldest lots first and records realised gain.
- `SNAPSHOT_ADJUSTMENT` down reduces lots **without** calling it a taxable sale. This is how a statement that shows fewer units is reconciled.
- `BONUS` is zero-cost and inherits the earliest open lot’s date.
- `SPLIT` rescales quantities.
- `DIVIDEND` and `INTEREST` do not change quantity.
- `REPAYMENT` is the loan path.

Duplicate detection: same person, same holding, same date, same quantity, and amounts within ₹0.01.

### A7.2 Deleting a holding

Triggers:

- `tg_ledger_event__no_update` — abort, message tells the caller to record a correction.
- `tg_ledger_event__no_delete` — same.

`deleteHolding` deletes only the `holding` row. Events remain. Orphan events are left out of totals.

`deleteHoldingEvents` is the one supported exception. Inside a transaction it drops `tg_ledger_event__no_delete`, deletes `ledger_event` rows for that holding id, then recreates the trigger from `LEDGER_NO_DELETE_TRIGGER`.

The screen rule (`HoldingDetailModal`):

- If the price source looks like a CAS statement or tradebook, **or** any event has an import batch id: one confirm. The holding row goes. **The records stay.** Statement history is not erased.
- Otherwise the user is asked “Also delete it from the record book? Yes deletes those records. Cancel keeps them.” Yes calls `deleteHoldingEvents` and then deletes the holding. Cancel deletes only the holding.

Deleting a non-primary family member also drops that person’s ledger rows, using the same trigger drop. Self cannot be deleted.

### A7.3 Current value versus invested

- **Current value** is quantity × unit price, except loans (outstanding) and certificate SGBs (IBJA / benchmark if present).
- Editing current value updates `current_price_minor` and sets `Manual Verified` only when the displayed number changed. The save must stick: the invested-amount correction receives the holding that was just saved, so it cannot write back a stale price.
- **Invested** is not edited by overwriting a column. A change appends a `CORRECTION` that supersedes the earliest open lot.
- Listed price refresh may later overwrite `current_price_minor` for a holding that has an ISIN. It skips liabilities, cash, and real estate that is not a REIT or InvIT. A manual verified price is not a permanent freeze against a later user-requested price refresh; the refresh writes the official close.

### A7.4 Tax classes

Rules are selected by the **sale date’s assessment year**, not by today’s date. Published sets: `AY25-26`, `AY26-27`, `AY27-28`. All three currently carry the same numbers. `AY27-28` is marked provisional. An unknown year produces no tax number.

| Input | Class |
|---|---|
| Sovereign SGB **and** hold mode `CERTIFICATE` | `SGB_EXEMPT` (tax zero; label says RBI redemption of a certificate) |
| SGB that is physical, digital, other, demat, or has no hold mode | `DEBT_SLAB` |
| Any real estate | `REAL_ESTATE_LTCG` always. Short versus long is ignored. The gain is labelled, not added into the tax total |
| Equity, or a mutual fund whose subcategory is not debt-like | `LTCG_EQUITY` if held more than 365 days, else `STCG_EQUITY` |
| Everything else, including FD, PPF, debt funds, bonds | `DEBT_SLAB` (gain is summed; tax is “your slab”, not a computed rupee) |

Equity-oriented mutual funds: category `EQUITY` is always equity. A mutual fund is treated as debt if the subcategory contains debt, gilt, liquid, overnight, money market, bond, duration, credit risk, banking and psu, floater, or income. Hybrid, index, ETF, and ELSS are treated as equity.

Numbers used when a year is published:

- Equity LTCG 12.5% after a ₹1,25,000 exemption. The exemption is applied on the summary, not on each row.
- Equity STCG 20%.
- Section 112A grandfathering: if purchased on or before 31 January 2018 and a 31 Jan 2018 FMV is stored, cost becomes `max(actual cost, min(FMV, sale proceeds))`.
- Dividends and interest in the year are listed as income. They are not run through a slab. REIT “monthly income” is counted as dividend income because that is the event type.
- `totalEstimatedTaxMinor` is equity LTCG tax plus equity STCG tax only.

This is an estimator inside the app. It is not a tax return.

### A7.5 Returns

XIRR cash flows are buys (outflow), sells, dividends, interest, and today’s value. Opening balances, bonus, split, valuation updates, repayments, snapshot adjustments, and corrections are not cash flows. If Newton and bisection both fail, XIRR is null.

CAGR is shown only for a simple case: one lot held at least 365 days and no extra open lot or sale.

## A8. Statement import

On the phone:

1. The user picks a PDF. The password tried is the one typed, then an NPS-style `first4(name)+ddmm`, then the PAN stored for that family member.
2. Bytes are base64 in memory. `cas_parse` runs on a background thread. Chaquopy calls `cas_android.parse_b64`, which writes a temp file, parses, and deletes it.
3. `parse_pdf_path` detects CAMS, KFintech, NSDL, CDSL, or NPS. Unknown types return an error naming those issuers. Wrong password returns code `BAD_PASSWORD`. The UI string for a dead engine remains exactly `Engine is not running.` or `Engine is running.` The user asked that those strings stay.
4. The JS layer maps rows into holding snapshots and re-tags REIT, InvIT, AIF, and SGB.
5. Commit mode is `REPLACE_SNAPSHOT`. Accounts (demat / folio) are inserted first because of foreign keys. Holdings are matched, not blindly duplicated. Units move by snapshot adjustment. A holding that was on the previous file from the same issuer and is absent now is closed. An NSE/BSE/AMFI price already on the holding is not replaced by the statement price.
6. Commit is refused if the statement PAN does not match the family member the user selected.
7. A successful import can trigger a scheduled backup.

CSV tradebooks stay in the browser parser. They are not sent to Python.

The vendored casparser tree is the engine. It is not changed unless the owner explicitly asks.

## A9. Prices

Manual only. The header refresh button and the add-holding search call `prices_refresh` with the comma-separated ISINs already in the vault.

`PriceFetcher` walks up to six Kolkata calendar days and GETs:

- NSE CM UDiFF bhavcopy zip. Series EQ, BE, SM, ST, and GB. GB is categorised `GOLD_SGB`.
- BSE equity bhavcopy CSV. Series `STK` only.
- AMFI `NAVAll.txt` from `www.amfiindia.com`, then `portal.amfiindia.com`. NAV is the second-to-last column.

The first source to claim an ISIN wins. ISINs are a local filter. They are not put in the URL. If the vault listed ISINs and none matched, the command returns ok false and the sentence `No last close or NAV matched the ISINs in this vault.`

The refresh runs off the UI thread. It does not freeze the screen on purpose; the click returns while Kotlin works, and the UI applies the catalog when the command resolves.

Official rows are also stored in `listed_security` for the picker.

## A10. Security specification

Three different secrets. Do not collapse them into one.

### A10.1 The file on the phone

`filesDir/vault.enc` is the SQLite snapshot. Kotlin writes it with AndroidX `EncryptedFile`, scheme `AES256_GCM_HKDF_4KB`, master key `AES256_GCM` in the Android Keystore. The WebView asks for the bytes only after the Keystore has decrypted them. JavaScript does not hold that key.

If the plugin channel is missing, Rust writes the same path with ordinary `fs::write` and **no** encryption. That path is a fallback, not the phone design.

Web preview (no Tauri) uses a different envelope, `GUDENC01`, AES-256-GCM, PBKDF2-SHA256, 310,000 iterations, derived from a hard-coded pepper, stored in IndexedDB. That is not the Android design and it is not keyed by the user’s password.

### A10.2 The login password

Stored only as PBKDF2-SHA256, 210,000 iterations, 16-byte salt, 256-bit output, in `vault_account`. Never in the clear. Policy: 8 characters, upper, lower, digit, special. Compared with a constant-time XOR loop. Wrong email and wrong password return the same message.

Two security answers use the same KDF, after trim, lower-case, and collapsed spaces. Password reset checks both answers and replaces the hash. It does not re-encrypt `vault.enc`, because the password was never that key.

The app opens and decrypts the file **before** the password screen, because the account row lives inside the file. The password screen is the gate that shows the books. After 180 days `password_changed_at` forces a change.

The session password is kept in memory only, so a scheduled backup can encrypt a `.kosha` file. Lock and process death clear it. Scheduled backup cannot run after the process is killed.

`localStorage` may remember the email (`kosha.remember_email`). It must not remember the password.

### A10.3 The backup file

A `.kosha` file is not a copy of `vault.enc`. It is a JSON snapshot (account including hashes and statement PAN, family, holdings, ledger, accounts, goals, last CAS state) encrypted as:

- Magic `GUDVLT01`, then a uint32 iteration count, 16-byte salt, 12-byte IV, AES-256-GCM ciphertext.
- PBKDF2-SHA256, 600,000 iterations.
- Passphrase string: `GrowUrDhan`, newline, lower-cased email, newline, password.

An older `KOSHA1` JSON backup, if encountered, uses the password alone. Plain JSON backups are rejected.

The user picks the folder (Storage Access Framework). The app stores a persistable URI in encrypted preferences and may keep the last 5 `.kosha` files. Google Drive or OneDrive sync is the user’s cloud, not a GrowUrDhan server. GrowUrDhan cannot open a backup without the email and password.

### A10.4 Other device secrets

- Licence trial clock and backup folder URI: `EncryptedSharedPreferences` (`gud_licence`, `gud_backup`).
- Diagnostics default off. Turning them on shows a warning that the file can contain financial activity and is for the support team. One public file: `Download/GrowUrDhan-diagnostics.txt`. It is not rotated into `GrowUrDhan-diagnostics (1).txt` on each launch. Lines are redacted when the key looks like `password`, `bytesB64`, `pdfBase64`, or `pan`. The file is trimmed around 400 KB.
- Play Integrity is requested with a random nonce. The token is discarded. The JSON field named `integrity` is actually “was this installed from Play (or is this the test APK)?”, not the Integrity API verdict.
- Full PAN may be stored in `pan_for_statements` because the user gave it to open a statement. Masked PAN is what the UI shows. Logs must not contain it.

## A11. Licence rules

Trial length is 30 days, stored once, and not reset.

`appUnlocked` is true for an active trial, a lifetime licence, or lifetime plus support. It is false when the installer is not Play and there is no lifetime licence (`sideloadBlocked`).

Support cannot be purchased unless lifetime is already owned. The Kotlin billing flow checks that again.

The device-test APK (`VITE_GUD_TEST_APK=1`) never calls Play. Purchases write `localStorage`. Kotlin `testApk()` is true only if `BuildConfig.GUD_TEST_APK` exists and is true; if the field is missing it returns false, so a Play build cannot accidentally become a test build.

Play in-app review runs only when the app was installed from Play and is not a test APK. Otherwise the Play listing is opened. The app never reads the star rating.

## A12. Books health

Home computes four finding kinds:

| Kind | Meaning |
|---|---|
| `COST_EQUALS_VALUE` | Cost and value are effectively the same, so gain is unknown |
| `STALE_STATEMENT` | Statement is old relative to today |
| `MISSING_FY_INTEREST` | A scheme looks like it missed a year-end credit |
| `MEMBER_PAN_MISSING` | A family member has no PAN stored for statements |

Dismiss key is `kind|title` in `localStorage` `kosha.health.dismissed`. The X button dismisses. It does not navigate. The headline is recomputed from what is still visible.

## A13. Add-holding form, as built

Products that ask for one “amount deposited”, copied into both cost and current value: PPF, EPF, Sukanya, Bank FD, SCSS, NSC, KVP, POMIS, Post Office RD, RBI floating-rate bond, Other.

NPS asks for one current value. Loans ask for sanctioned, outstanding, and EMI. Bond / GOI stays unit-based (face and quantity). Equity, mutual funds, and gold stay unit-based. REIT and InvIT are units when held as demat, and an amount when held as a lump sum. Both can take the optional monthly income.

FD compounding choices: cumulative (default), monthly, quarterly, half-yearly, yearly.

PPF and SSY no longer ask for “this FY contribution” or a deposit-day field on the add form.

---

# Part B — As-built system architecture

## B1. Repository layout that matters

```
src/                         React application and domain
src/db/schema.ts             DDL, schema version 7
src/db/repository.ts         SQL
src/db/sqliteWasmDriver.ts   WASM SQLite
src/db/encryptedStore.ts     Where the snapshot bytes go
src/context/KoshaContext.tsx Application state and workflows
src/lib/                     Algorithms
src-tauri/src/lib.rs         Tauri commands
src-tauri/permissions/       ACL allow-list
src-tauri/android-overlay/   Kotlin and ProGuard copied onto the Android project
scripts/android_prepare.mjs  Injects billing, Chaquopy, ABI, Python
scripts/cas_parse_server.py  Parser used by Chaquopy and by the dev sidecar
scripts/cas_android.py       Phone entry: health() and parse_b64()
src/casparser-main/          Vendored casparser
.github/workflows/android-aab.yml
.github/workflows/android-apk.yml
```

`src-tauri/gen/` is generated by `tauri android init` on the CI machine. It is not the source of truth. The overlay and `android_prepare.mjs` are.

## B2. Database design

`SCHEMA_VERSION = 7`. `Repository.initialise` applies v1 DDL, then additive `ALTER TABLE` for v3, v4, and v7, then `listed_security` (v5) and `vault_account` (v6). Old vaults are migrated in place. A new table is not rebuilt.

### B2.1 Tables

**`vault_meta`** — key/value. Holds `schema_version`, UI prefs (privacy, theme, SGB display mode, IBJA rate if one was saved), CAS latest JSON, prices-as-of, one-time migration flags.

**`entity`** — a family member. `entity_type` is one of `INDIVIDUAL`, `HUF`, `PROPRIETORSHIP`, `PRIVATE_TRUST`, `PARTNERSHIP_FIRM`. `relation` is one of `Self`, `Spouse`, `Parent`, `Child`, `HUF`, `Joint`. Columns include masked PAN, `pan_for_statements`, date of birth, date of majority, guardian, colour, notes, `is_primary`, `is_active`. Exactly one Self. Inactive entities disappear from holdings and events queries.

**`account`** — a demat account or a folio. `entity_id`, type, custodian, masked identifier, `dp_id`, `client_id`, `bo_id`, nominee, opened/closed dates.

**`instrument`** — the security. `instrument_id` is the same id as the holding. Name, category, subcategory, symbol or folio, ISIN, currency, taxonomy (`exposure_node`, `l2_node`, `wrapper`, `allocation_split`), 31 Jan 2018 FMV, AMC, RTA, AMFI code, advisor, scheme type, UCC, exchange, DP name.

**`holding`** — one owner’s position facts that are not the ledger. Price, benchmark, issue price, price source, review flags, institution, masked account number, notes, location, tags, coupon and interest in basis points, maturity, next coupon, EMI amount, EMI status, remaining EMI count, `acquired_on`, `details_json`. Check: `current_price_minor >= 0`.

**`details_json`** is the sparse bag in `HoldingDetails`: gold hold mode (`DEMAT` or `CERTIFICATE`) and purity, PPF/SSY contribution leftovers, FD `compounding`, `tdsApplicable`, NPS tier/CRA/PFM, face value, property area and ownership, AIF commitment and capital called, REIT `altHoldMode` and monthly-income fields, IFSC, linked holding.

**`ledger_event`** — the book. See A7. Indexes on (entity, holding, date), holding+date, import batch, supersedes, and a fingerprint of entity+holding+date+quantity+amount.

**`price_observation`** — historical price rows. Primary key includes `ingested_at` so a correction is a new row.

**`listed_security`** — last official close/NAV keyed by ISIN, for search and for matching.

**`import_batch`** — one file: filename, kind, SHA-256, statement date, period, investor name, masked PAN, email, CAS id, counts of new, duplicate, and conflict rows.

**`source_document`** — unique on SHA-256, so the same file is recognised.

**`statement_position`** — what that batch said the holding was: quantity, unit price, invested, ISIN, folio, current value, AMC, scheme type, section. Primary key `(import_batch_id, holding_id)`.

**`tax_rule_set`** — an optional installed rule document. A trigger blocks updates. The numbers the app actually uses are the TypeScript `RULE_SETS` in `taxRules.ts`, not this table.

**`liability`**, **`goal`**, **`setting`**, **`egress_log`** — present in the schema. Goals are used (Family). The egress log exists so a network call can be recorded; price refresh does not depend on it to function.

**`vault_account`** — one owner row. Email unique, name, password salt/hash/iterations/`password_changed_at`, masked PAN, statement PAN, date of birth, two question ids and two packed hashes.

### B2.2 Categories the product ships

`ASSET_SUBCATEGORIES` in `src/types.ts`:

- Equity: Large cap, Mid cap, Small cap, Multi & flexi cap, Sector & thematic, Other.
- Mutual funds: Equity, Debt, Hybrid, Index/ETF, ELSS, Other.
- Gold: Sovereign Gold Bond, Physical gold (24K), Digital gold, Other.
- Fixed income: PPF, EPF, NPS, Sukanya Samriddhi (SSY), Bank FD, SCSS, NSC, KVP, POMIS, Post Office RD, RBI Floating Rate Bond, Bond / GOI, Other.
- Real estate: Residential, Commercial, Land, REIT, InvIT, Other.
- Specialised: PMS, AIF, Unlisted equity, ESOP, Other.
- Cash: Savings, Current, Liquid/overnight, Other.
- Liability: Home, Car, Education, Personal, Other.

## B3. Data flows

### B3.1 Cold start

```
WebView loads
  → openVaultDriver()
       native? probe vault_blob_load
         yes → NativeKeystoreStore (Keystore decrypts vault.enc)
         no  → IndexedDB GUDENC01 wrap
  → SQLite WASM opens the snapshot
  → initialise() / migrate
  → load entities, holdings, events
  → catch up EMI, interest, monthly income
  → set React state, dbReady
  → licence gate → terms → onboarding or auth → shell
```

Fresh install: if there are no entities, holdings, or events, insert one empty Self. No sample portfolio.

### B3.2 Add a holding

```
AddHoldingModal
  → addHolding
       same owner + same ISIN already exists? append BUY, do not create a second row
       else upsert instrument + holding
            append BUY (or VALUATION_UPDATE if it is a loan)
       loan? EMI catch-up
       persistInterestCatchUp (re-reads events)
       persistMonthlyIncome
```

Single-balance products are saved as quantity 1. Price source on add is `Manual Initial Entry`.

### B3.3 Edit current value

```
Holding detail
  → if the rupee number changed: priceSource = "Manual Verified Entry"
  → updateHolding returns the saved holding
  → if invested changed: correctInvested(savedHolding) appends CORRECTION
       using the saved object, not the object from before the price write
```

### B3.4 Delete

```
statement history?  confirm → delete holding row only
manual holding?     confirm remove
                      Yes  → drop no-delete trigger, delete events, restore trigger, delete holding
                      Cancel → delete holding row only
```

### B3.5 Read a statement

```
PDF bytes in the WebView
  → cas_parse { pdfBase64, password, filename }
  → Rust → Kotlin background thread → cas_android.parse_b64
  → temp PDF → casparser → JSON → temp file deleted
  → user confirms
  → REPLACE_SNAPSHOT in SQLite
  → optional scheduled .kosha backup
```

The PDF is not uploaded.

### B3.6 Refresh prices

```
User taps refresh
  → prices_refresh { isins: "INE...,INE..." }
  → Kotlin thread GETs NSE, then BSE, then AMFI
  → { ok, asOf, catalog[] }
  → applyCatalogPrices writes current_price_minor and listed_security
```

### B3.7 Backup and restore

```
Unlock (password in memory)
  → packBackup: JSON → PBKDF2(email+password) → AES-GCM → .kosha bytes
  → backup_save (user picks a file) or backup_write_scheduled (folder already chosen)
Restore:
  → backup_open → bytes
  → decrypt with email + password
  → wipe vault → rewrite rows
```

Schedule is off until the user picks a folder. Triggers that exist in code: on unlock, and after a CAS import. Keep last 5 files.

### B3.8 Licence

```
Play build: play_licence_status → BillingClient query
  lifetime = owns gud_lifetime
  support  = owns gud_support_annual
  integrity = installer is Play
Test APK: localStorage only, no billing sheet
```

## B4. Build and release, as they exist

| | Play | Device test |
|---|---|---|
| Workflow file | `.github/workflows/android-aab.yml` | `.github/workflows/android-apk.yml` |
| Trigger | push to `GUD-Android` | push to `GUD-Android-APK` |
| Command | `npx tauri android build --target aarch64` | `npx tauri android build --apk --debug --target aarch64` |
| Artifact | `dist-play/GrowUrDhan.aab` named `GrowUrDhan-Android-AAB` | `dist-apk/*.apk` named `GrowUrDhan-Android-APK` |
| Test flag | not set | `VITE_GUD_TEST_APK=1` and `GUD_TEST_APK=1` |
| Keystore | step runs only if `ANDROID_KEYSTORE_BASE64` is present, together with alias and passwords | debug signing from the debug build |
| Play upload | `r0adkll/upload-google-play`, package `in.growurdhan.app`, track `internal`, status `draft`, only if `PLAY_SERVICE_ACCOUNT_JSON` is present | never |

CI image: Ubuntu, Node 22, Python 3.12, Temurin JDK 17, Rust target `aarch64-linux-android`, Android SDK at `/usr/local/lib/android/sdk`. `android_prepare.mjs` runs after init and again before the build. Icons are forced to RGBA so the Android build does not reject them.

Secrets must not appear inside a GitHub Actions `if:` expression. An earlier workflow file did that and GitHub registered the workflow as a zero-job failure. The current file copies secrets into step environment variables and gates later steps on a `present=true` output.

`bundle.targets` in `tauri.conf.json` is `"all"`. The AAB is produced by `tauri android build`, not by that field. Setting it to `["aab"]` makes `tauri android init` fail schema validation.

Rust commands the APK and the AAB both forward are listed in `src-tauri/permissions/android-vault.toml` and in `generate_handler!` in `lib.rs`. The capability file references the permission id `allow-android-vault`. It does not paste an inline command list.

## B5. What is intentionally not in the system

- No cloud database of holdings.
- No account server. Email is a local username.
- No server-side CAS. The public site pages (privacy, terms, delete-account) are static GitHub pages. “Delete account” in Family opens that page; it does not call a remote deletion API.
- No live streaming quotes. Last close and NAV only, and only when asked.
- No analytics SDK in `src/`.
- Windows is a different branch (`GUD-Windows`) and is outside this Android as-built.

---

# Part C — API documentation

## C1. There is no public application API

Another company’s program cannot send holdings to GrowUrDhan. The phone does not listen on a port.

What exists instead:

1. Nineteen Tauri commands between the WebView and the Android shell. This is the real interface.
2. A developer-only HTTP server, `127.0.0.1:8765`, used by `npm run dev` in a browser. It is not inside the APK.
3. Outbound HTTPS to NSE, BSE, AMFI, and Google Play. Documented in C5 so a reviewer can see exactly what leaves the device.

JavaScript entry point: `nativeCommand(name, args)` in `src/lib/license.ts`.

Channel order:

| Commands | First try | Second try, only if the first channel is missing or not allowed |
|---|---|---|
| `cas_health`, `cas_parse`, `diag_log`, `diag_status`, `diag_set`, `prices_refresh` | Rust app command | `plugin:growurdhan\|name` |
| vault, Play, backup, `app_exit` | `plugin:growurdhan\|name` | Rust app command |

A real failure (Play cancel, folder not writable, bad base64) is returned to the UI. It is not retried on the other channel.

Rust argument names are snake_case. Kotlin `@InvokeArg` fields are camelCase. When Rust forwards, it rebuilds camelCase JSON. After every `nativeCommand` except the log call itself, a redacted line may be written if diagnostics are on. Keys matching `password`, `bytesB64`, `pdfBase64`, or `pan` are logged as a character count only.

The ACL is `allow-android-vault` (`src-tauri/permissions/android-vault.toml`). Every name below is on that list.

## C2. Command reference

### Vault

#### `vault_blob_load`

- **Caller:** `NativeKeystoreStore` in `src/db/encryptedStore.ts`, during startup.
- **Arguments:** none.
- **Success:** `null` if the file does not exist. Otherwise base64 of the SQLite snapshot. Kotlin returns `{ "value": "<base64>" }`. Rust returns a JSON string. JavaScript accepts either.
- **Error:** `"Could not read the encrypted vault."` or the IO message.
- **Effect:** Kotlin reads `filesDir/vault.enc` through `EncryptedFile`. Rust, if it handles the call, reads `app_data_dir/vault.enc` with no Keystore. No network.

#### `vault_blob_save`

- **Caller:** `NativeKeystoreStore.save`.
- **Arguments:** `{ "bytesB64": "<base64 of the sqlite snapshot>" }`.
- **Success:** empty object.
- **Error:** `"Could not write the encrypted vault."`
- **Effect:** Kotlin deletes the old file and writes a new `EncryptedFile`. Rust fallback writes plaintext bytes. No network.

### Licence and Play

SKUs are fixed strings: `gud_lifetime`, `gud_support_annual`.

#### `play_licence_status`

- **Caller:** `loadLicence`.
- **Arguments:** none.
- **Success when billing is not ready, or the app was not installed from Play:**

```json
{
  "lifetime": false,
  "supportActive": false,
  "integrity": false,
  "trialStartedAt": null
}
```

`integrity` is true, and `trialStartedAt` is an ISO timestamp, when this is the test APK or the installer is `com.android.vending` or `com.google.android.feedback`. The trial timestamp is written once into encrypted preferences.

- **Success when BillingClient is ready:** the same fields, plus `"supportRenewsAt": null` and `"playAccountBound": true|false`. `lifetime` is true if an in-app purchase of `gud_lifetime` is in state `PURCHASED`. `supportActive` is true if a subscription purchase of `gud_support_annual` is `PURCHASED`. Unacknowledged purchases are acknowledged. If the client is not ready but the install is from Play, the plugin retries up to 8 times, 350 ms apart.
- **JavaScript:** `sideloadBlocked` is true when `integrity` is false and lifetime is false. A thrown command becomes an expired, sideload-blocked licence. The test APK does not call this command.
- **Desktop Rust stub** (used only if the plugin channel is skipped): lifetime false, support false, integrity false, trial null. That stub does **not** forward to Kotlin.
- **Side effect:** `pingIntegrity()` sends a random 16-byte nonce to Play Integrity and ignores the token. No PAN, password, or vault is sent.

#### `play_purchase`

- **Callers:** `purchaseLifetime` with `{ "sku": "gud_lifetime" }`. `purchaseSupport` with `{ "sku": "gud_support_annual", "offer": "standalone" }`.
- **Success:** `{}`. The UI then calls `play_licence_status` again. On the test APK the plugin returns `{}` without a billing sheet, and JavaScript instead sets `localStorage`.
- **Errors:**
  - `"Google Play Billing is not ready."`
  - `"Install GrowUrDhan from Google Play to buy a licence."`
  - `"Buy the ₹249 lifetime licence first. Support cannot be purchased on its own."`
  - `"Product <sku> is not in Play Console yet."`
  - `"Purchase cancelled."`
  - Play’s debug message, or `"Play Billing error <code>"`.
  - Desktop stub: `"Purchases are only available in the Google Play build."`
- **Effect:** opens the Play billing sheet. Support is a subscription; lifetime is a one-time product. Offer token selection: matching offer id or base-plan id, else base plan `standalone`, else the first offer. `setObfuscatedAccountId` is the constant `"gud"`, not the user’s email.

#### `play_restore`

- **Caller:** the Restore button. Skipped on the test APK and in the browser.
- **Arguments:** none.
- **Success:** the same object as `play_licence_status`.
- **Effect:** re-queries Play. Does not send vault data.

#### `play_review`

- **Caller:** Family “Rate on Google Play”, and the feedback prompt.
- **Arguments:** none.
- **Success:** `{ "launched": true, "fallback": false }` if the in-app review sheet opened. `{ "launched": false, "fallback": true }` if the listing was opened instead. The test APK and a non-Play install always take the fallback. The plugin does not reject when Play review fails; it opens the listing.
- **Desktop stub:** `{ "launched": false, "fallback": true }`, and it does not forward.
- **Effect:** Play In-App Review API, or `market://details?id=in.growurdhan.app`, or `https://play.google.com/store/apps/details?id=in.growurdhan.app`. The rating is not read back.

#### `play_open_listing`

- **Caller:** only if `play_review` throws.
- **Arguments:** none.
- **Success:** `{}` after opening the listing.
- **Desktop error:** `"Google Play reviews are only available in the Android build."`
- **Effect:** store listing only. Package name only. No body.

### Statements

#### `cas_health`

- **Caller:** Documents screen, on open.
- **Arguments:** none.
- **Success:** always resolved, even when Python failed.

```json
{
  "ok": true,
  "engine": "PYTHON_CASPARSER",
  "detail": "<JSON from cas_android.health()>",
  "logPath": "Download/GrowUrDhan-diagnostics.txt",
  "logPrivate": "<app private log path>",
  "logError": ""
}
```

When Python is not importable, `ok` is false and `error` is set. `detail` is the raw health JSON: `ok`, `engine`, `python`, `casparser`, `pypdfium2`, `server`, and on failure `error` and `trace`.

- **If the Android plugin was not registered:** `{ "ok": false, "engine": "PYTHON_CASPARSER", "error": "Android CAS plugin was not registered.", "logPath": "Download/GrowUrDhan-diagnostics.txt" }`.
- **Desktop Rust:** `{ "ok": false, "engine": "PYTHON_CASPARSER", "error": "Engine is not running." }`.
- **Browser:** does not use this command. It uses `GET /api/cas/health` on the sidecar (C4).
- **Effect:** starts Chaquopy if needed and imports casparser. No network. No PDF. The UI shows “Engine is running.” or “Engine is not running.”

#### `cas_parse`

- **Caller:** `parseCasPdfWithPython`, from the Documents “Read statement” action.
- **Arguments:**

```json
{
  "pdfBase64": "<standard base64>",
  "password": "<statement password>",
  "filename": "statement.pdf"
}
```

- **Success:** `{ "payload": "<JSON string>" }`. The UI parses `payload`.

Success payload fields:

| Field | Meaning |
|---|---|
| `ok` | true |
| `engine` | `PYTHON_CASPARSER` |
| `fileType` | `CAMS`, `KFINTECH`, `NSDL`, `CDSL`, or `NPS` |
| `casType` | detailed or summary, when the parser knows |
| `investorName`, `investorPan`, `investorEmail` | from the statement |
| `periodFrom`, `periodTo` | statement period |
| `accounts[]` | `name`, `type`, `dpId`, `clientId`, `folios`, `balance` |
| `holdings[]` | see below |
| `warnings` | parser warnings |
| `skippedNilHoldings` | rows with no units |
| `pdfTotals` | `demat`, `mutualFund`, `aif`, `grand` as printed |
| `parsedTotals` | `holdingsCount`, `holdingsValue` |
| `delta`, `complete` | parsed total versus printed total. `complete` is true when the absolute delta is under ₹1 |
| `filename` | the name passed in |

Each holding may include `kind`, `name`, `isin`, `quantity`, `price`, `value`, `cost`, `folio`, `amfi`, `schemeType`, `ucc`, `symbol`, `exchange`, `dpName`, `dpId`, `clientId`, `category`, `accountType`, and for CAMS/KFin also `amc`, `rta`, `advisor`. Numbers in this JSON are rupees and units, not minor units. The repository converts them on commit.

Failure payload, still inside `payload` (Android does not use HTTP status codes):

```json
{ "ok": false, "error": "Incorrect password", "code": "BAD_PASSWORD", "detail": "" }
```

`code` is `BAD_PASSWORD`, `PARSE_ERROR`, or `INTERNAL`. Empty PDF is `PARSE_ERROR` / `"Empty PDF."`.

- **Rejected command** (promise fails, no payload): `"Missing statement file."`, the Python traceback text, `"Android CAS plugin was not registered."`, or desktop `"Engine is not running."`.
- **Effect:** in-process parse. Temp PDF deleted. Logs store `passwordChars` and `pdfChars`, never the password or the PDF. No upload.

### Prices

#### `prices_refresh`

- **Caller:** header refresh and the holding search.
- **Arguments:** `{ "isins": "INE000A00000,INF000A00000" }`. Empty string means “do not filter”.
- **Success:**

```json
{
  "ok": true,
  "asOf": "2026-09-26",
  "catalog": [
    {
      "isin": "INE000A00000",
      "name": "Example Ltd",
      "symbol": "EXAMPLE",
      "nse": "EXAMPLE",
      "cat": "EQUITY",
      "px": 123.45,
      "dt": "2026-09-26",
      "src": "NSE close"
    }
  ],
  "catalogCount": 1,
  "warnings": []
}
```

`cat` is `EQUITY`, `GOLD_SGB`, or `MUTUAL_FUND`. `src` is `NSE close`, `BSE close`, or `AMFI NAV`. Mutual-fund rows use `amfi` instead of `nse`/`bse`. `px` is rupees or NAV, not minor units. The Android response does not include `nseAsOf`, `bseAsOf`, or `sources`. Those exist only on the desktop sidecar.

If `isins` is non-empty and nothing matched:

```json
{
  "ok": false,
  "error": "No last close or NAV matched the ISINs in this vault.",
  "asOf": null,
  "catalog": [],
  "catalogCount": 0,
  "warnings": []
}
```

Warning text, if any, is appended to `error`.

- **Rejected:** `"Could not refresh prices on this phone."` Desktop Rust: `"This command is only available in the Android app."` A desktop Tauri shell does not fall through to the HTTP sidecar. Only a normal browser does.
- **Effect:** HTTPS GET of official files. ISINs stay on the phone. No PAN, no vault.

### Diagnostics

Default is off. Family is the only switch.

#### `diag_status`

- **Arguments:** none.
- **Success:** `{ "ok": true, "enabled": false }`.
- **Effect:** reads preference `gud_diag`. No file write. No network.

#### `diag_set`

- **Arguments:** `{ "enabled": true }`.
- **Success:** `{ "ok": true, "enabled": true }`. Turning on appends one line, `diagnostics enabled`.
- **Effect:** writes the preference. If the flag fails to parse, the plugin forces off.

#### `diag_log`

- **Caller:** the redaction pump, and Rust `phone_log` (prefix `rust `). Not a button.
- **Arguments:** `{ "line": "<text, max 2000 chars>" }`.
- **Success:**

```json
{
  "ok": true,
  "path": "Download/GrowUrDhan-diagnostics.txt",
  "privatePath": "<path>",
  "writeError": ""
}
```

`ok` is false when the last write failed. Desktop: `{ "ok": true, "path": "desktop-noop", "echo": "<line>" }`.

- **Effect:** `Log.i`. If enabled, append to the app’s external-files copy and to `Download/GrowUrDhan-diagnostics.txt` (MediaStore on API 29+). No new numbered file each time. No network.

### Backup

All byte arguments are the already-encrypted `.kosha` file, not the password.

#### `backup_pick_folder`

- **Arguments:** none.
- **Success:** `{ "chosen": false }` if the user cancels. `{ "chosen": true, "label": "<folder name>" }` if they pick a tree.
- **Effect:** `ACTION_OPEN_DOCUMENT_TREE` with advanced storage shown, persistable read/write permission, URI saved in encrypted preferences. No file bytes. No network. Desktop: `"This command is only available in the Android app."`

#### `backup_clear_folder`

- **Arguments:** none.
- **Success:** `{}`.
- **Effect:** releases the persistable permission and deletes the stored URI. Does not delete `.kosha` files already written.

#### `backup_write_scheduled`

- **Arguments:**

```json
{
  "filename": "GrowUrDhan-2026-09-28.kosha",
  "bytesB64": "<ciphertext>",
  "keepLast": 5
}
```

- **Success:** `{ "kept": 3, "label": "GrowUrDhan backups" }`.
- **Errors:**
  - `"Choose a Google Drive or OneDrive folder first."`
  - `"That folder is no longer available. Pick it again."`
  - `"GrowUrDhan cannot write in that folder. Pick it again."`
  - `"Could not create the backup file in that folder."`
  - `"Could not write the backup file."`
  - `"Could not write the scheduled backup."`
- **Effect:** creates the file in the chosen tree and deletes older `*.kosha` files beyond `keepLast` (5 if the number is missing or below 1). The cloud provider may upload the file later. This command does not talk to Drive’s HTTP API.

#### `backup_save`

- **Arguments:** `{ "filename": "growurdhan.kosha", "bytesB64": "<ciphertext>" }`.
- **Success:** `{ "saved": true }`, or `{ "saved": false }` if the user cancels. Cancel is not an error.
- **Errors:** `"Could not open the save dialog on this phone."`, `"Could not write the backup file."`
- **Effect:** `ACTION_CREATE_DOCUMENT`. One file, wherever the user saves it. On a desktop shell the JS layer falls through to the browser save picker after the Android-only error.

#### `backup_open`

- **Arguments:** none.
- **Success:** `{ "value": "<base64>" }`, or `null` if the user cancels or the stream is empty.
- **Error:** `"Could not open that backup."`
- **Effect:** `ACTION_OPEN_DOCUMENT`, reads the whole file into memory. Decrypt happens in JavaScript after this returns. On a non-Android shell the JS layer falls through to an `<input type=file>`.

### Process

#### `app_exit`

- **Caller:** “Don’t accept” on the terms gate.
- **Arguments:** none.
- **Success:** `{}`, then `finishAffinity()`.
- **Effect:** closes the Android task. Does not delete the vault. If the command fails, the UI still shows the closed screen. Desktop: `"This command is only available in the Android app."`

## C3. Android callbacks that are not commands

These complete a command. The UI must not call them.

| Name | Completes |
|---|---|
| `backupPickFolderResult` | `backup_pick_folder` |
| `backupSaveResult` | `backup_save` |
| `backupOpenResult` | `backup_open` |
| `onPurchasesUpdated` | `play_purchase` |

## C4. Developer HTTP sidecar

**Not in the APK.** Started by `python3 scripts/cas_parse_server.py`. Bind address `127.0.0.1`, port `8765`. Vite proxies `/api/cas` to it. CORS allows any origin for GET, POST, and OPTIONS, header `Content-Type` only. This is acceptable only because the socket is loopback on the developer’s machine. Do not expose it.

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` or `/api/cas/health` | none | 200 `{"ok":true,"engine":"PYTHON_CASPARSER","eod":true,"issuers":["CAMS","KFINTECH","NSDL","CDSL","NPS"]}` |
| POST | `/parse` or `/api/cas/parse` | JSON below | 200, or 401 if `code` is `BAD_PASSWORD`, or 422 otherwise |
| POST | `/prices/refresh` or `/api/cas/prices/refresh` | empty body | 200 with the desktop price document, or 502 if `ok` is false. 500 `{"ok":false,"error":"Could not refresh prices: ..."}` on an exception |
| OPTIONS | any | | 204 |
| other | | | 404 `{"ok":false,"error":"Not found"}` |

Parse body:

```json
{
  "pdfBase64": "<standard base64>",
  "password": "<statement password>",
  "filename": "statement.pdf"
}
```

Limits: body over 30 MB → 413 `"PDF is too large (30 MB limit)."` Missing `pdfBase64` → 400. Bad JSON → 400. Bad base64 → 400. The PDF is a temp file and is deleted. The success JSON matches the Android `payload` in C2, with `filename` added.

The desktop price document is the full market catalog, not filtered by the vault:

```json
{
  "ok": true,
  "asOf": "YYYY-MM-DD",
  "nseAsOf": "YYYY-MM-DD",
  "bseAsOf": "YYYY-MM-DD",
  "amfiAsOf": "YYYY-MM-DD",
  "sources": [{ "name": "NSE", "asOf": "YYYY-MM-DD", "count": 0 }],
  "warnings": [],
  "catalog": [],
  "catalogCount": 0,
  "error": null
}
```

CLI form, no socket: `python3 scripts/cas_parse_server.py --file X.pdf --password '...'` prints JSON to stdout.

## C5. Outbound network

Cleartext is off. No command puts a PAN, a statement password, vault bytes, or a `.kosha` blob on the wire.

### Price files (GET only)

| URL pattern | Referer sent | Notes |
|---|---|---|
| `https://www.amfiindia.com/spages/NAVAll.txt` | `https://www.amfiindia.com/` | First choice |
| `https://portal.amfiindia.com/spages/NAVAll.txt` | same | If the first body is empty |
| `https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_{YYYYMMDD}_F_0000.csv.zip` | `https://www.nseindia.com/all-reports` | Up to 6 calendar days on the phone |
| `https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_{YYYYMMDD}_F_0000.CSV` | `https://www.bseindia.com/` | Same day walk |

Headers are a browser User-Agent, `Accept: */*`, and the Referer above. Connect timeout 10 seconds, read timeout 40 seconds. No cookies, no ISIN query string, no authorization header.

### Google

| Call | Sent | Not sent |
|---|---|---|
| Play Billing | Product ids `gud_lifetime` and `gud_support_annual`. Obfuscated account id `"gud"` | Email, PAN, vault, holdings |
| Play Integrity | 16 random bytes, result ignored | Any user field |
| In-app review | The review request | The star rating never returns to the app |
| Store listing | Package name `in.growurdhan.app` | Nothing else |

### Pages the user opens in a browser

Family links, ordinary navigation, no payload: `https://shahvikask.github.io/kosha/privacy.html`, `terms.html`, and `delete-account.html`.

## C6. Error strings that the UI depends on

These are part of the contract. Do not reword them without changing the screen that matches on them.

| String | Who emits it | Why it must stay |
|---|---|---|
| `Engine is not running.` | Desktop `cas_health` / `cas_parse`, and the Documents banner | The owner asked for this exact sentence |
| `Engine is running.` | Documents banner when `cas_health.ok` | Same |
| `No last close or NAV matched the ISINs in this vault.` | `prices_refresh` | Home and header show it |
| `Buy the ₹249 lifetime licence first. Support cannot be purchased on its own.` | JS and Kotlin | Licence screen |
| `Install GrowUrDhan from Google Play to buy a licence.` | Kotlin | Sideload of a Play-signed build |
| `ledger_event is append-only: record a CORRECTION event` | SQLite triggers | Any raw delete other than `deleteHoldingEvents` |

---

## Appendix A — Asset behaviour cheat sheet

| User action | Book entry | Balance |
|---|---|---|
| Add PPF/SSY/EPF/NSC/KVP/RD/cumulative FD/Other for an old year | `BUY` of quantity 1, then `INTEREST` on each missed 31 March | Grows to the projected value |
| Add a payout FD | `BUY` of quantity 1, then `INTEREST` cash on each payout date | Stays at the deposit |
| Add SGB with issue price and acquired date | `BUY` of grams, then half-year `INTEREST` | Market value is price × grams, not issue price. Coupon does not add to grams |
| Type a new current value and save | price source becomes Manual Verified | That number stays. Compound catch-up will not overwrite it |
| Change invested | `CORRECTION` superseding the opening lot | Old row remains |
| Delete manual holding, Yes | holding row and its events gone | Trigger restored afterwards |
| Delete manual holding, Cancel | holding row gone, events remain | Events no longer affect totals |
| Delete a CAS holding | holding row gone, events remain | Always |
| REIT monthly income filled in | `DIVIDEND` notes `Monthly income on YYYY-MM-DD` | Price unchanged. Stops after end date |
| Refresh prices | `current_price_minor` for matched ISINs | Skips loans, cash, and non-REIT property |
| Import CAS | snapshot adjustment, not a second buy of the same units | Statement price does not replace an official close |

## Appendix B — File index for a reviewer

| Question | Open this first |
|---|---|
| Interest math | `src/lib/interest.ts` |
| Tax class | `src/lib/taxRules.ts` |
| FIFO | `src/lib/lots.ts` |
| SQL and the delete exception | `src/db/repository.ts`, `src/db/schema.ts` |
| Workflows | `src/context/KoshaContext.tsx` |
| Password hash | `src/lib/auth.ts` |
| Backup crypto | `src/lib/backup.ts` |
| Phone file crypto | `GrowUrDhanPlugin.kt` `vault_blob_*`, `src/db/encryptedStore.ts` |
| Every command | `src-tauri/src/lib.rs`, `GrowUrDhanPlugin.kt` |
| Who may call them | `src-tauri/permissions/android-vault.toml` |
| Play versus test APK | `src/lib/license.ts`, `android-aab.yml`, `android-apk.yml` |
| Statement parse | `scripts/cas_android.py`, `scripts/cas_parse_server.py` |
| Prices | `PriceFetcher.kt`, `src/lib/eodPrices.ts` |

---

*End of as-built document. Generated from `GUD-Android` commit `95461c4`. It was not committed back to the repository, so it did not start another Play build.*
