"""App-update source: reads GitHub Releases on this repo directly, so `gh
release create` (or the GitHub UI) is the entire publishing step - no jar
upload to the API, no separate release database.

A release is picked up as an update once it has a `.jar` asset attached.
Tag names may or may not have a leading "v" (v0.7.0 or 0.7.0); the desktop
app's version strings never do, so it is stripped for comparison.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass

from .config import get_settings

_GITHUB_API = "https://api.github.com"
_cache: dict[str, tuple[float, list[dict]]] = {}


@dataclass
class Release:
    version: str
    channel: str  # "stable" (not prerelease/draft) or "beta" (anything newest)
    notes: str | None
    published_at: str | None
    asset_name: str
    asset_size: int
    download_url: str


def _fetch_releases() -> list[dict]:
    settings = get_settings()
    now = time.monotonic()
    cached = _cache.get(settings.github_repo)
    if cached and now - cached[0] < settings.github_cache_seconds:
        return cached[1]

    url = f"{_GITHUB_API}/repos/{settings.github_repo}/releases"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    if settings.github_token:
        req.add_header("Authorization", f"Bearer {settings.github_token}")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310 (fixed https host)
            data = json.load(resp)
    except (urllib.error.URLError, TimeoutError):
        # Serve stale cache rather than nothing if GitHub is briefly unreachable.
        return cached[1] if cached else []

    _cache[settings.github_repo] = (now, data)
    return data


def get_latest_release(channel: str = "stable") -> Release | None:
    releases = _fetch_releases()
    for rel in releases:
        if rel.get("draft"):
            continue
        if channel == "stable" and rel.get("prerelease"):
            continue
        jar = next((a for a in rel.get("assets", []) if a["name"].endswith(".jar")), None)
        if jar is None:
            continue
        tag = rel.get("tag_name", "")
        return Release(
            version=tag[1:] if tag.startswith("v") else tag,
            channel="beta" if rel.get("prerelease") else "stable",
            notes=rel.get("body"),
            published_at=rel.get("published_at"),
            asset_name=jar["name"],
            asset_size=jar["size"],
            download_url=jar["browser_download_url"],
        )
    return None


def get_release_by_version(version: str) -> Release | None:
    for rel in _fetch_releases():
        tag = rel.get("tag_name", "")
        tag_version = tag[1:] if tag.startswith("v") else tag
        if tag_version != version:
            continue
        jar = next((a for a in rel.get("assets", []) if a["name"].endswith(".jar")), None)
        if jar is None:
            return None
        return Release(
            version=tag_version,
            channel="beta" if rel.get("prerelease") else "stable",
            notes=rel.get("body"),
            published_at=rel.get("published_at"),
            asset_name=jar["name"],
            asset_size=jar["size"],
            download_url=jar["browser_download_url"],
        )
    return None
