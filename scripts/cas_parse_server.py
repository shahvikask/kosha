#!/usr/bin/env python3
"""Local CAS parse sidecar for Kosha.

Runs on the user's machine. PDFs are parsed in-process with the vendored
Python casparser (CDSL / NSDL / CAMS / KFin / NPS CRA) and never leave the device.

  python3 scripts/cas_parse_server.py              # HTTP on 127.0.0.1:8765
  python3 scripts/cas_parse_server.py --file X.pdf --password PAN
"""
from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import tempfile
import traceback
from datetime import datetime
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Optional

ROOT = Path(__file__).resolve().parent.parent
CAS_SRC = ROOT / "src" / "casparser-main"
if str(CAS_SRC) not in sys.path:
    sys.path.insert(0, str(CAS_SRC))
SCRIPTS = Path(__file__).resolve().parent
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from eod_prices import refresh_eod  # noqa: E402

HOST = "127.0.0.1"
PORT = 8765
PAN_RE = re.compile(r"[A-Z]{5}[0-9]{4}[A-Z]")


def _dec(v: Any) -> float:
    if v is None:
        return 0.0
    if isinstance(v, Decimal):
        return float(v)
    try:
        return float(str(v).replace(",", ""))
    except (TypeError, ValueError):
        return 0.0


def _s(v: Any) -> Optional[str]:
    if v is None:
        return None
    t = str(v).strip()
    return t or None


def _iso_date(raw: Any) -> Optional[str]:
    if raw is None:
        return None
    if hasattr(raw, "isoformat"):
        return raw.isoformat()
    t = str(raw).strip()
    if not t:
        return None
    for fmt in ("%Y-%m-%d", "%d-%b-%Y", "%d-%B-%Y", "%d-%m-%Y", "%d/%m/%Y", "%d/%m/%y"):
        try:
            return datetime.strptime(t, fmt).date().isoformat()
        except ValueError:
            continue
    return t


def _clean_name(name: Optional[str]) -> str:
    if not name:
        return "Unknown holding"
    n = name.strip().lstrip("!").strip()
    if "#" in n:
        left, right = n.split("#", 1)
        n = right.strip() or left.strip()
    return " ".join(n.split()) or "Unknown holding"


def _looks_like_address(name: str) -> bool:
    u = name.upper()
    if any(tok in u for tok in (
        "PINCODE", "PIN CODE", "RESIDENCY", "AHMEDABAD", "BANGALORE",
        "BENGALURU", "MUMBAI", "ROAD", "NAGAR",
    )):
        return True
    if any(ch.isdigit() for ch in name) and len(name) > 24:
        return True
    return False


def _fill_investor_from_password(result: dict, password: str) -> dict:
    if result.get("ok") and not result.get("investorPan"):
        pan = (password or "").strip().upper().replace(" ", "")
        if PAN_RE.fullmatch(pan):
            result["investorPan"] = pan
    return result


def _category(kind: str, scheme_type: Optional[str], name: str) -> str:
    st = (scheme_type or "").upper()
    nm = (name or "").upper()
    # SGB sits in the CDSL equity section; name must win before kind==equity.
    if kind == "gold" or "SGB" in nm or "SOVEREIGN GOLD" in nm:
        return "GOLD_SGB"
    if kind == "equity":
        return "EQUITY"
    if kind in ("bond", "nps"):
        return "FIXED_INCOME"
    if st == "AIF" or "AIF" in nm or "DISCOVERY FUND" in nm:
        return "SPECIALISED"
    if st == "ETF" or "ETF" in nm:
        return "MUTUAL_FUND"
    return "MUTUAL_FUND"


def _dump(model: Any) -> dict:
    if hasattr(model, "model_dump"):
        return model.model_dump(by_alias=True)
    if hasattr(model, "dict"):
        return model.dict(by_alias=True)
    return {}


