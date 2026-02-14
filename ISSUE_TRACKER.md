# Issue Tracker - PhotoSync

Last updated: 2026-02-14

## Status legend

- `TODO`: Chua bat dau
- `IN_PROGRESS`: Dang fix
- `BLOCKED`: Bi chan
- `DONE`: Da xong

## Overall progress

- Total issues: 16
- Done: 16
- In progress: 0
- Blocked: 0
- Todo: 0
- Completion: 100.0%

## Current focus

- No active issue. All tracked items are closed.

## Sprint plan

- Sprint 1 (`S1`): `SEC-001`, `BUILD-001`, `BUILD-002`, `SYNC-003`, `SYNC-004`, `SYNC-005`
- Sprint 2 (`S2`): `SYNC-001`, `SYNC-002`, `SYNC-006`, `AUTH-001`, `DATA-001`
- Sprint 3 (`S3`): `DATA-002`, `TOOLS-001`, `TOOLS-002`, `TOOLS-003`, `DOC-001`

## Prioritized backlog

| ID | Priority | Sprint | Status | Issue | Impact | File(s) | Proposed fix | Owner | ETA |
|---|---|---|---|---|---|---|---|---|---|
| SEC-001 | P0 | S1 | DONE | `google-services.json` dang commit trong repo | Ro ri key/config production | `app/google-services.json`, `.gitignore`, `.github/workflows/ci.yml` | Owner accepted risk for personal project; no key rotation requirement | User | 2026-02-14 |
| BUILD-001 | P0 | S1 | DONE | CI dang dung JDK 11 trong khi project compile Java 17 | CI fail hoac build khong on dinh | `.github/workflows/ci.yml`, `app/build.gradle.kts` | Chuyen CI sang JDK 17 | Codex | 2026-02-14 |
| BUILD-002 | P0 | S1 | DONE | CI chi cai Android platform/build-tools 31, project compileSdk 34 | Co the khong build duoc | `.github/workflows/ci.yml`, `app/build.gradle.kts` | Cai `platforms;android-34` va build-tools phu hop | Codex | 2026-02-14 |
| SYNC-003 | P1 | S1 | DONE | Flow "Free up space" xoa file xong chua cap nhat DB (`isLocal`) | UI/DB co trang thai sai | `app/src/main/java/com/example/photosync/MainActivity.kt`, `app/src/main/java/com/example/photosync/ui/main/MainViewModel.kt` | Sau delete `RESULT_OK`, goi update repository/viewmodel | Codex | 2026-02-14 |
| SYNC-004 | P1 | S1 | DONE | `markAsDeletedLocally()` de trong (placeholder) | Logic delete local khong hoan chinh | `app/src/main/java/com/example/photosync/data/repository/MediaRepository.kt`, `app/src/main/java/com/example/photosync/data/local/MediaDao.kt` | Implement bulk update/delete theo trang thai sync | Codex | 2026-02-14 |
| SYNC-005 | P1 | S1 | DONE | Trong `SyncWorker`, file khong doc duoc bi danh dau `isSynced=true` | Co the bo sot upload that | `app/src/main/java/com/example/photosync/workers/SyncWorker.kt`, `app/src/main/java/com/example/photosync/data/repository/MediaRepository.kt` | Tach trang thai `skipped/error`, khong danh dau synced gia | Codex | 2026-02-14 |
| SYNC-001 | P1 | S2 | DONE | `syncCloudMedia()` dang no-op nhung luong UI van goi cloud sync | Hanh vi khong dung ky vong, kho debug | `app/src/main/java/com/example/photosync/data/repository/MediaRepository.kt`, `app/src/main/java/com/example/photosync/ui/main/MainViewModel.kt` | Implement cloud fetch metadata + align call theo setting | Codex | 2026-02-14 |
| SYNC-002 | P1 | S2 | DONE | Flag `cloud_sync_enabled` mac dinh `false` | User dang nhap xong nhung khong sync cloud theo ky vong | `app/src/main/java/com/example/photosync/data/local/TokenManager.kt` | Dat default `cloud_sync_enabled = true` | Codex | 2026-02-14 |
| SYNC-006 | P2 | S2 | DONE | Worker khong co retry/backoff policy ro rang cho loi tam thoi | Upload fail de mat va can manual retry | `app/src/main/java/com/example/photosync/workers/SyncWorker.kt`, `app/src/main/java/com/example/photosync/ui/main/MainViewModel.kt` | Dung `Result.retry()` co dieu kien + backoff | Codex | 2026-02-14 |
| AUTH-001 | P1 | S2 | DONE | Dang dung `GoogleAuthUtil` (legacy/deprecated direction) | Rui ro maintainability/auth regression | `app/src/main/java/com/example/photosync/auth/AuthManager.kt` | Chuyen token fetch sang `AuthorizationClient` va cap nhat Photos scopes | Codex | 2026-02-14 |
| DATA-001 | P1 | S2 | DONE | Quet MediaStore co dung cot `DATA` | Khong an toan voi scoped storage moi | `app/src/main/java/com/example/photosync/data/repository/MediaRepository.kt` | Khong phu thuoc file path, dung URI + metadata an toan | Codex | 2026-02-14 |
| DATA-002 | P2 | S3 | DONE | Room dang `fallbackToDestructiveMigration()` | Mat du lieu khi tang version DB | `app/src/main/java/com/example/photosync/di/DatabaseModule.kt`, `app/src/main/java/com/example/photosync/data/local/AppDatabase.kt`, `app/src/main/java/com/example/photosync/data/local/DatabaseMigrations.kt` | Bo destructive fallback, them migration explicit `3 -> 4` | Codex | 2026-02-14 |
| TOOLS-001 | P1 | S3 | DONE | `scanForObjects()` goi ML Kit bat dong bo nhung khong await ket qua | UI "Scan complete" co the sai thoi diem | `app/src/main/java/com/example/photosync/ui/tools/ToolsViewModel.kt` | Chuyen thanh suspend/await toan bo batch, cap nhat progress dung | Codex | 2026-02-14 |
| TOOLS-002 | P2 | S3 | DONE | `searchByTag()` filter tren full list moi lan go | Co the lag voi gallery lon | `app/src/main/java/com/example/photosync/ui/tools/ToolsViewModel.kt`, `app/src/main/java/com/example/photosync/data/local/MediaDao.kt` | Them debounce + query trong DB | Codex | 2026-02-14 |
| TOOLS-003 | P2 | S3 | DONE | Tinh nang "Smart Optimize" va "Backup Verification" chua implement (`TODO`) | Chua hoan thien feature set | `app/src/main/java/com/example/photosync/ui/tools/ToolsScreen.kt` | An feature chua hoan thien sau feature-flag | Codex | 2026-02-14 |
| DOC-001 | P3 | S3 | DONE | README chua cap nhat sat voi code hien tai | Team de nham khi setup/debug | `README.md` | Cap nhat auth flow, cloud sync status, CI requirements | Codex | 2026-02-14 |

