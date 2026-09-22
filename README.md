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

### 🎧 High-Fidelity Audio Streaming
- **Dual Stream Engine**: High-quality audio streaming powered by **YouTube Music InnerTube** and **JioSaavn** (lossless DES-decrypted 320kbps AAC).
- **Background Media3 Playback**: Rock-solid background audio playback and lock-screen controls via AndroidX Media3 `MediaSessionService`.
- **Intelligent Fallback**: Automatic stream resolution and format selection for instant playback with minimal buffering.

### 🎙️ Synchronized Lyrics
- **Real-Time Synchronized Lyrics**: Powered by LRCLIB with 1:1 millisecond timestamp synchronization.
- **Original Language Lyrics**: Preserves authentic lyrics in their native script (Devanagari, Gurmukhi, Hangul, Japanese, Roman, etc.).
- **Live Sync Fine-Tuning**: Built-in real-time offset adjustment controls (`−0.5s` / `+0.5s`) for on-the-fly timing adjustments.
- **Acoustic Calibration**: Automatic intro offset calibration for acoustic cuts and live edits.
- **Interactive Seeking**: Tap any lyric line to jump directly to that timestamp in the audio.

### 🌌 Personal Music Universe Stats Redesign (v5.0.0)
- **Atmospheric Celestial Hero**: Central glowing planetary sphere with twilight aura, contemplative ridge silhouette, tilted orbital rings with pearl nodes, and attached reactive metrics (*This Month*, *Tracks*, *Artists*, *Plays*).
- **Circadian Flow Wave Graph**: Flowing golden landscape wave graph (`Canvas`) showing activity across 4 interactive circadian day-parts (*Morning*, *Afternoon*, *Evening*, *Night*) with rising sun peak and dynamic duration/percentage guide pins.
- **Dual Spotlight Cards & Leaderboards**: Balanced side-by-side cards for *Top Artists* (overlapping circular portraits) and *Top Tracks* (stacked fanned-out album artwork) with direct 1-tap playback.
- **Listening Vibe Persona**: Orbital glowing star graphic, discovery prompts, and aesthetic personality dimensions bar (`MOOD | ENERGY | GENRE MIX | CONSISTENCY`).

### 🎬 Cinematic Brand Launch Reveal
- **Macondo Brand Emergence**: Elegant app launch splash featuring the custom "Sielo" logo in **Macondo** typography with a warm ambient radial glow.
- **Dynamic Glyph Targeting**: Uses Compose layout measurement (`onTextLayout`) to dynamically calculate the center of the letter **"e"** as the focal zoom pivot.
- **Deep Zoom & Dissolve**: Gracefully dives 4.2× into the center of the name while dissolving the obsidian background to seamlessly reveal the home screen.

### 🔀 Hardware-Accelerated Animated Shuffle
- **Custom 36-Frame Spritesheet**: High-performance lossless WebP spritesheet rendered directly via Jetpack Compose `Canvas.drawImage` with sub-rectangle clipping.
- **Dynamic Color Tinting**: Supports real-time theme tinting across rest states and interactive accent flares (`AccentCoral`, `PaletteSand`, `PaletteSlateBlue`).
- **Repeatable & Duplicate-Free**: Re-randomizes upcoming tracks continuously on every tap with zero duplicate tracks of the active song and no sticky toggle states.

### 🤖 Intelligent Autoplay & Recommendations (`core-recommendations`)
- **Dual Candidate Pool**: Combines real-time **Last.fm** similarity data with curated artist clusters for seamless infinite music sessions.
- **Session-Aware Scoring**: Continuous background queue replenishment based on recent listening history and deduplication.

### 🎨 Modern UI & Tactile Design
- **Tactile Vinyl Player**: Interactive rotating vinyl disc with realistic vinyl groove textures and album artwork.
- **Balanced Player Screen Controls**: Ergonomic 5-button bottom tactile row (`[Lyrics]` — `[Previous]` — `[Play/Pause]` — `[Next]` — `[Shuffle]`).
- **Floating Island Mini-Player**: Dual-row design featuring full-width artist/title marquee on top and aligned playback controls with Mute on the bottom row.
- **Animated Waveform Progress Bar**: Interactive, scrubbable animated waveform visualizer.
- **Custom Typography**: Tailored static type system featuring **Macondo** (Brand Identity), **Sora** (Headings, Stats, Active Lyrics), and **Urbanist** (Song titles, Metadata, UI elements).
- **Infinite Genre Marquee**: Smooth infinite looping genre carousels for effortless music discovery.

