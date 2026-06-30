import Foundation

/// Lifecycle status of an `ActionItem`. New items start as `notStarted`.
///
/// The raw values are the contract's snake_case strings (`backend/api/openapi.yaml`
/// → `ActionStatus`: `not_started | in_progress | completed`) and match the
/// Android client's `@SerialName` values, so the status round-trips identically
/// across platforms.
public enum ActionStatus: String, Codable, CaseIterable {
    case notStarted = "not_started"
    case inProgress = "in_progress"
    case completed = "completed"
}

/// Classification of the content a user shared into the app.
///
/// The raw values are the contract's snake_case strings (`backend/api/openapi.yaml`
/// → `ContentType`: `link | text | image | video_ref`) and match the Android
/// client's `@SerialName` values.
public enum ContentType: String, Codable, CaseIterable {
    case link
    case text
    case image
    case videoRef = "video_ref"
}
