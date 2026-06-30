import Foundation

// MARK: - DistributionConfig (task 18.2 — App Store distribution + submission validation)
//
// This is the portable, host-testable model of SideQuest_iOS's App Store
// distribution declarations and the pure function that validates them
// (Req 13.1–13.7, 11.2). It deliberately imports only `Foundation` so it
// builds and runs on the Windows/Linux dev box (no Xcode), exactly like the
// rest of the shared domain layer.
//
// Why this lives in code as well as in the build config:
//   * The actual declarations the OS and App Store check live in the build
//     config — `App/Info.plist`, `App/SideQuest_iOS.entitlements`,
//     `ShareExtension/Info.plist`, `ShareExtension/ShareExtension.entitlements`,
//     and `project.yml`.
//   * But Req 13.6/13.7 require the *app* to be able to detect a missing
//     usage-description or a missing/mismatched entitlement / App Group / App ID
//     declaration and fail submission validation, *naming the offending
//     declaration*. That check has to be expressible as ordinary, testable code.
//
// So this file does two things:
//   1. Encodes the canonical *expectations* — the App ID, the shared App Group
//      (`AppGroup.identifier`), the BGTask identifiers the app registers
//      (`SyncBackgroundTasks.allIdentifiers`), the entitlements/capabilities
//      required for notifications + background processing + App Group, and the
//      runtime-prompted permissions that iOS governs with usage-description
//      strings.
//   2. Provides ``DistributionConfig/validateForSubmission(_:)`` — a pure
//      function that compares a ``DistributionManifest`` (the declarations as
//      actually configured in the build) against those expectations and returns
//      every offending declaration.
//
// Task 18.3 reads the real Info.plist / entitlements at test time, builds a
// ``DistributionManifest`` from them, and asserts ``validateForSubmission(_:)``
// passes — closing the loop between this code and the shipped config.

// MARK: - Runtime permission

/// A permission the app requests at runtime that iOS governs with an
/// Info.plist *usage-description* string (Req 11.2, 13.5).
///
/// iOS refuses the runtime prompt — and App Store review rejects the binary —
/// when the corresponding `Info.plist` key is missing or empty. The
/// description must also name SideQuest and identify the specific feature that
/// needs the permission (Req 11.2).
public struct RuntimePermission: Hashable, Sendable {

    /// The `Info.plist` key iOS reads for this permission's purpose string,
    /// e.g. `"NSPhotoLibraryUsageDescription"`.
    public let infoPlistKey: String

    /// Human-readable name of the feature that triggers the prompt, used to
    /// build a clear validation message naming the offending declaration.
    public let featureName: String

    public init(infoPlistKey: String, featureName: String) {
        self.infoPlistKey = infoPlistKey
        self.featureName = featureName
    }
}

// MARK: - Target declarations

/// The distribution-relevant declarations of a single build target, as
/// configured in that target's `Info.plist` and `.entitlements`.
///
/// One value of this type describes the main app; another describes the Share
/// Extension. ``DistributionConfig/validateForSubmission(_:)`` checks them
/// against the canonical expectations.
public struct TargetDeclarations: Sendable, Equatable {

    /// The target's bundle identifier (`PRODUCT_BUNDLE_IDENTIFIER`). This is the
    /// App ID portion the provisioning profile must match (Req 13.7).
    public var bundleIdentifier: String

    /// `com.apple.security.application-groups` entitlement values. For SideQuest
    /// this must contain ``AppGroup/identifier`` and be identical across the two
    /// targets (Req 13.2).
    public var appGroups: [String]

    /// The set of entitlement keys declared in the target's `.entitlements`
    /// (e.g. `aps-environment`, `keychain-access-groups`,
    /// `com.apple.security.application-groups`). Used to check the
    /// notifications / background / App Group capabilities are present
    /// (Req 13.4).
    public var entitlementKeys: Set<String>

    /// `UIBackgroundModes` from the target's `Info.plist` (e.g. `fetch`,
    /// `processing`) — the background-processing capability (Req 13.4).
    public var backgroundModes: [String]

    /// `BGTaskSchedulerPermittedIdentifiers` declared in the target's
    /// `Info.plist` (Req 13.3). Every identifier the app *registers* must appear
    /// here, and none declared here may be left unregistered.
    public var permittedBackgroundTaskIdentifiers: [String]

