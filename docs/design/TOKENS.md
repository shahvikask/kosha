# Tokens

Source values live in `android-native/core/model` as `BrandSourceTokens`. Semantic text and button roles live in `SemanticTokens`. Compose maps them in `GudTheme`.

## Contrast measured for M00

| Pair | Role | Result |
|---|---|---|
| `#1C1917` on `#F6EFE3`, `#FFFFFF` | Light body text | at least 4.5:1, tested |
| `#6B5E52` on `#F6EFE3` | Light muted text | at least 4.5:1, tested |
| `#F4F7FB` on `#07131F`, `#102536` | Dark body text | at least 4.5:1, tested |
| `#9BB3C7` on `#07131F` | Dark muted text | at least 4.5:1, tested |
| `#FFFFFF` on `#9A3412` | Light primary control | at least 4.5:1, tested |
| `#07131F` on `#F5C14A` | Dark primary control | at least 4.5:1, tested |
| `#D97706` on `#F6EFE3` | Rejected as small text | below 4.5:1, tested |

Spacing, radius, and 48dp controls follow Section 6A.4 for the three concept screens only. A full component library is not built.

## Not done

Chart series colors, 200% font screenshots, tablet rails, and the remaining `GUD*` components listed in Section 6A.4 are not implemented.
