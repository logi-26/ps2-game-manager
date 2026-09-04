from __future__ import annotations

import hashlib
import shutil
from functools import lru_cache
from pathlib import Path

from .config import get_settings

_CHUNK = 1 << 20


class BlobStore:
    """Content-addressed blob storage on the local filesystem.

    Layout: <root>/<sha[0:2]>/<sha[2:4]>/<sha>.  Swappable for S3/MinIO later
    without changing callers - the DB only ever stores the sha256.
    """

    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def _path(self, digest: str) -> Path:
        return self.root / digest[:2] / digest[2:4] / digest

    def put_bytes(self, data: bytes) -> tuple[str, int]:
        digest = hashlib.sha256(data).hexdigest()
        dest = self._path(digest)
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            tmp = dest.with_name(dest.name + ".tmp")
            tmp.write_bytes(data)
            tmp.replace(dest)
        return digest, len(data)

    def put_file(self, src: Path) -> tuple[str, int]:
        h = hashlib.sha256()
        size = 0
        with open(src, "rb") as fh:
            for chunk in iter(lambda: fh.read(_CHUNK), b""):
                h.update(chunk)
                size += len(chunk)
        digest = h.hexdigest()
        dest = self._path(digest)
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            tmp = dest.with_name(dest.name + ".tmp")
            shutil.copyfile(src, tmp)
            tmp.replace(dest)
        return digest, size

    def exists(self, digest: str) -> bool:
        return self._path(digest).exists()

    def path(self, digest: str) -> Path:
        return self._path(digest)


@lru_cache
def get_blobstore() -> BlobStore:
    return BlobStore(get_settings().blob_root)
