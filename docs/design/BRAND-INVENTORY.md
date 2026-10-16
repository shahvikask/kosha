# Brand inventory

Observed from `GUD-Android-Gmail` at `af96dcc8967ab595e1d9acee78e29e500cb99a9e`. The original files were not edited.

| Asset | Path | SHA-256 of bytes | Git blob |
|---|---|---|---|
| Mark | `public/brand/mark.svg` | `d612b12009911a06796e2fb2c75fa92247db87151a6344ae850acf04af823efc` | `52fd0aa1a9262e6d2ba8ab431c2955c6df5c417c` |
| Mark PNG | `public/brand/mark.png` | recorded in `legacy-parser-sha256.txt` | not restated here |
| Logo PNG | `public/brand/logo.png` | recorded in `legacy-parser-sha256.txt` | not restated here |
| Full logo | `public/brand/logo-full.png` | recorded in `legacy-parser-sha256.txt` | not restated here |

The mark is a 100 by 100 viewBox, navy tile `#07131F`, corner radius 22, gold stroke and rupee `#F5C14A`, teal tip `#2DD4BF`. Wordmark order is Grow, gold-underlined Ur, teal Dhan. Tagline is “Private Indian Family Wealth & Records”.

Copies, not replacements:

- `android-native/core/designsystem/src/main/assets/mark.svg` is a byte copy of the original SVG.
- `android-native/app/src/main/res/drawable-nodpi/ic_gud_mark.png` is a byte copy of `mark.png` for the launcher icon.
- `GUDBrandMark` redraws the same viewBox numbers in Compose. It is not a new symbol.

Owner visual approval is not granted by this file.
