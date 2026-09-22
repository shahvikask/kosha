#!/usr/bin/env python3
"""Rewrite src-tauri/icons/*.png as RGBA. Tauri 2 refuses palette/RGB icons."""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src-tauri" / "icons"


def convert_with_magick(path: Path) -> None:
    tool = shutil.which("magick") or shutil.which("convert")
    if not tool:
        raise RuntimeError("ImageMagick not found")
    tmp = path.with_suffix(".rgba.png")
    subprocess.check_call([tool, str(path), "-define", "png:color-type=6", f"PNG32:{tmp}"])
    tmp.replace(path)
    print(f"converted {path.name} -> RGBA")


def convert_with_pil(path: Path) -> None:
    from PIL import Image

    im = Image.open(path)
    mode = im.mode
    if mode != "RGBA":
        im.convert("RGBA").save(path, "PNG")
        print(f"converted {path.name} {mode} -> RGBA")
    else:
        print(f"ok {path.name} RGBA")


def main() -> int:
    if not ROOT.is_dir():
        print(f"no icon dir: {ROOT}", file=sys.stderr)
        return 1
    for path in sorted(ROOT.glob("*.png")):
        try:
            convert_with_magick(path)
        except Exception as magick_err:
            try:
                convert_with_pil(path)
            except Exception as pil_err:
                print(f"failed {path.name}: magick={magick_err}; pil={pil_err}", file=sys.stderr)
                return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