    /// Map of `NS…UsageDescription` key → its string value, as declared in the
    /// target's `Info.plist` (Req 11.2, 13.5). A missing key has no entry; an
    /// empty/whitespace value is treated as missing by validation.
    public var usageDescriptions: [String: String]

    /// Bundle identifiers of app extensions embedded in (associated with) this
    /// target. For the main app this must include the Share Extension
    /// (Req 13.2). Empty for the extension itself.
    public var associatedAppExtensionBundleIDs: [String]

    public init(
        bundleIdentifier: String,
        appGroups: [String] = [],
        entitlementKeys: Set<String> = [],
        backgroundModes: [String] = [],
        permittedBackgroundTaskIdentifiers: [String] = [],
        usageDescriptions: [String: String] = [:],
        associatedAppExtensionBundleIDs: [String] = []
    ) {
        self.bundleIdentifier = bundleIdentifier
        self.appGroups = appGroups
        self.entitlementKeys = entitlementKeys
        self.backgroundModes = backgroundModes
        self.permittedBackgroundTaskIdentifiers = permittedBackgroundTaskIdentifiers
        self.usageDescriptions = usageDescriptions
        self.associatedAppExtensionBundleIDs = associatedAppExtensionBundleIDs
    }
}

// MARK: - Distribution manifest

/// The full set of distribution declarations for both targets, plus the
/// background-task identifiers the app actually *registers* at runtime and the
/// runtime-prompted permissions iOS governs with usage descriptions.
///
/// This is the input to ``DistributionConfig/validateForSubmission(_:)``. In
/// production ``DistributionManifest/shipped`` mirrors the real build config; in
/// tests (task 18.3) it is built by reading the actual `Info.plist` /
/// `.entitlements`, or with injected faults to prove each failure is detected.
public struct DistributionManifest: Sendable, Equatable {

    /// The main app's declarations.
    public var app: TargetDeclarations

    /// The Share Extension's declarations.
    public var shareExtension: TargetDeclarations

    /// The `BGTaskScheduler` identifiers the app registers at launch (the
    /// single source of truth is ``SyncBackgroundTasks/allIdentifiers``, mirrored
    /// here so validation can compare *registered* vs *declared*; Req 13.3).
    public var registeredBackgroundTaskIdentifiers: [String]

    public init(
        app: TargetDeclarations,
        shareExtension: TargetDeclarations,
        registeredBackgroundTaskIdentifiers: [String]
    ) {
        self.app = app
        self.shareExtension = shareExtension
        self.registeredBackgroundTaskIdentifiers = registeredBackgroundTaskIdentifiers
    }
}

// MARK: - Validation issues

/// A single offending declaration found by submission validation. Every case
/// carries enough context to name the specific declaration at fault, satisfying
/// "the submission result indicating the missing/mismatched declaration"
/// (Req 13.6, 13.7).
public enum SubmissionValidationIssue: Equatable, Sendable {

    /// A runtime-prompted permission has no usage-description key at all
    /// (Req 13.6).
    case missingUsageDescription(key: String, feature: String)

    /// A runtime-prompted permission's usage-description string is present but
    /// empty/whitespace (Req 13.6 — a blank string is not a usage description).
    case emptyUsageDescription(key: String, feature: String)

    /// A usage-description string is present and non-empty but does not name
    /// SideQuest, violating Req 11.2 ("names SideQuest and identifies the
    /// specific feature").
    case usageDescriptionMissingAppName(key: String, feature: String)

    /// A target's App ID (bundle identifier) is missing or does not match the
    /// expected value (Req 13.7).
    case appIDMismatch(target: String, expected: String, actual: String)

    /// A target does not declare the shared App Group at all (Req 13.2).
    case missingAppGroup(target: String, expected: String)

    /// The App Group declared by the app and the Share Extension are not
    /// identical (Req 13.2).
    case appGroupMismatch(app: [String], shareExtension: [String])

    /// The main app does not list the Share Extension as an associated app
    /// extension (Req 13.2).
    case missingAssociatedExtension(expected: String)

    /// A required entitlement/capability key is absent from a target's
    /// entitlements (Req 13.4).
    case missingEntitlement(target: String, key: String)

