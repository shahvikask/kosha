# GrowUrDhan documentation

> **GrowUrDhan first market release — owner decision D57 (2026-10-26):** Financial vault backup/export, automated SAF backup, backup-file import and financial-data recovery/restore are **not available in the initial release**. They belong to the next upgrade. This note supersedes any older proposed screens, flows, requirements or tests in this document; those descriptions are retained only as legacy/future-upgrade references. Offline account-password recovery and standard CAS/CSV/PDF statement imports remain in scope. No backup or recovery must be advertised as active. See [base-release scope](implementation/BASE-RELEASE-SCOPE.md) and spec v1.3.

Engineering notes for the **GrowUrDhan Android app**.

## Ship and store

- `ANDROID.md` — Play AAB, SKUs, encryption, review prompt
- `ANDROID-APK.md` — sideload test APK on this branch only
- `PLAY-CONSOLE.md` — listing, Data safety, billing setup
- `RELEASE-CHANNELS.md` — `GUD-Android-APK` vs `GUD-Android` vs `GUD-Windows`

## Product domain

- `KOSHA-ARCHITECTURE.md` — system shape (on-device vault, no cloud ledger)
- `KOSHA-DATA-MODEL.md` — holdings, lots, family, documents
- `KOSHA-SECURITY.md` — encryption and privacy requirements
- `KOSHA-TESTING.md` — quality gates
- `KOSHA-CAS-SPEC.md` — CAS / statement import contract

CAS PDFs are parsed on the device or on the developer machine. They are not uploaded to GrowUrDhan servers.
