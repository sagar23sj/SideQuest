package com.actiontracker.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.actiontracker.ui.board.BoardScreen
import com.actiontracker.ui.bucket.BucketManagementScreen
import com.actiontracker.ui.bucket.CreateBucketScreen
import com.actiontracker.ui.detail.ItemDetailScreen
import com.actiontracker.ui.games.GamesHubScreen
import com.actiontracker.ui.games.SpellingBeeScreen
import com.actiontracker.ui.games.WordGuessScreen
import com.actiontracker.ui.leaderboard.LeaderboardScreen
import com.actiontracker.ui.profile.ProfileScreen
import com.actiontracker.ui.reminder.ReminderSettingsScreen
import com.actiontracker.ui.voice.VoiceJournalScreen
import com.actiontracker.ui.voice.VoiceReviewScreen

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
@Composable
fun ActionTrackerNavHost(
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar + FAB only show on the top-level tabs; pushed routes use
    // their own top-bar back affordance and hide the shell.
    val showShell = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showShell,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                BottomNavBar(navController = navController)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showShell,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                CaptureFab(onClick = onAddItem)
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
                    onOpenReminderSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
                    onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                    onManageBuckets = { navController.navigate(Routes.BUCKETS) },
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
                    onOpenReminders = { navController.navigate(Routes.REMINDER_SETTINGS) },
                    onManageBuckets = { navController.navigate(Routes.BUCKETS) },
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

            composable(Routes.LOGIN) {
                com.actiontracker.ui.auth.LoginScreen(
                    onAuthenticated = navController::popBackStack,
                    onSkip = navController::popBackStack,
                )
            }

            composable(Routes.JOIN_ORG) {
                com.actiontracker.ui.auth.JoinOrganizationScreen(
                    onJoined = navController::popBackStack,
                    onSkip = navController::popBackStack,
                )
            }
        }
    }
}

/**
 * The four-tab bottom navigation bar. Tab selection uses the standard
 * single-top + restore-state pattern so switching tabs keeps each tab's back
 * stack and avoids piling duplicate destinations.
 */
@Composable
private fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

/** The center capture FAB: a 20dp squircle, matching the Home Board design. */
@Composable
private fun CaptureFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(com.actiontracker.R.string.nav_capture_desc),
            )
        }
    }
}
