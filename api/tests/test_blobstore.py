from __future__ import annotations

import hashlib

from app.blobstore import BlobStore


def test_put_bytes_is_content_addressed(tmp_path):
    store = BlobStore(tmp_path / "blobs")
    data = b"hello world"
    digest, size = store.put_bytes(data)

    assert digest == hashlib.sha256(data).hexdigest()
    assert size == len(data)
    assert store.exists(digest)
    assert store.path(digest).read_bytes() == data


def test_put_bytes_lays_out_by_sha_prefix(tmp_path):
    store = BlobStore(tmp_path / "blobs")
    digest, _ = store.put_bytes(b"hello world")

    expected = store.root / digest[:2] / digest[2:4] / digest
    assert store.path(digest) == expected
    assert expected.is_file()


def test_put_bytes_is_idempotent(tmp_path):
    store = BlobStore(tmp_path / "blobs")
    digest1, _ = store.put_bytes(b"same content")
    mtime1 = store.path(digest1).stat().st_mtime_ns
    digest2, _ = store.put_bytes(b"same content")

    assert digest1 == digest2
    # second write shouldn't touch the already-existing file
    assert store.path(digest1).stat().st_mtime_ns == mtime1


def test_put_file_matches_put_bytes(tmp_path):
    src = tmp_path / "source.bin"
    data = b"\x00\x01\x02" * 1000
    src.write_bytes(data)

    store = BlobStore(tmp_path / "blobs")
    digest_from_file, size = store.put_file(src)
    digest_from_bytes, _ = store.put_bytes(data)

    assert digest_from_file == digest_from_bytes
    assert size == len(data)


def test_exists_false_for_unknown_digest(tmp_path):
    store = BlobStore(tmp_path / "blobs")
    assert store.exists("0" * 64) is False


def test_root_directory_created_on_init(tmp_path):
    root = tmp_path / "nested" / "blobs"
    assert not root.exists()
    BlobStore(root)
    assert root.is_dir()