def parse_pdf_path(pdf_path: str, password: str) -> dict:
    from casparser.enums import CASFileType, FileType
    from casparser.exceptions import CASParseError, IncorrectPasswordError
    from casparser.parsers.detect import (
        _open_document,
        detect_cas_type,
        detect_file_type,
    )

    doc = None
    try:
        doc = _open_document(pdf_path, password or "")
        file_type = detect_file_type(pdf_path, password or "", _doc=doc)
        if file_type == FileType.UNKNOWN:
            raise CASParseError(
                "Could not identify the statement. Supported: CAMS, KFintech, NSDL, CDSL, NPS."
            )

        if file_type in (FileType.CAMS, FileType.KFINTECH):
            cas_type = detect_cas_type(pdf_path, password or "", _doc=doc)
            if cas_type == CASFileType.DETAILED:
                from casparser.parsers import cams_detailed as parser
            else:
                from casparser.parsers import cams_summary as parser
            data = parser.parse(pdf_path, password or "", file_type=file_type, _doc=doc)
            return _fill_investor_from_password(_from_cams(data, file_type.name), password)
        if file_type == FileType.NSDL:
            from casparser.parsers import nsdl as parser
            data = parser.parse_nsdl(pdf_path, password or "", file_type=file_type, _doc=doc)
            return _fill_investor_from_password(_from_demat(data, "NSDL"), password)
        if file_type == FileType.NPS:
            from casparser.parsers import nps as parser
            data = parser.parse_nps(pdf_path, password or "", file_type=file_type, _doc=doc)
            return _fill_investor_from_password(_from_demat(data, "NPS"), password)
        from casparser.parsers import cdsl as parser
        data = parser.parse_cdsl(pdf_path, password or "", file_type=file_type, _doc=doc)
        return _fill_investor_from_password(_from_demat(data, "CDSL"), password)
    except IncorrectPasswordError as e:
        return {"ok": False, "error": "Incorrect password", "code": "BAD_PASSWORD", "detail": str(e)}
    except CASParseError as e:
        return {"ok": False, "error": str(e), "code": "PARSE_ERROR"}
    except Exception as e:
        msg = str(e)
        if "password" in msg.lower():
            return {"ok": False, "error": "Incorrect password", "code": "BAD_PASSWORD", "detail": msg}
        return {
            "ok": False,
            "error": f"Failed to parse statement: {msg}",
            "code": "INTERNAL",
            "detail": traceback.format_exc()[-1500:],
        }
    finally:
        if doc is not None:
            try:
                doc.close()
            except Exception:
                pass


