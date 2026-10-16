# Holding-detail read-only rows — M00 inventory

Read from `src/components/modals/HoldingDetailModal.tsx`, `src/lib/holdingForm.ts` `detailsLines`, and `src/lib/holdingView.ts` at `af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Those files were not edited. This is not a native screen.

If `holdingId` is missing, or no holding has that id, the modal renders nothing.

## Header

| Row | When | Source |
|---|---|---|
| Colour dot | always | category colour |
| Name | always | `displayHoldingName`. A leading short code and hyphen, after a `#` when one is present, is dropped |
| Owner chip | the member exists | member name and colour |
| Category line | always | short category name, subcategory when set, then `whereHeld` |
| Where held | the same line | institution, or depository name, or AMC, then masked account or folio. If both parts are empty, the text is “Location not recorded” |

Edit and Close are buttons, not rows.

## Three value cards

Amounts go through `PrivacyValue`, so a privacy mask can hide the figure. The label stays visible.

| Row | When | Empty or special case |
|---|---|---|
| Worth today | not a liability | certificate SGB adds the RBI rupee-per-gram line, or “The price saved on this holding is used” when no IBJA rate is stored. Demat SGB with a rate and a quantity adds the RBI redemption figure. The card itself still uses the exchange price |
| Still owed | liability | no SGB lines |
| Put in (Invested) | not a liability | a dash when invested is unknown |
| Original loan | liability | same cost figure |
| Statement has no buy price — add what you paid | not a liability, and the history is statement-only or invested is unknown or within ₹1 of value | this is a button into edit mode |
| Gain or loss | not a liability | “Need what you paid” when invested is unknown. Otherwise the signed gain |

## Return panel

Shown only when the holding is not a liability.

| Row | Meaning |
|---|---|
| Return for this instrument | section title |
| Abs. return | `holdingReturn` primary text |
| Annual return | secondary text |
| Profile reason | `profile.why` |
| Annual reason | `annualWhy` |
| Benchmark limit | fixed text: Nifty, category average, G-sec, and CRISIL are not in the vault. CMP is last close or NAV, not live |

## Quantity and price line

Hidden for a liability.

| Row | When |
|---|---|
| Quantity and unit | quantity is positive. Gold says grams. Equity, and unlisted or ESOP, say shares. Other unit holdings say units. A non-unit holding omits this part |
| Price each | always on this line |
| change price | button. It opens a price input and Save. Those are not read-only |
| Price source and date | `lastPriceUpdated` is set. NSE, BSE, or AMFI adds “last close, not live” |

## Fact grid

`field` draws a label and a value only when the value is non-empty. Dates in `YYYY-MM-DD` are formatted.

| Label | When |
|---|---|
| AMC | `amc` |
| RTA | `rta` |
| AMFI code | `amfiCode` |
| ISIN | `isin` |
| Symbol | equity, or the name looks like an ETF |
| Folio | every other holding, same `symbolOrFolio` value |
| Demat / BOID | masked account number |
| Depository | `dpName` |
| Exchange | `exchange` |
| Advisor | `advisor` |
| Family note (not the registrar nominee) | the linked account has a nominee |
| Lender | liability and `institutionName` |
| Bank | cash and `institutionName` |
| Institution | any other category and `institutionName` |
| Location | `location` |

`detailsLines` then adds a row only when that detail is stored:

| Label | Value |
|---|---|
| From | `acquiredOn` |
| Held as | REIT/AIF mode: Direct lumpsum or Demat (CAS). A mutual-fund mode later on the same list can add a second Held as row: Demat or SOA folio. Gold mode adds another: Demat or RBI / bank certificate |
| Plan | Direct or Regular |
| Option | IDCW or Growth |
| DP ID | uppercased |
| Client ID | as stored |
| BOID | `details.boId`. This is separate from the masked Demat / BOID row |
| Purity | stored purity |
| Hallmark / invoice | stored text |
| First exit (after year 5) | date |
| Yearly contribution | rupees, no paise |
| Contribution date | date |
| Interest | compounding: Cumulative compounded quarterly, or monthly, quarterly, half-yearly, or yearly payout |
| NPS tier | Tier I or Tier II |
| CRA | Protean, otherwise the stored code |
| Pension fund | stored name |
| TDS | “Yes (Form 16A / 26AS)” |
| Face value | rupees, no paise |
| Area | square feet |
| Ownership | Self, Joint, Spouse, or HUF, with the percent when stored |
| AIF category | Category I, II, or III |
| Commitment | rupees, no paise |
| Capital called | rupees, no paise |
| Strategy | stored name |
| Custodian | stored name |
| IFSC | uppercased |
| Rate | Floating or Fixed |
| Original tenor | months |
| Linked property | liability with `linkedHoldingId`. The name is used when that holding exists. Otherwise “Linked holding” |
| Linked loan | any other category with `linkedHoldingId`, same fallback |

These stored facts are not read-only rows: monthly income, income day, income start, income end, goal tags, and private notes. Income can be edited, but the closed view does not print it. Tags and notes are not shown in this modal at all.

## Loan EMI block

Shown only for a liability.

| Row | When |
|---|---|
| Loan EMI | section title |
| Monthly EMI | `monthlyEmiMinor` |
| Interest | `interestRatePercent`, as “% a year” |
| Remaining | `remainingEmiCount`, as months |
| Next EMI | `nextCouponDate` |
| Status | Paused, Stopped, or otherwise Paying |
| Catch-up note | fixed text: a due EMI is written when the vault opens, and outstanding falls by the EMI |

Resume EMI, Pause EMI, and Stop EMI are buttons. Resume is hidden while status is Paying. Pause is hidden when Paused or Stopped. Stop is hidden when Stopped.

## Dates that matter

The block appears when there is a next-coupon date, a maturity date, a coupon percent, an ELSS name or subcategory, or a PPF, EPF, NPS, Sukanya, or SSY name or subcategory.

| Row | When |
|---|---|
| Coupon percent | `couponRatePercent`. Gold adds “gold bond interest; booked on 31 March” |
| Fixed-income rate | `interestRatePercent` on fixed income, labelled “credited 31 March” |
| Next interest | `nextCouponDate` |
| Matures | gold |
| Matures / withdrawable | the government-scheme name test |
| Lock-in ends | the ELSS name test, when a maturity date exists |
| Matures | every other maturity date |
| ELSS hint | ELSS with no maturity date |
| Government-scheme hint | PPF, EPF, NPS, or Sukanya with no maturity date |

## Record book

| Row | When |
|---|---|
| No rows yet. Confirm a statement or add a buy. | no events for this holding |
| Event title | up to 20 events, newest first. Labels are Opening from statement, Updated to match statement, Bought, Sold, Bonus shares, Share split, Dividend, Interest, Loan recorded when a valuation note mentions a loan, otherwise Value updated, Loan repayment, Buy price you entered. An unknown type prints the raw type |
| Event date and notes | the same row. Notes are appended after the date |
| Event amount | `totalAmountMinor`, masked, no paise |

Older events beyond the first 20 are not shown. There is no “show more” control.

## Actions that are not rows

Record a buy or sell, or Record an extra payment for a liability. Remove holding asks for confirmation. A statement-backed holding says records from the statement are kept. Any other holding asks whether the record-book rows should also be deleted. A failure shows `window.alert` with the error message, or “Could not remove this holding.”

Edit mode and its inputs are already listed in `HOLDING-FORM-WIDGETS.md`. Saving what was paid can show “Could not save what you paid — this holding has no units on the books.” A blank name or a negative price returns without a message.
