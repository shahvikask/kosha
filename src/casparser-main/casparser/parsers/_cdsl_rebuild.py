#!/usr/bin/env python3
"""Rebuild cdsl.py from b64 parts in this directory. Run once after pull."""
import base64
from pathlib import Path
d = Path(__file__).resolve().parent
b64 = "".join((d / f"_cdsl_b64p{i}.txt").read_text() for i in range(3))
out = d / "cdsl.py"
out.write_bytes(base64.b64decode(b64))
print(f"wrote {out} ({out.stat().st_size} bytes)")