def _from_demat(data: Any, file_type: str) -> dict:
    d = _dump(data)
    period = d.get("statement_period") or {}
    period_from = _iso_date(period.get("from") or period.get("from_"))
    period_to = _iso_date(period.get("to"))

    investor_name = None
    investor_pan = None
    investor_email = None
    info = d.get("investor_info") or {}
    if info.get("email"):
        investor_email = info.get("email")
    raw_inv_name = _s(info.get("name"))
    if raw_inv_name and not _looks_like_address(raw_inv_name):
        investor_name = raw_inv_name

    accounts_out = []
    holdings = []
    pdf_total = 0.0
    demat_total = 0.0
    mf_total = 0.0
    aif_total = 0.0
    skipped_nil = 0

    for acct in d.get("accounts") or []:
        bal = _dec(acct.get("balance"))
        pdf_total += bal
        acct_type = (acct.get("type") or "").lower()
        if "mutual fund" in acct_type:
            mf_total += bal
        else:
            demat_total += bal

        owners = acct.get("owners") or []
        if owners:
            if not investor_name:
                investor_name = _s(owners[0].get("name"))
            if not investor_pan:
                investor_pan = _s(owners[0].get("PAN") or owners[0].get("pan"))

        accounts_out.append({
            "name": acct.get("name"),
            "type": acct.get("type"),
            "dpId": _s(acct.get("dp_id")),
            "clientId": _s(acct.get("client_id")),
            "folios": acct.get("folios") or 0,
            "balance": round(bal, 2),
        })

        def add_row(kind: str, row: dict, qty_key: str, price_key: str) -> None:
            nonlocal skipped_nil, aif_total
            qty = _dec(row.get(qty_key))
            val = _dec(row.get("value"))
            if qty <= 0 and val <= 0:
                skipped_nil += 1
                return
            name = _clean_name(row.get("name"))
            scheme_type = _s(row.get("type"))
            category = _category(kind, scheme_type, name)
            price = _dec(row.get(price_key) or row.get("nav") or row.get("market_price"))
            if price <= 0 and qty > 0 and val > 0:
                price = val / qty
            if val <= 0 and qty > 0 and price > 0:
                val = qty * price
            rec = {
                "kind": kind,
                "name": name,
                "isin": _s(row.get("isin")),
                "quantity": qty,
                "price": round(price, 6),
                "value": round(val, 2),
                "cost": round(_dec(row.get("total_cost")), 2) or None,
                "folio": _s(row.get("folio")),
                "amfi": _s(row.get("amfi") or row.get("amfi_code")),
                "schemeType": scheme_type,
                "ucc": _s(row.get("ucc")),
                "symbol": _s(row.get("symbol")),
                "exchange": _s(row.get("exchange")),
                "dpName": _s(acct.get("name")),
                "dpId": _s(acct.get("dp_id")),
                "clientId": _s(acct.get("client_id")),
                "category": category,
                "accountType": acct.get("type"),
            }
            holdings.append(rec)
            if category == "SPECIALISED":
                aif_total += val

        for eq in acct.get("equities") or []:
            add_row("equity", eq, "num_shares", "price")
        for mf in acct.get("mutual_funds") or []:
            add_row("mutual_fund", mf, "balance", "nav")
        for bd in acct.get("bonds") or []:
            add_row("bond", bd, "num_bonds", "market_price")

    nps = d.get("nps")
    if nps:
        pdf_total += _dec(nps.get("value"))
        for sch in nps.get("schemes") or []:
            qty = _dec(sch.get("units"))
            val = _dec(sch.get("value"))
            if qty <= 0 and val <= 0:
                skipped_nil += 1
                continue
            holdings.append({
                "kind": "nps",
                "name": _clean_name(sch.get("scheme")),
                "isin": None,
                "quantity": qty,
                "price": _dec(sch.get("nav")),
                "value": round(val, 2),
                "cost": None,
                "folio": _s(nps.get("pran")),
                "amfi": None,
                "schemeType": "NPS",
                "category": "FIXED_INCOME",
                "dpName": _s(nps.get("nps_sp")),
                "accountType": "NPS",
            })

    parsed_total = round(sum(h["value"] for h in holdings), 2)
    pdf_total = round(pdf_total, 2)
    delta = round(parsed_total - pdf_total, 2)

    return {
        "ok": True,
        "engine": "PYTHON_CASPARSER",
        "fileType": file_type,
        "casType": "DEMAT",
        "investorName": investor_name,
        "investorPan": investor_pan,
        "investorEmail": investor_email,
        "periodFrom": period_from,
        "periodTo": period_to,
        "accounts": accounts_out,
        "holdings": holdings,
        "warnings": list(d.get("parse_warnings") or []),
        "skippedNilHoldings": skipped_nil,
        "pdfTotals": {
            "demat": round(demat_total, 2),
            "mutualFund": round(mf_total, 2),
            "aif": round(aif_total, 2),
            "grand": pdf_total,
        },
        "parsedTotals": {
            "holdingsCount": len(holdings),
            "holdingsValue": parsed_total,
        },
        "delta": delta,
        "complete": abs(delta) < 1.0,
    }


