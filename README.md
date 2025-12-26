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

## Troubleshooting Google Photos auth (diagnostics)

Quick reproduction and diagnostics for Google Photos auth issues:

1) Purpose
- This section explains how to reproduce the tokeninfo diagnostic output and where the app stores it. Useful when troubleshooting 401/403 "insufficient authentication scope".

2) How diagnostics are collected
- `AuthManager` calls `https://oauth2.googleapis.com/tokeninfo?access_token=...` after obtaining an access token.
- The tokeninfo body (JSON) is saved via `TokenManager.saveDiagnostic("tokeninfo", ...)` after redaction.
- Saved setting key in app settings: `diag_tokeninfo` (stored in `SharedPreferences` named `app_settings`).

3) Where to find diagnostics in the running app
- After a failing sign-in or token retrieval, the app surfaces a short diagnostic string in the main UI error banner (the message includes the saved `tokeninfo` content or a shortened form).
- Alternatively, inspect `app_settings` `SharedPreferences` programmatically or via ADB.

4) Redaction and privacy
- The saved diagnostic JSON has `access_token`, `id_token`, and `refresh_token` fields replaced with `<redacted>` when present. Non-JSON values are truncated to 2000 chars.

5) Recommended steps to gather useful info
- Run the app on your device/emulator.
- Sign in and reproduce the failing flow.
- Copy the UI error banner text (it contains the persisted `tokeninfo` or a short explanation).
- Paste the diagnostic here (or attach) so the team can inspect `scope`, `audience`, and `token_type`.

6) Next actions once diagnostic is available
- If `scope` doesn't contain photoslibrary scopes: update requested scopes or force re-consent.
- If `aud` doesn't match expected `client_id`: ensure package name + SHA-1 match OAuth client in GCP.
- If `token_type` isn't `Bearer` or token appears as `id_token` only: request proper access token or switch to server auth code exchange.

7) Tests
- Unit tests for `MainViewModel` cover sign-in success/failure and reauth behavior. See `app/src/test`.
