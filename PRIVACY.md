# Privacy Policy for Glyph Odyssey

**Last Updated:** November 20, 2026

**Glyph Odyssey** is an open-source companion application designed for the Nothing Phone (3) to project synced lyrics, audio visualizers, and custom animations onto the rear Glyph LED matrix.

We value your privacy. This policy outlines how permissions and data are handled by the application.

---

## 1. Summary of Data Collection
- **Personal Data Collected:** None.
- **Data Sold or Shared:** None.
- **Third-Party Trackers or Analytics:** None.
- **Cloud Accounts or Logins:** None.

All media conversions, audio analysis, and LED rendering occur **100% locally on your device**.

---

## 2. Permissions & Usage

### A. Microphone Access (`android.permission.RECORD_AUDIO`)
- **Purpose:** Used solely for real-time acoustic frequency analysis in reactive visualizer modes (Mic Wave, Pulse, Reticle).
- **Processing:** Raw PCM audio samples are processed locally using Fast Fourier Transform (FFT) to calculate instantaneous amplitude levels.
- **Zero Storage / Zero Transmission:** Audio samples are discarded immediately after computing frame levels (every ~16ms). No audio is ever recorded to disk, retained in memory, or transmitted across any network.

### B. Notification Access (`NotificationListenerService`)
- **Purpose:** Detects active media playback sessions (track title, artist, playback state) to fetch corresponding synced lyrics and display them on the rear LED matrix.
- **Scope:** The service only reads `MediaController` metadata and playback status from active media applications (such as Spotify, YouTube Music, Apple Music).
- **Zero Personal Data:** Non-media notifications (messages, chats, emails, banking alerts) are strictly ignored. No notification content is ever saved, logged, or shared.

### C. Foreground Services & Notifications (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`)
- **Purpose:** Allows Live Lyrics and Music Visualizers to project smoothly to the rear matrix while your phone screen is off or when switching apps.
- A persistent notification is shown in your status drawer so you can stop the service at any time.

---

## 3. Network Services

### LRCLib Synced Lyrics
When Live Lyrics is active, the app queries the public LRCLib database (`https://lrclib.net`) using only the current **song title**, **artist name**, and **album name** to fetch synced lyrics.
- No user identifiers, device IDs, advertising tokens, or personal information are attached to requests.
- Fetched lyrics are cached locally on your device in an offline SQLite database so identical songs do not require repeat network requests.

---

## 4. Local Storage
All user animations, custom presets, and cached lyrics are stored in your device's private app directory:
- Saved presets (`/data/data/com.rickastley.glyphodyssey/files/presets`)
- Offline lyrics database (`/data/data/com.rickastley.glyphodyssey/databases/odyssey_lyrics.db`)

Uninstalling the app removes all associated local data.

---

## 5. Security & Open Source
Glyph Odyssey is completely open-source. You can review the full source code and verify these privacy guarantees directly on GitHub:
- **Repository:** [https://github.com/tezz-e/OdysseyGlyph](https://github.com/tezz-e/OdysseyGlyph)
- **License:** Open Source

---

## 6. Contact
If you have any questions or concerns regarding this policy, please open an issue on the GitHub repository:
[https://github.com/tezz-e/OdysseyGlyph/issues](https://github.com/tezz-e/OdysseyGlyph/issues)
