"""rename VMC label _oplpops_ infix to _ps2gm_

Revision ID: a27a211a27ad
Revises: d41433f64433
Create Date: 2026-09-06 11:43:21.518389
"""
from __future__ import annotations

from collections.abc import Sequence

from alembic import op


revision: str = 'a27a211a27ad'
down_revision: str | None = 'd41433f64433'
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


# VMC memory-card files were named "<gameId>_oplpops_<n>.VMC"; the "_oplpops_"
# infix was renamed to "_ps2gm_" as part of the PS2GM rebrand. VmcFile.label
# stores that stem verbatim, so rewrite it here. game_id is derived from the
# label at import time and doesn't contain the infix, so it's untouched.
def upgrade() -> None:
    op.execute(
        "UPDATE vmc_file SET label = REPLACE(label, '_oplpops_', '_ps2gm_')"
    )


def downgrade() -> None:
    op.execute(
        "UPDATE vmc_file SET label = REPLACE(label, '_ps2gm_', '_oplpops_')"
    )
