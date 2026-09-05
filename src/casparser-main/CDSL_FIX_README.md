# CDSL Parser Fix (Grok)

## Problem
CDSL CAS statements with wrapped broker names (e.g. MOTILAL OSWAL) produced 5-cell summary rows that were skipped. Motilal holdings (equity + AIF) and RAISE account were missing. Portfolio totals were under-reported by ~Rs 80 lakh.

## Apply the fix

```bash
cd src/casparser-main
patch -p2 < casparser/parsers/cdsl_fix.patch
```

If `cdsl.py` was corrupted to `SEE_FILE`, restore from git first:
```bash
git checkout HEAD~3 -- src/casparser-main/casparser/parsers/cdsl.py
patch -p2 < casparser/parsers/cdsl_fix.patch
```
(Adjust HEAD~N to the commit before the accidental placeholder push.)

## Verification (JUN + JUL 2026 CDSL CAS)
After fix, portfolio totals match PDF exactly:
- June: Rs 3,03,64,813.26
- July: Rs 3,09,32,314.00
Including Motilal Oswal equity (Reliance) + India Discovery Fund II AIF (Rs 42.5L).
