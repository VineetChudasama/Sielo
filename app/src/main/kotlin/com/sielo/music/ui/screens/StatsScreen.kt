package com.sielo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sielo.music.core.database.dao.ArtistStat
import com.sielo.music.core.database.dao.HourCount
import com.sielo.music.core.database.dao.SongStat
import com.sielo.music.ui.theme.AccentCoral
import com.sielo.music.ui.theme.AccentPeach
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.SurfaceDark
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val totalTimeMs by viewModel.totalListeningTime.collectAsState()
    val uniqueSongs by viewModel.totalUniqueSongs.collectAsState()
    val uniqueArtists by viewModel.totalUniqueArtists.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val hourlyDistribution by viewModel.hourlyDistribution.collectAsState()

    val totalMinutes = (totalTimeMs ?: 0L) / (1000 * 60)
    val totalHours = totalMinutes / 60
    val remainingMinutes = totalMinutes % 60
    val formattedDuration = if (totalHours > 0) "${totalHours}h ${remainingMinutes}m" else "${remainingMinutes}m"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp)
        ) {
            // Header
            Text(
                text = "Stats & Activity",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // Key Metrics Highlight Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "LISTENING TIME",
                            value = formattedDuration,
                            accentColor = AccentCoral,
                            modifier = Modifier.weight(1.3f)
                        )
                        MetricCard(
                            label = "TRACKS",
                            value = "$uniqueSongs",
                            accentColor = TextPrimary,
                            modifier = Modifier.weight(0.85f)
                        )
                        MetricCard(
                            label = "ARTISTS",
                            value = "$uniqueArtists",
                            accentColor = AccentPeach,
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                }

                // Time of Day Activity Bar Chart (Hours Display)
                item {
                    Text(
                        text = "Time of day distribution",
                        color = AccentCoral,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    TimeOfDayChart(
                        hourlyDistribution = hourlyDistribution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(22.dp))
                }

                // Top Artists Leaderboard
                item {
                    Text(
                        text = "Top artists",
                        color = AccentCoral,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                if (topArtists.isEmpty()) {
                    item {
                        Text(
                            text = "Play more tracks to populate your artist profile.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(topArtists) { index, artist ->
                        ArtistLeaderboardCard(rank = index + 1, artist = artist)
                    }
                }

                // Top Songs Leaderboard
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Most played tracks",
                        color = AccentCoral,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                if (topSongs.isEmpty()) {
                    item {
                        Text(
                            text = "No track history recorded yet.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(topSongs) { index, song ->
                        SongLeaderboardCard(
                            rank = index + 1,
                            song = song,
                            onPlay = { viewModel.playSongStat(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TimeOfDayChart(
    hourlyDistribution: List<HourCount>,
    modifier: Modifier = Modifier
) {
    var morningDurationMs = 0L
    var afternoonDurationMs = 0L
    var eveningDurationMs = 0L
    var nightDurationMs = 0L

    hourlyDistribution.forEach { item ->
        val dur = if (item.totalDurationMs > 0) item.totalDurationMs else item.count * 180000L
        when (item.hourOfDay) {
            in 6..11 -> morningDurationMs += dur
            in 12..16 -> afternoonDurationMs += dur
            in 17..21 -> eveningDurationMs += dur
            else -> nightDurationMs += dur
        }
    }

    val maxDurationMs = maxOf(morningDurationMs, afternoonDurationMs, eveningDurationMs, nightDurationMs, 1L).toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartBar(label = "MORNING", durationMs = morningDurationMs, ratio = morningDurationMs / maxDurationMs, color = AccentCoral)
                ChartBar(label = "AFTERNOON", durationMs = afternoonDurationMs, ratio = afternoonDurationMs / maxDurationMs, color = AccentPeach)
                ChartBar(label = "EVENING", durationMs = eveningDurationMs, ratio = eveningDurationMs / maxDurationMs, color = Color(0xFFE57373))
                ChartBar(label = "NIGHT", durationMs = nightDurationMs, ratio = nightDurationMs / maxDurationMs, color = Color(0xFFFFB74D))
            }
        }
    }
}

@Composable
fun ChartBar(
    label: String,
    durationMs: Long,
    ratio: Float,
    color: Color
) {
    val totalHours = durationMs.toDouble() / (1000.0 * 60.0 * 60.0)
    val displayHours = when {
        durationMs == 0L -> "0.0 hrs"
        totalHours < 0.1 -> "<0.1 hrs"
        else -> String.format(java.util.Locale.US, "%.1f hrs", totalHours)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((84 * ratio.coerceIn(if (durationMs > 0) 0.12f else 0.04f, 1f)).dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = displayHours,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ArtistLeaderboardCard(
    rank: Int,
    artist: ArtistStat
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    color = if (rank == 1) AccentCoral else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )

                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = artist.artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, CircleShape)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = artist.artistName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${artist.playCount} plays",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SongLeaderboardCard(
    rank: Int,
    song: SongStat,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$rank",
                    color = if (rank == 1) AccentCoral else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )

                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.songTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.songTitle,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artistName} • ${song.playCount} plays",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentCoral.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = AccentCoral,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
