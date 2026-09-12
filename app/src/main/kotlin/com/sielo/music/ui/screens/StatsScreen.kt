package com.sielo.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.core.database.dao.ArtistStat
import com.sielo.music.core.database.dao.HourCount
import com.sielo.music.core.database.dao.SongStat
import com.sielo.music.core.database.entity.ListeningEventEntity
import com.sielo.music.ui.components.SieloArtistPhoto
import com.sielo.music.ui.components.SieloSongArtwork
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SurfaceElevated
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextPrimary
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.viewmodel.StatsTimeframe
import com.sielo.music.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StatsSheetType {
    TRACKS,
    ARTISTS,
    PLAYS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val totalTimeMs by viewModel.totalListeningTime.collectAsState()
    val uniqueSongs by viewModel.totalUniqueSongs.collectAsState()
    val uniqueArtists by viewModel.totalUniqueArtists.collectAsState()
    val totalStreams by viewModel.totalStreamCount.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val allArtists by viewModel.allArtists.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val streamHistory by viewModel.streamHistory.collectAsState()
    val hourlyDistribution by viewModel.hourlyDistribution.collectAsState()

    var isTimeframeDropdownExpanded by remember { mutableStateOf(false) }
    var activeDetailSheet by remember { mutableStateOf<StatsSheetType?>(null) }

    val persona = remember(hourlyDistribution) { viewModel.computeListenerPersona(hourlyDistribution) }
    val peakHours = remember(hourlyDistribution) { viewModel.computePeakHourSlot(hourlyDistribution) }

    val totalMinutes = (totalTimeMs ?: 0L) / (1000 * 60)
    val totalHours = totalMinutes / 60
    val remainingMinutes = totalMinutes % 60
    val formattedDuration = if (totalHours > 0) "${totalHours}h ${remainingMinutes}m" else "${remainingMinutes}m"

    // Infinite breathing glow transition for Hero Aura
    val infiniteTransition = rememberInfiniteTransition(label = "AuraGlow")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp)
        ) {
            // Header with Timeframe Dropdown Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Analytics & Aura",
                        color = PaletteSageGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Listening Stats",
                        color = PaletteCream,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Properly Aligned Timeframe Dropdown Menu
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(PaletteOxfordBlue)
                            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                            .clickable { isTimeframeDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = selectedTimeframe.label,
                            color = PaletteSand,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Timeframe",
                            tint = PaletteSand,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isTimeframeDropdownExpanded,
                        onDismissRequest = { isTimeframeDropdownExpanded = false },
                        modifier = Modifier
                            .background(PaletteOxfordBlue)
                            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                    ) {
                        StatsTimeframe.entries.forEach { tf ->
                            val isSelected = tf == selectedTimeframe
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tf.label,
                                        color = if (isSelected) PaletteSand else PaletteCream,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    viewModel.selectTimeframe(tf)
                                    isTimeframeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // 1. Hero Listening Aura Card (Fixed constant container size)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(192.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        PaletteSand.copy(alpha = auraAlpha * 0.35f),
                                        Color(0xFF2A2050).copy(alpha = 0.5f),
                                        PaletteOxfordBlue
                                    ),
                                    radius = 700f
                                )
                            )
                            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PaletteSand.copy(alpha = 0.2f))
                                        .border(1.dp, PaletteSand.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = persona,
                                        color = PaletteSand,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timeline,
                                        contentDescription = "Peak",
                                        tint = PaletteSageGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Peak: $peakHours",
                                        color = PaletteSageGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "TOTAL LISTENING TIME",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (totalMinutes > 0) formattedDuration else "0h 0m",
                                    color = PaletteCream,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    maxLines = 1
                                )
                            }

                            // Animated Waveform Soundwaves Indicator (Fixed height row)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val barHeights = listOf(14.dp, 24.dp, 34.dp, 20.dp, 30.dp, 34.dp, 26.dp, 32.dp, 18.dp, 28.dp, 34.dp, 22.dp, 16.dp)
                                barHeights.forEachIndexed { idx, height ->
                                    val animatedScale by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600 + (idx * 90), easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "Wave$idx"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(height * animatedScale.coerceIn(0.35f, 1f))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (idx % 2 == 0) PaletteSand.copy(alpha = 0.85f)
                                                else PaletteSageGreen.copy(alpha = 0.85f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Bento Metrics Row (Functional & Clickable -> Opens Details)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernMetricCard(
                            label = "TRACKS",
                            value = "$uniqueSongs",
                            icon = Icons.Default.MusicNote,
                            accentColor = PaletteSand,
                            modifier = Modifier.weight(1f),
                            onClick = { activeDetailSheet = StatsSheetType.TRACKS }
                        )
                        ModernMetricCard(
                            label = "ARTISTS",
                            value = "$uniqueArtists",
                            icon = Icons.Default.Person,
                            accentColor = PaletteSageGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { activeDetailSheet = StatsSheetType.ARTISTS }
                        )
                        ModernMetricCard(
                            label = "PLAYS",
                            value = "$totalStreams",
                            icon = Icons.Default.Headphones,
                            accentColor = PaletteSlateBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { activeDetailSheet = StatsSheetType.PLAYS }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 3. Redesigned Circadian Time of Day Distribution
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "CIRCADIAN SPECTRUM",
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Time of Day Distribution",
                            color = PaletteCream,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RedesignedTimeOfDaySection(
                        hourlyDistribution = hourlyDistribution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 4. Top Artists Podium & Showcase
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "LEADERBOARD",
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Top Artists",
                            color = PaletteCream,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (topArtists.isEmpty()) {
                    item {
                        ModernEmptyArtistSpotlight()
                    }
                } else {
                    // Top #1 Artist Hero Card
                    item {
                        topArtists.firstOrNull()?.let { firstArtist ->
                            TopArtistSpotlightCard(artist = firstArtist)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Remaining Top Artists
                    itemsIndexed(topArtists.drop(1)) { idx, artist ->
                        ModernArtistLeaderboardRow(
                            rank = idx + 2,
                            artist = artist
                        )
                    }
                }

                // 5. Most Played Tracks Leaderboard with 1-Tap Play
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "HEAVY ROTATION",
                            color = PaletteSageGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Most Played Tracks",
                            color = PaletteCream,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (topSongs.isEmpty()) {
                    item {
                        ModernEmptySongsLeaderboard()
                    }
                } else {
                    itemsIndexed(topSongs) { index, song ->
                        ModernSongLeaderboardCard(
                            rank = index + 1,
                            song = song,
                            onPlay = { viewModel.playSongStat(song) }
                        )
                    }
                }
            }
        }

        // Functional Detail Bottom Sheet (For Tracks, Artists, Plays)
        activeDetailSheet?.let { sheetType ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { activeDetailSheet = null },
                sheetState = sheetState,
                containerColor = PaletteOxfordBlue,
                contentColor = PaletteCream,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(PaletteSand.copy(alpha = 0.5f))
                    )
                }
            ) {
                StatsDetailSheetContent(
                    sheetType = sheetType,
                    allSongs = allSongs,
                    allArtists = allArtists,
                    streamHistory = streamHistory,
                    onPlaySong = { viewModel.playSongStat(it) },
                    onPlayEvent = { viewModel.playHistoryEvent(it) },
                    onClose = { activeDetailSheet = null }
                )
            }
        }
    }
}

