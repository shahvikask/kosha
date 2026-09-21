# GrowUrDhan Android (`GUD-Android`)

Commercial phone app. Windows stays on `GUD-Windows`. Daily UI work stays on `Grok-Improvised-FrontEnd`, then merge into this branch.

## What ships

- Android App Bundle (`.aab`) for Google Play — never a store APK
- `targetSdk` / `compileSdk` **36**
- Application id `in.growurdhan.app`
- Play App Signing (upload key in CI secrets; Google holds the app signing key)
- 30-day trial, then Play Billing only

## Licence SKUs (create in Play Console)

| Product id | Type | Price |
|---|---|---|
| `gud_lifetime` | One-time | ₹249 |
| `gud_support_annual` | Subscription, yearly | ₹299. Base plan `standalone` only |

Checkout in the app — **only two options, in this order**:

1. Lifetime — ₹249 (required after the 30-day trial). Does **not** include support or enhancements.
2. Annual support — ₹299 / year (optional; disabled until lifetime is owned).

There is no bundle and no per-member seat. Family members stay unlimited inside the ₹249 licence.

Play cannot attach a 30-day free trial to a one-time SKU. A subscription trial would auto-charge, which is not a perpetual licence. The app is free; a genuine Play install starts a 30-day trial; then the user buys `gud_lifetime` through Play Billing. Prices are on the intro, the paywall, and the store listing.

The lifetime product is perpetual. Support can lapse; the vault still opens. After cancel, support continues until that Play period ends.

Purchases restore on a new phone that uses the **same Google Play account**. A sideloaded APK does not carry the licence.

## Encryption

- WebView snapshot is AES-GCM (`GUDENC01`)
- On the phone the file is `EncryptedFile` + Android Keystore
- `allowBackup=false` and data-extraction rules exclude the vault
- No exported `ContentProvider`

Rooted spyware can still photograph the screen while unlocked. That is out of scope. At rest, other apps do not get a readable database.

## CI

- `android-aab.yml` — AAB artifact; draft upload to Play internal if `PLAY_SERVICE_ACCOUNT_JSON` is set
- `android-enhancements.yml` — notes for support subscribers. Play cannot ship a different binary per subscription; enhancements are gated in the same AAB
- `marketing.yml` — privacy, deletion, listing copy to GitHub Pages

### Secrets to add

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON`

First AAB upload to Play must be manual so App Signing can enrol.

## Reviewer login (Play Console → App access)

The vault is on-device. Reviewers create one with:

- Email: `play.review@growurdhan.in`
- Password: `PlayReview#2026`
- PAN: `AAAPG1234A`
- DOB: `1985-01-15`
- Questions: first pet `Lotus`, city of birth `Pune`

Trial is 30 days, so review does not need a purchase. Add licence testers in Play Console for purchase SKUs.

## Support vs everyone

Security patches go to every licensed install (Play policy and user safety). Annual support unlocks enhancement flags in the app (`enhancementsEnabled`).

## Play reviews

After 7 days from first open, an unlocked vault may show a rating sheet. Submit opens Google Play In-App Review so the rating posts to the Play listing. Close / Not now snoozes 2 days. Family → Rate on Google Play is always available and stops the auto-prompt.

Play may hide the in-app sheet (quota, already reviewed). The app then opens the store listing. Sideloaded test APKs skip the in-app sheet and open the listing. GrowUrDhan does not collect the rating.
