# Navigation screen map

> **GrowUrDhan first market release — owner decision D57 (2026-10-26):** Financial vault backup/export, automated SAF backup, backup-file import and financial-data recovery/restore are **not available in the initial release**. They belong to the next upgrade. This note supersedes any older proposed screens, flows, requirements or tests in this document; those descriptions are retained only as legacy/future-upgrade references. Offline account-password recovery and standard CAS/CSV/PDF statement imports remain in scope. No backup or recovery must be advertised as active. See [base-release scope](../implementation/BASE-RELEASE-SCOPE.md) and spec v1.3.

M00 maps every inventory surface to a native destination in `M00Inventory.routes`. Only Unlock, Home, and Holding detail are implemented, plus the lockup drawn on those screens.

The five-tab shell (Home, Portfolio, Statements, Insights, Family) is not drawn. Destinations without a screen are not shown as buttons. That avoids dummy navigation. It is not a decision to drop those destinations.

| Legacy | Native destination | In this build |
|---|---|---|
| Auth | `unlock` | Yes. It does not open a vault. |
| Home | `home` | Yes. One labeled sample amount. |
| Holding detail | `portfolio/holding` | Yes. Unknown cost, no invented purchase. |
| Holdings, Accounts, Documents, Family, and the other inventory surfaces | see `M00Inventory.routes` | No |

Package id of this scaffold is `in.growurdhan.redevelopment`, not the shipping id `in.growurdhan.app`.
