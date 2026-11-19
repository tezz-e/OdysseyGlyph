# Glyph Odyssey — Focused Feature Brainstorm

This is a deliberately small product direction. The app already covers importing media, rendering GIFs/video, visualizers, lyric styling, presets, Glyph Toys, and an LRC import path. The next work should sharpen the experiences the app can credibly own rather than recreate a general Glyph editor, a GIF compositor, or Nothing OS utilities.

## Product principle

The best interactions should feel immediate:

> Tap once, look at the back of the phone, and something useful or expressive happens.

The rear matrix is low-resolution and outward-facing. It is strongest when it communicates a single thing clearly, not when it is treated as a tiny general-purpose display.

---

## 1. Trustworthy Live Lyrics

### Why this matters

Live Lyrics is the app's strongest differentiated behavior. The widget already provides the right entry point: a user taps it once and the foreground service starts, while the persistent notification reports that it is listening/syncing.

The present weakness is identity resolution. A user playing Karan Aujla's **Rap Killa** can receive lyrics for a different song with the same title. The current resolver searches a cleaned `title + artist` string, then accepts the first result with synced lyrics. A result merely having lyrics is not evidence that it is the song that is playing.

### Desired experience

`Tap widget` → `lyrics begin on the rear matrix`.

For the normal case, that is the whole experience. No library setup, no search UI, no confirmation dialog.

If the app cannot identify the track confidently, it must not confidently show the wrong lyrics. The existing fallback visual takes over and the existing persistent notification becomes the recovery surface.

### Resolver design

Capture the richest available media-session identity:

- exact title and artist;
- album;
- duration;
- playback source/package;
- stable media/session identifiers when provided by the player.

Keep multiple metadata forms instead of stripping detail too early:

- exact metadata;
- cleaned metadata;
- transliterated metadata where helpful;
- title and artist scored separately;
- featured artists, album versions, and parenthetical qualifiers retained as useful signals.

Rank eligible lyric candidates rather than taking the first search result. A candidate should be assessed using:

- title similarity;
- artist similarity;
- album agreement;
- duration difference;
- the confidence gap between the top two candidates.

An unrelated song with a matching title should be rejected when artist or duration disagrees.

### Behavior by confidence

| State | Rear matrix | Persistent notification |
|---|---|---|
| High confidence | Start lyrics immediately. | `Rap Killa — Karan Aujla` / `Lyrics syncing to Glyph` |
| Ambiguous | Keep the fallback visual active. | `Lyrics need confirmation` / `Tap to choose the correct match` |
| No usable synced lyrics | Keep the fallback visual active. | `No synced lyrics found` / `Choose lyrics` or `Import .lrc` |
| Previously saved match | Start the saved lyrics immediately. | `Using your saved lyrics` |

The widget stays intentionally simple: it starts/stops the feature. It is not a diagnostics screen.

### Correction flow

Tapping an ambiguous notification opens a narrow track picker, not the full Lyric Studio:

- Now playing: title and artist from the media session;
- two or three candidate tracks with title, artist, album, and duration;
- `Search again`;
- `Import .lrc`.

Selecting a result immediately uses it and saves the choice for future playback. There should also be a way to forget or replace a saved match.

### Saved Lyrics / My Matches

This is the underlying persistence system, not a burden imposed before first use.

It stores:

- high-confidence automatic matches cached locally;
- user-corrected matches, which take priority;
- imported `.lrc` files linked to a track identity where possible.

Lookup order:

`user correction or imported LRC` → `verified local match` → `online resolver` → `fallback + notification recovery`

Most users never need to open this screen. Power users can review, replace, import, or delete mappings. A good name is **Saved Lyrics** or **My Matches**, not a large, intimidating “Lyric Library.”

### Implementation guardrails

- Do not describe a candidate as “syncing” until its identity is sufficiently credible.
- Persist corrections; a user must never solve the same title collision twice.
- Cache selected LRC data for repeat playback and reduced network dependence.
- Use fallback output rather than incorrect text when confidence is low.
- Keep the widget-first start flow intact.

---

## 2. Quick Signal

### The job

Quick Signal is a small, outward-facing communication mode for the rear matrix:

> The person looking at the back of the phone can read a short message without the owner needing to say it aloud or turn the phone around.

Useful examples:

- `WAIT`
- `BRB`
- `CALL ME`
- `YES`
- `♥`
- `↑`
- `!`
- a name, nickname, or private in-joke

This is not a GIF editor, animation composer, social gallery, or a generic text-art tool.

### First-release interaction

1. Enter a short phrase or select a legible symbol.
2. See the actual 25×25 matrix preview.
3. Tap **SHOW**.
4. Clear it immediately, or let it expire.

Display choices:

- 1–4 characters: large, stationary;
- short phrase: automatically scroll at a readable pace;
- longer strings: reject or shorten rather than rendering unreadable pixel noise.

