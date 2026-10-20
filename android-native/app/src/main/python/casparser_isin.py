"""Android adapter for the frozen scripts/android_casparser_isin.py.

The upstream CAS parser is NOT modified. If the exact source has no bundled
ISIN catalog, unknown metadata is intentionally absent rather than invented.
"""
from android_casparser_isin import MFISINDb, ISINDb

__all__ = ["MFISINDb", "ISINDb"]
