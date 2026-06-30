import XCTest
import Foundation
import Dispatch
import SwiftCheck
@testable import SideQuestKit

// This property targets `SystemNotificationService.requestAuthorizationIfNeeded`
// and the `NotificationCenterAdapting` seam, both of which depend on the iOS-only
// `UserNotifications` framework (`requestAuthorization(options:)` takes a
// `UNAuthorizationOptions`). Mirroring `SystemNotificationService.swift`, the
// whole file is compiled only where that framework exists, so it runs on Apple
// platforms and is omitted on hosts (Windows/Linux) that lack it.
#if canImport(UserNotifications)
import UserNotifications

/// Property-based test for **Property 22 — "A permission prompt is triggered at
/// most once per capability"** (iOS design "iOS-specific properties"; task 13.3).
///
/// **Validates: Requirements 11.1, 11.5**
///
/// Req 11.1:
/// > WHEN a notification feature is first used and iOS notification permission
/// > has not previously been requested, THE App SHALL trigger the iOS system
/// > notification permission prompt exactly once.
///
/// Req 11.5:
/// > IF iOS permission for a given capability has already been granted or
/// > denied, THEN THE App SHALL NOT trigger the iOS system permission prompt for
/// > that capability again.
///
/// Property 22 statement:
/// > For any sequence of permission interactions, once iOS permission for a
/// > capability has been granted or denied, the system permission prompt for
/// > that capability is not triggered again (at most one prompt per capability).
///
/// ## Subject under test
///
/// `SystemNotificationService.requestAuthorizationIfNeeded()` — the single entry
/// point a notifying feature calls. Its guard prompts only when iOS has **not
/// yet determined** a status *and* the app has **not already requested** it,
/// recording the request in a `PermissionRequestStore` so the "at most once"
/// guarantee survives across launches and the main-app/Share-Extension split.
///
/// The "system permission prompt" is exactly the call into
/// `NotificationCenterAdapting.requestAuthorization(options:)`. We substitute:
///   * an in-memory `NotificationCenterAdapting` double that counts every
///     invocation of `requestAuthorization` (the prompt), starts from a
///     generated authorization status, and — like the real system — only
///     *displays* the prompt while the status is `.notDetermined`, after which
///     it resolves to the user's one-time grant/deny decision; and
///   * an in-memory `PermissionRequestStore` double that may start already
///     flagged (modelling a prompt shown in a prior session/process).
///
/// ## Property
///
/// For any starting status, any user decision, any pre-existing "already
/// requested" flag, and **any number of calls** to
/// `requestAuthorizationIfNeeded`, the system prompt
/// (`requestAuthorization(options:)`) is invoked **at most once total**, and is
/// invoked at all *iff* the very first interaction found the capability
/// undetermined and not previously requested. Equivalently, once a status is
/// determined or the prompt has already been requested, no further prompt is
/// triggered.
///
/// ## Async bridge
///
/// `requestAuthorizationIfNeeded()` is `async`; SwiftCheck property bodies are
/// synchronous, so each call is run to completion through the same
/// `runBlocking` helper used by the other async property tests in this suite.
///
/// Each property runs ≥100 iterations (the design mandates a minimum of 100; we
/// configure 200 for extra coverage).
final class PermissionPromptPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Property 22: at most one prompt per capability, across any call sequence

    /// For any scenario, the number of times the system prompt is triggered
    /// equals `1` exactly when the first interaction is undetermined and not
    /// already requested, and `0` otherwise — so it is **never** triggered more
    /// than once and **never** triggered again once a status is determined or
    /// the prompt was already requested (Property 22, Req 11.1 / 11.5).
    func testPromptTriggeredAtMostOncePerCapability() {
        property("permission prompt is triggered at most once per capability (Property 22, Req 11.1/11.5)",
                 arguments: Self.checkArgs)
            <- forAllNoShrink(scenarioGen) { scenario in
                let center = StubNotificationCenter(
                    initialStatus: scenario.initialStatus,
                    resolution: scenario.resolution
                )
                let store = InMemoryPermissionRequestStore(requested: scenario.alreadyRequested)
                let service = SystemNotificationService(center: center, permissionStore: store)

                // Drive an arbitrary-length sequence of "feature first used"
                // interactions. Only the first one can ever find the capability
                // undetermined-and-unrequested.
                for _ in 0..<scenario.callCount {
                    _ = Self.runBlocking { await service.requestAuthorizationIfNeeded() }
                }

                // The prompt may fire only on the first interaction, and only
                // when iOS had not determined a status and we had not asked
                // before. Every other combination must prompt zero times.
                let expectedPrompts = (scenario.initialStatus == .notDetermined
                                       && !scenario.alreadyRequested) ? 1 : 0

                let atMostOnce = center.requestAuthorizationCallCount <= 1
                let exactlyExpected = center.requestAuthorizationCallCount == expectedPrompts
                // The double only "shows" the prompt while undetermined; it can
                // never be shown more than once.
                let displayedAtMostOnce = center.promptsActuallyShown <= 1
                // The store ends up flagged iff it was already flagged or a
                // prompt was issued this session — so a future session also
                // won't prompt again (Req 11.5 persistence).
                let flagConsistent = store.hasRequestedAuthorization()
                    == (scenario.alreadyRequested || expectedPrompts == 1)

                return (atMostOnce <?> "prompt triggered \(center.requestAuthorizationCallCount) times (> 1)")
                    ^&&^ (exactlyExpected
                            <?> ("prompt count \(center.requestAuthorizationCallCount) != expected \(expectedPrompts) "
                                + "(start=\(scenario.initialStatus), alreadyRequested=\(scenario.alreadyRequested), calls=\(scenario.callCount))"))
                    ^&&^ (displayedAtMostOnce <?> "prompt displayed \(center.promptsActuallyShown) times (> 1)")
                    ^&&^ (flagConsistent <?> "permission-requested flag inconsistent after the sequence")
            }
    }

    // MARK: - Property 22: once determined/requested, a follow-up call never prompts

    /// Starting from a state where the capability is *already* determined or the
    /// prompt was *already* requested, no call ever triggers the system prompt —
    /// regardless of how many times the feature is used (Req 11.5).
    func testAlreadyResolvedCapabilityNeverPromptsAgain() {
        property("an already-determined-or-requested capability never prompts again (Property 22, Req 11.5)",
                 arguments: Self.checkArgs)
            <- forAllNoShrink(resolvedScenarioGen) { scenario in
                let center = StubNotificationCenter(
                    initialStatus: scenario.initialStatus,
                    resolution: scenario.resolution
                )
                let store = InMemoryPermissionRequestStore(requested: scenario.alreadyRequested)
                let service = SystemNotificationService(center: center, permissionStore: store)

                for _ in 0..<scenario.callCount {
                    _ = Self.runBlocking { await service.requestAuthorizationIfNeeded() }
                }

                return (center.requestAuthorizationCallCount == 0)
                    <?> ("a resolved/requested capability prompted \(center.requestAuthorizationCallCount) times "
                        + "(start=\(scenario.initialStatus), alreadyRequested=\(scenario.alreadyRequested))")
            }
    }

    // MARK: - Async bridge

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body and returns its result.
    private static func runBlocking<T>(_ operation: @escaping () async -> T) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let box = ResultBox<T>()
        Task {
            box.value = await operation()
            semaphore.signal()
        }
        semaphore.wait()
        return box.value!
    }

    private final class ResultBox<T> {
        var value: T?
    }
}