## Work log

| Date | ID | Change summary | By |
|---|---|---|---|
| 2026-02-14 | INIT | Tao issue tracker ban dau | Codex |
| 2026-02-14 | PLAN-001 | Them sprint mapping va chuyen `BUILD-001` sang `IN_PROGRESS` | Codex |
| 2026-02-14 | BUILD-001 | Cap nhat CI sang JDK 17 | Codex |
| 2026-02-14 | BUILD-002 | Cap nhat CI cai Android platform 34 va build-tools 34.0.0 | Codex |
| 2026-02-14 | SEC-001 | Remove `app/google-services.json` va them ignore rule. Dang cho rotate key tren GCP/Firebase | Codex |
| 2026-02-14 | SYNC-003 | Delete thanh cong se cap nhat DB thong qua `handleLocalDeletionSuccess()` | Codex |
| 2026-02-14 | SYNC-004 | Implement `markAsDeletedLocally()` va bo placeholder | Codex |
| 2026-02-14 | VERIFY-001 | Chay `./gradlew --no-daemon clean testDebugUnitTest` thanh cong | Codex |
| 2026-02-14 | SYNC-005 | Worker khong con danh dau item loi truy cap la `isSynced=true` | Codex |
| 2026-02-14 | VERIFY-002 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong | Codex |
| 2026-02-14 | SYNC-001 | Implement `syncCloudMedia()` fetch media metadata tu Google Photos | Codex |
| 2026-02-14 | SYNC-002 | Dat default `cloud_sync_enabled` thanh `true` | Codex |
| 2026-02-14 | VERIFY-003 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong sau SYNC-001/002 | Codex |
| 2026-02-14 | SYNC-006 | Them retry/backoff cho sync worker khi gap loi tam thoi | Codex |
| 2026-02-14 | VERIFY-004 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong sau SYNC-006 | Codex |
| 2026-02-14 | DATA-001 | Bo phu thuoc cot `MediaStore.DATA` khi scan local media | Codex |
| 2026-02-14 | TOOLS-001 | Scan ML Kit da await ket qua thay vi fire-and-forget | Codex |
| 2026-02-14 | TOOLS-002 | Them debounce + query tags qua Room thay vi filter full list moi keystroke | Codex |
| 2026-02-14 | VERIFY-005 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong sau DATA/TOOLS updates | Codex |
| 2026-02-14 | TOOLS-003 | Feature-flag off cho Smart Optimize va Backup Verification | Codex |
| 2026-02-14 | DOC-001 | Cap nhat README theo trang thai code hien tai va setup/CI moi | Codex |
| 2026-02-14 | VERIFY-006 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong sau TOOLS-003/DOC-001 | Codex |
| 2026-02-14 | AUTH-001 | Refactor `AuthManager` bo `GoogleAuthUtil`, doi sang `AuthorizationClient` | Codex |
| 2026-02-14 | DATA-002 | Them DB migration `3 -> 4` va bo `fallbackToDestructiveMigration()` | Codex |
| 2026-02-14 | VERIFY-007 | Chay `./gradlew --no-daemon testDebugUnitTest` thanh cong sau AUTH/DATA updates | Codex |
| 2026-02-14 | SEC-001-MITIGATION | Them CI guardrail chan commit `app/google-services.json` va pattern key `AIza...` | Codex |
| 2026-02-14 | SEC-001-ACCEPTED | Go bo CI guardrail theo quyet dinh owner va dong issue theo accepted risk | Codex |

## Definition of done (per issue)

- Code fix da merge
- Unit test/integration test lien quan da pass
- Khong gay regression cho luong sign-in/sync/delete
- Tracker cap nhat `Status = DONE` + ghi vao `Work log`
