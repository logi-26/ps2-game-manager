"""Config, cheat and VMC read endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Header, HTTPException, Response
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..blobstore import get_blobstore
from ..db import get_db
from ..deps import page_params
from ..models import CheatFile, ConfigFile, Game, VmcFile
from ..schemas import FileMetaOut, Page, VmcOut

router = APIRouter(tags=["files"])
_APPROVED = "approved"


def _text_response(body: str, sha: str, if_none_match: str | None) -> Response:
    etag = f'"{sha}"'
    if if_none_match and etag in {t.strip() for t in if_none_match.split(",")}:
        return Response(status_code=304, headers={"ETag": etag})
    return Response(body, media_type="text/plain; charset=utf-8", headers={"ETag": etag})


# ---- config ------------------------------------------------------------------
@router.get("/games/{game_id}/config")
def get_config(
    game_id: str,
    db: Session = Depends(get_db),
    if_none_match: str | None = Header(default=None),
):
    row = db.scalar(
        select(ConfigFile).where(ConfigFile.game_id == game_id, ConfigFile.status == _APPROVED)
    )
    if row is None:
        raise HTTPException(404, "no config for this game")
    return _text_response(row.body, row.sha256, if_none_match)


@router.get("/configs", response_model=Page[FileMetaOut])
def list_configs(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    console: str | None = None,
):
    limit, offset = paging
    stmt = select(ConfigFile).where(ConfigFile.status == _APPROVED)
    if console:
        stmt = stmt.join(Game).where(Game.console == console.upper())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.scalars(stmt.order_by(ConfigFile.game_id).limit(limit).offset(offset)).all()
    return Page(items=rows, total=total, limit=limit, offset=offset)


# ---- cheats ----------------------------------------------------------------
@router.get("/games/{game_id}/cheats")
def get_cheats(
    game_id: str,
    db: Session = Depends(get_db),
    if_none_match: str | None = Header(default=None),
):
    row = db.scalar(
        select(CheatFile).where(CheatFile.game_id == game_id, CheatFile.status == _APPROVED)
    )
    if row is None:
        raise HTTPException(404, "no cheats for this game")
    return _text_response(row.body, row.sha256, if_none_match)


@router.get("/cheats", response_model=Page[FileMetaOut])
def list_cheats(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    console: str | None = None,
):
    limit, offset = paging
    stmt = select(CheatFile).where(CheatFile.status == _APPROVED)
    if console:
        stmt = stmt.join(Game).where(Game.console == console.upper())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.scalars(stmt.order_by(CheatFile.game_id).limit(limit).offset(offset)).all()
    return Page(items=rows, total=total, limit=limit, offset=offset)


# ---- vmc -----------------------------------------------------------------
@router.get("/games/{game_id}/vmc", response_model=list[VmcOut])
def game_vmc(game_id: str, db: Session = Depends(get_db)):
    return db.scalars(
        select(VmcFile)
        .where(VmcFile.game_id == game_id, VmcFile.status == _APPROVED)
        .order_by(VmcFile.label)
    ).all()


@router.get("/games/{game_id}/vmc/{vmc_id}")
def game_vmc_bytes(game_id: str, vmc_id: int, db: Session = Depends(get_db)):
    row = db.scalar(
        select(VmcFile).where(
            VmcFile.id == vmc_id, VmcFile.game_id == game_id, VmcFile.status == _APPROVED
        )
    )
    if row is None:
        raise HTTPException(404, "vmc not found")
    blob = get_blobstore().path(row.sha256)
    if not blob.exists():
        raise HTTPException(410, "blob missing from store")
    return FileResponse(
        blob,
        media_type="application/octet-stream",
        filename=f"{row.label}.VMC",
        headers={"ETag": f'"{row.sha256}"'},
    )


@router.get("/vmc", response_model=Page[VmcOut])
def list_vmc(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    console: str | None = None,
):
    limit, offset = paging
    stmt = select(VmcFile).where(VmcFile.status == _APPROVED)
    if console:
        stmt = stmt.join(Game).where(Game.console == console.upper())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.scalars(stmt.order_by(VmcFile.game_id, VmcFile.label).limit(limit).offset(offset)).all()
    return Page(items=rows, total=total, limit=limit, offset=offset)
