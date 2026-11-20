# Price Tracker

## [Download the latest APK](https://github.com/nome2050/Price-Tracker/releases/latest/download/PriceTracker.apk)

Always the newest build, no zip to open. Install it over the old version to update; your data is kept.

A native Android app (Kotlin, Jetpack Compose, Material 3) that tracks the price of any product, in any app or on any web page, and compares it across KSA stores and global marketplaces. Pure-black AMOLED design with One UI-style rounded surfaces. Languages: English and Arabic.

> The images below are design previews rendered from the app's layout with sample products and prices. They are not captures from a physical device.

| Home | Dashboard | History |
|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Dashboard](docs/screenshots/dashboard.png) | ![History](docs/screenshots/history.png) |

| Product 360 | In-app browser | Settings |
|---|---|---|
| ![Product 360](docs/screenshots/product360.png) | ![Browser](docs/screenshots/browser.png) | ![Settings](docs/screenshots/settings.png) |

## Features

- **Track from any app or browser.** Select a price, choose **Track price** in the text menu. Or use **Share** on any product page or link.
- **Track on any web page.** The built-in browser highlights numbers when tap mode is on. Tap one and the price is re-read in the background.
- **History.** Day, Week, Month, Year and All charts with lowest, average and highest, plus drag-to-read.
- **Alerts.** Notifications on price drops and when a target price is reached. Background re-checks every 15 minutes to 24 hours.
- **Product 360.** When you add a product, the app searches Amazon.sa, Noon, Jarir, Extra, Sharaf DG, Lulu, Carrefour, Nahdi, AliExpress, eBay and Google Shopping and ranks matches by price. Results are read from each store's public search page, so a store that blocks automated requests or changes its layout will return fewer matches.
- **Dashboard.** Tracked count, savings found, drops in 24 hours, items at target, a basket price index chart, cheaper offers from other stores, and recent price moves.
- **AI (optional, Groq).** Matches the same product across stores, reads prices on pages normal parsing cannot, and gives buy-or-wait advice and a portfolio insight. Everything still works without a key, just less smartly.

## Install and update

1. Tap the download link at the top of this page on your phone.
2. Allow installs from this source when Android asks, then install. If Play Protect asks, choose **More details**, then **Install anyway**. The app is not on the Play Store and is signed with a private key, so Play Protect does not recognise it.
3. In **Settings**, paste your Groq API key and tap **Test key** to enable the AI features.

**Updating keeps your data.** Install a newer APK over the old one, do not uninstall. This works because:

- every build is signed with the same key (`keystore/release.jks`), which Android requires for an update;
- the version code rises with every CI build (`GITHUB_RUN_NUMBER`), so Android treats it as an upgrade;
- the database is never wiped on upgrade. When the schema changes, bump `AppDb.version` and add a Room `Migration` in `data/Repository.kt`. Schemas are exported to `app/schemas/`.

The signing key and its password are committed for private use. If this repository becomes public, generate a new key, move its passwords to GitHub Actions secrets, and remove `keystore/release.jks` from the repo. Anyone with the key can publish an APK that installs over yours.

## Build

Android Studio, or:

```
./gradlew assembleRelease     # JDK 17, Android SDK 35
```

GitHub Actions (`.github/workflows/build-apk.yml`) builds the signed release APK on every push and publishes it as the newest GitHub Release. The download link above always points at it.

To compile a default AI key into your own builds, add `groq.key=YOUR_KEY` to `local.properties` (git-ignored). A key typed in Settings overrides it. CI builds have no key, so enter it in Settings on the phone.

## Project layout

```
app/src/main/java/com/pricetracker/app/
  core/     price parser, HTML extractor, page fetcher, Product 360, Groq client, notifications
  data/     Room database and repository
  ui/       Compose screens: Home, Dashboard, Detail, Browser, Settings, quick-track sheet
  work/     WorkManager background checks
docs/screenshots/   README images
```

## Notes

- Android 17 is not a public build target yet, so the app compiles against API 35 and runs on newer versions.
- The app does not use an accessibility service or draw over other apps, so Play Protect has no sensitive permission to flag. It cannot read prices inside other apps on its own; use text selection or Share instead.
