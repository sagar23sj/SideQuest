package com.sidequest.ui.reminder

/**
 * State for the one-time, first-launch notification-permission request
 * (Req 6.1), exposed as a single immutable value from
 * [NotificationPermissionViewModel].
 *
 * The app root reads [shouldRequestPermission] to decide whether to launch the
 * runtime POST_NOTIFICATIONS request exactly once on first launch, and
 * [permissionGranted] to reflect the latest known grant state.
 *
 * @property shouldRequestPermission whether the first-launch permission request
 *   still needs to be made — true only when the app has not requested it before
 *   and the permission is not already granted (Req 6.1).
 * @property permissionGranted the latest known grant state of the OS
 *   notification permission.
 */
data class NotificationPermissionUiState(
    val shouldRequestPermission: Boolean = false,
    val permissionGranted: Boolean = false,
)
