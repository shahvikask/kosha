# Play Console checklist (GrowUrDhan)

Complete these in the console. The repo cannot tick them for you.

## Technical

- [ ] Create app `in.growurdhan.app`
- [ ] Enrol **Play App Signing** (first AAB upload — Google holds the app signing key; CI holds only the upload keystore)
- [ ] Upload AAB from Actions artifact `GrowUrDhan-Android-AAB` (not an APK)
- [ ] Confirm target API 36 on the bundle details page
- [ ] Closed testing with testers if the developer account is new (Play requires ~14 days of closed testing before production)

## Store

- [ ] Paste `marketing/play-listing-en.txt` and `play-listing-hi.txt`
- [ ] Feature graphic 1024×500 + phone screenshots (at least 2)
- [ ] Privacy policy URL: `https://shahvikask.github.io/kosha/privacy.html`
- [ ] Terms URL (optional extra): `https://shahvikask.github.io/kosha/terms.html`
- [ ] Category: Finance. Ads: **No**

## App content

- [ ] Data safety from `marketing/data-safety.yml` — no ads; financial info stays on device; Play Billing is Google’s collection
- [ ] Target audience: **18+ only**. Not Designed for Families
- [ ] Content rating questionnaire (finance, no violence, no user-generated public chat)
- [ ] App access: reviewer credentials in `docs/ANDROID.md`
- [ ] Account deletion URL: `https://shahvikask.github.io/kosha/delete-account.html`
- [ ] Sensitive permissions: none expected (no SMS, contacts, location). If the CAS file picker needs photos/files, declare “files and docs the user picks”

## Products

- [ ] In-app product `gud_lifetime` — one-time, ₹249, licence testers enabled
- [ ] Subscription `gud_support_annual` — ₹299 / year
  - Base plan `standalone` (the only offer the app buys)
  - Leave any leftover intro offer `with_licence` (₹250) **unpublished** — the app no longer sells a first-year bundle
- [ ] Licence testers for `play.review@growurdhan.in`

## Trial (read this)

Play does **not** attach a 30-day free trial to a one-time product. A subscription trial would auto-charge after 30 days, which contradicts a perpetual ₹249 licence.

What we ship instead (Payments-policy compliant):

1. The app is **free** on the store.
2. A genuine Play install starts a 30-day trial bound to that device’s Keystore. Prices are on the last intro slide, the licence screen, and the store listing.
3. After 30 days the vault stays on the phone but the UI asks for `gud_lifetime` through **Google Play Billing only**. No Stripe, UPI, or “pay on our website”.
4. Buying `gud_lifetime` is perpetual and restores on any phone signed into the same Play account (`queryPurchasesAsync`). A copied APK fails the Play installer check and stays locked.

## Payments policy

- [ ] No Stripe, UPI deep links, or “pay on our website” inside the app
- [ ] Trial and prices stated on onboarding slide 6 and on the licence screen
- [ ] Support subscription can be cancelled in Play; the lifetime licence remains
