from __future__ import annotations

from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict

T = TypeVar("T")


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class GameOut(ORMModel):
    id: str
    console: str
    region: str
    title: str | None
    long_title: str | None
    created_at: datetime


class GameDetailOut(GameOut):
    counts: dict[str, int]


class ArtworkOut(ORMModel):
    id: int
    game_id: str
    kind: str
    variant: int
    mime: str
    sha256: str
    bytes: int
    width: int | None
    height: int | None
    source: str
    status: str


class ArtworkKindOut(BaseModel):
    game_id: str
    kind: str
    count: int
    variants: list[ArtworkOut]


class FileMetaOut(ORMModel):
    id: int
    game_id: str
    sha256: str
    source: str
    status: str
    created_at: datetime


class VmcOut(ORMModel):
    id: int
    game_id: str
    label: str
    description: str | None
    sha256: str
    bytes: int
    source: str
    status: str


class AppReleaseOut(BaseModel):
    """A GitHub Release, as served by app/github.py - see AppRelease's
    docstring in models.py for why this isn't a DB-backed model."""

    version: str
    channel: str
    notes: str | None
    published_at: str | None
    asset_name: str
    bytes: int
    download_url: str


class ToolReleaseOut(ORMModel):
    id: int
    name: str
    os: str
    arch: str
    version: str | None
    sha256: str
    bytes: int


class ReportIn(BaseModel):
    game_id: str | None = None
    file_type: str
    reason: str
    reporter: str | None = None


class Page(BaseModel, Generic[T]):
    items: list[T]
    total: int
    limit: int
    offset: int
