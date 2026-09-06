import re, glob, sys, collections
import pathlib as _pathlib
import os as _os

# Anchored to the repo so the check runs the same from anywhere.
_os.chdir(_pathlib.Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main")


# Compose modifier extensions (size, width, clip, background, ...) are
# top-level functions: each needs its own import, and forgetting one is a
# compile error the project-type check can't see, because these aren't types.
#
# Where each name lives is learned from the rest of the project's imports -
# a name nothing imports anywhere (weight, align: scope members, not
# extensions) is simply not checked, which is what keeps this quiet.
files = glob.glob("java/**/*.kt", recursive=True)

home = collections.defaultdict(set)     # extension name -> packages it's imported from
file_text, file_pkg = {}, {}
for f in files:
    t = open(f).read()
    file_text[f] = t
    m = re.search(r'^package\s+([\w.]+)', t, re.M)
    file_pkg[f] = m.group(1) if m else ""
    for path in re.findall(r'^import\s+(\S+)', t, re.M):
        if path.endswith(".*"):
            continue
        pkg, _, name = path.rpartition(".")
        # Lower-case leading letter = function or property, not a type.
        if name and name[0].islower() and pkg.startswith("androidx.compose"):
            home[name].add(pkg)

def strip(t):
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'"""(.*?)"""', '""', t, flags=re.S)
    t = re.sub(r'"(\\.|[^"\\\n])*"', '""', t)
    return t

problems = []
for f in files:
    t = file_text[f]
    imports = re.findall(r'^import\s+(\S+)', t, re.M)
    imported = {p.rpartition(".")[2] for p in imports if not p.endswith(".*")}
    stars = {p[:-2] for p in imports if p.endswith(".*")}
    body = strip(t)
    local = set(re.findall(r'\bfun\s+(?:[\w.<>, ]+\.)?(\w+)\s*\(', body))
    for name, pkgs in home.items():
        if name in imported or name in local:
            continue
        if pkgs & stars or file_pkg[f] in pkgs:
            continue
        # Called on something, e.g. "Modifier.size(" or ".size(" in a chain.
        if re.search(r'\.' + name + r'\s*\(', body):
            problems.append((f, name, sorted(pkgs)[0]))

# Members of a Compose *scope* - LazyListScope, RowScope, BoxScope and the
# rest. They are available inside the scope's lambda and cannot be imported
# at all, so an import of one is always a typo that fails to compile.
#
# This bit exists because `import androidx.compose.foundation.lazy.stickyHeader`
# was written by hand and looks exactly like the real
# `...foundation.lazy.items` next to it. The learned map above could not
# catch it: nothing imports a scope member anywhere, so the name was never
# in the map to begin with.
NEVER_IMPORTABLE = {
    "stickyHeader": "LazyListScope",
    "item": "LazyListScope",
    "weight": "RowScope / ColumnScope",
    "align": "BoxScope / RowScope / ColumnScope",
    "alignByBaseline": "RowScope",
    "matchParentSize": "BoxScope",
    "menuAnchor": "ExposedDropdownMenuBoxScope",
    "animateItemPlacement": "LazyItemScope",
    "fillParentMaxSize": "LazyItemScope",
    "fillParentMaxWidth": "LazyItemScope",
    "fillParentMaxHeight": "LazyItemScope",
}

for _f in files:
    for _lineno, _line in enumerate(open(_f).read().splitlines(), 1):
        _m = re.match(r'\s*import\s+(\S+)', _line)
        if not _m:
            continue
        _name = _m.group(1).rpartition(".")[2]
        if _name in NEVER_IMPORTABLE:
            problems.append(
                (_f, _name + f" (a {NEVER_IMPORTABLE[_name]} member - use it inside the scope, do not import it)", "")
            )

for f, n, pkg in sorted(set(problems)):
    if pkg:
        print(f"MISSING-IMPORT  {f}: .{n}(  -> import {pkg}.{n}")
    else:
        print(f"BAD-IMPORT      {f}: {n}")
print("compose extension imports:", "FAIL" if problems else "ok")
sys.exit(1 if problems else 0)
