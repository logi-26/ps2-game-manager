from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.gzip import GZipMiddleware

from . import __version__
from .config import get_settings
from .ratelimit import RateLimitMiddleware
from .routers import artwork, files, games, health, releases

app = FastAPI(
    title="PS2 Game Manager API",
    version=__version__,
    summary="Database-backed replacement for the OPLPOPS raw-TCP file server.",
)

app.add_middleware(GZipMiddleware, minimum_size=500)

_rate_limit = get_settings().rate_limit_per_minute
if _rate_limit:
    app.add_middleware(RateLimitMiddleware, limit_per_minute=_rate_limit)

# Read-only: user uploads and bad-file reports were removed as a feature.
for module in (health, games, artwork, files, releases):
    app.include_router(module.router, prefix="/v1")


@app.get("/", include_in_schema=False)
def root() -> dict:
    return {"service": "ps2-game-manager-api", "version": __version__, "docs": "/docs"}
