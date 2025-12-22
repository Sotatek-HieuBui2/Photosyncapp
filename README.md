# PhotoSync

Android application to sync photos and videos to Google Photos.

## Features
- Sync local photos/videos to Google Photos.
- Check Google Drive storage quota.
- Local database (Room) to track sync status.
- Background sync using WorkManager.

## Setup
1. Open the project in Android Studio.
2. Configure your Google Cloud Project and get the OAuth 2.0 Client ID.
3. Update `SyncWorker.kt` with your token management logic.
4. Build and run.

## Architecture
- **MVVM**: Model-View-ViewModel pattern.
- **Repository**: Data abstraction.
- **Room**: Local caching.
- **Retrofit**: Network calls.
- **WorkManager**: Background tasks.
