from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from .. import namesearch
from ..db import get_db
from ..deps import page_params
from ..models import Artwork, CheatFile, ConfigFile, Game, VmcFile
from ..schemas import GameDetailOut, GameOut, GameSuggestionOut, Page

router = APIRouter(tags=["games"])


@router.get("/games", response_model=Page[GameOut])
def list_games(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    console: str | None = None,
    region: str | None = None,
    q: str | None = None,
):
    limit, offset = paging
    stmt = select(Game)
    if console:
        stmt = stmt.where(Game.console == console.upper())
    if region:
        stmt = stmt.where(Game.region == region.upper())
    if q:
        like = f"%{q}%"
        stmt = stmt.where(or_(Game.title.ilike(like), Game.id.ilike(like)))

    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.scalars(stmt.order_by(Game.id).limit(limit).offset(offset)).all()
    return Page(items=rows, total=total, limit=limit, offset=offset)


@router.get("/games/suggest", response_model=list[GameSuggestionOut])
def suggest_games(
    console: str,
    q: str,
    limit: int = 15,
    db: Session = Depends(get_db),
):
    """Fuzzy name -> game-ID candidates from the user-supplied reference lists
    (see scripts/import_gamelists.py) - never a single "best" answer, every
    candidate above the threshold is returned so the caller (the desktop app's
    "unable to detect game ID" rename screen) can show them all and let a
    human pick, which matters most for multi-disc/reissue titles that share a
    name but not an ID. Registered before /games/{game_id} - FastAPI matches
    routes in registration order, so this literal path must come first or
    "suggest" would be swallowed as a {game_id} value.
    """
    console = console.upper()
    if console not in ("PS1", "PS2"):
        raise HTTPException(422, "console must be PS1 or PS2")
    if len(q.strip()) < 2:
        raise HTTPException(422, "q must be at least 2 characters")
    limit = max(1, min(limit, 50))
    return namesearch.suggest(db, console, q, limit=limit)


@router.get("/games/{game_id}", response_model=GameDetailOut)
def get_game(game_id: str, db: Session = Depends(get_db)):
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(404, "game not found")

    def n(model, extra=None) -> int:
        stmt = select(func.count()).select_from(model).where(model.game_id == game_id)
        if extra is not None:
            stmt = stmt.where(extra)
        return db.scalar(stmt) or 0

    counts = {
        "artwork": n(Artwork, Artwork.status == "approved"),
        "config": n(ConfigFile, ConfigFile.status == "approved"),
        "cheat": n(CheatFile, CheatFile.status == "approved"),
        "vmc": n(VmcFile, VmcFile.status == "approved"),
    }
    return GameDetailOut(**GameOut.model_validate(game).model_dump(), counts=counts)
