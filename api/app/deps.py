from __future__ import annotations

from fastapi import Query

from .config import get_settings


def page_params(
    limit: int = Query(default=0, ge=0),
    offset: int = Query(default=0, ge=0),
) -> tuple[int, int]:
    s = get_settings()
    eff = limit or s.page_size_default
    return min(eff, s.page_size_max), offset
