from __future__ import annotations

import app.routers.files as files_router
from app.models import CheatFile, ConfigFile, VmcFile


# ---- config ------------------------------------------------------------------
def test_get_config_404_when_missing(client):
    resp = client.get("/v1/games/NOPE/config")
    assert resp.status_code == 404


def test_get_config_returns_body_with_etag(client, db_session, make_game):
    game = make_game()
    db_session.add(ConfigFile(game_id=game.id, body="VMode=NTSC", sha256="a" * 64, status="approved"))
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}/config")
    assert resp.status_code == 200
    assert resp.text == "VMode=NTSC"
    assert resp.headers["ETag"] == '"' + "a" * 64 + '"'


def test_get_config_304_on_matching_etag(client, db_session, make_game):
    game = make_game()
    db_session.add(ConfigFile(game_id=game.id, body="VMode=NTSC", sha256="a" * 64, status="approved"))
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}/config", headers={"If-None-Match": '"' + "a" * 64 + '"'})
    assert resp.status_code == 304


def test_get_config_ignores_non_approved(client, db_session, make_game):
    game = make_game()
    db_session.add(ConfigFile(game_id=game.id, body="x", sha256="a" * 64, status="pending"))
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}/config")
    assert resp.status_code == 404


def test_list_configs_filters_by_console(client, db_session, make_game):
    ps2 = make_game(id="SLUS_207.68", console="PS2")
    ps1 = make_game(id="SCUS_941.67", console="PS1")
    db_session.add_all(
        [
            ConfigFile(game_id=ps2.id, body="x", sha256="a" * 64, status="approved"),
            ConfigFile(game_id=ps1.id, body="y", sha256="b" * 64, status="approved"),
        ]
    )
    db_session.commit()

    resp = client.get("/v1/configs", params={"console": "PS2"})
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["game_id"] == ps2.id


# ---- cheats ------------------------------------------------------------------
def test_get_cheats_returns_body(client, db_session, make_game):
    game = make_game()
    db_session.add(CheatFile(game_id=game.id, body="9000000 00000063", sha256="a" * 64, status="approved"))
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}/cheats")
    assert resp.status_code == 200
    assert resp.text == "9000000 00000063"


def test_get_cheats_404_when_missing(client, make_game):
    game = make_game()
    resp = client.get(f"/v1/games/{game.id}/cheats")
    assert resp.status_code == 404


def test_list_cheats_pagination(client, db_session, make_game):
    for i in range(3):
        game = make_game(id=f"GAME_{i}")
        db_session.add(CheatFile(game_id=game.id, body="x", sha256=f"{i}" * 64, status="approved"))
    db_session.commit()

    resp = client.get("/v1/cheats", params={"limit": 2})
    body = resp.json()
    assert body["total"] == 3
    assert len(body["items"]) == 2


# ---- vmc -----------------------------------------------------------------
def test_game_vmc_only_returns_approved(client, db_session, make_game):
    game = make_game()
    db_session.add_all(
        [
            VmcFile(game_id=game.id, label="a_ps2gm_0", sha256="a" * 64, bytes=1, status="approved"),
            VmcFile(game_id=game.id, label="a_ps2gm_1", sha256="b" * 64, bytes=1, status="pending"),
        ]
    )
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}/vmc")
    body = resp.json()
    assert len(body) == 1
    assert body[0]["label"] == "a_ps2gm_0"


def test_game_vmc_bytes_404_when_missing(client, make_game):
    game = make_game()
    resp = client.get(f"/v1/games/{game.id}/vmc/999")
    assert resp.status_code == 404


def test_game_vmc_bytes_serves_file(client, db_session, make_game, blobstore, monkeypatch):
    monkeypatch.setattr(files_router, "get_blobstore", lambda: blobstore)
    sha, _ = blobstore.put_bytes(b"memory-card-bytes")
    game = make_game()
    vmc = VmcFile(game_id=game.id, label="a_ps2gm_0", sha256=sha, bytes=len(b"memory-card-bytes"), status="approved")
    db_session.add(vmc)
    db_session.commit()
    db_session.refresh(vmc)

    resp = client.get(f"/v1/games/{game.id}/vmc/{vmc.id}")
    assert resp.status_code == 200
    assert resp.content == b"memory-card-bytes"
    assert resp.headers["ETag"] == f'"{sha}"'


def test_list_vmc_filters_by_console(client, db_session, make_game):
    ps2 = make_game(id="SLUS_207.68", console="PS2")
    ps1 = make_game(id="SCUS_941.67", console="PS1")
    db_session.add_all(
        [
            VmcFile(game_id=ps2.id, label="l1", sha256="a" * 64, bytes=1, status="approved"),
            VmcFile(game_id=ps1.id, label="l2", sha256="b" * 64, bytes=1, status="approved"),
        ]
    )
    db_session.commit()

    resp = client.get("/v1/vmc", params={"console": "ps1"})
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["game_id"] == ps1.id
