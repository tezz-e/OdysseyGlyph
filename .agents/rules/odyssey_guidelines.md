---
name: odyssey-guidelines
description: Core guidelines for Android development and Odyssey Glyph project quirks.
trigger: always_on
---

# Odyssey Glyph & Android Development Rules

## 1. Material 3 Styling & Custom Fonts
* **Slider Styling:** Material 3 `Slider` components aggressively fall back to default purple (`#6750A4`) if `colorPrimary` is not strictly overridden. When styling sliders for the Nothing vibe, avoid heavy `ThemeOverlay` overrides that break default behaviors. Style explicitly using `app:thumbColor`, `app:trackColorActive`, etc.
* **Custom Fonts in MDC:** When defining custom fonts in a `TextAppearance` style for Material Components (like tooltips or buttons), you MUST include both `android:fontFamily` AND the app namespace `fontFamily` (e.g., `<item name="fontFamily">@font/ndot57</item>`). MDC often ignores the Android namespace.
* **Tooltip Text Appearances:** Do NOT inherit from `TextAppearance.Material3.Tooltip` as it may not exist in older MDC versions and will cause an AAPT compile error. Inherit from `TextAppearance.Material3.BodySmall` instead.

## 2. Nothing Ketchum SDK Stability
* **GlyphMatrixManager.unInit():** Always wrap `glyphManager?.unInit()` in a `try-catch` block. If the service was never fully registered or is already disconnected, it throws a fatal `IllegalArgumentException` ("Service not registered"). 

## 3. Foreground Services on Android 13+ (API 33+)
* **POST_NOTIFICATIONS:** Starting in Android 13, foreground service notifications will be silently dropped by the OS unless the app explicitly requests the `POST_NOTIFICATIONS` runtime permission. Always ensure `checkSelfPermission` and `requestPermissions` are called for `android.Manifest.permission.POST_NOTIFICATIONS` when enabling background services.
