#!/usr/bin/env python3
"""Download official end-of-day Indian prices, parse, then delete the files.

Sources (no live quotes, no Google/Moneycontrol/Yahoo):
  - NSE CM UDiFF bhavcopy zip (close, ISIN, symbol, name)
  - BSE CM UDiFF bhavcopy CSV (same columns)
  - AMFI NAVAll.txt (NAV by ISIN)

Casparser is not imported. Files live in a TemporaryDirectory and are
removed when parsing finishes, including on error.
"""
from __future__ import annotations

import csv
import io
import ssl
import tempfile
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

IST = timezone(timedelta(hours=5, minutes=30))
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
CTX = ssl.create_default_context()

NSE_SERIES = {"EQ", "BE", "SM", "ST", "GB"}
NSE_URL = "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_{ymd}_F_0000.csv.zip"
BSE_URL = "https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_{ymd}_F_0000.CSV"
AMFI_URL = "https://www.amfiindia.com/spages/NAVAll.txt"

ISIN_RE_OK = 12


def _now_ist() -> datetime:
    return datetime.now(IST)


def _get(url: str, referer: str, dest: Path, timeout: int = 45) -> Optional[bytes]:
    req = Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "*/*",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": referer,
        },
    )
    try:
        with urlopen(req, timeout=timeout, context=CTX) as resp:
            data = resp.read()
    except (HTTPError, URLError, TimeoutError, OSError):
        return None
    if not data or data.lstrip()[:15].lower().startswith((b"<!doctype", b"<html")):
        return None
    dest.write_bytes(data)
    return data


