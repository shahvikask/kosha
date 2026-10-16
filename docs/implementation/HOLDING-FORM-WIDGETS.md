# Holding-form widgets — M00 inventory

Read from `src/components/modals/AddHoldingModal.tsx` and `src/components/modals/HoldingDetailModal.tsx` at `af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Those files were not edited. This is not a native screen and it is not M24.

Subcategory lists are `ASSET_SUBCATEGORIES` in `src/types.ts`. Value-mode helpers are `usesUnits`, `isReitLike`, `isAifSub`, `isElssSub`, and `isIndexOrEtfSub` in `src/lib/holdingForm.ts`.

## Add holding — always present

| Widget | Control | Notes |
|---|---|---|
| Close | button | `onClose` |
| Asset Class * | select | eight categories. Changing it resets the subcategory to the first entry |
| Category description | text | not an input |
| Search NSE / BSE / AMFI * | text, result buttons, Change, Refresh prices | only Equity and Mutual Funds. A listed row must be picked. Typing a name by hand is off |
| Holding Name * | text | every other category |
| Family Owner * | select | current family members |
| Folio / ticker label | text | label comes from `identifierLabel`. Read-only after an equity pick |
| Subcategory * | select | the category’s list |
| Acquired-on date | date | `showAcquiredOn` is hard-coded true. The label changes by category |
| Goal tag text | text | Enter or comma also adds |
| Add Tag | button | |
| Remove tag | button | one per applied tag |
| Goal preset | button | family goal names plus Retirement, Child Higher Education, Emergency Fund, Wealth Compounding, House Purchase, Tax Saving (80C), Vacation / Travel |
| Private notes | text | |
| Cancel | button | |
| Save Holding / Record buy on existing | submit | the second label appears when the same owner already has the picked ISIN |

## Add holding — one value block

Exactly one of these blocks is shown.

| When | Widgets |
|---|---|
| Liability | Total loan amount *, Current outstanding *, Monthly EMI *, Interest rate, Rate type (Not sure / Floating / Fixed), Original tenor (months), Remaining EMI months *, Next EMI date *, Lender, Linked property (only if this owner has a non-REIT property) |
| AIF and held as is not Demat | Commitment *, Capital called, Current value *, Fund manager / AIF. This is the default, because an empty mode is treated as lumpsum |
| Units | Quantity *, buy price *, current price. Current price is read-only after a listed pick. Units are equity, mutual funds, all gold, bond/GOI, REIT/InvIT unless lumpsum, unlisted/ESOP, and AIF only when held as demat |
| One balance | Amount deposited * for PPF, EPF, Sukanya, Bank FD, SCSS, NSC, KVP, POMIS, Post Office RD, RBI Floating Rate Bond, and Fixed Income Other. NPS uses the same input with the label Current value *. One number is copied into both cost and current |
| Everything else | Current value * (or Current balance *), and Amount put in. PMS says Current value * and Amount invested |

## Add holding — extra panels

| When | Widgets |
|---|---|
| REIT, InvIT, or AIF | Held as *: Demat units or Direct lumpsum. AIF also has AIF category, required only for lumpsum |
| Equity | Exchange (Not sure / NSE / BSE), Depository (Not sure / NSDL / CDSL), DP ID and Client ID only for NSDL, BOID only for CDSL, Buy charges, Broker |
| Mutual fund, not an ETF name | Plan, Option, RTA (CAMS / KFin), Held as (SOA folio / Demat) |
| Mutual fund exchange | shown for an ETF name, or for demat plus an Index/ETF subcategory |
| Mutual fund demat ids | same depository, DP ID, Client ID, and BOID rules as equity, when held as demat or the name looks like an ETF |
| Mutual fund, always in this panel | AMC, Buy charges. ELSS also has Lock-in ends |
| Sovereign Gold Bond | Issue price, SGB coupon, Held as (Demat / certificate), Next interest date, First exit, Matures |
| Physical gold | Purity (24K / 22K / 999, default 24K), Jeweller, Hallmark / invoice no. |
| Digital gold | Provider |
| Gold Other | no extra panel |
| PPF | Interest rate, 15-year block ends, Bank / post office |
| EPF | Interest rate, Employer / EPFO |
| NPS | Tier (default I), CRA (Protean / CAMS / KFin), Pension fund |
| Sukanya | Interest rate, Matures, Bank / post office |
| Bank FD | Interest rate, Compounding (default Cumulative, plus monthly, quarterly, half-yearly, yearly), Matures, Bank, TDS checkbox |
| SCSS, NSC, KVP, POMIS, Post Office RD, RBI Floating Rate Bond | Interest rate, Matures, Bank / post office. The hint text changes by subcategory. It is not an input |
| Bond / GOI | ISIN, Face value, Coupon, Matures |
| Fixed Income Other | Interest rate, Matures / lock-in ends, Bank / institution |
| REIT/InvIT demat | ISIN, Exchange text (this one is not the NSE/BSE select) |
| REIT/InvIT lumpsum | Manager / vehicle |
| REIT or InvIT, either mode | Monthly income, Paid on day 1–28, From, Until |
| AIF demat | ISIN, Fund manager / AIF |
| Property that is not REIT/InvIT | Address, Carpet / built-up, Ownership (Self / Joint / Spouse / HUF), Your share % only for Joint or Spouse, Linked home loan only if this owner has a loan |
| PMS | Strategy name, PMS house, Custodian |
| Cash, not liquid | Bank, IFSC |
| Liquid / overnight | AMC, ISIN |
| Specialised other than AIF and PMS | Company / manager |

## Add holding — save behaviour that is not a widget

`handleSubmit` returns without a message when no listed security was picked, the name is blank, a unit quantity is not positive, or cost or current value is negative. HTML `required` also blocks submit on the starred number and date fields.

`fyContributionRupees` is state and would be saved as `annualContributionMinor`, but no input is bound to it. `contributionOn` has no input either. PPF and Sukanya therefore do not ask for this year’s contribution on this form.

## Holding detail — edit mode

`HoldingDetailModal` shows an Edit button. The edit block is not the add wizard. Its inputs are:

| Widget | When |
|---|---|
| Name, Folio / ticker, current price or outstanding | always in edit mode |
| Amount paid, Bought on | not a liability. Labels change for equity, SGB, other units, and fixed income |
| Issue price | SGB |
| Interest / coupon, Next interest date, Matures / lock-in ends | gold or fixed income |
| Monthly EMI, Interest, Remaining EMI months, Next EMI date | liability |
| Monthly income, Paid on day, Income from, Income until | REIT or InvIT |

The note under those inputs says units are not edited here. A buy or sell changes units. Category-specific add-form fields such as depository, purity, NPS tier, and IFSC are not repeated in this edit block.

## Not done

This inventory does not draw the native form, does not persist a holding, and does not cover every read-only row inside the holding-detail view. Those rows are display, not form widgets.
