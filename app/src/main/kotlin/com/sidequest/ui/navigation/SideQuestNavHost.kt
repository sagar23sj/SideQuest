package com.sidequest.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
 * Hosts the SideQuest navigation graph. The shell is intentionally minimal: the
 * Board is the full-screen home (no bottom bar), capture is a top-bar "New
 * Quest" action, Profile opens from the top-left avatar, and Insights/stats open
 * from the board's progress hero. Everything else (item detail, buckets,
 * create/edit bucket, voice review, games, leaderboard, reminder settings, auth)
 * is pushed on top with a back affordance.
 *
 * @param onAddTask invoked to start a capture (manual "New Quest"); the OS share
 *   sheet remains the primary external capture path.
 * @param deepLinkItemId when non-null (a reminder-notification tap), the graph
 *   opens that task's detail page once.
 */
@Composable
fun SideQuestNavHost(
    onAddTask: (bucketId: String?) -> Unit,
    modifier: Modifier = Modifier,
    deepLinkItemId: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // Deep-link from a reminder notification: open the task's detail page once,
    // then clear so it doesn't re-navigate on recomposition (Req 6.5).
    LaunchedEffect(deepLinkItemId) {
        val itemId = deepLinkItemId ?: return@LaunchedEffect
        navController.navigate(Routes.itemDetail(itemId))
        onDeepLinkHandled()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.BOARD,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.BOARD) {
            BoardScreen(
                onAddTask = { onAddTask(null) },
                onOpenItem = { itemId -> navController.navigate(Routes.itemDetail(itemId)) },
                onManageBuckets = { navController.navigate(Routes.BUCKETS) },
                onOpenBucket = { bucketId -> navController.navigate(Routes.bucketDetail(bucketId)) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                onOpenStats = { navController.navigate(Routes.STATS) },
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
            com.sidequest.ui.stats.StatsScreen(
                onNavigateBack = navController::popBackStack,
            )
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
