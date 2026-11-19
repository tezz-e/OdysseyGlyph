# Glyph Odyssey (Odyssey Glyph) - Project Knowledge Base

This document is the permanent single source of truth for the Glyph Odyssey project. Any AI agent or developer working on this codebase must consult this guide to understand project origins, architecture, UI conventions, hardware constraints, and style requirements.

---

## 1. Project Identity & Origin Story

- **Project Name**: Glyph Odyssey (Odyssey Glyph)
- **Application ID**: `com.rickastley.glyphodyssey`
- **Package / Namespace**: `com.example.odysseyglyph`
- **GitHub**: `https://github.com/tezz-e/OdysseyGlyph`
- **Target Hardware**: Nothing Phone (3) / Nothing Phone (2a) developer matrix (25×25 addressable rear monochrome LED array).

### The Origin Story
> "It started as a joke: I wanted to watch the Odyssey trailer 'as Nolan intended,' on the back of my phone, for a meme. Getting one video onto the Glyph Matrix properly took way more fixing and iterating than expected. Somewhere in there I realized the pipeline I'd built didn't care what video it was. So I generalized it."

The app began as an experiment to project Christopher Nolan's *Odyssey* trailer onto the rear LED matrix of a Nothing Phone. Iterating through box-filter downsampling, contrast overdrive, and frame binning revealed that the video rendering pipeline could process any media. This expanded into a complete lyrics engine, live audio visualizer, video-to-matrix converter, and direct matrix signaling suite for Nothing OS.

---

## 2. Core Feature Architecture

### A. Live Lyrics
- **Activities & Services**: `LiveLyricsActivity.kt`, `LiveLyricsToyService.kt`, `TrackMatchPickerActivity.kt`, `SavedLyricsActivity.kt`
- **Playback Tracking**: Uses Android's `MediaSessionManager` and `NotificationListenerService` to detect current media playback (Spotify, YouTube Music, etc.).
- **Lyric Engine**: Queries the `LRCLib` API for timestamped synchronized lyrics (`.lrc`). Lyrics can be imported manually or saved locally in SQLite/Room.
- **Multilingual Support**: Uses Android's on-device ICU `Transliterator` to phonetically convert non-Latin scripts (Japanese Kanji, Korean Hangul, Cyrillic, Arabic) into Latin syllables in real time.
- **True Audio Clock Sync**: Always syncs frames to the hardware audio clock (`mediaPlayer.currentPosition()`). Never use timer ticks or `postDelayed` accumulation for synchronization.
- **Automatic Fallback Visualizer**:
  - Plays automatically during instrumental intros, guitar solos, interludes, or whenever a track has no synced lyrics available.
  - Automatically yields back to scrolling lyrics the exact millisecond vocals resume.

### B. Music Visualizer
- **Activities & Services**: `MainActivity.kt` (Card 2 `Patterns`), `VisualizerService.kt`, `FallbackStyleBottomSheet.kt`
- **Role**: Dedicated standalone audio-reactive visualizer.
- **Distinct Behavior**: Unlike Live Lyrics, this mode **never displays lyrics**. Whatever music or audio is playing, it exclusively renders reactive LED matrix patterns (e.g. Spectrum, Waveform, Beat Pulse, Circular Ripple, Matrix Rain).

### C. Media Import & Presets
- **Activities & Processors**: `MainActivity.kt` (Card 3), `VideoProcessor.kt`, `GalleryActivity.kt`, `CenteredVideoView.kt`, `CropOverlayView.kt`
- **Pipeline**: Converts user videos and GIFs into 25×25 monochrome frame animations.
- **Processing**: Box-filter downsampling, calibrated contrast and brightness overdrive (up to 120% Sun Overdrive), image duration sliders, and frame-rate capping (1-30 FPS).
- **Glyph Toys Slots**: Processed animations can be saved to slots 1–3 (`frames_slot1.bin`, `frames_slot2.bin`, `frames_slot3.bin`) to appear in the Nothing OS Glyph Toys drawer.

