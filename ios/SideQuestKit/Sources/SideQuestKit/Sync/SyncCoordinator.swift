import Foundation

// MARK: - SyncCoordinator (task 16.6 — sync triggers + background registration)
//
// `SyncService` (task 16.1) is a portable `actor` that runs a single push/pull
// pass on demand and is fully unit-testable on any host. *When* a pass runs —
// connectivity restore, foreground entry, and the OS-scheduled background tasks
// — is platform integration that depends on `BackgroundTasks`, `Network`, and
// `UIKit`, none of which exist on the Windows/Linux dev box. So, exactly like
// `SystemNotificationService` (`UserNotifications`) and
// `LinkPresentationPreviewService` (`LinkPresentation`), this whole file is
// compiled only where those frameworks are available; the testable sync logic
// stays in `SyncService.swift`.
//
// Triggers implemented here (all run `SyncService.sync()`):
//   * Connectivity restore — an `NWPathMonitor` fires a pass on the
//     unsatisfied → satisfied transition (Req 6.4).
//   * Foreground entry — `UIApplication.willEnterForegroundNotification`
//     fires a pass (Req 6.6).
//   * Background — registered `BGTaskScheduler` tasks (a short `BGAppRefreshTask`
//     and a longer `BGProcessingTask`) run a pass while backgrounded; their
//     identifiers come from `SyncBackgroundTasks` and are declared in
//     `Info.plist` under `BGTaskSchedulerPermittedIdentifiers` (Req 6.5, 13.3).
//
// The first-sign-in full pull is exposed here too so the app composition
// (task 18.1) can run it once after sign-in and surface the all-or-nothing
// failure message (Req 6.7, 6.10).

#if canImport(BackgroundTasks) && canImport(Network)

import BackgroundTasks
import Network

#if canImport(UIKit)
import UIKit
#endif

/// Schedules and triggers ``SyncService`` passes from the app runtime: on
/// connectivity restore, on foreground entry, and from `BGTaskScheduler`
/// background tasks (task 16.6, Req 6.4, 6.5, 6.6).
///
/// Lifecycle: call ``registerBackgroundTasks()`` **before** the app finishes
/// launching (the OS requires task handlers to be registered at launch), then
/// call ``start()`` to begin connectivity/foreground observation and queue the
/// first background refresh.
public final class SyncCoordinator {

    private let sync: SyncService
    private let monitor: NWPathMonitor
    private let monitorQueue = DispatchQueue(label: "com.sidequest.sync.connectivity")

    /// Tracks the previous reachability so a pass fires only on the
    /// unsatisfied → satisfied *transition* (connectivity "restored after being
    /// unavailable", Req 6.4) rather than on every path update.
    private var wasReachable = false

    /// Retained foreground-notification observer token (block-based), removed on
    /// deinit.
    private var foregroundObserver: NSObjectProtocol?

    /// - Parameter sync: the portable sync engine this coordinator drives.
    public init(sync: SyncService) {
        self.sync = sync
        self.monitor = NWPathMonitor()
    }

    deinit {
        monitor.cancel()
        if let foregroundObserver {
            NotificationCenter.default.removeObserver(foregroundObserver)
        }
    }

    // MARK: - Background task registration (Req 6.5)

    /// Registers the `BGTaskScheduler` launch handlers for every identifier in
    /// ``SyncBackgroundTasks`` (Req 6.5). MUST be called before the app finishes
    /// launching; each identifier must also appear in `Info.plist` under
    /// `BGTaskSchedulerPermittedIdentifiers`, otherwise the OS rejects the
    /// registration (Req 13.3).
    public func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: SyncBackgroundTasks.appRefreshIdentifier,
            using: nil
        ) { [weak self] task in
            self?.handleAppRefresh(task)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: SyncBackgroundTasks.processingIdentifier,
            using: nil
        ) { [weak self] task in
            self?.handleProcessing(task)
        }
    }

    // MARK: - Start observing runtime triggers

    /// Begins connectivity monitoring and foreground observation, and queues the
    /// first background refresh. Idempotent enough to call once at app start.
    public func start() {
        startConnectivityMonitoring()
        startForegroundObserving()
        scheduleAppRefresh()
    }

    // MARK: - First sign-in full pull (Req 6.7, 6.10)

    /// Runs the all-or-nothing first-sign-in pull once on this device
    /// (Req 6.7). Re-throws on failure so the caller can show the
    /// "data could not be retrieved" message; nothing is imported and the next
    /// ordinary pass retries (Req 6.10). Delegates to
    /// ``SyncService/fullPullForFirstSignIn()``.
    public func performFirstSignInPull() async throws {
        try await sync.fullPullForFirstSignIn()
    }

    // MARK: - Connectivity restore (Req 6.4)

    private func startConnectivityMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let reachable = path.status == .satisfied
            let restored = reachable && !self.wasReachable
            self.wasReachable = reachable
            if restored {
                // Connectivity came back after being unavailable: sync the
                // changes marked for synchronization (Req 6.4).
                self.triggerPass()
            }
        }
        monitor.start(queue: monitorQueue)
    }

    // MARK: - Foreground entry (Req 6.6)

    private func startForegroundObserving() {
        #if canImport(UIKit)
        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.triggerPass()
        }
        #endif
    }

    // MARK: - Running a pass

    /// Fires a detached sync pass. Transient failures are non-fatal for these
    /// triggers (the retained dirty set is retried on the next pass, Req 6.9),
    /// so errors are swallowed here; user-facing failures are handled by the
    /// flows that need them (e.g. first sign-in, ``performFirstSignInPull()``).
    private func triggerPass() {
        Task { [sync] in
            try? await sync.sync()
        }
    }

    // MARK: - BGTaskScheduler handlers + scheduling (Req 6.5)

    private func handleAppRefresh(_ task: BGTask) {
        // Queue the next refresh before doing work, so the chain continues even
        // if this run is killed.
        scheduleAppRefresh()
        run(task)
    }

    private func handleProcessing(_ task: BGTask) {
        scheduleProcessing()
        run(task)
    }

    /// Runs a sync pass for a background `task`, wiring the OS expiration handler
    /// to cancel the work and reporting completion success/failure.
    private func run(_ task: BGTask) {
        let work = Task { [sync] () -> Bool in
            do {
                try await sync.sync()
                return true
            } catch {
                return false
            }
        }
        task.expirationHandler = {
            work.cancel()
        }
        Task {
            let success = await work.value
            task.setTaskCompleted(success: success)
        }
    }

    /// Submits a `BGAppRefreshTaskRequest` for the short background sync. Failure
    /// to submit (e.g. simulator, or the OS declining) is non-fatal — a
    /// foreground or connectivity pass will still sync (Req 6.4, 6.6).
    private func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: SyncBackgroundTasks.appRefreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Submits a `BGProcessingTaskRequest` for the longer catch-up sync, which
    /// the OS may run when the device has network and is on power.
    private func scheduleProcessing() {
        let request = BGProcessingTaskRequest(identifier: SyncBackgroundTasks.processingIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        try? BGTaskScheduler.shared.submit(request)
    }
}

#endif