    /// A required `UIBackgroundModes` value is absent (background-processing
    /// capability, Req 13.4).
    case missingBackgroundMode(mode: String)

    /// The app registers a `BGTaskScheduler` identifier that is not declared in
    /// `BGTaskSchedulerPermittedIdentifiers` — the OS would reject it (Req 13.3).
    case undeclaredRegisteredIdentifier(String)

    /// `BGTaskSchedulerPermittedIdentifiers` declares an identifier the app
    /// never registers — Req 13.3 forbids leaving a declared identifier
    /// unregistered.
    case unregisteredDeclaredIdentifier(String)

    /// A human-readable description of the offending declaration, suitable for
    /// the submission-failure result (Req 13.6, 13.7).
    public var offendingDeclaration: String {
        switch self {
        case let .missingUsageDescription(key, feature):
            return "Missing usage-description '\(key)' for runtime permission used by \(feature)."
        case let .emptyUsageDescription(key, feature):
            return "Empty usage-description '\(key)' for runtime permission used by \(feature)."
        case let .usageDescriptionMissingAppName(key, feature):
            return "Usage-description '\(key)' (\(feature)) must name SideQuest."
        case let .appIDMismatch(target, expected, actual):
            let shown = actual.isEmpty ? "<missing>" : actual
            return "\(target) App ID '\(shown)' does not match expected '\(expected)'."
        case let .missingAppGroup(target, expected):
            return "\(target) does not declare the shared App Group '\(expected)'."
        case let .appGroupMismatch(app, ext):
            return "App Group mismatch between app \(app) and Share Extension \(ext); they must be identical."
        case let .missingAssociatedExtension(expected):
            return "Main app does not declare the Share Extension '\(expected)' as an associated app extension."
        case let .missingEntitlement(target, key):
            return "\(target) is missing required entitlement/capability '\(key)'."
        case let .missingBackgroundMode(mode):
            return "Main app is missing required UIBackgroundModes value '\(mode)'."
        case let .undeclaredRegisteredIdentifier(id):
            return "BGTaskScheduler identifier '\(id)' is registered but not declared in BGTaskSchedulerPermittedIdentifiers."
        case let .unregisteredDeclaredIdentifier(id):
            return "BGTaskSchedulerPermittedIdentifiers declares '\(id)' but the app never registers it."
        }
    }
}

// MARK: - Validation result

/// The outcome of submission validation: either the configuration is ready to
/// submit, or it carries the list of offending declarations that fail it
/// (Req 13.6, 13.7).
public enum SubmissionValidationResult: Equatable, Sendable {

    /// All distribution declarations are present and consistent.
    case passed

    /// At least one declaration is missing or mismatched. The associated value
    /// lists every offending declaration found (non-empty).
    case failed([SubmissionValidationIssue])

    /// `true` only for ``passed``.
    public var isValid: Bool {
        if case .passed = self { return true }
        return false
    }

    /// The offending declarations, or an empty array when valid.
    public var issues: [SubmissionValidationIssue] {
        if case let .failed(issues) = self { return issues }
        return []
    }
}

// MARK: - DistributionConfig

/// Canonical App Store distribution expectations for SideQuest_iOS plus the
/// pure submission-validation function (task 18.2, Req 11.2, 13.1–13.7).
public enum DistributionConfig {

    // MARK: Identifiers (Req 13.2, 13.7)

    /// The main app's App ID (bundle identifier). MUST match `project.yml`'s
    /// `PRODUCT_BUNDLE_IDENTIFIER` for the `SideQuest_iOS` target and the
    /// distribution provisioning profile (Req 13.7).
    public static let appBundleID = "com.sidequest"

    /// The Share Extension's bundle identifier — a child of the app's App ID and
    /// the associated app extension the app embeds (Req 13.2).
    public static let shareExtensionBundleID = "com.sidequest.ShareExtension"

    /// The shared App Group, sourced from the single code constant so it cannot
    /// drift from the runtime container lookup (Req 13.2).
    public static let appGroupIdentifier = AppGroup.identifier

    // MARK: Required entitlement / capability keys (Req 13.4)

    /// App Group entitlement key (App Group capability).
    public static let appGroupEntitlementKey = "com.apple.security.application-groups"

