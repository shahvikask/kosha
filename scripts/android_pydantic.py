"""Minimal pydantic v2 surface for on-device casparser (no pydantic-core)."""
from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Callable, Dict, Optional, Union


def ConfigDict(**kwargs):
    return dict(kwargs)


@dataclass
class FieldInfo:
    default: Any = None
    alias: Optional[str] = None
    default_factory: Any = None


def Field(default: Any = None, alias: Optional[str] = None, default_factory: Any = None, **_kwargs):
    return FieldInfo(default=default, alias=alias, default_factory=default_factory)


def model_validator(*, mode: str = "after"):
    def deco(fn: Callable):
        raw = fn.__func__ if isinstance(fn, classmethod) else fn
        raw._gud_validator_mode = mode
        return fn if isinstance(fn, classmethod) else classmethod(fn)

    return deco


def field_validator(*_fields, mode: str = "after"):
    return model_validator(mode=mode)


def computed_field(fn=None, **_kwargs):
    if fn is None:
        return lambda f: f
    return fn


class ValidationError(ValueError):
    pass


class ModelMeta(type):
    def __new__(mcls, name, bases, ns):
        cls = super().__new__(mcls, name, bases, ns)
        fields: Dict[str, FieldInfo] = {}
        for base in reversed(cls.__mro__):
            anns = getattr(base, "__annotations__", {})
            for key, val in getattr(base, "__dict__", {}).items():
                if isinstance(val, FieldInfo):
                    fields[key] = val
                elif key in anns and not key.startswith("_") and not callable(val):
                    fields.setdefault(key, FieldInfo(default=val))
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
        annotations = {}
        for base in reversed(type(self).__mro__):
            annotations.update(getattr(base, "__annotations__", {}))
        for attr, info in type(self).model_fields.items():
            if attr in raw:
                val = raw[attr]
            elif info.default_factory is not None:
                val = info.default_factory()
            elif info.default is not None:
                val = info.default
            else:
                val = None
            anno = annotations.get(attr)
            if val is not None and anno is not None:
                val = _coerce(anno, val)
            setattr(self, attr, val)
        for name in dir(type(self)):
            fn = getattr(type(self), name, None)
            raw_fn = getattr(fn, "__func__", fn)
            if callable(raw_fn) and getattr(raw_fn, "_gud_validator_mode", None) == "after":
                raw_fn(type(self), self)

    def model_dump(self, **kwargs):
        by_alias = bool(kwargs.get("by_alias"))
        out = {}
        for attr, info in type(self).model_fields.items():
            key = info.alias if by_alias and info.alias else attr
            val = getattr(self, attr, None)
            if hasattr(val, "model_dump"):
                val = val.model_dump(**kwargs)
            elif isinstance(val, list):
                val = [v.model_dump(**kwargs) if hasattr(v, "model_dump") else v for v in val]
            out[key] = val
        return out

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def model_dump_json(self, **kwargs):
        return json.dumps(self.model_dump(**kwargs), default=str)


def _coerce(annotation: Any, val: Any) -> Any:
    origin = getattr(annotation, "__origin__", None)
    args = getattr(annotation, "__args__", ())
    if origin is Union:
        non_none = [a for a in args if a is not type(None)]
        if len(non_none) == 1:
            return _coerce(non_none[0], val)
        return val
    if origin in (list,):
        inner = args[0] if args else Any
        if isinstance(val, list):
            return [_coerce(inner, v) if inner is not Any else v for v in val]
        return val
    if isinstance(annotation, type) and issubclass(annotation, BaseModel) and isinstance(val, dict):
        return annotation(**val)
    return val
