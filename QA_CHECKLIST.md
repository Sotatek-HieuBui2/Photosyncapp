# QA & Accessibility Checklist - PhotoSync

## 1. Accessibility (A11y)

### Visual
- [ ] **Color Contrast**: Ensure text on background meets WCAG AA (4.5:1 for normal text, 3:1 for large text).
  - *Check*: Primary text on `Surface` and `Background`.
  - *Check*: Text on `Primary` colored buttons.
- [ ] **Touch Targets**: All interactive elements must be at least 48x48dp.
  - *Check*: Toolbar icons (Settings, Profile).
  - *Check*: Grid items (should be easy to tap).
  - *Check*: FAB (Standard size is 56dp).
- [ ] **Dynamic Type**: App should respond to system font size changes.
  - *Test*: Set system font size to Largest. Ensure layouts don't break and text doesn't overlap.

### Screen Readers (TalkBack)
- [ ] **Content Descriptions**: All icons and images must have meaningful descriptions.
  - *Check*: `contentDescription` is not null for functional icons (Sync, Profile).
  - *Check*: `contentDescription` is `null` for decorative icons to be skipped.
- [ ] **State Announcements**: Changes in state should be announced.
  - *Check*: When Sync starts/finishes, TalkBack should announce it.
  - *Check*: Toggle switches (Auto Sync) should announce "On/Off".
- [ ] **Focus Order**: Navigation order should be logical (Top -> Bottom, Left -> Right).

## 2. Functional QA

### Authentication
- [ ] **Sign In**: Google Sign-In flow works.
- [ ] **Sign Out**: Clears data and returns to login screen.
- [ ] **Revoked Access**: Handle case where user revokes permission in Google Account settings.

### Sync Logic
- [ ] **Manual Sync**: Tapping FAB starts sync.
- [ ] **Auto Sync**: Background worker runs periodically (approx 15 mins).
- [ ] **Resume**: Sync picks up where it left off (doesn't re-upload synced items).
- [ ] **Empty State**: Correctly handles 0 photos.
- [ ] **No Network**: Shows error or retries gracefully.

### Performance
- [ ] **Large Gallery**: Test with 1000+ items. Ensure scrolling is smooth (LazyGrid).
- [ ] **Memory**: Monitor memory usage during sync. Ensure no OOM crashes.
- [ ] **Battery**: Ensure background worker doesn't drain battery excessively.

## 3. UI/UX Polish
- [ ] **Loading States**: Skeletons or spinners shown while data loads.
- [ ] **Error Feedback**: Toasts or Snackbars for errors (Network, Permission).
- [ ] **Motion**: Animations (FAB spinner, Success check) play smoothly.
- [ ] **Dark Mode**: App looks good in Dark theme. Colors are not too harsh.

## 4. Device Compatibility
- [ ] **Screen Sizes**: Test on small phone (5") and tablet (10").
- [ ] **Android Versions**: Test on Android 8.0 (Min SDK) and Android 14 (Target SDK).
- [ ] **Permissions**: Handle runtime permissions correctly on Android 13+ (Granular media permissions).
