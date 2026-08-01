# Inventory App — Backend

Node.js + TypeScript + Express + Prisma + PostgreSQL API for the supermarket
inventory, sales and supplier-invoice app. Single owner account (JWT auth).

## Setup

```bash
cd backend
npm install
cp .env.example .env   # then edit JWT_SECRET at minimum
```

### Database

Either run Postgres via Docker:

```bash
docker compose up -d
```

...or point `DATABASE_URL` in `.env` at any Postgres 14+ instance you already
have running.

Then apply the schema and create the owner account:

```bash
npx prisma migrate deploy   # or: npx prisma migrate dev   (first time / dev)
npm run seed                # creates the owner login from SEED_OWNER_* in .env
npm run dev                 # http://localhost:4000
```

Change the seeded password immediately via `POST /api/auth/change-password`.

## API overview

All routes except `POST /api/auth/login` require `Authorization: Bearer <token>`.

| Area | Endpoints |
| --- | --- |
| Auth | `POST /api/auth/login`, `POST /api/auth/change-password` |
| Products | `GET/POST /api/products`, `GET/PUT/DELETE /api/products/:id`, `GET /api/products/barcode/:barcode`, `GET /api/products/categories` (distinct categories in use), `POST /api/products/:id/restock`, `POST /api/products/:id/adjust`, `GET /api/products/:id/transactions` |
| Suppliers | `GET/POST /api/suppliers`, `GET/PUT/DELETE /api/suppliers/:id` |
| Supplier invoices | `GET/POST /api/invoices`, `GET /api/invoices/upcoming?days=`, `PUT /api/invoices/:id`, `POST /api/invoices/:id/pay`, `DELETE /api/invoices/:id` |
| Sales | `GET/POST /api/sales`, `GET /api/sales/:id` |
| Expenses (recurring, e.g. salaries) | `GET/POST /api/expenses`, `PUT/DELETE /api/expenses/:id` |
| Dashboard | `GET /api/dashboard/summary` — inventory valuation, net worth after pending invoices, today/month revenue & profit, recurring expense burn rate, alert counts |
| Stats | `GET /api/stats/top-products?period=day\|month&date=&sortBy=quantity\|profit`, `GET /api/stats/margins?limit=` (highest-margin items), `GET /api/stats/revenue?period=day\|month&from=&to=` |
| Alerts | `GET /api/alerts?days=` — low-stock items + due-soon/overdue supplier invoices (polled by the Android app for reminders) |
| Working days | `GET /api/workdays/today`, `POST /api/workdays/today` (`{ isWorking }`) — see below |
| Uploads | `POST /api/uploads/image` (multipart `image` field) — used for barcode-less "fallback" products |
| Backup | `GET /api/backup/export` — full JSON dump of all business data; `POST /api/backup/restore` — wipes and replaces all business data from an uploaded dump — see below |

### Key business rules

- A product needs a **barcode or an image** (fallback for loose/unlabeled
  goods) — enforced server-side.
- `quantity`/`lowStockThreshold` are always stored in base units (pcs, kg,
  etc.) — the source of truth for valuation and stock logic. `unitsPerPackage`
  (default 1) is just metadata so a client can offer "how many packages, of
  how many units each" data entry and convert to/from base units on its own;
  the server never does that conversion itself.
- `category` is a plain string field, not a separate managed entity —
  `GET /api/products/categories` just returns the distinct values already in
  use so a client can suggest them, but typing a new one on product
  creation "creates" it implicitly.
- Every stock change (restock, sale, manual adjustment) writes an
  `InventoryTransaction` row, so stock levels are always auditable.
- Selling price and purchase cost are **snapshotted onto each `SaleItem`** at
  sale time, so later cost changes don't rewrite historical profit.
- Inventory valuation = `Σ(quantity × purchaseCost)` for active products,
  minus the total of all **pending** supplier invoices.
- Recurring expenses (`DAILY`/`MONTHLY`/`ONE_TIME`) feed a daily/monthly burn
  rate used to compute a realistic profit figure, not just revenue.
- **Working days**: a day with no `WorkingDay` row is assumed worked. Marking
  a day as *not* worked (`POST /api/workdays/today {"isWorking": false}`)
  excludes that day's `DAILY`-frequency expenses (e.g. a cashier's daily
  wage) from that day's and the month-to-date's profit calculation —
  `MONTHLY`-frequency expenses (rent, etc.) still accrue regardless, since
  those are owed whether or not the shop opened that particular day.

### Backup & restore

- `GET /api/backup/export` returns one JSON document with every business
  table (products, suppliers, supplier invoices, sales, sale items,
  expenses, working days, inventory transactions). The `User` table is
  **deliberately excluded** — restoring an old password hash over the
  current one could lock the owner out of their own backend.
- `POST /api/backup/restore` accepts that same JSON shape and does a **full
  wipe-and-replace**: every business table is cleared and re-populated from
  the upload, preserving original IDs/timestamps so relations between
  records stay intact. This is destructive and irreversible — there is no
  merge or partial-restore mode.
- The server also takes its **own daily backup automatically**: once at
  startup and then every 24h, a timestamped `inventory-backup-YYYY-MM-DD.json`
  file is written to `BACKUP_DIR` (default `backups/`, configurable via
  `.env`), with files older than `BACKUP_RETENTION_DAYS` (default 14) deleted
  on each run. This protects against *data* loss (a bad restore, a dropped
  table) but **not** against losing the whole disk/server — since it's
  same-disk, it is not a substitute for copying a backup somewhere else
  (e.g. the Android app's weekly pull, see `android/README.md`).
