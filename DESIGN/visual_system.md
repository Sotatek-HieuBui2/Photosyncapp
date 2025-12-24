Visual System — PhotoSync

Purpose
- Single-source design spec for developers: colors, typography, icons, components, motion, and asset export guidelines.

1. Color Palette
- Primary: Indigo 600 — #3949AB
- Accent / CTA: Teal 500 — #009688
- Surface (light): #FFFFFF
- Surface (card): #F6F8FB
- On-surface text primary: #0F1724
- On-surface text secondary: #6B7280
- Error: #B00020
- Success: #2E7D32
- Overlay/disabled: #E5E7EB

Accessibility notes
- Primary on white: contrast 7.5:1 — OK for small text.
- Always check dynamic text sizes in Android accessibility settings.

2. Typography
- Font family: Roboto (Android default) or Inter.
- Scale:
  - H1 / Title: 22sp SemiBold
  - H2 / Section: 18sp Medium
  - Body: 14sp Regular
  - Caption / Label: 12sp Medium
- Line height: 1.2–1.4x depending on density.
- Use `textAppearance` styles in Android theme for easy overrides.

3. Spacing & Tokens
- Spacing unit: 8dp base
- Margins: small 8dp, medium 16dp, large 24dp
- Corner radius: 8dp (cards), 16dp (dialogs)
- Elevation: cards 2dp, raised FAB 6dp

4. Iconography
- Style: Material icons, outline for neutral, filled for active/selected.
- Sizes: touch area 48dp; icon size 24dp (default), 18dp for helper icons.
- File format: VectorDrawable (XML) / SVG source.
- Naming: ic_[feature]_[state]. Example: ic_sync_default, ic_sync_active, ic_pause.

5. Buttons & CTAs
- Primary CTA: Floating Action Button (FAB)
  - Size: 56dp
  - Color: `Teal 500` background, white icon
  - Behavior: shows a circular progress ring when active (determinate progress), with center icon replaced by spinner or check on completion.
- Secondary: Outlined or text buttons in toolbar.
- Disabled state: reduce alpha to 40%.

6. Dashboard Layout
- Phone: Single column list with top AppBar showing storage + status banner.
- Tablet: 2-column grid for gallery previews.
- Card: thumbnail (left) 88x88dp, title + meta (right), status badge top-right of thumbnail.
- Grid: 3 columns on small phones, 4 on large phones/tablets.

7. Sync Queue & Progress
- Sync Queue: Bottom sheet (modal) that lists items currently pending, uploading, failed.
- Per-item status: uploading (progress bar 0–100%), succeeded (green check), failed (retry icon).
- Global progress: persistent small banner under AppBar: `Uploading 14/60 • 23% • ETA 00:12:34`.
- Resume UX: show message `Resumed: continuing from 40%` when worker restarts mid-queue.

8. Notifications
- Small content: app icon, title "PhotoSync", message "Uploading 14/60 (23%) • ETA 00:12:34"
- Actions: Pause / Cancel (Quick action buttons)
- Foreground persistent: determinate progress + percent.

9. Motion & Micro-interactions
- Motion durations:
  - Short: 120ms
  - Medium: 240ms
  - Long: 360ms
- Use ease-in-out for entry/exit; linear for progress.
- Micro interactions: FAB progress ring, thumbnail upload fade + check, subtle elevation on list item long-press.

10. Accessibility
- Touch targets: Min 48dp for actionable items.
- Content descriptions for icons and buttons.
- Announce sync start/complete to TalkBack.
- Respect font scaling and RTL mirroring.

11. Asset Export & Implementation Checklist (for dev)
- Icons (VectorDrawable):
  - ic_sync_default.xml
  - ic_sync_active.xml
  - ic_pause.xml
  - ic_cancel.xml
  - ic_check.xml
  - ic_settings.xml
  - ic_signout.xml
  - ic_storage.xml
  - ic_photo_placeholder.xml
- Launcher icons: adaptive icons (foreground.svg, background.svg) in shapes for mdpi/mdpi xhdpi etc — provide as Android Asset Studio inputs.
- Lottie suggestions: `success_confetti.json` for completion, `upload_spinner.json` for FAB.
- Colors: create `res/values/colors.xml` with names: colorPrimary, colorAccent, surface, onSurface, success, error, disabledOverlay.
- Dimens: `res/values/dimens.xml` with tokens: spacing_small, spacing_medium, corner_card, fab_size.

12. Figma / Mockup structure (recommended pages)
- Page 1: Style Guide (colors, type, tokens)
- Page 2: Components (AppBar, FAB, Card, Buttons, Notification)
- Page 3: Screens (Dashboard, Sync Queue, Settings)
- Page 4: Assets (icons, Lottie previews)

13. Implementation notes for Android devs
- Use Material3 theme and dynamic color tokens if possible.
- Use `VectorDrawable` for all icons; provide fallback PNG for very old devices if required.
- Implement FAB progress ring as a custom drawable layered on top of `FloatingActionButton` or use `ProgressIndicator` inside an `ExtendedFloatingActionButton`.
- Persist per-file `isSynced` in DB (already present). Use WorkManager progress to feed UI.

14. Export & Handoff
- Provide a single ZIP containing: SVGs, VectorDrawable XML, colors.xml, dimens.xml, small PNG mockups of Dashboard and Sync Queue (if available).
- Provide Figma link (if created) and a simple README with naming rules.

-- End of visual system spec
