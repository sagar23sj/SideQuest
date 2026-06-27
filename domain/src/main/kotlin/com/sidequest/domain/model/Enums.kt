package com.sidequest.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle status of an [ActionItem]. New items start as [NOT_STARTED].
 */
@Serializable
enum class ActionStatus {
    @SerialName("not_started")
    NOT_STARTED,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,
}

/**
 * Classification of the content a user shared into the app.
 */
@Serializable
enum class ContentType {
    @SerialName("link")
    LINK,

    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    @SerialName("video_ref")
    VIDEO_REF,
}