// MARK: - Test doubles

/// In-memory `NotificationCenterAdapting` double.
///
/// Counts every invocation of `requestAuthorization(options:)` (the system
/// prompt the service triggers) and faithfully models the platform behaviour
/// that the prompt is only *displayed* while the status is `.notDetermined`;
/// afterwards iOS resolves to the existing status without re-prompting. Scheduling
/// methods are no-ops — this property only exercises the permission path.
private final class StubNotificationCenter: NotificationCenterAdapting {

    /// Total times the service asked the system to prompt.
    private(set) var requestAuthorizationCallCount = 0

    /// Times an actual prompt would have been shown (status was undetermined).
    private(set) var promptsActuallyShown = 0

    /// Current modelled authorization status.
    private var status: NotificationAuthStatus

    /// The user's one-time grant/deny decision applied when the prompt is shown.
    private let resolution: NotificationAuthStatus

    init(initialStatus: NotificationAuthStatus, resolution: NotificationAuthStatus) {
        self.status = initialStatus
        self.resolution = resolution
    }

    func authorizationStatus() async -> NotificationAuthStatus {
        status
    }

    func requestAuthorization(options: UNAuthorizationOptions) async -> NotificationAuthStatus {
        requestAuthorizationCallCount += 1
        // The real system prompt only appears while the status is not yet
        // determined; once determined, `requestAuthorization` returns the
        // existing status without showing UI again.
        if status == .notDetermined {
            promptsActuallyShown += 1
            status = resolution
        }
        return status
    }

