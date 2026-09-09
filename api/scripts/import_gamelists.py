"""Import the user-supplied game-name reference lists into game_list_entry
(see models.py's GameListEntry and app/namesearch.py) - the data
GET /games/suggest fuzzy-matches against. Unrelated to import_content.py's
"Server Content" import; this is a much smaller, purely reference dataset.

    python -m scripts.import_gamelists --dir "C:\\...\\game-lists"

Re-running replaces each (console, region)'s rows wholesale - this is
reference data the user periodically replaces with an updated export, not
data anyone edits row by row, so upsert-by-key would just strand IDs that
dropped out of a newer list. Run `alembic upgrade head` first so the
game_list_entry table exists.

Expected layout (fixed, not configurable)
------------------------------------------
  <dir>/PS2 NTSCJ.txt / PS2 NTSCU.txt / PS2 PAL.txt
      tab-delimited "<catalog-id>\\t <title>", Windows-1252, CRLF.
      IDs are catalog format ("SLES-51716") - converted to the app's
      filename format ("SLES_517.16") via convert_catalog_id().
  <dir>/ps1/PS1 NTSCJ.txt / PS1 NTSCU.txt / PS1 PAL.txt
      "**"-delimited "<id>**<title>", UTF-8, CRLF.
      IDs are already filename format - used as-is.

Both shapes have bare alphabetical section-header lines ("0-9","A","B",...)
mixed in for human readability, which the line parsers skip (along with any
other line that doesn't parse into a valid id + non-empty title).
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.db import SessionLocal
from app.models import GameListEntry

_CATALOG_ID_RE = re.compile(r"^([A-Z]{4})-(\d{5})$")
_FILENAME_ID_RE = re.compile(r"^[A-Z]{4}_\d{3}\.\d{2}$")
_stats: Counter = Counter()


def convert_catalog_id(raw: str) -> str | None:
    """"SLES-51716" -> "SLES_517.16" (the app's filename-format ID), or None
    if `raw` isn't in that catalog shape."""
    m = _CATALOG_ID_RE.match(raw.strip().upper())
    if not m:
        return None
    prefix, digits = m.groups()
    return f"{prefix}_{digits[:3]}.{digits[3:]}"


def parse_ps2_line(line: str) -> tuple[str, str] | None:
    """One "PS2 <region>.txt" line -> (filename-format id, title), or None
    for a section-header line or anything that doesn't parse as an ID."""
    if "\t" not in line:
        return None
    raw_id, _, title = line.partition("\t")
    game_id = convert_catalog_id(raw_id)
    title = title.strip()
    if game_id is None or not title:
        return None
    return game_id, title


def parse_ps1_line(line: str) -> tuple[str, str] | None:
    """One "PS1 <region>.txt" line -> (id, title), or None for a
    section-header line or anything that doesn't parse as an ID."""
    if "**" not in line:
        return None
    raw_id, _, title = line.partition("**")
    game_id = raw_id.strip().upper()
    title = title.strip()
    if not _FILENAME_ID_RE.match(game_id) or not title:
        return None
    return game_id, title


def _parse_file(path: Path, encoding: str, parse_line) -> list[tuple[str, str]]:
    entries: list[tuple[str, str]] = []
    text = path.read_text(encoding=encoding, errors="replace")
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parsed = parse_line(line)
        if parsed is None:
            _stats["skipped_line"] += 1
            continue
        entries.append(parsed)
    return entries


def import_region(db: Session, console: str, region: str, entries: list[tuple[str, str]]) -> None:
    # The raw lists have a handful of exact-duplicate lines (the same id+title
    # listed twice) - dedupe here since the unique constraint is on
    # (console, region, game_id, title), so an exact repeat would otherwise
    # fail the second insert. dict.fromkeys preserves first-seen order.
    deduped = list(dict.fromkeys(entries))
    _stats[f"{console}.{region}.exact_duplicate_lines"] += len(entries) - len(deduped)

    db.execute(delete(GameListEntry).where(GameListEntry.console == console, GameListEntry.region == region))
    for game_id, title in deduped:
        db.add(GameListEntry(console=console, region=region, game_id=game_id, title=title))
    db.commit()
    _stats[f"{console}.{region}.rows"] += len(deduped)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", required=True, type=Path, help="path to the game-lists folder")
    args = ap.parse_args(argv)

    root: Path = args.dir.expanduser().resolve()
    if not root.is_dir():
        ap.error(f"not a directory: {root}")

    files = [
        ("PS2", "NTSCJ", root / "PS2 NTSCJ.txt", "cp1252", parse_ps2_line),
        ("PS2", "NTSCU", root / "PS2 NTSCU.txt", "cp1252", parse_ps2_line),
        ("PS2", "PAL", root / "PS2 PAL.txt", "cp1252", parse_ps2_line),
        ("PS1", "NTSCJ", root / "ps1" / "PS1 NTSCJ.txt", "utf-8", parse_ps1_line),
        ("PS1", "NTSCU", root / "ps1" / "PS1 NTSCU.txt", "utf-8", parse_ps1_line),
        ("PS1", "PAL", root / "ps1" / "PS1 PAL.txt", "utf-8", parse_ps1_line),
    ]

    db = SessionLocal()
    try:
        for console, region, path, encoding, parse_line in files:
            if not path.is_file():
                print(f"  !! missing, skipped: {path}")
                _stats[f"{console}.{region}.missing_file"] += 1
                continue
            entries = _parse_file(path, encoding, parse_line)
            import_region(db, console, region, entries)
            print(f"{console} {region}: {len(entries)} rows")
    finally:
        db.close()

    print("\n== import summary ==")
    for key in sorted(_stats):
        print(f"  {key:24} {_stats[key]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
