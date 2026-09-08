from __future__ import annotations

from app.models import Artwork, CheatFile, ConfigFile, VmcFile


def test_list_games_empty(client):
    resp = client.get("/v1/games")
    assert resp.status_code == 200
    body = resp.json()
    assert body == {"items": [], "total": 0, "limit": 100, "offset": 0}


def test_list_games_returns_inserted_rows(client, make_game):
    make_game(id="SLUS_207.68", console="PS2", title="Final Fantasy X")
    make_game(id="SCUS_941.67", console="PS1", title="Final Fantasy VII")

    resp = client.get("/v1/games")
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 2
    ids = [g["id"] for g in body["items"]]
    assert ids == ["SCUS_941.67", "SLUS_207.68"]  # ordered by id


def test_list_games_filters_by_console(client, make_game):
    make_game(id="SLUS_207.68", console="PS2")
    make_game(id="SCUS_941.67", console="PS1")

    resp = client.get("/v1/games", params={"console": "ps2"})  # lower-case on purpose
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["id"] == "SLUS_207.68"


def test_list_games_filters_by_region(client, make_game):
    make_game(id="SLUS_207.68", region="NTSCU")
    make_game(id="SLES_207.68", region="PAL")

    resp = client.get("/v1/games", params={"region": "pal"})
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["id"] == "SLES_207.68"


def test_list_games_search_matches_title_or_id(client, make_game):
    make_game(id="SLUS_207.68", title="Final Fantasy X")
    make_game(id="SCUS_941.67", title="Gran Turismo")

    by_title = client.get("/v1/games", params={"q": "fantasy"}).json()
    assert [g["id"] for g in by_title["items"]] == ["SLUS_207.68"]

    by_id = client.get("/v1/games", params={"q": "941.67"}).json()
    assert [g["id"] for g in by_id["items"]] == ["SCUS_941.67"]


def test_list_games_pagination(client, make_game):
    for i in range(5):
        make_game(id=f"GAME_{i}")

    resp = client.get("/v1/games", params={"limit": 2, "offset": 2})
    body = resp.json()
    assert body["total"] == 5
    assert body["limit"] == 2
    assert body["offset"] == 2
    assert len(body["items"]) == 2


def test_get_game_not_found(client):
    resp = client.get("/v1/games/NOPE_000.00")
    assert resp.status_code == 404


def test_get_game_returns_counts_of_approved_only(client, db_session, make_game):
    game = make_game()
    db_session.add_all(
        [
            Artwork(game_id=game.id, kind="COV", variant=0, mime="image/jpeg", sha256="a" * 64, bytes=1, status="approved"),
            Artwork(game_id=game.id, kind="COV", variant=1, mime="image/jpeg", sha256="b" * 64, bytes=1, status="pending"),
            ConfigFile(game_id=game.id, body="x", sha256="c" * 64, status="approved"),
            CheatFile(game_id=game.id, body="y", sha256="d" * 64, status="approved"),
            VmcFile(game_id=game.id, label="l1", sha256="e" * 64, bytes=1, status="approved"),
        ]
    )
    db_session.commit()

    resp = client.get(f"/v1/games/{game.id}")
    assert resp.status_code == 200
    body = resp.json()
    assert body["id"] == game.id
    assert body["counts"] == {"artwork": 1, "config": 1, "cheat": 1, "vmc": 1}
