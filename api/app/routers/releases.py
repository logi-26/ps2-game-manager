"""App-update and bundled-tool endpoints (replace the TCP VERSION / UPDATE / CUE2POPS).

App releases are read live from GitHub Releases (see ../github.py) rather than
stored in the DB: cutting a release IS publishing it. Bundled tools
(cue2pops etc.) are still DB+blob backed since they aren't versioned as
GitHub releases of this repo.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse, RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import github
from ..blobstore import get_blobstore
from ..db import get_db
from ..models import ToolRelease
from ..schemas import AppReleaseOut, ToolReleaseOut

router = APIRouter(tags=["releases"])


def _to_schema(rel: github.Release) -> AppReleaseOut:
    return AppReleaseOut(
        version=rel.version,
        channel=rel.channel,
        notes=rel.notes,
        published_at=rel.published_at,
        asset_name=rel.asset_name,
        bytes=rel.asset_size,
        download_url=rel.download_url,
    )


@router.get("/app/latest", response_model=AppReleaseOut)
def app_latest(channel: str = "stable"):
    rel = github.get_latest_release(channel)
    if rel is None:
        raise HTTPException(404, f"no releases with a .jar asset on channel {channel!r}")
    return _to_schema(rel)


@router.get("/app/releases/{version}/download")
def app_download(version: str):
    rel = github.get_release_by_version(version)
    if rel is None:
        raise HTTPException(404, "unknown version")
    return RedirectResponse(rel.download_url, status_code=307)


@router.get("/tools/{name}")
def tool_download(name: str, os: str, arch: str = "any", db: Session = Depends(get_db)):
    row = db.scalar(
        select(ToolRelease).where(
            ToolRelease.name == name, ToolRelease.os == os, ToolRelease.arch == arch
        )
    )
    if row is None:
        raise HTTPException(404, f"no {name} build for {os}/{arch}")
    blob = get_blobstore().path(row.sha256)
    if not blob.exists():
        raise HTTPException(410, "blob missing from store")
    suffix = ".exe" if os == "windows" else ""
    return FileResponse(
        blob,
        media_type="application/octet-stream",
        filename=f"{name}{suffix}",
        headers={"ETag": f'"{row.sha256}"'},
    )


@router.get("/tools", response_model=list[ToolReleaseOut])
def list_tools(db: Session = Depends(get_db)):
    return db.scalars(select(ToolRelease).order_by(ToolRelease.name, ToolRelease.os)).all()
