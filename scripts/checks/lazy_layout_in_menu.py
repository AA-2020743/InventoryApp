import re, glob, sys
import pathlib as _pathlib
import os as _os

# Anchored to the repo so the check runs the same from anywhere.
_os.chdir(_pathlib.Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main")


# A DropdownMenu measures its content's intrinsic width. Lazy layouts are
# SubcomposeLayouts and have no intrinsic measurements, so one inside a menu
# throws the moment the menu opens - a crash neither brace balancing nor
# type resolution catches.
MENUS = ("DropdownMenu(", "ExposedDropdownMenu(")
LAZY = ("LazyColumn(", "LazyRow(", "LazyVerticalGrid(", "LazyHorizontalGrid(", "LazyVerticalStaggeredGrid(")

def match(src, i, opener, closer):
    """Index of the delimiter closing the one at src[i]."""
    depth = 0
    while i < len(src):
        if src[i] == opener:
            depth += 1
        elif src[i] == closer:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(src) - 1

def call_region(src, name_idx, name):
    """The argument list plus any trailing lambda.

    Brace-matching straight from the call name finds the FIRST '{' - which is
    usually an argument lambda like onDismissRequest, closing immediately and
    hiding the content lambda entirely. The parens have to be walked first.
    """
    paren = src.index("(", name_idx)
    close = match(src, paren, "(", ")")
    end = close
    j = close + 1
    while j < len(src) and src[j] in " \t\r\n":
        j += 1
    if j < len(src) and src[j] == "{":
        end = match(src, j, "{", "}")
    return src[name_idx:end + 1]

problems = []
for f in glob.glob("java/**/*.kt", recursive=True):
    src = open(f).read()
    for menu in MENUS:
        start = 0
        while True:
            idx = src.find(menu, start)
            if idx == -1:
                break
            start = idx + len(menu)
            body = call_region(src, idx, menu)
            for lazy in LAZY:
                if lazy in body:
                    problems.append((f, src[:idx].count("\n") + 1, menu.rstrip("("), lazy.rstrip("(")))

for f, line, menu, lazy in problems:
    print(f"LAZY-IN-MENU  {f}:{line}: {lazy} inside {menu} - crashes when the menu opens")
print("lazy-in-menu:", "FAIL" if problems else "ok")
sys.exit(1 if problems else 0)