def _parse_date(raw: str) -> Optional[str]:
    t = (raw or "").strip()
    if not t:
        return None
    for fmt in ("%Y-%m-%d", "%d-%b-%Y", "%d-%B-%Y", "%d-%m-%Y", "%d/%m/%Y"):
        try:
            return datetime.strptime(t, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def _f(v: Any) -> Optional[float]:
    try:
        x = float(str(v).replace(",", "").strip())
    except (TypeError, ValueError):
        return None
    if x <= 0:
        return None
    return x


def _walk_days(limit: int = 12):
    start = _now_ist().date()
    for i in range(limit):
        yield start - timedelta(days=i)


def _parse_udiff(text: str, exchange: str) -> tuple[str, list[dict]]:
    reader = csv.DictReader(io.StringIO(text))
    as_of = ""
    rows: list[dict] = []
    for rec in reader:
        isin = (rec.get("ISIN") or "").strip().upper()
        if len(isin) < ISIN_RE_OK:
            continue
        if exchange == "NSE":
            series = (rec.get("SctySrs") or "").strip().upper()
            if series not in NSE_SERIES:
                continue
        else:
            if (rec.get("FinInstrmTp") or "").strip().upper() != "STK":
                continue
        px = _f(rec.get("ClsPric"))
        if px is None:
            continue
        dt = _parse_date(rec.get("TradDt") or rec.get("BizDt") or "")
        if dt and dt > as_of:
            as_of = dt
        name = (rec.get("FinInstrmNm") or "").strip()
        symbol = (rec.get("TckrSymb") or "").strip().upper()
        series = (rec.get("SctySrs") or "").strip().upper()
        cat = "GOLD_SGB" if series == "GB" else "EQUITY"
        rows.append(
            {
                "isin": isin,
                "name": name or symbol or isin,
                "symbol": symbol,
                "nse": symbol if exchange == "NSE" else "",
                "bse": symbol if exchange == "BSE" else "",
                "amfi": "",
                "cat": cat,
                "px": px,
                "dt": dt or as_of,
                "src": f"{exchange} close",
            }
        )
    return as_of, rows


def fetch_nse(tmpdir: Path) -> tuple[Optional[str], list[dict], Optional[str]]:
    for day in _walk_days():
        ymd = day.strftime("%Y%m%d")
        path = tmpdir / f"nse_{ymd}.zip"
        data = _get(NSE_URL.format(ymd=ymd), "https://www.nseindia.com/all-reports", path)
        if not data or data[:2] != b"PK":
            continue
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                name = zf.namelist()[0]
                text = zf.read(name).decode("utf-8", errors="replace")
        except (zipfile.BadZipFile, IndexError, OSError):
            continue
        as_of, rows = _parse_udiff(text, "NSE")
        if rows:
            return as_of or day.isoformat(), rows, None
    return None, [], "NSE bhavcopy not published for the last sessions (holiday or file delay)."


def fetch_bse(tmpdir: Path) -> tuple[Optional[str], list[dict], Optional[str]]:
    for day in _walk_days():
        ymd = day.strftime("%Y%m%d")
        path = tmpdir / f"bse_{ymd}.csv"
        data = _get(BSE_URL.format(ymd=ymd), "https://www.bseindia.com/", path)
        if not data:
            continue
        try:
            text = data.decode("utf-8", errors="replace")
        except Exception:
            continue
        if "ISIN" not in text.splitlines()[0]:
            continue
        as_of, rows = _parse_udiff(text, "BSE")
        if rows:
            return as_of or day.isoformat(), rows, None
    return None, [], "BSE bhavcopy not published for the last sessions (holiday or file delay)."


def parse_amfi(text: str) -> tuple[str, list[dict]]:
    as_of = ""
    rows: list[dict] = []
    seen: set[str] = set()
    for line in text.splitlines():
        if ";" not in line:
            continue
        parts = [p.strip() for p in line.split(";")]
        if len(parts) < 8:
            continue
        if parts[0].lower().startswith("scheme"):
            continue
        code, isin_g, isin_r, name = parts[0], parts[1], parts[2], parts[3]
        nav = _f(parts[-2])
        dt = _parse_date(parts[-1])
        if nav is None:
            continue
        if dt and dt > as_of:
            as_of = dt
        for isin in (isin_g, isin_r):
            isin = isin.upper()
            if len(isin) < ISIN_RE_OK or isin in ("-", "NA", "N/A"):
                continue
            if isin in seen:
                continue
            seen.add(isin)
            rows.append(
                {
                    "isin": isin,
                    "name": name,
                    "symbol": code,
                    "nse": "",
                    "bse": "",
                    "amfi": code,
                    "cat": "MUTUAL_FUND",
                    "px": nav,
                    "dt": dt or as_of,
                    "src": "AMFI NAV",
                }
            )
    return as_of, rows


def fetch_amfi(tmpdir: Path) -> tuple[Optional[str], list[dict], Optional[str]]:
    path = tmpdir / "NAVAll.txt"
    data = _get(AMFI_URL, "https://www.amfiindia.com/", path, timeout=60)
    if not data:
        data = _get(
            "https://portal.amfiindia.com/spages/NAVAll.txt",
            "https://www.amfiindia.com/",
            path,
            timeout=60,
        )
    if not data:
        return None, [], "AMFI NAV file could not be downloaded."
    text = data.decode("utf-8", errors="replace")
    as_of, rows = parse_amfi(text)
    if not rows:
        return None, [], "AMFI NAV file had no priced schemes."
    return as_of, rows, None


def merge_catalog(nse: list[dict], bse: list[dict], amfi: list[dict]) -> list[dict]:
    by: dict[str, dict] = {}
    for row in nse:
        by[row["isin"]] = dict(row)
    for row in bse:
        cur = by.get(row["isin"])
        if cur:
            cur["bse"] = row["symbol"] or cur.get("bse") or ""
            if not cur.get("nse"):
                cur["nse"] = ""
                cur["src"] = row["src"]
                cur["px"] = row["px"]
                cur["dt"] = row["dt"]
            cur["symbol"] = cur.get("nse") or row["symbol"]
        else:
            by[row["isin"]] = dict(row)
    for row in amfi:
        # Fund ISINs must not be overwritten by a stray equity row.
        existing = by.get(row["isin"])
        if existing and existing.get("cat") == "EQUITY":
            continue
        by[row["isin"]] = dict(row)
    return list(by.values())


def refresh_eod() -> dict[str, Any]:
    warnings: list[str] = []
    sources: list[dict] = []
    with tempfile.TemporaryDirectory(prefix="kosha_eod_") as raw:
        tmp = Path(raw)
        nse_asof, nse_rows, nse_warn = fetch_nse(tmp)
        bse_asof, bse_rows, bse_warn = fetch_bse(tmp)
        amfi_asof, amfi_rows, amfi_warn = fetch_amfi(tmp)
        for w in (nse_warn, bse_warn, amfi_warn):
            if w:
                warnings.append(w)
        if nse_asof:
            sources.append({"name": "NSE", "asOf": nse_asof, "count": len(nse_rows)})
        if bse_asof:
            sources.append({"name": "BSE", "asOf": bse_asof, "count": len(bse_rows)})
        if amfi_asof:
            sources.append({"name": "AMFI", "asOf": amfi_asof, "count": len(amfi_rows)})
        catalog = merge_catalog(nse_rows, bse_rows, amfi_rows)
        # TemporaryDirectory deletes zip/csv/txt here.
    dates = [s["asOf"] for s in sources if s.get("asOf")]
    as_of = max(dates) if dates else None
    return {
        "ok": bool(catalog),
        "asOf": as_of,
        "nseAsOf": nse_asof,
        "bseAsOf": bse_asof,
        "amfiAsOf": amfi_asof,
        "sources": sources,
        "warnings": warnings,
        "catalog": catalog,
        "catalogCount": len(catalog),
        "error": None if catalog else "No end-of-day files could be read.",
    }


if __name__ == "__main__":
    import json

    out = refresh_eod()
    sample = [r for r in out["catalog"] if r["isin"] == "INE467B01029"]
    print(json.dumps({k: out[k] for k in out if k != "catalog"}, indent=2))
    print("TCS", sample[:1])
    print("tmpdir gone", not Path("/tmp").joinpath("kosha_eod_").exists())
