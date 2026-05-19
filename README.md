# BPSync

Android app that imports blood pressure measurements from [MedM Blood Pressure Diary](https://play.google.com/store/apps/details?id=com.medm.bloodpressure) CSV exports and uploads them to **Google Health** via the [Health Connect API](https://developer.android.com/health-and-fitness/guides/health-connect).

## Features

- **CSV Import** — Parses MedM's quoted CSV format (`Date, Time, Sys, Dia, Pulse, Irregular Pulse, Source`)
- **Two-level Deduplication** — Deduplicates rows within the CSV *and* checks existing Health Connect records before writing
- **Device Metadata** — Preserves the BP monitor source device from the CSV in Health Connect records
- **Batch Upload** — Writes both `BloodPressureRecord` and `HeartRateRecord` in batches of 50
- **Material Design 3** — Full MD3 UI with dynamic color, animated transitions, and progress indicators
- **Menu Options** — Browse CSV, Reset CSV, Re-consent Permissions, About dialog with version info
- **Adaptive Icon** — Custom icon with support for adaptive icons (API 26+)
- **Named APK** — Output file is `BPSync-{version}-{buildType}.apk`

## Screenshots

<!-- Add screenshots here -->

## Requirements

- Android 9 (API 28) or higher
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed on device
- JDK 17 for building

## Build

```bash
# Set JAVA_HOME to JDK 17
export JAVA_HOME=/path/to/jdk17

# Build debug APK
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/BPSync-<version>-debug.apk`

## Usage

1. Export your blood pressure data from MedM as a CSV file
2. Open BPSync and grant Health Connect permissions when prompted
3. Tap **Browse CSV** and select the exported file
4. The app parses, deduplicates, and uploads records to Health Connect
5. View your data in Google Health / Google Fit

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.1.0 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Health Connect SDK | 1.1.0 |
| Android Gradle Plugin | 8.9.1 |
| compileSdk | 36 |
| minSdk / targetSdk | 28 / 35 |

## Project Structure

```
BPSync/
├── app/src/main/java/com/bpsync/app/
│   ├── MainActivity.kt           # Entry point
│   ├── BPSyncScreen.kt           # Main UI (Compose + Material 3)
│   ├── CsvParser.kt              # CSV parsing + in-file dedup
│   ├── HealthConnectWriter.kt    # Health Connect API integration
│   └── ui/theme/Theme.kt         # Dynamic color theme
├── app/src/main/res/             # Icons, colors, strings
├── build.gradle.kts              # Root build config
└── settings.gradle.kts
```

## License

<!-- Choose a license: MIT, Apache 2.0, etc. -->
