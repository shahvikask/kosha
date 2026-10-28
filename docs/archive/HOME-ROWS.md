# Home-screen read-only rows — M00 inventory

Read from `src/components/screens/HomeScreen.tsx`, `src/lib/homeDashboard.ts`, `src/lib/holdingView.ts`, `src/lib/growth.ts`, and `src/lib/taxonomy.ts` at `af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Those files were not edited. This is not a native screen.

Money rows use `PrivacyValue` or `PrivacyDelta`. A privacy mask can hide the figure. The label stays. Amounts are shown without paise. The figures follow the selected family member. “The family” means everyone. A missing member name falls back to “Family”.

The hero is always shown. Everything under “Start with your statement” is shown only when the filtered book has no holdings. Everything under the action cards is shown only when it has at least one holding.

## Hero

| Row | Source |
|---|---|
| `{who} · Net worth` | selected member, or “The family” |
| Net-worth figure | assets minus liabilities for that filter |
| Last statement | short statement date, or “None yet” |
| Prices | short price date, or “Not refreshed” |
| `FY {year} income` | current financial-year income from the growth model |
| Absolute return | signed gain and percent when a buy price exists |
| Need what you paid | when invested is unknown and the invested total is not positive. The line under it says CAS has no buy price on this holding, or on N holdings |
| Partial-return note | “On holdings with a buy price · N still need cost”, or “Current value compared with invested amount” |
| Invested | growth-model invested total |
| Assets | growth-model asset total. The bar is always full width |
| Loans | liability total. A positive amount is prefixed with a minus |
| Net worth | the same net-worth figure as the hero. A negative net worth draws an empty bar |

The statement and price chips use one freshness colour. A statement 90 or more days old is red. A statement 45 or more days old, or a price date seven or more days away from the statement date, is amber. Otherwise the chips are green. A missing statement does not by itself turn the chip red.

XIRR, day change, and dividend totals are calculated on the portfolio summary. They are not home rows. Day change is fixed at zero in that calculation.

## Empty book

| Row | Text |
|---|---|
| Title | Start with your statement |
| Body | Drop your CAS PDF. We read it on this phone and show every holding for you to confirm. Nothing is added until you say so. |

“Read a statement” and “Add PPF, property or a loan” are buttons.

## Books health

Shown only when the book is not empty. A dismissed finding stays dismissed in `localStorage` under `kosha.health.dismissed`. The headline counts only the findings still visible.

| Headline | When |
|---|---|
| Books look consistent | no visible finding |
| 1 thing to check | one visible finding |
| `{n} things to check` | more than one |

The empty line is “Invested, statements and family PAN look in order for this view.”

| Finding | Title | Detail |
|---|---|---|
| Cost equals value | “Invested still matches current value”, or “{n} holdings still use statement value as invested” | one name plus “has no separate purchase cost. CAS does not print that.”, or up to two names, then “and others” when there are more than two |
| Stale statement | “{issuer} statement is {n} days old” | “Last confirmed through {date}. Read a newer CAS or NPS file to refresh.” Amber from 45 days, red from 90. The issuer is the first word of CDSL, NSDL, CAMS, KFintech, or NPS |
| Missing prior-year interest | “No FY {year} interest on {name}”, or “{n} deposits have no FY {year} interest” | “Open the holding and confirm the interest. Payout schemes post on their own dates; PPF, SSY and EPF post on 31 March.” |
| Missing PAN | “{name} has no PAN on file”, or “{n} family members are missing a PAN” | “CAS passwords and matching use PAN. Save it on the Family screen.” Only members who already have a statement and no statement PAN |

Each finding is a button. Dismiss is a separate button.

## Add what you paid

Shown when at least one holding still needs a cost. The count is the full list. Only the first six names are rows. Each row is the holding name, its current value, and “Need what you paid”. “View all {n}” appears after six.

## Charts

| Section | Rows |
|---|---|
| Where the money sits | one donut slice per positive asset bucket: Shares, Mutual funds, Gold, Govt schemes, Property, Bank & deposits, AIF & others. Loans are excluded. The centre says Assets and the asset total. Empty text: “No assets to chart.” |
| Look-through mix | “Equity, debt and gold after opening hybrid funds.” A bar appears only for a positive exposure: Equity, Debt & fixed income, Gold & commodities, Property, Cash & liquid, Private & unlisted, Protection. “What you owe” is excluded |
| Whose name it is in | only when everyone is selected and more than one member has a positive asset value. The label is the member name |

Selecting a slice filters “What moved the needle”. Selecting it again clears the filter. There is a `fy` drill type in the source, but no control sets it.

## Time charts

| Section | Rows |
|---|---|
| Assets over time | “Statement value. Flat between files.” Change is the first-to-last percent. Empty chart text: “Import two statements to see assets move.” Under the chart, one filed statement prints “{issuer} through {date}”. More than one prints the last three as “{issuers} {date}”, joined by “ · ” |
| Invested over time | “Cost printed on each file, carried forward.” Change is the first-to-last invested percent. Empty chart text: “CAMS/KFin prints cost. CDSL usually does not — add what you paid.” |

Issuer names are “CDSL (Demat + Mutual Funds)”, “NSDL (Demat + Mutual Funds)”, “CAMS Mutual Funds”, “KFintech Mutual Funds”, and “NPS (Protean / CRA statement)”. An unknown issuer prints its raw code. The live pad to today is not a statement caption.

## Since last statement

Shown only when a statement date exists.

| Row | Meaning |
|---|---|
| Since last statement | title |
| Comparison line | “{date} file vs today's prices on those same holdings.” |
| Delta | today’s value minus the file value |
| `{n} new since then` | only when new holdings exist |
| On the file | statement total |
| Now | current total for those holdings |
| Mover | holding name and its delta. If none: “Prices have not moved from the statement yet, or refresh EOD prices.” |

## Gain and concentration

| Section | Rows |
|---|---|
| Gain booked vs still on paper | Unrealised and Realised. A negative figure is drawn as zero |
| Largest holdings | “No assets to rank.”, or “Top {n} = {percent}% of assets”. At most five positive asset holdings. Each row is the name and its percent of assets |

## What moved the needle

The title changes to the selected bucket, exposure, or member. The subtitle stays “Holdings ranked by absolute gain.” Loans are excluded. The default list is three rows. “View more ({n})” and “Show less” expand it. An empty filter says “Nothing in this slice.”

| Row | Content |
|---|---|
| Name | display name |
| Owner and bucket | member name, then Shares, Mutual funds, Gold, Govt schemes, Property, Bank & deposits, or AIF & others |
| Value and gain | hidden when invested is unknown. That row says “Need what you paid” instead |

“All holdings” is a button.

## Goals and coming up

Goals are shown only when at least one goal exists. Each row is the goal name and a percent capped at 100. A target of zero is 0%. “Edit” is a button.

Coming up is shown only when a future item exists. The list is capped at 24 and sorted by date, then title. The screen first shows two. “Show {n} more” reveals the rest.

| Title | When |
|---|---|
| EMI due · {name} | a loan with a future EMI date that is not stopped |
| Gold bond interest · {name} | gold with a future coupon date |
| Interest · {name} | another future coupon date, or the next scheduled interest date when no coupon date was used |
| Monthly income · {name} | the next monthly-income date |
| Gold bond matures · {name} | a future gold maturity |
| Matures · {name} | a future fixed-income maturity |
| Loan ends · {name} | a future loan maturity |
| Lock-in ends · {name} | any other future maturity |
| Goal · {name} | a goal target date that is today or later. This row has no holding |

## Actions that are not rows

Read a statement, Add manually, See holdings, and Demat & folios appear only when the book is not empty. Their hints are “CDSL, CAMS, KFin”, “PPF, FD, Gold, etc.”, “Search & compare”, and “Where it sits”.
