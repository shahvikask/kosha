# GrowUrDhan release channels

Never mix these.

| Branch | Audience | Store | Ship as |
|---|---|---|---|
| `main` | Product snapshot | Merge target from GIF | Source only |
| `Grok-Improvised-FrontEnd` | Daily web/UI | — | Merge into `main`, then Android / Windows |
| `GUD-Android-APK` | Device testing | Sideload APK only | Debug APK for Vikas's phone |
| `GUD-Android` | Commercial Android | Google Play AAB, API 36 | `in.growurdhan.app` |
| `GUD-Windows` | Personal Windows | NSIS `.exe` | `in.growurdhan.desktop` |

Rules:

1. All Android product work and phone testing happens on `GUD-Android-APK`.
2. After Vikas confirms the APK on a real phone, merge `GUD-Android-APK` into `GUD-Android`.
3. Only `GUD-Android` is uploaded to Google Play (AAB, never the test APK).
4. Windows `.exe` stays on `GUD-Windows`.
5. Do not compile Android from `GUD-Windows` or the Windows installer from an Android branch.
6. The test APK may open a 30-day trial when sideloaded. The Play AAB must not.
