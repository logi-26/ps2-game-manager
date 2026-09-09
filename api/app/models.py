from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

# Enumerated string values kept as plain checks in the app layer rather than DB
# enums, so SQLite and Postgres behave identically.
CONSOLES = ("PS1", "PS2")
REGIONS = ("NTSCU", "PAL", "NTSCJ")
ART_KINDS = ("COV", "COV2", "ICO", "SCR", "SCR2", "BG", "LAB", "LGO")
SOURCES = ("official", "user_upload")
STATUSES = ("approved", "pending", "rejected")


class Base(DeclarativeBase):
    pass


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class Game(Base, TimestampMixin):
    __tablename__ = "game"

    # the disc serial, e.g. "SLUS_207.68"
    id: Mapped[str] = mapped_column(String(16), primary_key=True)
    console: Mapped[str] = mapped_column(String(3), nullable=False)
    region: Mapped[str] = mapped_column(String(8), nullable=False)
    title: Mapped[str | None] = mapped_column(String(255))
    long_title: Mapped[str | None] = mapped_column(String(512))

    artwork: Mapped[list[Artwork]] = relationship(back_populates="game", cascade="all, delete-orphan")
    configs: Mapped[list[ConfigFile]] = relationship(back_populates="game", cascade="all, delete-orphan")
    cheats: Mapped[list[CheatFile]] = relationship(back_populates="game", cascade="all, delete-orphan")
    vmcs: Mapped[list[VmcFile]] = relationship(back_populates="game", cascade="all, delete-orphan")


class Artwork(Base, TimestampMixin):
    __tablename__ = "artwork"
    __table_args__ = (
        UniqueConstraint("game_id", "kind", "variant", "source", name="uq_artwork_slot"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("game.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(8), nullable=False)  # COV, ICO, SCR, BG, ...
    variant: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    mime: Mapped[str] = mapped_column(String(32), nullable=False)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    width: Mapped[int | None] = mapped_column(Integer)
    height: Mapped[int | None] = mapped_column(Integer)
    source: Mapped[str] = mapped_column(String(16), nullable=False, default="official")
    status: Mapped[str] = mapped_column(String(16), nullable=False, default="approved", index=True)
    submitted_by: Mapped[str | None] = mapped_column(String(64))

    game: Mapped[Game] = relationship(back_populates="artwork")


class _TextFile(Base, TimestampMixin):
    __abstract__ = True

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    source: Mapped[str] = mapped_column(String(16), nullable=False, default="official")
    status: Mapped[str] = mapped_column(String(16), nullable=False, default="approved", index=True)
    submitted_by: Mapped[str | None] = mapped_column(String(64))


class ConfigFile(_TextFile):
    __tablename__ = "config_file"
    __table_args__ = (UniqueConstraint("game_id", "source", name="uq_config_slot"),)

    game_id: Mapped[str] = mapped_column(ForeignKey("game.id", ondelete="CASCADE"), index=True)
    cfg_version: Mapped[str | None] = mapped_column(String(8))
    game: Mapped[Game] = relationship(back_populates="configs")


class CheatFile(_TextFile):
    __tablename__ = "cheat_file"
    __table_args__ = (UniqueConstraint("game_id", "source", name="uq_cheat_slot"),)

    game_id: Mapped[str] = mapped_column(ForeignKey("game.id", ondelete="CASCADE"), index=True)
    game: Mapped[Game] = relationship(back_populates="cheats")


class VmcFile(Base, TimestampMixin):
    __tablename__ = "vmc_file"
    __table_args__ = (UniqueConstraint("game_id", "label", "source", name="uq_vmc_slot"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("game.id", ondelete="CASCADE"), index=True)
    label: Mapped[str] = mapped_column(String(128), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[str] = mapped_column(String(16), nullable=False, default="official")
    status: Mapped[str] = mapped_column(String(16), nullable=False, default="approved", index=True)
    submitted_by: Mapped[str | None] = mapped_column(String(64))

    game: Mapped[Game] = relationship(back_populates="vmcs")


class AppRelease(Base, TimestampMixin):
    """Unused: app updates are served live from GitHub Releases (see
    app/github.py + routers/releases.py) rather than mirrored into the DB -
    cutting a release on GitHub *is* publishing it. Table kept, empty, as a
    landing spot if a self-hosted fallback is ever needed."""

    __tablename__ = "app_release"

    version: Mapped[str] = mapped_column(String(32), primary_key=True)
    channel: Mapped[str] = mapped_column(String(16), nullable=False, default="beta", index=True)
    notes: Mapped[str | None] = mapped_column(Text)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class GameListEntry(Base, TimestampMixin):
    """A name -> game-ID reference row from a user-supplied catalogue list
    (see scripts/import_gamelists.py), used only to power GET /games/suggest's
    fuzzy name matching (app/namesearch.py). Deliberately unrelated to Game -
    keeping this separate means /games keeps meaning "a game we have real
    content for", not "a game we've merely heard the name of".

    Unique on (console, region, game_id, title) rather than just
    (console, region, game_id): the real source lists have a real, if small,
    error rate where the same ID is listed under two genuinely different
    titles (confirmed - e.g. one ID claimed by both "Dynasty Tactics 2" and
    "007 - Everything or Nothing" in one region file). Since nothing here
    can tell which is right, both stay as separate candidate rows rather
    than one being silently dropped - harmless for fuzzy matching (a human
    reviews every suggestion), and title in the key still collapses exact
    duplicate lines (the same id+title repeated verbatim, also seen in the
    raw data) down to one row."""

    __tablename__ = "game_list_entry"
    __table_args__ = (
        UniqueConstraint("console", "region", "game_id", "title", name="uq_gle_console_region_id_title"),
        Index("ix_gle_console_title", "console", "title"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    console: Mapped[str] = mapped_column(String(3), nullable=False)
    region: Mapped[str] = mapped_column(String(8), nullable=False)
    game_id: Mapped[str] = mapped_column(String(16), nullable=False)
    title: Mapped[str] = mapped_column(String(255), nullable=False)


class ToolRelease(Base, TimestampMixin):
    __tablename__ = "tool_release"
    __table_args__ = (UniqueConstraint("name", "os", "arch", name="uq_tool_slot"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(32), nullable=False, index=True)  # cue2pops, hdl_dump, genvmc
    os: Mapped[str] = mapped_column(String(16), nullable=False)  # windows, linux
    arch: Mapped[str] = mapped_column(String(16), nullable=False, default="any")
    version: Mapped[str | None] = mapped_column(String(32))
    sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    bytes: Mapped[int] = mapped_column(Integer, nullable=False)


