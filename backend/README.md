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
| Supplier invoices | `GET/POST /api/invoices`, `GET /api/invoices/upcoming?days=`, `PUT /api/invoices/:id`, `POST /api/invoices/:id/pay` (optional `{ payFromCashRegister: true }` deducts the amount from the cash register), `DELETE /api/invoices/:id` |
| Sales | `GET /api/sales?from=&to=&limit=&paymentStatus=`, `POST /api/sales`, `GET/PUT/DELETE /api/sales/:id`, `POST /api/sales/:id/collect` (marks a deferred sale as collected) |
| Expenses (recurring, e.g. salaries) | `GET/POST /api/expenses`, `PUT/DELETE /api/expenses/:id` |
| Assets (non-inventory business property) | `GET/POST /api/assets`, `PUT/DELETE /api/assets/:id` |
| Cash register | `GET /api/cash-register` (balance + last 100 ledger entries), `POST /api/cash-register/set` (`{ value, note }` — reconciles to a counted amount), `POST /api/cash-register/entries` (`{ amount, note }` — manual signed entry) |
| Dashboard | `GET /api/dashboard/summary` — inventory valuation, net worth after pending invoices/plus deferred receivables/cash register balance, today/month revenue & profit, recurring expense burn rate, alert counts |
| Stats | `GET /api/stats/top-products?period=day\|month&date=&sortBy=quantity\|profit` (each item includes `category`, so a client can chart sales by category without a separate endpoint), `GET /api/stats/margins?limit=` (highest-margin items), `GET /api/stats/revenue?period=day\|month&from=&to=` |
| Alerts | `GET /api/alerts?days=` — low-stock items + due-soon/overdue supplier invoices (polled by the Android app for reminders) |
| Working days | `GET /api/workdays/today`, `POST /api/workdays/today` (`{ isWorking }`) — see below |
| Uploads | `POST /api/uploads/image` (multipart `image` field) — used for barcode-less "fallback" products |
| Backup | `GET /api/backup/export` — zip archive with a full JSON dump of all business data plus the uploads folder; `POST /api/backup/restore` — wipes and replaces all business data and uploaded files from an uploaded archive — see below |

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
- `POST /api/sales` accepts an optional `clientId` (a UUID set by the Android
  app's offline sales queue). If a sale with that `clientId` already exists,
  the existing sale is returned (`200`) instead of creating a duplicate —
  this makes retrying the same offline sale after a dropped response safe
  to do blindly, without risking a double sell.
- Inventory valuation = `Σ(quantity × purchaseCost)` for active products,
  **plus** the total value of all `Asset` rows (non-inventory business
  property like equipment/fixtures — a simple name+value+category record),
  minus the total of all **pending** supplier invoices. `Asset` has no
  effect on stock or sales, it only feeds `assetsValue`/`netValuation` on
  the dashboard summary.
- `Product.soldByWeight` marks an item as sold loose by weight (e.g. rice,
  produce) rather than as discrete units. It doesn't change any
  server-side math — `purchaseCost`/`sellingPrice` are just interpreted as
  price-per-kg by convention, and `quantity` is still stored in the same
  base-unit column (kg, in this case) as any other product — it exists so
  the Android client knows to prompt for grams sold instead of a whole-unit
  count on the Sell screen.
- Recurring expenses (`DAILY`/`MONTHLY`/`ONE_TIME`) feed a daily/monthly burn
  rate used to compute a realistic profit figure, not just revenue.
- **Working days**: a day with no `WorkingDay` row is assumed worked. Marking
  a day as *not* worked (`POST /api/workdays/today {"isWorking": false}`)
  excludes that day's `DAILY`-frequency expenses (e.g. a cashier's daily
  wage) from that day's and the month-to-date's profit calculation —
  `MONTHLY`-frequency expenses (rent, etc.) still accrue regardless, since
  those are owed whether or not the shop opened that particular day.
- **Deferred (pay-later) sales**: `POST /api/sales` accepts
  `paymentStatus: "PAID" | "DEFERRED"` (default `PAID`) and an optional
  `customerName`. Revenue/cost/profit are recognized immediately either way —
  the difference is only in cash flow: a `DEFERRED` sale's `totalAmount` is
  counted as an outstanding receivable in `dashboard.deferredReceivablesTotal`
  (and folded into `netValuation`) until `POST /api/sales/:id/collect` marks
  it `PAID`, at which point it's credited to the cash register instead.
- **Editing/deleting past sales**: `PUT /api/sales/:id` replaces a sale's
  full item list at once (the Android edit screen reopens the sale as an
  editable cart and resubmits everything); stock is reconciled by the *net*
  per-product delta between the old and new item lists (one
  `SALE_CORRECTION` inventory transaction per affected product, not an
  unwind-then-redo pair), and pricing is re-snapshotted from each product's
  *current* `sellingPrice`/`purchaseCost` at edit time. `DELETE
  /api/sales/:id` reverses every item's quantity back to stock. Both keep
  the sale's cash register entry (if any) in sync.
- **Cash register**: a simple signed ledger (`CashRegisterEntry`) whose
  balance is always the sum of every entry, never stored directly. Every
  non-deferred sale automatically credits the register for its full amount
  at checkout (and a deferred sale does so only once collected); editing or
  deleting a sale updates or removes that one linked entry instead of
  duplicating it. Paying a supplier invoice with `payFromCashRegister: true`
  debits the register by the invoice amount — net effect on `netValuation`
  is zero, since the pending-invoice liability drops by the same amount the
  cash asset does.

### Backup & restore

- `GET /api/backup/export` returns a **zip archive** (`Content-Type:
  application/zip`) containing `data.json` — one JSON document with every
  business table (products, suppliers, supplier invoices, sales, sale
  items, expenses, working days, inventory transactions, assets, cash
  register entries) — plus an
  `uploads/` folder holding every file currently in the server's uploads
  directory, so product photos are backed up too, not just the row that
  references them. The `User` table is **deliberately excluded** from
  `data.json` — restoring an old password hash over the current one could
  lock the owner out of their own backend.
- `POST /api/backup/restore` accepts that same archive shape and does a
  **full wipe-and-replace**: every business table is cleared and
  re-populated from `data.json` (preserving original IDs/timestamps so
  relations between records stay intact), and every file currently in
  `uploads/` is deleted and replaced with the archive's `uploads/` files.
  This is destructive and irreversible — there is no merge or
  partial-restore mode.
- The server also takes its **own daily backup automatically**: once at
  startup and then every 24h, a timestamped `inventory-backup-YYYY-MM-DD.zip`
  file (same archive format as `/export`) is written to `BACKUP_DIR`
  (default `backups/`, configurable via `.env`), with files older than
  `BACKUP_RETENTION_DAYS` (default 14) deleted on each run. This protects
  against *data* loss (a bad restore, a dropped table) but **not** against
  losing the whole disk/server — since it's same-disk, it is not a
  substitute for copying a backup somewhere else (e.g. the Android app's
  weekly pull, see `android/README.md`).