### D. Quick Signal
- **Activities & Services**: `QuickSignalActivity.kt`, `QuickSignalToyService.kt`
- **Role**: Projects instant short messages (e.g. WAIT, BRB, symbols like hearts and arrows) or custom text directly onto the rear matrix with configurable auto-off timers.

### E. Phone Controls & System Integration
- **Quick Settings Tile**: `LiveLyricsTileService.kt` allows toggling Live Lyrics directly from the Android quick settings notification shade.
- **Home Screen Widget**: `OdysseyWidgetProvider.kt`, `WidgetToggleActivity.kt` provides one-tap matrix control from the launcher.
- **Housing**: Formerly on the main dashboard, these controls now live in the header's 3-dot overflow menu to keep the main screen clean.

---

## 3. UI/UX Design System (Nothing OS Aesthetics)

### Color Palette
- Scrim / Deep Background: `#050507` (90% obsidian glass)
- Card & Surface Glass: `#141418` (dark obsidian frosted glass)
- Accent Brand Color: `#EA3323` (Nothing Red)
- Specular Borders: `#24FFFFFF` (1dp hairline rim)
- Text Primary: `#FFFFFF`
- Text Subdued: `#B0B0B0` / `#80FFFFFF`

### Typography
- Headings, Badges, Matrix Labels: `ndot57` (`@font/ndot57`, uppercase, letter spacing 0.04 to 0.06)
- Body, Descriptions, UI Controls: `font_geist` (`@font/font_geist`, clean, modern sans-serif)

### Motion & Kinematics
- **Fluid Springs**: `TouchSpringAnimator.kt` uses second-order spring physics:
  - Stiffness $k \approx 380f$
  - Damping ratio $\zeta \approx 0.78f$ (fast settling in ~200ms with zero lingering oscillation)
  - Pressed scale: `0.94f` - `0.96f`, resting scale: `1.0f`
- **Liquid Glass Touch Glow**: Touch contact on cards casts a subtle specular rim glow tracking finger movement; strictly 0.0f at rest (no permanent gradient bands).
- **Logo & 3-Dot Button Touch Interaction**:
  - `GLYPH ODYSSEY` title in `activity_main.xml`: Clickable with spring scale bounce (`0.92f`), tactile keyboard haptics, and a 7-frame digital matrix character scramble glitch (`·`, `:`, `□`, `■`, `▲`, `▼`, `◆`, `0`, `1`, `X`, `+`, `░`, `▒`) flashing Nothing Red.
  - `btnHeaderMenu` (3-dot menu button): Uses square dot-matrix LED pixels and `TouchSpringAnimator` bounce.
  - **Zero circular ripples**: Both `tvHeaderTitle` and `btnHeaderMenu` use `android:background="@null"` so no circular touch ripple overlays distort the Nothing aesthetic.

### Live Lyrics Telemetry & Controls
- **State Badge (`tvMasterState`) & Session Lifecycle**:
  - `OFFLINE` (gray `#888888`) when the master toggle is OFF.
  - `PERMISSION REQUIRED` (`#EA3323`) when Notification Access is missing.
  - `STANDBY` (gray `#888888`) when the toggle is ON and either no media app is active, the app is closed, its playback is stopped (`STATE_STOPPED`/`STATE_NONE`), or its notification was dismissed. Displays "Waiting for music" / "Play a track to begin.".
  - `PAUSED` (gray `#888888`) **ONLY** when a media session exists, its state is `STATE_PAUSED`, it has an active track title, and its media notification is actively visible in the notification shade. Displays "Playback Paused" with track info.
  - `STREAMING` (`#EA3323`) **ONLY** when music is actively playing (`hasActiveSession && isPlaying`) and projecting frames to the rear matrix.
- **Media Session Validation (`MediaSessionListenerService.kt`)**:
  - Controllers in `STATE_NONE`, `STATE_STOPPED`, or `STATE_ERROR` are rejected as inactive.
  - Paused controllers require an active `Notification.CATEGORY_TRANSPORT` / `MediaStyle` notification in `activeNotifications`; if swiped away or app closed, session immediately disconnects and resets via `MusicPlaybackState.clearSession()`.
  - Real-time `onNotificationRemoved` and 500ms safety poll guarantee immediate return to `STANDBY` without stale metadata or stuck "Playback Paused" states.
