from __future__ import annotations

import app.routers.releases as releases_router
from app import github
from app.models import ToolRelease


def _platform(platform="windows-appimage") -> github.PlatformAsset:
    name = "PS2GM-windows-1.2.0.zip" if platform == "windows-appimage" else "PS2GM-jarbundle-1.2.0.zip"
    return github.PlatformAsset(
        platform=platform,
        asset_name=name,
        size=123,
        download_url=f"https://example.invalid/{name}",
        checksum_asset_name=name + ".sha256",
        checksum_download_url=f"https://example.invalid/{name}.sha256",
    )


def _release(version="1.2.0", channel="stable", platforms=None) -> github.Release:
    return github.Release(
        version=version,
        channel=channel,
        notes="notes",
        published_at="2026-01-01T00:00:00Z",
        platforms=platforms if platforms is not None else [_platform("windows-appimage"), _platform("jar-bundle")],
    )


def test_app_latest_returns_release(client, monkeypatch):
    monkeypatch.setattr(github, "get_latest_release", lambda channel="stable": _release())

    resp = client.get("/v1/app/latest")
    assert resp.status_code == 200
    body = resp.json()
    assert body["version"] == "1.2.0"
    assert {p["platform"] for p in body["platforms"]} == {"windows-appimage", "jar-bundle"}
    assert "download_url" not in body["platforms"][0]


def test_app_latest_404_when_none(client, monkeypatch):
    monkeypatch.setattr(github, "get_latest_release", lambda channel="stable": None)

    resp = client.get("/v1/app/latest")
    assert resp.status_code == 404


def test_app_download_redirects(client, monkeypatch):
    monkeypatch.setattr(
        github, "get_release_asset", lambda version, platform: _platform(platform)
    )

    resp = client.get(
        "/v1/app/releases/1.2.0/download", params={"platform": "windows-appimage"}, follow_redirects=False
    )
    assert resp.status_code == 307
    assert resp.headers["location"] == "https://example.invalid/PS2GM-windows-1.2.0.zip"


def test_app_download_404_for_unknown_version(client, monkeypatch):
    monkeypatch.setattr(github, "get_release_asset", lambda version, platform: None)

    resp = client.get(
        "/v1/app/releases/9.9.9/download", params={"platform": "windows-appimage"}, follow_redirects=False
    )
    assert resp.status_code == 404


def test_app_download_404_for_platform_with_no_build(client, monkeypatch):
    # A release that only shipped one platform's zip - the other platform is
    # simply absent, never a server error.
    monkeypatch.setattr(github, "get_release_asset", lambda version, platform: None)

    resp = client.get(
        "/v1/app/releases/1.2.0/download", params={"platform": "jar-bundle"}, follow_redirects=False
    )
    assert resp.status_code == 404


def test_app_checksum_redirects(client, monkeypatch):
    monkeypatch.setattr(
        github, "get_release_asset", lambda version, platform: _platform(platform)
    )

    resp = client.get(
        "/v1/app/releases/1.2.0/checksum", params={"platform": "jar-bundle"}, follow_redirects=False
    )
    assert resp.status_code == 307
    assert resp.headers["location"] == "https://example.invalid/PS2GM-jarbundle-1.2.0.zip.sha256"


def test_app_fixture_asset_404_when_fixture_mode_off(client):
    resp = client.get("/v1/app/fixtures/PS2GM-windows-1.2.0.zip")
    assert resp.status_code == 404


def test_app_fixture_asset_serves_file(client, tmp_path, monkeypatch):
    from app.config import get_settings

    (tmp_path / "PS2GM-windows-1.2.0.zip").write_bytes(b"zip-bytes")
    monkeypatch.setattr(get_settings(), "github_fixture_dir", tmp_path)

    resp = client.get("/v1/app/fixtures/PS2GM-windows-1.2.0.zip")
    assert resp.status_code == 200
    assert resp.content == b"zip-bytes"


def test_app_fixture_asset_rejects_path_escape(client, tmp_path, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "github_fixture_dir", tmp_path)

    resp = client.get("/v1/app/fixtures/..%2F..%2Fsecrets.txt")
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
