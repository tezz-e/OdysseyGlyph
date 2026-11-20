"use client";

import { motion, useReducedMotion } from "framer-motion";
import styles from "./page.module.css";
import { useEffect, useState, useCallback } from "react";

import BootSequence from "./components/BootSequence";
import DotMatrixText from "./components/DotMatrixText";
import LedGrid from "./components/LedGrid";
import CopyButton from "./components/CopyButton";

const springDefault = { type: "spring" as const, bounce: 0, duration: 0.8 };
const springBouncy  = { type: "spring" as const, bounce: 0.2, duration: 0.6 };

const GITHUB_URL  = "https://github.com/tezz-e/OdysseyGlyph";
const RELEASES_URL = "https://github.com/tezz-e/OdysseyGlyph/releases";

// Precomputed grayscale values for the box-filter downsampling demo.
// "Naive" has extreme jumps (simulating nearest-neighbour artefacts).
// "Box filter" shows the same scene averaged into a compressed tonal range.
const naivePixels = [92, 8, 88, 12, 95, 18, 82, 5, 78, 22, 90, 6, 85, 10, 92, 8, 80, 18, 75, 12, 88, 6, 92, 14, 82];
const boxPixels   = [46, 38, 48, 32, 58, 36, 42, 28, 44, 40, 50, 30, 52, 34, 54, 28, 44, 38, 46, 32, 50, 28, 54, 36, 48];

