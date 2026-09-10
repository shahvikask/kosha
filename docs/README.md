# Kosha Engineering Documentation

This directory contains the architectural and engineering source of truth for Kosha.

## Baseline documents

- `KOSHA-ARCHITECTURE.md` — product and system architecture
- `KOSHA-DATA-MODEL.md` — canonical financial domain model
- `KOSHA-SECURITY.md` — privacy and security requirements
- `KOSHA-TESTING.md` — testing and release quality gates
- `KOSHA-CAS-SPEC.md` — CAS ingestion contract and parser requirements

These documents are intentionally technology-independent where possible so the domain can be reused by the web application and future Android application.

**Channels:** Android commercial = `main` (SQLite WASM). Windows personal = `GUD-Windows` (`%LOCALAPPDATA%\GrowUrDhan\vault.db`). See [RELEASE-CHANNELS.md](./RELEASE-CHANNELS.md) and [WINDOWS.md](./WINDOWS.md).

## Local Python CAS reader

CAS PDFs (CDSL, NSDL, CAMS, KFintech) are parsed on the user's machine. They never leave the device.

`npm run dev` starts Vite and a local Python sidecar on `127.0.0.1:8765`. The Documents tab posts the PDF to that sidecar, auto-detects the issuer, and shows a confirmation page (PDF total vs parsed total) before records are written.

```
pip install -r scripts/requirements-cas.txt
npm run dev
```

Same issuer overwrites the previous snapshot (January then February keeps February, it does not add them). The header shows: `The data is up to date until <statement period end>`.
