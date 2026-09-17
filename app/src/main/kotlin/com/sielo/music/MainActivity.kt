package com.sielo.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.sielo.music.ui.screens.StatsScreen
import com.sielo.music.ui.theme.ObsidianBlack
import com.sielo.music.ui.theme.SieloTheme
import com.sielo.music.viewmodel.HomeViewModel
import com.sielo.music.viewmodel.PlayerViewModel
import com.sielo.music.viewmodel.ProfileViewModel
import com.sielo.music.viewmodel.SearchViewModel
import com.sielo.music.viewmodel.StatsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var userManager: com.sielo.music.core.auth.UserManager
    @javax.inject.Inject lateinit var playerManager: com.sielo.music.core.audio.PlayerManager

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val listenTogetherViewModel: com.sielo.music.viewmodel.ListenTogetherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val isInsideRoomScreen = currentRoute == Screen.ListenTogether.route && activeRoomState != null
                var isPlayerExpanded by remember { mutableStateOf(false) }
                var showLaunchReveal by remember { mutableStateOf(true) }

                val currentUser by userManager.currentUser.collectAsState()
                val isAuthDialogOpen by userManager.isAuthDialogOpen.collectAsState()
                val isOnboardingOpen by userManager.isOnboardingOpen.collectAsState()

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

                val intentData = intent?.data
                androidx.compose.runtime.LaunchedEffect(intentData) {
                    if (intentData != null) {
                        val uriStr = intentData.toString()
                        if (uriStr.contains("sielo://room") || uriStr.contains("sielo://join")) {
                            listenTogetherViewModel.joinRoom(uriStr, "", listenTogetherViewModel.getSavedUserName())
                            navController.navigate(Screen.ListenTogether.route)
                        }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(currentUser, isOnboardingOpen) {
                    if (currentUser == null && !isOnboardingOpen) {
                        userManager.openAuthDialog()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                    containerColor = ObsidianBlack,
                    bottomBar = {
                        SieloBottomBar(
                            navController = navController,
                            onTabSelected = {
                                if (isPlayerExpanded) {
                                    isPlayerExpanded = false
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Multi-Tab Navigation Shell
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    viewModel = homeViewModel,
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
                                    viewModel = listenTogetherViewModel
                                )
                            }
                            composable(Screen.Stats.route) {
                                StatsScreen(viewModel = statsViewModel, modifier = Modifier.fillMaxSize())
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(viewModel = profileViewModel, modifier = Modifier.fillMaxSize())
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
                                .padding(bottom = 12.dp)
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
                    }
                }

                if (showLaunchReveal) {
                    SieloLaunchReveal(
                        onFinish = { showLaunchReveal = false }
                    )
                }

                if (isAuthDialogOpen) {
                    com.sielo.music.ui.screens.AuthDialog(
                        userManager = userManager,
                        onDismiss = { userManager.closeAuthDialog() }
                    )
                }

                if (isOnboardingOpen) {
                    com.sielo.music.ui.screens.NewUserOnboardingScreen(
                        userManager = userManager,
                        onFinished = {
                            userManager.closeOnboarding()
                            homeViewModel.refreshHome()
                        }
                    )
                }

                // Popup to directly rejoin room if user left abruptly
                if (pendingRejoinSession != null && activeRoomState == null && !isAuthDialogOpen && !isOnboardingOpen) {
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
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        listenTogetherViewModel.checkPendingRejoinSession()
    }
}
