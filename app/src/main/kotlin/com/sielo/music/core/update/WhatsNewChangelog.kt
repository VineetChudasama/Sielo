package com.sielo.music.core.update

/**
 * Version-keyed changelog for the "What's New" feature.
 * Entries are listed newest-first. Each version maps to a list of user-facing
 * change strings (with emoji prefix for visual clarity).
 *
 * Add a new version entry here whenever the app version is bumped.
 */
object WhatsNewChangelog {

    /**
     * All changelog entries, keyed by version string.
     * Keep this map sorted newest-to-oldest so iteration order is correct.
     */
    val entries: Map<String, List<String>> = linkedMapOf(
        "11.5.4" to listOf(
            "💾 Dynamic Offline Cache Playlist: Dynamically generates and syncs the 'Downloaded Cache' playlist directly from your local vault, complete with live track counts and instant gapless replay",
            "⚡ Seamless Offline Playback: Songs you listen to are automatically cached in the background; the audio engine checks your local vault first for instant, zero-network, zero-data playback",
            "📥 Manual Offline Vault Control: Download any song for offline replay directly from the track action menu or remove individual cached items anytime",
            "🔄 Continuous Cloud Stats Sync: Added background interval sync (every 3 minutes) so your listening history, stream counts, and lifetime stats are continuously preserved without risk of data loss",
            "🛡️ Multi-Device & Account Switch Persistence: Bidirectional cloud synchronization guarantees stats and listening history never reset or get overwritten when logging in, out, or across devices",
            "🔥 Trending-Aware Search Ranking: Multi-tiered ranking integrating official YouTube Data API (India region prioritized), Last.fm API, and community listening stats to surface viral tracks at the top of search"
        ),
        "11.5.3" to listOf(
            "⚡ High-Speed Search Caching: Added instant in-memory search caching with 60s freshness to make repeat and backspace queries instantaneous",
            "🔍 Smart Fuzzy Search: Resilient spelling mistake tolerance for song titles and artists without returning fabricated results",
            "🎯 Textual Relevance Priority: Calibrated ranking engine guaranteeing exact title and artist matches always dominate over personal history",
            "🛡️ Search History Preservation: Full search queries (including prefixes like 'arijit' and 'arijit singh') are now preserved without premature pruning",
            "🌐 Isolated Search Providers: Network or parsing errors in song search or artist search no longer crash or suppress the other provider"
        ),
        "11.5.2" to listOf(
            "🔁 Seamless Continuous Loop: Fixed loop mode to repeat the current track indefinitely without advancing, with an active loop notice in the queue",
            "🎤 Hinglish Lyrics Focus: Dual-language synced lyrics now accurately highlight the romanized/Hinglish sung lines instead of literal English translations",
            "⏱️ 15% Song Play Threshold: Play counts and listening history now record precisely after listening to 15% of any song",
            "📊 Smart History-Based Queue Ranking: Dynamic queues prioritize tracks similar to your most frequently played artists and clusters while strictly preventing song repeats",
            "📋 Dynamic Queue & Up Next Titles: Clear naming distinguishes user-added 'Up next' tracks from the dynamically tuned 'Queue'"
        ),
        "11.5.1" to listOf(
            "🎯 Personalized Search Relevance: Search ranking now prioritizes tracks based on your listening history, most played artists, and favorite music styles and languages alongside overall popularity",
            "🛡️ Same-Day Duplicate Shield: Fixed an issue where played songs reappeared in autoplay; songs enjoyed today are strictly excluded from being re-recommended in queue on the same day",
            "🔒 Protected Play Next Queue: Shuffling the queue now strictly keeps all tracks you manually added to 'Play Next' in their exact position and order without scrambling them"
        ),
        "11.5.0" to listOf(
            "🔂 Single-Song Loop Mode: Added a tactile loop button to both the mini player and full player screen — repeats your current favorite track indefinitely until toggled off",
            "📋 Strict Album & Playlist Queue: Albums and playlists now keep their exact, pure track order without injecting random dynamic recommendations into your queue, with full shuffle support",
            "🎧 YouTube & YouTube Music Playlist Import: Enhanced playlist importer now seamlessly accepts links from both YouTube and YouTube Music with expanded format handling",
            "⏱️ Verified 20-Second Play Count: Plays and 'Keep listening' recents only register when a song has been enjoyed for at least 20 seconds",
            "✨ What's New Notification: Dialog now reliably highlights all latest features and enhancements on first launch after updating"
        ),
        "11.4.0" to listOf(
            "🎵 Flawless Mixtape & Underground Playback: Full multi-source search fallback for unreleased tracks and underground albums (e.g. Bella's discography)",
            "🎨 Authentic Album Art Architecture: Curated artwork resolution for releases with missing archive art (e.g. 'Home: The Album' & 'One Hit Wonder')",
            "✨ Pure Playlist Import: Spotify imports now strictly fetch real, authentic songs — zero covers, instrumentals, or karaoke",
            "🚪 Instant Room Exit & Cleanup: Leaving a Listen Together room is now immediate and destroys the room once the last user leaves",
            "📊 Room Mini-Player Upgrades: Added top progress bar line and buffering animation indicator on play/pause button",
            "🖼️ Verified Artist Portraits: High-accuracy profile matching ensures real artist photos without showing wrong individuals",
            "🌐 Real-Time Download Verification: Website download button confirms actual device download with immediate feedback",
            "☁️ Cloud Backup & Smooth Onboarding: Firebase library sync across devices, with returning users seamlessly skipping onboarding"
        ),
        "11.3.6" to listOf(
            "🎵 Fixed underground & mixtape song playback (e.g. Bella's albums) that previously buffered without playing",
            "🎨 Fixed album artwork for Bella's 'Home: The Album' and 'One Hit Wonder'",
            "⚡ Universal multi-tier stream resolution waterfall for all catalog IDs"
        ),
        "11.3.5" to listOf(
            "🌐 Website download button now detects real download completion on device",
            "✨ Playlist import always fetches original songs — no karaoke or covers",
            "🎨 Fixed wrong artist profile photos (e.g. Bella) being shown",
            "🔍 Song & artist matching is now strictly accurate during playlist import"
        ),
        "11.3.4" to listOf(
            "✨ Playlist import now imports original songs — no more karaoke or covers",
            "🎨 Fixed Bella artist profile photo showing the wrong person",
            "🎵 Song & artist matching made strictly accurate for playlist imports"
        ),
        "11.3.3" to listOf(
            "🚪 Listen Together: Leave room instantly with no delay and auto-destroy on last leave",
            "📊 Room mini-player now shows a live song progress bar on top",
            "⏳ Buffering indicator shown in room play/pause button while loading"
        ),
        "11.3.2" to listOf(
            "☁️ Cloud Backup: Your library now syncs across devices via Firebase",
            "🔄 Returning users skip onboarding — only shown to new accounts",
            "🔒 Secure login with Firebase Auth (Google, Email)"
        )
    )

