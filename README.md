# Price Tracker (Android, native)

Kotlin + Jetpack Compose, Material 3, pure-black (AMOLED) One UI style. English, हिन्दी, العربية.

- **Track in any app**: accessibility service + floating tag / Quick Settings tile. Tap any price on screen to track it; tracked app prices are recorded whenever they appear.
- **Track in browsers**: built-in browser with "tap any number" mode, share-to-app, and "Track price" in the text-selection menu.
- **History**: Day / Week / Month / Year / All charts, low / avg / high, target-price alerts.
- **Product 360**: searches Amazon.sa, Noon, Jarir, Extra, Sharaf DG, Lulu, Carrefour, Nahdi, AliExpress, eBay, Google Shopping and ranks matches by price (best effort; store pages change).
- **AI (Groq, optional)**: same-product matching in Product 360, AI price reading on pages parsing can't handle, and buy-or-wait advice. Put `groq.key=...` in the git-ignored local.properties to compile a default key in; a key typed in Settings overrides it.

Build: open in Android Studio, or `./gradlew assembleDebug` (JDK 17, Android SDK 35).
