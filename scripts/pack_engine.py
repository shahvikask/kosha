"""Freeze the local CAS + EOD sidecar into gud-engine(.exe) for the Windows installer."""
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src-tauri" / "resources"
OUT.mkdir(parents=True, exist_ok=True)
name = "gud-engine.exe" if os.name == "nt" else "gud-engine"
dest = OUT / name

cmd = [
    sys.executable, "-m", "PyInstaller",
    "--noconfirm", "--clean", "--onefile",
    "--noconsole",
    "--name", "gud-engine",
    "--distpath", str(OUT),
    "--workpath", str(ROOT / "target" / "pyinstaller"),
    "--specpath", str(ROOT / "target" / "pyinstaller"),
    "--add-data", f"{ROOT / 'src' / 'casparser-main'}{os.pathsep}casparser-main",
    "--add-data", f"{ROOT / 'scripts' / 'eod_prices.py'}{os.pathsep}.",
    "--hidden-import", "casparser",
    "--hidden-import", "casparser.parsers",
    "--hidden-import", "casparser.parsers.cdsl",
    "--hidden-import", "casparser.parsers.nsdl",
    "--hidden-import", "pypdfium2",
    "--hidden-import", "pydantic",
    "--hidden-import", "eod_prices",
    str(ROOT / "scripts" / "cas_parse_server.py"),
]
print(" ".join(cmd))
subprocess.check_call(cmd)
print("wrote", dest, "size", dest.stat().st_size if dest.exists() else "missing")
