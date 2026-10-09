"""Non-frozen Android bootstrap for the immutable vendored CASPARSER entry.

The app must package the unchanged scripts/cas_android.py,
scripts/cas_parse_server.py, scripts/eod_prices.py,
scripts/android_pydantic.py, scripts/android_casparser_isin.py and
src/casparser-main/casparser/** with an Android-compatible PDFium wheel.
This source does not alter any of those frozen modules.
"""
from __future__ import annotations

import sys


def parse_b64(pdf_base64: str, password: str = "", filename: str = "statement.pdf") -> str:
    # Required frozen mobile shims, rather than importing host pydantic-core or
    # the desktop ISIN database from paths which do not exist on the phone.
    import android_pydantic
    import android_casparser_isin
    sys.modules.setdefault("pydantic", android_pydantic)
    sys.modules.setdefault("casparser_isin", android_casparser_isin)
    from cas_android import parse_b64
    return parse_b64(pdf_base64, password, filename)
