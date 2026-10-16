# Navigation screen map

M00 maps every inventory surface to a native destination in `M00Inventory.routes`. Only Unlock, Home, and Holding detail are implemented, plus the lockup drawn on those screens.

The five-tab shell (Home, Portfolio, Statements, Insights, Family) is not drawn. Destinations without a screen are not shown as buttons. That avoids dummy navigation. It is not a decision to drop those destinations.

| Legacy | Native destination | In this build |
|---|---|---|
| Auth | `unlock` | Yes. It does not open a vault. |
| Home | `home` | Yes. One labeled sample amount. |
| Holding detail | `portfolio/holding` | Yes. Unknown cost, no invented purchase. |
| Holdings, Accounts, Documents, Family, and the other inventory surfaces | see `M00Inventory.routes` | No |

Package id of this scaffold is `in.growurdhan.redevelopment`, not the shipping id `in.growurdhan.app`.
