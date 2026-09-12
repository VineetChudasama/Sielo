package com.sielo.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import com.sielo.music.ui.navigation.Screen
import com.sielo.music.ui.screens.HomeScreen
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

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SieloTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val playbackState by playerViewModel.playbackState.collectAsState()
                var isPlayerExpanded by remember { mutableStateOf(false) }

                // System back press handles closing full-screen player first
                BackHandler(enabled = isPlayerExpanded) {
                    isPlayerExpanded = false
                }

                // If player is not expanded, back press falls back to previous tab/screen sequentially
                BackHandler(enabled = !isPlayerExpanded && navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }

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
                            composable(Screen.Stats.route) {
                                StatsScreen(viewModel = statsViewModel, modifier = Modifier.fillMaxSize())
                            }
                            composable(Screen.Profile.route) {
                                ProfileScreen(viewModel = profileViewModel, modifier = Modifier.fillMaxSize())
                            }
                        }

                        // Floating Persistent Mini-Player Island (Docked above bottom bar)
                        if (playbackState.currentTrack != null && !isPlayerExpanded) {
                            MiniPlayerIsland(
                                playbackState = playbackState,
                                onTogglePlay = { playerViewModel.togglePlayPause() },
                                onToggleMute = { playerViewModel.toggleMute() },
                                onSkipPrevious = { playerViewModel.skipPrevious() },
                                onSkipNext = { playerViewModel.skipNext() },
                                onSeek = { playerViewModel.seekTo(it) },
                                onClick = { isPlayerExpanded = true },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            )
                        }

                        // Full-Screen Hi-Fi Vinyl & Lyrics Player Modal
                        AnimatedVisibility(
                            visible = isPlayerExpanded,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            PlayerScreen(
                                viewModel = playerViewModel,
                                onClose = { isPlayerExpanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