@Composable
fun ModernMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = PaletteCream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View list",
                    tint = TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun RedesignedTimeOfDaySection(
    hourlyDistribution: List<HourCount>,
    modifier: Modifier = Modifier
) {
    var morningDurationMs = 0L
    var afternoonDurationMs = 0L
    var eveningDurationMs = 0L
    var nightDurationMs = 0L

    if (hourlyDistribution.isNotEmpty()) {
        hourlyDistribution.forEach { item ->
            val dur = if (item.totalDurationMs > 0) item.totalDurationMs else item.count * 60000L
            when (item.hourOfDay) {
                in 6..11 -> morningDurationMs += dur
                in 12..16 -> afternoonDurationMs += dur
                in 17..21 -> eveningDurationMs += dur
                else -> nightDurationMs += dur
            }
        }
    }

    val totalDayDurationMs = (morningDurationMs + afternoonDurationMs + eveningDurationMs + nightDurationMs).coerceAtLeast(1L)
    val morningRatio = (morningDurationMs.toFloat() / totalDayDurationMs).coerceIn(0f, 1f)
    val afternoonRatio = (afternoonDurationMs.toFloat() / totalDayDurationMs).coerceIn(0f, 1f)
    val eveningRatio = (eveningDurationMs.toFloat() / totalDayDurationMs).coerceIn(0f, 1f)
    val nightRatio = (nightDurationMs.toFloat() / totalDayDurationMs).coerceIn(0f, 1f)

    val maxSlot = listOf(
        "Morning" to morningDurationMs,
        "Afternoon" to afternoonDurationMs,
        "Evening" to eveningDurationMs,
        "Night" to nightDurationMs
    ).maxByOrNull { it.second }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Dominant Window Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peak Activity Rhythm",
                    color = PaletteCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (maxSlot != null && maxSlot.second > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PaletteSand.copy(alpha = 0.15f))
                            .border(1.dp, PaletteSand.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Peak: ${maxSlot.first}",
                            color = PaletteSand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Proportional Circadian Spectrum Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(SurfaceElevated),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (morningDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(morningRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFF59E0B))
                    )
                }
                if (afternoonDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(afternoonRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF06B6D4))
                    )
                }
                if (eveningDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(eveningRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEC4899))
                    )
                }
                if (nightDurationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(nightRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF8B5CF6))
                    )
                }
                if (morningDurationMs == 0L && afternoonDurationMs == 0L && eveningDurationMs == 0L && nightDurationMs == 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PaletteSlateBlue.copy(alpha = 0.3f))
                    )
                }
            }

            // 2x2 Bento Day-Part Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DayPartCard(
                        title = "Dawn / Morning",
                        timeRange = "6 AM – 12 PM",
                        durationMs = morningDurationMs,
                        percentage = if (totalDayDurationMs > 1L) (morningRatio * 100).toInt() else 0,
                        icon = Icons.Default.WbTwilight,
                        accentColor = Color(0xFFF59E0B),
                        isPeak = maxSlot?.first == "Morning" && morningDurationMs > 0L,
                        modifier = Modifier.weight(1f)
                    )
                    DayPartCard(
                        title = "Day / Afternoon",
                        timeRange = "12 PM – 5 PM",
                        durationMs = afternoonDurationMs,
                        percentage = if (totalDayDurationMs > 1L) (afternoonRatio * 100).toInt() else 0,
                        icon = Icons.Default.WbSunny,
                        accentColor = Color(0xFF06B6D4),
                        isPeak = maxSlot?.first == "Afternoon" && afternoonDurationMs > 0L,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DayPartCard(
                        title = "Dusk / Evening",
                        timeRange = "5 PM – 10 PM",
                        durationMs = eveningDurationMs,
                        percentage = if (totalDayDurationMs > 1L) (eveningRatio * 100).toInt() else 0,
                        icon = Icons.Default.LightMode,
                        accentColor = Color(0xFFEC4899),
                        isPeak = maxSlot?.first == "Evening" && eveningDurationMs > 0L,
                        modifier = Modifier.weight(1f)
                    )
                    DayPartCard(
                        title = "Night / Midnight",
                        timeRange = "10 PM – 6 AM",
                        durationMs = nightDurationMs,
                        percentage = if (totalDayDurationMs > 1L) (nightRatio * 100).toInt() else 0,
                        icon = Icons.Default.Nightlight,
                        accentColor = Color(0xFF8B5CF6),
                        isPeak = maxSlot?.first == "Night" && nightDurationMs > 0L,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DayPartCard(
    title: String,
    timeRange: String,
    durationMs: Long,
    percentage: Int,
    icon: ImageVector,
    accentColor: Color,
    isPeak: Boolean,
    modifier: Modifier = Modifier
) {
    val totalMins = durationMs / (1000 * 60)
    val hours = totalMins / 60
    val mins = totalMins % 60
    val durationText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .border(
                1.dp,
                if (isPeak) accentColor.copy(alpha = 0.8f) else BorderGlass,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "$percentage%",
                    color = if (percentage > 0) PaletteCream else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = title,
                    color = PaletteCream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeRange,
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            Text(
                text = if (durationMs > 0) durationText else "0m",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TopArtistSpotlightCard(artist: ArtistStat) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF2E2610),
                        PaletteOxfordBlue
                    )
                )
            )
            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Gold Medal Badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "1",
                        color = PaletteDarkNavy,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                com.sielo.music.ui.components.SieloArtistPhoto(
                    imageUrl = null,
                    name = artist.artistName,
                    modifier = Modifier
                        .size(54.dp)
                        .border(1.5.dp, Color(0xFFFFD700), CircleShape)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artist.artistName,
                            color = PaletteCream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "#1 Top Artist • ${artist.playCount} plays",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Star",
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ModernArtistLeaderboardRow(
    rank: Int,
    artist: ArtistStat
) {
    val rankColor = when (rank) {
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> PaletteSlateBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "#$rank",
                    color = rankColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                com.sielo.music.ui.components.SieloArtistPhoto(
                    imageUrl = null,
                    name = artist.artistName,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = artist.artistName,
                        color = PaletteCream,
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
fun ModernSongLeaderboardCard(
    rank: Int,
    song: SongStat,
    onPlay: () -> Unit
) {
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> PaletteSlateBlue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PaletteOxfordBlue)
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
                    color = medalColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )

                com.sielo.music.ui.components.SieloSongArtwork(
                    thumbnailUrl = song.thumbnailUrl,
                    title = song.songTitle,
                    artist = song.artistName,
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.songTitle,
                        color = PaletteCream,
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
                    .background(PaletteSand.copy(alpha = 0.15f))
                    .border(1.dp, PaletteSand.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = PaletteSand,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun StatsDetailSheetContent(
    sheetType: StatsSheetType,
    allSongs: List<SongStat>,
    allArtists: List<ArtistStat>,
    streamHistory: List<ListeningEventEntity>,
    onPlaySong: (SongStat) -> Unit,
    onPlayEvent: (ListeningEventEntity) -> Unit,
    onClose: () -> Unit
) {
    val title = when (sheetType) {
        StatsSheetType.TRACKS -> "All Unique Tracks (${allSongs.size})"
        StatsSheetType.ARTISTS -> "All Listened Artists (${allArtists.size})"
        StatsSheetType.PLAYS -> "Stream History (${streamHistory.size} plays)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = PaletteCream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (sheetType) {
                StatsSheetType.TRACKS -> {
                    if (allSongs.isEmpty()) {
                        item { ModernEmptySongsLeaderboard() }
                    } else {
                        itemsIndexed(allSongs) { idx, song ->
                            ModernSongLeaderboardCard(
                                rank = idx + 1,
                                song = song,
                                onPlay = { onPlaySong(song) }
                            )
                        }
                    }
                }
                StatsSheetType.ARTISTS -> {
                    if (allArtists.isEmpty()) {
                        item { ModernEmptyArtistSpotlight() }
                    } else {
                        itemsIndexed(allArtists) { idx, artist ->
                            ModernArtistLeaderboardRow(
                                rank = idx + 1,
                                artist = artist
                            )
                        }
                    }
                }
                StatsSheetType.PLAYS -> {
                    if (streamHistory.isEmpty()) {
                        item { ModernEmptySongsLeaderboard() }
                    } else {
                        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        itemsIndexed(streamHistory) { idx, event ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                                    .clickable { onPlayEvent(event) }
                                    .padding(12.dp)
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
                                        SieloSongArtwork(
                                            thumbnailUrl = event.thumbnailUrl,
                                            title = event.songTitle,
                                            artist = event.artistName,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = event.songTitle,
                                                color = PaletteCream,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${event.artistName} • ${dateFormat.format(Date(event.timestampMs))}",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(PaletteSand.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = PaletteSand,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernEmptyArtistSpotlight() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = PaletteSand,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Play your favorite songs to uncover your top artists.",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun ModernEmptySongsLeaderboard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                tint = PaletteSageGreen,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No track history recorded yet. Start listening now!",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}
