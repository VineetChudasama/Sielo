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
