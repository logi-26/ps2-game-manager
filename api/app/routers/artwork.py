from __future__ import annotations

from fastapi import APIRouter, Depends, Header, HTTPException, Response
from fastapi.responses import FileResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..blobstore import get_blobstore
from ..db import get_db
from ..deps import page_params
from ..models import Artwork, Game
from ..schemas import ArtworkKindOut, ArtworkOut, Page

router = APIRouter(tags=["artwork"])
_APPROVED = "approved"


@router.get("/games/{game_id}/artwork", response_model=list[ArtworkOut])
def game_artwork(game_id: str, db: Session = Depends(get_db)):
    return db.scalars(
        select(Artwork)
        .where(Artwork.game_id == game_id, Artwork.status == _APPROVED)
        .order_by(Artwork.kind, Artwork.variant)
    ).all()


@router.get("/games/{game_id}/artwork/{kind}", response_model=ArtworkKindOut)
def game_artwork_kind(game_id: str, kind: str, db: Session = Depends(get_db)):
    rows = db.scalars(
        select(Artwork)
        .where(
            Artwork.game_id == game_id,
            Artwork.kind == kind.upper(),
            Artwork.status == _APPROVED,
        )
        .order_by(Artwork.variant)
    ).all()
    return ArtworkKindOut(game_id=game_id, kind=kind.upper(), count=len(rows), variants=rows)


@router.get(
    "/games/{game_id}/artwork/{kind}/{variant}",
    responses={200: {"content": {"image/*": {}}}, 304: {}, 404: {}},
)
def game_artwork_bytes(
    game_id: str,
    kind: str,
    variant: int,
    db: Session = Depends(get_db),
    if_none_match: str | None = Header(default=None),
):
    row = db.scalar(
        select(Artwork).where(
            Artwork.game_id == game_id,
            Artwork.kind == kind.upper(),
            Artwork.variant == variant,
            Artwork.status == _APPROVED,
        )
    )
    if row is None:
        raise HTTPException(404, "artwork not found")

    etag = f'"{row.sha256}"'
    if if_none_match and etag in {t.strip() for t in if_none_match.split(",")}:
        return Response(status_code=304, headers={"ETag": etag})

    blob = get_blobstore().path(row.sha256)
    if not blob.exists():
        raise HTTPException(410, "blob missing from store")
    return FileResponse(
        blob,
        media_type=row.mime,
        headers={"ETag": etag, "Cache-Control": "public, max-age=86400"},
    )


@router.get("/artwork", response_model=Page[ArtworkOut])
def list_artwork(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    console: str | None = None,
    kind: str | None = None,
):
    limit, offset = paging
    stmt = select(Artwork).where(Artwork.status == _APPROVED)
    if kind:
        stmt = stmt.where(Artwork.kind == kind.upper())
    if console:
        stmt = stmt.join(Game).where(Game.console == console.upper())

    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.scalars(
        stmt.order_by(Artwork.game_id, Artwork.kind, Artwork.variant).limit(limit).offset(offset)
    ).all()
    return Page(items=rows, total=total, limit=limit, offset=offset)
