from __future__ import annotations

import pytest

from app import namesearch


@pytest.fixture(autouse=True)
def _clear_namesearch_cache():
    namesearch.clear_cache()
    yield
    namesearch.clear_cache()


def test_normalize_lowercases():
    assert namesearch.normalize_title("Metal Gear Solid") == "metal gear solid"


def test_normalize_ampersand_becomes_and():
    assert namesearch.normalize_title("Guns & Roses") == "guns and roses"


def test_normalize_strips_the_as_a_whole_word():
    assert namesearch.normalize_title("The Getaway") == "getaway"
    # "the" inside another word (e.g. "Theme") must not be stripped
    assert namesearch.normalize_title("Theme Park") == "theme park"


def test_normalize_strips_punctuation_and_collapses_whitespace():
    assert namesearch.normalize_title("007 - GoldenEye: Rogue Agent!!") == "007 goldeneye rogue agent"


def test_normalize_and_and_ampersand_variants_match():
    assert namesearch.normalize_title("Rock and Roll Racing") == namesearch.normalize_title("Rock & Roll Racing")


def test_suggest_finds_exact_match(db_session, make_game_list_entry):
    make_game_list_entry(game_id="SLUS_005.94", console="PS1", region="NTSCU", title="Metal Gear Solid")

    results = namesearch.suggest(db_session, "PS1", "Metal Gear Solid")

    assert len(results) == 1
    assert results[0]["game_id"] == "SLUS_005.94"
    assert results[0]["score"] == 100


def test_suggest_tolerates_case_and_punctuation_differences(db_session, make_game_list_entry):
    make_game_list_entry(game_id="SLES_050.32", console="PS2", region="PAL", title="AC/DC Live: Rock Band")

    results = namesearch.suggest(db_session, "PS2", "acdc live rock band")

    assert any(r["game_id"] == "SLES_050.32" for r in results)


def test_suggest_returns_every_candidate_above_threshold_never_just_one(db_session, make_game_list_entry):
    # Same title, two different IDs (e.g. reissue/region variant) - both must come back.
    make_game_list_entry(game_id="SLES_520.05", console="PS2", region="PAL", title="007 - Everything or Nothing")
    make_game_list_entry(game_id="SLES_520.46", console="PS2", region="PAL", title="007 - Everything or Nothing")

    results = namesearch.suggest(db_session, "PS2", "007 Everything or Nothing")

    ids = {r["game_id"] for r in results}
    assert ids == {"SLES_520.05", "SLES_520.46"}


def test_suggest_respects_score_threshold(db_session, make_game_list_entry):
    make_game_list_entry(game_id="SLUS_005.94", console="PS1", region="NTSCU", title="Metal Gear Solid")

    results = namesearch.suggest(db_session, "PS1", "Completely Unrelated Racing Game", min_score=60)

    assert results == []


def test_suggest_respects_limit(db_session, make_game_list_entry):
    for i in range(5):
        make_game_list_entry(game_id=f"SLUS_000.0{i}", console="PS2", region="NTSCU", title=f"Ace Combat {i}")

    results = namesearch.suggest(db_session, "PS2", "Ace Combat", limit=2)

    assert len(results) <= 2


def test_suggest_only_matches_within_the_requested_console(db_session, make_game_list_entry):
    make_game_list_entry(game_id="SLUS_005.94", console="PS1", region="NTSCU", title="Metal Gear Solid")

    results = namesearch.suggest(db_session, "PS2", "Metal Gear Solid")

    assert results == []


def test_suggest_caches_across_calls_within_a_process(db_session, make_game_list_entry):
    make_game_list_entry(game_id="SLUS_005.94", console="PS1", region="NTSCU", title="Metal Gear Solid")
    first = namesearch.suggest(db_session, "PS1", "Metal Gear Solid")
    assert len(first) == 1

    # A new entry added after the first call must NOT appear until the cache is cleared -
    # this is the documented "restart after reimport" behaviour, verified directly.
    make_game_list_entry(game_id="SLUS_007.76", console="PS1", region="NTSCU", title="Metal Gear Solid VR Missions")
    still_cached_ids = {r["game_id"] for r in namesearch.suggest(db_session, "PS1", "Metal Gear Solid VR Missions")}
    assert "SLUS_007.76" not in still_cached_ids

    namesearch.clear_cache()
    after_clear_ids = {r["game_id"] for r in namesearch.suggest(db_session, "PS1", "Metal Gear Solid VR Missions")}
    assert "SLUS_007.76" in after_clear_ids
