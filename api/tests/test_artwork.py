from __future__ import annotations

import app.routers.artwork as artwork_router
from app.models import Artwork


def _add_artwork(db_session, game_id, kind="COV", variant=0, status="approved", sha256="a" * 64, mime="image/jpeg", bytes_=3):
    row = Artwork(
        game_id=game_id, kind=kind, variant=variant, mime=mime, sha256=sha256, bytes=bytes_, status=status
    )
    db_session.add(row)
    db_session.commit()
    return row


def test_game_artwork_only_returns_approved(client, db_session, make_game):
    game = make_game()
    _add_artwork(db_session, game.id, variant=0, status="approved", sha256="a" * 64)
    _add_artwork(db_session, game.id, variant=1, status="pending", sha256="b" * 64)

    resp = client.get(f"/v1/games/{game.id}/artwork")
    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["variant"] == 0


def test_game_artwork_kind_groups_variants(client, db_session, make_game):
    game = make_game()
    _add_artwork(db_session, game.id, kind="COV", variant=0, sha256="a" * 64)
    _add_artwork(db_session, game.id, kind="COV", variant=1, sha256="b" * 64)
    _add_artwork(db_session, game.id, kind="SCR", variant=0, sha256="c" * 64)

    resp = client.get(f"/v1/games/{game.id}/artwork/cov")  # lower-case kind
    assert resp.status_code == 200
    body = resp.json()
    assert body["kind"] == "COV"
    assert body["count"] == 2
    assert [v["variant"] for v in body["variants"]] == [0, 1]


def test_artwork_bytes_404_when_missing(client):
    resp = client.get("/v1/games/NOPE/artwork/COV/0")
    assert resp.status_code == 404


def test_artwork_bytes_serves_file_with_etag(client, db_session, make_game, blobstore, monkeypatch):
    monkeypatch.setattr(artwork_router, "get_blobstore", lambda: blobstore)
    sha, _ = blobstore.put_bytes(b"fake-jpeg-bytes")
    game = make_game()
    _add_artwork(db_session, game.id, sha256=sha, bytes_=len(b"fake-jpeg-bytes"))

    resp = client.get(f"/v1/games/{game.id}/artwork/COV/0")
    assert resp.status_code == 200
    assert resp.content == b"fake-jpeg-bytes"
    assert resp.headers["ETag"] == f'"{sha}"'


def test_artwork_bytes_returns_304_on_matching_etag(client, db_session, make_game, blobstore, monkeypatch):
    monkeypatch.setattr(artwork_router, "get_blobstore", lambda: blobstore)
    sha, _ = blobstore.put_bytes(b"fake-jpeg-bytes")
    game = make_game()
    _add_artwork(db_session, game.id, sha256=sha, bytes_=len(b"fake-jpeg-bytes"))

    resp = client.get(f"/v1/games/{game.id}/artwork/COV/0", headers={"If-None-Match": f'"{sha}"'})
    assert resp.status_code == 304


def test_artwork_bytes_410_when_blob_missing_from_store(client, db_session, make_game, blobstore, monkeypatch):
    monkeypatch.setattr(artwork_router, "get_blobstore", lambda: blobstore)
    game = make_game()
    _add_artwork(db_session, game.id, sha256="f" * 64)  # never written to the blobstore

    resp = client.get(f"/v1/games/{game.id}/artwork/COV/0")
    assert resp.status_code == 410


def test_list_artwork_filters_by_console_and_kind(client, db_session, make_game):
    ps2 = make_game(id="SLUS_207.68", console="PS2")
    ps1 = make_game(id="SCUS_941.67", console="PS1")
    _add_artwork(db_session, ps2.id, kind="COV", sha256="a" * 64)
    _add_artwork(db_session, ps2.id, kind="SCR", sha256="b" * 64)
    _add_artwork(db_session, ps1.id, kind="COV", sha256="c" * 64)

    resp = client.get("/v1/artwork", params={"console": "PS2", "kind": "cov"})
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["game_id"] == ps2.id
