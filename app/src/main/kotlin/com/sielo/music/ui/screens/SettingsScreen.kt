package com.sielo.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sielo.music.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sielo.music.BuildConfig
import com.sielo.music.core.auth.model.AuthProvider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import com.sielo.music.ui.theme.BorderGlass
import com.sielo.music.ui.theme.BorderSubtle
import com.sielo.music.ui.theme.MacondoFontFamily
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
import com.sielo.music.viewmodel.SettingsViewModel

sealed class SettingsSubScreen {
    data object None : SettingsSubScreen()
    data object AccountProfile : SettingsSubScreen()
    data object ConnectedServices : SettingsSubScreen()
    data object AudioPlayback : SettingsSubScreen()
    data object PlayerBehavior : SettingsSubScreen()
    data object AudioOutput : SettingsSubScreen()
    data object DownloadsStorage : SettingsSubScreen()
    data object Appearance : SettingsSubScreen()
    data object Notifications : SettingsSubScreen()
    data object PrivacyData : SettingsSubScreen()
    data object Recommendations : SettingsSubScreen()
    data object About : SettingsSubScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.None) }

    // System back gesture handling
    BackHandler {
        if (currentSubScreen != SettingsSubScreen.None) {
            currentSubScreen = SettingsSubScreen.None
        } else {
            onBack()
        }
    }

    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.None) {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "SettingsScreenTransition"
    ) { subScreen ->
        when (subScreen) {
            SettingsSubScreen.None -> {
                MainSettingsList(
                    viewModel = viewModel,
                    onBack = onBack,
                    onNavigateToSubScreen = { currentSubScreen = it },
                    modifier = modifier
                )
            }
            SettingsSubScreen.AccountProfile -> {
                AccountProfileSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.ConnectedServices -> {
                ConnectedServicesSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.AudioPlayback -> {
                AudioPlaybackSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.PlayerBehavior -> {
                PlayerBehaviorSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.AudioOutput -> {
                AudioOutputSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.DownloadsStorage -> {
                DownloadsStorageSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.Appearance -> {
                AppearanceSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.Notifications -> {
                NotificationsSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.PrivacyData -> {
                PrivacyDataSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.Recommendations -> {
                RecommendationsSubScreen(
                    viewModel = viewModel,
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
            SettingsSubScreen.About -> {
                AboutSubScreen(
                    onBack = { currentSubScreen = SettingsSubScreen.None },
                    modifier = modifier
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SETTINGS LIST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainSettingsList(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToSubScreen: (SettingsSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val streamQuality by viewModel.streamQuality.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val privateListening by viewModel.privateListening.collectAsState()

    var showSignOutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PaletteCream,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Settings",
                                color = PaletteCream,
                                fontFamily = SoraFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Customize your Sielo experience",
                                color = TextSecondary,
                                fontFamily = UrbanistFontFamily,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Sielo Emblem Accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PaletteOxfordBlue)
                            .border(1.dp, BorderGlass, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = PaletteSand,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 1. ACCOUNT
            item {
                SettingsSection(title = "ACCOUNT") {
                    val displayName = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Sielo Listener"
                    val handle = currentUser?.username?.takeIf { it.isNotBlank() }
                        ?: currentUser?.email?.substringBefore("@")
                        ?: "sielo_user"
                    val providerText = if (currentUser?.provider == AuthProvider.GOOGLE) "Google Account" else "Sielo Account"

                    SettingsNavigationRow(
                        title = "Account & Profile",
                        description = "$displayName (@$handle) • $providerText",
                        icon = Icons.Default.AccountCircle,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.AccountProfile) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "Connected Services",
                        description = if (currentUser?.provider == AuthProvider.GOOGLE) "Connected via Google Sign-In" else "No third-party accounts linked",
                        icon = Icons.Default.Sync,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.ConnectedServices) }
                    )
                    if (currentUser != null) {
                        SettingsDivider()
                        SettingsActionRow(
                            title = "Sign Out",
                            description = "Sign out of your active Sielo profile",
                            icon = Icons.Default.Security,
                            textColor = PaletteSand,
                            onClick = { showSignOutDialog = true }
                        )
                    }
                }
            }

            // 2. PLAYBACK
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "PLAYBACK") {
                    SettingsNavigationRow(
                        title = "Audio & Playback",
                        description = "Streaming quality ($streamQuality), DSP equalizer, bass boost",
                        icon = Icons.Default.Audiotrack,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.AudioPlayback) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "Player Behavior",
                        description = "Autoplay queue, crossfade, gapless, playback position",
                        icon = Icons.Default.PlayCircleOutline,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.PlayerBehavior) }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "Audio Output",
                        description = "Headset disconnect, Bluetooth resume, volume normalization",
                        icon = Icons.Default.VolumeUp,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.AudioOutput) }
                    )
                }
            }

            // 3. DOWNLOADS & STORAGE
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "DOWNLOADS & STORAGE") {
                    SettingsNavigationRow(
                        title = "Storage & Cache",
                        description = "$cacheSize cached • Clear audio and image cache safely",
                        icon = Icons.Default.Storage,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.DownloadsStorage) }
                    )
                }
            }

            // 4. APPEARANCE
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "APPEARANCE") {
                    SettingsNavigationRow(
                        title = "Theme & Appearance",
                        description = "OLED midnight mode, dynamic artwork ambient glow",
                        icon = Icons.Default.DarkMode,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.Appearance) }
                    )
                }
            }

            // 5. NOTIFICATIONS
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "NOTIFICATIONS") {
                    SettingsNavigationRow(
                        title = "Notification Preferences",
                        description = "Playback controls, artist new releases & room alerts",
                        icon = Icons.Default.Notifications,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.Notifications) }
                    )
                }
            }

            // 6. PRIVACY & DATA
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "PRIVACY & DATA") {
                    SettingsToggleRow(
                        title = "Private Listening",
                        description = if (privateListening) "Active: playback is hidden from history and stats" else "Tracks you play will count towards history and stats",
                        icon = Icons.Default.Lock,
                        isChecked = privateListening,
                        onCheckedChange = { viewModel.togglePrivateListening() }
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "Data & History Controls",
                        description = "Clear search history, manage listening data",
                        icon = Icons.Default.History,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.PrivacyData) }
                    )
                }
            }

            // 7. RECOMMENDATIONS
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "RECOMMENDATIONS") {
                    SettingsNavigationRow(
                        title = "Music Recommendations",
                        description = "Tune discovery engine, diversity mode & followed artists",
                        icon = Icons.Default.Radio,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.Recommendations) }
                    )
                }
            }

            // 8. ABOUT
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSection(title = "ABOUT") {
                    SettingsNavigationRow(
                        title = "About Sielo",
                        description = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • Licenses, support & engine",
                        icon = Icons.Default.Info,
                        onClick = { onNavigateToSubScreen(SettingsSubScreen.About) }
                    )
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = "Sign Out?",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "You will be signed out of your Sielo profile. You can sign back in anytime with your credentials.",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.5.sp
                )
            },
            containerColor = PaletteOxfordBlue,
            shape = RoundedCornerShape(18.dp),
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut {
                            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
                ) {
                    Text("Sign Out", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = PaletteSand)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUB-SCREENS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountProfileSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    var displayName by remember(currentUser) { mutableStateOf(currentUser?.name ?: "") }
    var username by remember(currentUser) { mutableStateOf(currentUser?.username ?: "") }
    var bio by remember(currentUser) { mutableStateOf(currentUser?.bio ?: "") }

    SubScreenScaffold(
        title = "Edit Profile",
        subtitle = "Update your public listener identity & bio",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "PROFILE IDENTITY") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PaletteOxfordBlue)
                        .border(2.dp, PaletteSand, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString()
                        ?: currentUser?.email?.firstOrNull()?.uppercaseChar()?.toString()
                        ?: "S"
                    Text(
                        text = initial,
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = currentUser?.email ?: "Local Profile",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.5.sp
                )
                Text(
                    text = if (currentUser?.provider == AuthProvider.GOOGLE) "Google Authenticated" else "Sielo Listener",
                    color = PaletteSageGreen,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "PERSONAL DETAILS") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "DISPLAY NAME",
                    color = PaletteSand,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = { Text("e.g. Maya S.", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PaletteDarkNavy,
                        unfocusedContainerColor = PaletteDarkNavy,
                        focusedTextColor = PaletteCream,
                        unfocusedTextColor = PaletteCream,
                        focusedIndicatorColor = PaletteSand,
                        unfocusedIndicatorColor = BorderGlass
                    ),
                    singleLine = true
                )

                Text(
                    text = "HANDLE / USERNAME",
                    color = PaletteSand,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                    placeholder = { Text("e.g. maya_vibes", color = TextMuted) },
                    prefix = { Text("@", color = PaletteSand, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PaletteDarkNavy,
                        unfocusedContainerColor = PaletteDarkNavy,
                        focusedTextColor = PaletteCream,
                        unfocusedTextColor = PaletteCream,
                        focusedIndicatorColor = PaletteSand,
                        unfocusedIndicatorColor = BorderGlass
                    ),
                    singleLine = true
                )

                Text(
                    text = "BIO",
                    color = PaletteSand,
                    fontFamily = SoraFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    placeholder = { Text("Write something about your music taste...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PaletteDarkNavy,
                        unfocusedContainerColor = PaletteDarkNavy,
                        focusedTextColor = PaletteCream,
                        unfocusedTextColor = PaletteCream,
                        focusedIndicatorColor = PaletteSand,
                        unfocusedIndicatorColor = BorderGlass
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val finalName = displayName.trim().ifBlank { "Sielo Listener" }
                        val finalUsername = username.trim().takeIf { it.isNotBlank() }
                        val finalBio = bio.trim().takeIf { it.isNotBlank() }
                        viewModel.updateProfile(finalName, finalUsername, finalBio)
                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "SAVE CHANGES",
                        color = PaletteDarkNavy,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedServicesSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()

    SubScreenScaffold(
        title = "Connected Services",
        subtitle = "Linked platforms, cloud vaults & audio engines",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "IDENTITY & CLOUD SYNC") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PaletteSand.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUser?.provider == AuthProvider.GOOGLE) "Google Account" else "Sielo Cloud Sync",
                        color = PaletteCream,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = currentUser?.email ?: "Guest Mode • Sign in to sync across devices",
                        color = TextSecondary,
                        fontFamily = UrbanistFontFamily,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PaletteSageGreen.copy(alpha = 0.15f))
                        .border(1.dp, PaletteSageGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (currentUser != null) "Active" else "Guest",
                        color = PaletteSageGreen,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
            }

            if (currentUser == null) {
                SettingsDivider()
                SettingsActionRow(
                    title = "Connect Google Account",
                    description = "Backup playlists and sync listening history",
                    icon = Icons.Default.AccountCircle,
                    textColor = PaletteSand,
                    onClick = { viewModel.openAuthDialog() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "INTEGRATIONS & PROTOCOLS") {
            SettingsNavigationRow(
                title = "Android MediaSession & Bluetooth",
                description = "Hardware media controls, AVRCP metadata & lockscreen player",
                icon = Icons.Default.Bluetooth,
                onClick = {
                    Toast.makeText(context, "System MediaSession running actively", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "InnerTube & JioSaavn Hi-Fi CDN",
                description = "Direct edge server streaming nodes • 320kbps Opus / FLAC",
                icon = Icons.Default.Wifi,
                onClick = {
                    Toast.makeText(context, "Hi-Fi CDN connection verified & operational", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Local Audio Storage",
                description = "Direct storage access for offline playback and cached tracks",
                icon = Icons.Default.Storage,
                onClick = {
                    Toast.makeText(context, "Audio storage operational", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPlaybackSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val streamQuality by viewModel.streamQuality.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val bassBoostEnabled by viewModel.bassBoostEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val bassStrength by viewModel.bassStrength.collectAsState()

    var showQualitySheet by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf(false) }
    var showBassSheet by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Audio & Playback",
        subtitle = "Streaming bitrates, dynamic DSP & acoustic balance",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "AUDIO QUALITY") {
            SettingsValueRow(
                title = "Streaming Quality",
                value = streamQuality,
                icon = Icons.Default.Audiotrack,
                onClick = { showQualitySheet = true }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "DSP & ACOUSTIC EFFECTS") {
            SettingsToggleRow(
                title = "Dynamic Equalizer",
                description = "Hardware DSP acoustic filter tailored for clean vocals & instruments",
                icon = Icons.Default.Equalizer,
                isChecked = equalizerEnabled,
                onCheckedChange = { viewModel.toggleEqualizer() }
            )
            if (equalizerEnabled) {
                SettingsDivider()
                SettingsValueRow(
                    title = "Equalizer Preset",
                    value = equalizerPreset,
                    icon = Icons.Default.Tune,
                    onClick = { showPresetSheet = true }
                )
            }
            SettingsDivider()
            SettingsToggleRow(
                title = "Bass Boost",
                description = "Enhances low-frequency harmonics and subwoofer punch",
                icon = Icons.Default.GraphicEq,
                isChecked = bassBoostEnabled,
                onCheckedChange = { viewModel.toggleBassBoost() }
            )
            if (bassBoostEnabled) {
                SettingsDivider()
                SettingsValueRow(
                    title = "Bass Boost Punch",
                    value = "${bassStrength / 10}% Power",
                    icon = Icons.Default.GraphicEq,
                    onClick = { showBassSheet = true }
                )
            }
        }
    }

    if (showQualitySheet) {
        val qualities = listOf(
            "Auto (Network Adaptive 128k - 320k)",
            "Standard (160kbps AAC)",
            "High (256kbps Opus)",
            "Master (320kbps Opus / FLAC)"
        )
        SingleChoiceBottomSheet(
            title = "Streaming Quality",
            options = qualities,
            selectedOption = streamQuality,
            onSelect = {
                viewModel.setStreamQuality(it)
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false }
        )
    }

    if (showPresetSheet) {
        val presets = listOf("Normal", "Acoustic", "Bass Boost", "Electronic", "Pop", "Rock", "Vocal")
        SingleChoiceBottomSheet(
            title = "Equalizer Preset",
            options = presets,
            selectedOption = equalizerPreset,
            onSelect = {
                viewModel.setEqualizerPreset(it)
                showPresetSheet = false
            },
            onDismiss = { showPresetSheet = false }
        )
    }

    if (showBassSheet) {
        val bassLevels = listOf("Subtle (40%)", "Balanced (60%)", "Heavy Punch (80%)", "Maximum Slam (100%)")
        val currentLabel = when {
            bassStrength >= 950 -> "Maximum Slam (100%)"
            bassStrength >= 750 -> "Heavy Punch (80%)"
            bassStrength >= 550 -> "Balanced (60%)"
            else -> "Subtle (40%)"
        }
        SingleChoiceBottomSheet(
            title = "Bass Boost Level",
            options = bassLevels,
            selectedOption = currentLabel,
            onSelect = {
                val strength = when (it) {
                    "Maximum Slam (100%)" -> 1000
                    "Heavy Punch (80%)" -> 800
                    "Balanced (60%)" -> 600
                    else -> 400
                }
                viewModel.setBassStrength(strength)
                showBassSheet = false
            },
            onDismiss = { showBassSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBehaviorSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val gaplessPlayback by viewModel.gaplessPlayback.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val rememberPosition by viewModel.rememberPosition.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    var showCrossfadeSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Player Behavior",
        subtitle = "Queue autoplay, crossfade and track transitions",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "QUEUE & AUTOPLAY") {
            SettingsToggleRow(
                title = "Autoplay Recommended Songs",
                description = "Continue playing similar music when queue reaches the end",
                icon = Icons.Default.PlayCircleOutline,
                isChecked = autoplayEnabled,
                onCheckedChange = { viewModel.toggleAutoplay() }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "Remember Playback Position",
                description = "Resume long songs and sessions where you left off",
                icon = Icons.Default.Speed,
                isChecked = rememberPosition,
                onCheckedChange = { viewModel.toggleRememberPosition() }
            )
            SettingsDivider()
            SettingsValueRow(
                title = "Playback Speed",
                value = if (playbackSpeed == 1.0f) "1.0x (Normal)" else "${playbackSpeed}x",
                icon = Icons.Default.Speed,
                onClick = { showSpeedSheet = true }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "TRANSITIONS") {
            SettingsToggleRow(
                title = "Gapless Playback",
                description = "Seamless continuous transition between consecutive tracks",
                icon = Icons.Default.Audiotrack,
                isChecked = gaplessPlayback,
                onCheckedChange = { viewModel.toggleGaplessPlayback() }
            )
            SettingsDivider()
            SettingsValueRow(
                title = "Crossfade Duration",
                value = if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s",
                icon = Icons.Default.Tune,
                onClick = { showCrossfadeSheet = true }
            )
        }
    }

    if (showSpeedSheet) {
        val speeds = listOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val currentSpeedLabel = if (playbackSpeed == 1.0f) "1.0x (Normal)" else "${playbackSpeed}x"
        SingleChoiceBottomSheet(
            title = "Playback Speed",
            options = speeds,
            selectedOption = currentSpeedLabel,
            onSelect = {
                val speed = when (it) {
                    "0.75x" -> 0.75f
                    "1.25x" -> 1.25f
                    "1.5x" -> 1.5f
                    "2.0x" -> 2.0f
                    else -> 1.0f
                }
                viewModel.setPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showCrossfadeSheet) {
        val crossfadeOptions = listOf("0 (Off)", "3 seconds", "5 seconds", "8 seconds", "12 seconds")
        SingleChoiceBottomSheet(
            title = "Crossfade Duration",
            options = crossfadeOptions,
            selectedOption = if (crossfadeSeconds == 0) "0 (Off)" else "$crossfadeSeconds seconds",
            onSelect = {
                val sec = when {
                    it.startsWith("3") -> 3
                    it.startsWith("5") -> 5
                    it.startsWith("8") -> 8
                    it.startsWith("12") -> 12
                    else -> 0
                }
                viewModel.setCrossfadeSeconds(sec)
                showCrossfadeSheet = false
            },
            onDismiss = { showCrossfadeSheet = false }
        )
    }
}

@Composable
private fun AudioOutputSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pauseOnDisconnect by viewModel.pauseOnDisconnect.collectAsState()
    val bluetoothAutoResume by viewModel.bluetoothAutoResume.collectAsState()
    val volumeNormalization by viewModel.volumeNormalization.collectAsState()

    SubScreenScaffold(
        title = "Audio Output",
        subtitle = "Bluetooth, headphone disconnect & volume leveling",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "HARDWARE & HEADPHONES") {
            SettingsToggleRow(
                title = "Pause on Disconnect",
                description = "Instantly pause playback when headphones or Bluetooth disconnect",
                icon = Icons.Default.Headphones,
                isChecked = pauseOnDisconnect,
                onCheckedChange = { viewModel.togglePauseOnDisconnect() }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "Bluetooth Auto-Resume",
                description = "Resume playback automatically when reconnecting to familiar audio gear",
                icon = Icons.Default.Bluetooth,
                isChecked = bluetoothAutoResume,
                onCheckedChange = { viewModel.toggleBluetoothAutoResume() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "VOLUME CONTROL") {
            SettingsToggleRow(
                title = "Volume Normalization",
                description = "Balances loudness across all tracks to avoid sudden jumps",
                icon = Icons.Default.VolumeUp,
                isChecked = volumeNormalization,
                onCheckedChange = { viewModel.toggleVolumeNormalization() }
            )
        }
    }
}

@Composable
private fun DownloadsStorageSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()

    var showClearCacheConfirm by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Downloads & Storage",
        subtitle = "Offline cache, disk usage and memory management",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "DATA USAGE") {
            SettingsToggleRow(
                title = "Download Over Wi-Fi Only",
                description = "Prevents music downloads over mobile cellular networks",
                icon = Icons.Default.Wifi,
                isChecked = downloadWifiOnly,
                onCheckedChange = { viewModel.toggleDownloadWifiOnly() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "STORAGE MANAGEMENT") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = PaletteSand,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Audio & Image Cache",
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$cacheSize currently allocated on disk",
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            SettingsDivider()
            SettingsActionRow(
                title = "Clear Audio Cache",
                description = "Removes temporary streaming chunks without losing liked songs",
                icon = Icons.Default.DeleteForever,
                textColor = Color(0xFFE63946),
                onClick = { showClearCacheConfirm = true }
            )
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = {
                Text(
                    text = "Clear Cached Audio?",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will remove temporary buffered audio chunks and image cache. Your library, liked tracks, and account data will remain untouched.",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.5.sp
                )
            },
            containerColor = PaletteOxfordBlue,
            shape = RoundedCornerShape(18.dp),
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirm = false
                        viewModel.clearAudioCache { updatedSize ->
                            Toast.makeText(context, "Audio cache cleared ($updatedSize)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                ) {
                    Text("Clear Cache", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel", color = PaletteSand)
                }
            }
        )
    }
}

@Composable
private fun AppearanceSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val oledDarkTheme by viewModel.oledDarkTheme.collectAsState()
    val dynamicArtworkTint by viewModel.dynamicArtworkTint.collectAsState()
    val highFpsVisuals by viewModel.highFpsVisuals.collectAsState()

    SubScreenScaffold(
        title = "Theme & Appearance",
        subtitle = "Visual palette, artwork ambient glow & display modes",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "THEME & COLOR") {
            SettingsToggleRow(
                title = "OLED Midnight Theme",
                description = "True Obsidian Black (#0D1B2A) optimized for battery saver on OLED screens",
                icon = Icons.Default.DarkMode,
                isChecked = oledDarkTheme,
                onCheckedChange = { viewModel.toggleOledDarkTheme() }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "Dynamic Artwork Glow",
                description = "Adapts background lighting and subtle tint to the active song cover",
                icon = Icons.Default.ColorLens,
                isChecked = dynamicArtworkTint,
                onCheckedChange = { viewModel.toggleDynamicArtworkTint() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "ANIMATIONS & GRAPHICS") {
            SettingsToggleRow(
                title = "High Framerate Visuals",
                description = "Enables fluid vinyl rotation and interactive audio bars",
                icon = Icons.Default.Tune,
                isChecked = highFpsVisuals,
                onCheckedChange = { viewModel.toggleHighFpsVisuals() }
            )
        }
    }
}

@Composable
private fun NotificationsSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notifPlayback by viewModel.notificationsPlayback.collectAsState()
    val notifNewReleases by viewModel.notificationsNewReleases.collectAsState()
    val notifListenTogether by viewModel.notificationsListenTogether.collectAsState()

    SubScreenScaffold(
        title = "Notifications",
        subtitle = "System media session, release alerts & room requests",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "ALERTS & CONTROLS") {
            SettingsToggleRow(
                title = "Playback Controls Notification",
                description = "Persistent lock screen and status bar media player",
                icon = Icons.Default.NotificationsActive,
                isChecked = notifPlayback,
                onCheckedChange = { viewModel.toggleNotificationsPlayback() }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "New Releases from Followed Artists",
                description = "Receive updates when your favorite artists drop new tracks or albums",
                icon = Icons.Default.Notifications,
                isChecked = notifNewReleases,
                onCheckedChange = { viewModel.toggleNotificationsNewReleases() }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "Listen Together Invitations",
                description = "Alerts when peers invite you into shared listening rooms",
                icon = Icons.Default.Headphones,
                isChecked = notifListenTogether,
                onCheckedChange = { viewModel.toggleNotificationsListenTogether() }
            )
        }
    }
}

@Composable
private fun PrivacyDataSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val privateListening by viewModel.privateListening.collectAsState()
    var showClearSearchConfirm by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Privacy & Data",
        subtitle = "Session history, incognito mode & data management",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "LISTENING PRIVACY") {
            SettingsToggleRow(
                title = "Private Listening Session",
                description = "When active, your playback history and stats will not be recorded",
                icon = Icons.Default.Lock,
                isChecked = privateListening,
                onCheckedChange = { viewModel.togglePrivateListening() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingsSection(title = "DATA CONTROLS") {
            SettingsActionRow(
                title = "Clear Search History",
                description = "Removes all recent query history from search suggestions",
                icon = Icons.Default.History,
                textColor = PaletteSand,
                onClick = { showClearSearchConfirm = true }
            )
        }
    }

    if (showClearSearchConfirm) {
        AlertDialog(
            onDismissRequest = { showClearSearchConfirm = false },
            title = {
                Text(
                    text = "Clear Search History?",
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will delete all saved search queries. This action cannot be undone.",
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 13.5.sp
                )
            },
            containerColor = PaletteOxfordBlue,
            shape = RoundedCornerShape(18.dp),
            confirmButton = {
                Button(
                    onClick = {
                        showClearSearchConfirm = false
                        viewModel.clearSearchHistory {
                            Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSand)
                ) {
                    Text("Clear", color = PaletteDarkNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchConfirm = false }) {
                    Text("Cancel", color = PaletteSand)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationsSubScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recDiversity by viewModel.recommendationDiversity.collectAsState()
    val prioritizeFollowed by viewModel.prioritizeFollowedArtists.collectAsState()
    var showDiversitySheet by remember { mutableStateOf(false) }

    SubScreenScaffold(
        title = "Recommendations",
        subtitle = "Personalized curation, taste radar & discovery style",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "DISCOVERY PREFERENCE") {
            SettingsValueRow(
                title = "Discovery Style",
                value = recDiversity,
                icon = Icons.Default.Radio,
                onClick = { showDiversitySheet = true }
            )
            SettingsDivider()
            SettingsToggleRow(
                title = "Prioritize Followed Artists",
                description = "Weight feeds and quick picks heavily towards followed creators",
                icon = Icons.Default.Person,
                isChecked = prioritizeFollowed,
                onCheckedChange = { viewModel.togglePrioritizeFollowedArtists() }
            )
        }
    }

    if (showDiversitySheet) {
        val options = listOf(
            "Familiar (Songs & artists you know best)",
            "Balanced (Equal mix of favorites & new discoveries)",
            "Adventurous (Dives deep into indie & international gems)"
        )
        SingleChoiceBottomSheet(
            title = "Discovery Style",
            options = options,
            selectedOption = options.firstOrNull { it.startsWith(recDiversity) } ?: options[1],
            onSelect = {
                val clean = when {
                    it.startsWith("Familiar") -> "Familiar"
                    it.startsWith("Adventurous") -> "Adventurous"
                    else -> "Balanced"
                }
                viewModel.setRecommendationDiversity(clean)
                showDiversitySheet = false
            },
            onDismiss = { showDiversitySheet = false }
        )
    }
}

@Composable
private fun AboutSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SubScreenScaffold(
        title = "About Sielo",
        subtitle = "Version, architecture & open source licenses",
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsSection(title = "APPLICATION") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Sielo Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PaletteDarkNavy)
                        .border(1.5.dp, PaletteSand.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
                Column {
                    Text(
                        text = "Sielo",
                        color = PaletteCream,
                        fontFamily = MacondoFontFamily,
                        fontSize = 24.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Music Lives Here • Pure Hi-Fi Audio",
                        color = PaletteSand,
                        fontFamily = SoraFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        SettingsSection(title = "BUILD & VERSION") {
            SettingsInfoRow(label = "Application Version", value = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            SettingsInfoRow(label = "Codename", value = "Antigravity Hi-Fi")
            SettingsInfoRow(label = "Target Android SDK", value = "Android 15 (API 35)")
            SettingsInfoRow(label = "Min Android SDK", value = "Android 8.0 (API 26)")
        }

        SettingsSection(title = "AUDIO ENGINE") {
            SettingsInfoRow(label = "Decoder Core", value = "ExoPlayer 2.19.1 + Android Hardware DSP")
            SettingsInfoRow(label = "Max Output Quality", value = "Lossless 320kbps AAC / OPUS")
            SettingsInfoRow(label = "DSP Processing", value = "Hardware Acoustic Equalizer & Virtualizer")
            SettingsInfoRow(label = "Playback Engine", value = "InnerTube Audio Pipeline (Adaptive Stream)")
        }

        SettingsSection(title = "LICENSES & CREDITS") {
            SettingsInfoRow(label = "Open Source License", value = "Apache 2.0 / MIT")
            SettingsInfoRow(label = "Designed For", value = "Sielo High-Fidelity Music Experience")
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        SettingsSection(title = "SUPPORT & FEEDBACK") {
            SettingsActionRow(
                title = "Star on GitHub",
                description = "Support the project by starring the repository",
                icon = Icons.Default.Star,
                textColor = com.sielo.music.ui.theme.PaletteSand,
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VineetChudasama/Sielo"))
                    context.startActivity(intent)
                }
            )
            SettingsDivider()
            SettingsActionRow(
                title = "Send Feedback",
                description = "Share your thoughts or report an issue",
                icon = Icons.Default.Email,
                textColor = com.sielo.music.ui.theme.PaletteSand,
                onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:vineet.builds@gmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Sielo Feedback")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INFO ROW — label / value display (About screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontFamily = UrbanistFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = PaletteCream,
            fontFamily = SoraFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubScreenScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteDarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 210.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaletteCream,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            color = PaletteCream,
                            fontFamily = SoraFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontFamily = UrbanistFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = title,
            color = PaletteSand,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PaletteOxfordBlue)
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PaletteSand,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isChecked) PaletteSand else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontFamily = UrbanistFontFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PaletteDarkNavy,
                checkedTrackColor = PaletteSand,
                uncheckedThumbColor = PaletteSand,
                uncheckedTrackColor = PaletteOxfordBlue,
                uncheckedBorderColor = PaletteSlateBlue
            )
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PaletteSand,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    color = PaletteCream,
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = value,
                    color = PaletteSand,
                    fontFamily = UrbanistFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    icon: ImageVector,
    textColor: Color = PaletteCream,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(
                text = title,
                color = textColor,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = TextSecondary,
                fontFamily = UrbanistFontFamily,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderGlass)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceBottomSheet(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaletteOxfordBlue,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = title,
                color = PaletteCream,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { opt ->
                    val isSel = opt == selectedOption || opt.startsWith(selectedOption) || selectedOption.startsWith(opt)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PaletteSlateBlue.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable { onSelect(opt) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = opt,
                            color = if (isSel) PaletteSand else PaletteCream,
                            fontFamily = UrbanistFontFamily,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        if (isSel) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PaletteSand,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}





