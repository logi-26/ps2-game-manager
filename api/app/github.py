"""App-update source: reads GitHub Releases on this repo directly, so `gh
release create` (or the GitHub UI) is the entire publishing step - no jar
upload to the API, no separate release database.

A release is picked up as an update once it has at least one recognised
per-platform zip (with its `.sha256` sidecar) attached:
  PS2GM-windows-<version>.zip    / .zip.sha256   -> platform "windows-appimage"
  PS2GM-jarbundle-<version>.zip  / .zip.sha256   -> platform "jar-bundle"

Tag names may or may not have a leading "v" (v0.7.0 or 0.7.0); the desktop
app's version strings never do, so it is stripped for comparison.

For local/offline development and testing (no real release needed), set
OPLAPI_GITHUB_FIXTURE_DIR to a directory containing a releases.json shaped
like GitHub's own `GET /repos/{repo}/releases` response, plus the zip/sha256
files it references by filename - see local-dev/README.md.
"""

from __future__ import annotations

import json
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from .config import get_settings

_GITHUB_API = "https://api.github.com"
_cache: dict[str, tuple[float, list[dict]]] = {}

# "PS2GM-windows-1.2.0.zip" / "PS2GM-jarbundle-1.2.0.zip"
_ASSET_RE = re.compile(r"^PS2GM-(windows|jarbundle)-(?P<version>.+)\.zip$")
_ASSET_KEY_TO_PLATFORM = {"windows": "windows-appimage", "jarbundle": "jar-bundle"}


@dataclass
class PlatformAsset:
    platform: str  # "windows-appimage" | "jar-bundle"
    asset_name: str
    size: int
    download_url: str
    checksum_asset_name: str
    checksum_download_url: str


@dataclass
class Release:
    version: str
    channel: str  # "stable" (not prerelease/draft) or "beta" (anything newest)
    notes: str | None
    published_at: str | None
    platforms: list[PlatformAsset]


def _fetch_releases() -> list[dict]:
    settings = get_settings()

    if settings.github_fixture_dir:
        return _fetch_releases_fixture(settings.github_fixture_dir)

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


def _fetch_releases_fixture(fixture_dir: Path) -> list[dict]:
    releases_json = fixture_dir / "releases.json"
    if not releases_json.is_file():
        return []
    return json.loads(releases_json.read_text(encoding="utf-8"))


def _platforms_for(rel: dict) -> list[PlatformAsset]:
    assets_by_name = {a["name"]: a for a in rel.get("assets", [])}
    platforms: list[PlatformAsset] = []
    for asset in rel.get("assets", []):
        match = _ASSET_RE.match(asset["name"])
        if match is None:
            continue
        checksum_name = asset["name"] + ".sha256"
        checksum_asset = assets_by_name.get(checksum_name)
        if checksum_asset is None:
            # A zip without its sidecar is unusable - skip this platform for
            # this release rather than serving an unverifiable download.
            continue
        platforms.append(
            PlatformAsset(
                platform=_ASSET_KEY_TO_PLATFORM[match.group(1)],
                asset_name=asset["name"],
                size=asset["size"],
                download_url=asset["browser_download_url"],
                checksum_asset_name=checksum_name,
                checksum_download_url=checksum_asset["browser_download_url"],
            )
        )
    return platforms


def _to_release(rel: dict) -> Release | None:
    platforms = _platforms_for(rel)
    if not platforms:
        return None
    tag = rel.get("tag_name", "")
    return Release(
        version=tag[1:] if tag.startswith("v") else tag,
        channel="beta" if rel.get("prerelease") else "stable",
        notes=rel.get("body"),
        published_at=rel.get("published_at"),
        platforms=platforms,
    )


def get_latest_release(channel: str = "stable") -> Release | None:
    for rel in _fetch_releases():
        if rel.get("draft"):
            continue
        if channel == "stable" and rel.get("prerelease"):
            continue
        release = _to_release(rel)
        if release is not None:
            return release
    return None


def get_release_by_version(version: str) -> Release | None:
    for rel in _fetch_releases():
        tag = rel.get("tag_name", "")
        tag_version = tag[1:] if tag.startswith("v") else tag
        if tag_version != version:
            continue
        return _to_release(rel)
    return None


def get_release_asset(version: str, platform: str) -> PlatformAsset | None:
    release = get_release_by_version(version)
    if release is None:
        return None
    return next((p for p in release.platforms if p.platform == platform), None)
