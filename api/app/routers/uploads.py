"""User-contributed uploads. Everything lands as status='pending' for later review."""

from __future__ import annotations

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session

from ..blobstore import get_blobstore
from ..config import get_settings
from ..db import get_db
from ..deps import require_write_key
from ..models import ART_KINDS, Artwork, ConfigFile, Game, VmcFile

router = APIRouter(tags=["uploads"], dependencies=[Depends(require_write_key)])

_IMAGE_MIME = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png"}


def _read_capped(upload: UploadFile) -> bytes:
    cap = get_settings().max_upload_bytes
    data = upload.file.read(cap + 1)
    if len(data) > cap:
        raise HTTPException(413, f"upload exceeds {cap} bytes")
    return data


def _require_game(db: Session, game_id: str) -> Game:
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(404, "unknown game")
    return game


@router.post("/games/{game_id}/artwork", status_code=201)
def upload_artwork(
    game_id: str,
    kind: str = Form(...),
    variant: int = Form(0),
    submitted_by: str | None = Form(None),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    _require_game(db, game_id)
    kind = kind.upper().lstrip("_")
    if kind not in ART_KINDS:
        raise HTTPException(422, f"kind must be one of {ART_KINDS}")
    ext = (file.filename or "").rsplit(".", 1)[-1].lower()
    mime = _IMAGE_MIME.get(ext)
    if mime is None:
        raise HTTPException(422, "file must be .jpg or .png")

    data = _read_capped(file)
    sha, size = get_blobstore().put_bytes(data)
    row = Artwork(
        game_id=game_id,
        kind=kind,
        variant=variant,
        mime=mime,
        sha256=sha,
        bytes=size,
        source="user_upload",
        status="pending",
        submitted_by=submitted_by,
    )
    db.add(row)
    db.commit()
    return {"id": row.id, "status": row.status, "sha256": sha}


@router.post("/games/{game_id}/config", status_code=201)
def upload_config(
    game_id: str,
    submitted_by: str | None = Form(None),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    _require_game(db, game_id)
    body = _read_capped(file).decode("utf-8", errors="replace")
    sha, _ = get_blobstore().put_bytes(body.encode("utf-8"))
    row = ConfigFile(
        game_id=game_id,
        body=body,
        sha256=sha,
        source="user_upload",
        status="pending",
        submitted_by=submitted_by,
    )
    db.add(row)
    db.commit()
    return {"id": row.id, "status": row.status, "sha256": sha}


@router.post("/games/{game_id}/vmc", status_code=201)
def upload_vmc(
    game_id: str,
    label: str = Form(...),
    description: str | None = Form(None),
    submitted_by: str | None = Form(None),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    _require_game(db, game_id)
    data = _read_capped(file)
    sha, size = get_blobstore().put_bytes(data)
    row = VmcFile(
        game_id=game_id,
        label=label,
        description=description,
        sha256=sha,
        bytes=size,
        source="user_upload",
        status="pending",
        submitted_by=submitted_by,
    )
    db.add(row)
    db.commit()
    return {"id": row.id, "status": row.status, "sha256": sha}
