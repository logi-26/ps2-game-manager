"""App-update and bundled-tool endpoints (replace the TCP VERSION / UPDATE / CUE2POPS)."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..blobstore import get_blobstore
from ..db import get_db
from ..models import AppRelease, ToolRelease
from ..schemas import AppReleaseOut, ToolReleaseOut

router = APIRouter(tags=["releases"])


@router.get("/app/latest", response_model=AppReleaseOut)
def app_latest(channel: str = "beta", db: Session = Depends(get_db)):
    row = db.scalar(
        select(AppRelease)
        .where(AppRelease.channel == channel)
        .order_by(AppRelease.published_at.desc().nullslast(), AppRelease.created_at.desc())
    )
    if row is None:
        raise HTTPException(404, f"no releases on channel {channel!r}")
    return row


@router.get("/app/releases/{version}/download")
def app_download(version: str, db: Session = Depends(get_db)):
    row = db.get(AppRelease, version)
    if row is None:
        raise HTTPException(404, "unknown version")
    blob = get_blobstore().path(row.sha256)
    if not blob.exists():
        raise HTTPException(410, "blob missing from store")
    return FileResponse(
        blob,
        media_type="application/java-archive",
        filename=f"OPLPOPS-Manager_{version}.jar",
        headers={"ETag": f'"{row.sha256}"'},
    )


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
