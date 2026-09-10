# GrowUrDhan release channels

Never mix these. Future work follows this split.

| Branch | Audience | Store | Ship as |
|---|---|---|---|
| `main` | Commercial **Android** | Official **SQLite WASM** (browser / WebView) | Android build |
| `Grok-Improvised-FrontEnd` | Daily desktop/web development | WASM + IndexedDB (same as main) | Merge into `main` (Android) **and** into `GUD-Windows` (desktop) |
| `GUD-Windows` | Personal + close relatives, **Windows desktop** | `%LOCALAPPDATA%\GrowUrDhan\vault.db` | `.exe` installer |

Rules:

1. **Desktop (personal / family)** = Windows `.exe` on `GUD-Windows`. Vault file is `%LOCALAPPDATA%\GrowUrDhan\vault.db`. Not a Windows Service.
2. **Commercial** = most stable Android build from `main`. Keep official SQLite WASM. Do not replace it with the Windows file driver.
3. When the user says **compile the .exe package**: merge current work from `main` or `Grok-Improvised-FrontEnd` into `GUD-Windows`, then build the NSIS installer. Do not compile Android from `GUD-Windows`. Do not put the AppData file driver on `main`.
4. Casparser engine stays locked on every branch.
