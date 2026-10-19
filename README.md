<p align="center">
  <img src="docs/sielo_brand_header.png" alt="Sielo" width="560" />
</p>

<p align="center">
  <strong>A tactile, lossless music streaming app crafted with Jetpack Compose & AndroidX Media3</strong>
</p>

<p align="center">
  🌐 <strong>Official Website</strong>: <a href="https://www.sielo.site"><strong>www.sielo.site</strong></a>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white" alt="Android" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/media/media3"><img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer-FF6F00.svg" alt="Media3" /></a>
  <a href="https://github.com/VineetChudasama/Sielo/releases"><img src="https://img.shields.io/badge/Version-v11.5.4-E63946.svg" alt="Version 11.5.4" /></a>
  <a href="https://www.sielo.site"><img src="https://img.shields.io/badge/Website-www.sielo.site-0D1B2A.svg?logo=googlechrome&logoColor=white" alt="Website" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" /></a>
</p>

---

## 🌟 Distinctive & Unique Highlights

What sets **Sielo** apart from typical streaming clients:

### 1. 👥 End-to-End Encrypted "Listen Together" (Sub-150ms Sync)
- **Zero-Knowledge P2P Sessions**: Rooms are fortified with **AES-256-GCM** client-side encryption. Server and broker relays never see plain text room messages, usernames, or song metadata.
- **Clock Drift Compensation**: Micro-second continuous drift estimation delivers tight sub-150ms audio synchronization across peer devices.
- **In-Room Co-Queueing & Live Chat**: Seamlessly discover and queue songs together while conversing with in-room encrypted messaging and swipe-to-reply.
- **Resilient Instant Rejoin & Auto-Cleanup**: Leaving a room instantly tears down connections, and rooms auto-destroy once the last user leaves.

### 2. 💽 Tactile Vinyl Experience & Atmospheric Visual Design
- **Interactive Vinyl Player**: Realistic spinning record with microscopic vinyl groove textures, interactive tonearm mechanics, and tactile haptic feedback.
- **Dynamic Dual-Color Artwork Glow**: Album cards dynamically sample their top two dominant palette colors for rich, atmospheric ambient cards free from harsh default borders.
- **Floating Island Mini-Player**: Luminous dual-row island with scrolling text marquee, live top progress bar, and immediate thumb controls designed for single-hand use.
- **Real-Time Interactive Waveform**: Scrub audio with precision through an animated waveform progress bar.

### 3. 🧠 Smart Endless Autoplay & Recommendation Engine
- **3-Tier Candidate Balancing**: Sophisticated pool allocation balancing Familiar favorites (Pool A ~60%), Fresh discoveries (Pool B ~25%), and Moderate rotation gems (Pool C ~15%).
- **Same-Day Duplicate Shield**: Strict elimination ensuring songs enjoyed today are never re-recommended in queue on the same day.
- **Clean Studio Track Enforcement**: Intelligent acoustic pattern filtering that strips out low-quality covers, karaoke, sped up, slowed, and instrumental noise.
- **Artist Spacing & Seamless Preloading**: Enforces spacing between tracks from the same artist and preloads upcoming audio into ExoPlayer for instantaneous (<1s) gapless transitions.

### 4. 📋 Advanced Queue Control & Protected "Play Next"
- **Protected "Play Next" Queue**: Manually queued tracks remain strictly fixed in their exact order and position immediately following the current song—shuffling the queue never alters your prioritized songs.
- **Strict Album & Playlist Modes**: When enjoying an album or imported playlist, Sielo preserves its authentic track list without injecting unexpected dynamic recommendations, while retaining optional shuffle.
- **Single-Song Loop Mode**: Dedicated loop button on both the mini-player island and the full vinyl player to loop any favorite track indefinitely.

### 5. 🔍 Personalized Relevance Search
- **Taste-Weighted Search Ranking**: Results combine exact title/artist text matching with the user's personal listening history, top played artists, favorited tracks, and preferred music languages and vibes.
- **Multi-Script & Typo Handling**: Phonetic query normalization (e.g. Hindi, Punjabi, English token transliterations) and verified artist portraits.

### 6. 🎧 Universal Playlist Importer
- **Multi-Platform Support**: Effortlessly import public and unlisted playlists from **Spotify**, **YouTube**, and **YouTube Music**.
- **Authentic Track Matching**: High-precision track validator resolves original studio master recordings, eliminating wrong remixes or acoustic live versions.

### 7. 🎚️ Dual-Engine Lossless Audio Pipeline
- **Hybrid Streaming Architecture**: Seamless orchestration between **YouTube Music InnerTube** and **JioSaavn** (lossless 320kbps CD-quality AAC).
- **Smart Stream Recovery**: Multi-client fallback (Android Mobile, VR, and Saavn CDN) that recovers degraded streams on the fly without interruption.
- **Intelligent Offline Chunk Cache**: Bounded, encrypted LRU cache system caching songs to prevent redundant network consumption.
- **Verified 15% Song Play Threshold**: Listening history and analytics only register a play after 15% of track duration has elapsed, preventing accidental skips from distorting recommendations.

