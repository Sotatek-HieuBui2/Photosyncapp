# PhotoSync

Android app to sync local photos/videos to Google Photos with background upload, local tracking, and utility tools.

## Current status

- Platform: Android `minSdk 24`, `target/compileSdk 34`
- Language/UI: Kotlin + Jetpack Compose
- Build JDK: Java 17
- CI: GitHub Actions runs lint + unit tests

## Main features

- Google sign-in and token diagnostics
- Local media scan via `MediaStore` (URI-based)
- Google Photos upload flow (`uploads` + `mediaItems:batchCreate`)
- Foreground sync worker with progress/ETA
- Auto-sync toggle and Wi-Fi only constraints
- Duplicate finder and object tagging tools
- Search by tags with debounced query

## Setup

1. Open project in Android Studio (latest stable recommended).
2. Use JDK 17 for Gradle/Android build.
3. Configure Google Cloud OAuth for package `com.example.photosync` and your SHA-1/SHA-256.
4. Place your local `app/google-services.json` manually if needed for local development.
   - This file is intentionally ignored and must not be committed.
5. Build and run:
   - `./gradlew assembleDebug`

## Test and CI

- Unit tests:
  - `./gradlew testDebugUnitTest`
- Lint + tests (same as CI):
  - `./gradlew lintDebug testDebugUnitTest`

CI workflow file: `.github/workflows/ci.yml`

## Architecture

- `MainActivity` + Compose screens
- `MainViewModel` and `ToolsViewModel` (MVVM)
- `MediaRepository` as data orchestration layer
- Room DB (`AppDatabase`, `MediaDao`, `MediaItemEntity`)
- Retrofit APIs (`GooglePhotosApi`, `GoogleDriveApi`)
- WorkManager worker (`SyncWorker`)
- Hilt DI modules (`NetworkModule`, `DatabaseModule`)

## Cloud sync behavior

- Cloud metadata sync runs in `MediaRepository.syncCloudMedia()`.
- `cloud_sync_enabled` default is `true` in `TokenManager`.
- If cloud sync is disabled, app skips cloud fetch and continues local scan/upload flow.

## Auth diagnostics

- `AuthManager` validates token scopes via OAuth2 tokeninfo endpoint.
- Diagnostic payload is stored under key `diag_tokeninfo` in `app_settings`.
- Sensitive token fields are redacted by `TokenManager.saveDiagnostic()`.

## Known limitations

- `AuthManager` currently still uses `GoogleAuthUtil` token flow (legacy direction).
- `Smart Optimize` and `Backup Verification` cards are present but feature-flagged off.
- DB uses `fallbackToDestructiveMigration()` and still needs proper migration path.
