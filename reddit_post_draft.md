okay so this started because I wanted to watch the Odyssey trailer on the back of my phone. as a meme.

getting it to look decent on the 25×25 matrix took way longer than it should have. turns out naive downsampling looks like garbage — you have to actually average the pixels or faces turn into noise. anyway I went down that rabbit hole and ended up with a pipeline that didn't care what video you gave it, so I kept going.

**what it does:**
- convert any video from your gallery and play it on the matrix. everything stays on-device
- live lyrics — reads whatever's playing (any app, not just spotify), pulls synced lyrics from the internet, scrolls them on your glyphs in real time. if the song has no lyrics it falls back to a built-in visualizer so the matrix isn't just sitting there dark
- widget + quick settings tile — someone pointed out that glyph toys stop after 10 minutes, which kills it mid-album. live lyrics now runs as a background service you can toggle from your home screen or notification shade. stays on as long as you want

**install:** play protect blocks it because of the notification listener permission (needed to read what's playing). blanket policy, not an actual detection — [0/66 on virustotal](https://www.virustotal.com/gui/file/578b874776cc2773b39eb972232d2fbe65dc3004c30083b4d43403ae5d18f37d), [clean on koodous](https://koodous.com/apks/578b874776cc2773b39eb972232d2fbe65dc3004c30083b4d43403ae5d18f37d/general-information) too. it can be installed via adb for now.

also posted on [nothing.community](https://nothing.community/d/61270-i-built-an-app-that-converts-any-video-into-a-glyph-matrix-animation) if you want to see the thread.

website + install guide: [https://odyssey-glyph.vercel.app/](https://odyssey-glyph.vercel.app/)  
github: [https://github.com/tezz-e/OdysseyGlyph](https://github.com/tezz-e/OdysseyGlyph)
