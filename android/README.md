# Inventory App — Android

Native Android (Kotlin + Jetpack Compose) client for the supermarket
inventory, sales and supplier-invoice backend in `../backend`.

## Requirements

- Android Studio (Koala/2024.1 or newer) with JDK 17
- An Android device or emulator running API 26+
- The backend running somewhere reachable from the phone (see `../backend/README.md`)

## Opening the project

1. Open the `android/` folder in Android Studio (not the repo root).
2. Let Gradle sync — Studio will download the Android SDK components it
   needs (compileSdk/targetSdk 34) automatically if they're missing.
3. Run on a device/emulator.

> This project ships its own Gradle wrapper (`./gradlew`), but building from
> the command line still requires the Android SDK (`ANDROID_HOME` /
> `local.properties` with `sdk.dir=...`) — Android Studio sets this up for
> you automatically on first sync.

## First run

On first launch you'll be asked for:

- **Server URL** — e.g. `http://10.0.2.2:4000` when the backend runs on your
  development machine and you're using the Android emulator (`10.0.2.2`
  routes to the host machine's `localhost`). Use your machine's LAN IP
  (e.g. `http://192.168.1.50:4000`) when testing on a physical phone on the
  same network.
- **Email / password** — the owner account created by `npm run seed` in the
  backend.

The server URL, session token, theme and (if changed) language are all
persisted locally, so this is only needed once. Change the server URL any
time from Settings.

## Features

- **Barcode scanning**: camera-based scanning via CameraX + on-device ML Kit
  (no network/Google account needed). External Bluetooth/USB barcode
  scanners work automatically wherever there's a text field for a barcode
  (Sell screen, Add product screen) — those scanners just "type" the
  barcode followed by Enter, exactly like a keyboard, so no special
  integration code is required.
- **Inventory**: add products by scanning a barcode, or with just a photo
  for loose/unlabeled goods (e.g. produce); restock and manual stock
  adjustments are tracked separately so quantities stay auditable. A
  product can be marked "sold by weight" (rice, produce, etc.) — its
  purchase/selling price are then per-kg, and the unit is locked to kg.
