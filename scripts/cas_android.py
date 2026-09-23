"""On-device CAS entry for the Android APK (Chaquopy).

Uses the same vendored casparser + parse_pdf_path as the desktop sidecar.
The PDF is written to a temp file on the phone and never uploaded.
"""
from __future__ import annotations

import base64
import json
import os
import sys
import tempfile
import traceback
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))


def health() -> str:
    detail = {"ok": False, "engine": "PYTHON_CASPARSER", "python": sys.version}
    try:
        import casparser  # noqa: F401

        detail["casparser"] = getattr(casparser, "__version__", "yes")
    except Exception as exc:
        detail["error"] = f"casparser import failed: {exc}"
        detail["trace"] = traceback.format_exc()[-800:]
        return json.dumps(detail)
    try:
        import pypdfium2  # noqa: F401

        detail["pypdfium2"] = getattr(pypdfium2, "__version__", "yes")
    except Exception as exc:
        detail["error"] = f"pypdfium2 import failed: {exc}"
        detail["trace"] = traceback.format_exc()[-800:]
        return json.dumps(detail)
    try:
        from cas_parse_server import parse_pdf_path  # noqa: F401

        detail["server"] = True
    except Exception as exc:
        detail["error"] = f"cas_parse_server import failed: {exc}"
        detail["trace"] = traceback.format_exc()[-800:]
        return json.dumps(detail)
    detail["ok"] = True
    return json.dumps(detail)


def parse_b64(pdf_b64: str, password: str = "", filename: str = "statement.pdf") -> str:
    from cas_parse_server import parse_pdf_path

    raw = base64.b64decode(pdf_b64 or "")
    if not raw:
        return json.dumps({"ok": False, "error": "Empty PDF.", "code": "PARSE_ERROR"})
    fd, path = tempfile.mkstemp(suffix=".pdf")
    try:
        os.write(fd, raw)
        os.close(fd)
        fd = -1
        result = parse_pdf_path(path, password or "")
        result["filename"] = filename or "statement.pdf"
        return json.dumps(result, default=str)
    except Exception as exc:
        return json.dumps(
            {
                "ok": False,
                "error": f"Failed to parse statement: {exc}",
                "code": "INTERNAL",
                "detail": traceback.format_exc()[-1500:],
            }
        )
    finally:
        if fd >= 0:
            try:
                os.close(fd)
            except OSError:
                pass
        try:
            os.unlink(path)
        except OSError:
            pass
