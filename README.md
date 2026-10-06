<p align="center">
  <img src="docs/sielo_brand_header.png" alt="Sielo" width="560" />
</p>

<p align="center">
  <strong>A tactile, lossless music streaming app crafted with Jetpack Compose & AndroidX Media3</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white" alt="Android" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/media/media3"><img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer-FF6F00.svg" alt="Media3" /></a>
  <a href="https://sielo-music.vercel.app"><img src="https://img.shields.io/badge/Website-sielo--music.vercel.app-0D1B2A.svg?logo=vercel&logoColor=white" alt="Website" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" /></a>
</p>

---

## 🌟 Distinctive & Unique Highlights

What sets **Sielo** apart from typical streaming clients:

### 1. 👥 End-to-End Encrypted "Listen Together" (Sub-150ms Sync)
- **Zero-Knowledge P2P Sessions**: Rooms are fortified with **AES-256-GCM** client-side encryption. Server and broker relays never see plain text room messages, usernames, or song metadata.
- **Clock Drift Compensation**: Micro-second continuous drift estimation delivers tight sub-150ms audio synchronization across peer devices.
- **In-Room Co-Queueing & Live Chat**: Seamlessly discover and queue songs together while conversing with in-room encrypted messaging and swipe-to-reply.
- **Resilient Instant Rejoin**: Backgrounding, incoming calls, or brief network switches don't kill the room—the app auto-reconnects with state reconciliation.

### 2. 💽 Tactile Vinyl Experience & Atmospheric Visual Design
- **Interactive Vinyl Player**: Realistic spinning record with microscopic vinyl groove textures, interactive tonearm mechanics, and tactile haptic feedback.
- **Dynamic Dual-Color Artwork Glow**: Album cards dynamically sample their top two dominant palette colors for rich, atmospheric ambient cards free from harsh default borders.
- **Floating Island Mini-Player**: Luminous dual-row island with scrolling text marquee and immediate thumb controls designed for single-hand use.
- **Real-Time Interactive Waveform**: Scrub audio with precision through an animated waveform progress bar.

