#!/usr/bin/env python3
"""Rewrite src-tauri/icons/*.png as RGBA. Tauri 2 refuses palette/RGB icons."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow", "-q"])
    from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "src-tauri" / "icons"


def main() -> int:
    if not ROOT.is_dir():
        print(f"no icon dir: {ROOT}", file=sys.stderr)
        return 1
    for path in sorted(ROOT.glob("*.png")):
        im = Image.open(path)
        mode = im.mode
        size = im.size
        if mode != "RGBA":
            im = im.convert("RGBA")
            im.save(path, "PNG")
            print(f"converted {path.name} {mode} -> RGBA {size[0]}x{size[1]}")
        else:
            print(f"ok {path.name} RGBA {size[0]}x{size[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
