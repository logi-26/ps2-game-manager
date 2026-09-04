"""Load the legacy "Server Content" tree into the database + blob store.

    python -m scripts.import_content --content-dir "../Server Content"
    python -m scripts.import_content --content-dir "../Server Content" --console PS2 --limit 500

Idempotent: re-running upserts rows and skips blobs already in the store.
Run `alembic upgrade head` first so the schema exists.

Legacy layout
-------------
  Covers/<console>/<region>/<_KIND>/<gameId>/<gameId>_(<n>)<_KIND>.{jpg|png}
  Configs/<console>/<region>/<gameId>.cfg
  Cheats/<console>/<region>/<gameId>.cht
  MemoryCards/<console>/<region>/<gameId>_oplpops_<n>.VMC  (+ .txt sidecar)
  Application/cue2pops/<linux|windows>/<file>
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.blobstore import get_blobstore
from app.db import SessionLocal
from app.models import (
    ART_KINDS,
    CONSOLES,
    REGIONS,
    Artwork,
    CheatFile,
    ConfigFile,
    Game,
    ToolRelease,
    VmcFile,
)

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None

_VARIANT_RE = re.compile(r"_\((\d+)\)")
_MIME = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png"}
_stats: Counter = Counter()
_game_cache: dict[str, Game] = {}


# --------------------------------------------------------------------------- #
def get_or_make_game(db: Session, game_id: str, console: str, region: str) -> Game:
    cached = _game_cache.get(game_id)
    if cached is not None:
        if cached.region != region:
            _stats["game.region_conflict"] += 1
        return cached

    game = db.get(Game, game_id)
    if game is None:
        game = Game(id=game_id, console=console, region=region)
        db.add(game)
        db.flush()  # client-assigned PK: register it before the next lookup
        _stats["game.new"] += 1
    _game_cache[game_id] = game
    return game


def _image_dims(path: Path) -> tuple[int | None, int | None]:
    if Image is None:
        return None, None
    try:
        with Image.open(path) as im:
            return im.width, im.height
    except Exception:
        return None, None


# --------------------------------------------------------------------------- #
def import_configs(db: Session, root: Path, only_console: str | None) -> None:
    for cfg in sorted(root.glob("Configs/*/*/*.cfg")):
        console, region = cfg.parts[-3], cfg.parts[-2]
        if console not in CONSOLES or region not in REGIONS:
            continue
        if only_console and console != only_console:
            continue
        game_id = cfg.stem
        body = cfg.read_text(encoding="utf-8", errors="replace")
        title = _cfg_field(body, "Title")
        cfg_version = _cfg_field(body, "CfgVersion")

        game = get_or_make_game(db, game_id, console, region)
        if title and not game.title:
            game.title = title

        sha, _ = get_blobstore().put_bytes(body.encode("utf-8"))
        row = db.scalar(
            select(ConfigFile).where(
                ConfigFile.game_id == game_id, ConfigFile.source == "official"
            )
        )
        if row is None:
            db.add(
                ConfigFile(
                    game_id=game_id, body=body, sha256=sha, cfg_version=cfg_version, source="official"
                )
            )
            _stats["config.new"] += 1
        elif row.sha256 != sha:
            row.body, row.sha256, row.cfg_version = body, sha, cfg_version
            _stats["config.updated"] += 1
        else:
            _stats["config.unchanged"] += 1
    db.commit()


def import_cheats(db: Session, root: Path, only_console: str | None) -> None:
    for cht in sorted(root.glob("Cheats/*/*/*.cht")):
        console, region = cht.parts[-3], cht.parts[-2]
        if console not in CONSOLES or region not in REGIONS:
            continue
        if only_console and console != only_console:
            continue
        game_id = cht.stem
        body = cht.read_text(encoding="utf-8", errors="replace")
        get_or_make_game(db, game_id, console, region)
        sha, _ = get_blobstore().put_bytes(body.encode("utf-8"))
        row = db.scalar(
            select(CheatFile).where(CheatFile.game_id == game_id, CheatFile.source == "official")
        )
        if row is None:
            db.add(CheatFile(game_id=game_id, body=body, sha256=sha, source="official"))
            _stats["cheat.new"] += 1
        elif row.sha256 != sha:
            row.body, row.sha256 = body, sha
            _stats["cheat.updated"] += 1
        else:
            _stats["cheat.unchanged"] += 1
    db.commit()


def import_covers(db: Session, root: Path, only_console: str | None, limit: int | None) -> None:
    covers = root / "Covers"
    seen = 0
    for img in covers.rglob("*"):
        if not img.is_file() or img.suffix.lower() not in _MIME:
            continue
        # Covers/<console>/<region>/<_KIND>/<gameId>/<file>
        try:
            console, region, kind_dir, game_id = img.parts[-5], img.parts[-4], img.parts[-3], img.parts[-2]
        except IndexError:
            continue
        if console not in CONSOLES or region not in REGIONS:
            continue
        if only_console and console != only_console:
            continue
        kind = kind_dir.lstrip("_").upper()
        if kind not in ART_KINDS:
            _stats["art.skipped_kind"] += 1
            continue
        m = _VARIANT_RE.search(img.name)
        variant = int(m.group(1)) if m else 0

        get_or_make_game(db, game_id, console, region)
        sha, size = get_blobstore().put_file(img)
        row = db.scalar(
            select(Artwork).where(
                Artwork.game_id == game_id,
                Artwork.kind == kind,
                Artwork.variant == variant,
                Artwork.source == "official",
            )
        )
        if row is None:
            w, h = _image_dims(img)
            db.add(
                Artwork(
                    game_id=game_id, kind=kind, variant=variant, mime=_MIME[img.suffix.lower()],
                    sha256=sha, bytes=size, width=w, height=h, source="official",
                )
            )
            _stats["art.new"] += 1
        elif row.sha256 != sha:
            row.sha256, row.bytes = sha, size
            row.width, row.height = _image_dims(img)
            _stats["art.updated"] += 1
        else:
            _stats["art.unchanged"] += 1

        seen += 1
        if seen % 2000 == 0:
            db.commit()
            print(f"  ... {seen} cover files")
        if limit and seen >= limit:
            break
    db.commit()


def import_vmcs(db: Session, root: Path, only_console: str | None) -> None:
    # PS1 memory cards are .VMC, PS2 ones are .bin
    vmc_files = sorted(root.glob("MemoryCards/PS1/*/*.VMC")) + sorted(
        root.glob("MemoryCards/PS2/*/*.bin")
    )
    for vmc in vmc_files:
        console, region = vmc.parts[-3], vmc.parts[-2]
        if console not in CONSOLES or region not in REGIONS:
            continue
        if only_console and console != only_console:
            continue
        label = vmc.stem
        game_id = re.split(r"_oplpops_?\d*$", label)[0] or label
        desc_path = vmc.with_suffix(".txt")
        description = (
            desc_path.read_text(encoding="utf-8", errors="replace").strip()
            if desc_path.exists()
            else None
        )
        get_or_make_game(db, game_id, console, region)
        sha, size = get_blobstore().put_file(vmc)
        row = db.scalar(
            select(VmcFile).where(
                VmcFile.game_id == game_id, VmcFile.label == label, VmcFile.source == "official"
            )
        )
        if row is None:
            db.add(
                VmcFile(
                    game_id=game_id, label=label, description=description,
                    sha256=sha, bytes=size, source="official",
                )
            )
            _stats["vmc.new"] += 1
        elif row.sha256 != sha:
            row.sha256, row.bytes, row.description = sha, size, description
            _stats["vmc.updated"] += 1
        else:
            _stats["vmc.unchanged"] += 1
    db.commit()


def import_tools(db: Session, root: Path) -> None:
    base = root / "Application" / "cue2pops"
    if not base.is_dir():
        return
    for exe in base.rglob("*"):
        if not exe.is_file():
            continue
        os_name = exe.parts[-2].lower()
        if os_name not in {"windows", "linux"}:
            continue
        sha, size = get_blobstore().put_file(exe)
        row = db.scalar(
            select(ToolRelease).where(
                ToolRelease.name == "cue2pops", ToolRelease.os == os_name, ToolRelease.arch == "any"
            )
        )
        if row is None:
            db.add(
                ToolRelease(name="cue2pops", os=os_name, arch="any", sha256=sha, bytes=size)
            )
            _stats["tool.new"] += 1
        elif row.sha256 != sha:
            row.sha256, row.bytes = sha, size
            _stats["tool.updated"] += 1
        else:
            _stats["tool.unchanged"] += 1
    db.commit()


def _cfg_field(body: str, key: str) -> str | None:
    for line in body.splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip() or None
    return None


# --------------------------------------------------------------------------- #
def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--content-dir", required=True, type=Path, help='path to the "Server Content" folder')
    ap.add_argument("--console", choices=CONSOLES, help="limit to one console")
    ap.add_argument("--limit", type=int, help="stop after N cover files (for quick test runs)")
    ap.add_argument(
        "--skip", nargs="*", default=[], choices=["configs", "cheats", "covers", "vmcs", "tools"]
    )
    args = ap.parse_args(argv)

    root: Path = args.content_dir.expanduser().resolve()
    if not root.is_dir():
        ap.error(f"not a directory: {root}")

    sections = [
        ("configs", lambda: import_configs(db, root, args.console)),
        ("cheats", lambda: import_cheats(db, root, args.console)),
        ("covers", lambda: import_covers(db, root, args.console, args.limit)),
        ("vmcs", lambda: import_vmcs(db, root, args.console)),
        ("tools", lambda: import_tools(db, root)),
    ]
    db = SessionLocal()
    try:
        for name, fn in sections:
            if name in args.skip:
                continue
            print(f"{name} ...", flush=True)
            try:
                fn()
            except Exception as exc:  # keep going; report at the end
                db.rollback()
                _stats[f"{name}.ERROR"] += 1
                print(f"  !! {name} failed: {exc!r}", flush=True)
    finally:
        db.close()

    print("\n== import summary ==")
    for key in sorted(_stats):
        print(f"  {key:24} {_stats[key]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
