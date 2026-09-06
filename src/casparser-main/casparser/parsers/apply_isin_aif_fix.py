#!/usr/bin/env python3
"""Apply ISIN-clean + AIF-type tagging to cdsl.py (run from parsers/ dir)."""
from pathlib import Path
path = Path(__file__).with_name("cdsl.py")
text = path.read_text(encoding="utf-8")
if "_clean_isin_text" in text:
    print("already patched")
    raise SystemExit(0)

helpers = """
def _clean_isin_text(text: str) -> str:
    \"\"\"Strip control chars / newlines that CDSL sometimes embeds mid-ISIN.

    Observed on Edelweiss Multi Cap (INF754K01RW0): extractor yields
    ``INF754K01R\\x02\\nW0``, which fails ``ISIN_RE`` and drops the whole
    valuation row (Rs 20L+ gap vs summary).
    \"\"\"
    if not text:
        return ""
    return re.sub(r"[\\x00-\\x1f\\s]", "", text).strip()


def _infer_mf_type(name: Optional[str], isin: str) -> Optional[str]:
    \"\"\"Tag AIF / ETF holdings explicitly for downstream consumers.\"\"\"
    n = (name or "").upper()
    if any(
        k in n
        for k in (
            "AIF",
            "ALTERNATIVE INVESTMENT",
            "INDIA DISCOVERY FUND",
            "CATEGORY III",
            "CAT III",
        )
    ):
        return "AIF"
    if "ETF" in n or "BEES" in n:
        return "ETF"
    return None

"""

anchor = "def _looks_numeric(text: str) -> bool:\n    s = text.strip()\n    if not s:\n        return False\n    return bool(NUMERIC_RE.match(s))\n\n\ndef _rel_close"
if anchor not in text:
    raise SystemExit("anchor not found - unexpected cdsl.py")
text = text.replace(
    anchor,
    "def _looks_numeric(text: str) -> bool:\n    s = text.strip()\n    if not s:\n        return False\n    return bool(NUMERIC_RE.match(s))\n\n" + helpers + "\ndef _rel_close",
)

old_isin = """    for i in range(min(3, len(block.cells))):
        s = block.cells[i].text.strip()
        if ISIN_RE.match(s):
            isin_idx = i
            break
    if isin_idx is None:
        return None
    isin = block.cells[isin_idx].text.strip()
"""
new_isin = """    for i in range(min(3, len(block.cells))):
        s = _clean_isin_text(block.cells[i].text)
        if ISIN_RE.match(s):
            isin_idx = i
            break
    if isin_idx is None:
        return None
    isin = _clean_isin_text(block.cells[isin_idx].text)
"""
if old_isin not in text:
    raise SystemExit("isin block not found")
text = text.replace(old_isin, new_isin)

old_h = """    first = block.cells[0].text.strip()
    if not ISIN_RE.match(first):
        return None
    isin = first
"""
new_h = """    first = _clean_isin_text(block.cells[0].text)
    if not ISIN_RE.match(first):
        return None
    isin = first
"""
if old_h in text:
    text = text.replace(old_h, new_h)

old_mf = """    return MutualFund(
        name=name,
        isin=isin,
        balance=balance,
        nav=nav,
        value=value,
        total_cost=invested,
        ucc=ucc,
        folio=folio,
        pnl=pnl,
        **{"return": ret},
    )
"""
new_mf = """    return MutualFund(
        name=name,
        isin=isin,
        type=_infer_mf_type(name, isin),
        balance=balance,
        nav=nav,
        value=value,
        total_cost=invested,
        ucc=ucc,
        folio=folio,
        pnl=pnl,
        **{"return": ret},
    )
"""
if old_mf not in text:
    raise SystemExit("MutualFund return not found")
text = text.replace(old_mf, new_mf)

old_inf = """                    cur_account.mutual_funds.append(
                        MutualFund(
                            name=name,
                            isin=isin,
                            balance=num_shares,
                            nav=price,
                            value=value,
                        )
                    )
"""
new_inf = """                    cur_account.mutual_funds.append(
                        MutualFund(
                            name=name,
                            isin=isin,
                            type=_infer_mf_type(name, isin),
                            balance=num_shares,
                            nav=price,
                            value=value,
                        )
                    )
"""
if old_inf in text:
    text = text.replace(old_inf, new_inf)

path.write_text(text, encoding="utf-8")
print(f"patched {path} ({len(text)} bytes)")