    /// Push-notification capability key. Local reminders need no entitlement,
    /// but the notification feature set includes push (Req 7), whose capability
    /// is `aps-environment`.
    public static let pushNotificationEntitlementKey = "aps-environment"

    /// Shared Keychain access-group entitlement key — lets the app and the Share
    /// Extension read the same auth tokens (Req 10.4); part of the capability
    /// surface validated for submission.
    public static let keychainAccessGroupEntitlementKey = "keychain-access-groups"

    /// `UIBackgroundModes` values required for background synchronization
    /// (Req 6.5, 13.4): a short app-refresh pass and a longer processing pass.
    public static let requiredBackgroundModes = ["fetch", "processing"]

    // MARK: Runtime-prompted permissions (Req 11.2, 13.5)

    /// Permissions the app requests at runtime that iOS governs with a
    /// usage-description string. SideQuest captures links, text, **images, and
    /// video references**; selecting existing photos/videos to turn into action
    /// items reads the photo library, which iOS gates behind
    /// `NSPhotoLibraryUsageDescription`. (Notification authorization is also
    /// requested at runtime but iOS presents its own system prompt with no
    /// Info.plist usage-description string, so it is not listed here.)
    public static let runtimePermissions: [RuntimePermission] = [
        RuntimePermission(
            infoPlistKey: "NSPhotoLibraryUsageDescription",
            featureName: "capturing photos and videos as SideQuest action items"
        )
    ]

    /// The `BGTaskScheduler` identifiers the app registers — the single source
    /// of truth is ``SyncBackgroundTasks`` (Req 6.5, 13.3).
    public static var registeredBackgroundTaskIdentifiers: [String] {
        SyncBackgroundTasks.allIdentifiers
    }

    // MARK: - Validation (Req 13.6, 13.7)

    /// Validates a ``DistributionManifest`` against the canonical distribution
    /// expectations and returns every offending declaration (Req 13.6, 13.7).
    ///
    /// Fails when:
    ///   * a runtime-prompted permission has a missing/empty usage-description,
    ///     or one that does not name SideQuest (Req 11.2, 13.5, 13.6);
    ///   * a target's App ID is missing or mismatched (Req 13.7);
    ///   * the shared App Group is missing from either target, or the two
    ///     targets' App Groups are not identical (Req 13.2, 13.7);
    ///   * the app does not list the Share Extension as an associated extension
    ///     (Req 13.2);
    ///   * a required notifications / background-processing / App Group
    ///     entitlement or background mode is absent (Req 13.4);
    ///   * a registered `BGTaskScheduler` identifier is not declared, or a
    ///     declared identifier is never registered (Req 13.3).
    ///
    /// - Returns: ``SubmissionValidationResult/passed`` when every declaration is
    ///   present and consistent, otherwise ``SubmissionValidationResult/failed(_:)``
    ///   with one issue per offending declaration.
    public static func validateForSubmission(_ manifest: DistributionManifest) -> SubmissionValidationResult {
        var issues: [SubmissionValidationIssue] = []

        issues.append(contentsOf: validateAppIDs(manifest))
        issues.append(contentsOf: validateAppGroup(manifest))
        issues.append(contentsOf: validateAssociatedExtension(manifest))
        issues.append(contentsOf: validateEntitlements(manifest))
        issues.append(contentsOf: validateBackgroundModes(manifest))
        issues.append(contentsOf: validateBackgroundTaskIdentifiers(manifest))
        issues.append(contentsOf: validateUsageDescriptions(manifest))

        return issues.isEmpty ? .passed : .failed(issues)
    }

    /// Convenience that validates the shipped configuration
    /// (``DistributionManifest/shipped``). The app composition (task 18.1) can
    /// call this to assert the build is submission-ready.
    public static func validateShippedConfiguration() -> SubmissionValidationResult {
        validateForSubmission(.shipped)
    }

    // MARK: - Individual checks

    private static func validateAppIDs(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        var issues: [SubmissionValidationIssue] = []
        if manifest.app.bundleIdentifier != appBundleID {
            issues.append(.appIDMismatch(
                target: "Main app",
                expected: appBundleID,
                actual: manifest.app.bundleIdentifier
            ))
        }
        if manifest.shareExtension.bundleIdentifier != shareExtensionBundleID {
            issues.append(.appIDMismatch(
                target: "Share Extension",
                expected: shareExtensionBundleID,
                actual: manifest.shareExtension.bundleIdentifier
            ))
        }
        return issues
    }

