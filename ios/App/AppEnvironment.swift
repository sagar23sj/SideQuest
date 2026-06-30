import Foundation
import SwiftUI
import SideQuestKit

// MARK: - App composition root (task 18.1)
//
// `AppEnvironment` is the single place the app's object graph is assembled:
// the shared GRDB store → repositories → backend client / auth → sync →
// notifications. It is created once in `SideQuestApp` and handed to the view
// tree as an `@EnvironmentObject`, so every screen draws its repositories and
// services from the same wired instances (design: "Client Architecture
// (MVVM + Repository)").
//
// Responsibilities pulled together here:
//   * Open the shared App Group SQLite store (the offline-first source of truth,
//     Req 5.1) and build the repositories over it (task 6.1).
//   * Build the REST/JSON `BackendClient`, the Keychain-backed `AuthService`
//     (Req 10.4), the `SyncService` + `SyncCoordinator` (task 16), and the
//     `SystemNotificationService` (task 13).
//   * Register background tasks at launch (Req 6.5) and, on launch, reschedule
//     pending notifications and run the first sync pass (Req 6.6, 7.11).
//
// Permission for notifications is requested on *first use* of a notifying
// feature (Req 11.1) — by the reminder / notification-settings flows — not at
// launch, so launching the app never shows a permission prompt.

/// The wired object graph for the running app. Owns the store, repositories, and
/// services and exposes the launch-time lifecycle hooks.
@MainActor
final class AppEnvironment: ObservableObject {

    // MARK: Store & repositories

    let database: SideQuestDatabase
    let itemRepository: ActionItemRepository
    let bucketRepository: BucketRepository
    let planRepository: ActionPlanRepository
    let bucketManagement: BucketManagementService

    // MARK: Backend / auth / sync

    let backendClient: BackendClient
    let authService: AuthService
    let syncService: SyncService
    let syncCoordinator: SyncCoordinator

    // MARK: Notifications

    let notificationService: NotificationService
    let notificationPreferences: NotificationPreferencesStore

    // MARK: Enrichment

    let llmService: LLMService

    /// A non-fatal store-setup problem to surface in the UI (e.g. the App Group
    /// container could not be resolved — Req 13.7). `nil` when the shared store
    /// opened normally.
    @Published private(set) var storeWarning: String?

    /// Builds the full graph. The shared App Group store is opened first; if it
    /// cannot be resolved the app falls back to a local store so it still runs
    /// (degraded — Share Extension captures won't be visible), recording a
    /// ``storeWarning`` rather than crashing.
    init() {
        // 1. Local store — the offline-first source of truth (Req 5.1, 4.10).
        var warning: String?
        let database: SideQuestDatabase
        do {
            database = try SideQuestDatabase.openShared()
        } catch {
            warning = "The shared storage area is unavailable, so items shared into SideQuest may not appear. (\(error))"
            database = AppEnvironment.makeFallbackStore()
        }
        self.database = database
        self.storeWarning = warning

        // 2. Repositories over the store (task 6.1).
        let itemRepository = ActionItemRepository(database: database)
        let bucketRepository = BucketRepository(database: database)
        let planRepository = ActionPlanRepository(database: database)
        self.itemRepository = itemRepository
        self.bucketRepository = bucketRepository
        self.planRepository = planRepository
        self.bucketManagement = BucketManagementService(
            buckets: bucketRepository,
            items: itemRepository
        )

        // 3. Backend client (REST/JSON over HTTPS, Req 2.1).
        let transport = URLSessionHTTPTransport(baseURL: AppEnvironment.backendBaseURL())
        let backendClient = BackendClient(transport: transport)
        self.backendClient = backendClient

        // 4. Auth with Keychain token storage (Req 10.4).
        let authService = AuthService(
            transport: BackendAuthTransport(client: backendClient),
            tokenStore: KeychainTokenStore()
        )
        self.authService = authService

        // 5. Sync — pushes/pulls ActionItems, account derived from the token
        //    (Req 6, 10.2); driven by the coordinator's launch/foreground/
        //    connectivity/background triggers (task 16.6).
        let syncService = SyncService(
            backend: backendClient,
            store: itemRepository,
            authorizer: authService
        )
        self.syncService = syncService
        self.syncCoordinator = SyncCoordinator(sync: syncService)

        // 6. Notifications anchored to local wall-clock time (Req 7).
        self.notificationService = SystemNotificationService()
        self.notificationPreferences = UserDefaultsNotificationPreferencesStore()

        // 7. LLM proxy for notification text, authenticated with the current
        //    bearer token (Req 7.16); fails soft to default text.
        self.llmService = ProxyLLMService(
            client: backendClient,
            authTokenProvider: { [authService] in try? await authService.accessToken() }
        )
    }

    // MARK: - Launch lifecycle

    /// Registers the `BGTaskScheduler` launch handlers (Req 6.5).
    ///
    /// MUST be called during launch (from `SideQuestApp.init`) — the OS requires
    /// background-task handlers to be registered before the app finishes
    /// launching, and every identifier must be declared in `Info.plist`
    /// (Req 13.3). Delegates to ``SyncCoordinator/registerBackgroundTasks()``.
    func registerBackgroundTasks() {
        syncCoordinator.registerBackgroundTasks()
    }

    /// Starts runtime triggers and reconciles scheduled work once the UI is up.
    ///
    /// - Begins connectivity / foreground sync observation and queues the first
    ///   background refresh (Req 6.4, 6.6).
    /// - Reschedules pending notifications on launch (Req 7.11): asks the
    ///   notification service to reconcile its pending requests and re-applies
    ///   the user's evening-nudge / global-daily preferences against the current
    ///   items, so a reboot or relaunch never drops a still-wanted schedule.
    /// - Kicks off an initial sync pass so foreground data is fresh.
    func start() async {
        syncCoordinator.start()

        await notificationService.rescheduleAllPending()
        await reapplyNotificationPreferences()

        // An initial foreground sync; transient failures are retried by the
        // coordinator's later triggers (Req 6.9), so failure here is non-fatal.
        try? await syncService.sync()
    }

    /// Re-applies the persisted evening-nudge and global-daily preferences to the
    /// notification service using the current items (Req 7.11, 7.12, 7.15). Used
    /// on launch and after the item set changes.
    func reapplyNotificationPreferences() async {
        let applier = NotificationPreferenceApplier(service: notificationService)
        let items = (try? itemRepository.fetchAll()) ?? []
        await applier.applyEveningNudge(notificationPreferences.eveningNudge(), items: items)
        await applier.applyGlobalDaily(notificationPreferences.globalDaily())
    }

    // MARK: - Configuration

    /// The backend base URL. Read from the `BackendBaseURL` Info.plist key so it
    /// can vary per build configuration, falling back to the production host.
    private static func backendBaseURL() -> URL {
        if let string = Bundle.main.object(forInfoDictionaryKey: "BackendBaseURL") as? String,
           let url = URL(string: string) {
            return url
        }
        return URL(string: "https://api.sidequest.app")!
    }

    /// A private, non-shared store used only when the App Group container can't
    /// be opened, so the app still launches. Captures from the Share Extension
    /// won't be visible in this degraded mode (surfaced via ``storeWarning``).
    private static func makeFallbackStore() -> SideQuestDatabase {
        let directory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first ?? FileManager.default.temporaryDirectory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let path = directory.appendingPathComponent("SideQuest-local.sqlite").path
        // Force-try is acceptable here: this is the last-resort path taken only
        // when the shared store already failed; a local file in Application
        // Support is openable on any device.
        return try! SideQuestDatabase(path: path)
    }
}
