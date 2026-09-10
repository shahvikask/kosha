# GrowUrDhan for Windows (personal / family)

This is the **desktop** channel (`GUD-Windows`). Commercial Android stays on `main` with SQLite WASM.

Vault file: `%LOCALAPPDATA%\GrowUrDhan\vault.db`  
Uninstall does **not** delete that file.

The CAS reader is bundled as `gud-engine.exe` and started with the app. It is a child process, not a Windows Service.

## Installer (CI)

Push to `GUD-Windows` (or run the workflow by hand). GitHub Actions builds `GrowUrDhan_0.1.0_x64-setup.exe`.

## Build on your PC

Needs: Node 22, Rust stable, Python 3.12, WebView2 (already on Windows 11).

```
git checkout GUD-Windows
git pull
npm install
pip install pyinstaller -r scripts/requirements-cas.txt
pip install -e src/casparser-main
python scripts/pack_engine.py
npm run desktop:build
```

Installer: `src-tauri\target\release\bundle\nsis\GrowUrDhan_0.1.0_x64-setup.exe`
