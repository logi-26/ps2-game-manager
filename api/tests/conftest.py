from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.blobstore import BlobStore
from app.db import get_db
from app.main import app
from app.models import Base, Game


@pytest.fixture()
def db_session() -> Iterator[Session]:
    """A fresh in-memory SQLite DB per test. StaticPool keeps the single
    connection alive across TestClient's threadpool hops - the standard
    pattern for testing FastAPI + SQLAlchemy with sqlite://."""
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)()
    try:
        yield session
    finally:
        session.close()
        engine.dispose()


@pytest.fixture()
def client(db_session: Session) -> Iterator[TestClient]:
    def _get_db_override() -> Iterator[Session]:
        yield db_session

    app.dependency_overrides[get_db] = _get_db_override
    try:
        with TestClient(app) as c:
            yield c
    finally:
        app.dependency_overrides.clear()


@pytest.fixture()
def blobstore(tmp_path) -> BlobStore:
    return BlobStore(tmp_path / "blobs")


@pytest.fixture()
def make_game(db_session: Session):
    """Insert (and commit) a Game row, returning it."""

    def _make(id: str = "SLUS_207.68", console: str = "PS2", region: str = "NTSCU", title: str | None = "Test Game") -> Game:
        game = Game(id=id, console=console, region=region, title=title)
        db_session.add(game)
        db_session.commit()
        db_session.refresh(game)
        return game

    return _make
