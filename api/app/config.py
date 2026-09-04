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

    # when set, write endpoints require this value in an X-API-Key header
    write_api_key: str | None = None

    page_size_default: int = 100
    page_size_max: int = 1000

    # cap on inbound uploads (bytes)
    max_upload_bytes: int = 32 * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    s = Settings()
    s.blob_root.mkdir(parents=True, exist_ok=True)
    if s.database_url.startswith("sqlite:///"):
        Path(s.database_url.removeprefix("sqlite:///")).parent.mkdir(parents=True, exist_ok=True)
    return s
