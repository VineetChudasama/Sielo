package com.sielo.music.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.BuildConfig
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.TextSecondary
import com.sielo.music.ui.theme.UrbanistFontFamily

@Composable
fun AppUpdateDialog(
    newVersion: String,
    onRemindLater: () -> Unit,
    onUpdateNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onRemindLater,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.border(1.dp, BorderGlass, RoundedCornerShape(24.dp)),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PaletteSand.copy(alpha = 0.25f), PaletteOxfordBlue)
                            )
                        )
                        .border(1.dp, PaletteSand.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Available",
                        tint = PaletteSand,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Update Available",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "A new version of Sielo (v$newVersion) is now available. Please download the update from our website for the latest features, enhancements, and bug fixes.",
                    color = PaletteCream.copy(alpha = 0.82f),
                    fontFamily = UrbanistFontFamily,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )

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
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateNow,
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
                    text = "Update Now",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRemindLater,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PaletteCream.copy(alpha = 0.75f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = "Remind Me Later",
                    fontFamily = UrbanistFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            }
        }
    )
}
