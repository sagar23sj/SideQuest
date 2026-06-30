import Foundation

/// The `BGTaskScheduler` task identifiers SideQuest_iOS registers for
/// background synchronization (task 16.6, Req 6.5).
///
/// This type is the **single source of truth** for those identifiers in code,
/// mirroring the role ``AppGroup`` plays for the App Group identifier. The same
/// strings MUST also be declared in the main app's `Info.plist` under
/// `BGTaskSchedulerPermittedIdentifiers`; the OS rejects a `register` /
/// `submit` call for any identifier that is not listed there, and distribution
/// validation requires every registered identifier to match a declared one
/// (Req 13.3, 6.5). Keeping the identifiers here means the registration code in
/// ``SyncCoordinator`` and the `Info.plist` declaration cannot drift apart.
///
/// Two identifiers are used because the design splits background sync into a
/// short app-refresh pass and a longer catch-up processing pass (design:
/// "`BGAppRefreshTask` for short syncs, `BGProcessingTask` for longer catch-up
/// syncs", Req 6.5).
public enum SyncBackgroundTasks {

    /// Identifier for the short, frequent ``BGAppRefreshTask`` that runs a
    /// quick sync pass while the app is backgrounded.
    public static let appRefreshIdentifier = "com.sidequest.sync.refresh"

    /// Identifier for the longer ``BGProcessingTask`` used for catch-up syncs
    /// (e.g. after extended offline periods), which the system may schedule when
    /// the device is on power.
    public static let processingIdentifier = "com.sidequest.sync.processing"

    /// All registered identifiers, in declaration order. Used by
    /// ``SyncCoordinator/registerBackgroundTasks()`` and mirrored by the
    /// `BGTaskSchedulerPermittedIdentifiers` array in `Info.plist`.
    public static let allIdentifiers: [String] = [
        appRefreshIdentifier,
        processingIdentifier
    ]
}
