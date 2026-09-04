"""Optional, off-by-default per-client rate limiting.

No new dependency (same reasoning as MiniJson on the desktop side): this is a
small, fixed-window counter, not a distributed limiter - fine for a single
API process. Only activates when OPLAPI_RATE_LIMIT_PER_MINUTE is set.
"""

from __future__ import annotations

import time
from collections import defaultdict

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.types import ASGIApp

_WINDOW_SECONDS = 60


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp, limit_per_minute: int) -> None:
        super().__init__(app)
        self.limit = limit_per_minute
        # client_ip -> (window_start_epoch_seconds, count_in_window)
        self._counters: dict[str, tuple[float, int]] = defaultdict(lambda: (0.0, 0))

    async def dispatch(self, request: Request, call_next):
        client_ip = request.client.host if request.client else "unknown"
        now = time.monotonic()
        window_start, count = self._counters[client_ip]

        if now - window_start >= _WINDOW_SECONDS:
            window_start, count = now, 0

        count += 1
        self._counters[client_ip] = (window_start, count)

        if count > self.limit:
            retry_after = max(1, int(_WINDOW_SECONDS - (now - window_start)))
            return JSONResponse(
                {"detail": f"rate limit exceeded ({self.limit}/min)"},
                status_code=429,
                headers={"Retry-After": str(retry_after)},
            )

        return await call_next(request)