export default function Home() {
  const [mounted, setMounted] = useState(false);
  const [isBooting, setIsBooting] = useState(true);
  const shouldReduceMotion = useReducedMotion();

  const handleBootComplete = useCallback(() => {
    setIsBooting(false);
  }, []);

  useEffect(() => { setMounted(true); }, []);
  if (!mounted) return null;

  return (
    <main className={styles.main}>
      {isBooting ? (
        <BootSequence onComplete={handleBootComplete} />
      ) : null}

      {/* ── NAV ────────────────────────────────────────────────────── */}
      <nav className={styles.nav}>
        <a href="#hero" className={styles.navLogo}>GLYPH ODYSSEY</a>
        <div className={styles.navLinks}>
          <a href="#features"     className={styles.navLink}>Features</a>
          <a href="#how-it-works" className={styles.navLink}>How It Works</a>
          <a href="#install"      className={styles.navLink}>Install</a>
          <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={styles.navLink}>GitHub</a>
        </div>
        <a href="#install" className={styles.navCta}>
          Install Guide
        </a>
      </nav>

      {/* ── HERO ───────────────────────────────────────────────────── */}
      <section id="hero" className={styles.hero}>
        {!isBooting && (
          <>
            <div style={{ display: "flex", gap: "1.5rem", flexWrap: "wrap", justifyContent: "center", marginBottom: "1rem" }}>
              <DotMatrixText text="GLYPH" color="var(--foreground)" delayOffset={0} />
              <DotMatrixText text="ODYSSEY" color="var(--accent)" delayOffset={0.75} />
            </div>

            <motion.p
              className={styles.subtitle}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...springDefault, delay: shouldReduceMotion ? 0 : 0.5 }}
            >
              An open-source lyrics engine, hardware video processor, and quick signal studio for the Nothing Phone (3) Glyph Matrix.
            </motion.p>

            <motion.div
              className={styles.heroCtas}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...springDefault, delay: shouldReduceMotion ? 0 : 0.8 }}
            >
              <a href="#install" className={styles.downloadBtn}>
                Install Instructions
              </a>
              <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={styles.githubBtn}>
                View on GitHub
              </a>
            </motion.div>

            <motion.div
              style={{ display: "flex", justifyContent: "center", marginTop: "1rem" }}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...springDefault, delay: shouldReduceMotion ? 0 : 1.0 }}
            >
              <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer" style={{ opacity: 0.8, transition: "opacity 0.2s" }} onMouseOver={(e) => e.currentTarget.style.opacity = "1"} onMouseOut={(e) => e.currentTarget.style.opacity = "0.8"}>
                <img src="https://img.shields.io/github/downloads/tezz-e/OdysseyGlyph/total?style=for-the-badge&color=black&labelColor=111111&logo=github" alt="Total Downloads" style={{ border: "1px solid #333" }} />
              </a>
            </motion.div>


            <motion.p
              className={styles.deviceReq}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ ...springDefault, delay: shouldReduceMotion ? 0 : 1.3 }}
            >
              Requires Nothing Phone (3)
            </motion.p>
          </>
        )}
      </section>

      {/* ── INTERACTIVE MATRIX SIMULATION ──────────────────────────── */}
      <section className={styles.proof}>
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={springDefault}
          style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "1rem" }}
        >
          <LedGrid />
          <p className={styles.proofComing} style={{ opacity: 0.5, letterSpacing: "1px" }}>[ INTERACTIVE HARDWARE SIMULATION ]</p>
        </motion.div>
      </section>

      {/* ── ORIGIN STORY ───────────────────────────────────────────── */}
      <section className={styles.origin}>
        <motion.blockquote
          className={styles.originText}
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={springDefault}
        >
          It started as a joke — I wanted to watch the Odyssey trailer "as Nolan intended," on the
          back of my phone, for a meme. Getting one video onto the Glyph Matrix properly took way more
          fixing and iterating than expected. Somewhere in there I realized the pipeline I'd built
          didn't care what video it was. So I generalized it.
        </motion.blockquote>
      </section>

      {/* ── FEATURES ───────────────────────────────────────────────── */}
      <section id="features" className={styles.features}>

        {/* 1: Hardware Video Pipeline */}
        <div className={styles.featureCard}>
          <div className={styles.featureContent}>
            <motion.h2 className={styles.featureTitle}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
              HARDWARE VIDEO PIPELINE
            </motion.h2>
            <motion.p className={styles.featureDesc}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
              Pick any video, GIF, or photo from your gallery. Pinch and pan to crop a region into the 25×25
              overlay with live video playback behind the frame. A streaming MediaCodec pipeline runs on Qualcomm&apos;s
              hardware video processing unit, extracting raw monochrome luminance straight from YUV byte buffers.
              Processing times dropped from 35 seconds to under 2 seconds with zero Bitmap allocations.
            </motion.p>
          </div>
          <motion.div className={styles.visualPlaceholder}
            initial={{ opacity: 0, scale: 0.9 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-100px" }} transition={springBouncy}>
            <div style={{ display: "flex", flexDirection: "column", gap: "1rem", alignItems: "center", width: "80%" }}>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.6rem", color: "var(--accent)", letterSpacing: "2px" }}>[ QUALCOMM VPU PIPELINE ]</div>
              <div style={{ display: "flex", alignItems: "baseline", gap: "0.75rem" }}>
                <span style={{ fontFamily: "var(--font-mono)", fontSize: "2.8rem", color: "var(--foreground)", fontWeight: "bold" }}>1.8s</span>
                <span style={{ fontFamily: "var(--font-mono)", fontSize: "0.9rem", color: "var(--muted-foreground)", textDecoration: "line-through" }}>35.2s</span>
              </div>
              <div style={{ width: "100%", height: "4px", background: "#222", borderRadius: "2px", overflow: "hidden" }}>
                <motion.div
                  style={{ height: "100%", background: "var(--accent)" }}
                  animate={shouldReduceMotion ? { width: "100%" } : { width: ["0%", "100%"] }}
                  transition={{ duration: 1.8, repeat: Infinity, ease: "easeInOut" }}
                />
              </div>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.65rem", color: "var(--muted-foreground)", letterSpacing: "1px" }}>ZERO-COPY YUV LUMINANCE · 15 FPS</div>
            </div>
          </motion.div>
        </div>

        {/* 2: Quick Signal */}
        <div className={`${styles.featureCard} ${styles.reverse}`}>
          <div className={styles.featureContent}>
            <motion.h2 className={styles.featureTitle}
              initial={{ opacity: 0, x: 50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
              QUICK SIGNAL
            </motion.h2>
            <motion.p className={styles.featureDesc}
              initial={{ opacity: 0, x: 50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
              Broadcast instant status messages and symbols on the rear 25×25 matrix without unlocking your
              phone or playing audio. Pick from pre-made presets (WAIT, BRB, STOP, TAXI) or type custom text.
              Choose between Pixel, Smooth, and Blocky font rendering modes to punch through bright daylight
              or scroll cleanly across the LEDs.
            </motion.p>
          </div>
          <motion.div className={styles.visualPlaceholder}
            initial={{ opacity: 0, scale: 0.9 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-100px" }} transition={springBouncy}>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", alignItems: "center" }}>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.6rem", color: "var(--accent)", letterSpacing: "2px" }}>[ REAR MATRIX SIGNAL ]</div>
              <motion.div
                style={{ fontFamily: "var(--font-mono)", fontSize: "2.4rem", color: "var(--foreground)", padding: "0.6rem 1.8rem", border: "1px solid var(--border)", borderRadius: "12px", background: "#0a0a0a", letterSpacing: "4px" }}
                animate={shouldReduceMotion ? {} : { opacity: [0.7, 1, 0.7], borderColor: ["var(--border)", "var(--accent)", "var(--border)"] }}
                transition={{ duration: 2, repeat: Infinity }}>
                WAIT
              </motion.div>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.6rem", color: "var(--muted-foreground)", letterSpacing: "1px" }}>PIXEL · SMOOTH · BLOCKY</div>
            </div>
          </motion.div>
        </div>

        {/* 3: Live Lyrics */}
        <div className={styles.featureCard}>
          <div className={styles.featureContent}>
            <motion.h2 className={styles.featureTitle}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
              LIVE LYRICS
            </motion.h2>
            <motion.p className={styles.featureDesc}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
              A background service that detects whatever is currently playing system-wide (Spotify, YouTube Music,
              Apple Music, local players) via Android&apos;s MediaSession, fetches time-synced lyrics from LRCLib, and
              scrolls them live across the Glyph Matrix. Features smart consensus scoring to prevent mismatches on live
              or remastered cuts, and caches fetched lyrics in a local SQLite database so repeat plays work completely
              offline with zero network requests. If a song has no lyrics, it automatically falls back to an audio visualizer.
            </motion.p>
          </div>
          <motion.div className={styles.visualPlaceholder}
            initial={{ opacity: 0, scale: 0.9 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-100px" }} transition={springBouncy}>
            <motion.div
              style={{ fontFamily: "var(--font-mono)", fontSize: "3rem", color: "var(--foreground)", whiteSpace: "nowrap" }}
              animate={shouldReduceMotion ? {} : { x: ["100%", "-100%"] }}
              transition={{ duration: 5, repeat: Infinity, ease: "linear" }}>
              NEVER GONNA GIVE YOU UP
            </motion.div>
          </motion.div>
        </div>

        {/* 4: Widget, QS Tile & Visualizers */}
        <div className={`${styles.featureCard} ${styles.reverse}`}>
          <div className={styles.featureContent}>
            <motion.h2 className={styles.featureTitle}
              initial={{ opacity: 0, x: 50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
              WIDGET &amp; QS TILE
            </motion.h2>
            <motion.p className={styles.featureDesc}
              initial={{ opacity: 0, x: 50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
              Live Lyrics, Quick Signal, and the standalone visualizers run as independent background services
              that bypass Nothing&apos;s 10-minute toy timeout. Toggle them on the fly from a home screen widget
              or Quick Settings tile without ever opening the app, running real-time frequency bars, expanding
              concentric pulses, or animated reticles as long as you want.
            </motion.p>
          </div>
          <motion.div className={styles.visualPlaceholder}
            initial={{ opacity: 0, scale: 0.9 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-100px" }} transition={springBouncy}>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", alignItems: "center" }}>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.6rem", color: "var(--muted-foreground)", letterSpacing: "2px" }}>QUICK SETTINGS</div>
              <motion.div
                style={{ width: "72px", height: "72px", borderRadius: "18px", border: "1px solid var(--border)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", fontSize: "1.5rem" }}
                whileTap={shouldReduceMotion ? {} : { scale: 0.92 }}
                animate={shouldReduceMotion ? {} : { borderColor: ["var(--border)", "var(--foreground)", "var(--border)"] }}
                transition={{ duration: 2, repeat: Infinity }}>
                ◈
              </motion.div>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: "0.55rem", color: "var(--accent)", letterSpacing: "1px" }}>GLYPH ODYSSEY</div>
            </div>
          </motion.div>
        </div>

        {/* 5: Toy Manager */}
        <div className={styles.featureCard}>
          <div className={styles.featureContent}>
            <motion.h2 className={styles.featureTitle}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
              TOY MANAGER
            </motion.h2>
            <motion.p className={styles.featureDesc}
              initial={{ opacity: 0, x: -50 }} whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
              Save, preview, and manage your processed animations as Glyph Toys. Browse your preset
              library, swap between them on the fly, and delete what you don&apos;t need, all without
              touching Nothing&apos;s GlyphMatrixEditor.
            </motion.p>
          </div>
          <motion.div className={styles.visualPlaceholder}
            initial={{ opacity: 0, scale: 0.9 }} whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, margin: "-100px" }} transition={springBouncy}
            whileHover={shouldReduceMotion ? {} : { scale: 1.05, rotate: 2 }}>
            <div className={styles.presetList}>
              <div className={styles.presetLabel}>[ PRESETS ]</div>
              <div className={styles.presetItem}>odyssey.bin</div>
              <div className={styles.presetItem} style={{ opacity: 0.6 }}>my_clip.bin</div>
              <div className={styles.presetItem} style={{ opacity: 0.3 }}>untitled.bin</div>
            </div>
          </motion.div>
        </div>

      </section>

      {/* ── HOW IT WORKS ───────────────────────────────────────────── */}
      <section id="how-it-works" className={styles.howItWorks}>
        <motion.h2 className={styles.sectionTitle}
          initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
          HOW IT WORKS
        </motion.h2>

        <div className={styles.sectionSublabel}>[ VIDEO PIPELINE ]</div>
        <div className={styles.howGrid}>

          {/* 1: Box-filter downsampling */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
            <div className={styles.howVisual}>
              <div className={styles.beforeAfter}>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>NAIVE</div>
                  <div className={styles.baGrid}>
                    {naivePixels.map((v, i) => (
                      <div key={i} className={styles.baCell} style={{ background: `hsl(0,0%,${v}%)` }} />
                    ))}
                  </div>
                </div>
                <div className={styles.baArrow}>→</div>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>BOX FILTER</div>
                  <div className={styles.baGrid}>
                    {boxPixels.map((v, i) => (
                      <div key={i} className={styles.baCell} style={{ background: `hsl(0,0%,${v}%)` }} />
                    ))}
                  </div>
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Box-Filter Downsampling</h3>
            <p className={styles.howBody}>
              Naive pixel-dropping (nearest-neighbour) throws away most of the source frame at 25×25, turning
              faces into unrecognizable blobs. Box-filter downsampling mathematically averages every pixel
              in each output cell&apos;s corresponding source region — an area-weighted mean that preserves
              edges and visible detail.
            </p>
          </motion.div>

          {/* 2: Adaptive S-curve */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
            <div className={styles.howVisual}>
              <div className={styles.beforeAfter} style={{ alignItems: "center" }}>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>FLAT</div>
                  <div className={styles.contrastBar} style={{ background: "linear-gradient(to right,#222,#555,#888)" }} />
                </div>
                <div className={styles.baArrow}>→</div>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>S-CURVE</div>
                  <div className={styles.contrastBar} style={{ background: "linear-gradient(to right,#080808,#fff)" }} />
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Adaptive S-Curve Contrast</h3>
            <p className={styles.howBody}>
              A flat grayscale conversion looks washed out on a 25×25 monochrome LED grid — mid-range greys
              are indistinguishable. Per-frame local contrast stretching maps each frame&apos;s actual tonal range
              to 0–255, then an S-curve and unsharp mask ensure faces and shapes punch through on the
              physical hardware.
            </p>
          </motion.div>

          {/* 3: Gesture -> pixel mapping */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.2 }}>
            <div className={styles.howVisual}>
              <div className={styles.beforeAfter} style={{ alignItems: "center" }}>
                <div className={styles.baPanel} style={{ alignItems: "center" }}>
                  <div className={styles.baPanelLabel}>SCREEN</div>
                  <div className={styles.cropSource}>
                    <div className={styles.cropCircle} />
                  </div>
                </div>
                <div className={styles.baArrow}>→</div>
                <div className={styles.baPanel} style={{ alignItems: "center" }}>
                  <div className={styles.baPanelLabel}>SOURCE PIXELS</div>
                  <div className={styles.cropGrid}>
                    {Array.from({ length: 9 }).map((_, i) => (
                      <div key={i} className={styles.cropCell} />
                    ))}
                  </div>
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Gesture → Source Pixel Mapping</h3>
            <p className={styles.howBody}>
              The pinch/pan crop overlay lets you frame any portion of the video on screen. An inverse-transform
              matrix converts the crop circle&apos;s screen coordinates into exact source-frame pixel coordinates,
              so the box filter always samples from the right region regardless of zoom level or pan offset.
            </p>
          </motion.div>

          {/* 4: Hardware-Accelerated Decoding */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.3 }}>
            <div className={styles.howVisual}>
              <div className={styles.beforeAfter} style={{ alignItems: "center" }}>
                <div className={styles.baPanel} style={{ alignItems: "center" }}>
                  <div className={styles.baPanelLabel}>RETRIEVER</div>
                  <div style={{ fontFamily: "var(--font-mono)", fontSize: "1.1rem", color: "var(--muted-foreground)" }}>~35s</div>
                </div>
                <div className={styles.baArrow}>→</div>
                <div className={styles.baPanel} style={{ alignItems: "center" }}>
                  <div className={styles.baPanelLabel}>MEDIACODEC</div>
                  <div style={{ fontFamily: "var(--font-mono)", fontSize: "1.1rem", color: "var(--accent)", fontWeight: "bold" }}>&lt; 2s</div>
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Hardware-Accelerated Decoding</h3>
            <p className={styles.howBody}>
              Early versions used Android&apos;s MediaMetadataRetriever, which throttled CPU and took 35 seconds for a 9-second clip. Ripping it out for a streaming MediaCodec pipeline on Qualcomm&apos;s hardware VPU with zero-copy Y-plane luminance extraction cut processing time to under 2 seconds with zero Bitmap allocations.
            </p>
          </motion.div>

        </div>

        <div className={styles.sectionSublabel} style={{ marginTop: "4rem" }}>[ LYRICS ENGINE ]</div>
        <div className={styles.howGrid}>

          {/* 5 — Script-Agnostic Transliteration */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
            <div className={styles.howVisual}>
              <div style={{ display: "flex", alignItems: "center", gap: "1rem", fontFamily: "var(--font-mono)", fontSize: "0.85rem", background: "#111", padding: "0.75rem 1.5rem", borderRadius: "4px" }}>
                <span style={{ color: "var(--accent)" }}>夜に駆ける</span>
                <span style={{ color: "var(--muted-foreground)" }}>→</span>
                <span style={{ color: "var(--foreground)" }}>yoru ni kakeru</span>
              </div>
            </div>
            <h3 className={styles.howTitle}>Script-Agnostic Transliteration</h3>
            <p className={styles.howBody}>
              LRC lyrics arrive in whatever language the song is in. Japanese kanji, Korean Hangul, Arabic — none of those characters exist in a 25×25 pixel font. Android&apos;s built-in ICU Transliterator converts any script to phonetic Latin on-device, no network call, no API key. The matrix scrolls what the song sounds like, not untranslatable glyphs.
            </p>
          </motion.div>

          {/* 6 — Wordless Timestamps */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
            <div className={styles.howVisual}>
              <div style={{ display: "flex", width: "80%", height: "16px", gap: "2px", background: "#1a1a1a", padding: "4px" }}>
                <div style={{ flex: 4, background: "var(--foreground)" }}></div>
                <div style={{ flex: 1, background: "transparent" }}></div>
                <div style={{ flex: 2, background: "var(--foreground)" }}></div>
                <div style={{ flex: 1, background: "transparent" }}></div>
                <div style={{ flex: 6, background: "var(--foreground)" }}></div>
                <div style={{ flex: 1, background: "transparent" }}></div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Wordless Timestamps</h3>
            <p className={styles.howBody}>
              LRC files mark the start of each line, never individual words. The app estimates per-word timing by weighting each word proportionally by character count — longer words get more time — with extra hold added after punctuation since those have natural spoken pauses. A 25% blank gap at the end of each word creates a discrete strobe rather than a smear. No ML, no word-level data.
            </p>
          </motion.div>

          {/* 7 — Drift Prevention */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.2 }}>
            <div className={styles.howVisual}>
              <div className={styles.beforeAfter} style={{ width: "100%", alignItems: "center" }}>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>CALLBACK ONLY</div>
                  <div style={{ position: "relative", width: "100%", height: "2px", background: "#333", marginTop: "10px" }}>
                    <div style={{ position: "absolute", left: "50%", top: "-4px", width: "2px", height: "10px", background: "#555" }}></div>
                    <div style={{ position: "absolute", left: "70%", top: "-4px", width: "6px", height: "10px", background: "var(--accent)" }}></div>
                  </div>
                </div>
                <div className={styles.baPanel}>
                  <div className={styles.baPanelLabel}>500MS POLL</div>
                  <div style={{ position: "relative", width: "100%", height: "2px", background: "#333", marginTop: "10px" }}>
                    <div style={{ position: "absolute", left: "50%", top: "-4px", width: "2px", height: "10px", background: "#555" }}></div>
                    <div style={{ position: "absolute", left: "50%", top: "-4px", width: "6px", height: "10px", background: "var(--foreground)", transform: "translateX(-2px)" }}></div>
                  </div>
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Drift Prevention</h3>
            <p className={styles.howBody}>
              Spotify and YouTube Music only fire a position update when something changes — play, pause, skip. Between those events, position estimates drift and lyrics fall out of sync. The fix is a polling loop that reads the MediaController every 500ms to stay locked to real playback position.
            </p>
          </motion.div>
          {/* 8 — Per-Word Hyphenation */}
          <motion.div className={styles.howCard}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.3 }}>
            <div className={styles.howVisual}>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", fontFamily: "var(--font-mono)", fontSize: "0.7rem", background: "#111", padding: "0.75rem 1.25rem", borderRadius: "4px", width: "100%" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
                  <span style={{ color: "var(--foreground)" }}>IMPOSSIBLE</span>
                  <span style={{ color: "var(--accent)" }}>→</span>
                  <div style={{ display: "flex", flexDirection: "column", lineHeight: "1.2" }}>
                    <span style={{ color: "var(--foreground)" }}>IMPOS<span style={{ color: "var(--accent)" }}>-</span></span>
                    <span style={{ color: "var(--foreground)" }}>SIBLE</span>
                  </div>
                  <span style={{ color: "var(--accent)" }}>→</span>
                  <div style={{ display: "flex", gap: "0.3rem", alignItems: "center" }}>
                    <span style={{ color: "var(--foreground)" }}>IMP<span style={{ color: "var(--accent)" }}>-</span></span>
                    <span style={{ color: "var(--muted-foreground)", fontSize: "0.5rem" }}>then</span>
                    <span style={{ color: "var(--foreground)" }}>OSS<span style={{ color: "var(--accent)" }}>-</span></span>
                    <span style={{ color: "var(--muted-foreground)", fontSize: "0.5rem" }}>then</span>
                    <span style={{ color: "var(--foreground)" }}>IBLE</span>
                  </div>
                </div>
              </div>
            </div>
            <h3 className={styles.howTitle}>Per-Word Hyphenation</h3>
            <p className={styles.howBody}>
              A 25-pixel-wide LED grid can&apos;t fit most English words unbroken. For every word, the font engine measures its rendered pixel width, finds the mathematically optimal hyphen point that balances the two halves, and stacks them. If even that doesn&apos;t fit, it slices the word into sequential chunks and animates through them during the word&apos;s timeslice — the matrix spells out long words one piece at a time.
            </p>
          </motion.div>

        </div>
      </section>

      {/* ── INSTALL ────────────────────────────────────────────────── */}
      <section id="install" className={styles.install}>
        <motion.h2 className={styles.sectionTitle}
          initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
          INSTALLATION
        </motion.h2>

        <div className={styles.installInner}>

          {/* Device requirement */}
          <motion.div className={styles.installWarning}
            initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
            ⚠&nbsp; Requires Nothing Phone (3). Older models and the (4a) Pro have
            zone-based LED strips or lower-density arrays, not the full 25×25 addressable matrix required by this app.
          </motion.div>

          {/* Play Protect blocking notice */}
          <motion.div className={styles.playProtectWarning}
            initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.1 }}>
            <div className={styles.playProtectTitle}>🚫 Google Play Protect is currently blocking normal sideload install</div>
            <p>
              Any app declaring the <code className={styles.inlineCode}>NOTIFICATION_LISTENER</code> permission gets
              automatically hard-blocked by Play Protect when sideloaded — not based on actual harm, but blanket policy.
              0 of 66 VirusTotal vendors flag this APK. A formal Play Protect appeal is in progress with Google.
            </p>
            <p>Until the appeal clears, use ADB install below — it bypasses the PackageInstaller entirely.</p>
          </motion.div>

          {/* Primary: ADB install */}
          <motion.div
            initial={{ opacity: 0, y: 10 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={{ ...springDefault, delay: 0.15 }}>
            <div className={styles.methodLabel}>PRIMARY METHOD</div>
          </motion.div>

          <ol className={styles.installSteps}>
            {[
              {
                num: "01", title: "Enable Developer Options",
                body: "On your Nothing Phone (3), go to Settings → About Phone → tap Build Number 7 times. You'll see \"You are now a developer!\"",
              },
              {
                num: "02", title: "Enable USB Debugging",
                body: "Go to Settings → System → Developer Options → toggle USB Debugging on.",
              },
              {
                num: "03", title: "Install Android Platform Tools",
                body: (
                  <span>
                    Download{" "}
                    <a href="https://developer.android.com/tools/releases/platform-tools" target="_blank" rel="noopener noreferrer" className={styles.inlineLink}>
                      Android Platform Tools
                    </a>{" "}
                    for your computer. Extract it and open a terminal in that folder.
                  </span>
                ),
              },
              {
                num: "04", title: "Download the APK",
                body: (
                  <span>
                    Go to{" "}
                    <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer" className={styles.inlineLink}>
                      the Releases page
                    </a>{" "}
                    and download <code className={styles.inlineCode}>app-release.apk</code> to your computer.
                  </span>
                ),
              },
              {
                num: "05", title: "Run ADB Install",
                body: (
                  <div>
                    Plug in your phone via USB, accept the debugging prompt on-device, then run:
                    <div className={styles.codeWrapper}>
                      <code className={styles.adbCommand}>adb install app-release.apk</code>
                      <CopyButton text="adb install app-release.apk" />
                    </div>
                    That{"'"}s it. No Play Protect, no blocked install.
                  </div>
                ),
              },
            ].map(({ num, title, body }, i) => (
              <motion.li key={num} className={styles.installStep}
                initial={{ opacity: 0, x: -30 }} whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: "-100px" }}
                transition={{ ...springDefault, delay: i * 0.08 }}>
                <span className={styles.stepNum}>{num}</span>
                <div>
                  <div className={styles.stepTitle}>{title}</div>
                  <div className={styles.stepBody}>{body}</div>
                </div>
              </motion.li>
            ))}
          </ol>

          {/* Secondary: normal sideload — pending appeal */}
          <details className={styles.pendingInstall}>
            <summary className={styles.pendingSummary}>
              <span className={styles.pendingBadge}>PENDING PLAY PROTECT APPEAL</span>
              {" "}Normal sideload install
            </summary>
            <div className={styles.pendingContent}>
              <p>Once the appeal clears, you{"'"}ll be able to install directly from your phone without a computer.</p>
              <ol className={styles.installSteps} style={{ marginTop: "1.25rem" }}>
                {[
                  { num: "01", title: "Go to Releases", body: (<span>Head to <a href={RELEASES_URL} target="_blank" rel="noopener noreferrer" className={styles.inlineLink}>the Releases page on GitHub</a> and find the latest release.</span>) },
                  { num: "02", title: "Download the APK", body: (<span>Download <code className={styles.inlineCode}>app-release.apk</code> directly to your Nothing Phone.</span>) },
                  { num: "03", title: "Allow Unknown Sources", body: "Android will prompt you to allow installs from unknown sources. Enable it for your browser or Files app, then tap the downloaded APK to install." },
                  { num: "04", title: "Grant Permissions", body: "On first launch, grant media access. For Live Lyrics, also enable Notification Access in Android settings — the app will guide you there." },
                ].map(({ num, title, body }) => (
                  <li key={num} className={styles.installStep} style={{ opacity: 0.55 }}>
                    <span className={styles.stepNum}>{num}</span>
                    <div>
                      <div className={styles.stepTitle}>{title}</div>
                      <div className={styles.stepBody}>{body}</div>
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          </details>

          <details className={styles.buildFromSource}>
            <summary className={styles.buildSummary}>Building from source</summary>
            <div className={styles.buildContent}>
              <p>Clone the repo, open in Android Studio, sync Gradle, and run to a connected Nothing Phone.</p>
              <code className={styles.codeBlock}>git clone https://github.com/tezz-e/OdysseyGlyph.git</code>
            </div>
          </details>

        </div>
      </section>


      {/* ── SECURITY & TRUST ─────────────────────────────────────────── */}
      <section id="security" className={styles.install} style={{ paddingTop: "2rem" }}>
        <motion.h2 className={styles.sectionTitle}
          initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
          SECURITY & TRUST
        </motion.h2>

        <div className={styles.installInner}>
          <motion.div className={styles.howCard} style={{ width: "100%", padding: "2rem" }}
            initial={{ opacity: 0, y: 30 }} whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-100px" }} transition={springDefault}>
            <h3 className={styles.howTitle} style={{ marginBottom: "0.5rem" }}>Verifiable Open Source Builds</h3>
            <p className={styles.howBody} style={{ marginBottom: "1.5rem" }}>
              To guarantee safety, the APK distributed in our releases is built transparently by GitHub Actions — not on a personal computer. You can view the raw build logs to verify that the APK is compiled directly from the open-source code in this repository without any tampering.
            </p>

            <h3 className={styles.howTitle} style={{ marginBottom: "0.5rem" }}>Automated VirusTotal Scans</h3>
            <p className={styles.howBody}>
              Every release is automatically uploaded to VirusTotal by our automated pipeline. The release notes include a direct link to the scan analysis and the exact cryptographic SHA-256 hash of the APK, so you can be 100% certain the clean scan matches the file you download.
            </p>
          </motion.div>
        </div>
      </section>

      {/* ── FOOTER ─────────────────────────────────────────────────── */}
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerLinks}>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className={styles.footerLink}>
              GitHub
            </a>
            <a href={`${GITHUB_URL}/blob/main/LICENSE`} target="_blank" rel="noopener noreferrer" className={styles.footerLink}>
              GNU GPLv3 License
            </a>
            <a href="/privacy" className={styles.footerLink}>
              Privacy Policy
            </a>
          </div>
          <p className={styles.footerDisclaimer}>Not affiliated with Nothing Technology Limited.</p>
          <p className={styles.footerPrivacy}>
            Live Lyrics reads notification and media metadata locally on your device.
            No data is transmitted or stored externally.
          </p>
        </div>
      </footer>

    </main>
  );
}
