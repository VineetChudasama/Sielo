package com.sielo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sielo.music.core.network.innertube.JioSaavnSongArtworkResolver
import com.sielo.music.core.network.innertube.YouTubeArtistImageResolver
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.PaletteCream
import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSlateBlue

object ArtworkPaletteCache {
    private val colorCache = ConcurrentHashMap<String, Color>()

    fun get(key: String): Color? = colorCache[key]
    fun put(key: String, color: Color) {
        colorCache[key] = color
    }
}

/**
 * Extracts and caches the dynamic vibrant / dominant artwork color for ambient glow and disc accents.
 */
@Composable
fun rememberArtworkDominantColor(
    artworkUrl: String?,
    title: String? = "",
    artist: String? = "",
    defaultColor: Color = PaletteSand
): State<Color> {
    val context = LocalContext.current
    val cacheKey = remember(artworkUrl, title, artist) {
        "${artworkUrl ?: ""}-${title ?: ""}-${artist ?: ""}"
    }

    val initialColor = remember(cacheKey) {
        ArtworkPaletteCache.get(cacheKey) ?: defaultColor
    }

    return produceState(initialValue = initialColor, key1 = cacheKey) {
        val cached = ArtworkPaletteCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val resolvedSaavn = JioSaavnSongArtworkResolver.getCachedArtwork(title, artist)
        val raw = artworkUrl?.trim() ?: ""
        val effectiveUrl = when {
            !resolvedSaavn.isNullOrBlank() -> resolvedSaavn
            raw.isNotBlank() && !raw.contains("i.ytimg.com") && !raw.contains("default-music") -> raw
            else -> raw
        }

        if (effectiveUrl.isBlank()) {
            value = defaultColor
            return@produceState
        }

        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(effectiveUrl)
                    .allowHardware(false)
                    .size(100, 100)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is coil.request.SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val palette = Palette.from(bitmap).generate()
                    val dominantInt = palette.getVibrantColor(
                        palette.getDominantColor(
                            palette.getLightVibrantColor(
                                palette.getMutedColor(defaultColor.toArgb())
                            )
                        )
                    )
                    val extracted = Color(dominantInt)
                    val finalColor = if (extracted == Color.Black || extracted == Color.Transparent || extracted == Color.Unspecified) {
                        defaultColor
                    } else {
                        extracted
                    }
                    ArtworkPaletteCache.put(cacheKey, finalColor)
                    value = finalColor
                }
            } catch (_: Exception) {
                value = defaultColor
            }
        }
    }
}

/**
 * Universal Album Art Resolver for Sielo Music.
 * Fast inline fallback if a remote URL is already valid.
 */
fun resolveAlbumArt(thumbnailUrl: String?, title: String? = "", artist: String? = ""): String {
    val raw = thumbnailUrl?.trim() ?: ""
    if (raw.isNotEmpty() && !raw.contains("i.ytimg.com") && !raw.contains("default-music") && !raw.contains("default-film")) {
        return raw
    }
    return ""
}

/**
 * Dynamic Song Album Artwork component.
 * Imports original studio song art directly from JioSaavn on the fly when needed.
 * Does NOT store or cache anything locally on disk.
 */
@Composable
fun SieloSongArtwork(
    thumbnailUrl: String?,
    title: String? = "",
    artist: String? = "",
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = title
) {
    val context = LocalContext.current

    // Synchronous memory cache check to avoid reloading on screen transitions
    val raw = thumbnailUrl?.trim() ?: ""
    val cached = JioSaavnSongArtworkResolver.getCachedArtwork(title, artist)
    val isAlreadyStudioArt = raw.isNotBlank() && !raw.contains("i.ytimg.com") && !raw.contains("lh3.googleusercontent.com") && !raw.contains("default-music") && !raw.contains("default-film")
    val initialResolved = cached ?: if (isAlreadyStudioArt) raw else null

    // Dynamically resolve high-res studio artwork without flashing raw video thumbnails
    val resolvedUrl by produceState<String?>(
        initialValue = initialResolved,
        key1 = thumbnailUrl,
        key2 = title,
        key3 = artist
    ) {
        if (!cached.isNullOrBlank()) {
            value = cached
        } else if (!title.isNullOrBlank()) {
            val saavnCover = JioSaavnSongArtworkResolver.resolveSongArtwork(title, artist)
            if (!saavnCover.isNullOrBlank()) {
                value = saavnCover
            } else if (raw.isNotBlank()) {
                value = raw
            }
        } else if (raw.isNotBlank()) {
            value = raw
        }
    }

    val imageRequest = remember(resolvedUrl) {
        if (!resolvedUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .memoryCacheKey(resolvedUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(200)
                .build()
        } else null
    }

    if (imageRequest != null) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
                .clip(shape)
                .background(PaletteOxfordBlue),
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
                        tint = PaletteSand.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(PaletteOxfordBlue, PaletteSlateBlue.copy(alpha = 0.6f))
                            )
                        )
                        .border(1.dp, BorderGlass, shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = PaletteSand.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            }
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(PaletteOxfordBlue, PaletteDarkNavy)
                    )
                )
                .border(1.dp, BorderGlass, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = PaletteSand.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Dynamic YouTube Artist Photo component.
 * Retains loaded artist photos in memory across screen transitions without re-fetching.
 */
@Composable
fun SieloArtistPhoto(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val firstLetter = remember(name) {
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A"
    }

    val raw = imageUrl?.trim() ?: ""
    val cachedPhoto = YouTubeArtistImageResolver.getCachedArtistImageUrl(name)
    val isDirectPhoto = raw.isNotBlank() && (raw.contains("ggpht.com") || raw.contains("googleusercontent.com")) && !raw.contains("i.ytimg.com") && !raw.contains("default-music")
    val initialPhoto = cachedPhoto ?: if (isDirectPhoto) raw else null

    // Directly fetch artist photo from YouTube Music on the fly when needed
    val resolvedUrl by produceState<String?>(
        initialValue = initialPhoto,
        key1 = imageUrl,
        key2 = name
    ) {
        if (!cachedPhoto.isNullOrBlank()) {
            value = cachedPhoto
        } else if (isDirectPhoto) {
            value = raw
        } else if (name.isNotBlank()) {
            val ytPhoto = YouTubeArtistImageResolver.resolveArtistImageUrl(name)
            if (!ytPhoto.isNullOrBlank()) {
                value = ytPhoto
            } else if (raw.isNotBlank()) {
                value = raw
            }
        }
    }

    val imageRequest = remember(resolvedUrl) {
        if (!resolvedUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .memoryCacheKey(resolvedUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(200)
                .build()
        } else null
    }

    if (imageRequest != null) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = name,
            contentScale = contentScale,
            modifier = modifier
                .clip(shape)
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderSubtle, shape),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(PaletteSlateBlue, PaletteOxfordBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = firstLetter,
                        color = PaletteCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(PaletteSlateBlue, PaletteOxfordBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = firstLetter,
                        color = PaletteCream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            }
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(PaletteSlateBlue, PaletteOxfordBlue)
                    )
                )
                .border(1.dp, BorderSubtle, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                color = PaletteCream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