Offer durations such as `15 sec`, `1 min`, and `Until cleared`, plus a one-tap clear control.

Keep a small set of recent or favourite signals—six is enough—for situations users repeat.

### Visual and interaction rules

- Preview with the real matrix grid, not a phone-screen approximation.
- Provide a small curated symbol set that is verified to remain legible on 25×25; arbitrary emoji will often become noise.
- One type treatment and one motion rule are better than a menu of effects.
- Starting a Quick Signal should clearly manage ownership of the Glyph matrix and restore the prior app-owned state when feasible.

### Explicit non-goals

- no animation timeline;
- no sticker/template marketplace;
- no AI phrase generator;
- no social feed or sharing layer;
- no trigger/automation engine;
- no attempt to compete with full matrix/GIF editors.

---

## 3. Speak to Show (translation feasibility candidate)

### The job

Speak to Show lets a person place the phone rear-up on a table, speak a short phrase, and let the person across from them read its translation on the rear Matrix.

Its promise is deliberately modest:

> Say it once. Let them read it.

It is not a claim to replace a full two-way conversation translator, continuous live subtitles, or a general-purpose AI assistant.

### Why it belongs here

The rear Matrix is unusually suited to a brief, outward-facing message. This turns a physical gesture—placing the phone with its rear visible—into a practical social interaction instead of another front-screen app flow.

### One-time setup

The user sets up a small number of saved language pairs:

- `I speak`: source language;
- `Show to them`: target language;
- output form: native script when it is legible on the Matrix, or an explicitly labelled Romanized output.

For example: `EN → PA` or `HI → EN`.

A dedicated compact widget or Quick Settings entry starts this mode directly. It must not overload the Live Lyrics widget's simple on/off meaning.

### Normal end-user flow

1. Tap the dedicated **Speak to Show** widget.
2. The persistent notification becomes: `Speak to Show ready` / `Hold the Glyph Button to translate EN → PA`.
3. Put the phone rear-up on the table. The Matrix shows a simple `READY` state plus the active language direction.
4. Hold the rear Glyph Button while speaking a short phrase.
5. The Matrix shows a clear listening state—one restrained visual cue, not a decorative visualizer.
6. Release the button. The app transcribes and translates the utterance.
7. The translated text appears, oriented for the person across the table. A following hold-and-release replaces it.

The output should be a large, concise phrase or a few readable chunks. It should never become a long, tiny, endlessly scrolling paragraph.

### Scope boundary: one direction in v1

Version one translates the phone owner's speech for the person facing the rear Matrix. That is a coherent interaction.

A complete two-way conversation is not. When the other person replies, their translation needs to face the owner, while the Matrix is facing them. Solving that requires awkward flipping, direction switching, or another display surface. Do not pretend that a single outward-facing 25×25 display is a complete bilingual conversation device.

### Privacy and control

- The microphone starts only after an explicit widget/tile action.
- Holding the Glyph Button is the speech boundary; it does not listen continuously.
- The persistent notification remains the stop/status surface.
- Saved language pairs are useful; storing conversation history is not required.
- A user can immediately repeat an utterance if recognition was wrong.

### Language and typography rules

- Do not silently transliterate every language into ASCII.
- Native-script output is preferred only where it remains legible on the Matrix.
- Romanization is an intentional, visibly labelled choice: a pronunciation aid, not a universal substitute for a reader's written language.
- Begin with a small, tested set of language pairs and short turns. Validate understanding with bilingual users before broadening support.

### Feasibility gate before production work

This should begin as a physical-device spike, not an implementation commitment. Validate on a real Phone (3):

- hold-to-talk input and Glyph Button lifecycle;
- speech recognition accuracy in quiet and ordinary noisy settings;
- translation latency and quality for chosen language pairs;
- Matrix text readability at conversational distance;
- battery drain and thermal behaviour over repeated turns;
- coexistence with existing Glyph Toys and app-controlled Matrix output.

The likely pipeline is microphone capture → voice/utterance detection → on-device ASR → translation → Matrix rendering. Model/runtime choices, throughput, and hardware delegation must be benchmarked on the actual device rather than assumed from unrelated chipset benchmarks.

### Explicit non-goals

- no passive activation merely because the phone is flipped;
- no always-listening microphone;
- no promise of full bidirectional live conversation in v1;
- no global dictation overlay;
- no Accessibility Service-based insertion into other apps;
- no attempt to become a clone of a general translation or AI-assistant app.

---

## What not to build next

These are either already covered in the app, handled by Nothing OS, or much better served by existing specialist tools:

- a GIF/video/text composer or full Matrix editor;
- more visualizer modes as a feature strategy;
- recording/OBS tally indicators (the phone has a dedicated rear recording indicator);
- another timer, clock, battery, notification, or focus utility;
- broad automation rules for apps, contacts, keywords, or social sharing.

The near-term standard is not feature count. It is whether the app can make a rear-matrix interaction feel more dependable and more human in a real moment.
