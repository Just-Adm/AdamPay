# Adam Pay

<<<<<<< HEAD
محفظة نقدية شخصية على Android، بالدينار الأردني فقط. مبنية لهاتف **Honor X7e (LAB-LX2) · MagicOS 10 · Android 16**، وهي أخت تطبيق Hyper Athlete: نفس المعمارية بس شكل مختلف.

| | |
|---|---|
| Package | `com.adampay.app` |
| Kotlin + Jetpack Compose | Material 3، ثيم داكن بالأخضر (افتراضي) + فاتح، خط Tajawal وأرقام بعرض ثابت |
| minSdk / targetSdk / compileSdk | 26 / **36 (Android 16)** / 37 |
| المعمارية | MVVM: `ui` / `domain` / `data` · Hilt · Room (v1) · DataStore · Coroutines/Flow · WorkManager · Glance |
| المال | كل مبلغ `Long` بالفلس (1 دينار = 1000 فلس). الأرصدة ما بتنحفظ، بتنحسب من الحركات |
| الحالة | **0.6.0-phase6**: كل المراحل المطلوبة خلصت (المرحلة 4 والمرحلة 7 انشالوا بطلبك) |

---

## شو موجود

### المرحلة 1: المحفظة
- الإعداد الأول (اللغة، الاسم، الرصيد الافتتاحي) ويقدر يتعاد من الإعدادات.
- ثلاث جيوب (في جيبي، في المنزل، الحصّالة) بكروت بتتقلب يمين ويسار، الرصيد الإجمالي، إضافة مصروف / دخل / تحويل، كشف الحساب مع بحث وفلاتر، الإدخال السريع، الفئات.
- ثلاث لغات: عربي + English (افتراضي)، عربي بالكامل، English بالكامل. التبديل فوري.

### المرحلة 2: عدّ النقود
- عدّ الجيب بالأوراق والقطع أو بكتابة المبلغ، مقارنة مع الرصيد، وتسجيل الفرق (مصروف، دخل، مفقود، موجود). إدارة الجيوب (ترتيب، ألوان، أرشفة).

### المرحلة 3: التخطيط
- ميزانية أسبوعية، أهداف ادخار، سلسلة الأيام، تذكير مسائي بترد عليه من الإشعار نفسه، وتذكير بعدّ النقود.

### المرحلة 5: الحساب والأمان
- قفل برمز من 4 أرقام (PBKDF2 مع salt) والبصمة، وإعادة القفل فوراً أو بعد دقيقة أو بعد 5 دقائق، وإخفاء التطبيق من قائمة التطبيقات الأخيرة.
- تسجيل دخول Google (بدون تخزين أي Token) ونسخ احتياطي على مجلد التطبيق المخفي بـ Drive: كل ليلة الساعة 11:30، تدريجي (بيرفع بس اللي تغيّر)، حد 3 GB، واسترجاع لأي نسخة.

### المرحلة 6: الوصول السريع
- بطاقة الهدف الزهري تحت الرصيد الإجمالي.
- التسجيل السريع: نافذة فوق أي تطبيق.
- ثلاث أدوات للشاشة الرئيسية (الرصيد، الإدخال السريع، هدفي).
- اختصارات بالضغط المطوّل على الأيقونة.
- الضغط مرتين على زر AI بيفتح التسجيل السريع.

---

## البناء

1. ثبّت Android SDK (platforms;android-37.0 و build-tools 37.0.0) واعمل `local.properties` فيه:
   ```
   sdk.dir=/path/to/android-sdk
   ```
2. نسخة تجريبية (Debug) بدون أي مفتاح:
   ```
   ./gradlew :app:assembleDebug
   ```
3. نسخة موقّعة (Release): المفتاح **مش موجود بهاد الملف** عن قصد. حط النسخة اللي عندك هيك:
=======
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
>>>>>>> 0c493a638df8b9d975d83b0513eb94e2ead5d100
   ```
   keystore/adampay-release.p12
   keystore/keystore.properties
   ```
<<<<<<< HEAD
   بعدين:
   ```
   ./gradlew :app:assembleRelease
   ```
   بصمة المفتاح (SHA-1): `A0:5B:9F:E8:38:D7:51:BF:8C:76:40:B2:FF:E6:E7:B4:5A:B6:D7:A7`

## الاختبارات
```
./gradlew :app:testDebugUnitTest        # 165 اختبار
./gradlew :app:recordRoborazziDebug     # صور الشاشات في app/screenshots
```

## Google Cloud (لتسجيل الدخول والنسخ الاحتياطي)
- مشروع **Adam Pay** مع تفعيل **Google Drive API**.
- Google Auth Platform: نوع External، والتطبيق منشور (In production).
- الصلاحيات: `openid`، `userinfo.email`، `userinfo.profile`، `drive.appdata`.
- عميل OAuth من نوع **Android**: الحزمة `com.adampay.app` وبصمة SHA-1 فوق.

ما في أي مفتاح أو كلمة سر داخل الكود.

## ملفات مهمة
- `build-notes.md`: كل القرارات والتفاصيل مرحلة مرحلة.
- `tools/strings.py`: مصدر كل النصوص (عربي وإنجليزي). عدّل الجدول فيه وبعدين شغّل `python3 tools/strings.py`.
- `tools/svg2vd.py`: تحويل أيقونات Material Symbols.
=======
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
>>>>>>> 0c493a638df8b9d975d83b0513eb94e2ead5d100
