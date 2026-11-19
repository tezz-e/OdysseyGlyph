# Google Play Store Pre-Release Audit & Technical Readiness Report
**Application:** Glyph Odyssey  
**Application ID:** `com.rickastley.glyphodyssey`  
**Internal Namespace:** `com.example.odysseyglyph`  
**Compile SDK:** 35 | **Target SDK:** 35 (Android 15) | **Min SDK:** 29 / 33  
**Audit Date:** November 20, 2026  
**Auditor:** Teamwork Mobile Quality & Security Engineering Group  
**Document Version:** 1.0.0 (Master Pre-Submission Release Assessment)  

---

## 1. Executive Summary & Readiness Verdict

### 1.1 Readiness Verdict: 🛑 NOT READY FOR PRODUCTION RELEASE (CONDITIONAL)

Following an exhaustive pre-release audit spanning source code architecture, runtime lifecycles, Android 14/15 platform compliance, Google Play Developer policies, Nothing OS industrial design tokens, and Gradle release packaging, **Glyph Odyssey is NOT READY for submission to the Google Play Store**.

While the core functionality is robust, creative, and supported by a passing test suite of **1,249 unit tests (0 failures)**, the application possesses **9 Critical Blockers** and **17 High Severity Defects** that would result in:
1. **Immediate automated rejection by Google Play Store review bots** (missing in-app privacy policy link, declaring unused sensitive permissions `READ_MEDIA_AUDIO`, misuse of `mediaPlayback` foreground service type, and unauthenticated internal component exposure).
2. **Fatal runtime crashes and security exceptions on user devices** running Android 14 and Android 15 (background microphone foreground service start exceptions, `MissingForegroundServiceTypeException` on legacy fallbacks, and native media player session exhaustion).
3. **Complete failure of release build automation** (`./gradlew assembleRelease` and `./gradlew bundleRelease` fail unconditionally due to missing keystore validation, while `./gradlew lint` aborts on 6 fatal errors).
4. **Severe visual brand dissonance** (pervasive rogue red hues `#FF0000`, `#FF4444`, `#FF6655`, `#D71921` violating the canonical Nothing Red `#EA3323`, erratic border stroke opacities, and 129+ hardcoded UI strings with zero internationalization).
5. **Intellectual property and credential exposure** (unobfuscated plaintext bytecode due to disabled R8/ProGuard, and developer workstation filesystem paths leaked in tracked `local.properties`).

---

### 1.2 Critical Blockers Summary Table

| Finding ID | Requirement | Component / File | Failure Mode / Policy Violation | Mandatory Remediation |
|---|---|---|---|---|
| **R3-01** | Play Compliance | `layout_nothing_overflow_menu.xml`, `strings.xml` | **Immediate Store Rejection**: Complete absence of an in-app Privacy Policy link while requesting sensitive permissions (`RECORD_AUDIO`, `NotificationListenerService`). | Add Privacy Policy item to custom 3-dot overflow menu, strings resource, and launch via `CustomTabsIntent`. |
| **R3-02** / **R2-01** | Android 14/15 Policy & Lifecycle | `VisualizerService.kt:134-184`, `LiveLyricsService.kt:266-284`, `AndroidManifest.xml:133-141` | **Runtime Security Crash**: Declaring `mediaPlayback` without playing media; launching `microphone` FGS from background triggers `ForegroundServiceStartNotAllowedException`; dynamic switch to mic styles never issues FGS type promotion. | Align FGS types to `specialUse`/`connectedDevice`; dynamically call `startForeground()` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` only when mic styles activate; catch background start exceptions. |
| **R2-13** | Architecture / UI Logic | `MainActivity.kt:75, 163, 915, 930, 995` | **Broken Core UX / Unstoppable Thread**: Inverted `isRendering` boolean logic causes back-press handler to evaluate to `false` during active video rendering, permanently preventing user cancellation of heavy background encoding threads. | Decouple UI rendering state from cancellation token; pass dedicated `isRenderCancelled` AtomicBoolean to `VideoProcessor`. |
| **R2-17** | Resource Hygiene | `CenteredVideoView.kt:14, 104`, `MainActivity.kt:315-319` | **Native Session Leak / Crash**: `CenteredVideoView` instantiates native `MediaPlayer` but `MainActivity.onDestroy()` never calls `videoView.release()`. Screen rotations abandon sessions in `mediaserver`, causing error `(1, -19)` and media playback death. | Explicitly invoke `binding.videoPreview.release()` in `MainActivity.onDestroy()`. |
| **R2-18** | Architecture / Memory | `GalleryActivity.kt:128-174` | **ANR & OutOfMemoryError**: Tapping a preset synchronously parses full `.bin` file on Main UI thread, instantiating hundreds of 25×25 `Bitmap` objects in a loop without recycling, freezing UI and triggering OOM. | Decode frames off the UI thread; render frames onto a single reusable 25×25 mutable `Bitmap`; clear callbacks on dismiss. |
| **R5-01** | Packaging / Build | `android/app/build.gradle:22-36` | **Release Build Failure**: `signingConfigs.release` unconditionally asserts `release.jks`. `./gradlew assembleRelease` and `./gradlew bundleRelease` fail immediately at `:app:validateSigningRelease`. | Guard release signing config with `if (signingConfigs.release.storeFile?.exists())` to allow unsigned/CI test release builds. |
| **R5-02** / **R3-09** | Build Quality / Lint | `SnackbarUtils.kt:9, 24`, `odyssey_widget.xml:16`, `MainActivity.kt:1008` | **Fatal Lint Gate Failure**: `./gradlew lint` aborts with exit code 1 due to 6 fatal errors (4 `RestrictedApi` violations, 1 `UseAppTint`, 1 `UnsafeImplicitIntentLaunch`). | Cast `this.view` to `ViewGroup` instead of `SnackbarLayout`; add `tools:ignore="UseAppTint"`; set explicit package on `RELOAD_FRAMES` broadcast. |
| **R5-03** | Repository Security | `android/local.properties`, `android/app/local.properties`, `.gitignore` | **Confidentiality / CI Failure**: `local.properties` containing absolute developer paths (`sdk.dir=C\:\\Users\\manee\\...`) is committed in Git and omitted from `.gitignore`. Pollutes multi-platform CI builds. | Untrack files via `git rm --cached`, scrub Git history, and add `**/local.properties` to root `.gitignore`. |
| **R3-03** | Security / Attack Surface | `AndroidManifest.xml:38, 80, 86, 90, 95` | **Security Breach**: 5 internal activities exported without intent filters or permissions. Malicious apps can trigger matrix flashes or corrupt user offline lyric database. | Explicitly set `android:exported="false"` on all non-launcher activities. |

---

### 1.3 Audit Findings Breakdown by Severity & Requirement

| Requirement Area | Critical | High | Medium | Low | Total Findings |
|---|:---:|:---:|:---:|:---:|:---:|
| **R2: Code Hygiene, Architecture & Lifecycle** | 4 | 6 | 7 | 4 | **21** |
| **R3: Google Play Compliance & Security** | 2 | 4 | 3 | 2 | **11** |
| **R4: UI/UX & Design System Consistency** | 0 | 2 | 2 | 3 | **7** |
| **R5: Build Config, Packaging & Repo Readiness** | 3 | 4 | 3 | 3 | **13** |
| **Consolidated Overall (Deduplicated)** | **9** | **15** | **14** | **11** | **49** |

---

## 2. Finding Classification Schema

To ensure objective evaluation and align with Google Play submission standards, every finding in this report is classified according to the following four-tier schema:

```
+------------------------------------------------------------------------------------+
| SEVERITY LEVEL | PLAY STORE & RUNTIME IMPACT DEFINITION                            |
+------------------------------------------------------------------------------------+
| CRITICAL       | Flaws that guarantee immediate Google Play Store rejection, unhandled|
|                | fatal crashes/ANRs in primary user flows, platform security         |
|                | violations, or fatal Gradle build/lint failures blocking release.   |
+------------------------------------------------------------------------------------+
| HIGH           | Severe resource leaks (sockets, audio HAL, MediaServer handles),   |
|                | major architectural defects, broken core features, over-declared    |
|                | sensitive permissions, or brand-damaging design token divergence.  |
+------------------------------------------------------------------------------------+
| MEDIUM         | Edge-to-edge UI occlusion, non-fatal lint warnings, missing error   |
|                | streams, priority inversions, unthrottled background wakeups, or    |
|                | unlocalized user interface strings.                                |
+------------------------------------------------------------------------------------+
| LOW            | Non-critical cosmetic misalignments, minor asset bloat, code style   |
|                | improvements, deprecation warnings, or repository file hygiene.     |
+------------------------------------------------------------------------------------+
```

---

## 3. Requirement-by-Requirement Technical Audit Sections

### Section 1: Requirement R2 — Code Hygiene, Architecture, and Lifecycle Review

#### 1.1 Background Services Lifecycle & Concurrency
The application relies heavily on long-running foreground and background services to drive Nothing OS rear Glyph LED matrices: `LiveLyricsService`, `VisualizerService`, `OdysseyToyService`, and `MediaSessionListenerService`. Several severe lifecycle and hardware resource management flaws were identified:

##### [CRITICAL] Finding R2-01: Foreground Service Type Mismatch During Dynamic Style Switching
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/VisualizerService.kt:134–153, 173–184`
- **Failure Mode:** At initial startup in `onStartCommand()`, `VisualizerService` checks whether `activeStyle` requires the microphone. If the user starts with an ambient/math pattern (e.g., style 1 "Ambient Math"), the service starts as `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. When the user enters settings and selects a mic-reactive pattern (e.g., style 2 "Mic Wave"), `onSharedPreferenceChanged` immediately spins up `visualizerHelper.start()`, capturing raw PCM audio from the hardware microphone. However, `startForeground()` is **never re-invoked** to dynamically promote the service to include `FOREGROUND_SERVICE_TYPE_MICROPHONE`.
- **Platform Risk:** On Android 14+ (targetSdk 35), accessing microphone buffers from a foreground service without an active `microphone` FGS declaration triggers an immediate `SecurityException` or is permanently muted by Android AppOps privacy guards.
- **Remediation:** In `VisualizerService.onSharedPreferenceChanged`, whenever `activeStyle` transitions to a microphone style:
  ```kotlin
  val hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
  if (isMicStyle(activeStyle) && hasMicPerm) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          ServiceCompat.startForeground(
              this,
              NOTIFICATION_ID,
              buildNotification(),
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
          )
      }
      visualizerHelper.start()
  } else {
      visualizerHelper.stop()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          ServiceCompat.startForeground(
              this,
              NOTIFICATION_ID,
              buildNotification(),
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
          )
      }
  }
  ```

##### [HIGH] Finding R2-02: Hardware Microphone Churn and AudioRecord Thrashing in 60Hz Playback Loop
- **Location:** `LiveLyricsService.kt:122–126, 165–169, 190` & `AudioVisualizerHelper.kt:31–66, 73–75`
- **Failure Mode:** In `LiveLyricsService.playbackRunnable`, when `frameData == null` (such as instrumental gaps between lyric lines), the service starts `visualizerHelper.start()`. As soon as the next lyric line appears, line 190 immediately executes `visualizerHelper.stop()`. `AudioVisualizerHelper.start()` allocates a new `AudioRecord` instance, claims exclusive OS microphone hardware, starts an unmanaged raw JVM thread, and begins FFT processing. `stop()` halts the hardware and nulls the reference. Rapidly starting and stopping hardware audio capture on every lyric boundary causes severe audio HAL thrashing, thread allocation spikes, audio buffer underruns, and native crashes if `audioRecord.release()` occurs while the thread is blocked inside `audioRecord.read()`.
- **Remediation:** 
  1. Add a debounce threshold (minimum 1500ms of continuous silence/instrumental gap) before spinning up `AudioVisualizerHelper`.
  2. Implement thread synchronization in `AudioVisualizerHelper` using an atomic running flag and worker thread join before releasing `AudioRecord`.
  3. Keep `AudioRecord` allocated in a paused/standby state rather than tearing down and rebuilding the native audio HAL pipeline.

##### [HIGH] Finding R2-03: MediaSessionManager Listener Leak in NotificationListenerService
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/MediaSessionListenerService.kt:132–135, 141–145`
- **Failure Mode:** In `onListenerConnected()`, an anonymous listener lambda `{ newSessions -> updateActiveSessions(newSessions) }` is registered with `mediaSessionManager.addOnActiveSessionsChangedListener(..., componentName)`. In `onListenerDisconnected()` and `onDestroy()`, `removeOnActiveSessionsChangedListener` is never called. Android repeatedly cycles `NotificationListenerService` connections during package updates, DND toggles, and configuration changes; each reconnection permanently leaks an anonymous listener in the system `MediaSessionManager`. Furthermore, `onDestroy()` does not unregister callbacks from `activeController` or cancel `positionSyncRunnable`.
- **Remediation:** Store `OnActiveSessionsChangedListener` as a private field, call `mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)` in `onListenerDisconnected()`, and implement `onDestroy()` to call `disconnectActiveSession()` and cancel pending handler callbacks.

