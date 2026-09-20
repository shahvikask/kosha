# GrowUrDhan release channels

Never mix these.

| Branch | Audience | Store | Ship as |
|---|---|---|---|
| `main` | Product snapshot for Android | Merge target from GIF | Source only |
| `Grok-Improvised-FrontEnd` | Daily web/UI | — | Merge into `main`, `GUD-Android`, `GUD-Windows` |
| `GUD-Android` | Commercial Android | Google Play AAB, API 36 | `in.growurdhan.app` |
| `GUD-Windows` | Personal Windows | NSIS `.exe` | `in.growurdhan.desktop` |

Rules:

1. Android commercial work happens on `GUD-Android`.
2. Windows `.exe` happens on `GUD-Windows`.
3. Do not compile Android from `GUD-Windows` or the Windows installer from `GUD-Android`.