    private static func validateAppGroup(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        var issues: [SubmissionValidationIssue] = []

        if !manifest.app.appGroups.contains(appGroupIdentifier) {
            issues.append(.missingAppGroup(target: "Main app", expected: appGroupIdentifier))
        }
        if !manifest.shareExtension.appGroups.contains(appGroupIdentifier) {
            issues.append(.missingAppGroup(target: "Share Extension", expected: appGroupIdentifier))
        }

        // Req 13.2: the identifier declared in the Share Extension must be
        // identical to the one declared in the main app. Compare as sets so
        // ordering is irrelevant but any difference in membership is caught.
        if Set(manifest.app.appGroups) != Set(manifest.shareExtension.appGroups) {
            issues.append(.appGroupMismatch(
                app: manifest.app.appGroups,
                shareExtension: manifest.shareExtension.appGroups
            ))
        }
        return issues
    }

    private static func validateAssociatedExtension(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        if manifest.app.associatedAppExtensionBundleIDs.contains(shareExtensionBundleID) {
            return []
        }
        return [.missingAssociatedExtension(expected: shareExtensionBundleID)]
    }

    private static func validateEntitlements(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        var issues: [SubmissionValidationIssue] = []

        // Main app: App Group + push notifications + shared keychain (Req 13.4).
        let requiredAppEntitlements = [
            appGroupEntitlementKey,
            pushNotificationEntitlementKey,
            keychainAccessGroupEntitlementKey
        ]
        for key in requiredAppEntitlements where !manifest.app.entitlementKeys.contains(key) {
            issues.append(.missingEntitlement(target: "Main app", key: key))
        }

        // Share Extension: App Group + shared keychain (it neither pushes nor
        // schedules background tasks, but it shares the store and tokens).
        let requiredExtensionEntitlements = [
            appGroupEntitlementKey,
            keychainAccessGroupEntitlementKey
        ]
        for key in requiredExtensionEntitlements where !manifest.shareExtension.entitlementKeys.contains(key) {
            issues.append(.missingEntitlement(target: "Share Extension", key: key))
        }
        return issues
    }

    private static func validateBackgroundModes(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        requiredBackgroundModes
            .filter { !manifest.app.backgroundModes.contains($0) }
            .map { .missingBackgroundMode(mode: $0) }
    }

    private static func validateBackgroundTaskIdentifiers(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        var issues: [SubmissionValidationIssue] = []
        let declared = Set(manifest.app.permittedBackgroundTaskIdentifiers)
        let registered = Set(manifest.registeredBackgroundTaskIdentifiers)

        // Every registered identifier must be declared, else the OS rejects the
        // register/submit call (Req 13.3).
        for id in manifest.registeredBackgroundTaskIdentifiers where !declared.contains(id) {
            issues.append(.undeclaredRegisteredIdentifier(id))
        }
        // No declared identifier may be left unregistered (Req 13.3).
        for id in manifest.app.permittedBackgroundTaskIdentifiers where !registered.contains(id) {
            issues.append(.unregisteredDeclaredIdentifier(id))
        }
        return issues
    }

    private static func validateUsageDescriptions(_ manifest: DistributionManifest) -> [SubmissionValidationIssue] {
        var issues: [SubmissionValidationIssue] = []
        for permission in runtimePermissions {
            guard let value = manifest.app.usageDescriptions[permission.infoPlistKey] else {
                issues.append(.missingUsageDescription(
                    key: permission.infoPlistKey,
                    feature: permission.featureName
                ))
                continue
            }
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                issues.append(.emptyUsageDescription(
                    key: permission.infoPlistKey,
                    feature: permission.featureName
                ))
            } else if !trimmed.localizedCaseInsensitiveContains("SideQuest") {
                // Req 11.2: the usage description must name SideQuest.
                issues.append(.usageDescriptionMissingAppName(
                    key: permission.infoPlistKey,
                    feature: permission.featureName
                ))
            }
        }
        return issues
    }
}
