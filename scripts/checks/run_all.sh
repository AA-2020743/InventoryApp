#!/usr/bin/env bash
# Every check that stands in for a compiler we cannot run here.
#
# None of these replace `./gradlew assembleRelease`. Each one exists because
# a specific mistake reached a build or a phone: a DTO referenced without its
# import, a Modifier extension without its own, a stringResource given the
# wrong number of arguments, a lazy list inside a dropdown (which crashes
# when the menu opens), and a list capped so quietly that the figures drawn
# from it were wrong rather than merely short.
set -u
cd "$(dirname "$0")/../.."
status=0
for check in \
  scripts/checks/project_type_imports.py \
  scripts/checks/compose_extension_imports.py \
  scripts/checks/string_resource_arity.py \
  scripts/checks/lazy_layout_in_menu.py \
  scripts/checks/no_silent_caps.py
do
  python3 "$check" || status=1
done
exit $status
