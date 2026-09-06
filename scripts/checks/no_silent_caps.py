#!/usr/bin/env python3
"""Fail the build when a list can silently arrive incomplete.

A cap that nobody asked for is the worst kind of bug in this app: the screen
still looks right. The margins tab reported the catalogue's lowest margin
from a list capped at twenty, so "lowest" quietly meant "lowest of the top
twenty". The day view summed a capped list of sales to get the day's
revenue, so the total was short by whatever the cap cut off. Neither looked
broken.

The rule this enforces:

  * A route that returns a list must not invent its own limit. It may honour
    one the caller asked for; it must not default to a number.
  * The app must not pass a limit it made up either.
  * Truncating a list that has already been fetched is fine when the screen
    says so - a search box, a "+N more" line, an "other" pie slice - so
    those live in ALLOWED below, each with the reason it is honest.

Anything else fails, and adding to ALLOWED is a deliberate act with a
sentence attached.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend" / "src"
ANDROID = ROOT / "android" / "app" / "src" / "main" / "java"

# (file suffix, matched text) -> why this truncation is honest.
ALLOWED: dict[tuple[str, str], str] = {
    ("ui/sales/SalesScreen.kt", ".take(6)"):
        "search results under a search box - typing narrows them",
    ("ui/sales/EditSaleScreen.kt", ".take(6)"):
        "search results under a search box - typing narrows them",
    ("ui/spoilage/SpoiledProductScreen.kt", ".take(6)"):
        "search results under a search box - typing narrows them",
    ("ui/sales/ReceiptsScreen.kt", ".take(4)"):
        "first lines of a receipt, with a '+N more' line under them",
    ("ui/invoices/InvoicesScreen.kt", ".take(MAX_PRODUCT_MATCHES)"):
        "search results, with the remainder counted out in the UI",
    ("ui/common/PieChart.kt", ".take(maxSlices - 1)"):
        "smallest slices are merged into an 'other' slice, not dropped",
}

failures: list[str] = []


def allowed(path: Path, matched: str) -> bool:
    rel = path.as_posix()
    return any(rel.endswith(suffix) and matched == text for (suffix, text) in ALLOWED)


def check_backend() -> None:
    # const limit = req.query.limit ? Number(req.query.limit) : 50
    default_limit = re.compile(r"req\.query\.limit\s*\)\s*:\s*(\d+)")
    # take: 100  (a literal, rather than one derived from a caller's limit)
    literal_take = re.compile(r"\btake:\s*(\d+)\b")
    for file in sorted(BACKEND.rglob("*.ts")):
        for lineno, line in enumerate(file.read_text().splitlines(), 1):
            for pattern, what in ((default_limit, "a default limit of"), (literal_take, "take:")):
                m = pattern.search(line)
                if m and not allowed(file, m.group(0)):
                    failures.append(
                        f"{file.relative_to(ROOT)}:{lineno}: {what} {m.group(1)} - "
                        f"a route must not cap a list the caller did not cap"
                    )


def check_android() -> None:
    # limit = 200, or a bare .take(20) on a fetched list
    passes_limit = re.compile(r"\blimit\s*=\s*(\d+)")
    takes = re.compile(r"\.take\([^)]*\)")
    for file in sorted(ANDROID.rglob("*.kt")):
        for lineno, line in enumerate(file.read_text().splitlines(), 1):
            if line.lstrip().startswith("//"):
                continue
            m = passes_limit.search(line)
            if m and not allowed(file, m.group(0)):
                failures.append(
                    f"{file.relative_to(ROOT)}:{lineno}: asks the server for only {m.group(1)} rows - "
                    f"omit the limit unless the screen says it is showing a subset"
                )
            for m in takes.finditer(line):
                if not allowed(file, m.group(0)):
                    failures.append(
                        f"{file.relative_to(ROOT)}:{lineno}: {m.group(0)} truncates a fetched list - "
                        f"say so on screen and add it to ALLOWED, or drop it"
                    )


check_backend()
check_android()

for failure in failures:
    print(f"SILENT-CAP  {failure}")
print("no silent caps:", "FAIL" if failures else "ok")
sys.exit(1 if failures else 0)
