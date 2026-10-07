package com.sielo.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sielo.music.ui.components.MiniPlayerIsland
import com.sielo.music.ui.components.SieloBottomBar
import com.sielo.music.ui.components.SieloLaunchReveal
import com.sielo.music.ui.navigation.Screen
import com.sielo.music.ui.screens.HomeScreen
import com.sielo.music.ui.screens.ListenTogetherScreen
import com.sielo.music.ui.screens.PlayerScreen
import com.sielo.music.ui.screens.ProfileScreen
import com.sielo.music.ui.screens.SearchScreen
import com.sielo.music.ui.screens.SettingsScreen
import com.sielo.music.ui.screens.StatsScreen
import com.sielo.music.ui.screens.UserPlaylistsScreen
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.SieloTheme
import com.sielo.music.viewmodel.HomeViewModel
import com.sielo.music.viewmodel.PlayerViewModel
import com.sielo.music.viewmodel.ProfileViewModel
import com.sielo.music.viewmodel.SearchViewModel
import com.sielo.music.viewmodel.SettingsViewModel
import com.sielo.music.viewmodel.StatsViewModel
import com.sielo.music.viewmodel.UserPlaylistsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var userManager: com.sielo.music.core.auth.UserManager
    @javax.inject.Inject lateinit var playerManager: com.sielo.music.core.audio.PlayerManager
    @javax.inject.Inject lateinit var innerTubeClient: com.sielo.music.core.network.innertube.InnerTubeClient
    @javax.inject.Inject lateinit var appUpdateManager: com.sielo.music.core.update.AppUpdateManager

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Notification permission for foreground media playback session */ }

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val userPlaylistsViewModel: UserPlaylistsViewModel by viewModels()
    private val playlistImportViewModel: com.sielo.music.viewmodel.PlaylistImportViewModel by viewModels()
    private val listenTogetherViewModel: com.sielo.music.viewmodel.ListenTogetherViewModel by viewModels()

    private val pendingDeepLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var lastHandledDeepLink: String? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { uriStr ->
            if (uriStr != lastHandledDeepLink) {
                pendingDeepLink.value = uriStr
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent?.data?.toString()?.let { uriStr ->
                pendingDeepLink.value = uriStr
            }
        }
        appUpdateManager.checkForUpdates()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Connect player recommendation engine with dynamic user taste learning
        playerManager.onTrackPlayedTasteListener = { artist, genre ->
            userManager.recordSongPlayed(artist, genre)
        }
        playerManager.userTasteSeedsProvider = {
            userManager.getTopTasteArtists() + userManager.getFavoriteArtists()
        }

        setContent {
            SieloTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val playbackState by playerViewModel.playbackState.collectAsState()
                val activeRoomState by listenTogetherViewModel.roomState.collectAsState()
                val pendingRejoinSession by listenTogetherViewModel.pendingRejoinSession.collectAsState()
                val availableUpdateVersion by appUpdateManager.availableUpdateVersion.collectAsState()
                val isInsideRoomScreen = currentRoute == Screen.ListenTogether.route && activeRoomState != null
                var isPlayerExpanded by remember { mutableStateOf(false) }
                var showLaunchReveal by remember { mutableStateOf(true) }
                var showFeedbackPopup by remember { mutableStateOf(false) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (settingsViewModel.shouldShowFeedbackPopup()) {
                        showFeedbackPopup = true
                    }
                }

                val currentUser by userManager.currentUser.collectAsState()
                val isAuthDialogOpen by userManager.isAuthDialogOpen.collectAsState()
                val isOnboardingOpen by userManager.isOnboardingOpen.collectAsState()

                // If player is expanded, back gesture collapses the player immediately
                BackHandler(enabled = isPlayerExpanded) {
                    isPlayerExpanded = false
                }

                // If player is not expanded, back press falls back to previous tab/screen, or returns to Home tab
                BackHandler(enabled = !isPlayerExpanded && currentRoute != Screen.Home.route) {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                val deepLinkUrl by pendingDeepLink.collectAsState()
                androidx.compose.runtime.LaunchedEffect(deepLinkUrl, showLaunchReveal) {
                    if (!showLaunchReveal) {
                        val uriStr = deepLinkUrl
                        if (!uriStr.isNullOrBlank()) {
                            // Immediately clear pending event to avoid re-triggering across recompositions
                            pendingDeepLink.value = null
                            if (uriStr != lastHandledDeepLink) {
                                lastHandledDeepLink = uriStr
                                try {
                                    val uri = android.net.Uri.parse(uriStr)
                                    val scheme = uri.scheme?.lowercase().orEmpty()
                                    val host = uri.host?.lowercase().orEmpty()
                                    val isSieloHost = host == "sielo-music.vercel.app"
                                    val isSieloCustomScheme = scheme == "sielo"
                                    val isLegacyHost = host == "sielo.app" || host == "vineetchudasama.github.io"

                                    if (isSieloHost || isSieloCustomScheme || isLegacyHost) {
                                        val pathSegments = uri.pathSegments ?: emptyList()
                                        val firstSegment = pathSegments.getOrNull(0)?.lowercase().orEmpty()
                                        val secondSegment = pathSegments.getOrNull(1)

                                        when {
                                            // 1. Track / Song Deep Link: /song/{songId} or /track/{songId}
                                            firstSegment == "song" || firstSegment == "track" || (uri.getQueryParameter("id") != null && uriStr.contains("track")) -> {
                                                val songId = secondSegment?.takeIf { it.isNotBlank() }
                                                    ?: uri.getQueryParameter("id")
                                                val trackTitle = uri.getQueryParameter("title")
                                                val trackArtist = uri.getQueryParameter("artist")

                                                if (!songId.isNullOrBlank()) {
                                                    if (!trackTitle.isNullOrBlank() && !trackArtist.isNullOrBlank()) {
                                                        val track = com.sielo.music.core.network.models.SieloTrack(
                                                            id = songId,
                                                            title = trackTitle,
                                                            artist = trackArtist
                                                        )
                                                        playerManager.playTrack(track, listOf(track))
                                                    }
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val resolved = innerTubeClient.resolveTrackById(songId)
                                                            if (resolved != null) {
                                                                if (trackTitle.isNullOrBlank() || trackArtist.isNullOrBlank()) {
                                                                    playerManager.playTrack(resolved, listOf(resolved))
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("MainActivity", "Failed to resolve deep-link track $songId: ${e.message}")
                                                        }
                                                    }
                                                }
                                            }

                                            // 2. Album Deep Link: /album/{albumId}
                                            firstSegment == "album" || uriStr.contains("album") -> {
                                                val albumId = secondSegment?.takeIf { it.isNotBlank() }
                                                    ?: uri.getQueryParameter("id")
                                                val albumArtist = uri.getQueryParameter("artist")

                                                navController.navigate(Screen.Home.route) {
                                                    launchSingleTop = true
                                                }

                                                if (!albumArtist.isNullOrBlank()) {
                                                    homeViewModel.openArtist(albumArtist)
                                                } else if (!albumId.isNullOrBlank()) {
                                                    lifecycleScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val tracks = innerTubeClient.getAlbumSongs(albumId)
                                                            if (tracks.isNotEmpty()) {
                                                                val artist = tracks.firstOrNull()?.artist
                                                                if (!artist.isNullOrBlank()) {
                                                                    launch(Dispatchers.Main) {
                                                                        homeViewModel.openArtist(artist)
                                                                    }
                                                                } else {
                                                                    playerManager.playTrack(tracks.first(), tracks)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("MainActivity", "Failed to resolve deep-link album $albumId: ${e.message}")
                                                        }
                                                    }
                                                }
                                            }

                                            // 3. Listen Together Deep Link: /listen/{roomId} or /room/{roomId} or /join/{roomId}
                                            firstSegment == "listen" || firstSegment == "room" || firstSegment == "join" || uriStr.contains("listen") || uriStr.contains("room") -> {
                                                val roomKey = uri.getQueryParameter("key").orEmpty()
                                                val success = listenTogetherViewModel.joinRoom(uriStr, roomKey, listenTogetherViewModel.getSavedUserName())
                                                if (success) {
                                                    navController.navigate(Screen.ListenTogether.route) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }

                                            // 4. Artist Deep Link: /artist/{name} or ?name=
                                            firstSegment == "artist" || uriStr.contains("artist") -> {
                                                val artistName = uri.getQueryParameter("name")
                                                    ?: secondSegment
                                                    ?: uriStr.substringAfter("artist/").substringBefore("?").replace("+", " ")
                                                if (!artistName.isNullOrBlank()) {
                                                    navController.navigate(Screen.Home.route) {
                                                        launchSingleTop = true
                                                    }
                                                    homeViewModel.openArtist(artistName.replace("+", " "))
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Error handling deep link: ${e.message}")
                                }
                            }
                        }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(currentUser, isOnboardingOpen) {
                    if (currentUser == null && !isOnboardingOpen) {
                        userManager.openAuthDialog()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBlack)
                ) {
                    val isBottomBarVisible = currentRoute != Screen.Settings.route && currentRoute != Screen.UserPlaylists.route

                    // Multi-Tab Navigation Shell (Content extends smoothly behind floating bottom bar)
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                viewModel = homeViewModel,
                                importViewModel = playlistImportViewModel,
                                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Search.route) {
                            SearchScreen(viewModel = searchViewModel, modifier = Modifier.fillMaxSize())
                        }
                        composable(Screen.ListenTogether.route) {
                            ListenTogetherScreen(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = listenTogetherViewModel,
                                onNavigateBack = { navController.navigate(Screen.Home.route) }
                            )
                        }
                        composable(Screen.Stats.route) {
                            StatsScreen(viewModel = statsViewModel, modifier = Modifier.fillMaxSize())
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                viewModel = profileViewModel,
                                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                                onOpenPlaylists = { navController.navigate(Screen.UserPlaylists.route) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.UserPlaylists.route) {
                            UserPlaylistsScreen(viewModel = userPlaylistsViewModel, importViewModel = playlistImportViewModel,
                                onBack = { navController.popBackStack() },
                                onPlayTrack = { track, queue -> profileViewModel.playTrack(track, queue) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Floating Persistent Mini-Player Island (Docked above bottom bar)
                    AnimatedVisibility(
                        visible = playbackState.currentTrack != null && !isPlayerExpanded && !isInsideRoomScreen,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(280)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (isBottomBarVisible) 84.dp else 8.dp)
                    ) {
                        MiniPlayerIsland(
                            playbackState = playbackState,
                            onTogglePlay = { playerViewModel.togglePlayPause() },
                            onToggleMute = { playerViewModel.toggleMute() },
                            onSkipPrevious = { playerViewModel.skipPrevious() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onShuffle = { playerViewModel.shuffleQueue() },
                            onClick = { isPlayerExpanded = true }
                        )
                    }

                    // Floating SieloBottomBar (Floats organically over content without dark rectangle box)
                    AnimatedVisibility(
                        visible = isBottomBarVisible && !isInsideRoomScreen,
                        enter = fadeIn(animationSpec = tween(250)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        SieloBottomBar(
                            navController = navController,
                            onTabSelected = {
                                if (isPlayerExpanded) {
                                    isPlayerExpanded = false
                                }
                            }
                        )
                    }

                    // Full-Screen Hi-Fi Vinyl & Lyrics Player Modal
                    AnimatedVisibility(
                        visible = isPlayerExpanded,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(280)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(250))
                    ) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            onClose = { isPlayerExpanded = false }
                        )
                    }

                    if (showLaunchReveal) {
                        SieloLaunchReveal(
                            onFinish = { showLaunchReveal = false }
                        )
                    }

                    if (isAuthDialogOpen && !showLaunchReveal) {
                        com.sielo.music.ui.screens.AuthDialog(
                            userManager = userManager,
                            onDismiss = { userManager.closeAuthDialog() }
                        )
                    }

                    if (isOnboardingOpen && !showLaunchReveal) {
                        com.sielo.music.ui.screens.NewUserOnboardingScreen(
                            userManager = userManager,
                            innerTubeClient = innerTubeClient,
                            onFinished = {
                                userManager.closeOnboarding()
                                homeViewModel.refreshHome()
                            }
                        )
                    }

                    // Popup to directly rejoin room if user left abruptly (shown only after Sielo opening animation finishes)
                    if (pendingRejoinSession != null && activeRoomState == null && !showLaunchReveal && !isAuthDialogOpen && !isOnboardingOpen) {
                        com.sielo.music.ui.screens.RejoinRoomDialog(
                            session = pendingRejoinSession!!,
                            onRejoin = {
                                listenTogetherViewModel.rejoinPreviousRoom()
                                navController.navigate(Screen.ListenTogether.route) {
                                    launchSingleTop = true
                                }
                            },
                            onDismiss = {
                                listenTogetherViewModel.dismissRejoinPrompt()
                            }
                        )
                    }
                    // App update available popup (throttled once every 24hr, with Remind Me Later and Update Now)
                    availableUpdateVersion?.let { newVer ->
                        if (!showLaunchReveal && !isAuthDialogOpen && !isOnboardingOpen) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            com.sielo.music.ui.components.AppUpdateDialog(
                                newVersion = newVer,
                                onRemindLater = { appUpdateManager.remindMeLater() },
                                onUpdateNow = { appUpdateManager.updateNow(context) }
                            )
                        }
                    }

                    // Feedback popup
                    if (showFeedbackPopup && !showLaunchReveal && !isAuthDialogOpen && !isOnboardingOpen && pendingRejoinSession == null && availableUpdateVersion == null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        com.sielo.music.ui.screens.FeedbackPopupDialog(
                            onDismiss = { neverShowAgain ->
                                showFeedbackPopup = false
                                settingsViewModel.recordFeedbackPopupShown(neverShowAgain)
                            },
                            onStarClicked = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VineetChudasama/Sielo"))
                                context.startActivity(intent)
                            },
                            onFeedbackClicked = {
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
        }
    }

    override fun onResume() {
        super.onResume()
        listenTogetherViewModel.checkPendingRejoinSession()
        playerManager.reloadCurrentTrackArtwork()
    }

    override fun onPause() {
        super.onPause()
        playerManager.saveCurrentPlaybackPosition(forceSync = true)
    }

    override fun onStop() {
        super.onStop()
        playerManager.saveCurrentPlaybackPosition(forceSync = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.saveCurrentPlaybackPosition(forceSync = true)
    }
}






