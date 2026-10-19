package com.sielo.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.BuildConfig
import com.sielo.music.core.update.UpdateDownloadState
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.PaletteSageGreen
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily

@Composable
fun AppUpdateDialog(
    newVersion: String,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onStartDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onInstallAndRestart: () -> Unit = {},
    onRemindLater: () -> Unit = {},
    onOpenWebsite: () -> Unit = {}
) {
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    val isCompleted = downloadState is UpdateDownloadState.Completed
    val isError = downloadState is UpdateDownloadState.Error

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onRemindLater()
            }
        },
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.border(1.dp, BorderGlass, RoundedCornerShape(24.dp)),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status icon with glowing gradient halo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = when {
                                    isCompleted -> listOf(PaletteSageGreen.copy(alpha = 0.3f), PaletteOxfordBlue)
                                    isError -> listOf(Color(0xFFE63946).copy(alpha = 0.3f), PaletteOxfordBlue)
                                    isDownloading -> listOf(PaletteSand.copy(alpha = 0.3f), PaletteOxfordBlue)
                                    else -> listOf(PaletteSand.copy(alpha = 0.25f), PaletteOxfordBlue)
                                }
                            )
                        )
                        .border(
                            1.dp,
                            when {
                                isCompleted -> PaletteSageGreen.copy(alpha = 0.5f)
                                isError -> Color(0xFFE63946).copy(alpha = 0.5f)
                                else -> PaletteSand.copy(alpha = 0.4f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.CheckCircle
                            isError -> Icons.Default.ErrorOutline
                            isDownloading -> Icons.Default.CloudDownload
                            else -> Icons.Default.SystemUpdate
                        },
                        contentDescription = "Update Status",
                        tint = when {
                            isCompleted -> PaletteSageGreen
                            isError -> Color(0xFFE63946)
                            else -> PaletteSand
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when {
                        isCompleted -> "Download Complete"
                        isDownloading -> "Downloading Update"
                        isError -> "Download Interrupted"
                        else -> "Update Available"
                    },
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Description text dynamically reacting to state
                AnimatedContent(
                    targetState = downloadState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "dialog_desc"
                ) { state ->
                    when (state) {
                        is UpdateDownloadState.Completed -> {
                            Text(
                                text = "Sielo v$newVersion has been successfully downloaded. Tap below to install the update and refresh the app.",
                                color = PaletteCream.copy(alpha = 0.88f),
                                fontFamily = UrbanistFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is UpdateDownloadState.Downloading -> {
                            val mbDownloaded = state.bytesDownloaded / (1024f * 1024f)
                            val mbTotal = state.totalBytes / (1024f * 1024f)
                            val percent = (state.progress * 100f).toInt()

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Downloading Sielo v$newVersion directly in the app. Please keep Sielo open.",
                                    color = PaletteCream.copy(alpha = 0.82f),
                                    fontFamily = UrbanistFontFamily,
                                    fontSize = 13.5.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Real progress bar
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PaletteSand,
                                    trackColor = PaletteDarkNavy
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (state.totalBytes > 0) {
                                            String.format(java.util.Locale.ROOT, "%.1f / %.1f MB", mbDownloaded, mbTotal)
                                        } else {
                                            String.format(java.util.Locale.ROOT, "%.1f MB", mbDownloaded)
                                        },
                                        color = TextSecondary,
                                        fontFamily = UrbanistFontFamily,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "$percent%",
                                        color = PaletteSand,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(
                                text = "Failed to download update: ${state.message}. You can retry or download directly from our website.",
                                color = PaletteCream.copy(alpha = 0.85f),
                                fontFamily = UrbanistFontFamily,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        UpdateDownloadState.Idle -> {
                            Text(
                                text = "A new version of Sielo (v$newVersion) is available with performance improvements and bug fixes. You can download and install it directly within the app.",
                                color = PaletteCream.copy(alpha = 0.82f),
                                fontFamily = UrbanistFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Version information card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "New: v$newVersion",
                            color = PaletteSand,
                            fontFamily = SoraFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Official website download link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PaletteDarkNavy.copy(alpha = 0.5f))
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                        .clickable { onOpenWebsite() }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Website",
                        tint = PaletteSand,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Or download from website: www.sielo.site",
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is UpdateDownloadState.Completed -> {
                    Button(
                        onClick = onInstallAndRestart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSand,
                            contentColor = PaletteDarkNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Restart",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Install & Restart App",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                is UpdateDownloadState.Downloading -> {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PaletteCream.copy(alpha = 0.8f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Cancel Download",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp
                        )
                    }
                }
                is UpdateDownloadState.Error -> {
                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSand,
                            contentColor = PaletteDarkNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Retry Download",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                UpdateDownloadState.Idle -> {
                    Button(
                        onClick = onStartDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSand,
                            contentColor = PaletteDarkNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Directly In App",
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isError) {
                        OutlinedButton(
                            onClick = onOpenWebsite,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PaletteSand
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PaletteSand.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Text(
                                text = "Open Website",
                                fontFamily = SoraFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    OutlinedButton(
                        onClick = onRemindLater,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text(
                            text = if (isCompleted) "Dismiss" else "Remind Later",
                            fontFamily = SoraFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    )
}
