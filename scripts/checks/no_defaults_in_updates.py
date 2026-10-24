#!/usr/bin/env python3
"""Fail the build when an update schema has a default in it.

An edit must only change what it was sent. Under zod 4, `.partial()` keeps
each field's `.default(...)` and applies it when the field is missing, so a
schema with defaults used for an update fills in every field the request
left out. Renaming a cheese sold by the kilo through `productInput.partial()`
saved it as "pcs", not sold by weight, one unit per package and a low-stock
threshold of 0 - caught against a test server before the upgrade, not on the
shop's data. (Its stock survived only because the edit route discards
`quantity` itself.)

Updates parse with a schema that has no defaults (`productBase.partial()`),
and creates add the defaults on top of it (`productBase.extend({...})`).

For every `name.partial()` in the backend, this finds `const name = ...` in
the same file and fails if its definition - or anything it spreads in
(`...fields`) or extends (`base.extend(`) - contains `.default(`.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend" / "src"

PARTIAL = re.compile(r"\b(\w+)\.partial\(\)")


def initializer(text: str, name: str) -> str | None:
    """The text after `const name =` up to the end of its statement."""
    m = re.search(rf"\bconst\s+{re.escape(name)}\b[^=]*=", text)
    if not m:
        return None
    depth = 0
    i = m.end()
    while i < len(text):
        c = text[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == ";" and depth == 0:
            return text[m.end():i]
        i += 1
    return text[m.end():]


def defaults_in(text: str, name: str, seen: set[str]) -> list[str]:
    """Names, starting with `name`, whose definitions carry a default."""
    if name in seen:
        return []
    seen.add(name)
    body = initializer(text, name)
    if body is None:
        return []
    found = [name] if ".default(" in body else []
    parents = re.findall(r"\.\.\.(\w+)", body)
    base = re.match(r"\s*(\w+)\.extend\(", body)
    if base:
        parents.append(base.group(1))
    for parent in parents:
        found += defaults_in(text, parent, seen)
    return found


failures: list[str] = []
for file in sorted(BACKEND.rglob("*.ts")):
    text = file.read_text()
    for m in PARTIAL.finditer(text):
        name = m.group(1)
        hits = defaults_in(text, name, set())
        if hits:
            line = text.count("\n", 0, m.start()) + 1
            rel = file.relative_to(ROOT).as_posix()
            failures.append(
                f"DEFAULT-IN-UPDATE  {rel}:{line}: {name}.partial() - "
                f"defaults in {', '.join(hits)} would fill in fields the update didn't send"
            )

for f in failures:
    print(f)
print("no defaults in updates: " + ("FAIL" if failures else "ok"))
sys.exit(1 if failures else 0)
