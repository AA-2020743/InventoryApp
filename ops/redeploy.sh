#!/usr/bin/env bash
#
# Pull the current code and put it live.
#
#   sudo ./ops/redeploy.sh              # deploy main
#   BRANCH=some/branch ./ops/redeploy.sh
#
# Safe to re-run: every step is idempotent, and a run that changes nothing
# still ends by proving the service answers.
set -euo pipefail

BRANCH="${BRANCH:-main}"
SERVICE="${SERVICE:-inventory-backend}"
# Where the API listens. It binds loopback and nginx fronts it, so this is
# the address to health-check - not the public name, which would also be
# testing DNS and the certificate.
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:4000/health}"

# Derived from this file's own location rather than hardcoded, so the script
# works from any checkout and from any working directory.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Already root (the usual case here) means sudo is unnecessary, and on a box
# without it installed, insisting would be the only thing that failed.
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    SUDO="sudo"
fi

say() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

say "Updating to origin/$BRANCH"
# --ff-only on purpose: if the checkout has drifted, stop and say so rather
# than quietly building a merge nobody reviewed.
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git merge --ff-only "origin/$BRANCH"
git --no-pager log --oneline -1

cd "$repo_root/backend"

say "Installing dependencies"
# ci, not install: it installs exactly what package-lock.json pins, so a
# deploy can't quietly pick up a different dependency tree than the one that
# passed CI. It also fails outright when the lockfile and package.json
# disagree, which is worth knowing before the build rather than after.
npm ci

say "Generating the Prisma client"
# npm ci wipes node_modules, so this has to come after it and before the
# build - the generated client is what tsc type-checks against.
npx prisma generate

say "Applying migrations"
# deploy, never dev: it only applies what is already committed and will not
# invent a migration or reset the database.
npx prisma migrate deploy

say "Building"
npm run build

say "Restarting $SERVICE"
$SUDO systemctl restart "$SERVICE"

say "Checking the service answers"
# systemd reports "active" the moment the process starts, which is before
# node has opened the port - so poll the health endpoint rather than trust
# the unit state, and fail loudly if it never comes up.
for attempt in $(seq 1 15); do
    if curl -fsS --max-time 2 "$HEALTH_URL" >/dev/null 2>&1; then
        echo "healthy after ${attempt}s: $(curl -fsS "$HEALTH_URL")"
        exit 0
    fi
    sleep 1
done

echo "ERROR: $SERVICE did not answer $HEALTH_URL within 15s" >&2
$SUDO systemctl status "$SERVICE" --no-pager --lines 30 >&2
exit 1
