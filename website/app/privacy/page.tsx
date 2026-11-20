import styles from "./privacy.module.css";
import Link from "next/link";

export const metadata = {
  title: "Privacy Policy | Glyph Odyssey",
  description: "Privacy policy for Glyph Odyssey - 100% on-device, zero tracking.",
};

export default function PrivacyPage() {
  return (
    <main className={styles.main}>
      <nav className={styles.nav}>
        <Link href="/" className={styles.navLogo}>
          GLYPH ODYSSEY
        </Link>
        <Link href="/" className={styles.navLink}>
          Back to Home
        </Link>
      </nav>

      <div className={styles.container}>
        <div className={styles.header}>
          <span className={styles.badge}>DOCUMENT // PRIVACY</span>
          <h1 className={styles.title}>Privacy Policy</h1>
          <p className={styles.subtitle}>Last updated: November 20, 2026</p>
        </div>

        <section className={styles.section}>
          <p className={styles.lead}>
            <strong>Glyph Odyssey</strong> is an open-source companion application designed for the Nothing Phone (2a) to project synced lyrics, audio visualizers, and custom animations onto the rear Glyph LED matrix.
          </p>
          <p>
            We believe in total user privacy. All media processing, conversion, and matrix rendering occur <strong>100% locally on your device</strong>.
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>1. Summary of Data Collection</h2>
          <ul className={styles.list}>
            <li><strong>Personal Data Collected:</strong> None.</li>
            <li><strong>Data Sold or Shared:</strong> None.</li>
            <li><strong>Third-Party Trackers or Analytics:</strong> None.</li>
            <li><strong>Cloud Accounts or Logins:</strong> None.</li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>2. Permissions & Usage</h2>
          
          <div className={styles.card}>
            <h3 className={styles.h3}>A. Microphone Access (<code>RECORD_AUDIO</code>)</h3>
            <p><strong>Purpose:</strong> Used solely for real-time acoustic frequency analysis in reactive visualizer modes (Mic Wave, Pulse, Reticle).</p>
            <p><strong>Processing:</strong> Raw audio samples are processed locally using Fast Fourier Transform (FFT) to calculate instantaneous amplitude levels.</p>
            <p><strong>Zero Storage / Zero Transmission:</strong> Audio samples are discarded immediately after computing frame levels (every ~16ms). No audio is ever recorded to disk, retained in memory, or transmitted across any network.</p>
          </div>

          <div className={styles.card}>
            <h3 className={styles.h3}>B. Notification Access (<code>NotificationListenerService</code>)</h3>
            <p><strong>Purpose:</strong> Detects active media playback sessions (track title, artist, playback state) to fetch corresponding synced lyrics and display them on the rear LED matrix.</p>
            <p><strong>Scope:</strong> The service only reads MediaController metadata and playback status from active media applications (Spotify, YouTube Music, Apple Music).</p>
            <p><strong>Zero Personal Data:</strong> Non-media notifications (messages, chats, emails, banking alerts) are strictly ignored. No notification content is ever saved, logged, or shared.</p>
          </div>

          <div className={styles.card}>
            <h3 className={styles.h3}>C. Foreground Services (<code>FOREGROUND_SERVICE</code>)</h3>
            <p><strong>Purpose:</strong> Allows Live Lyrics and Music Visualizers to project smoothly to the rear matrix while your phone screen is off or when switching apps.</p>
            <p>A persistent notification is shown in your status drawer so you can stop the service at any time.</p>
          </div>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>3. Network Services</h2>
          <p>
            When Live Lyrics is active, the app queries the public LRCLib database (<code>https://lrclib.net</code>) using only the current <strong>song title</strong>, <strong>artist name</strong>, and <strong>album name</strong> to fetch synced lyrics.
          </p>
          <ul className={styles.list}>
            <li>No user identifiers, device IDs, advertising tokens, or personal information are attached to requests.</li>
            <li>Fetched lyrics are cached locally on your device in an offline SQLite database so identical songs do not require repeat network requests.</li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>4. Local Storage</h2>
          <p>All user animations, custom presets, and cached lyrics are stored in your device&apos;s private app directory:</p>
          <ul className={styles.list}>
            <li>Saved presets (<code>/data/data/com.rickastley.glyphodyssey/files/presets</code>)</li>
            <li>Offline lyrics database (<code>/data/data/com.rickastley.glyphodyssey/databases/odyssey_lyrics.db</code>)</li>
          </ul>
          <p>Uninstalling the app removes all associated local data.</p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>5. Open Source Verification</h2>
          <p>
            Glyph Odyssey is completely open-source. You can review the full source code and verify these privacy guarantees directly on GitHub:
          </p>
          <p>
            <a href="https://github.com/tezz-e/OdysseyGlyph" target="_blank" rel="noopener noreferrer" className={styles.link}>
              github.com/tezz-e/OdysseyGlyph &rarr;
            </a>
          </p>
        </section>

        <section className={styles.section}>
          <h2 className={styles.h2}>6. Contact</h2>
          <p>
            If you have questions or concerns regarding this policy, please open an issue on GitHub:
          </p>
          <p>
            <a href="https://github.com/tezz-e/OdysseyGlyph/issues" target="_blank" rel="noopener noreferrer" className={styles.link}>
              github.com/tezz-e/OdysseyGlyph/issues &rarr;
            </a>
          </p>
        </section>

        <footer className={styles.footer}>
          <p>&copy; {new Date().getFullYear()} Glyph Odyssey. Open source under MIT/GPL.</p>
        </footer>
      </div>
    </main>
  );
}
