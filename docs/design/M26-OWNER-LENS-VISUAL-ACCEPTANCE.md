# M26 owner-lens visual and accessibility acceptance — first market release

**2026-10-26 status: SPECIFIED, NOT CAPTURED, NOT OWNER ACCEPTED.** GrowUrDhan specification v1.3 Section 6A and rules G06/G14 govern. This contract applies to the real `NativeBooksScreen`, not the three M00 synthetic `ConceptScreens`. Financial vault backup/recovery is **deferred D57** and must never be introduced into these screenshots.

## Target states (all synthetic owner/account/amount examples)
1. Private encrypted books unlocked after actual device credential + app-password admission; no visible private records when locked/backgrounded.
2. Whole household: Home counts, holdings, Family records, custody/nominees, goals, Records, Documents/statement import, Settings. A member-lens picker clearly communicates that it controls which financial items are visible, not identity/authentication.
3. Selected individual: only that member's holdings, custody/nominee, records and member-specific goal edits; household goals may be read-only; new goals inherit selected member. Member creation requires Whole household. Never allow a pending event/price/holding/goal editor from another member to survive switching lenses.
4. Documents in member lens: block access to whole-household CAS/CSV import and import batch/source-document history; explain the explicit Whole household switch. In Whole household show protected statement picker and source provenance as permitted after admission. D57 encrypted **financial backup picker** must not exist.
5. Stale view/lock: previously loaded audit history, XIRR or old source must not flash when the member lens, lifecycle admission or vault changes. Disabled actions explain unavailable prerequisites without fabricated success.
6. Unknown cost, empty household/zero holdings, HUF/guardian/minor constraints, long names and large INR amounts; no guessed prices/costs and no accidental cross-member content.

## Responsive and accessibility matrix
- **Phone viewport:** 360dp compact and 412dp typical, portrait, plus tablet for M34. Native Compose Material 3 only; brand logo matches official archived brand without distortion. Test light and dark; dynamic colors only if separately approved.
- **Font:** 1.0 and 2.0 font scale. Full label and error visibility, 48dp touch targets, no inaccessible navigation controls, no clipped amounts/totals/owner names, no accidental horizontal scrolling of financial data or ambiguous row ownership.
- **TalkBack:** announced labels, chosen member and picker state, group/heading order, selected tab, disabled state and error text. Every member-lens change must communicate changed scope; contrast meets Section 6A (4.5:1 normal text, 3:1 large text/nontext meaningful boundaries).
- **Semantics:** `choice-Member lens`, `choice-Account owner`, `choice-Goal owner` (household scope only), `choice-Holding` have honest current state. Under member lens, edits cannot point to excluded persons or custody/holding references. Secret PAN/account values are masked by default; `FLAG_SECURE` prevents OS screenshots of real authenticated private screens.
- **Screenshots:** all public CI screenshots must use *fictitious*, deterministic data only. Never turn off `FLAG_SECURE` for actual private-data activities merely to capture screenshots. Capture safe synthetic/isolated composables or local owner-reviewed protected-device screenshots with values fully redacted, and label which method is used. No private customer documents, balances, PAN, DOB, emails, account identifiers or authorization codes in public artifacts.

## Evidence and acceptance
- Record source SHA, screenshots with device API/density/font scale/theme, matched screen state, screenshots vs official brand/logo reference, Compose semantics/interaction results, colors/contrast findings, manual TalkBack keyboard and touch exploration, reviewer and dates.
- Automated unit/Compose instrumentation and screenshot evidence are necessary but not sufficient for G14; owner visual sign-off and independent physical device/AppSec review are separate.
- **Mandatory open work:** 360dp/200% font/touch exploration on actual M26 forms and navigation; dark/light screenshot baselines; actual member-lens selected-state announcements and contrast measurement; tested on one exact source SHA. Do not mark M26 or E01/E02/E03 visually accepted from source-only controls.
