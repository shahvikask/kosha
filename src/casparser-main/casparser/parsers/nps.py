"""Standalone NPS CRA statement parser (Protean / CAMS / KFin CRA).

This is **not** the NPS *section* inside a CDSL CAS (that lives in
`cdsl._parse_nps`). These PDFs are the monthly "NPS TRANSACTION
STATEMENT" emailed by the CRA — Protean samples look like:

    NPS TRANSACTION STATEMENT
    Aug 01, 2026 To Aug 31, 2026
    PRAN 110123981321
    Subscriber Name VIKAS KUNJBIHARI SHAH
    ...
    Investment Details - Scheme Wise Summary
        <scheme columns>
        value  (E = U * N)
        units  (U)
        NAV    (N)

Holdings snapshot only. The contribution / transaction ledger is
intentionally not parsed as buys (same policy as CDSL's NPS section).
The printed "Total Contribution" (B) is stored on ``NPSAccount.contribution``
and used as invested cost — split across schemes by current value.

Password convention used by Protean CRA (and documented for the
Kosha UI): first four letters of the subscriber's first name,
lowercase, plus DDMM of date of birth. Example: Vikas born 22 Mar
→ ``vika2203``.
"""

from __future__ import annotations

import re
from decimal import Decimal, InvalidOperation
from typing import Dict, List, Optional, Sequence, Tuple

from casparser.enums import FileType
from casparser.exceptions import CASParseError
from casparser.types import (
    InvestorInfo,
    NPSAccount,
    NPSScheme,
    NSDLCASData,
    StatementPeriod,
)

from . import pageobj
from .pageobj import Atom

# --- regexes ----------------------------------------------------------------

_PERIOD_RE = re.compile(
    r"([A-Z][a-z]{2})\s+(\d{1,2}),\s+(\d{4})\s+To\s+([A-Z][a-z]{2})\s+(\d{1,2}),\s+(\d{4})",
    re.I,
)
_MONTHS = {
    "jan": "01",
    "feb": "02",
    "mar": "03",
    "apr": "04",
    "may": "05",
    "jun": "06",
    "jul": "07",
    "aug": "08",
    "sep": "09",
    "oct": "10",
    "nov": "11",
    "dec": "12",
}
_PRAN_RE = re.compile(r"\b(\d{12})\b")
_TIER_RE = re.compile(r"\bTIER\s*[-\s]*(I{1,2}|1|2)\b", re.I)
_ASSET_RE = re.compile(r"\bSCHEME\s+([ECGA])\b", re.I)
_PCT_RE = re.compile(r"^(\d+(?:\.\d+)?)\s*%$")
_NUM_RE = re.compile(r"^[+-]?\d{1,3}(?:,\d{2,3})*(?:\.\d+)?$|^[+-]?\d+\.\d+$")

_FUND_MANAGERS = (
    "ICICI PRUDENTIAL",
    "ADITYA BIRLA",
    "MAX LIFE",
    "KOTAK",
    "HDFC",
    "ICICI",
    "LIC",
    "UTI",
    "SBI",
    "TATA",
    "AXIS",
)

# Left of this x is the Particulars/References label column of the
# scheme-wise table; scheme columns sit to the right.
_TABLE_LABEL_X = 175.0

# Minimum x-gap (points) between two scheme-name column anchors.
_COL_GAP_MIN = 25.0


# --- tiny helpers -----------------------------------------------------------


def _to_decimal(text) -> Decimal:
    if text is None:
        return Decimal(0)
    s = str(text).replace(",", "").replace("%", "").strip()
    if not s or s in ("-", "--", "N.A", "NA"):
        return Decimal(0)
    try:
        return Decimal(s)
    except InvalidOperation:
        return Decimal(0)


def _looks_numeric(text: str) -> bool:
    t = (text or "").strip().replace(",", "")
    if not t or t.endswith("%"):
        return False
    return bool(_NUM_RE.match(t.replace("+", "")))


