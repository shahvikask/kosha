# Device-test APK (`GUD-Android-APK`)

This branch is for installing GrowUrDhan on a real phone **before** Play production.

## What this file is

- A **debug APK** you sideload (Unknown sources / Install unknown apps).
- Same vault UI as production: intro, signup, CAS, family, delete account.
- 30-day trial works **without** Google Play, so you can test.
- Buy buttons on this build use a local test licence, not a real charge.
- Vault is still encrypted on the phone.

## What this file is not

- Not the Play Store package.
- Not an AAB.
- Do not upload this APK to Play.

## Install on your phone

1. Open the GitHub Action **GrowUrDhan Android APK (device test)** on branch `GUD-Android-APK`.
2. Download the artifact `GrowUrDhan-Android-APK`.
3. Copy the `.apk` to the phone (Drive, Telegram to yourself, USB).
4. On the phone: Settings → allow install from that app → open the APK → Install.
5. Open GrowUrDhan, tap through the bilingual intro, create a vault.

If Android blocks the install, it is asking you to allow “Install unknown apps” for Chrome/Files/Drive — that is expected for a test APK.

## After you confirm

Say that the phone build is good. Then we merge `GUD-Android-APK` → `GUD-Android` and ship an AAB to Play. The Play build will again refuse a copied APK.
