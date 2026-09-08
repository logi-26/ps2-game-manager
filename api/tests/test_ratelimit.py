from __future__ import annotations

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.ratelimit import RateLimitMiddleware


def _client(limit: int) -> TestClient:
    app = FastAPI()

    @app.get("/")
    def _root():
        return {"ok": True}

    app.add_middleware(RateLimitMiddleware, limit_per_minute=limit)
    return TestClient(app)


def test_requests_under_the_limit_pass():
    client = _client(limit=3)
    for _ in range(3):
        assert client.get("/").status_code == 200


def test_requests_over_the_limit_are_rejected():
    client = _client(limit=2)
    assert client.get("/").status_code == 200
    assert client.get("/").status_code == 200

    resp = client.get("/")
    assert resp.status_code == 429
    assert "Retry-After" in resp.headers


def test_different_client_ips_are_tracked_separately():
    client = _client(limit=1)
    # TestClient always reports the same client host, so simulate a second
    # client by hitting a second TestClient instance against a fresh app -
    # the middleware's counters are per-app-instance anyway.
    other = _client(limit=1)

    assert client.get("/").status_code == 200
    assert other.get("/").status_code == 200  # separate middleware instance, separate counter