def _clean_ws(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").replace("\n", " ")).strip()


def _clean_scheme_name(s: str) -> str:
    s = _clean_ws(s)
    # Some CRA templates clip the last letter of DIRECT on a wrapped line.
    if re.search(r"\bDIREC$", s, re.I):
        s += "T"
    return s


def _norm_tier(t: Optional[str]) -> Optional[str]:
    if not t:
        return None
    return {"1": "I", "2": "II"}.get(t.upper(), t.upper())


def _asset_class(name: str) -> Optional[str]:
    if m := _ASSET_RE.search(name):
        return m.group(1).upper()
    if "VATSALYA" in name.upper():
        return "VATSALYA"
    return None


def _fund_manager(name: str) -> Optional[str]:
    up = name.upper()
    for token in _FUND_MANAGERS:
        if token in up:
            return token.title() if token not in ("ICICI", "HDFC", "LIC", "UTI", "SBI") else token
    return None


def _center(a: Atom) -> float:
    return (a.x_left + a.x_right) / 2.0


def nps_statement_password(name: str, date_of_birth: str) -> str:
    """CRA NPS PDF password: first 4 letters of first name (lowercase) + DDMM.

    ``date_of_birth`` accepts ``YYYY-MM-DD`` or ``DD-MM-YYYY`` / ``DD/MM/YYYY``.
    Raises ``ValueError`` if either piece is missing.
    """
    first = (name or "").strip().split()[0] if (name or "").strip() else ""
    letters = "".join(ch for ch in first if ch.isalpha())[:4].lower()
    if not letters:
        raise ValueError("name has no letters")
    dob = (date_of_birth or "").strip()
    ddmm = None
    if m := re.match(r"^(\d{4})-(\d{2})-(\d{2})", dob):
        ddmm = m.group(3) + m.group(2)
    elif m := re.match(r"^(\d{2})[/-](\d{2})[/-](\d{4})", dob):
        ddmm = m.group(1) + m.group(2)
    if not ddmm:
        raise ValueError("date of birth must be YYYY-MM-DD or DD-MM-YYYY")
    return letters + ddmm


# --- header / summary -------------------------------------------------------


def _page_atoms(pages: Sequence[Sequence[Atom]], page: int) -> List[Atom]:
    """1-indexed page → atom list (empty if out of range)."""
    i = page - 1
    if i < 0 or i >= len(pages):
        return []
    return list(pages[i])


def _joined_text(pages: Sequence[Sequence[Atom]], max_pages: int = 4) -> str:
    parts = []
    for i, page in enumerate(pages):
        if i >= max_pages:
            break
        parts.append(" ".join(a.text for a in page))
    return "\n".join(parts)


def _value_right_of(atoms: Sequence[Atom], label: str, x_max: float = 300.0) -> Optional[str]:
    """Return the text sitting to the right of ``label`` on the same line."""
    needle = label.lower()
    for a in atoms:
        if a.text.strip().lower().rstrip(" :") == needle or a.text.strip().lower() == needle:
            same = [
                b
                for b in atoms
                if abs(b.y_top - a.y_top) <= 3.0
                and b.x_left > a.x_right - 2
                and b.x_left < x_max
                and b.text.strip()
            ]
            same.sort(key=lambda b: b.x_left)
            if same:
                return _clean_ws(" ".join(b.text for b in same))
    return None


def _parse_period(atoms: Sequence[Atom]) -> Optional[StatementPeriod]:
    blob = " ".join(a.text for a in atoms)
    m = _PERIOD_RE.search(blob)
    if not m:
        return None
    m1, d1, y1, m2, d2, y2 = m.groups()
    k1, k2 = m1[:3].lower(), m2[:3].lower()
    if k1 not in _MONTHS or k2 not in _MONTHS:
        return None
    return StatementPeriod(
        **{
            "from": f"{y1}-{_MONTHS[k1]}-{int(d1):02d}",
            "to": f"{y2}-{_MONTHS[k2]}-{int(d2):02d}",
        }
    )


def _parse_pran(atoms: Sequence[Atom]) -> Optional[str]:
    # Prefer the value sitting next to the PRAN label so we don't pick a
    # 12-digit POP registration number.
    labelled = _value_right_of(atoms, "PRAN", x_max=280.0)
    if labelled:
        m = _PRAN_RE.search(labelled.replace(" ", ""))
        if m:
            return m.group(1)
    for a in atoms:
        if _PRAN_RE.fullmatch(a.text.strip()):
            return a.text.strip()
    blob = " ".join(a.text for a in atoms)
    m = re.search(r"PRAN\s+(\d{12})", blob, re.I)
    return m.group(1) if m else None


def _parse_subscriber_name(atoms: Sequence[Atom]) -> str:
    """Subscriber Name plus wrapped continuation lines in the left column."""
    label = next(
        (a for a in atoms if a.text.strip().lower().startswith("subscriber name")),
        None,
    )
    if label is None:
        return ""
    # Same-line value (stops before the right-hand status column ~ x=300).
    same = [
        b
        for b in atoms
        if abs(b.y_top - label.y_top) <= 3.0
        and b.x_left > label.x_right - 2
        and b.x_left < 300
        and b.text.strip()
    ]
    same.sort(key=lambda b: b.x_left)
    parts = [b.text.strip() for b in same]
    # Wrapped lines hang under the value x (~140) until Address / next label.
    addr = next((a for a in atoms if a.text.strip().lower() == "address"), None)
    y_floor = addr.y_top + 1 if addr is not None else label.y_top - 40
    cont = [
        b
        for b in atoms
        if y_floor < b.y_top < label.y_top - 2
        and 80 < b.x_left < 280
        and b.text.strip()
        and not b.text.strip().lower().startswith("tier")
    ]
    cont.sort(key=lambda b: -b.y_top)
    parts.extend(b.text.strip() for b in cont)
    name = _clean_ws(" ".join(parts))
    # Drop guardian parenthetical for the canonical investor name; keep the
    # child's name which is what holdings should attach to.
    name = re.split(r"\s*\(\s*Under\s+Guardian", name, maxsplit=1, flags=re.I)[0]
    return name.strip(" )")


def _parse_address(atoms: Sequence[Atom]) -> str:
    label = next((a for a in atoms if a.text.strip().lower() == "address"), None)
    if label is None:
        return ""
    stop_labels = {"mobile number", "email id", "ira status", "pop-sp reg no"}
    lines = []
    for a in sorted(atoms, key=lambda x: -x.y_top):
        if a.y_top > label.y_top + 1:
            continue
        if a.y_top < label.y_top - 140:
            break
        t = a.text.strip()
        if t.lower() in stop_labels:
            if a.x_left < 80:
                break
            continue
        if 80 < a.x_left < 280 and t and not t.lower().startswith("tier"):
            lines.append(t)
    # Keep order top→bottom as collected (we iterated top-down).
    # Dedupe adjacent repeats.
    out: List[str] = []
    for ln in lines:
        if not out or out[-1] != ln:
            out.append(ln)
    return _clean_ws(", ".join(out))


def _parse_tier_from_header(atoms: Sequence[Atom]) -> Optional[str]:
    blob = " ".join(a.text for a in atoms)
    m = re.search(r"NPS Transaction Statement for Tier\s*(I{1,2}|1|2)", blob, re.I)
    if m:
        return _norm_tier(m.group(1))
    return None


def _detect_cra(pages: Sequence[Sequence[Atom]]) -> str:
    blob = _joined_text(pages, max_pages=4).lower()
    if "protean" in blob or "npscra.proteantech" in blob:
        return "PROT"
    if "camsonline" in blob or "cams cra" in blob:
        return "CAMS"
    if "kfintech" in blob or "kfin cra" in blob:
        return "KFIN"
    return "PROT"


def _parse_investment_summary(atoms: Sequence[Atom]) -> Tuple[Optional[Decimal], Optional[Decimal]]:
    """Return (portfolio_value A, total_contribution B)."""
    marker = next((a for a in atoms if a.text.strip() in ("(A)", "A") and a.x_left < 120), None)
    if marker is None:
        # Fall back: the first large 2-decimal number under "Investment Summary".
        hdr = next((a for a in atoms if "Investment Summary" in a.text), None)
        if hdr is None:
            return None, None
        nums = [
            a
            for a in atoms
            if a.y_top < hdr.y_top - 10
            and _looks_numeric(a.text)
            and _to_decimal(a.text) >= Decimal("100")
        ]
        nums.sort(key=lambda a: -a.y_top)
        if not nums:
            return None, None
        return _to_decimal(nums[0].text), None

    row = [
        a
        for a in atoms
        if marker.y_top - 30 < a.y_top < marker.y_top - 2 and _looks_numeric(a.text)
    ]
    row.sort(key=lambda a: a.x_left)
    if not row:
        return None, None
    value = _to_decimal(row[0].text)
    contrib = _to_decimal(row[2].text) if len(row) >= 3 else None
    return value, contrib


def _parse_scheme_preference(atoms: Sequence[Atom]) -> List[Tuple[str, Decimal]]:
    """[(scheme_name, allocation_pct), ...] from Current Scheme Preference."""
    hdr = next((a for a in atoms if "Current Scheme Preference" in a.text), None)
    stop = next((a for a in atoms if "Investment Summary" in a.text), None)
    if hdr is None:
        return []
    y_hi = hdr.y_top
    y_lo = stop.y_top if stop is not None else 0
    region = [a for a in atoms if y_lo < a.y_top < y_hi]
    schemes: List[Tuple[str, Decimal]] = []
    # Group by y into lines, look for "Scheme N" + name + percent.
    by_y: Dict[int, List[Atom]] = {}
    for a in region:
        by_y.setdefault(round(a.y_top, 0), []).append(a)
    # Walk top-down; a scheme can wrap onto the next line (e.g. trailing DIRECT).
    pending_name = ""
    pending_pct: Optional[Decimal] = None
    for y in sorted(by_y.keys(), reverse=True):
        line = sorted(by_y[y], key=lambda a: a.x_left)
        texts = [a.text.strip() for a in line]
        joined = " ".join(texts)
        if re.match(r"Scheme\s+\d+", joined, re.I) or any(
            re.match(r"Scheme\s+\d+", t, re.I) for t in texts
        ):
            if pending_name and pending_pct is not None:
                schemes.append((_clean_scheme_name(pending_name), pending_pct))
            name_parts = []
            pct = None
            for t in texts:
                if re.match(r"Scheme\s+\d+", t, re.I):
                    continue
                if m := _PCT_RE.match(t):
                    pct = Decimal(m.group(1))
                    continue
                if "NPS TRUST" in t.upper() or name_parts:
                    name_parts.append(t)
            pending_name = " ".join(name_parts)
            pending_pct = pct
        elif pending_name and not any(_PCT_RE.match(t) for t in texts):
            extra = [
                t
                for t in texts
                if t.upper() in ("DIRECT", "REGULAR") or t.upper().endswith("DIRECT")
            ]
            if extra:
                pending_name = pending_name + " " + " ".join(extra)
    if pending_name and pending_pct is not None:
        schemes.append((_clean_scheme_name(pending_name), pending_pct))
    return schemes


# --- scheme-wise table ------------------------------------------------------


def _classify_three_numbers(nums: List[Decimal]) -> Optional[Tuple[Decimal, Decimal, Decimal]]:
    """Given 3 numbers from a scheme column (any order), return (value, units, nav).

    Uses units * nav ≈ value as the ground truth. Returns None if the
    arithmetic doesn't confirm a unique pairing.
    """
    if len(nums) != 3:
        return None
    a, b, c = nums
    candidates = [
        (a, b, c),
        (a, c, b),
        (b, a, c),
        (b, c, a),
        (c, a, b),
        (c, b, a),
    ]
    hits = []
    for value, units, nav in candidates:
        if units <= 0 or nav <= 0:
            continue
        got = (units * nav).quantize(Decimal("0.01"))
        want = value.quantize(Decimal("0.01"))
        if abs(got - want) <= Decimal("0.05"):
            hits.append((value, units, nav))
    if not hits:
        return None
    # units * nav is commutative, so both (units, nav) and (nav, units)
    # match. Prefer the pairing where NAV is the smaller factor — NPS
    # NAVs are typically 10–100 while units are hundreds to thousands.
    uniq = {(v, u, n) for v, u, n in hits}
    return min(uniq, key=lambda t: t[2])


def _parse_scheme_wise_table(atoms: Sequence[Atom]) -> List[NPSScheme]:
    hdr = next(
        (
            a
            for a in atoms
            if "Investment Details" in a.text or "Scheme Wise Summary" in a.text
        ),
        None,
    )
    if hdr is None:
        return []
    region = [
        a
        for a in atoms
        if a.y_top < hdr.y_top - 2 and a.x_left >= _TABLE_LABEL_X and a.text.strip()
    ]
    trust = [a for a in region if "NPS TRUST" in a.text.upper()]
    if not trust:
        return []

    # Column anchors = NPS TRUST fragments, clustered by x so a wrapped
    # name that repeats the marker doesn't spawn extra columns.
    trust_sorted = sorted(trust, key=lambda a: a.x_left)
    anchors: List[Atom] = []
    for a in trust_sorted:
        if not anchors or a.x_left - anchors[-1].x_left >= _COL_GAP_MIN:
            anchors.append(a)
    if not anchors:
        return []

    def col_of(atom: Atom) -> int:
        cx = _center(atom)
        return min(range(len(anchors)), key=lambda i: abs(_center(anchors[i]) - cx))

    names: List[List[Atom]] = [[] for _ in anchors]
    numbers: List[List[Atom]] = [[] for _ in anchors]
    for a in region:
        # Skip the Particulars/References leftover if any leaked past x=175.
        low = a.text.strip().lower()
        if low in ("particulars", "references", "e=u*n", "u", "n"):
            continue
        if low.startswith("scheme wise") or low.startswith("holdings (") or low.startswith(
            "nav as on"
        ) or low.startswith("total units"):
            continue
        i = col_of(a)
        if _looks_numeric(a.text):
            numbers[i].append(a)
        else:
            names[i].append(a)

    schemes: List[NPSScheme] = []
    for i, anchor in enumerate(anchors):
        name_atoms = sorted(names[i], key=lambda a: (-a.y_top, a.x_left))
        name = _clean_scheme_name(" ".join(a.text for a in name_atoms))
        if "NPS TRUST" not in name.upper():
            name = _clean_scheme_name(anchor.text + " " + name)
        nums = [_to_decimal(a.text) for a in sorted(numbers[i], key=lambda a: -a.y_top)]
        # Dedup identical values that got double-extracted.
        packed: List[Decimal] = []
        for n in nums:
            if not packed or n != packed[-1]:
                packed.append(n)
        triple = None
        if len(packed) >= 3:
            triple = _classify_three_numbers(packed[:3])
        elif len(packed) == 2:
            # value + units, or units + nav. Prefer units * nav if one looks
            # like a NAV (< 500 with 4dp) and the other like units.
            x, y = packed
            if x > 0 and y > 0:
                # Treat the smaller as NAV when it's in typical NAV range.
                nav, units = (x, y) if x < y else (y, x)
                if nav < Decimal("500") and units >= nav:
                    value = (units * nav).quantize(Decimal("0.01"))
                    triple = (value, units, nav)
        if triple is None:
            continue
        value, units, nav = triple
        if units <= 0:
            continue
        schemes.append(
            NPSScheme(
                scheme=name,
                fund_manager=_fund_manager(name),
                tier=_norm_tier(_TIER_RE.search(name).group(1) if _TIER_RE.search(name) else None),
                asset_class=_asset_class(name),
                units=units,
                nav=nav,
                value=value.quantize(Decimal("0.01")),
            )
        )
    return schemes


def _parse_closing_units(pages: Sequence[Sequence[Atom]]) -> List[Tuple[str, Decimal]]:
    """[(scheme_name, closing_units), ...] from the Transaction Details table."""
    out: List[Tuple[str, Decimal]] = []
    for page in pages[1:]:
        close = next((a for a in page if a.text.strip().lower() == "closing balance"), None)
        if close is None:
            continue
        trust = [a for a in page if "NPS TRUST" in a.text.upper()]
        if not trust:
            continue
        trust_sorted = sorted(trust, key=lambda a: a.x_left)
        anchors: List[Atom] = []
        for a in trust_sorted:
            if not anchors or a.x_left - anchors[-1].x_left >= _COL_GAP_MIN:
                anchors.append(a)
        nums = [
            a
            for a in page
            if abs(a.y_top - close.y_top) <= 3.0
            and a.x_left > 150
            and _looks_numeric(a.text)
        ]
        nums.sort(key=lambda a: a.x_left)
        if not nums:
            continue
        # Pair left-to-right with anchors.
        n = min(len(anchors), len(nums))
        for i in range(n):
            name = _clean_scheme_name(anchors[i].text)
            out.append((name, _to_decimal(nums[i].text)))
        if out:
            return out
    return out


# --- public entry -----------------------------------------------------------


def parse_nps(
    pdf_path: str,
    password: str,
    file_type: FileType = FileType.NPS,
    *,
    _doc=None,
) -> NSDLCASData:
    """Parse a standalone NPS CRA transaction statement.

    Returns ``NSDLCASData`` with empty ``accounts`` and the holdings in
    ``nps``, so the Kosha sidecar can reuse the existing demat→holdings
    mapper (kind ``nps``, folio = PRAN).
    """
    pages = pageobj.extract_atoms(pdf_path, password, _doc=_doc)
    if not pages or not pages[0]:
        raise CASParseError("NPS statement has no readable text.")

    p1 = pages[0]
    warnings: List[str] = []
    period = _parse_period(p1) or StatementPeriod(**{"from": "", "to": ""})
    pran = _parse_pran(p1)
    name = _parse_subscriber_name(p1)
    email = _value_right_of(p1, "email id", x_max=300.0) or ""
    # Email is often a lone atom on the value column without sitting on
    # the same y as the label (wrapped address pushes it).
    if not email:
        for a in p1:
            t = a.text.strip()
            if "@" in t and "." in t:
                email = t.split()[0]
                break
    mobile = _value_right_of(p1, "mobile number", x_max=300.0) or ""
    address = _parse_address(p1)
    header_tier = _parse_tier_from_header(p1)
    cra = _detect_cra(pages)
    total_a, contrib_b = _parse_investment_summary(p1)
    preference = _parse_scheme_preference(p1)
    schemes = _parse_scheme_wise_table(p1)

    if not schemes:
        closing = _parse_closing_units(pages)
        if len(closing) == 1 and total_a and total_a > 0 and closing[0][1] > 0:
            units = closing[0][1]
            nav = (total_a / units).quantize(Decimal("0.0001"))
            scheme_name = closing[0][0]
            if preference:
                scheme_name = preference[0][0]
            schemes = [
                NPSScheme(
                    scheme=scheme_name,
                    fund_manager=_fund_manager(scheme_name),
                    tier=header_tier
                    or _norm_tier(
                        _TIER_RE.search(scheme_name).group(1)
                        if _TIER_RE.search(scheme_name)
                        else "I"
                    ),
                    asset_class=_asset_class(scheme_name),
                    units=units,
                    nav=nav,
                    value=total_a.quantize(Decimal("0.01")),
                )
            ]
        else:
            # Some Protean months leave the scheme-wise cells blank (the
            # table is drawn, labels are present, values are not in the
            # content stream). Closing-balance units on page 2 are often
            # blank in the same months, so we cannot invent a breakdown.
            warnings.append(
                "Scheme-wise units/NAV were not printed in this statement's "
                "text layer. Recorded the portfolio total only. Upload a "
                "month that prints the scheme-wise summary, or import NPS "
                "from a CDSL CAS."
            )
            if total_a and total_a > 0:
                if len(preference) == 1:
                    scheme_name = preference[0][0]
                else:
                    scheme_name = f"NPS Tier {header_tier or 'I'} Portfolio"
                schemes = [
                    NPSScheme(
                        scheme=scheme_name,
                        fund_manager=_fund_manager(scheme_name),
                        tier=header_tier
                        or _norm_tier(
                            _TIER_RE.search(scheme_name).group(1)
                            if _TIER_RE.search(scheme_name)
                            else "I"
                        ),
                        asset_class=_asset_class(scheme_name),
                        units=Decimal("1"),
                        nav=total_a,
                        value=total_a.quantize(Decimal("0.01")),
                    )
                ]

    # Fill missing tier from the statement header.
    for s in schemes:
        if not s.tier and header_tier:
            s.tier = header_tier

    value = total_a if total_a is not None else sum((s.value for s in schemes), Decimal(0))
    if value is None:
        value = Decimal(0)

    if schemes and total_a is not None:
        summed = sum((s.value for s in schemes), Decimal(0)).quantize(Decimal("0.01"))
        want = total_a.quantize(Decimal("0.01"))
        if abs(summed - want) > Decimal("1.00") and len(schemes) > 1:
            warnings.append(
                f"Scheme-wise values ({summed}) do not add up to the printed "
                f"portfolio total ({want})."
            )

    if not schemes and (value is None or value == 0) and not pran:
        raise CASParseError(
            "Could not read holdings from this NPS statement. "
            "It may be a different CRA template."
        )

    investor = InvestorInfo(
        name=name or "",
        email=email or "",
        address=address or "",
        mobile=mobile or "",
    )
    nps = NPSAccount(
        pran=pran,
        nps_sp=cra,
        value=(value or Decimal(0)).quantize(Decimal("0.01")),
        contribution=contrib_b.quantize(Decimal("0.01")) if contrib_b and contrib_b > 0 else None,
        schemes=schemes,
    )
    return NSDLCASData(
        accounts=[],
        statement_period=period,
        investor_info=investor,
        file_type=file_type,
        nps=nps,
        parse_warnings=warnings,
    )


__all__ = ["parse_nps", "nps_statement_password"]
