package com.sielo.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.ui.theme.PaletteCream
import com.sielo.music.ui.theme.PaletteDarkNavy
import com.sielo.music.ui.theme.PaletteOxfordBlue
import com.sielo.music.ui.theme.PaletteSand
import com.sielo.music.ui.theme.SoraFontFamily
import com.sielo.music.ui.theme.UrbanistFontFamily

@Composable
fun FeedbackPopupDialog(
    onDismiss: (Boolean) -> Unit, // passes 'neverShowAgain' boolean
    onStarClicked: () -> Unit,
    onFeedbackClicked: () -> Unit
) {
    var neverShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(neverShowAgain) },
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Support Sielo",
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Enjoying Sielo? Consider starring the project on GitHub or sharing your feedback directly with the developer to help improve the app!",
                    color = PaletteCream.copy(alpha = 0.8f),
                    fontFamily = UrbanistFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy)
                        .clickable { onStarClicked() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Star", tint = PaletteSand)
                    Text("Star on GitHub", color = PaletteSand, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaletteDarkNavy)
                        .clickable { onFeedbackClicked() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = "Email", tint = PaletteSand)
                    Text("Send Feedback", color = PaletteSand, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { neverShowAgain = !neverShowAgain }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = neverShowAgain,
                        onCheckedChange = { neverShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PaletteSand,
                            uncheckedColor = PaletteCream.copy(alpha = 0.5f),
                            checkmarkColor = PaletteOxfordBlue
                        )
                    )
                    Text(
                        text = "Do not show this pop up again",
                        color = PaletteCream.copy(alpha = 0.7f),
                        fontFamily = UrbanistFontFamily,
                        fontSize = 14.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss(false) },
                enabled = !neverShowAgain
            ) {
                Text(
                    text = "Later",
                    color = if (!neverShowAgain) PaletteSand else PaletteCream.copy(alpha = 0.35f),
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(neverShowAgain) }
            ) {
                Text(
                    text = "Close",
                    color = PaletteSand,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