##### [HIGH] Finding R2-04: BaseToyService Playback Loop & Handler Leak on Service Termination
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/OdysseyToyService.kt:114–122, 149–152`
- **Failure Mode:** `mainHandler.removeCallbacks(playbackRunnable)`, `glyphManager?.turnOff()`, `glyphManager?.unInit()`, and `mediaPlayer?.release()` are located strictly inside `onUnbind()`. If the OS terminates or stops the service directly without unbinding (or after multiple bind cycles), `onDestroy()` only unregisters `broadcastReceiver`. `playbackRunnable` remains scheduled on `mainHandler` at 50Hz, causing an indefinite memory leak of the service and continuous CPU wakeups.
- **Remediation:** Move all cleanup logic (`mainHandler.removeCallbacksAndMessages(null)`, `glyphManager?.unInit()`, `mediaPlayer?.release()`) into `onDestroy()`. In `onUnbind()`, return `false` or guarantee full state teardown.

##### [HIGH] Finding R2-05: Synchronous MediaPlayer.prepare() on UI Thread in Toy Service
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/OdysseyToyService.kt:176–191`
- **Failure Mode:** Inside `loadFramesAndStart()`, initializing the audio track executes `mainHandler.post { mediaPlayer.prepare() }`. Calling synchronous `prepare()` on Android's Main Looper parses audio headers and performs disk I/O on the UI thread, causing main thread hitches and risking ANRs.
- **Remediation:** Switch to `mediaPlayer.prepareAsync()` and trigger playback strictly inside `setOnPreparedListener`.

##### [MEDIUM] Finding R2-06: Delayed stopSelf() with Notification Removed in LiveLyricsService
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/LiveLyricsService.kt:323–329`
- **Failure Mode:** In `STOP_LIVE_LYRICS`, `stopForeground(STOP_FOREGROUND_REMOVE)` is executed immediately, stripping foreground protection, while `stopSelf()` is delayed by 600ms via `mainHandler.postDelayed(stopRunnable, 600)`. During this 600ms window, the service runs as a bare background service. If the app process has no visible activities, this violates Android 14 background execution limits and may trigger process termination or lifecycle desynchronization if a new start intent arrives.
- **Remediation:** Execute `stopSelf()` immediately without artificial delays, or retain the foreground notification until `stopSelf()` is executed.

---

#### 1.2 LRCLib Synced Lyrics Client, Network Resilience & Offline Cache

##### [HIGH] Finding R2-07: Socket & Stream Leaks in LRCLibClient via Unclosed HttpURLConnection
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/LRCLibClient.kt:82–93, 108–122, 132–147`
- **Failure Mode:** `openConnection` instantiates `HttpURLConnection` across `getExactLyrics`, `searchCandidates`, and `searchLyrics`. Neither `conn.disconnect()` nor error stream closing is implemented in `finally` blocks. On non-200 responses (e.g., 404 Not Found, 429 Rate Limit, 500 Internal Error) or read exceptions, connection handles and error streams remain open. Over extensive music playback sessions, pooled sockets leak, leading to socket exhaustion (`SocketException: Too many open files`) and total network failure.
- **Remediation:** Wrap all connection and stream operations in standard `try-catch-finally` blocks: ensure `BufferedReader` is closed via `.use { }`, consume/close `conn.errorStream`, and explicitly call `conn.disconnect()` in a `finally` block.

##### [HIGH] Finding R2-08: Premature Instrumental Gap Lyric Blanking in LrcUtils
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/LrcUtils.kt:72`
- **Failure Mode:** `LrcUtils.getFrameTextAtTime` calculates `lineDuration = nextTimeMs - currentLyric.first`. Line 72 states:
  ```kotlin
  if (timeSinceStart < 0 || timeSinceStart > lineDuration || lineDuration > 10000f) return null
  ```
  If a song has a guitar solo or bridge longer than 10 seconds following a lyric line, `lineDuration > 10000f` evaluates to `true` throughout the **entire duration** of that lyric line. As a result, the lyric line is blanked from start to finish and never rendered to the Glyph Matrix.
- **Remediation:** Modify the condition to evaluate `timeSinceStart`: display the lyric line for its standard duration (or up to ~5000ms), and only blank during the trailing instrumental portion:
  ```kotlin
  if (timeSinceStart < 0 || timeSinceStart > lineDuration || (timeSinceStart > 5000L && lineDuration > 10000f)) return null
  ```

##### [HIGH] Finding R2-09: Synchronous SQLite Disk I/O on Android Main/UI Thread
- **Location:** `SavedLyricsRepository.kt:35–52, 179–198`, `LiveLyricsService.kt:476`, `LiveLyricsToyService.kt:241`, `SavedLyricsActivity.kt:47`, `TrackMatchPickerActivity.kt:172`
- **Failure Mode:** `SavedLyricsRepository.findMatch` and `getAllMatches` open and query `dbHelper.readableDatabase` synchronously. These methods are invoked directly on the Main UI thread from activity lifecycle methods (`onCreate`, `renderNowPlayingInfo`) and service track change callbacks (`onTrackIdentityChanged`). This causes `StrictMode.DiskReadViolation` and frame drops on track changes.
- **Remediation:** Offload database lookups to background dispatchers (`Dispatchers.IO` or a single-threaded executor), returning results asynchronously via callbacks or Kotlin `Flow`.

##### [MEDIUM] Finding R2-10: Destructive Database Upgrade Strategy in SavedLyricsDbHelper
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/SavedLyricsDbHelper.kt:32–35`
- **Failure Mode:** `onUpgrade` executes `DROP TABLE IF EXISTS saved_lyrics` and calls `onCreate(db)`. If the database schema is updated in subsequent releases, all user-verified matches, imported custom `.lrc` files, and confidence records will be permanently erased.
- **Remediation:** Implement incremental schema migrations using `ALTER TABLE` or migration scripts rather than dropping the table on upgrade.

##### [MEDIUM] Finding R2-11: Unbounded Growth and Lack of Cache Eviction in Saved Lyrics Database
- **Location:** `SavedLyricsRepository.kt:91–116` & `SavedLyricsDbHelper.kt`
- **Failure Mode:** Every song that plays and auto-matches is permanently written into `glyph_saved_lyrics.db` via `saveAutoVerifiedMatch`. There is no table entry cap, expiration time (TTL), or LRU eviction for auto-verified matches (`MATCH_TYPE_AUTO_VERIFIED`). Over months of normal music playback, thousands of full synced lyric text records accumulate indefinitely.
- **Remediation:** Add an LRU eviction query in `saveRecord`: keep all user-selected (`MATCH_TYPE_USER_SELECTED`) and imported (`MATCH_TYPE_IMPORTED_LRC`) matches permanently, but cap auto-verified matches to a maximum (e.g., 500 most recently played tracks).

