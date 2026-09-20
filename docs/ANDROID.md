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
| `gud_support_annual` | Subscription, yearly | ₹299. Offer `with_licence` ₹250 first year |

Checkout in the app:

1. Lifetime only — ₹249
2. Lifetime + first year support — **₹499** (249 + 250). Not ₹599; that sum was a slip.
3. Support later (requires lifetime) — ₹299 / year

Play cannot attach a 30-day free trial to a one-time SKU. A subscription trial would auto-charge, which is not a perpetual licence. The app is free; a genuine Play install starts a 30-day trial; then the user buys `gud_lifetime` through Play Billing. Prices are on the intro, the paywall, and the store listing.

The lifetime product is perpetual. Support can lapse; the vault still opens.

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
