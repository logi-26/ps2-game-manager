from __future__ import annotations

from fastapi import FastAPI

from . import __version__
from .routers import artwork, files, games, health, releases, reports, uploads

app = FastAPI(
    title="PS2 Game Manager API",
    version=__version__,
    summary="Database-backed replacement for the OPLPOPS raw-TCP file server.",
)

for module in (health, games, artwork, files, releases, uploads, reports):
    app.include_router(module.router, prefix="/v1")


@app.get("/", include_in_schema=False)
def root() -> dict:
    return {"service": "ps2-game-manager-api", "version": __version__, "docs": "/docs"}
