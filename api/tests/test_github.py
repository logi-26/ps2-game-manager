from __future__ import annotations

import json

from app import github


def _gh_release(tag="1.2.0", prerelease=False, draft=False, assets=None):
    return {
        "tag_name": tag,
        "prerelease": prerelease,
        "draft": draft,
        "published_at": "2026-01-01T00:00:00Z",
        "body": "notes",
        "assets": assets or [],
    }


def _asset(name, size=123):
    return {"name": name, "size": size, "browser_download_url": f"https://example.invalid/{name}"}


def test_recognises_both_platforms_with_sidecars():
    rel = _gh_release(
        assets=[
            _asset("PS2GM-windows-1.2.0.zip"),
            _asset("PS2GM-windows-1.2.0.zip.sha256"),
            _asset("PS2GM-jarbundle-1.2.0.zip"),
            _asset("PS2GM-jarbundle-1.2.0.zip.sha256"),
        ]
    )
    release = github._to_release(rel)
    assert release is not None
    assert {p.platform for p in release.platforms} == {"windows-appimage", "jar-bundle"}


def test_zip_without_sidecar_is_skipped():
    rel = _gh_release(assets=[_asset("PS2GM-windows-1.2.0.zip")])  # no .sha256
    release = github._to_release(rel)
    assert release is None


def test_release_with_no_recognised_assets_is_none():
    rel = _gh_release(assets=[_asset("unrelated-file.txt")])
    assert github._to_release(rel) is None


def test_v_prefixed_tag_is_stripped():
    rel = _gh_release(
        tag="v2.0.0",
        assets=[_asset("PS2GM-jarbundle-2.0.0.zip"), _asset("PS2GM-jarbundle-2.0.0.zip.sha256")],
    )
    release = github._to_release(rel)
    assert release.version == "2.0.0"


def test_get_latest_release_skips_drafts_and_prereleases_on_stable(monkeypatch):
    releases = [
        _gh_release(tag="2.0.0-beta", prerelease=True, assets=[_asset("PS2GM-jarbundle-2.0.0-beta.zip"), _asset("PS2GM-jarbundle-2.0.0-beta.zip.sha256")]),
        _gh_release(tag="1.9.0", draft=True, assets=[_asset("PS2GM-jarbundle-1.9.0.zip"), _asset("PS2GM-jarbundle-1.9.0.zip.sha256")]),
        _gh_release(tag="1.5.0", assets=[_asset("PS2GM-jarbundle-1.5.0.zip"), _asset("PS2GM-jarbundle-1.5.0.zip.sha256")]),
    ]
    monkeypatch.setattr(github, "_fetch_releases", lambda: releases)

    release = github.get_latest_release("stable")
    assert release.version == "1.5.0"


def test_get_release_asset_returns_matching_platform(monkeypatch):
    rel = _gh_release(
        assets=[
            _asset("PS2GM-windows-1.2.0.zip"),
            _asset("PS2GM-windows-1.2.0.zip.sha256"),
        ]
    )
    monkeypatch.setattr(github, "_fetch_releases", lambda: [rel])

    asset = github.get_release_asset("1.2.0", "windows-appimage")
    assert asset is not None
    assert asset.asset_name == "PS2GM-windows-1.2.0.zip"

    assert github.get_release_asset("1.2.0", "jar-bundle") is None
    assert github.get_release_asset("9.9.9", "windows-appimage") is None


def test_fixture_dir_serves_local_releases_json(tmp_path, monkeypatch):
    (tmp_path / "releases.json").write_text(
        json.dumps(
            [
                _gh_release(
                    tag="0.0.1-test",
                    assets=[
                        _asset("PS2GM-jarbundle-0.0.1-test.zip"),
                        _asset("PS2GM-jarbundle-0.0.1-test.zip.sha256"),
                    ],
                )
            ]
        ),
        encoding="utf-8",
    )
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "github_fixture_dir", tmp_path)

    release = github.get_latest_release("stable")
    assert release is not None
    assert release.version == "0.0.1-test"


def test_fixture_dir_missing_releases_json_returns_empty(tmp_path, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "github_fixture_dir", tmp_path)
    assert github.get_latest_release("stable") is None
