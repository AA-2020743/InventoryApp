# Checks

These stand in for a compiler and a phone that the development environment
does not have. Run them all with:

    ./scripts/checks/run_all.sh

They also run on every push (`.github/workflows/checks.yml`), alongside the
backend type-check and a real Android release build — the last of which is
the only thing that actually proves the app compiles.

Each check exists because a specific mistake reached a build or a phone.

| Check | What it caught |
| --- | --- |
| `project_type_imports.py` | A DTO referenced in a file that imports its DTOs one by one, without adding the import. |
| `compose_extension_imports.py` | `Modifier.size` / `Modifier.width` used in a file that imported `height` and `padding` but not those. Compose modifiers are top-level functions, one import each. |
| `string_resource_arity.py` | A `stringResource` passed arguments the string has no placeholders for — silent at runtime, so it ships. |
| `lazy_layout_in_menu.py` | A `LazyColumn` inside a `DropdownMenu`. The menu measures its content's intrinsic width; a lazy list is a `SubcomposeLayout` and has none, so opening the menu crashes. |
| `no_silent_caps.py` | A list arriving capped without the screen saying so. See below. |

## Why the cap check is the important one

The others catch things that fail loudly. A cap does not: the screen still
looks right and the numbers are simply wrong.

The margins tab reports the catalogue's highest, average and lowest margin.
With the list capped at twenty, "lowest" meant "lowest of the twenty
highest" — a plausible number that was not the one asked for. The day view
summed the sales list it had fetched to get the day's revenue, so a capped
list gave a total short by whatever the cap removed. The collected-tabs
history asked for the last two hundred paid sales and then kept the ones
that were collections, so the limit applied before the filter and the
history could come back nearly empty however many collections there were.

The rule: a route must not invent a limit, and the app must not pass one it
made up. Truncating a list already on screen is fine when the screen says
so — a search box, a `+N more` line, an "other" pie slice — and each of
those is listed in `ALLOWED` in `no_silent_caps.py` with the reason it is
honest. Adding to that list is deliberate and comes with a sentence.
