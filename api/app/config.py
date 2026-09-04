from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Project root = the api/ directory (this file is api/app/config.py)
API_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="OPLAPI_", env_file=str(API_DIR / ".env"), extra="ignore"
    )

    # sqlite for dev; set OPLAPI_DATABASE_URL=postgresql+psycopg://... for Postgres
    database_url: str = f"sqlite:///{API_DIR / 'var' / 'dev.db'}"
    blob_root: Path = API_DIR / "var" / "blobs"

    page_size_default: int = 100
    page_size_max: int = 1000

    # app-update source: GitHub Releases on this repo (see app/github.py).
    # An unauthenticated client is capped at 60 req/hr by GitHub; the module
    # caches responses, and a token (no special scope needed) raises that to 5000/hr.
    github_repo: str = "logi-26/ps2-game-manager"
    github_token: str | None = None
    github_cache_seconds: int = 300

    # Off by default: a solo desktop client doing a batch-download run can
    # easily fire several thousand requests in a few minutes (every art kind
    # for every game), which a naive limit would mistake for abuse. Only turn
    # this on if the API is reachable beyond localhost, and size it generously
    # (a few thousand/min) so normal batch operations aren't affected.
    rate_limit_per_minute: int | None = None


@lru_cache
def get_settings() -> Settings:
    s = Settings()
    s.blob_root.mkdir(parents=True, exist_ok=True)
    if s.database_url.startswith("sqlite:///"):
        Path(s.database_url.removeprefix("sqlite:///")).parent.mkdir(parents=True, exist_ok=True)
    return s
