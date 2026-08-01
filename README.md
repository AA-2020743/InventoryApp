# Supermarket Inventory App

An inventory, sales and supplier-invoice system for a small supermarket:
barcode-driven stock tracking (with a photo fallback for unlabeled goods),
point-of-sale style selling, supplier invoice reminders, recurring expense
tracking, and live profit/loss and inventory valuation.

The project has two parts:

- **`backend/`** — Node.js + TypeScript + Express + Prisma + PostgreSQL API.
  Single owner account, self-hosted. See `backend/README.md`.
- **`android/`** — Native Android app (Kotlin + Jetpack Compose) that talks
  to the backend. English/Arabic with RTL, light/dark theme. See
  `android/README.md`.

## Quickstart

```bash
# 1. Backend
cd backend
npm install
cp .env.example .env   # set JWT_SECRET
docker compose up -d   # or point DATABASE_URL at your own Postgres
npx prisma migrate deploy
npm run seed           # creates the owner login
npm run dev            # http://localhost:4000

# 2. Android
# Open android/ in Android Studio, sync, run.
# On first launch enter the server URL (e.g. http://10.0.2.2:4000 for the
# emulator) and the owner credentials from `npm run seed`.
```

## How the pieces fit together

- **Inventory valuation** = `Σ(quantity × purchase cost)` across active
  products, **minus** total pending supplier invoices — computed live by
  `GET /api/dashboard/summary`, not a stored/cached snapshot.
- **Daily profit/loss** = today's revenue minus today's cost of goods sold
  minus the daily rate of recurring expenses (salaries, etc.) — also
  computed live, so it can go negative (a loss) whenever expenses outweigh
  sales, and reflects reality at any point in the day, not just at close.
- **Reminders**: low stock and upcoming/overdue supplier invoices are
  exposed via `GET /api/alerts`; the Android app polls this daily in the
  background (WorkManager) and raises local notifications — no push
  server (FCM) required since this is a single self-hosted backend.
- **Barcode scanning**: the Android app uses the camera (CameraX + on-device
  ML Kit) for scanning, and also works with external Bluetooth/USB barcode
  scanners for free — those emulate a keyboard, so any barcode text field
  in the app accepts them without special code.
- **Statistics**: top-selling and highest-margin items, sortable by
  quantity or profit, with a calendar/month picker in the app to inspect
  any specific day or month's sales.
- **Backup & restore**: the server writes its own daily rotating backup to
  disk, and the Android app additionally pulls a full weekly copy to the
  phone (with an on-demand export/share and restore-from-file in Settings)
  so data survives even a total loss of the server's disk — see
  `backend/README.md` and `android/README.md` for details.

## Repository layout

```
backend/    Node/TS/Express/Prisma API
android/    Kotlin/Compose Android app
```
