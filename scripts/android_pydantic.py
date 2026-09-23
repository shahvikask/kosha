"""Minimal pydantic v2 surface for on-device casparser (no pydantic-core)."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, Optional


def ConfigDict(**kwargs):
    return dict(kwargs)


@dataclass
class FieldInfo:
    default: Any = None
    alias: Optional[str] = None


def Field(default: Any = None, alias: Optional[str] = None, **_kwargs):
    return FieldInfo(default=default, alias=alias)


def model_validator(*, mode: str = "after"):
    def deco(fn: Callable):
        fn._gud_validator_mode = mode
        return classmethod(fn) if not isinstance(fn, classmethod) else fn

    return deco


class ModelMeta(type):
    def __new__(mcls, name, bases, ns):
        cls = super().__new__(mcls, name, bases, ns)
        fields: Dict[str, FieldInfo] = {}
        for base in reversed(cls.__mro__):
            for key, val in getattr(base, "__dict__", {}).items():
                if isinstance(val, FieldInfo):
                    fields[key] = val
            anns = getattr(base, "__annotations__", {})
            for key in anns:
                fields.setdefault(key, FieldInfo())
        cls.model_fields = fields
        return cls


class BaseModel(metaclass=ModelMeta):
    def __init__(self, **data: Any):
        cfg = getattr(self, "model_config", {}) or {}
        populate = bool(cfg.get("populate_by_name"))
        raw = dict(data)
        for attr, info in type(self).model_fields.items():
            if info.alias and info.alias in raw and (populate or attr not in raw):
                raw.setdefault(attr, raw[info.alias])
        for name in dir(type(self)):
            fn = getattr(type(self), name, None)
            raw_fn = getattr(fn, "__func__", fn)
            if callable(raw_fn) and getattr(raw_fn, "_gud_validator_mode", None) == "before":
                raw = raw_fn(type(self), raw)
        for attr, info in type(self).model_fields.items():
            if attr in raw:
                setattr(self, attr, raw[attr])
            elif info.default is not None:
                setattr(self, attr, info.default)
            else:
                setattr(self, attr, None)

    def model_dump(self, **_kwargs):
        out = {}
        for attr in type(self).model_fields:
            val = getattr(self, attr, None)
            if hasattr(val, "model_dump"):
                val = val.model_dump()
            elif isinstance(val, list):
                val = [v.model_dump() if hasattr(v, "model_dump") else v for v in val]
            out[attr] = val
        return out
