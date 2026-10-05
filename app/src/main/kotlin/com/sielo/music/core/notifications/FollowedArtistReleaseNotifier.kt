package com.sielo.music.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sielo.music.MainActivity
import com.sielo.music.R
import com.sielo.music.core.auth.UserManager
import com.sielo.music.core.network.innertube.InnerTubeClient
import com.sielo.music.core.network.models.ArtistDetails
import com.sielo.music.core.network.models.SieloTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowedArtistReleaseNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val innerTubeClient: InnerTubeClient,
    private val userManager: dagger.Lazy<UserManager>
) {
    companion object {
        const val CHANNEL_ID = "sielo_artist_releases"
        private const val PREFS_NAME = "sielo_artist_releases_cache"
        private const val TAG = "ArtistReleaseNotifier"
        const val EXTRA_TRACK_ID = "com.sielo.music.EXTRA_TRACK_ID"
        const val EXTRA_TRACK_TITLE = "com.sielo.music.EXTRA_TRACK_TITLE"
        const val EXTRA_TRACK_ARTIST = "com.sielo.music.EXTRA_TRACK_ARTIST"
        const val EXTRA_TRACK_THUMB = "com.sielo.music.EXTRA_TRACK_THUMB"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "New Releases"
            val descriptionText = "Notifications when followed artists release or upload new music"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("(?i)\\s*\\((?:official\\s*(?:video|audio|music\\s*video)?|feat\\.?|ft\\.?|prod\\.?|remix|acoustic|live|version|edit|visualizer).*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[(?:official\\s*(?:video|audio|music\\s*video)?|feat\\.?|ft\\.?|prod\\.?|remix|acoustic|live|version|edit|visualizer).*?\\]"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Called when a user follows an artist.
     * Records ALL existing songs and titles as known baseline, so old songs are never notified.
     */
    suspend fun onArtistFollowed(artistName: String) = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return@withContext
        val artistIdKey = "known_releases_" + cleanArtist.lowercase()
        val artistTitleKey = "known_titles_" + cleanArtist.lowercase()

        val allKnownTracks = fetchArtistBaselineTracks(cleanArtist)
        val idSet = allKnownTracks.map { it.id }.toSet()
        val titleSet = allKnownTracks.map { normalizeTitle(it.title) }.filter { it.isNotBlank() }.toSet()

        val existingIds = prefs.getStringSet(artistIdKey, emptySet()) ?: emptySet()
        val existingTitles = prefs.getStringSet(artistTitleKey, emptySet()) ?: emptySet()

        prefs.edit()
            .putStringSet(artistIdKey, existingIds + idSet)
            .putStringSet(artistTitleKey, existingTitles + titleSet)
            .apply()

        Log.d(TAG, "Recorded initial baseline for followed artist '$cleanArtist' (${idSet.size} tracks, ${titleSet.size} titles)")
    }

    suspend fun checkForNewReleases(followedArtists: List<String>) = withContext(Dispatchers.IO) {
        if (followedArtists.isEmpty()) return@withContext
        val accountCreatedTime = runCatching { userManager.get().getAccountCreationTime() }.getOrDefault(System.currentTimeMillis())
        val cal = Calendar.getInstance().apply { timeInMillis = accountCreatedTime }
        val accountCreatedYear = cal.get(Calendar.YEAR)

        Log.d(TAG, "Checking new releases for ${followedArtists.size} followed artists (account created at $accountCreatedTime, year $accountCreatedYear)...")

        for (artist in followedArtists) {
            val cleanArtist = artist.trim()
            if (cleanArtist.isBlank()) continue
            try {
                checkArtistReleases(cleanArtist, accountCreatedYear)
            } catch (e: Exception) {
                Log.w(TAG, "Error checking releases for $cleanArtist: ${e.message}")
            }
        }
    }

    private suspend fun checkArtistReleases(artistName: String, accountCreatedYear: Int) {
        val artistIdKey = "known_releases_" + artistName.lowercase()
        val artistTitleKey = "known_titles_" + artistName.lowercase()

        val knownIds = prefs.getStringSet(artistIdKey, null)?.toMutableSet()
        val knownTitles = prefs.getStringSet(artistTitleKey, null)?.toMutableSet()

        val details = innerTubeClient.getArtistDetails(artistName) ?: return

        // 1. If no baseline exists, establish baseline now without notifying
        if (knownIds == null || knownTitles == null) {
            val baselineTracks = extractAllTracksFromDetails(details)
            val idSet = baselineTracks.map { it.id }.toSet()
            val titleSet = baselineTracks.map { normalizeTitle(it.title) }.filter { it.isNotBlank() }.toSet()

            prefs.edit()
                .putStringSet(artistIdKey, idSet)
                .putStringSet(artistTitleKey, titleSet)
                .apply()
            Log.d(TAG, "Baseline established for $artistName (${idSet.size} tracks, ${titleSet.size} titles)")
            return
        }

        // 2. Inspect ONLY latestAlbum and singles released in or after the user's account creation year
        val candidateReleases = mutableListOf<SieloTrack>()

        // Check singles
        for (single in details.singles) {
            val sYear = single.year?.toIntOrNull() ?: accountCreatedYear
            // Must not be an older release prior to user account creation
            if (sYear < accountCreatedYear) continue

            val singleTitleNorm = normalizeTitle(single.title)
            if (singleTitleNorm.isNotBlank() && knownTitles.contains(singleTitleNorm)) continue

            if (single.tracks.isNotEmpty()) {
                for (track in single.tracks) {
                    val trackTitleNorm = normalizeTitle(track.title)
                    if (track.id !in knownIds && trackTitleNorm !in knownTitles) {
                        candidateReleases.add(track)
                    }
                }
            } else if (single.id !in knownIds && singleTitleNorm !in knownTitles) {
                candidateReleases.add(
                    SieloTrack(
                        id = single.id,
                        title = single.title,
                        artist = single.artist.ifBlank { artistName },
                        album = single.title,
                        thumbnailUrl = single.thumbnailUrl
                    )
                )
            }
        }

        // Check latest album
        val latest = details.latestAlbum
        if (latest != null) {
            val aYear = latest.year?.toIntOrNull() ?: accountCreatedYear
            if (aYear >= accountCreatedYear) {
                for (track in latest.tracks) {
                    val trackTitleNorm = normalizeTitle(track.title)
                    if (track.id !in knownIds && trackTitleNorm !in knownTitles) {
                        candidateReleases.add(track)
                    }
                }
            }
        }

        // De-duplicate candidate releases
        val genuineNew = candidateReleases
            .distinctBy { normalizeTitle(it.title) }
            .filter { it.id !in knownIds && normalizeTitle(it.title) !in knownTitles }

        if (genuineNew.isNotEmpty()) {
            Log.i(TAG, "Found ${genuineNew.size} genuine new releases for $artistName released in/after $accountCreatedYear: ${genuineNew.map { it.title }}")
            for (newTrack in genuineNew.take(3)) {
                postNotification(artistName, newTrack)
            }

            val newIds = genuineNew.map { it.id }.toSet()
            val newTitles = genuineNew.map { normalizeTitle(it.title) }.filter { it.isNotBlank() }.toSet()

            prefs.edit()
                .putStringSet(artistIdKey, knownIds + newIds)
                .putStringSet(artistTitleKey, knownTitles + newTitles)
                .apply()
        }
    }

    private suspend fun fetchArtistBaselineTracks(artistName: String): List<SieloTrack> {
        val tracks = mutableListOf<SieloTrack>()
        try {
            val details = innerTubeClient.getArtistDetails(artistName)
            if (details != null) {
                tracks.addAll(extractAllTracksFromDetails(details))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching baseline tracks for $artistName: ${e.message}")
        }
        return tracks
    }

    private fun extractAllTracksFromDetails(details: ArtistDetails): List<SieloTrack> {
        val tracks = mutableListOf<SieloTrack>()
        tracks.addAll(details.topSongs)
        details.latestAlbum?.tracks?.let { tracks.addAll(it) }
        details.singles.forEach { single ->
            tracks.addAll(single.tracks)
            if (single.tracks.isEmpty() && single.id.isNotBlank()) {
                tracks.add(
                    SieloTrack(
                        id = single.id,
                        title = single.title,
                        artist = single.artist.ifBlank { details.name },
                        album = single.title,
                        thumbnailUrl = single.thumbnailUrl
                    )
                )
            }
        }
        details.pastAlbums.forEach { album ->
            tracks.addAll(album.tracks)
            if (album.tracks.isEmpty() && album.id.isNotBlank()) {
                tracks.add(
                    SieloTrack(
                        id = album.id,
                        title = album.title,
                        artist = album.artist.ifBlank { details.name },
                        album = album.title,
                        thumbnailUrl = album.thumbnailUrl
                    )
                )
            }
        }
        details.originalAlbums.forEach { tracks.addAll(it.tracks) }
        details.featuredAlbums.forEach { tracks.addAll(it.tracks) }
        return tracks.distinctBy { it.id }
    }

    private fun postNotification(artistName: String, track: SieloTrack) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TRACK_ID, track.id)
                putExtra(EXTRA_TRACK_TITLE, track.title)
                putExtra(EXTRA_TRACK_ARTIST, track.artist)
                putExtra(EXTRA_TRACK_THUMB, track.thumbnailUrl)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                track.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val largeBitmap = track.thumbnailUrl?.let { urlStr ->
                try {
                    URL(urlStr).openStream().use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { null }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF22C55E.toInt())
                .setContentTitle("New song from $artistName")
                .setContentText("${track.title} is out now. Tap to listen!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (largeBitmap != null) {
                builder.setLargeIcon(largeBitmap)
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(largeBitmap).setSummaryText("${track.title} • $artistName"))
            }

            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = (artistName.hashCode() * 31 + track.id.hashCode()) and 0x7FFFFFFF
            notificationManager.notify(notificationId, builder.build())
            Log.d(TAG, "Posted new release notification for ${track.title} by $artistName (id=$notificationId)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.message}", e)
        }
    }
}
