package com.sielo.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.sielo.music.core.network.models.SieloAlbum
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.TextMuted
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily

/**
 * Universal Sielo Album Hero Component.
 *
 * Fully data-driven, modular, responsive, and compatible with Albums, EPs, Singles,
 * Compilations, and Soundtracks across the entire catalog.
 */
@Composable
fun AlbumHero(
    album: SieloAlbum,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onSaveAsPlaylist: (() -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    isFavorite: Boolean = album.isFavorite,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Responsive artwork sizing: adapts smoothly across 360dp to 430dp+
    val artworkSize = (screenWidth * 0.54f).coerceIn(180.dp, 240.dp)
    val hasTracks = true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletteDarkNavy)
    ) {
        // 1. Dynamic Blurred Artwork Background
        AlbumHeroBackground(
            thumbnailUrl = album.thumbnailUrl,
            modifier = Modifier.matchParentSize()
        )

        // Hero Vertical Content Hierarchy
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2. Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                        .border(1.dp, BorderGlass, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PaletteCream,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Top Right Action Buttons (Favorite & More)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onFavorite != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                                .border(1.dp, BorderGlass, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onFavorite() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Favorited" else "Favorite",
                                tint = if (isFavorite) Color(0xFFE63946) else PaletteCream,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onSaveAsPlaylist != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                                .border(1.dp, BorderGlass, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSaveAsPlaylist() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Save as Playlist",
                                tint = PaletteSand,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onMore != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                                .border(1.dp, BorderGlass, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onMore() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = PaletteCream,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Central Album Artwork
            AlbumArtwork(
                thumbnailUrl = album.thumbnailUrl,
                title = album.title,
                size = artworkSize
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Album Metadata (Badge, Title, Artists, Year, Tracks, Quality)
            AlbumMetadata(
                album = album,
                onArtistClick = onArtistClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            // 5. Album Description (Expandable, hidden if null/empty)
            val albumDesc = album.description
            if (!albumDesc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AlbumDescription(
                    description = albumDesc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Action Buttons (Play Album, Shuffle, Favorite, Download, More)
            AlbumActions(
                onPlay = onPlay,
                onShuffle = onShuffle,
                isFavorite = isFavorite,
                onFavorite = onFavorite,
                onDownload = onDownload,
                onMore = onMore,
                onSaveAsPlaylist = onSaveAsPlaylist,
                hasTracks = hasTracks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * Dynamic Blurred Artwork Background.
 * Renders the album art with heavy blur and a multi-stop gradient overlay fading into
 * PaletteDarkNavy (#0D1B2A) and PaletteOxfordBlue (#1B263B).
 */
@Composable
fun AlbumHeroBackground(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
            )
            // Dark scrim to prevent light backgrounds from blowing out contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
            )
        }

        // Seamless theme gradient into Sielo dark navy canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PaletteDarkNavy.copy(alpha = 0.35f),
                            PaletteDarkNavy.copy(alpha = 0.75f),
                            PaletteDarkNavy.copy(alpha = 0.95f),
                            PaletteDarkNavy
                        )
                    )
                )
        )

        // Subtle ambient radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PaletteOxfordBlue.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )
    }
}

/**
 * Central Album Artwork with responsive sizing, subtle outer glow, rounded corners,
 * and authentic Sielo fallback state.
 */
@Composable
fun AlbumArtwork(
    thumbnailUrl: String?,
    title: String,
    size: Dp = 210.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(thumbnailUrl) {
        if (!thumbnailUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(true)
                .build()
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.65f),
                ambientColor = PaletteOxfordBlue.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(PaletteOxfordBlue)
            .border(1.dp, BorderGlass, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(PaletteOxfordBlue, PaletteDarkNavy)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = PaletteSand.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                },
                error = {
                    AlbumArtworkFallback()
                },
                success = {
                    SubcomposeAsyncImageContent()
                }
            )
        } else {
            AlbumArtworkFallback()
        }
    }
}

/**
 * Elegant Sielo artwork fallback with deep navy & subtle slate gradient and music icon.
 */
@Composable
private fun AlbumArtworkFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(PaletteOxfordBlue, PaletteDarkNavy)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = PaletteSand.copy(alpha = 0.75f),
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SIELO",
                color = PaletteSand.copy(alpha = 0.6f),
                fontFamily = SoraFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

/**
 * Dynamic Album Metadata:
 * - Dynamic album type badge (ALBUM, EP, SINGLE, COMPILATION, SOUNDTRACK)
 * - Large, bold typography for title (maxLines = 2, text ellipsize)
 * - Single or multiple artist names
 * - Release Info (year • tracks • audio quality badge if available)
 */
@Composable
fun AlbumMetadata(
    album: SieloAlbum,
    onArtistClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayType = remember(album.type, album.tracks.size, album.songCount) {
        val raw = album.type?.trim()?.uppercase()
        if (!raw.isNullOrBlank() && raw != "UNKNOWN") {
            raw
        } else {
            val count = if (album.songCount > 0) album.songCount else album.tracks.size
            when {
                count <= 1 -> "SINGLE"
                count in 2..6 -> "EP"
                else -> "ALBUM"
            }
        }
    }

    val trackCount = if (album.songCount > 0) album.songCount else album.tracks.size
    val trackCountText = if (trackCount > 0) {
        "$trackCount ${if (trackCount == 1) "song" else "songs"}"
    } else null

    val releaseYear = album.year?.takeIf { it.isNotBlank() }
        ?: album.releaseDate?.takeIf { it.isNotBlank() }

    val releaseInfoParts = remember(releaseYear, trackCountText) {
        listOfNotNull(releaseYear, trackCountText)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dynamic Album Type Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(PaletteOxfordBlue.copy(alpha = 0.85f))
                .border(1.dp, BorderGlass, RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 3.5.dp)
        ) {
            Text(
                text = displayType,
                color = PaletteSageGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = SoraFontFamily
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Album Title
        Text(
            text = album.title,
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Artist Name(s)
        Text(
            text = album.artist,
            color = PaletteSand,
            fontFamily = UrbanistFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onArtistClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onArtistClick(album.artist) }
            } else Modifier
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Release Info Row: {year} • {trackCount} • {quality badge}
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (releaseInfoParts.isNotEmpty()) {
                Text(
                    text = releaseInfoParts.joinToString(" • "),
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.sp
                )
            }

            // Audio Quality Badge (Only rendered if available)
            val quality = album.audioQuality
            if (!quality.isNullOrBlank()) {
                if (releaseInfoParts.isNotEmpty()) {
                    Text(
                        text = " • ",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PaletteSlateBlue.copy(alpha = 0.35f))
                        .border(0.75.dp, BorderGlass, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = quality.uppercase(),
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Expandable / collapsible Album Description with "more" / "less" toggle.
 * Completely hidden if description is null or empty.
 */
@Composable
fun AlbumDescription(
    description: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = description,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isExpanded) "Show less" else "Read more",
            color = PaletteSand,
            fontFamily = SoraFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isExpanded = !isExpanded }
        )
    }
}

/**
 * Action Buttons for Album:
 * - Primary: PLAY / PLAY ALBUM
 * - Secondary: SHUFFLE
 * - Auxiliary action buttons: Favorite toggle, Download, More
 */
@Composable
fun AlbumActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isFavorite: Boolean,
    onFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onSaveAsPlaylist: (() -> Unit)? = null,
    hasTracks: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play Album Button (Primary Accent)
        Button(
            onClick = onPlay,
            enabled = hasTracks,
            colors = ButtonDefaults.buttonColors(
                containerColor = PaletteSand,
                contentColor = PaletteDarkNavy,
                disabledContainerColor = PaletteOxfordBlue.copy(alpha = 0.5f),
                disabledContentColor = TextMuted
            ),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (hasTracks) PaletteDarkNavy else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PLAY ALBUM",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Shuffle Button (Secondary Glass)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                .clickable(
                    enabled = hasTracks,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onShuffle() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedShuffleIcon(
                    modifier = Modifier.size(18.dp),
                    tint = if (hasTracks) PaletteCream else TextMuted
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SHUFFLE",
                    color = if (hasTracks) PaletteCream else TextMuted,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (onSaveAsPlaylist != null) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSaveAsPlaylist() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Save as Playlist",
                    tint = PaletteSand,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Optional Quick Download Action
        if (onDownload != null) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue)
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDownload() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Album",
                    tint = PaletteCream,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Shimmer skeleton loading state for AlbumHero.
 */
@Composable
fun AlbumHeroSkeleton(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "albumHeroShimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "albumShimmerAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletteDarkNavy)
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cover artwork skeleton
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Badge skeleton
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.7f))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Title skeleton
        Box(
            modifier = Modifier
                .size(width = 180.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist skeleton
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha * 0.7f))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteOxfordBlue.copy(alpha = shimmerAlpha))
            )
        }
    }
}