### 8. 🎙️ Millisecond-Accurate Lyrics
- **Precision Word & Line Timestamps**: Integration with LRCLIB offering exact 1:1 millisecond timestamp synchronization.
- **Native Script Preservation**: Preserves original scripts across Devanagari, Gurmukhi, Hangul, Japanese, Roman, and Arabic characters.
- **Tap-to-Seek & Timing Calibration**: Jump playback straight to any lyric line with on-the-fly timing offset adjustments (`±0.5s`).

### 9. 🌌 Music Universe Stats & Circadian Flow
- **Planetary Listening Universe**: Celestial dashboard mapping personal listening habits and favorite artists into celestial bodies.
- **Circadian Flow Waveform**: Visual breakdown of your listening activity curves across morning, afternoon, evening, and night.
- **Taste Persona Radar**: Aesthetic dimensional breakdown measuring your musical habits across *Mood*, *Energy*, *Genre Diversity*, and *Consistency*.

### 10. ☁️ Account Sync & "What's New" Dialog
- **Cross-Device Cloud Backup**: Secure login with Firebase Auth (Google Sign-In, Email/Password) and automatic cloud backup for your library, favorites, and playlists.
- **What's New Changelog**: Highlights latest features and enhancements on first launch after each version update.

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
│  │  • Floating Mini-Player│     UI State Emission    │  • UserPlaylistsViewModel     │  │
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
│  │  • Protected Play Next Queue           │   Play / Pause / Seek Commands  │        │  │
│  │  • Smart Duplicate-Free Shuffle        │────────────────────────────────►│  (SES) │  │
│  │  • Single-Song Loop Mode               │                                 └───┬────┘  │
│  └───────────────────┬────────────────────┘                                     │       │
│                      │                                                          │       │
│                      │ Autoplay & Queue Replenishment                           │       │
│                      ▼                                                          ▼       │
│  ┌────────────────────────────────────────┐       Resolved Audio URL        ┌────────┐  │
│  │         AutoplayQueueEngine            │────────────────────────────────►│  Exo-  │  │
│  │                                        │  (Chunk-Buffered Stream URI)    │ Player │  │
│  │  • 3-Tier Candidate Pools (A/B/C)      │                                 └───┬────┘  │
│  │  • Same-Day Duplicate Shield           │◄─────────────────┐                  │       │
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
│  │              MusicBrainz, Cover Art Archive & Playlist Importer                   │  │
│  │                                                                                   │  │
│  │  • Strict Release Group Deduplication (Studio Albums vs EPs vs Soundtracks)       │  │
│  │  • Universal Importer (Spotify, YouTube, YouTube Music)                           │  │
│  │  • Clean Artist Alias Verification & False-Collaboration Suppression              │  │
│  └───────────────────────────────────────────┬───────────────────────────────────────┘  │
│                                              │                                          │
│                                              │ Validated Entities                       │
│                                              ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                Local Database (Room)                              │  │
│  │                                                                                   │  │
│  │  • ListeningHistoryDao (20s Play Stats)     • RecommendationHistoryDao (Shield)   │  │
│  │  • FavoriteTrackDao (Favorites)             • SearchHistoryDao & SearchPlayDao    │  │
│  │  • UserPlaylistsDao (Custom Playlists)      • RoomSessionDao (Encrypted Rooms)    │  │
│  └───────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Project Structure

```
Sielo/
├── app/                      # Main Android application module (UI, Navigation, ViewModels)
│   └── src/main/kotlin/com/sielo/music/
│       ├── core/auth/        # Firebase Auth & User Profile management
│       ├── core/sync/        # CloudSyncManager for library backup
│       ├── core/update/      # WhatsNewChangelog & in-app update checks
│       ├── ui/               # Jetpack Compose screens, components, theme & shaders
│       └── viewmodel/        # MVVM ViewModels (Home, Search, Player, Playlists, etc.)
├── core-audio/               # Audio playback engine (ExoPlayer, PlayerManager, Service)
├── core-recommendations/     # Autoplay queue engine, Candidate pools, Song classifier
├── core-network/             # InnerTube, JioSaavn, MusicBrainz metadata resolvers
├── core-playlist-import/     # Universal Spotify, YouTube & YouTube Music importers
├── core-lyrics/              # LRCLIB synchronized lyrics provider
└── core-database/            # Room Database, entities, and DAOs
```

---

## 🛠️ Tech Stack & Dependencies

- **Language & Runtime**: Kotlin 2.0 (Coroutines, StateFlow, Structured Concurrency)
- **UI Framework**: Jetpack Compose + Material 3 + Custom Shaders
- **Audio & Media**: AndroidX Media3 1.5.1 (ExoPlayer, MediaSession)
- **Architecture**: Multi-Module Clean Architecture + Dagger Hilt
- **Network**: OkHttp 4, Kotlinx Serialization
- **Image Pipeline**: Coil 2.7 (with hardware-accelerated palette extraction)
- **Local Database**: Room 2.6 (KSP)
- **Cloud & Auth**: Firebase Auth, Cloud Firestore
- **Real-Time P2P**: Eclipse Paho MQTT with AES-256-GCM encryption
- **Typography**: Sora (Headings, Stats, Metrics) & Urbanist (Body, Metadata)

---

## 🚀 Building From Source

```bash
# Clone the repository
git clone https://github.com/VineetChudasama/Sielo.git
cd Sielo

# Build Release APK
./gradlew :app:assembleRelease
```

The APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for complete details.
