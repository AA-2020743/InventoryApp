#!/usr/bin/env bash
# Off-site backup puller — run this ON YOUR SECOND SERVER (not on the
# InventoryApp production VPS). It logs into the production API, downloads
# a fresh full backup (DB + uploaded images, via GET /api/backup/export),
# saves it locally with a timestamped filename, and prunes anything beyond
# the newest 20 backups so disk usage on this server stays bounded.
#
# Nothing on the production server needs to change for this to work — it
# reuses the existing authenticated /api/backup/export endpoint.
#
# Setup on this (second) server:
#   1. Save this file, e.g. /opt/inventory-offsite/fetch-offsite-backup.sh
#   2. chmod +x fetch-offsite-backup.sh
#   3. Create /opt/inventory-offsite/.env (chmod 600) with:
#        API_BASE_URL=https://your-production-domain-or-ip:4000
#        API_EMAIL=owner@example.com
#        API_PASSWORD=your-login-password
#        BACKUP_DIR=/opt/inventory-offsite/backups
#        KEEP_COUNT=20
#   4. Test it once by hand: ./fetch-offsite-backup.sh
#   5. Add two cron entries (see bottom of this file for the exact lines)
#      so it runs at 12:00 AM and 7:00 PM Egypt time.
#
# Requires: bash, curl, python3 (for JSON parsing — no extra deps needed).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-"$SCRIPT_DIR/.env"}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file at $ENV_FILE — see the setup instructions at the top of this script." >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

: "${API_BASE_URL:?Set API_BASE_URL in $ENV_FILE}"
: "${API_EMAIL:?Set API_EMAIL in $ENV_FILE}"
: "${API_PASSWORD:?Set API_PASSWORD in $ENV_FILE}"
BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/backups}"
KEEP_COUNT="${KEEP_COUNT:-20}"

mkdir -p "$BACKUP_DIR"

echo "[$(date -Iseconds)] Logging in to $API_BASE_URL ..."
LOGIN_RESPONSE="$(curl -fsS -X POST "$API_BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$API_EMAIL\",\"password\":\"$API_PASSWORD\"}")"

TOKEN="$(python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" <<< "$LOGIN_RESPONSE")"
if [[ -z "$TOKEN" ]]; then
  echo "Login failed — check API_EMAIL/API_PASSWORD/API_BASE_URL." >&2
  exit 1
fi

TIMESTAMP="$(date -u +%Y-%m-%dT%H%M%SZ)"
OUT_FILE="$BACKUP_DIR/inventory-backup-$TIMESTAMP.zip"

echo "[$(date -Iseconds)] Downloading backup to $OUT_FILE ..."
curl -fsS "$API_BASE_URL/api/backup/export" \
  -H "Authorization: Bearer $TOKEN" \
  -o "$OUT_FILE"

if [[ ! -s "$OUT_FILE" ]]; then
  echo "Downloaded backup is empty — aborting without pruning." >&2
  rm -f "$OUT_FILE"
  exit 1
fi
echo "[$(date -Iseconds)] Saved $(du -h "$OUT_FILE" | cut -f1) to $OUT_FILE"

# Keep only the newest $KEEP_COUNT backups in BACKUP_DIR; delete the rest
# (oldest first) so this server's disk usage never grows unbounded.
mapfile -t FILES < <(ls -1t "$BACKUP_DIR"/inventory-backup-*.zip 2>/dev/null)
if (( ${#FILES[@]} > KEEP_COUNT )); then
  echo "[$(date -Iseconds)] ${#FILES[@]} backups on disk, keeping newest $KEEP_COUNT ..."
  for old in "${FILES[@]:$KEEP_COUNT}"; do
    echo "  removing $old"
    rm -f "$old"
  done
fi

echo "[$(date -Iseconds)] Done. $(ls -1 "$BACKUP_DIR"/inventory-backup-*.zip 2>/dev/null | wc -l) backup(s) retained."

# --- Cron setup (run `crontab -e` on this second server) -------------------
# Egypt is Africa/Cairo. Rather than hardcode a UTC offset (which breaks if
# Egypt ever changes its DST rules again), pin the cron job's timezone
# explicitly with a leading TZ= assignment so it always fires at true local
# midnight and 7pm regardless of this server's own system timezone:
#
#   TZ=Africa/Cairo
#   0 0  * * * /opt/inventory-offsite/fetch-offsite-backup.sh >> /opt/inventory-offsite/fetch-offsite-backup.log 2>&1
#   0 19 * * * /opt/inventory-offsite/fetch-offsite-backup.sh >> /opt/inventory-offsite/fetch-offsite-backup.log 2>&1
#
# (Older cron implementations ignore a TZ= line inside the crontab; if yours
# does, either set the server's system timezone to Africa/Cairo instead
# (`sudo timedatectl set-timezone Africa/Cairo`), or convert to UTC: Egypt
# is currently UTC+2 with no DST, so use `0 22 * * *` and `0 17 * * *` UTC.)
