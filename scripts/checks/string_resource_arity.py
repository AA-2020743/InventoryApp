import re, glob, sys, xml.etree.ElementTree as ET
import pathlib as _pathlib
import os as _os

# Anchored to the repo so the check runs the same from anywhere.
_os.chdir(_pathlib.Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main")


E = {e.get("name"): (e.text or "")
     for e in ET.parse("res/values/strings.xml").getroot() if e.tag == "string"}

def top_level_args(src, open_idx):
    """Split the argument list starting at src[open_idx] == '(' on commas that
    are not nested inside another (), [] or {} - a naive [^)]* stops at the
    first inner ')' and miscounts every call with a nested one."""
    depth, args, cur, i = 0, [], "", open_idx
    while i < len(src):
        ch = src[i]
        if ch in "([{":
            depth += 1
            if depth == 1:
                i += 1
                continue
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                if cur.strip():
                    args.append(cur.strip())
                return args, i
        if depth == 1 and ch == ",":
            if cur.strip():
                args.append(cur.strip())
            cur = ""
        else:
            cur += ch
        i += 1
    return args, i

problems = []
for f in glob.glob("java/**/*.kt", recursive=True):
    src = open(f).read()
    for m in re.finditer(r'stringResource\s*\(', src):
        args, _ = top_level_args(src, m.end() - 1)
        if not args:
            continue
        key = re.match(r'R\.string\.(\w+)$', args[0])
        if not key or key.group(1) not in E:
            continue
        name = key.group(1)
        want = len(set(re.findall(r'%(\d+)\$[sd]', E[name])))
        got = len(args) - 1
        if want != got:
            problems.append((f, name, want, got))

for f, n, w, g in problems:
    print(f"ARITY  {f}: {n} declares {w} placeholder(s), call passes {g}")
print("stringResource arity:", "FAIL" if problems else "ok")
sys.exit(1 if problems else 0)
