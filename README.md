# GrowUrDhan (Android)

Local-first family wealth tracker for Indian households.
Application id: `in.growurdhan.app`.

This branch (`GUD-Android-APK`) is the **sideload test APK**. Production Play AAB lives on `GUD-Android`. Windows stays on `GUD-Windows`.

## What this repo is

- React + Vite UI in `src/`
- Tauri 2 Android shell in `src-tauri/`
- Play Billing, Integrity, encrypted vault, in-app review in `GrowUrDhanPlugin.kt`
- Play listing, privacy, terms, and account-deletion pages in `marketing/`

## Licence (two products only)

1. Lifetime — ₹249 (required after the 30-day trial)
2. Annual support — ₹299 / year (optional; lifetime first)

No bundle. No per-member seats. Family members are unlimited under the lifetime licence.

## Docs that belong on this branch

| File | Purpose |
|---|---|
| [docs/ANDROID-APK.md](docs/ANDROID-APK.md) | How to sideload the test APK |
| [docs/ANDROID.md](docs/ANDROID.md) | Play / AAB identity, SKUs, encryption |
| [docs/PLAY-CONSOLE.md](docs/PLAY-CONSOLE.md) | Store listing and billing checklist |
| [docs/RELEASE-CHANNELS.md](docs/RELEASE-CHANNELS.md) | Which branch ships what |
| [docs/README.md](docs/README.md) | Engineering notes index |

## Build

```bash
npm install
npm test
npm run android:prepare
npm run android:apk    # this branch
# on GUD-Android: npm run android:aab
```

Do not upload this branch's APK to Google Play.
