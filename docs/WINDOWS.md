# GrowUrDhan for Windows (personal / family)

Desktop channel (`GUD-Windows`). Commercial Android stays on `main` with SQLite WASM.

| What | Where |
|---|---|
| Program (all Windows users) | `%ProgramFiles%\GrowUrDhan\` (needs admin to install) |
| Family vault (this Windows login) | `%LOCALAPPDATA%\GrowUrDhan\vault.db` — **native SQLite**, not IndexedDB |
| CAS engine | Child process next to the exe. Not a Windows Service |

Uninstall removes Program Files. It does **not** delete `vault.db`.

The vault is per Windows user on purpose: a guest account on the laptop does not see your books. Every profile can *launch* the app; they each get their own vault unless they log in as you.

## Installer

Uninstall the previous (AppData) copy first, then run the new setup as administrator.

Push to `GUD-Windows` builds `GrowUrDhan_*_x64-setup.exe` in Actions.
