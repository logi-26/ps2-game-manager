from __future__ import annotations

import app.routers.releases as releases_router
from app import github
from app.models import ToolRelease


def _release(version="1.2.0", channel="stable") -> github.Release:
    return github.Release(
        version=version,
        channel=channel,
        notes="notes",
        published_at="2026-01-01T00:00:00Z",
        asset_name="PS2GM.jar",
        asset_size=123,
        download_url="https://example.invalid/PS2GM.jar",
    )


def test_app_latest_returns_release(client, monkeypatch):
    monkeypatch.setattr(github, "get_latest_release", lambda channel="stable": _release())

    resp = client.get("/v1/app/latest")
    assert resp.status_code == 200
    body = resp.json()
    assert body["version"] == "1.2.0"
    assert body["download_url"] == "https://example.invalid/PS2GM.jar"


def test_app_latest_404_when_none(client, monkeypatch):
    monkeypatch.setattr(github, "get_latest_release", lambda channel="stable": None)

    resp = client.get("/v1/app/latest")
    assert resp.status_code == 404


def test_app_download_redirects(client, monkeypatch):
    monkeypatch.setattr(github, "get_release_by_version", lambda version: _release(version=version))

    resp = client.get("/v1/app/releases/1.2.0/download", follow_redirects=False)
    assert resp.status_code == 307
    assert resp.headers["location"] == "https://example.invalid/PS2GM.jar"


def test_app_download_404_for_unknown_version(client, monkeypatch):
    monkeypatch.setattr(github, "get_release_by_version", lambda version: None)

    resp = client.get("/v1/app/releases/9.9.9/download", follow_redirects=False)
    assert resp.status_code == 404


def test_tool_download_serves_file(client, db_session, blobstore, monkeypatch):
    monkeypatch.setattr(releases_router, "get_blobstore", lambda: blobstore)
    sha, _ = blobstore.put_bytes(b"exe-bytes")
    db_session.add(
        ToolRelease(name="cue2pops", os="windows", arch="any", sha256=sha, bytes=len(b"exe-bytes"))
    )
    db_session.commit()

    resp = client.get("/v1/tools/cue2pops", params={"os": "windows"})
    assert resp.status_code == 200
    assert resp.content == b"exe-bytes"
    assert resp.headers["content-disposition"].endswith('cue2pops.exe"')


def test_tool_download_404_for_unknown_combo(client):
    resp = client.get("/v1/tools/cue2pops", params={"os": "linux"})
    assert resp.status_code == 404


def test_list_tools(client, db_session):
    db_session.add_all(
        [
            ToolRelease(name="cue2pops", os="windows", sha256="a" * 64, bytes=1),
            ToolRelease(name="hdl_dump", os="linux", sha256="b" * 64, bytes=1),
        ]
    )
    db_session.commit()

    resp = client.get("/v1/tools")
    assert resp.status_code == 200
    names = [t["name"] for t in resp.json()]
    assert names == ["cue2pops", "hdl_dump"]