    func add(_ request: UNNotificationRequest) async {}

    func pendingRequestIdentifiers() async -> [String] { [] }

    func removePendingRequests(withIdentifiers identifiers: [String]) {}
}

/// In-memory `PermissionRequestStore` double. May start already flagged to model
/// a prompt that was shown in a prior launch or in the other process (the App
/// Group-shared flag), which must keep the prompt from firing again (Req 11.5).
private final class InMemoryPermissionRequestStore: PermissionRequestStore {

    private var requested: Bool

    init(requested: Bool = false) {
        self.requested = requested
    }

    func hasRequestedAuthorization() -> Bool {
        requested
    }

    func markAuthorizationRequested() {
        requested = true
    }
}

// MARK: - Scenario + generators

/// One generated permission scenario: where the capability starts, what the
/// user would decide if prompted, whether the prompt was already requested, and
/// how many times the feature is used.
private struct PermissionScenario {
    var initialStatus: NotificationAuthStatus
    var resolution: NotificationAuthStatus
    var alreadyRequested: Bool
    var callCount: Int
}

/// Any of the five authorization statuses can be the starting state.
private let anyStatusGen = Gen<NotificationAuthStatus>.fromElements(of: [
    .notDetermined, .denied, .authorized, .provisional, .ephemeral
])

/// The user's decision when prompted resolves to a *determined* status (the
/// prompt is dismissed with a grant or deny outcome). `.notDetermined` is
/// excluded because dismissing the system prompt always yields a decision.
private let resolutionGen = Gen<NotificationAuthStatus>.fromElements(of: [
    .authorized, .denied, .provisional, .ephemeral
])

/// 1...12 repeated interactions — a non-trivial "sequence of permission
/// interactions" so the at-most-once guarantee is exercised across many calls.
private let callCountGen = Gen<Int>.choose((1, 12))

/// The full scenario space: every starting status, decision, prior-request
/// flag, and call count.
private let scenarioGen: Gen<PermissionScenario> = Gen.compose { c in
    PermissionScenario(
        initialStatus: c.generate(using: anyStatusGen),
        resolution: c.generate(using: resolutionGen),
        alreadyRequested: c.generate(using: boolGen),
        callCount: c.generate(using: callCountGen)
    )
}

/// Either branch of a binary choice, used for the prior-request flag and the
/// determined-vs-undetermined starting state.
private let boolGen = Gen<Bool>.fromElements(of: [true, false])

/// Scenarios in which the capability is **already resolved** — either iOS has a
/// determined status, or the prompt was already requested — so no prompt may
/// ever fire again.
private let resolvedScenarioGen: Gen<PermissionScenario> = Gen.compose { c in
    // Either a determined starting status, or notDetermined-but-already-requested.
    let determinedStart = c.generate(using: Gen<NotificationAuthStatus>.fromElements(of: [
        .denied, .authorized, .provisional, .ephemeral
    ]))
    let useDeterminedStart = c.generate(using: boolGen)
    return PermissionScenario(
        initialStatus: useDeterminedStart ? determinedStart : .notDetermined,
        resolution: c.generate(using: resolutionGen),
        // When the start is undetermined, force already-requested so it is still
        // "resolved" (prompt previously issued); otherwise the flag is free.
        alreadyRequested: useDeterminedStart ? c.generate(using: boolGen) : true,
        callCount: c.generate(using: callCountGen)
    )
}
#endif
