# Adam Pay

A cash wallet for Android. It helps you track the money you carry in your pockets, not in a bank.

## The story

I do not have a bank account. I only use cash. For a long time I lost track of my money. I did not know where it went, or how much I still had. Most wallet apps are made for cards and bank accounts, so they did not help me.

So I built Adam Pay for cash. You can create more than one "pocket", like *wallet*, *home*, or *piggy bank*. You can set a savings goal for something you want, like a game or a new phone. The feature I use most is quick logging. Press the AI button on your phone twice, and the app records the spending in under two seconds.

## Features

- **Pockets**: many cash places, with a total balance. Swipe between them.
- **Transactions**: expenses, income, and transfers between pockets. Search and filters.
- **Quick Log**: a small window that opens over any app. Also available from the home screen widget and the app shortcuts.
- **Double press of the AI button** on Honor phones opens Quick Log.
- **Cash count**: count the notes and coins in a pocket, compare with the balance, and save the difference.
- **Savings goals**: with 4 levels (25%, 50%, 75%, 100%). One goal can be featured on the home screen.
- **Weekly budget**, day streaks, and an evening reminder.
- **Home screen widgets**: balance, quick entries, and your goal.
- **App lock**: PIN (stored as a PBKDF2 hash) and fingerprint. Lock immediately, after 1 minute, or after 5 minutes. The app can also hide itself from recent apps.
- **Backup to Google Drive**: a private app folder that only this app can read. It runs every night and uploads only the changes. You can restore any backup.
- **Languages**: English, Arabic, or a mix of both. You can switch while the app is open.
- **Currency**: Jordanian Dinar only. Money is stored as whole fils (1 JD = 1000 fils), so there are no rounding errors.

## Tech stack

| Area | Tools |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, layers for `ui`, `domain`, and `data` |
| Dependency injection | Hilt |
| Data | Room (database), DataStore (settings), Kotlin Coroutines and Flow |
| Background work | WorkManager (nightly backup and reminders) |
| Widgets | Glance |
| Cloud | Google Identity sign-in, Google Drive `appDataFolder`, OkHttp |
| Tests | JUnit, Robolectric, Roborazzi (screenshot tests), MockWebServer |

**Design notes**

- Balances are not stored. They are calculated from the transaction rows, so they cannot get out of sync.
- The app does not store any Google token. Sign-in is handled by Google Play services.
- The backup stores data in small compressed chunks, so only new chunks are uploaded.

**AI tool used to build it:** Claude Opus 5.5 (Anthropic), as a coding assistant.

## Build it yourself

**Requirements:** Android SDK platform 37, build-tools 37.0.0, and a recent JDK (17 or newer).

1. Clone the repo.
2. Create a `local.properties` file in the project root:
   ```
   sdk.dir=/path/to/your/android-sdk
   ```
3. Build a debug version. You do not need any keys for this:
   ```
   ./gradlew :app:assembleDebug
   ```
4. To build a signed release, put your own keystore here. These files are not in the repo, and the folder is git-ignored:
   ```
   keystore/adampay-release.p12
   keystore/keystore.properties
   ```
   Then run:
   ```
   ./gradlew :app:assembleRelease
   ```

## Run the tests

```
./gradlew :app:testDebugUnitTest        # unit and integration tests
./gradlew :app:recordRoborazziDebug     # screenshots go to app/screenshots
```

## Google sign-in and backup (optional)

Sign-in is only needed for the Google Drive backup. The app works fully without it.

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Google Drive API**.
3. Set up the **Google Auth Platform**: user type *External*.
4. Use these scopes: `openid`, `userinfo.email`, `userinfo.profile`, and `drive.appdata`.
5. Create an **OAuth client ID** of type **Android**. Use the package name `com.adampay.app` and the SHA-1 fingerprint of your signing key.

There are no secrets or passwords in the code.

## Project structure

```
app/src/main/java/com/adampay/app/
  core/      security, backup, Google auth, launch routing
  data/      Room database, DataStore, repositories
  domain/    money rules, ledger, layout, goals
  ui/        Compose screens, widgets, Quick Log
tools/
  strings.py   all text in English and Arabic (run: python3 tools/strings.py)
  svg2vd.py    converts icons to Android vector drawables
build-notes.md  decisions and details, phase by phase
```

## Status

Version **0.6.0**. The core features, the app lock, the backup, the widgets, and the shortcuts are built and covered by automated tests (165 tests). It is built for Honor X7e (Android 16). Testing on a real device is still in progress.