    /**
     * Returns all changelog versions and their entries that are *newer* than
     * [fromVersion] and up to (and including) [toVersion], sorted newest-first.
     *
     * If [fromVersion] is blank/null (first install), returns only [toVersion] entry.
     */
    fun getChangesSince(
        fromVersion: String?,
        toVersion: String
    ): List<Pair<String, List<String>>> {
        val keys = entries.keys.toList() // already newest-first
        val toIdx = keys.indexOf(toVersion)
        if (toIdx == -1) {
            return entries.entries.firstOrNull()?.let { listOf(it.key to it.value) } ?: emptyList()
        }

        if (fromVersion == toVersion) {
            return emptyList()
        }

        if (fromVersion.isNullOrBlank()) {
            // First time seeing What's New: return current version's entry
            return listOf(toVersion to (entries[toVersion] ?: emptyList()))
        }

        val fromIdx = keys.indexOf(fromVersion)
        val endIdx = if (fromIdx == -1) {
            // fromVersion is older than any tracked entry (e.g. 11.3.1, 11.2.0):
            // show all entries from toVersion down to the oldest tracked version
            keys.size
        } else {
            fromIdx
        }

        if (toIdx < endIdx) {
            return keys
                .subList(toIdx, endIdx)
                .mapNotNull { ver -> entries[ver]?.let { ver to it } }
        }

        return listOf(toVersion to (entries[toVersion] ?: emptyList()))
    }
}
