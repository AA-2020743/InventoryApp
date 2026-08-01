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
  adjustments are tracked separately so quantities stay auditable.
- **Sell**: scan (or type) items into a running cart, then checkout; stock
  is decremented server-side.
- **Supplier invoices**: track pending/paid invoices per supplier with due
  dates; the Dashboard surfaces overdue and due-soon counts.
- **Recurring expenses**: salaries and other daily/monthly costs feed into
  the profit calculation, not just revenue.
- **Dashboard**: net valuation (inventory at cost minus pending invoices),
  today/this-month revenue and profit-or-loss (auto-refreshes every 30s so
  the running total stays live through the day), low-stock and invoice
  alerts.
- **Statistics**: top-selling items and highest-margin items, sortable by
  quantity or profit, plus a calendar/month picker to browse sales and
  stats for any specific day or month.
- **Localization**: English and Arabic (with RTL layout), switchable from
  Settings without reinstalling.
- **Theme**: light, dark, or follow system, switchable from Settings.
- **Reminders**: a background WorkManager job polls `/api/alerts` hourly
  and raises local notifications for low stock and invoices due soon or
  overdue (no push/FCM server needed).

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
