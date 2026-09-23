"""ISIN lookup stub used when casparser-isin cannot be pip-installed on device."""
from __future__ import annotations

from typing import Any, Dict, Iterable


class MFISINDb:
    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False

    def isin_lookup(self, *_args, **_kwargs):
        raise ValueError("isin database not bundled on phone")

    def direct_isin_lookup(self, *_args, **_kwargs):
        return []


class ISINDb:
    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False

    def batch_isin_lookup(self, isins: Iterable[str]) -> Dict[str, Any]:
        return {}
