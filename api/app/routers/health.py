from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..blobstore import get_blobstore
from ..db import get_db

router = APIRouter(tags=["meta"])


@router.get("/health")
def health(db: Session = Depends(get_db)) -> dict:
    db.execute(text("SELECT 1"))
    return {
        "status": "ok",
        "database": "ok",
        "blob_root": str(get_blobstore().root),
    }
