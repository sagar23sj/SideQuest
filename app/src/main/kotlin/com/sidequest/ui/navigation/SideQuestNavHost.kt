package com.sidequest.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sidequest.R
import com.sidequest.ui.board.BoardScreen
import com.sidequest.ui.bucket.BucketManagementScreen
import com.sidequest.ui.bucket.CreateBucketScreen
import com.sidequest.ui.detail.ItemDetailScreen
import com.sidequest.ui.games.GamesHubScreen
import com.sidequest.ui.games.SpellingBeeScreen
import com.sidequest.ui.games.WordGuessScreen
import com.sidequest.ui.leaderboard.LeaderboardScreen
import com.sidequest.ui.profile.ProfileScreen
import com.sidequest.ui.reminder.ReminderSettingsScreen
import com.sidequest.ui.voice.VoiceJournalScreen
import com.sidequest.ui.voice.VoiceReviewScreen

/**
 * Hosts the SideQuest navigation shell: a four-tab bottom bar (Board, Games,
 * Voice, Profile) with a center capture FAB, plus the pushed routes layered on
 * top (item detail, bucket management, create/edit bucket, voice review, the
 * games, the leaderboard, reminder settings, and the auth flow).
 *
 * This replaces the previous two-state `RootScreen` enum in `MainActivity` with
 * a real Navigation-Compose graph so every built and newly added screen is
 * reachable.
 *
 * @param onAddItem invoked by the capture FAB. The OS share sheet remains the
 *   primary external capture path; this gives an in-app entry point that reuses
 *   the same categorization flow (hosted by the share-target activity).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SideQuestNavHost(
    onAddTask: (bucketId: String?) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar shows on every top-level tab; the capture FAB is scoped to
    // the Board only (the bucket detail screen hosts its own per-bucket FAB).
    val showShell = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // The host contributes no insets of its own; each destination's own
        // Scaffold/TopAppBar consumes the status-bar inset exactly once, so the
        // page content sits flush at the top of the screen (no double gap).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showShell,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                BottomNavBar(
                    navController = navController,
                    onCapture = { onAddTask(null) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.BOARD) {
                BoardScreen(
                    onAddTask = { onAddTask(null) },
                    onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                    onManageBuckets = { navController.navigate(Routes.BUCKETS) },
                    onOpenBucket = { bucketId -> navController.navigate(Routes.bucketDetail(bucketId)) },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                    onOpenStats = {
                        // Switch to the Insights tab (same options as the bottom
                        // bar) so the tab stays in sync and the back stack is
                        // consistent.
                        navController.navigate(Routes.STATS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(Routes.GAMES) {
                GamesHubScreen(
                    onPlaySpellingBee = { navController.navigate(Routes.SPELLING_BEE) },
                    onPlayWordGuess = { navController.navigate(Routes.WORD_GUESS) },
                    onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                )
            }

            composable(Routes.VOICE) {
                VoiceJournalScreen(
                    onReviewEntry = { entryId -> navController.navigate(Routes.voiceReview(entryId)) },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onNavigateBack = navController::popBackStack,
                    onOpenReminders = { navController.navigate(Routes.REMINDER_SETTINGS) },
                    onManageBuckets = { navController.navigate(Routes.BUCKETS) },
                    onCreateBucket = { navController.navigate(Routes.CREATE_BUCKET) },
                    onJoinOrganization = { navController.navigate(Routes.JOIN_ORG) },
                    onSignIn = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(
                route = Routes.ITEM_DETAIL_PATTERN,
                arguments = listOf(navArgument(Routes.ITEM_DETAIL_ARG) { type = NavType.StringType }),
            ) {
                ItemDetailScreen(onNavigateBack = navController::popBackStack)
            }

            composable(Routes.BUCKETS) {
                BucketManagementScreen(
                    onNavigateBack = navController::popBackStack,
                    onCreateBucket = { navController.navigate(Routes.CREATE_BUCKET) },
                    onEditBucket = { bucketId -> navController.navigate(Routes.editBucket(bucketId)) },
                )
            }

            composable(
                route = Routes.BUCKET_DETAIL_PATTERN,
                arguments = listOf(navArgument(Routes.BUCKET_DETAIL_ARG) { type = NavType.StringType }),
            ) {
                com.sidequest.ui.bucket.BucketDetailScreen(
                    onNavigateBack = navController::popBackStack,
                    onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                    onAddTask = { bucketId -> onAddTask(bucketId) },
                )
            }

            composable(Routes.CREATE_BUCKET) {
                CreateBucketScreen(onNavigateBack = navController::popBackStack)
            }

            composable(
                route = Routes.EDIT_BUCKET_PATTERN,
                arguments = listOf(navArgument(Routes.EDIT_BUCKET_ARG) { type = NavType.StringType }),
            ) {
                CreateBucketScreen(onNavigateBack = navController::popBackStack)
            }

            composable(
                route = Routes.VOICE_REVIEW_PATTERN,
                arguments = listOf(navArgument(Routes.VOICE_REVIEW_ARG) { type = NavType.StringType }),
            ) {
                VoiceReviewScreen(onNavigateBack = navController::popBackStack)
            }

            composable(Routes.REMINDER_SETTINGS) {
                ReminderSettingsScreen(onNavigateBack = navController::popBackStack)
            }

            composable(Routes.SPELLING_BEE) {
                SpellingBeeScreen(onNavigateBack = navController::popBackStack)
            }

            composable(Routes.WORD_GUESS) {
                WordGuessScreen(onNavigateBack = navController::popBackStack)
            }

            composable(Routes.LEADERBOARD) {
                LeaderboardScreen(
                    onNavigateBack = navController::popBackStack,
                    onJoinOrganization = { navController.navigate(Routes.JOIN_ORG) },
                )
            }

            composable(Routes.STATS) {
                // Insights tab — no back arrow (it's a top-level tab).
                com.sidequest.ui.stats.StatsScreen()
            }

            composable(Routes.LOGIN) {
                com.sidequest.ui.auth.LoginScreen(
                    onAuthenticated = navController::popBackStack,
                    onSkip = navController::popBackStack,
                )
            }

            composable(Routes.JOIN_ORG) {
                com.sidequest.ui.auth.JoinOrganizationScreen(
                    onJoined = navController::popBackStack,
                    onSkip = navController::popBackStack,
                )
            }
        }
    }
}

/**
 * The four-tab bottom navigation bar, styled to the SideQuest design: a
 * floating rounded `surface-container` bar where the active tab is a filled
 * pill (`secondary-container`). Tab selection uses the standard single-top +
 * restore-state pattern so switching tabs keeps each tab's back stack and
 * avoids piling duplicate destinations.
 */
@Composable
private fun BottomNavBar(
    navController: NavHostController,
    onCapture: () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tabs = TopLevelDestination.visible()
    val mid = tabs.size / 2
    val leftTabs = tabs.take(mid)
    val rightTabs = tabs.drop(mid)

    fun onTabClick(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftTabs.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    BottomNavItem(destination = destination, selected = selected, onClick = { onTabClick(destination) })
                }
            }

            CaptureButton(onClick = onCapture)

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rightTabs.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    BottomNavItem(destination = destination, selected = selected, onClick = { onTabClick(destination) })
                }
            }
        }
    }
}

/**
 * The center capture action: a raised, primary-colored circular "+" — the
 * highest-frequency action in the app (add a quest), placed where the thumb
 * naturally rests.
 */
@Composable
private fun CaptureButton(onClick: () -> Unit) {
    val desc = stringResource(R.string.nav_capture_desc)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = desc,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * A single bottom-nav tab. The active tab renders as a filled
 * `secondary-container` pill with its label; inactive tabs show just the
 * outlined icon, matching the design's pill active-state.
 */
@Composable
private fun BottomNavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(destination.labelRes)
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = label,
                tint = content,
            )
            if (selected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
            }
        }
    }
}