def _from_cams(data: Any, file_type: str) -> dict:
    d = _dump(data)
    period = d.get("statement_period") or {}
    period_from = _iso_date(period.get("from") or period.get("from_"))
    period_to = _iso_date(period.get("to"))
    info = d.get("investor_info") or {}

    holdings = []
    skipped_nil = 0
    pdf_total = 0.0

    for folio in d.get("folios") or []:
        for sch in folio.get("schemes") or []:
            valn = sch.get("valuation") or {}
            qty = _dec(sch.get("close"))
            val = _dec(valn.get("value"))
            nav = _dec(valn.get("nav"))
            if qty <= 0 and val <= 0:
                skipped_nil += 1
                continue
            name = _clean_name(sch.get("scheme"))
            scheme_type = _s(sch.get("type"))
            rec = {
                "kind": "mutual_fund",
                "name": name,
                "isin": _s(sch.get("isin")),
                "quantity": qty,
                "price": nav,
                "value": round(val if val else qty * nav, 2),
                "cost": round(_dec(valn.get("cost")), 2) or None,
                "folio": _s(folio.get("folio")),
                "amc": _s(folio.get("amc")),
                "rta": _s(sch.get("rta")),
                "amfi": _s(sch.get("amfi")),
                "advisor": _s(sch.get("advisor")),
                "schemeType": scheme_type,
                "category": _category("mutual_fund", scheme_type, name),
                "accountType": "Mutual Fund Folio",
            }
            holdings.append(rec)
            pdf_total += rec["value"]

    parsed_total = round(sum(h["value"] for h in holdings), 2)
    pdf_total = round(pdf_total, 2)
    return {
        "ok": True,
        "engine": "PYTHON_CASPARSER",
        "fileType": file_type,
        "casType": str(d.get("cas_type") or "DETAILED"),
        "investorName": None if _looks_like_address(_s(info.get("name")) or "") else _s(info.get("name")),
        "investorPan": None,
        "investorEmail": _s(info.get("email")),
        "periodFrom": period_from,
        "periodTo": period_to,
        "accounts": [],
        "holdings": holdings,
        "warnings": list(d.get("parse_warnings") or []),
        "skippedNilHoldings": skipped_nil,
        "pdfTotals": {"demat": 0, "mutualFund": pdf_total, "aif": 0, "grand": pdf_total},
        "parsedTotals": {"holdingsCount": len(holdings), "holdingsValue": parsed_total},
        "delta": round(parsed_total - pdf_total, 2),
        "complete": abs(parsed_total - pdf_total) < 1.0,
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "KoshaCasParser/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[cas-parser] " + (fmt % args) + "\n")

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, default=str).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _refresh_prices(self) -> None:
        try:
            result = refresh_eod()
        except Exception as exc:
            traceback.print_exc()
            self._json(500, {"ok": False, "error": f"Could not refresh prices: {exc}"})
            return
        status = 200 if result.get("ok") else 502
        self._json(status, result)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        if self.path.split("?", 1)[0] in ("/health", "/api/cas/health"):
            self._json(200, {
                "ok": True,
                "engine": "PYTHON_CASPARSER",
                "eod": True,
                "issuers": ["CAMS", "KFINTECH", "NSDL", "CDSL", "NPS"],
            })
            return
        self._json(404, {"ok": False, "error": "Not found"})

    def do_POST(self) -> None:  # noqa: N802
        path = self.path.split("?", 1)[0]
        if path in ("/prices/refresh", "/api/cas/prices/refresh"):
            self._refresh_prices()
            return
        if path not in ("/parse", "/api/cas/parse"):
            self._json(404, {"ok": False, "error": "Not found"})
            return
        length = int(self.headers.get("Content-Length") or "0")
        if length > 30 * 1024 * 1024:
            self._json(413, {"ok": False, "error": "PDF is too large (30 MB limit)."})
            return
        raw = self.rfile.read(length) if length else b"{}"
        try:
            req = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            self._json(400, {"ok": False, "error": "Invalid JSON body."})
            return

        password = str(req.get("password") or "")
        filename = str(req.get("filename") or "statement.pdf")
        b64 = req.get("pdfBase64") or ""
        if not b64:
            self._json(400, {"ok": False, "error": "Missing pdfBase64."})
            return
        try:
            pdf_bytes = base64.b64decode(b64)
        except Exception:
            self._json(400, {"ok": False, "error": "Could not decode PDF."})
            return

        tmp = tempfile.NamedTemporaryFile(prefix="kosha_cas_", suffix=".pdf", delete=False)
        try:
            tmp.write(pdf_bytes)
            tmp.close()
            result = parse_pdf_path(tmp.name, password)
        finally:
            try:
                Path(tmp.name).unlink(missing_ok=True)
            except Exception:
                pass

        status = 200 if result.get("ok") else (401 if result.get("code") == "BAD_PASSWORD" else 422)
        result["filename"] = filename
        self._json(status, result)


def serve(host: str = HOST, port: int = PORT) -> None:
    httpd = ThreadingHTTPServer((host, port), Handler)
    print(
        f"[cas-parser] Python CAS parser listening on http://{host}:{port} "
        "(CAMS, KFintech, NSDL, CDSL, NPS)",
        flush=True,
    )
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


def main() -> int:
    p = argparse.ArgumentParser(description="Kosha local Python CAS parser")
    p.add_argument("--serve", action="store_true", default=False)
    p.add_argument("--host", default=HOST)
    p.add_argument("--port", type=int, default=PORT)
    p.add_argument("--file")
    p.add_argument("--password", default="")
    args = p.parse_args()
    if args.file:
        result = parse_pdf_path(args.file, args.password)
        print(json.dumps(result, indent=2, default=str))
        return 0 if result.get("ok") else 1
    serve(args.host, args.port)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
