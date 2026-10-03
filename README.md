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
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" /></a>
</p>

---

## ✨ Features

### 🎧 High-Fidelity & Lossless Streaming
- **Dual Stream Engine**: High-fidelity audio playback combining **YouTube Music InnerTube** and **JioSaavn** (lossless 320kbps CD-quality AAC).
- **Background Media3 Playback**: Rock-solid background audio playback, lock-screen controls, and notification media session powered by AndroidX Media3 `MediaSessionService`.
- **Intelligent Stream Fallback**: Instantaneous format resolution with minimal buffering, network resilience, and seamless stream recovery.

### 👥 Listen Together (Private Social Sessions)
- **End-to-End Encrypted Rooms**: AES-256-GCM authenticated encryption guarantees 100% private peer-to-peer listening sessions where servers never see plaintext messages, usernames, or song metadata.
- **Sub-150ms Audio Synchronization**: Real-time playback synchronization (Play, Pause, Seek, and Skip) with continuous latency drift correction.
- **Collaborative Queue & Live Chat**: In-room live music search allowing participants to co-curate the queue alongside fluid, encrypted in-room chat with swipe-to-reply.
- **Resilient Rejoin**: Host backgrounding or network hiccups won't kill the room—smart reconnect ensures seamless rejoin with zero playback interruption.

### 🎙️ Synchronized Real-Time Lyrics
- **Millisecond-Accurate Lyrics**: Integrated with LRCLIB for 1:1 millisecond timestamp synchronization.
- **Original Language Preservation**: Native script rendering across Devanagari, Gurmukhi, Roman, Hangul, Japanese, and more.
- **Interactive Seeking**: Tap any lyric line to jump directly to that timestamp in the audio.
- **Live Sync Fine-Tuning**: On-the-fly timing offset adjustments (`±0.5s`) and automatic acoustic intro calibration.

### 💽 Large Visual Album Cards & Smart Discography
- **Top 2 Color Gradient Cards**: Dynamic visual album showcase featuring an edge-to-edge left-to-right gradient extracted directly from each album's artwork, with soft atmospheric glow and zero default color interference.
- **Clean Discography Structure**: Clear separation between Studio Albums, Singles & EPs, and Soundtracks & Features, backed by MusicBrainz, Cover Art Archive, and authentic studio metadata.
- **Rich Release Metadata**: Automatic year, track count, total duration, and subtle release type pills (`[ ALBUM ]`, `[ SINGLE ]`, `[ EP ]`, `[ SOUNDTRACK ]`).

### 🎨 Tactile Haptics & Modern Aesthetics
- **Tactile Vinyl Player**: Interactive spinning vinyl record with authentic groove micro-textures, stylus bulb illumination, and responsive haptic controls.
- **Floating Island Mini-Player**: Luminous dual-row island featuring artist/title marquee and ergonomic bottom playback controls with instant mute.
- **Animated Waveform Visualizer**: Real-time interactive scrubbable waveform progress bar.
- **Refined Typography**: Modern type hierarchy utilizing **Sora** (Headings, Stats, Lyrics) and **Urbanist** (Track titles, Metadata, System UI).

### 🔍 Verified Artist Profiles & Smart Search
- **Authentic Artist Curation**: Strict avatar ownership and deduplication logic that filters out spoofed profiles, duplicate collaboration aliases, and compilation clutter.
- **Deep Artist Catalog**: High-resolution artist backdrops, curated top tracks, complete release discography, and similar artist influence graphs.
- **Type-Ahead Instant Search**: Fast, responsive search suggestions with categorized browse tiles and recent query history.

### 🌌 Music Universe Stats & Listening Insights
- **Atmospheric Celestial Dashboard**: Ambient planetary visualization tracking your personal listening patterns over time.
- **Circadian Flow Wave Graph**: Interactive landscape wave showing listening activity across morning, afternoon, evening, and night day-parts.
- **Listening Vibe Persona**: Dynamic aesthetic dimensions analysis (`MOOD | ENERGY | GENRE MIX | CONSISTENCY`) and top artist/track spotlights.

### 🤖 Adaptive Recommendations & Taste Engine
- **Taste Onboarding**: Curated onboarding grid selecting favorite artists and genres to seed custom feeds.
- **Dynamic Recommendations**: Personalized "Songs for you" and "Since you like..." recommendation modules that refresh dynamically.
- **Session-Aware Autoplay**: Continuous background queue replenishment powered by Last.fm similarity clustering and history-aware deduplication.

---

## 🏛️ Architecture & Modular Structure

Sielo follows **Clean Architecture** principles and is organized into modular Gradle layers:

```text
Sielo
├── app/                  # Main Android Application & Compose UI
│   ├── ui/               # Screens, Themes & Tactile Components
│   └── viewmodel/        # StateFlow ViewModels
├── core-audio/           # Media3 ExoPlayer & Background Service
├── core-recommendations/ # Autoplay Engine & Similar Artist Scoring
├── core-lyrics/          # LRCLIB API & Synced Lyrics Engine
├── core-network/         # InnerTube & JioSaavn Lossless Resolvers
└── core-database/        # Room DB & Listening Analytics
```

### Tech Stack
- **Language**: Kotlin 2.0 (Coroutines, StateFlow, Flow)
- **UI Framework**: Jetpack Compose + Material 3
- **Dependency Injection**: Dagger Hilt
- **Audio Engine**: AndroidX Media3 (ExoPlayer, MediaSession)
- **Networking**: OkHttp 4, Kotlinx Serialization
- **Image Loading**: Coil 2.7 (with hardware-accelerated palette generation)
- **Local Database**: Room (KSP)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or higher
- Android SDK 35 (minSdk 26)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/VineetChudasama/Sielo.git
cd Sielo

# Build Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install to Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
