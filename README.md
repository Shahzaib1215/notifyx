# NotifyX Android starter

Native Kotlin + Jetpack Compose starter for a non-root Android notification utility.

## Included in this build
- Installed app list with user/system filter and search
- Per-app local audio selection using Android Storage Access Framework
- Persistable access to selected audio URI (where the provider supports it)
- Per-app audio preference saved locally using SharedPreferences
- Audio preview, remove assignment, and re-open saved selection after app restart
- Per-app handoff to Android notification settings
- Vibration pattern tests

## Build requirements
- Android Studio stable
- JDK 17
- Android SDK Platform 35
- Internet access for the first Gradle dependency sync

## Build a debug APK
Open this folder in Android Studio, sync Gradle, then run:

Windows:
```bat
gradlew.bat assembleDebug
```

macOS/Linux:
```bash
./gradlew assembleDebug
```

Output:
`app/build/outputs/apk/debug/app-debug.apk`

## Important Android behavior
NotifyX can remember a local audio URI and preview it, but an ordinary third-party app cannot silently change another app's notification-channel sound. To actually apply a tone, open that app's Android notification settings and select the sound for the relevant channel/category if the device exposes a picker. Android/OEM behavior varies.

## Play Store note
`QUERY_ALL_PACKAGES` is included for the all-installed-apps feature. Google Play limits this permission to eligible core use cases and requires declaration/review. For Play distribution, assess package visibility policy and use the narrowest compliant approach.

## Not yet production-complete
Room database migration, profile automation, JSON backup/restore, full channel metadata inspection, accessibility review, automated tests, app icon, privacy policy and release signing remain future work. The app does not claim to apply system notification changes automatically.
