"""Fuzzy game-name -> ID matching, backed by GameListEntry (see models.py).

Reference data only ever changes via scripts/import_gamelists.py (no live
writes to game_list_entry), so this caches each console's rows in memory
for the life of the process rather than querying per request - see
GET /games/suggest in routers/games.py. Restart the API after a reimport
to pick up new/changed data; there's no TTL or file-watch, deliberately -
this data changes rarely and nothing else writes it, so a simple
load-once-per-process cache is the right amount of complexity.
"""

from __future__ import annotations

import re
import threading

from rapidfuzz import fuzz, process
from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import GameListEntry

_WORD_THE = re.compile(r"\bthe\b")
_NON_ALNUM = re.compile(r"[^a-z0-9]+")
_WHITESPACE = re.compile(r"\s+")

# Deliberately minimal - case, "&" vs "and", punctuation/whitespace. Resist
# adding stemming or a synonym table; a human reviews every suggestion, this
# just needs to get titles close enough for the fuzzy scorer to work with.
def normalize_title(title: str) -> str:
    s = title.lower()
    s = _WORD_THE.sub(" ", s)
    s = s.replace("&", " and ")
    s = _NON_ALNUM.sub(" ", s)
    return _WHITESPACE.sub(" ", s).strip()


# console -> [(game_id, title, region, normalized_title), ...]
_cache: dict[str, list[tuple[str, str, str, str]]] = {}
_lock = threading.Lock()


def clear_cache() -> None:
    """Drops the in-memory cache so the next suggest() call re-reads the DB -
    only meaningful for tests (each gets a fresh in-memory DB) or right after
    a reimport in the same process; production restarts the API instead."""
    with _lock:
        _cache.clear()


def _load(db: Session, console: str) -> list[tuple[str, str, str, str]]:
    rows = db.execute(
        select(GameListEntry.game_id, GameListEntry.title, GameListEntry.region).where(
            GameListEntry.console == console
        )
    ).all()
    return [(game_id, title, region, normalize_title(title)) for game_id, title, region in rows]


def suggest(db: Session, console: str, query: str, limit: int = 15, min_score: int = 60) -> list[dict]:
    """Ranked name-match candidates for `query` against `console`'s reference
    list - a list of {game_id, title, console, region, score} dicts, highest
    score first. Deliberately returns every candidate above the threshold
    (including same-title-different-id duplicates, e.g. multi-disc/reissue
    variants) rather than collapsing them - picking the right one is a human
    decision, not something to silently resolve here."""
    with _lock:
        entries = _cache.get(console)
        if entries is None:
            entries = _load(db, console)
            _cache[console] = entries

    if not entries:
        return []

    normalized_query = normalize_title(query)
    choices = [entry[3] for entry in entries]
    matches = process.extract(
        normalized_query, choices, scorer=fuzz.token_set_ratio, limit=limit, score_cutoff=min_score
    )

    results = []
    for _normalized_title, score, idx in matches:
        game_id, title, region, _ = entries[idx]
        results.append(
            {"game_id": game_id, "title": title, "console": console, "region": region, "score": int(round(score))}
        )
    results.sort(key=lambda r: (-r["score"], r["title"]))
    return results
