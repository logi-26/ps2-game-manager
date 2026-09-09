from __future__ import annotations

import pytest

from app import namesearch


@pytest.fixture(autouse=True)
def _clear_namesearch_cache():
    namesearch.clear_cache()
    yield
    namesearch.clear_cache()


def test_suggest_returns_ranked_candidates(client, make_game_list_entry):
    make_game_list_entry(game_id="SLUS_005.94", console="PS1", region="NTSCU", title="Metal Gear Solid")

    resp = client.get("/v1/games/suggest", params={"console": "PS1", "q": "metal gear solid"})

    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["game_id"] == "SLUS_005.94"
    assert body[0]["score"] == 100
    assert body[0]["region"] == "NTSCU"


def test_suggest_never_collapses_multiple_ids_for_one_name(client, make_game_list_entry):
    make_game_list_entry(game_id="SLES_520.05", console="PS2", region="PAL", title="007 - Everything or Nothing")
    make_game_list_entry(game_id="SLES_520.46", console="PS2", region="PAL", title="007 - Everything or Nothing")

    resp = client.get("/v1/games/suggest", params={"console": "PS2", "q": "007 Everything or Nothing"})

    ids = {row["game_id"] for row in resp.json()}
    assert ids == {"SLES_520.05", "SLES_520.46"}


def test_suggest_requires_a_valid_console(client):
    resp = client.get("/v1/games/suggest", params={"console": "PS3", "q": "anything"})
    assert resp.status_code == 422


def test_suggest_requires_at_least_two_characters(client):
    resp = client.get("/v1/games/suggest", params={"console": "PS2", "q": "a"})
    assert resp.status_code == 422


def test_suggest_caps_the_limit_at_50(client, make_game_list_entry):
    for i in range(3):
        make_game_list_entry(game_id=f"SLUS_000.0{i}", console="PS2", region="NTSCU", title="Ace Combat")

    resp = client.get("/v1/games/suggest", params={"console": "PS2", "q": "Ace Combat", "limit": 500})

    assert resp.status_code == 200
    assert len(resp.json()) <= 50


def test_suggest_route_is_not_shadowed_by_the_game_id_path_param(client):
    # /games/suggest must resolve to the suggest handler, not GET /games/{game_id}
    # with game_id="suggest" (which would 404 with "game not found" instead of a
    # 422 for the missing required "q"/"console" query params).
    resp = client.get("/v1/games/suggest")
    assert resp.status_code == 422