##### [LOW] Finding R2-12: Non-Standard Placeholder User-Agent in LRCLibClient
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/LRCLibClient.kt:24`
- **Failure Mode:** `USER_AGENT` is configured with a placeholder URL: `OdysseyGlyphApp/2.0 (https://github.com/example/OdysseyGlyph)`. LRCLib API policies require client identification to maintain public API access. Using dummy URLs risks API rate limiting or IP banning.
- **Remediation:** Update the User-Agent header to reference the official application repository: `GlyphOdyssey/2.0.0 (https://github.com/Nothing-Developer-Programme/glyph-odyssey)`.

---

#### 1.3 Matrix Frame Processor & Hardware Decoding Engine

##### [CRITICAL] Finding R2-13: Inverted Cancellation Logic in MainActivity Video Processing
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/MainActivity.kt:75, 163, 915, 930, 995`
- **Failure Mode:** `MainActivity` declares `isRendering = AtomicBoolean(false)`.
  - In `startProcessing()`, line 930 calls `isRendering.set(false)`, and line 995 passes `isCancelled = isRendering` to `VideoProcessor.processMedia`.
  - In `cancelProcessing()`, line 915 calls `isRendering.set(true)`.
  - In `onBackPressedDispatcher`:
    ```kotlin
    if (isRendering.get()) {
        cancelProcessing()
    }
    ```
  Because `isRendering` is set to `false` during active rendering, `isRendering.get()` evaluates to `false` when the user presses Back during video rendering! The back press fails to call `cancelProcessing()`, and the heavy background rendering thread continues running. Furthermore, `isRendering` is never reset in `onComplete`.
- **Remediation:** Decouple active rendering state from the cancellation token:
  ```kotlin
  private val isRenderCancelled = AtomicBoolean(false)
  private var isRenderingActive = false

  private fun startProcessing(...) {
      isRenderCancelled.set(false)
      isRenderingActive = true
      // pass isRenderCancelled to VideoProcessor.processMedia
  }

  private fun cancelProcessing() {
      isRenderCancelled.set(true)
      isRenderingActive = false
  }

  // in onBackPressed:
  if (isRenderingActive) {
      cancelProcessing()
  }
  ```

##### [MEDIUM] Finding R2-14: Thread Priority Inversion in Hardware Video Decoder Worker Pool
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/HardwareVideoDecoder.kt:58–65`
- **Failure Mode:** `HardwareVideoDecoder` instantiates a fixed thread pool of up to 8 threads and sets worker priority to:
  `android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)`
  `THREAD_PRIORITY_DISPLAY` (-4) is intended solely for the UI thread and compositor. Assigning this priority to up to 8 parallel CPU workers performing heavy spatial downsampling causes CPU starvation of the app's Main Looper and RenderThread, leading to severe UI jank, frame drops, and input latency.
- **Remediation:** Change worker thread priority to `android.os.Process.THREAD_PRIORITY_BACKGROUND` (10) or `THREAD_PRIORITY_DEFAULT` (0).

##### [MEDIUM] Finding R2-15: Missing Output Image Release on Downsampling Exception
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/HardwareVideoDecoder.kt:173–183`
- **Failure Mode:** `val image = decoder.getOutputImage(outIndex)` is extracted and closed synchronously via `image.close()` after `extractCropPixels`. If `extractCropPixels` throws an unchecked exception (such as `BufferUnderflowException` or invalid stride calculation), `image.close()` is skipped before entering the `finally` block. Leaving an acquired `Image` open pins the `MediaCodec` output buffer, causing the decoder pipeline to stall indefinitely.
- **Remediation:** Enclose image extraction in a `try-finally` block: `image.use { ... }` or `try { extractCropPixels(...) } finally { image.close() }`.

##### [MEDIUM] Finding R2-16: Binary Header Inconsistency and Audio Offset Loss
- **Location:** `VideoProcessor.kt:514, 716` & `OdysseyToyService.kt:240–257`
- **Failure Mode:** `VideoProcessor.processMedia` allocates a 12-byte header (V2 format: `frameCount`, `fps`, `playbackMode`). In contrast, `processLyrics` allocates a 16-byte header (V3 format: adding `audioOffsetMs`). When `processMedia` extracts video with attached audio, it writes the 12-byte header, omitting `audioOffsetMs`. `BaseToyService.readFramesAsset` reads V2 files with default `audioOffsetMs = 0`, causing audio-to-video synchronization offset to be lost.
- **Remediation:** Unify `processMedia` to write the standard 16-byte V3 header with `audioOffsetMs` included.

---

#### 1.4 State Management, Resource Cleanup, and Memory Leaks

##### [CRITICAL] Finding R2-17: Native MediaPlayer Handle Leak in CenteredVideoView
- **Location:** `CenteredVideoView.kt:14, 104` & `MainActivity.kt:315–319`
- **Failure Mode:** `CenteredVideoView` instantiates `val mediaPlayer = MediaPlayer()`. Although `CenteredVideoView` exposes a `release()` method, `MainActivity.onDestroy()` NEVER invokes `videoView.release()`. On every configuration change (screen rotation, split-screen mode, foldable unfold), the activity is destroyed and recreated, abandoning the native `MediaPlayer` instance in Android's `mediaserver`. After multiple rotations, the system runs out of media player sessions, resulting in `MediaPlayer` error `(1, -19)` and total failure to play media until process restart.
- **Remediation:** Add `binding.videoPreview.release()` inside `MainActivity.onDestroy()`.

##### [CRITICAL] Finding R2-18: Large Preset Dialog Bitmap Leak and UI Thread Freeze
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/GalleryActivity.kt:128–174`
- **Failure Mode:** When a user taps a preset in `GalleryActivity`, `onPresetClicked` runs synchronously on the Main UI thread. It reads the entire `.bin` file into memory (which can be >1MB for 3-minute lyrics) and executes a loop creating hundreds or thousands of 25×25 `Bitmap` objects (`Bitmap.createBitmap`) directly on the UI thread, causing UI freezes and potential ANRs. Furthermore, upon `dialog.dismiss()`, `frames` (holding thousands of Bitmaps) is never recycled, and scheduled `handler.postDelayed` callbacks are not cleared, causing heap bloat and `OutOfMemoryError` (OOM).
- **Remediation:** 
  1. Do not pre-decode all frames into a list of separate Bitmaps. Use a single reusable 25×25 mutable `Bitmap`, updating its pixels on each tick.
  2. In `dialog.setOnDismissListener`, call `handler.removeCallbacksAndMessages(null)` and recycle preview resources.

##### [MEDIUM] Finding R2-19: Synchronous Disk I/O and Bitmap Allocation in RecyclerView Scrolling
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/GalleryActivity.kt:310–334`
- **Failure Mode:** In `PresetAdapter.onBindViewHolder`, the adapter opens a `FileInputStream` to read `frames.bin`, allocates byte buffers, and instantiates a new `Bitmap` synchronously on the UI thread for every item as the user scrolls. This induces noticeable scrolling stutter and dropped frames.
- **Remediation:** Implement an in-memory thumbnail cache (`LruCache<String, Bitmap>`) and load thumbnails asynchronously off the main thread.

##### [MEDIUM] Finding R2-20: Persistent 60Hz Frame Bus UI Callbacks from Background Activities
- **Location:** `LiveLyricsActivity.kt:103, 386` & `QuickSignalActivity.kt:76, 364`
- **Failure Mode:** `LiveLyricsActivity` and `QuickSignalActivity` register their `frameBusSubscription` in `onCreate()` and only unsubscribe in `onDestroy()`. When these activities are in the background (user pressed Home or switched apps), background services continue posting frames at 60Hz to `GlyphFrameBus`. The subscriptions continuously execute `runOnUiThread { ... }` tasks on the Main Looper, waking the CPU and scheduling unnecessary invalidations for non-visible views, draining battery.
- **Remediation:** Move `GlyphFrameBus.subscribe` to `onStart()` / `onResume()` and unsubscribe in `onStop()` / `onPause()`.

##### [LOW] Finding R2-21: Concurrency and Thread Safety in MusicPlaybackState
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/MusicPlaybackState.kt:7–23, 44–53, 110, 136`
- **Failure Mode:** `MusicPlaybackState.listeners` is a `mutableListOf<StateChangeListener>()`. While notifications iterate over `synchronized(listeners) { listeners.toList() }`, `addListener` and `removeListener` do not synchronize on `listeners`. If a listener is registered or unregistered while state updates occur, a `ConcurrentModificationException` can be thrown. Furthermore, shared mutable state fields (`trackTitle`, `isPlaying`, `position`, `hasActiveSession`) lack `@Volatile` annotations, creating memory barrier visibility delays across binder and main threads.
- **Remediation:** Use `CopyOnWriteArrayList<StateChangeListener>` for `listeners` or synchronize `addListener` / `removeListener` on `listeners`. Mark shared mutable primitive fields as `@Volatile`.

---

### Section 2: Requirement R3 — Google Play Store Compliance and Security Review

#### 2.1 Permissions, User Data Access & Mandatory Privacy Policy Disclosures

##### [CRITICAL] Finding R3-01: Complete Absence of In-App Privacy Policy Link
- **Location:** `layout_nothing_overflow_menu.xml`, `strings.xml`, `NothingOverflowMenu.kt`
- **Failure Mode & Policy Violation:** Google Play Developer Policy explicitly requires:
  > *"Apps that handle personal and sensitive user data (including microphone access via `RECORD_AUDIO` and notification metadata via `NotificationListenerService`) must post a privacy policy in both the designated field in the Play Console AND within the app itself."*
  An exhaustive regex search (`privacy|policy`) confirmed there is **zero privacy policy link, dialog, or menu item anywhere within the application**. Submitting an app requesting `RECORD_AUDIO` and `BIND_NOTIFICATION_LISTENER_SERVICE` without an in-app privacy policy guarantees **immediate automated rejection** by Google Play Review bots.
- **Remediation:** 
  1. Add a "Privacy Policy" menu entry in `layout_nothing_overflow_menu.xml`.
  2. Define `privacy_policy_url` in `res/values/strings.xml` pointing to the public privacy document.
  3. In `NothingOverflowMenu.kt`, hook `itemMenuPrivacy` and trigger a `CustomTabsIntent` or `Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url)))`.

##### [HIGH] Finding R3-04: Unused Sensitive Permission Declaration (`READ_MEDIA_AUDIO`)
- **Location:** `android/app/src/main/AndroidManifest.xml:5`
- **Failure Mode & Policy Violation:** `<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />` is declared in the manifest. Static code analysis verifies that `READ_MEDIA_AUDIO` is never checked or requested. Media selection in `MainActivity.kt:126` uses `ActivityResultContracts.OpenDocument()`, which delegates file picking to the system `DocumentsUI` provider and grants scoped URI read permissions via `Intent.FLAG_GRANT_READ_URI_PERMISSION`. Google Play Developer Policy strictly forbids declaring sensitive media permissions that the app does not actively utilize.
- **Remediation:** Remove `<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />` from `AndroidManifest.xml`.

##### [HIGH] Finding R3-05: Inconsistent and Circumventable NotificationListenerService Prominent Disclosure
- **Location:** `MainActivity.kt:500–517` & `LiveLyricsActivity.kt:345–347`
- **Failure Mode & Policy Violation:** Google Play User Data policy requires that before requesting special access (such as Notification Listener), the app must present a prominent in-app disclosure explaining what data is collected, how it is used, and confirming that personal messages/notifications are not stored or transmitted.
  - While `ProminentDisclosureDialog` exists, `MainActivity.kt:500–517` (when clicking Visualizer) displays a completely different ad-hoc dialog (`dialog_nothing_onboarding`) with hardcoded text lacking the required privacy guarantees.
  - In `LiveLyricsActivity.kt:345–347`, clicking `btnGrantPermission` calls `startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))` directly without showing any disclosure dialog.
- **Remediation:** Standardize all notification listener access checks to invoke `ProminentDisclosureDialog.showIfRequired(context, onAccepted)`.

##### [HIGH] Finding R3-06: Missing Prominent Disclosure & Rationale for `RECORD_AUDIO`
- **Location:** `MainActivity.kt:529–543`, `LiveLyricsActivity.kt:321–327`, `FallbackStyleBottomSheet.kt:30–34`
- **Failure Mode & Policy Violation:** When a user selects an audio-reactive visualizer style (e.g., style 2 "Audio Wave", style 3 "Subwoofer", style 5 "Pulse Ring", style 8 "Beat Wave"), the app triggers `requestPermissions(arrayOf(RECORD_AUDIO), 101)` abruptly with zero rationale. Under Google Play policies, dangerous permissions whose purpose is not immediately obvious from core app function must provide a pre-permission disclosure explaining that audio is captured in real-time volatile memory solely for FFT visualization and is never recorded or transmitted.
- **Remediation:** Implement a dedicated audio disclosure modal or integrate an audio rationale into `FallbackStyleBottomSheet` before firing the permission request contract.

---

#### 2.2 Target SDK 34/35+ Platform Policies & Foreground Service Restrictions

##### [CRITICAL] Finding R3-02: Foreground Service Type Mismatch (`mediaPlayback`) & Android 14+ Background Microphone Restrictions
- **Location:** `AndroidManifest.xml:133–141`, `LiveLyricsService.kt:266–284`, `VisualizerService.kt:134–152`, `LiveLyricsTileService.kt:51–65`, `WidgetToggleActivity.kt:40–52`
- **Failure Mode & Policy Violation:**
  1. **Google Play Policy Mismatch:** `LiveLyricsService` and `VisualizerService` specify `android:foregroundServiceType="mediaPlayback|microphone"`. Google Play's Android 14 FGS policy strictly defines `mediaPlayback` as: *"For apps that play audio or video in the background"*. Glyph Odyssey does not play audio or video; it only receives playback state metadata or captures microphone FFT. Declaring `mediaPlayback` without playback functionality causes Play Store submission rejection.
  2. **Android 14 Background Microphone Ban:** On targetSdk 34+, Android introduces strict background restrictions on `FOREGROUND_SERVICE_TYPE_MICROPHONE`. If an app attempts to call `startForeground()` requesting `microphone` from the background (e.g. via `LiveLyricsTileService` when tapped from notification shade, or from `WidgetToggleActivity` on home screen), Android throws `SecurityException` / `ForegroundServiceStartNotAllowedException`.
  3. **Over-requesting Microphone in Live Lyrics:** In `LiveLyricsService.kt:268`, if `RECORD_AUDIO` was ever granted, the service unconditionally appends `FOREGROUND_SERVICE_TYPE_MICROPHONE` upon start, even when displaying pure text synced lyrics.
  4. **Fallback Crash:** The fallback catch block in `LiveLyricsService.kt:279` and `VisualizerService.kt:147` invokes `startForeground(NOTIFICATION_ID, notification)` (the legacy 2-argument overload). On targetSdk 34+, calling the 2-argument method when FGS types are declared in the manifest throws `android.app.MissingForegroundServiceTypeException`.
- **Remediation:**
  1. In `LiveLyricsService.kt`, only append `FOREGROUND_SERVICE_TYPE_MICROPHONE` if `fallbackEnabled` is true AND `isMicStyle(fallbackStyle)` is true.
  2. For background invocations (Quick Settings tile, AppWidget), catch `ForegroundServiceStartNotAllowedException` / `SecurityException` and start the service with non-microphone types or prompt the user with a Notification pending intent to open the activity.
  3. Align `LiveLyricsService` and `VisualizerService` with `specialUse` (matrix LED hardware driving) or `connectedDevice`, providing the Play Console submission video demonstrating outward hardware LED signaling.

##### [MEDIUM] Finding R3-07: Legacy Permission Flow Without Callbacks and Immediate FGS Launch
- **Location:** `LiveLyricsActivity.kt:159–174` & `MainActivity.kt:537–543`
- **Failure Mode:** In `LiveLyricsActivity.kt:162`, `requestPermissions` for `POST_NOTIFICATIONS` is asynchronous. On line 171, the app immediately executes `startForegroundService()` on the next line without awaiting the user's response. No `onRequestPermissionsResult` is implemented. If the user denies or ignores the notification prompt, the foreground service notification may be silently dropped by Android 13+.
- **Remediation:** Migrate to AndroidX `registerForActivityResult(ActivityResultContracts.RequestPermission())` and trigger service launch inside the callback.

##### [MEDIUM] Finding R3-08: Unsafe Implicit Internal Broadcast in MainActivity.kt
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/MainActivity.kt:1008`
- **Failure Mode & Lint Violation:** Lint Error `[UnsafeImplicitIntentLaunch]`:
  `sendBroadcast(Intent("com.example.odysseyglyph.RELOAD_FRAMES"))`
  On Android 14+ (targetSdk 34+), sending an implicit broadcast to an internal non-exported receiver is blocked and triggers a runtime `SecurityException` on future Android releases.
- **Remediation:** Explicitly specify the package:
  ```kotlin
  sendBroadcast(Intent("com.example.odysseyglyph.RELOAD_FRAMES").setPackage(packageName))
  ```

##### [MEDIUM] Finding R3-09: Restricted Material API Access in SnackbarUtils.kt
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/SnackbarUtils.kt:9, 24`
- **Failure Mode & Lint Violation:** Lint Error `[RestrictedApi]`:
  `Snackbar.SnackbarLayout` and `SnackbarBaseLayout.setLayoutParams` belong to the internal `LIBRARY_GROUP` scope of `com.google.android.material`. Invoking restricted APIs breaks `./gradlew lint` builds and risks runtime `NoSuchMethodError` / `IncompatibleClassChangeError` across Material library upgrades.
- **Remediation:** Cast `this.view` to standard `android.view.ViewGroup` instead of `Snackbar.SnackbarLayout`:
  ```kotlin
  val layout = this.view as ViewGroup
  val params = layout.layoutParams as? ViewGroup.MarginLayoutParams
  ```

---

#### 2.3 Component Export Security & Attack Surface

##### [HIGH] Finding R3-03: Internal Activities Exported Without Intent Filters or Permissions
- **Location:** `AndroidManifest.xml:38–40, 80–84, 86–89, 90–94, 95–99`
- **Failure Mode & Security Vulnerability:**
  The following five internal activities are marked `android:exported="true"` without defining an `<intent-filter>` or requiring permissions:
  1. `MainActivity` (Line 38)
  2. `LiveLyricsActivity` (Line 80)
  3. `TrackMatchPickerActivity` (Line 86)
  4. `SavedLyricsActivity` (Line 90)
  5. `QuickSignalActivity` (Line 95)
  Only `SplashActivity` is the true launcher entry point. Under the Android OS security model, any third-party app installed on the device can directly launch `QuickSignalActivity` to flash unauthorized LEDs, launch `TrackMatchPickerActivity` to alter user cached offline lyrics, or bypass splash/onboarding guards.
- **Remediation:** Explicitly set `android:exported="false"` on all five activities in `AndroidManifest.xml`.

##### [LOW] Finding R3-10: Application Data Backup Security (allowBackup="true")
- **Location:** `android/app/src/main/AndroidManifest.xml:16`
- **Failure Mode:** `android:allowBackup="true"` allows full extraction of the application's SharedPreferences (`OdysseyPrefs`) and SQLite database (`SavedLyricsDbHelper`) via `adb backup` on rooted or developer-unlocked devices.
- **Remediation:** Set `android:allowBackup="false"` or configure `android:dataExtractionRules` and `android:fullBackupContent`.

##### [LOW] Finding R3-11: Hardcoded Test Nothing SDK Key
- **Location:** `android/app/src/main/AndroidManifest.xml:23–25`
- **Failure Mode:** `<meta-data android:name="NothingKey" android:value="test" />` is declared. Prior to production distribution on Google Play Store, developer verification with the Nothing Developer Programme is recommended to confirm whether a production developer key is required for commercial releases.
- **Remediation:** Inject release keys via `manifestPlaceholders` in `build.gradle`.

---

#### 2.4 Edge-to-Edge Display Enforcement, Window Insets & Bottom Sheet Occlusions

##### [MEDIUM] Finding R4-04: Landscape Cutout Clipping & BottomSheet Navigation Bar Occlusion
- **Location:** `WindowInsetsUtil.kt:71–123`, `FallbackStyleBottomSheet.kt:42–75`, `dialog_fallback_style.xml:10`, `bottom_sheet_advanced_settings.xml:7`
- **Failure Mode:**
  1. In `WindowInsetsUtil.applySystemBarsPadding()`, `applyLeft` and `applyRight` default to `false`. When a Nothing device is held in landscape orientation, display cutouts (camera hole punch) and horizontal system bars cause top/app bar content to clip under the cutout.
  2. In `FallbackStyleBottomSheet` and Advanced Settings bottom sheets, static 22–24dp bottom padding is used without listening for `WindowInsetsCompat.Type.navigationBars()`. On Android 15 devices with transparent navigation bars or 3-button navigation (48–56dp), bottom action buttons (`btnApplyFallback`, sliders) are partially occluded by the navigation bar.
- **Remediation:**
  1. In `WindowInsetsUtil.applySystemBarsPadding()`, enable cutout left/right padding when `WindowInsetsCompat.Type.displayCutout()` insets are non-zero.
  2. In `FallbackStyleBottomSheet.kt` and `MainActivity.showAdvancedSettingsBottomSheet()`, apply navigation bar insets to the bottom of the dialog container:
     ```kotlin
     ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
         val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
         v.updatePadding(bottom = v.paddingBottom + navBars.bottom)
         insets
     }
     ```

---

### Section 3: Requirement R4 — UI/UX and Design System Consistency Review

#### 3.1 Nothing OS Aesthetic Token Alignment

##### [HIGH] Finding R4-02: Brand Design Token Violation: Off-Palette Red Hex Colors
- **Location:**
  1. `SplashActivity.kt:66`: Hardcodes `#FF0000` (generic RGB red) during glitch animation.
  2. `widget_bg_active.xml:4`: Hardcodes `<solid android:color="#FF0000" />` for active home widget state.
  3. `activity_main.xml:633–634`: Hardcodes `android:textColor="#FF4444"` and `app:strokeColor="#FF4444"` on `btnCancel`.
  4. `activity_quick_signal.xml:309`: Hardcodes `app:strokeColor="#FF6655"` on the main "SHOW SIGNAL" button.
  5. `QuickSignalActivity.kt:394`: Hardcodes `btnToggleSignal.strokeColor = ColorStateList.valueOf(Color.parseColor("#FF6655"))`.
  6. `ic_launcher_foreground.xml:45`: Hardcodes `android:fillColor="#D71921"` for the signature peak LED diode.
- **Failure Mode & Design Inconsistency:** Nothing OS design identity strictly mandates the canonical Nothing Red: `#EA3323` (RGB 234, 51, 35). The presence of generic `#FF0000`, coral `#FF4444`, salmon `#FF6655`, and muted `#D71921` breaks visual coherence and degrades the premium industrial aesthetic.
- **Remediation:** 
  1. Define `<color name="nothing_red">#EA3323</color>` in `res/values/colors.xml`.
  2. Replace all instances of `#FF0000`, `#FF4444`, `#FF6655`, and `#D71921` with `@color/nothing_red` or `#EA3323`.

---

#### 3.2 Obsidian Glass Surfaces & Hairline Border Consistency

##### [MEDIUM] Finding R4-03: Inconsistent Hairline Border Opacities Across Drawables
- **Location:**
  - `colors.xml:15` (`#33FFFFFF` 20% alpha)
  - `bg_glass_card.xml:15` (`#0DFFFFFF` 5% alpha)
  - `bg_sheet_glass.xml:12` (`#0DFFFFFF` 5% alpha)
  - `bg_preview_bezel.xml:8` (`#33FFFFFF` 20% alpha)
  - `bg_nothing_menu.xml:8` (`#24FFFFFF` 14% canonical hairline)
  - `dialog_prominent_disclosure.xml:63` (`#24FFFFFF`)
- **Failure Mode & Design Inconsistency:** Requirement R4 specifies the Nothing OS `#24FFFFFF` hairline border (14% alpha specular stroke). Across the drawables, stroke opacities fluctuate between `#0DFFFFFF` (5%), `#24FFFFFF` (14%), and `#33FFFFFF` (20%). In addition, `colorDivider` in `colors.xml` is defined as `#33FFFFFF`.
- **Remediation:**
  1. Define canonical border tokens in `res/values/colors.xml`:
     ```xml
     <color name="hairline_border">#24FFFFFF</color>
     <color name="colorDivider">#24FFFFFF</color>
     ```
  2. Update all stroke elements in `bg_glass_card.xml`, `bg_sheet_glass.xml`, and `bg_preview_bezel.xml` to reference `@color/hairline_border`.

---

#### 3.3 Typography Hierarchy & Asset Optimization

##### [LOW] Finding R4-06 / R5-05: Dead Weight Font Assets Bloating APK by 2.25 MB
- **Location:** `android/app/src/main/res/font/dotgothic16.ttf` (2,069,236 bytes ~ 2.07 MB) & `android/app/src/main/res/font/jetbrains_mono.ttf` (187,208 bytes ~ 187 KB)
- **Failure Mode:** Static analysis confirms `dotgothic16.ttf` and `jetbrains_mono.ttf` are never referenced in any layout, theme, or code file. The application uses `@font/ndot57` and `@font/font_geist`. These unreferenced fonts bloat the release APK/AAB size by > 2.25 MB.
- **Remediation:** Delete `dotgothic16.ttf` and `jetbrains_mono.ttf` from `res/font/`.

---

#### 3.4 Dark Theme Integrity, Interactive Spotlight Tour & Custom 3-Dot Overflow Menu

##### [LOW] Finding R4-05: RTL Layout Offset Flaw in Custom 3-Dot Overflow Menu
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/NothingOverflowMenu.kt:80–84`
- **Failure Mode:** `NothingOverflowMenu.kt:80` hardcodes `val xOff = (-200 * density).toInt()`, assuming a fixed LTR anchor. `AndroidManifest.xml:20` declares `android:supportsRtl="true"`. In RTL device configurations (e.g. Arabic, Hebrew), this negative offset shifts the popup window completely off-screen to the left.
- **Remediation:** Position using `Gravity.END` with `showAsDropDown(anchorView, 0, yOff, Gravity.END)` or mirror the offset based on `context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL`.

##### [LOW] Finding R4-07: Custom Splash Screen vs Android 12+ System Splash API
- **Location:** `android/app/src/main/java/com/example/odysseyglyph/SplashActivity.kt:16–85`
- **Failure Mode:** Starting in Android 12 (API 31+), Android displays a system splash screen based on `windowSplashScreenBackground` and `windowSplashScreenAnimatedIcon`. Launching a custom `SplashActivity` results in a dual-splash effect that triggers Lint warning `[CustomSplashScreen]`.
- **Remediation:** Migrate to AndroidX `androidx.core.splashscreen.SplashScreen.installSplashScreen()` in `MainActivity`, or set `android:windowIsTranslucent="true"` and keep `SplashActivity` minimal.

---

#### 3.5 Hardcoded Strings & Localization Readiness

##### [HIGH] Finding R4-01: Pervasive Hardcoded Strings & Zero Localization Readiness
- **Location:** `res/values/strings.xml`, `res/layout/*.xml` (129+ hardcoded `android:text` instances, 19+ hardcoded hints/descriptions), Kotlin UI source files
- **Failure Mode:**
  - `res/values/strings.xml` defines only 10 placeholder strings.
  - 129 layout attributes hardcode user-facing strings directly in XML.
  - Critical UI strings—including Snackbars ("Visualizer Started!", "Render cancelled."), Dialogs ("PERMISSION REQUIRED", "ACCESS DISCLOSURE"), Spotlight Tour steps ("LIVE LYRICS", "MEDIA IMPORT"), and dynamic state tags ("OFFLINE", "STREAMING", "SYNCED")—are hardcoded in English.
  - Zero alternative language configurations exist (e.g., `values-es`, `values-de`, `values-fr`).
  - Violates Android internationalization best practices and triggers numerous Android Lint `SetTextI18n` warnings.
- **Remediation:** Extract all strings to `res/values/strings.xml` with appropriate parameter placeholders (e.g. `<string name="trim_format">TRIM: %.1fs - %.1fs</string>`).

---

### Section 4: Requirement R5 — Build Configuration, Packaging, and Repository Readiness Review

#### 4.1 ProGuard / R8 Obfuscation & Missing Keep Rules

##### [HIGH] Finding R5-04: Minification/R8 Obfuscation Disabled & Missing ProGuard Rules
- **Location:** `android/app/build.gradle:31–36`, `proguard-rules.pro` (missing), `android/app/libs/glyph-matrix-sdk-2.0.aar`
- **Failure Mode & Intellectual Property Risk:**
  In `build.gradle`:
  ```groovy
  buildTypes {
      release {
          minifyEnabled false
          signingConfig signingConfigs.release
      }
  }
  ```
  Bytecode obfuscation and dead-code shrinking are completely disabled for release builds. The proprietary video processing algorithms, downsamplers (`LedTone.kt`), unsharp masking, and Nothing OS integration code are exposed in plaintext bytecode to reverse engineering via JADX.
  Furthermore, `android/app/libs/glyph-matrix-sdk-2.0.aar` contains **no `consumer-rules.pro`** or `proguard.txt`. If an engineer simply toggles `minifyEnabled true`:
  1. R8 will obfuscate/strip `com.nothing.thirdparty.IGlyphService` AIDL interfaces, causing IPC Binder transactions to Nothing OS system services to crash with `NoSuchMethodError` or `RemoteException`.
  2. R8 will strip `<init>(Context, AttributeSet)` constructors on 14 custom views instantiated via XML reflection (`CenteredImageView`, `CenteredVideoView`, `CipherTextView`, `CropOverlayView`, `GlyphDynamicPodView`, `GlyphMatrixMirrorView`, `GlyphProgressView`, `KnobView`, `LiquidGlassSegmentedBar`, `LiveAudioMeterView`, `OscilloscopeReticleView`, `SegmentedPillSlider`, `TactileWaveformScrubberView`, `TiltSpecularCardView`), causing `android.view.InflateException` crashes.
  3. Data models (`Preset`, `LrcTrack`, `SavedLyricRecord`) serialized via `org.json` will be corrupted.
- **Remediation:** Create `android/app/proguard-rules.pro` with comprehensive keep rules and update `build.gradle`:
  ```groovy
  buildTypes {
      release {
          minifyEnabled true
          shrinkResources true
          proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
          if (signingConfigs.release.storeFile?.exists()) {
              signingConfig signingConfigs.release
          }
      }
  }
  ```
  Keep rules content:
  ```proguard
  # Keep Nothing OS Glyph Matrix SDK & AIDL IPC Interfaces
  -keep class com.nothing.ketchum.** { *; }
  -keep interface com.nothing.thirdparty.IGlyphService { *; }
  -keep class com.nothing.thirdparty.IGlyphService$* { *; }
  -keep class com.nothing.thirdparty.** { *; }

  # Keep Custom UI Views referenced in XML layouts
  -keep public class com.example.odysseyglyph.*View extends android.view.View {
      public <init>(android.content.Context);
      public <init>(android.content.Context, android.util.AttributeSet);
      public <init>(android.content.Context, android.util.AttributeSet, int);
  }
  -keep public class com.example.odysseyglyph.LiquidGlassSegmentedBar { *; }
  -keep public class com.example.odysseyglyph.TactileWaveformScrubberView { *; }
  -keep public class com.example.odysseyglyph.TiltSpecularCardView { *; }
  -keep public class com.example.odysseyglyph.OscilloscopeReticleView { *; }
  -keep public class com.example.odysseyglyph.KnobView { *; }
  -keep public class com.example.odysseyglyph.LiveAudioMeterView { *; }
  -keep public class com.example.odysseyglyph.CipherTextView { *; }
  -keep public class com.example.odysseyglyph.SegmentedPillSlider { *; }

  # Keep Serialization Data Models
  -keepclassmembers class com.example.odysseyglyph.Preset { *; }
  -keepclassmembers class com.example.odysseyglyph.LrcTrack { *; }
  -keepclassmembers class com.example.odysseyglyph.SavedLyricRecord { *; }

  # Keep Third-Party & Support Libraries
  -keep class com.otaliastudios.zoomlayout.** { *; }
  -keep class androidx.dynamicanimation.** { *; }
  ```

---

#### 4.2 Release Signing Configuration & Variant Validation

##### [CRITICAL] Finding R5-01: Release Build Fails Due to Missing Keystore Validation
- **Location:** `android/app/build.gradle:22–36`
- **Failure Mode & Build Abort:**
  ```groovy
  signingConfigs {
      release {
          storeFile file(System.getenv("KEYSTORE_FILE") ?: "release.jks")
          storePassword System.getenv("KEYSTORE_PASSWORD") ?: ""
          keyAlias System.getenv("KEY_ALIAS") ?: ""
          keyPassword System.getenv("KEY_PASSWORD") ?: ""
      }
  }
  buildTypes {
      release {
          minifyEnabled false
          signingConfig signingConfigs.release
      }
  }
  ```
  Neither `release.jks` exists on disk nor are environment variables set by default. Executing `./gradlew assembleRelease` or `./gradlew bundleRelease` unconditionally fails:
  ```
  > Task :app:validateSigningRelease FAILED
  FAILURE: Build failed with an exception.
  * What went wrong:
  Execution failed for task ':app:validateSigningRelease'.
  > Keystore file 'C:\Users\manee\Desktop\glyph_odyssey\android\app\release.jks' not found for signing config 'release'.
  ```
- **Remediation:** Make release signing conditional on the presence of the keystore file:
  ```groovy
  signingConfigs {
      release {
          def ksPath = System.getenv("KEYSTORE_FILE") ?: "release.jks"
          def ksFile = file(ksPath)
          if (ksFile.exists()) {
              storeFile ksFile
              storePassword System.getenv("KEYSTORE_PASSWORD") ?: ""
              keyAlias System.getenv("KEY_ALIAS") ?: ""
              keyPassword System.getenv("KEY_PASSWORD") ?: ""
              v1SigningEnabled true
              v2SigningEnabled true
          }
      }
  }
  buildTypes {
      release {
          if (signingConfigs.release.storeFile?.exists()) {
              signingConfig signingConfigs.release
          }
      }
  }
  ```

---

#### 4.3 Asset Optimization & Unused Resource Purge

##### [MEDIUM] Finding R5-09: Multi-Density Launcher Icon Duplication and Abuse
- **Location:** `res/mipmap-*/ic_launcher.png`, `res/drawable/img_toy_preview.png`
- **Failure Mode:** An identical 5,727-byte 512×512 PNG file was placed across `mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`, and `drawable/img_toy_preview.png`.
  Lint reports `[IconDipSize]`: `ic_launcher.png varies significantly in its density-independent size across densities: mdpi: 512x512 dp vs xxxhdpi: 128x128 dp`, alongside `[IconLocation]` and `[IconDuplicates]`. On lower-density displays or launchers, a 512×512 px bitmap in `mipmap-mdpi` will be scaled to 512 dp, causing visual distortion and excessive memory allocation during icon rendering.
- **Remediation:** Scale launcher icons properly for standard mipmap buckets: mdpi (48×48), hdpi (72×72), xhdpi (96×96), xxhdpi (144×144), xxxhdpi (192×192), and move `img_toy_preview.png` to `drawable-nodpi/`.

##### [LOW] Finding R5-11: Unused Resources in Release Artifacts (17 items)
- **Location:** `res/drawable/bg_pixel_card.xml`, `res/drawable/bg_preview_bezel.xml`, `colors.xml`, `dimens.xml`, etc.
- **Failure Mode:** 17 dead resource entries are bundled into release artifacts.
- **Remediation:** Remove obsolete XML drawables and enable `shrinkResources true`.

---

#### 4.4 Repository Readiness, Secrets Leak & Hygiene

##### [CRITICAL] Finding R5-03: Critical Git Leak: local.properties Tracked in Git with Absolute Paths
- **Location:** `android/local.properties`, `android/app/local.properties`, `.gitignore`
- **Failure Mode & Security Leak:** `git ls-files` reports both `android/local.properties` and `android/app/local.properties` are actively committed and tracked in Git. Line 8 of `android/local.properties`:
  ```properties
  sdk.dir=C\:\\Users\\manee\\AppData\\Local\\Android\\Sdk
  ```
  This violates repository hygiene, leaks the developer's username and local workstation directory structure, breaks CI runners on Linux/macOS by hardcoding a Windows path, and is omitted from `.gitignore`.
- **Remediation:**
  1. Add `**/local.properties` to `.gitignore`.
  2. Run `git rm --cached android/local.properties android/app/local.properties`.

##### [LOW] Finding R5-12: Loose Development and Scratch Files in Root
- **Location:** Project root (`classes.jar`, `classes_search.jar`, `HEAD_MainActivity.kt`, `HEAD_MINUS_1_MainActivity.kt`, `modify_gallery.py`, `modify_toyservice.py`, `runs.json`, `New folder/`)
- **Failure Mode:** Loose compilation outputs, scripts, and temporary backups pollute the Git tree.
- **Remediation:** Clean and untrack stray development artifacts from repository root.

---

#### 4.5 Packaging Format & CI/CD Pipeline Deficits

##### [HIGH] Finding R5-06: CI Workflow Targets APK Instead of Play Store AAB
- **Location:** `.github/workflows/ci.yml:45`
- **Failure Mode:** Google Play Store mandates the **Android App Bundle (.aab)** format for all new app submissions. `.github/workflows/ci.yml` executes `./gradlew assembleRelease` (generating `.apk`), omitting `.aab` generation entirely. Furthermore, CI never executes `./gradlew test` or `./gradlew lint`, allowing broken code to produce release artifacts.
- **Remediation:** In `.github/workflows/ci.yml`, replace `./gradlew assembleRelease` with `./gradlew bundleRelease`, upload `app-release.aab`, and add gate steps for `./gradlew test` and `./gradlew lint`.

##### [MEDIUM] Finding R5-08: Application Package Namespace Uses Reserved com.example.*
- **Location:** `android/app/build.gradle:7`
- **Failure Mode:** `build.gradle` declares `namespace 'com.example.odysseyglyph'`. The `com.example.*` domain is reserved for sample/template code and strongly discouraged for production apps by Google Play. While `applicationId` is overridden to `"com.rickastley.glyphodyssey"`, generated `BuildConfig` and `R` classes remain in `com.example.odysseyglyph`.
- **Remediation:** Refactor namespace to `com.rickastley.glyphodyssey` to align package namespace with application identity.

##### [MEDIUM] Finding R5-10: Unnecessary Jetifier Flag Enabled in Modern AndroidX Project
- **Location:** `android/gradle.properties:2`
- **Failure Mode:** `android.enableJetifier=true` is set. All dependencies in `build.gradle` are modern AndroidX or Material libraries. Jetifier is unnecessary and increases build and sync latency.
- **Remediation:** Set `android.enableJetifier=false`.

---

#### 4.6 Licensing Compliance

##### [HIGH] Finding R5-07: Licensing Conflict: GPLv3 Codebase Statically Bundling Proprietary Nothing SDK
- **Location:** `LICENSE` & `android/app/libs/glyph-matrix-sdk-2.0.aar`
- **Failure Mode & Legal Tension:** The project is licensed under **GNU General Public License v3.0** (`LICENSE`). It statically links and distributes `glyph-matrix-sdk-2.0.aar`, which is proprietary intellectual property of Nothing Technology Limited. GPLv3 Section 5 and 6 require that the entire program, including linked libraries, be licensed under GPLv3. Linking against proprietary vendor SDKs without a specific "Classpath Exception" or dual-licensing structure violates GPLv3 terms.
- **Remediation:** Add a standard linking exception clause to the `LICENSE` file for the Nothing Glyph SDK (similar to the OpenSSL or Android SDK linking exceptions).

##### [LOW] Finding R5-13: Outdated and Misleading Documentation in README.md
- **Location:** `README.md:23–26`
- **Failure Mode:** The README states: *"If you're on a Nothing Phone 1, 2, 2a, or (4a) Pro — close this tab... This app does nothing useful on other models."* This directly contradicts the recently introduced **Virtual Glyph Matrix Mode**, which supports rendering and simulation on non-Nothing devices.
- **Remediation:** Update README to highlight Virtual Glyph Matrix support and replace sideload/Play Protect bypass guides with standard Play Store installation instructions.

---

### Section 5: Gradle Verification Execution Summary

#### 5.1 `./gradlew test` Execution Outcome
- **Command:** `cd c:\Users\manee\Desktop\glyph_odyssey\android; .\gradlew.bat test --info`
- **Duration:** 1 minute 27 seconds
- **Outcome:** **SUCCESS (Exit Code 0)** — `BUILD SUCCESSFUL in 1m 27s`
- **Suite-by-Suite Test Execution Breakdown:**

```
+--------------------------------------+-------+---------+--------+---------+
| Test Suite Class                     | Tests | Passed  | Failed | Skipped |
+--------------------------------------+-------+---------+--------+---------+
| AdversarialChallengeTest             |    12 |      12 |      0 |       0 |
| Tier1BentoConsoleCoverageTest        |   165 |     165 |      0 |       0 |
| Tier1DynamicPodCoverageTest          |    60 |      60 |      0 |       0 |
| Tier1FoundationCoverageTest          |   195 |     195 |      0 |       0 |
| Tier1PlayReadinessCoverageTest       |    45 |      45 |      0 |       0 |
| Tier1TacticalCropperCoverageTest     |    90 |      90 |      0 |       0 |
| Tier2BentoConsoleBoundaryTest        |   165 |     165 |      0 |       0 |
| Tier2DynamicPodBoundaryTest          |    60 |      60 |      0 |       0 |
| Tier2FoundationBoundaryTest          |   195 |     195 |      0 |       0 |
| Tier2PlayReadinessBoundaryTest       |    45 |      45 |      0 |       0 |
| Tier2TacticalCropperBoundaryTest     |    90 |      90 |      0 |       0 |
| Tier3CrossFeatureCombinationsTest    |    60 |      60 |      0 |       0 |
| Tier4RealWorldScenariosTest          |    18 |      18 |      0 |       0 |
| FoundationUnitTest                   |    29 |      29 |      0 |       0 |
| LedToneTest                          |     7 |       7 |      0 |       0 |
| Milestone1AdversarialTest            |    13 |      13 |      0 |       0 |
+--------------------------------------+-------+---------+--------+---------+
| TOTALS                               | 1,249 |   1,249 |      0 |       0 |
+--------------------------------------+-------+---------+--------+---------+
```

---

#### 5.2 `./gradlew lint` Execution Outcome
- **Command:** `cd c:\Users\manee\Desktop\glyph_odyssey\android; .\gradlew.bat lint`
- **Duration:** 2 minutes 07 seconds
- **Outcome:** **FAILED (Exit Code 1)** — `Lint found 6 errors, 393 warnings. FAILURE: Build failed with an exception.`
- **Detailed Fatal Errors (Breaking Build Gate):**
  1. `SnackbarUtils.kt:9`: `Error: Snackbar.SnackbarLayout can only be accessed from within the same library group (referenced groupId=com.google.android.material from groupId=OdysseyGlyph) [RestrictedApi]`
  2. `SnackbarUtils.kt:9`: `Error: SnackbarLayout can only be accessed from within the same library group [RestrictedApi]`
  3. `SnackbarUtils.kt:24`: `Error: SnackbarBaseLayout.setLayoutParams can only be called from within the same library group [RestrictedApi]`
  4. `SnackbarUtils.kt:24`: `Error: SnackbarBaseLayout.setLayoutParams can only be called from within the same library group [RestrictedApi]`
  5. `odyssey_widget.xml:16`: `Error: Must use app:tint instead of android:tint [UseAppTint from androidx.appcompat]`
  6. `MainActivity.kt:1008`: `Error: The intent action com.example.odysseyglyph.RELOAD_FRAMES (used to send a broadcast) matches the intent filter of a non-exported receiver... use Intent.setPackage(<APPLICATION_ID>) [UnsafeImplicitIntentLaunch]`

---

#### 5.3 `./gradlew assembleRelease` & `./gradlew bundleRelease` Failure Analysis
- **Command:** `cd c:\Users\manee\Desktop\glyph_odyssey\android; .\gradlew.bat assembleRelease` & `bundleRelease`
- **Duration:** 1m 42s (`assembleRelease`) / 26s (`bundleRelease`)
- **Outcome:** **FAILED (Exit Code 1)** — Both tasks fail at `:app:validateSigningRelease`
- **Verbatim Error Log:**
  ```
  > Task :app:validateSigningRelease FAILED

  FAILURE: Build failed with an exception.

  * What went wrong:
  Execution failed for task ':app:validateSigningRelease'.
  > Keystore file 'C:\Users\manee\Desktop\glyph_odyssey\android\app\release.jks' not found for signing config 'release'.

  * Try:
  > Run with --stacktrace option to get the stack trace.
  > Run with --info or --debug option to get more log output.
  > Run with --scan to get full insights.
  > Get more help at https://help.gradle.org.

  BUILD FAILED in 1m 42s
  ```
- **Root Cause:** In `android/app/build.gradle:22-36`, `buildTypes.release` assigns `signingConfig signingConfigs.release` unconditionally. Because `release.jks` does not exist on disk, Gradle's task validation immediately halts compilation.

---

## 4. Consolidated Master Finding Matrix

The following master table indexes all 49 audited findings across Requirements R2 through R5, organized by Severity and Finding ID:

| Finding ID | Scope | Severity | Issue Title | Affected File & Lines | Risk / Policy Violation | Actionable Remediation |
|---|---|---|---|---|---|---|
| **R3-01** | R3 | **CRITICAL** | Missing in-app Privacy Policy link | `layout_nothing_overflow_menu.xml`, `strings.xml` | Immediate Play Store rejection for sensitive permissions (`RECORD_AUDIO`, notification access). | Add Privacy Policy item to overflow menu; launch via CustomTabsIntent. |
| **R3-02** | R3/R2 | **CRITICAL** | FGS type mismatch & background mic crash | `AndroidManifest.xml:133-141`, `LiveLyricsService.kt:266`, `VisualizerService.kt:134` | Android 14+ crashes with `ForegroundServiceStartNotAllowedException`; rejects `mediaPlayback` without playback. | Align FGS type with `specialUse`; dynamically request mic FGS type only when needed; catch background exceptions. |
| **R2-01** | R2 | **CRITICAL** | Missing dynamic promotion to microphone FGS | `VisualizerService.kt:134-153, 173-184` | Switching to mic styles without re-calling `startForeground()` violates Android 14 AppOps, crashing or muting mic. | Re-invoke `startForeground()` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` inside `onSharedPreferenceChanged`. |
| **R2-13** | R2 | **CRITICAL** | Inverted cancellation logic in video rendering | `MainActivity.kt:75, 163, 915, 930, 995` | `isRendering.get()` evaluates to `false` during active rendering; back-press fails to cancel heavy worker thread. | Decouple UI rendering state from `isRenderCancelled = AtomicBoolean(false)` token. |
| **R2-17** | R2 | **CRITICAL** | Native MediaPlayer handle leak in video preview | `CenteredVideoView.kt:14, 104`, `MainActivity.kt:315-319` | `videoView.release()` never called in `onDestroy()`. Mediaserver client exhaustion causes error `(1, -19)`. | Invoke `binding.videoPreview.release()` in `MainActivity.onDestroy()`. |
| **R2-18** | R2 | **CRITICAL** | Preset dialog bitmap leak and UI freeze | `GalleryActivity.kt:128-174` | Synchronous parsing of `.bin` file on UI thread creates 1,000s of Bitmaps without recycling, causing ANR and OOM. | Decode off main thread; draw frames onto single reusable 25×25 mutable Bitmap; clear callbacks on dismiss. |
| **R5-01** | R5 | **CRITICAL** | Release build failure on missing keystore | `android/app/build.gradle:22-36` | `validateSigningRelease` fails `./gradlew assembleRelease` and `bundleRelease` due to non-existent `release.jks`. | Guard release signing config with `if (signingConfigs.release.storeFile?.exists())`. |
| **R5-02** | R5/R3 | **CRITICAL** | Fatal Gradle Lint errors blocking build | `SnackbarUtils.kt:9,24`, `odyssey_widget.xml:16`, `MainActivity.kt:1008` | `./gradlew lint` exits with code 1 due to 6 fatal errors, preventing automated CI validation. | Cast to `ViewGroup`; suppress `UseAppTint` on RemoteViews; add package to implicit broadcast. |
| **R5-03** | R5 | **CRITICAL** | `local.properties` tracked in Git with absolute paths | `android/local.properties`, `android/app/local.properties`, `.gitignore` | Leaks developer workstation paths; breaks Linux/macOS CI builds; omitted from `.gitignore`. | Untrack via `git rm --cached` and add `**/local.properties` to `.gitignore`. |
| **R3-03** | R3 | **HIGH** | 5 internal activities exported without filters | `AndroidManifest.xml:38, 80, 86, 90, 95` | Third-party apps can launch internal activities to flash LEDs or tamper with offline lyrics database. | Set `android:exported="false"` on `MainActivity`, `LiveLyricsActivity`, `QuickSignalActivity`, etc. |
| **R3-04** | R3 | **HIGH** | Unused sensitive permission `READ_MEDIA_AUDIO` | `AndroidManifest.xml:5` | Google Play policy violation for declaring sensitive storage/media permissions not actively utilized. | Remove `READ_MEDIA_AUDIO` uses-permission from `AndroidManifest.xml`. |
| **R3-05** | R3 | **HIGH** | Inconsistent NotificationListener disclosures | `MainActivity.kt:500-517`, `LiveLyricsActivity.kt:345` | Bypassing prominent disclosures violates Play User Data policy, risking automated suspension. | Standardize all flows to invoke `ProminentDisclosureDialog.showIfRequired()`. |
| **R3-06** | R3 | **HIGH** | Missing rationale and disclosure for `RECORD_AUDIO` | `MainActivity.kt:529-543`, `LiveLyricsActivity.kt:321` | Abrupt permission prompt without pre-disclosure violates Play Store dangerous permission policies. | Display pre-permission modal explaining volatile memory FFT audio visualization. |
| **R2-02** | R2 | **HIGH** | Hardware microphone churn and AudioRecord thrashing | `LiveLyricsService.kt:122`, `AudioVisualizerHelper.kt:31` | Spinning up and releasing `AudioRecord` at 60Hz during lyric transitions causes audio HAL churn and native crashes. | Add 1500ms debounce before visualizer fallback; synchronize stop/release with worker thread. |
| **R2-03** | R2 | **HIGH** | MediaSessionManager listener leak in service | `MediaSessionListenerService.kt:132-145` | Anonymous listener never unregistered; leaks listener instances in OS system server across reconnections. | Store listener as field and unregister in `onListenerDisconnected()` and `onDestroy()`. |
| **R2-04** | R2 | **HIGH** | Toy Service loop and handler leak on termination | `OdysseyToyService.kt:114-122, 149-152` | Teardown strictly in `onUnbind()` leaves 50Hz playback runnable alive if service is stopped without unbinding. | Move cleanup logic into `onDestroy()`. |
| **R2-05** | R2 | **HIGH** | Synchronous `MediaPlayer.prepare()` on UI thread | `OdysseyToyService.kt:176-191` | Synchronous audio header decoding on Main Looper risks UI hitches and ANR. | Use `mediaPlayer.prepareAsync()` and listen via `setOnPreparedListener`. |
| **R2-07** | R2 | **HIGH** | Socket and stream leaks in LRCLibClient | `LRCLibClient.kt:82-93, 108-122, 132-147` | Unclosed `HttpURLConnection` and error streams on non-200 responses lead to socket exhaustion. | Wrap in `try-finally`, use `.use { }` for streams, and explicitly call `disconnect()`. |
| **R2-08** | R2 | **HIGH** | Premature lyric blanking on gaps >10s | `LrcUtils.kt:72` | `lineDuration > 10000f` blanks the entire preceding lyric line from start to finish. | Evaluate `timeSinceStart` so lyrics display normally and only blank during trailing pause. |
| **R2-09** | R2 | **HIGH** | Synchronous SQLite database queries on UI thread | `SavedLyricsRepository.kt:35-52`, `SavedLyricsActivity.kt:47` | Querying SQLite on Main UI thread violates `StrictMode` and drops frames on track changes. | Offload repository lookups to background dispatchers (`Dispatchers.IO`). |
| **R4-01** | R4 | **HIGH** | 129+ hardcoded layout strings & no localization | `res/layout/*.xml`, `strings.xml`, Kotlin UI | Zero multi-language readiness; degrades international user experience; triggers Lint warnings. | Extract all layout and code strings into `res/values/strings.xml`. |
| **R4-02** | R4 | **HIGH** | Brand design token violation: rogue red hexes | `SplashActivity.kt:66`, `widget_bg_active.xml:4`, `activity_main.xml:633` | Rogue reds (`#FF0000`, `#FF4444`, `#FF6655`, `#D71921`) violate canonical Nothing Red (`#EA3323`). | Define `<color name="nothing_red">#EA3323</color>` and unify across all layouts and code. |
| **R5-04** | R5 | **HIGH** | Minification disabled & missing ProGuard rules | `android/app/build.gradle:33-36`, `proguard-rules.pro` | Plaintext bytecode exposure; enabling R8 without rules breaks AIDL interfaces and XML custom views. | Create `proguard-rules.pro` keeping `IGlyphService` and 14 custom views; enable `minifyEnabled true`. |
| **R5-05** | R5/R4 | **HIGH** | 2.25+ MB unreferenced dead font assets | `res/font/dotgothic16.ttf`, `res/font/jetbrains_mono.ttf` | Bloats APK/AAB download size by >2.25 MB without being referenced anywhere. | Delete `dotgothic16.ttf` and `jetbrains_mono.ttf` from `res/font/`. |
| **R5-06** | R5 | **HIGH** | CI/CD workflow targets APK instead of Play Store AAB | `.github/workflows/ci.yml:45` | Fails to build required App Bundle (`.aab`); lacks `./gradlew test` and `lint` gates. | Update CI to run `./gradlew bundleRelease` and upload `.aab`; add test and lint steps. |
| **R5-07** | R5 | **HIGH** | GPLv3 licensing conflict with proprietary Nothing AAR | `LICENSE`, `glyph-matrix-sdk-2.0.aar` | Distributing proprietary vendor AAR under pure GPLv3 creates legal license incompatibility. | Add a Nothing SDK linking exception grant to `LICENSE`. |
| **R3-07** | R3 | **MEDIUM** | Asynchronous permission request without callbacks | `LiveLyricsActivity.kt:159-174`, `MainActivity.kt:537` | Immediate FGS launch after calling permission request can lead to dropped notifications. | Migrate to `registerForActivityResult(RequestPermission())`. |
| **R3-08** | R3 | **MEDIUM** | Unsafe implicit broadcast for internal receiver | `MainActivity.kt:1008` | Implicit internal broadcast triggers Lint failure and runtime error on modern Android. | Add `.setPackage(packageName)` to intent. |
| **R3-09** | R3 | **MEDIUM** | Restricted Material API access in SnackbarUtils | `SnackbarUtils.kt:9, 24` | Invoking internal `LIBRARY_GROUP` APIs breaks Gradle Lint and risks NoSuchMethodError. | Cast `this.view` to `ViewGroup` instead of `SnackbarLayout`. |
| **R4-03** | R4 | **MEDIUM** | Inconsistent hairline border opacities (5%, 14%, 20%) | `colors.xml:15`, `bg_glass_card.xml:15`, `bg_sheet_glass.xml:12` | Visual inconsistency across cards and bottom sheets. | Define canonical `#24FFFFFF` token and apply consistently. |
| **R4-04** | R4 | **MEDIUM** | Landscape cutout clipping & BottomSheet nav occlusion | `WindowInsetsUtil.kt:71`, `FallbackStyleBottomSheet.kt:42` | Bottom action buttons occluded by 3-button nav bar; landscape cutout clips content. | Apply navigation bar bottom insets and display cutout horizontal padding. |
| **R2-06** | R2 | **MEDIUM** | Delayed stopSelf() with stripped notification | `LiveLyricsService.kt:323-329` | Service runs as bare background service for 600ms, risking termination on Android 14. | Execute `stopSelf()` immediately without artificial delay. |
| **R2-10** | R2 | **MEDIUM** | Destructive database upgrade in SavedLyricsDbHelper | `SavedLyricsDbHelper.kt:32-35` | Schema bump drops `saved_lyrics` table, wiping all verified lyrics matches and custom imports. | Replace table drop with incremental `ALTER TABLE` migrations. |
| **R2-11** | R2 | **MEDIUM** | Unbounded SQLite database growth | `SavedLyricsRepository.kt:91-116` | Auto-verified matches accumulate indefinitely with no eviction cap. | Implement LRU eviction query capping auto-verified records at 500 tracks. |
| **R2-14** | R2 | **MEDIUM** | Worker pool priority inversion (`THREAD_PRIORITY_DISPLAY`) | `HardwareVideoDecoder.kt:58-65` | Assigning UI display priority to 8 CPU decoder threads starves Main Looper and RenderThread. | Set decoder thread priority to `THREAD_PRIORITY_BACKGROUND` or `DEFAULT`. |
| **R2-15** | R2 | **MEDIUM** | Unclosed Image on downsampling exception | `HardwareVideoDecoder.kt:173-183` | Exception in `extractCropPixels` skips `image.close()`, stalling MediaCodec output buffers. | Wrap image processing in `try-finally` or `image.use { }`. |
| **R2-16** | R2 | **MEDIUM** | Binary header inconsistency (12-byte vs 16-byte) | `VideoProcessor.kt:514, 716`, `OdysseyToyService.kt:240` | VideoProcessor writes 12-byte header, losing audio synchronization offset. | Unify binary header format to 16-byte V3 with `audioOffsetMs`. |
| **R2-19** | R2 | **MEDIUM** | Synchronous file I/O in RecyclerView scrolling | `GalleryActivity.kt:310-334` | Reading `.bin` and allocating Bitmaps in `onBindViewHolder` causes scrolling stutter. | Cache thumbnails in `LruCache<String, Bitmap>` and load asynchronously. |
| **R2-20** | R2 | **MEDIUM** | Persistent 60Hz frame bus wakeups in background | `LiveLyricsActivity.kt:103`, `QuickSignalActivity.kt:76` | Stopped activities continue receiving 60Hz UI dispatches, draining battery. | Subscribe in `onStart()`/`onResume()` and unsubscribe in `onStop()`/`onPause()`. |
| **R5-08** | R5 | **MEDIUM** | Application namespace uses `com.example.*` | `android/app/build.gradle:7` | Reserved template domain discouraged for production Play Store apps. | Refactor namespace to `com.rickastley.glyphodyssey`. |
| **R5-09** | R5 | **MEDIUM** | Multi-density launcher icon duplication | `res/mipmap-*/ic_launcher.png`, `res/drawable/img_toy_preview.png` | 512×512 PNG duplicated across all densities; mdpi scales to huge memory footprint. | Provide correctly sized icons (48, 72, 96, 144, 192 px) per dpi bucket. |
| **R5-10** | R5 | **MEDIUM** | Jetifier enabled unnecessarily | `android/gradle.properties:2` | Slows build and sync times in fully-migrated AndroidX project. | Set `android.enableJetifier=false`. |
| **R3-10** | R3 | **LOW** | Backup enabled (`allowBackup="true"`) | `AndroidManifest.xml:16` | SharedPreferences and SQLite database exposed to `adb backup`. | Set `allowBackup="false"` or specify `dataExtractionRules`. |
| **R3-11** | R3 | **LOW** | Hardcoded test NothingKey in manifest | `AndroidManifest.xml:23-25` | Commercial production release may require production key from Nothing Developer Programme. | Inject release NothingKey via manifest placeholders. |
| **R4-05** | R4 | **LOW** | RTL layout offset flaw in 3-dot overflow menu | `NothingOverflowMenu.kt:80-84` | Hardcoded negative X offset throws menu off-screen on RTL devices. | Use `Gravity.END` or invert offset when RTL layout direction detected. |
| **R4-07** | R4 | **LOW** | Custom splash activity triggers dual splash warning | `SplashActivity.kt:16-85` | Dual splash screen effect on Android 12+ devices; triggers Lint warning. | Adopt AndroidX `SplashScreen` API in `MainActivity`. |
| **R2-12** | R2 | **LOW** | Placeholder User-Agent in LRCLibClient | `LRCLibClient.kt:24` | Dummy URL risks API rate limiting or blocking. | Set User-Agent to official repository URL and version string. |
| **R2-21** | R2 | **LOW** | Unsynchronized listener registration in PlaybackState | `MusicPlaybackState.kt:7-23, 44-53` | Potential `ConcurrentModificationException` and memory barrier visibility lag. | Use `CopyOnWriteArrayList` and mark shared mutable primitives `@Volatile`. |
| **R5-11** | R5 | **LOW** | 17 unused resources in binary | `res/drawable/bg_pixel_card.xml`, `colors.xml`, etc. | Minor binary bloat. | Delete unused resources and enable `shrinkResources true`. |
| **R5-12** | R5 | **LOW** | Loose development and scratch files in root | `classes.jar`, `HEAD_*.kt`, `modify_*.py`, etc. | Pollutes repository cleanliness. | Remove loose scratch files from repository root. |
| **R5-13** | R5 | **LOW** | Outdated README documentation | `README.md:23-26` | Claims app does nothing on non-Nothing devices, ignoring Virtual Matrix mode. | Update documentation to reflect Virtual Matrix capabilities. |

---

## 5. Prioritized Action Plan / Remediation Roadmap

To ensure a structured, risk-mitigated progression toward Google Play Store release readiness, remediations must be executed in four disciplined phases:

```
+---------------------------------------------------------------------------------------+
| PHASE 1: Critical Play Store Submission Blockers & Build Enablers                     |
| Target: Resolve all immediate rejection reasons and fix release build/lint failures   |
+---------------------------------------------------------------------------------------+
|  1. Add in-app Privacy Policy link to NothingOverflowMenu & strings.xml (R3-01).     |
|  2. Fix Gradle release signing by guarding with `storeFile?.exists()` (R5-01).        |
|  3. Resolve 6 fatal Gradle Lint errors in SnackbarUtils, widget, & MainActivity (R5-02)|
|  4. Remove unused sensitive permission `READ_MEDIA_AUDIO` from manifest (R3-04).     |
|  5. Mark 5 internal activities `android:exported="false"` in manifest (R3-03).        |
|  6. Align FGS types, add dynamic mic promotion, and catch background FGS starts (R3-02)|
|  7. Untrack `local.properties` via `git rm --cached` and add to `.gitignore` (R5-03).  |
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 2: High Severity Stability, Architecture & Security Fixes                       |
| Target: Eliminate runtime crashes, hardware churn, memory leaks, and licensing risks  |
+---------------------------------------------------------------------------------------+
|  1. Fix inverted cancellation logic in MainActivity video processing (R2-13).         |
|  2. Add `videoView.release()` to `MainActivity.onDestroy()` (R2-17).                  |
|  3. Refactor GalleryActivity preset dialog to use single reusable Bitmap (R2-18).     |
|  4. Author `proguard-rules.pro` keeping `IGlyphService` & custom views; enable R8 (R5-04)|
|  5. Wrap LRCLibClient connections in `try-finally` with explicit `disconnect()` (R2-07)|
|  6. Debounce AudioRecord startup in LiveLyricsService and synchronize release (R2-02).|
|  7. Unregister MediaSessionManager listener in MediaSessionListenerService (R2-03).    |
|  8. Fix premature lyric blanking bug in LrcUtils for lines preceding >10s gaps (R2-08)|
|  9. Offload SavedLyricsRepository SQLite queries to background dispatchers (R2-09).   |
| 10. Add Nothing Glyph SDK linking exception clause to LICENSE (R5-07).                |
| 11. Overhaul CI/CD workflow to generate Play Store `.aab` bundles and run tests (R5-06)|
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 3: Design Harmonization & Localization Polish                                   |
| Target: Ensure Nothing OS visual token compliance and internationalization readiness  |
+---------------------------------------------------------------------------------------+
|  1. Replace rogue red hexes (`#FF0000`, `#FF4444`, etc.) with `#EA3323` (R4-02).     |
|  2. Standardize hairline border strokes to `#24FFFFFF` across all drawables (R4-03).   |
|  3. Extract all 129+ hardcoded layout strings and UI messages to `strings.xml` (R4-01).|
|  4. Apply WindowInsets navigation bar padding to BottomSheets (R4-04).                |
|  5. Fix RTL layout negative X offset bug in NothingOverflowMenu (R4-05).              |
|  6. Standardize prominent disclosures for NotificationListener & mic access (R3-05/06)|
+---------------------------------------------------------------------------------------+
                                           |
                                           v
+---------------------------------------------------------------------------------------+
| PHASE 4: Build, Packaging & Repository Optimization                                   |
| Target: Reduce binary footprint, optimize build latency, and finalize documentation   |
+---------------------------------------------------------------------------------------+
|  1. Delete unreferenced 2.25+ MB fonts `dotgothic16.ttf` & `jetbrains_mono.ttf` (R4-06)|
|  2. Scale launcher icons properly across mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi (R5-09).  |
|  3. Refactor application namespace from `com.example.*` to `com.rickastley.*` (R5-08).|
|  4. Disable Jetifier in `gradle.properties` (R5-10).                                  |
|  5. Clean loose development artifacts and temporary scripts from repository root (R5-12)|
|  6. Update README documentation to showcase Virtual Glyph Matrix mode (R5-13).        |
+---------------------------------------------------------------------------------------+
```

---

## 6. Verification and Validation Procedures

To verify remediation and confirm Play Store release readiness, execute the following commands in PowerShell from `c:\Users\manee\Desktop\glyph_odyssey\android`:

### 6.1 Unit Test Suite Verification
```powershell
.\gradlew.bat test
```
- **Validation Criteria:** Must exit with code 0 (`BUILD SUCCESSFUL`). All 1,249 existing unit tests must pass without regressions.

### 6.2 Lint Gate Verification
```powershell
.\gradlew.bat lint
```
- **Validation Criteria:** Must exit with code 0 (`BUILD SUCCESSFUL`). Fatal error count must drop from 6 to 0.

### 6.3 Release App Bundle Generation Verification
```powershell
.\gradlew.bat bundleRelease
```
- **Validation Criteria:** Must exit with code 0 (`BUILD SUCCESSFUL`). Successfully output `app-release.aab` under `android/app/build/outputs/bundle/release/` without throwing keystore validation errors.

### 6.4 Binary Obfuscation Verification
```powershell
jar tf app/build/outputs/bundle/release/app-release.aab | findstr "classes.dex"
```
- **Validation Criteria:** Decompiling the output DEX with JADX must verify that internal class and method names are obfuscated, while `com.nothing.thirdparty.IGlyphService` and custom View constructors remain intact.

### 6.5 Secret & Hygiene Verification
```powershell
git status --porcelain
git ls-files "*/local.properties"
```
- **Validation Criteria:** Output must return 0 results, confirming `local.properties` is completely untracked and excluded from version control.

---
*Report Authoring Completed: November 20, 2026*  
*Sign-off: Mobile Pre-Release Audit Subagent (Worker 1)*
