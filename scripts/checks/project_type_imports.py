import re, glob, collections, sys
import pathlib as _pathlib
import os as _os

# Anchored to the repo so the check runs the same from anywhere.
_os.chdir(_pathlib.Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main")


files = glob.glob("java/**/*.kt", recursive=True)

def pkg(t):
    m = re.search(r'^package\s+([\w.]+)', t, re.M)
    return m.group(1) if m else ""

declared = collections.defaultdict(set)
file_pkg, file_text = {}, {}
for f in files:
    t = open(f).read()
    file_text[f], p = t, pkg(t)
    file_pkg[f] = p
    for name in re.findall(
        r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
        r'(?:public |internal |private |abstract |open |sealed |data |enum |value |annotation )*'
        r'(?:class|interface|object)\s+(\w+)', t, re.M):
        declared[name].add(p)

def strip(t):
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'"""(.*?)"""', '""', t, flags=re.S)
    t = re.sub(r'"(\\.|[^"\\\n])*"', '""', t)
    return t

problems = []
for f in files:
    t, p = file_text[f], file_pkg[f]
    # Match the path as one non-space run: "[\w.]+" is greedy and eats the
    # dot of a trailing ".*", so a wildcard import parses as an explicit one
    # with an empty name and every type in the file looks unresolved.
    imports = re.findall(r'^import\s+(\S+)(?:\s+as\s+(\w+))?', t, re.M)
    imported, star_pkgs = set(), set()
    for full, alias in imports:
        if full.endswith(".*"):
            star_pkgs.add(full[:-2])
        else:
            imported.add(alias or full.rsplit(".", 1)[-1])
    body = strip(t)
    local = set(re.findall(r'\b(?:class|interface|object|enum class|data class)\s+(\w+)', body))
    for name in set(re.findall(r'\b([A-Z]\w+)\b', body)):
        if name not in declared:
            continue
        pkgs = declared[name]
        if p in pkgs or name in local or name in imported or (pkgs & star_pkgs):
            continue
        if re.search(r'[\w.]+\.' + name + r'\b', body):
            continue
        problems.append((f, name, sorted(pkgs)))

for f, n, pk in problems:
    print(f"UNRESOLVED  {f}: {n}  (declared in {pk})")
print("import check:", "FAIL" if problems else "every project type used is resolvable")
print(f"scanned {len(files)} files, {len(declared)} project types")
sys.exit(1 if problems else 0)