- **In-Screen Twin Mirror Blanking**:
  - When the master toggle is switched off, or when music pauses/stops, `liveMatrixMirror.clearFrame()` is called and `GlyphFrameBus.postFrame(ByteArray(625))` is broadcasted immediately so the last lyric word never gets stuck on the virtual matrix.
- **Sync Offset Slider & Reset Button**:
  - `sliderSync` (-1000ms to +1000ms, stepSize 50ms): Vibrates on every step point during scrolling/dragging (`slider.vibrateTick()`) for a crisp tactile dial feel.
  - `btnResetSync`: **Only visible when offset $\ne$ 0ms** (`View.VISIBLE`). Automatically hides (`View.GONE`) when offset is 0ms. Tapping it resets slider to 0ms and hides the button.

### Custom Nothing Overflow Menu
- Class: `NothingOverflowMenu.kt`
- Layout: `layout_nothing_overflow_menu.xml`
- Trigger: Header 3-dot button (`btnHeaderMenu`) using `ic_more_vert.xml` drawn with square dot-matrix LED pixels.
- Menu Items:
  1. `Feature Tour`: Launches the interactive spotlight tour.
  2. `Add Quick Settings Tile`: Requests tile addition to system shade.
  3. `Add Home Widget`: Requests launcher widget pinning.
  4. `GitHub Project`: Launches repository in browser.
  5. `About Odyssey Glyph`: Opens the About dialog featuring the origin story. Does NOT include a duplicate feature tour button.

### Interactive Spotlight Tour
- Class: `NothingSpotlightOverlay.kt`
- Mechanism: Full-screen hardware-accelerated overlay punching squircle cutouts (`PorterDuff.Mode.CLEAR`) with glowing Nothing Red rims and connecting chevron arrows.
- Steps:
  1. Live Lyrics (card 1)
  2. Music Visualizer (card 2)
  3. Media Import (card 3)
  4. Quick Signal (card 4 - tooltip adaptive positioning placed above card)
  5. Phone Controls & More (Step 5 targets `btnHeaderMenu`, automatically drops down `NothingOverflowMenu` in a "show-and-tell" style, with tooltip placed below).
- Navigation: Bidirectional (`PREV` and `NEXT`), `SKIP`, animated stadium indicator dots. Moving back from Step 5 automatically dismisses the drop-down menu.

---

## 4. Strict Communication & Writing Rules (Humanizer)

When generating user-facing copy, tutorial strings, dialog text, or release notes, adhere strictly to these principles:
- **Tone**: Plain, natural human voice.
- **Prohibited AI Tells**: Never use words like `actually`, `crucial`, `enhance`, `robust`, `key`, `landscape`, `testament`, `game changer`, `delve`, `tapestry`, `moreover`, `seamless`.
- **Punctuation**: **Never use em-dashes (`—` or `–`)**. Use colons, commas, periods, or parentheses instead.
- **Clarity**: State exactly what a feature does without marketing hype or filler.

---

## 5. Development & Hardware Environments

- **ADB Path (Windows)**: `C:\Users\manee\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Emulator**: `emulator-5554` (Pixel_10_Pro, 1280×2856)
- **Physical Test Device**: `00015365L000249` (Nothing Phone 2a, Metroid A024, 1260×2800)
- **Build Commands**:
  - Unit tests & build: `cd android; .\gradlew.bat testDebugUnitTest assembleDebug`
  - Quick APK assemble: `cd android; .\gradlew.bat assembleDebug`
  - Install to device: `& "C:\Users\manee\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s <device_id> install -r android/app/build/outputs/apk/debug/app-debug.apk`
  - Launch: `& "C:\Users\manee\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s <device_id> shell am start -n com.rickastley.glyphodyssey/com.example.odysseyglyph.MainActivity`