### 3. 🌐 Web Ecosystem & Seamless Universal Deep Linking
- **Live Ecosystem Integration**: Fully connected with [sielo-music.vercel.app](https://sielo-music.vercel.app).
- **Universal Link Sharing**: Share tracks, albums, artists, or Listen Together rooms via web links that open directly in Sielo if installed, or gracefully preview on the web.
- **Automated In-App Update Engine**: Background update system checks for newer APK releases on the website and unobtrusively prompts users once every 24 hours with flexible choices (*Update Now* or *Remind Me Later*).

### 4. 🎚️ Dual-Engine Lossless Audio Pipeline
- **Hybrid Streaming Architecture**: Seamless orchestration between **YouTube Music InnerTube** and **JioSaavn** (lossless 320kbps CD-quality AAC).
- **Smart Stream Recovery**: Multi-client fallback (Android Mobile, VR, and Saavn CDN) that recovers degraded streams on the fly without interruption.
- **Intelligent Offline Chunk Cache**: Bounded, encrypted LRU cache system caching songs to prevent redundant network consumption.

### 5. 🎙️ Millisecond-Accurate Lyrics
- **Precision Word & Line Timestamps**: Integration with LRCLIB offering exact 1:1 millisecond timestamp synchronization.
- **Native Script Preservation**: Preserves original scripts across Devanagari, Gurmukhi, Hangul, Japanese, Roman, and Arabic characters.
- **Tap-to-Seek & Timing Calibration**: Jump playback straight to any lyric line with on-the-fly timing offset adjustments (`±0.5s`).

### 6. 🌌 Music Universe Stats & Circadian Flow
- **Planetary Listening Universe**: Celestial dashboard mapping personal listening habits and favorite artists into celestial bodies.
- **Circadian Flow Waveform**: Visual breakdown of your listening activity curves across morning, afternoon, evening, and night.
- **Taste Persona Radar**: Aesthetic dimensional breakdown measuring your musical habits across *Mood*, *Energy*, *Genre Diversity*, and *Consistency*.

---

## 🏛️ System Architecture

```text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                 PRESENTATION LAYER (UI)                                 │
│                                                                                         │
│  ┌────────────────────────┐      User Gestures       ┌───────────────────────────────┐  │
│  │   Jetpack Compose UI   │─────────────────────────►│     StateFlow ViewModels      │  │
│  │                        │  (Play, Pause, Seek,     │                               │  │
│  │  • Tactile Vinyl Player│   Query, AddToQueue)     │  • PlayerViewModel            │  │
│  │  • Dynamic Album Glow  │                          │  • HomeViewModel / SearchVM   │  │
│  │  • Synced Lyrics View  │◄─────────────────────────│  • ProfileViewModel           │  │
│  │  • Floating Mini-Player│     UI State Emission    │  • SongActionsViewModel       │  │
│  └───────────┬────────────┘   StateFlow<ScreenState> └───────────────┬───────────────┘  │
│              │                                                       │                  │
│              │ Room Action / Chat                                    │ Intent / Command │
│              │ (AES-256 Payload)                                     │ (PlayTrack,      │
│              ▼                                                       │  SeekTo, Skip)   │
│  ┌──────────────────────────────────────────┐                        │                  │
│  │    Listen Together Session (P2P Mesh)    │                        │                  │
│  │                                          │                        │                  │
│  │  [Room Host] ──(MQTT Broker Relay)──►    │                        │                  │
│  │  • Clock Drift Correction (sub-150ms)    │                        │                  │
│  │  • AES-256-GCM Encrypted Chat & Queue    │                        │                  │
│  └───────────────────┬──────────────────────┘                        │                  │
│                      │                                               │                  │
│                      │ Synchronized Playback Event                   │                  │
│                      └───────────────────────┬───────────────────────┘                  │
└──────────────────────────────────────────────┼──────────────────────────────────────────┘
                                               │
                                               ▼ MediaController Binder / Flow
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CORE AUDIO & PLAYBACK ENGINE (MEDIA3)                           │
│                                                                                         │
│  ┌────────────────────────────────────────┐       Tracks / MediaItems       ┌────────┐  │
│  │             PlayerManager              │────────────────────────────────►│ Media3 │  │
│  │                                        │                                 │Service │  │
│  │  • Dynamic Queue Management            │   Play / Pause / Seek Commands  │        │  │
│  │  • Smart Duplicate-Free Shuffle        │────────────────────────────────►│  (SES) │  │
│  │  • Autoplay Candidate Replenishment    │                                 └───┬────┘  │
│  └───────────────────┬────────────────────┘                                     │       │
│                      │                                                          │       │
│                      │ Track ID / Metadata Request                              │       │
│                      ▼                                                          ▼       │
│  ┌────────────────────────────────────────┐       Resolved Audio URL        ┌────────┐  │
│  │           StreamClientUtils            │────────────────────────────────►│  Exo-  │  │
│  │                                        │  (Chunk-Buffered Stream URI)    │ Player │  │
│  │  • Bitrate Selection (320k vs 160k)    │                                 └───┬────┘  │
│  │  • Fallback Stream Recovery Engine     │◄─────────────────┐                  │       │
│  └───────────────────┬────────────────────┘  Cached Chunks   │                  │ Audio │
│                      │                                       │                  │ Output│
│                      │ Local Cache Check                     │                  ▼       │
│                      ▼                                       │               [DAC /     │
│  ┌────────────────────────────────────────┐                  │               Headset]   │
│  │          OfflineCacheManager           │──────────────────┘                          │
│  │  LRU SimpleCache • Encrypted Storage   │                                             │
│  └────────────────────────────────────────┘                                             │
└──────────────────────────────────────────────┬──────────────────────────────────────────┘
                                               │
                                               │ Fetch Metadata & Stream URLs
                                               │ (HTTPS / TLS 1.3 Async)
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         DATA SOURCES & METADATA PIPELINE                                │
│                                                                                         │
│  ┌───────────────────────┐   ┌────────────────────────┐   ┌──────────────────────────┐  │
│  │   YouTube InnerTube   │   │   JioSaavn Resolvers   │   │     LRCLIB Provider      │  │
│  │                       │   │                        │   │                          │  │
│  │ • Client Video Stream │   │ • 320kbps CD AAC Audio │   │ • 1ms Synced Lyrics      │  │
│  │ • Type-Ahead Search   │   │ • DES Secret Key Decrypt│  │ • Language Normalization │  │
│  │ • Trending Song Feeds │   │ • High-Res Studio Art  │   │ • Acoustic Offset Trim   │  │
│  └───────────┬───────────┘   └───────────┬────────────┘   └────────────┬─────────────┘  │
│              │                           │                             │                │
│              │ Raw Metadata              │ Studio Metadata             │ Timestamped    │
│              │ Streams                   │ & Cover Art                 │ Lines          │
│              └───────────────┬───────────┴─────────────────────────────┤                │
│                              │                                         │                │
│                              ▼                                         ▼                │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │                      MusicBrainz & Cover Art Archive (CAA)                        │  │
│  │                                                                                   │  │
│  │  • Strict Release Group Deduplication (Studio Albums vs EPs vs Soundtracks)       │  │
│  │  • High-Resolution Original Front Cover Resolution (500px / 1200px)               │  │
│  │  • Clean Artist Alias Verification & False-Collaboration Suppression              │  │
│  │  └───────────────────────────────────────────┬───────────────────────────────────────┘  │
│                                              │                                          │
│                                              │ Validated Entities                       │
│                                              ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                Local Database (Room)                              │  │
│  │                                                                                   │  │
│  │  • TrackCacheDao (Local Playback Cache)    • ListeningHistoryDao (Stats & Vibe)   │  │
│  │  • ArtistMetadataDao (Verified Profiles)   • RoomSessionDao (Encrypted Rooms)     │  │
│  └───────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack & Dependencies

- **Language & Runtime**: Kotlin 2.0 (Coroutines, StateFlow, Structured Concurrency)
- **UI Framework**: Jetpack Compose + Material 3 + Custom Shaders
- **Audio & Media**: AndroidX Media3 1.5.1 (ExoPlayer, MediaSession)
- **Architecture**: Modular MVVM + Clean Architecture + Dagger Hilt
- **Network**: OkHttp 4, Kotlinx Serialization
- **Image Pipeline**: Coil 2.7 (with hardware-accelerated palette extraction)
- **Local Database**: Room 2.6 (KSP)
- **Typography**: Sora (Headings, Stats, Metrics) & Urbanist (Body, Metadata)

---

## 🚀 Building From Source

```bash
# Clone the repository
git clone https://github.com/VineetChudasama/Sielo.git
cd Sielo

# Build Debug APK
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for complete details.
