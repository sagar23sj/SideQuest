package com.sidequest.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import com.sidequest.R

/**
 * A bottom-navigation tab in the app shell (Board / Games / Voice / Profile),
 * matching the SideQuest Home Board design. The center "+" capture action is
 * intentionally *not* a tab — it is a FAB rendered separately in
 * [SideQuestScaffold] — so this enum holds only the four destination tabs.
 *
 * @property route the start route for the tab; see [Routes].
 * @property labelRes the tab label string resource.
 * @property selectedIcon the filled icon shown when the tab is active.
 * @property unselectedIcon the outlined icon shown when the tab is inactive.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BOARD(
        route = Routes.BOARD,
        labelRes = R.string.nav_board,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
    ),
    GAMES(
        route = Routes.GAMES,
        labelRes = R.string.nav_games,
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports,
    ),
    VOICE(
        route = Routes.VOICE,
        labelRes = R.string.nav_voice,
        selectedIcon = Icons.Filled.Mic,
        unselectedIcon = Icons.Outlined.Mic,
    ),
    PROFILE(
        route = Routes.PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    ),
    ;

    companion object {
        /**
         * The tabs currently surfaced in the bottom bar. Board and Profile are
         * always shown; Games and Voice appear only when their feature flags are
         * enabled (hidden during the planner-focused rollout).
         */
        fun visible(): List<TopLevelDestination> = entries.filter { destination ->
            when (destination) {
                GAMES -> com.sidequest.FeatureFlags.GAMES_ENABLED
                VOICE -> com.sidequest.FeatureFlags.VOICE_JOURNAL_ENABLED
                else -> true
            }
        }
    }
}