### 🔐 Dual Authentication & Adaptive Taste Engine (v4.0.4)
- **Google OAuth, Native Auth & Guest Mode**: Seamless Sign in with Google (OAuth) alongside email & password login/registration, plus a dedicated **"Continue as Guest"** option for instant access.
- **Interactive Taste Onboarding**: Curated selection grid of 30+ trending artists worldwide (including top Indian artists like Arijit Singh, Diljit Dosanjh, AP Dhillon, Prateek Kuhad, Anuv Jain, and AR Rahman) requiring at least 5 selections, followed by favorite genre selection from 10 trending music genres.
- **Personalized "Songs for you"**: Dedicated home feed section dynamically curating top songs based directly on chosen taste artists.
- **Dynamic "Since you like..." Recommendations**: Smart section placed above Similar Artists that dynamically resamples on launch and refresh.
- **Adaptive Queue Autoplay**: Seeds upcoming tracks from taste profiles and learns preferences in real-time as songs are played.
- **Clean Account State & Vault**: "Keep Listening" hides automatically for new profiles until playback begins; session data cleanly wipes on logout.

### 👥 Listen Together (v4.0.4)
- **Private End-to-End Encrypted Rooms**: AES-256-GCM authenticated encryption ensures 100% private peer-to-peer listening sessions where servers never see plaintext messages, usernames, or song metadata.
- **Shorter 6-Character Room IDs**: Clean, concise alphanumeric identifiers (e.g. `K4M8X2`) for quick manual entry.
- **Branded Center-Logo QR Codes**: Generates high-res QR codes with the circular Sielo badge embedded at the center with Error Correction Level H (30% tolerance) for instant camera scanning.
- **Expandable Members Dropdown**: Interactive dropdown showing all active room participants, host badges, and the number of songs each member added to the queue.
- **Swipe-Left to Reply**: Modern chat interaction with tactile swipe-to-reply gesture, quote previews, and inline quoted replies.
- **Smooth Queue ⇄ Chat Transitions**: Fluid animated slide transitions when switching between collaborative queue and live chat.
- **Resilient Disconnects & Direct Rejoin Popup**: Abrupt disconnects or host backgrounding no longer delete rooms or boot listeners. Opening Sielo after an abrupt disconnect prompts the user with a direct **"Rejoin Room"** popup dialog.
- **Sub-150ms Synchronized Audio**: Real-time playback synchronization (Play, Pause, Seek, and Skip) with latency drift correction.
- **Collaborative Queue & Song Search**: In-room live music search allowing any participant to queue up tracks with "Added by [User]" badges.

### 🔍 Search & Verified Artist Profiles
- **Fuzzy Deduplication**: Intelligent filtering that eliminates fake typo profiles, collaboration pseudo-names (`feat.`, `&`, `vs`), and incomplete single-word duplicates.
- **Strict Avatar Ownership**: Guaranteed unique profile pictures for all official artists without cross-artist image pollution.
- **Instant Search**: Type-ahead search with immediate results, search dropdown suggestions, and recent search history.
- **Curated Browse Categories**: Dynamic category tiles with themed backgrounds and genre recommendations.

### 📊 Listening History & Insights
- **Local Listening Events**: Stored in a local Room database tracking playback sessions, listening time, and repeated tracks.
- **Stats Overview**: Clean charts and summary metrics of top artists and songs.

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
- **Language**: Kotlin (Coroutines, StateFlow, Flow)
- **UI Framework**: Jetpack Compose + Material 3
- **Dependency Injection**: Dagger Hilt
- **Audio Engine**: AndroidX Media3 (ExoPlayer, MediaSession)
- **Networking**: OkHttp 4, Kotlinx Serialization
- **Image Loading**: Coil 3
- **Local Database**: Room (KSP)

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or higher
- Android SDK 34+ (minSdk 26)

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