- **Sell**: scan (or type) items into a running cart, then checkout; stock
  is decremented server-side. Adding a weight-based product to the cart
  prompts for grams sold instead of a whole-unit count (tap the row's edit
  icon to change it later); everything else uses the usual +/- stepper. If
  checkout can't reach the server, the sale is saved to a local offline
  queue instead of being lost, and a background job syncs it automatically
  once connectivity returns (each queued sale carries a client-generated ID
  so a retry after a dropped response can't double-sell). Barcode/name
  lookup also falls back to a local product cache when offline, so you can
  keep ringing up sales through a dropped connection — see "Offline
  selling" below for the tradeoffs this implies.
- **Supplier invoices**: track pending/paid invoices per supplier with due
  dates; the Dashboard surfaces overdue and due-soon counts.
- **Recurring expenses**: salaries and other daily/monthly costs feed into
  the profit calculation, not just revenue.
- **Assets**: a simple name/value/category list for non-inventory business
  property (equipment, fixtures, etc.) — reachable from the Invoices tab —
  whose total value feeds into the Dashboard's net valuation alongside
  inventory.
- **Dashboard**: net valuation (inventory + assets at value, minus pending
  invoices), today/this-month revenue and profit-or-loss (auto-refreshes
  every 30s so the running total stays live through the day), low-stock
  and invoice alerts.
- **Statistics**: top-selling items and highest-margin items, sortable by
  quantity or profit, plus a calendar/month picker to browse sales and
  stats for any specific day or month. Pie charts break the selected
  period's sales down by category and by item (revenue share), drawn
  directly with Compose Canvas — no charting library dependency.
- **Localization**: English and Arabic (with RTL layout), switchable from
  Settings without reinstalling.
- **Theme**: light, dark, or follow system, switchable from Settings.
- **Reminders**: a background WorkManager job polls `/api/alerts` daily
  and raises local notifications for low stock and invoices due soon or
  overdue (no push/FCM server needed).
- **Backup & restore** (Settings screen): "Export now" pulls a full backup
  archive (a zip containing all business data plus every uploaded product
  photo) from the server, saves it to app-specific local storage, and opens
  the share sheet so you can send it to Drive, email, etc. for real
  off-device safety. "Restore from file" lets you pick any such backup zip
  and, after a destructive-action confirmation, replaces **all** current
  products/sales/invoices/suppliers/expenses and uploaded photos with its
  contents — this cannot be undone. A weekly WorkManager job also does this
  pull automatically in the background (belt-and-suspenders alongside the
  server's own daily backups, in case the server's disk is ever lost
  entirely) and posts a notification when it saves a new one.

### Offline selling

The Sell screen keeps working through a dropped connection:

- Every successful product lookup (barcode scan or name search) refreshes a
  local Room cache. If a later lookup can't reach the server, it's answered
  from that cache instead of failing outright.
- If checkout itself can't reach the server, the finished sale is queued
  locally (with a client-generated ID) instead of being lost, and a
  WorkManager job — triggered immediately when the queue gains an entry,
  and again on every app startup in case the app was killed first — syncs
  it once connectivity returns. The Sell screen's title bar shows a "N
  pending sync" badge whenever sales are queued.
- The client-generated ID means a retried sync after a dropped *response*
  (the sale actually went through, but the phone never heard back) is
  recognized by the server as the same sale rather than sold twice.
- The one thing this can't fully protect against: two sales of the same
  low-stock item made offline, on the same phone, before either syncs. The
  local cache's quantity is decremented optimistically as each offline sale
  is queued so this is unlikely, but it can still drift from the server
  until connectivity returns. If a queued sale is ultimately rejected by the
  server for a real reason (e.g. the product was deleted in the meantime),
  it's dropped rather than retried forever, and a notification tells you to
  review recent stock levels.

## Building a release APK

The debug build (the one you get from just pressing Run in Android Studio)
works fine for testing, but for actual daily use you want a signed release
build — smaller, faster, no debug logging. This only needs to be set up
once; every rebuild after that is a single command.

### 1. Create a keystore (one time only)

```bash
keytool -genkeypair -v -keystore release-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias inventory-app
```

You'll be prompted for a keystore password, a key password, and some
identity fields (name/org — can be anything, it's not verified). **Keep
`release-key.jks` and both passwords somewhere safe** — losing them means
you can never sign an update to an already-installed copy of the app
again; you'd have to uninstall and reinstall fresh.

### 2. Point Gradle at it

Copy `keystore.properties.example` (in `android/`) to `keystore.properties`
(same folder) and fill in the real values:

```
storeFile=/absolute/path/to/release-key.jks
storePassword=<the keystore password>
keyAlias=inventory-app
keyPassword=<the key password>
```

`keystore.properties` is gitignored on purpose — it and the `.jks` file
never get committed. Without this file present, the release build type
just falls back to unsigned (still builds, just isn't installable until
signed), so a fresh clone or CI without your keystore doesn't break.

### 3. Build

```bash
cd android
./gradlew assembleRelease
```

The signed APK lands at
`android/app/build/outputs/apk/release/app-release.apk`. Copy it to your
phone (e.g. `adb install app-release.apk` with the phone connected over
USB and USB debugging on, or just transfer the file and open it — you'll
need to allow "install unknown apps" for whichever app you transfer it
through, since it's not coming from the Play Store).

## Known limitation of this build environment

This project was written in a sandboxed environment whose network policy
blocks `dl.google.com` (Google's Maven repository), so **Gradle could not
actually resolve the Android Gradle Plugin or any AndroidX/Google
dependency here, and the app was never compiled or run in this session**.
Every file was written carefully and cross-checked (brace/paren balance,
string-resource parity between `values/` and `values-ar/`, consistent
package/import names), but you should treat the first Android Studio sync
as the real first build and fix anything Studio's compiler flags — most
likely small things like an import path or a Compose API that shifted
between library versions.
