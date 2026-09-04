from __future__ import annotations

from fastapi import Header, HTTPException, Query, status

from .config import get_settings


def require_write_key(x_api_key: str | None = Header(default=None)) -> None:
    """Gate write endpoints. If no key is configured, writes are open (dev mode)."""
    configured = get_settings().write_api_key
    if configured and x_api_key != configured:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing or invalid X-API-Key")


def page_params(
    limit: int = Query(default=0, ge=0),
    offset: int = Query(default=0, ge=0),
) -> tuple[int, int]:
    s = get_settings()
    eff = limit or s.page_size_default
    return min(eff, s.page_size_max), offset
