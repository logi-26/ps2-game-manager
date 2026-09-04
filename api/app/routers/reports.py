"""Bad-file reports (replaces the TCP REPORT command)."""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import page_params, require_write_key
from ..models import FileReport
from ..schemas import Page, ReportIn

router = APIRouter(tags=["reports"])


@router.post("/reports", status_code=201, dependencies=[Depends(require_write_key)])
def create_report(payload: ReportIn, db: Session = Depends(get_db)):
    row = FileReport(
        game_id=payload.game_id,
        file_type=payload.file_type,
        reason=payload.reason,
        reporter=payload.reporter,
    )
    db.add(row)
    db.commit()
    return {"id": row.id}


@router.get("/reports", response_model=Page[dict], dependencies=[Depends(require_write_key)])
def list_reports(
    db: Session = Depends(get_db),
    paging: tuple[int, int] = Depends(page_params),
    resolved: bool | None = None,
):
    limit, offset = paging
    stmt = select(FileReport)
    if resolved is not None:
        stmt = stmt.where(FileReport.resolved == resolved)
    rows = db.scalars(stmt.order_by(FileReport.created_at.desc()).limit(limit).offset(offset)).all()
    items = [
        {
            "id": r.id,
            "game_id": r.game_id,
            "file_type": r.file_type,
            "reason": r.reason,
            "reporter": r.reporter,
            "resolved": r.resolved,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        }
        for r in rows
    ]
    return Page(items=items, total=len(items), limit=limit, offset=offset)
