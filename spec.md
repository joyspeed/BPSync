# BPSync — Specification

## 1. Overview

BPSync is a single-screen Android application that bridges **MedM Blood Pressure Diary** (CSV export) with **Google Health** (via Health Connect). It reads a CSV file, deduplicates entries, and writes `BloodPressureRecord` and `HeartRateRecord` data into Health Connect.

## 2. Data Flow

```
MedM CSV file → CsvParser → BpReading list → HealthConnectWriter → Health Connect
```

### 2.1 Input: MedM CSV Format

Quoted CSV with the following columns:

| Column | Type | Example |
|---|---|---|
| Date | `yyyy-MM-dd` | `2025-04-10` |
| Time | `HH:mm:ss` | `07:30:00` |
| Sys | numeric (mmHg) | `120` |
| Dia | numeric (mmHg) | `80` |
| Pulse | integer (bpm) | `72` |
| Irregular Pulse | text | `detected` / empty |
| Source | text | `Omron HEM-7156T` |

### 2.2 Output: Health Connect Records

Each CSV row produces **two** Health Connect records:

1. **BloodPressureRecord** — systolic, diastolic, time, zone offset, device metadata, body position (unknown), measurement location (left upper arm)
2. **HeartRateRecord** — pulse as a single sample, 1-second duration window, same time and device metadata

## 3. Deduplication

Two levels to prevent duplicate entries:

### 3.1 CSV-level Dedup

Before upload, rows are deduplicated by the tuple `(date, time, systolic, diastolic, pulse)` using `distinctBy`. This handles cases where the same measurement appears multiple times in the CSV.

### 3.2 Health Connect-level Dedup

Before writing, the app reads existing `BloodPressureRecord` entries from Health Connect in the time range covered by the CSV. Records are fingerprinted as `"epoch_millis|sys_mmHg|dia_mmHg"`. Any CSV row whose fingerprint matches an existing record is skipped.

## 4. Components

### 4.1 CsvParser

- **Input**: `InputStream` (from content resolver via SAF file picker)
- **Output**: `List<BpReading>` (deduplicated)
- Handles quoted CSV fields (fields may contain commas inside quotes)
- Skips header row and malformed rows silently
- Pure Kotlin — no Android dependencies

### 4.2 HealthConnectWriter

- **Singleton object** providing:
  - `PERMISSIONS` — set of required Health Connect permissions (read/write for BP and HR)
  - `isAvailable(context)` — checks Health Connect SDK availability
  - `writeReadings(context, readings, onProgress)` — main write function
- Batches writes in groups of **50 records**
- Returns `WriteResult(written, skipped)` wrapped in `Result<>`
- Device metadata is set from the CSV `Source` column using `Device(manufacturer=source)`
- All records use `Metadata.manualEntry()` factory

### 4.3 BPSyncScreen (UI)

- Single-screen Compose UI with Material 3
- **States**: idle → file selected → uploading (with progress) → done / error
- **Top app bar** with overflow menu:
  - Browse CSV — opens SAF file picker
  - Reset CSV — clears selected file
  - Re-consent Permissions — re-triggers Health Connect permission flow
  - About — version info dialog
- Shows record count, dedup stats, and upload progress with animated indicators

### 4.4 MainActivity

- Minimal activity: enables edge-to-edge, sets Compose content with `BPSyncTheme`

### 4.5 Theme

- Dynamic color on Android 12+ (Material You)
- Falls back to default Material 3 light/dark schemes on older devices

## 5. Permissions

| Permission | Purpose |
|---|---|
| `health.READ_BLOOD_PRESSURE` | Dedup check against existing records |
| `health.WRITE_BLOOD_PRESSURE` | Write BP records |
| `health.READ_HEART_RATE` | Future dedup (currently unused) |
| `health.WRITE_HEART_RATE` | Write HR records |

Permissions are requested at runtime via Health Connect's permission contract. The app declares a `ViewPermissionUsageActivity` alias as required by the Health Connect privacy policy.

## 6. Build Configuration

| Property | Value |
|---|---|
| `applicationId` | `com.bpsync.app` |
| `minSdk` | 28 (Android 9) |
| `targetSdk` | 35 |
| `compileSdk` | 36 |
| JVM target | 17 |
| APK naming | `BPSync-{versionName}-{buildType}.apk` |

## 7. Constraints & Limitations

- **No network access** — the app is fully offline; data flows from local CSV to on-device Health Connect
- **No persistent storage** — no database or shared preferences; state is held in memory per session
- **Single CSV format** — only MedM Blood Pressure Diary CSV is supported
- **Write-only sync** — does not read Health Connect data back into the app (reads are only for dedup)
- **No scheduled sync** — manual one-shot import only
