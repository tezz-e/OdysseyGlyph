# Odyssey Glyph ProGuard / R8 Rules

# Preserve Kotlin Attributes & Annotations
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable

# Preserve Data Models and Entities
-keep class com.example.odysseyglyph.Preset { *; }
-keep class com.example.odysseyglyph.MediaTrackIdentity { *; }
-keep class com.example.odysseyglyph.LyricCandidate { *; }
-keep class com.example.odysseyglyph.LyricResolution { *; }
-keep class com.example.odysseyglyph.LyricConfidenceLevel { *; }
-keep class com.example.odysseyglyph.LyricMatchScore { *; }
-keep class com.example.odysseyglyph.MatchedLyricRecord { *; }
-keep class com.example.odysseyglyph.MusicPlaybackState { *; }

# Preserve Nothing OS Glyph / Ketchum SDK interfaces
-keep class com.nothing.ketchum.** { *; }
-keep interface com.nothing.ketchum.** { *; }
-keep class com.nothing.glyph.** { *; }
-keep interface com.nothing.glyph.** { *; }

# Preserve Custom Views referenced in XML layouts
-keep class com.example.odysseyglyph.*View { *; }
-keep class com.example.odysseyglyph.BorderBeamDrawable { *; }
-keep class com.example.odysseyglyph.TiltSpecularCardView { *; }
-keep class com.example.odysseyglyph.LiveAudioMeterView { *; }
-keep class com.example.odysseyglyph.GlyphMatrixMirrorView { *; }
-keep class com.example.odysseyglyph.CropOverlayView { *; }
-keep class com.example.odysseyglyph.CenteredVideoView { *; }
-keep class com.example.odysseyglyph.CenteredImageView { *; }
-keep class com.example.odysseyglyph.KnobView { *; }
-keep class com.example.odysseyglyph.LiquidGlassSegmentedBar { *; }
-keep class com.example.odysseyglyph.SegmentedPillSlider { *; }
-keep class com.example.odysseyglyph.GlyphProgressView { *; }
-keep class com.example.odysseyglyph.OscilloscopeReticleView { *; }
-keep class com.example.odysseyglyph.TactileWaveformScrubberView { *; }
-keep class com.example.odysseyglyph.CipherTextView { *; }

# Preserve Services and Broadcast Receivers
-keep class com.example.odysseyglyph.*Service* { *; }
-keep class com.example.odysseyglyph.OdysseyWidgetProvider { *; }

# OkHttp & Networking
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.** { *; }
-keepnames interface okhttp3.** { *; }
