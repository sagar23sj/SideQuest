import XCTest
import Foundation
@testable import SideQuestKit

/// Smoke tests for the App Store distribution configuration (task 18.3,
/// Req 4.1, 11.2, 13.2, 13.3, 13.4, 13.5).
///
/// Unlike the unit tests for ``DistributionConfig`` (task 18.2), which feed it
/// hand-built ``DistributionManifest`` values, these tests parse the **real**
/// build-config files that ship in the repo:
///
///   * `ios/App/Info.plist`
///   * `ios/App/SideQuest_iOS.entitlements`
///   * `ios/ShareExtension/Info.plist`
///   * `ios/ShareExtension/ShareExtension.entitlements`
///   * `ios/project.yml`
///
/// They reconstruct a ``DistributionManifest`` from those files and assert
/// ``DistributionConfig/validateForSubmission(_:)`` passes, and separately
/// assert the Share Extension's `NSExtensionActivationRule` registers SideQuest
/// as a share target for links / text / images / movies. This closes the loop
/// between the in-code expectations and the declarations the OS and App Store
/// actually read, so a drift in any plist/entitlement/project.yml fails CI.
///
/// ## Resolving the config files
///
/// The plists and entitlements live **outside** the SwiftPM package (they
/// belong to the Xcode app/extension targets, which are materialized from
/// `project.yml` on macOS). SwiftPM resource bundling can't reach them, so the
/// tests resolve them relative to this source file via `#filePath`:
///
/// ```
/// <repo>/ios/SideQuestKit/Tests/SideQuestKitTests/DistributionConfigurationSmokeTests.swift
/// └── up 4 ──────────────────────────────────────────────────► <repo>/ios
/// ```
///
/// `#filePath` is the absolute path captured at compile time; since the tests
/// run on the machine that built them, the derived paths resolve correctly.
///
/// ## Where each field comes from
///
/// The bundle identifiers (App IDs) and the "Share Extension is an associated
/// app extension" relationship are declared in `project.yml`, not the plists
/// (the plists carry the unresolved `$(PRODUCT_BUNDLE_IDENTIFIER)` build
/// variable). So those fields are parsed from `project.yml`; everything else
/// (App Groups, entitlement keys, background modes, BGTask identifiers, usage
/// descriptions, the activation rule) is parsed from the plists/entitlements.
final class DistributionConfigurationSmokeTests: XCTestCase {

    // MARK: - Integrated submission validation

    /// The manifest reconstructed from the real config files must pass
    /// submission validation (Req 13.2–13.5, 11.2). This is the end-to-end
    /// smoke test; the focused tests below pin individual guarantees.
    func testRealConfigurationPassesSubmissionValidation() throws {
        let manifest = try makeManifestFromRealConfig()
        let result = DistributionConfig.validateForSubmission(manifest)

        XCTAssertEqual(
            result,
            .passed,
            "Distribution config failed submission validation:\n"
                + result.issues.map { " • \($0.offendingDeclaration)" }.joined(separator: "\n")
        )
    }

    // MARK: - Share Extension activation rule (Req 4.1)

    /// The Share Extension `Info.plist` must declare an `NSExtensionActivationRule`
    /// that registers SideQuest as a share target for web links, text, images,
    /// and movies (Req 4.1).
    func testShareExtensionActivationRuleSupportsLinkTextImageMovie() throws {
        let extInfo = try parsePlist(at: configURL("ShareExtension/Info.plist"))

        let ns = try XCTUnwrap(extInfo["NSExtension"] as? [String: Any], "NSExtension missing")
        XCTAssertEqual(
            ns["NSExtensionPointIdentifier"] as? String,
            "com.apple.share-services",
            "Extension is not declared as a share extension"
        )

        let attributes = try XCTUnwrap(
            ns["NSExtensionAttributes"] as? [String: Any],
            "NSExtensionAttributes missing"
        )
        let rule = try XCTUnwrap(
            attributes["NSExtensionActivationRule"] as? [String: Any],
            "NSExtensionActivationRule missing"
        )

        // Links (web URLs): count must be present and at least 1.
        let webURLCount = intValue(rule["NSExtensionActivationSupportsWebURLWithMaxCount"])
        XCTAssertNotNil(webURLCount, "Activation rule does not support web URLs (links)")
        XCTAssertGreaterThanOrEqual(webURLCount ?? 0, 1, "Web URL max count must be >= 1")

        // Text.
        XCTAssertEqual(
            boolValue(rule["NSExtensionActivationSupportsText"]),
            true,
            "Activation rule does not support text"
        )

        // Images.
        let imageCount = intValue(rule["NSExtensionActivationSupportsImageWithMaxCount"])
        XCTAssertNotNil(imageCount, "Activation rule does not support images")
        XCTAssertGreaterThanOrEqual(imageCount ?? 0, 1, "Image max count must be >= 1")

        // Movies (video references).
        let movieCount = intValue(rule["NSExtensionActivationSupportsMovieWithMaxCount"])
        XCTAssertNotNil(movieCount, "Activation rule does not support movies")
        XCTAssertGreaterThanOrEqual(movieCount ?? 0, 1, "Movie max count must be >= 1")
    }

    // MARK: - App Group identical across targets (Req 13.2)

    /// The `com.apple.security.application-groups` entitlement must be present in
    /// both targets, contain ``AppGroup/identifier``, and be identical between
    /// the main app and the Share Extension (Req 13.2).
    func testAppGroupIdentifierIdenticalAcrossTargets() throws {
        let appEntitlements = try parsePlist(at: configURL("App/SideQuest_iOS.entitlements"))
        let extEntitlements = try parsePlist(at: configURL("ShareExtension/ShareExtension.entitlements"))

        let appGroups = appEntitlements[appGroupEntitlementKey] as? [String] ?? []
        let extGroups = extEntitlements[appGroupEntitlementKey] as? [String] ?? []

        XCTAssertTrue(
            appGroups.contains(AppGroup.identifier),
            "Main app does not declare the shared App Group '\(AppGroup.identifier)'"
        )
        XCTAssertTrue(
            extGroups.contains(AppGroup.identifier),
            "Share Extension does not declare the shared App Group '\(AppGroup.identifier)'"
        )
        XCTAssertEqual(
            Set(appGroups),
            Set(extGroups),
            "App Group declarations differ between targets: app=\(appGroups) ext=\(extGroups)"
        )
    }

    // MARK: - BGTaskScheduler identifiers (Req 13.3)

    /// Every `BGTaskScheduler` identifier the app registers
    /// (``SyncBackgroundTasks/allIdentifiers``) must be declared in the main
    /// app's `BGTaskSchedulerPermittedIdentifiers`, and no declared identifier
    /// may be left unregistered (Req 13.3, 6.5).
    func testRegisteredBackgroundTaskIdentifiersMatchDeclared() throws {
        let appInfo = try parsePlist(at: configURL("App/Info.plist"))

        let declared = appInfo["BGTaskSchedulerPermittedIdentifiers"] as? [String] ?? []
        let registered = SyncBackgroundTasks.allIdentifiers

        for id in registered {
            XCTAssertTrue(
                declared.contains(id),
                "Registered BGTask identifier '\(id)' is not declared in BGTaskSchedulerPermittedIdentifiers"
            )
        }
        for id in declared {
            XCTAssertTrue(
                registered.contains(id),
                "Declared BGTask identifier '\(id)' is never registered by the app"
            )
        }
        XCTAssertEqual(
            Set(declared),
            Set(registered),
            "Declared and registered BGTask identifiers must match exactly"
        )
    }

    // MARK: - Entitlements + usage descriptions (Req 13.4, 13.5, 11.2)

    /// The required capability entitlements must be present in each target, and
    /// every runtime-prompted permission's usage description must be present,
    /// non-empty, and name SideQuest (Req 13.4, 13.5, 11.2).
    func testEntitlementsAndUsageDescriptionsPresent() throws {
        let appInfo = try parsePlist(at: configURL("App/Info.plist"))
        let appEntitlements = try parsePlist(at: configURL("App/SideQuest_iOS.entitlements"))
        let extEntitlements = try parsePlist(at: configURL("ShareExtension/ShareExtension.entitlements"))

        // Main app: App Group + push notifications + shared Keychain (Req 13.4).
        for key in [
            DistributionConfig.appGroupEntitlementKey,
            DistributionConfig.pushNotificationEntitlementKey,
            DistributionConfig.keychainAccessGroupEntitlementKey
        ] {
            XCTAssertNotNil(appEntitlements[key], "Main app missing entitlement '\(key)'")
        }

        // Share Extension: App Group + shared Keychain (Req 13.4).
        for key in [
            DistributionConfig.appGroupEntitlementKey,
            DistributionConfig.keychainAccessGroupEntitlementKey
        ] {
            XCTAssertNotNil(extEntitlements[key], "Share Extension missing entitlement '\(key)'")
        }

        // Background-processing capability (Req 13.4).
        let backgroundModes = appInfo["UIBackgroundModes"] as? [String] ?? []
        for mode in DistributionConfig.requiredBackgroundModes {
            XCTAssertTrue(
                backgroundModes.contains(mode),
                "Main app missing UIBackgroundModes value '\(mode)'"
            )
        }

        // Usage descriptions (Req 13.5, 11.2): present, non-empty, names SideQuest.
        for permission in DistributionConfig.runtimePermissions {
            let value = appInfo[permission.infoPlistKey] as? String
            let unwrapped = try XCTUnwrap(
                value,
                "Missing usage description '\(permission.infoPlistKey)' for \(permission.featureName)"
            )
            let trimmed = unwrapped.trimmingCharacters(in: .whitespacesAndNewlines)
            XCTAssertFalse(
                trimmed.isEmpty,
                "Empty usage description '\(permission.infoPlistKey)' for \(permission.featureName)"
            )
            XCTAssertTrue(
                trimmed.localizedCaseInsensitiveContains("SideQuest"),
                "Usage description '\(permission.infoPlistKey)' must name SideQuest"
            )
        }
    }

    // MARK: - Manifest reconstruction from the real config files

    /// Builds a ``DistributionManifest`` purely from the on-disk config files so
    /// it can be fed to ``DistributionConfig/validateForSubmission(_:)``.
    private func makeManifestFromRealConfig() throws -> DistributionManifest {
        let appInfo = try parsePlist(at: configURL("App/Info.plist"))
        let appEntitlements = try parsePlist(at: configURL("App/SideQuest_iOS.entitlements"))
        let extEntitlements = try parsePlist(at: configURL("ShareExtension/ShareExtension.entitlements"))

        // Bundle IDs + associated-extension relationship come from project.yml,
        // since the plists carry the unresolved $(PRODUCT_BUNDLE_IDENTIFIER).
        let yaml = try String(contentsOf: configURL("project.yml"), encoding: .utf8)
        let bundleIDs = try targetBundleIdentifiers(fromProjectYAML: yaml)
        let appEmbedsExtension = appTargetEmbedsShareExtension(inProjectYAML: yaml)

        let app = TargetDeclarations(
            bundleIdentifier: bundleIDs.app,
            appGroups: appEntitlements[appGroupEntitlementKey] as? [String] ?? [],
            entitlementKeys: Set(appEntitlements.keys),
            backgroundModes: appInfo["UIBackgroundModes"] as? [String] ?? [],
            permittedBackgroundTaskIdentifiers: appInfo["BGTaskSchedulerPermittedIdentifiers"] as? [String] ?? [],
            usageDescriptions: usageDescriptions(from: appInfo),
            associatedAppExtensionBundleIDs: appEmbedsExtension ? [bundleIDs.shareExtension] : []
        )

        let shareExtension = TargetDeclarations(
            bundleIdentifier: bundleIDs.shareExtension,
            appGroups: extEntitlements[appGroupEntitlementKey] as? [String] ?? [],
            entitlementKeys: Set(extEntitlements.keys)
        )

        return DistributionManifest(
            app: app,
            shareExtension: shareExtension,
            registeredBackgroundTaskIdentifiers: SyncBackgroundTasks.allIdentifiers
        )
    }

    // MARK: - Plist helpers

    private let appGroupEntitlementKey = "com.apple.security.application-groups"

    /// Parses an XML plist / entitlements file into a dictionary.
    private func parsePlist(at url: URL) throws -> [String: Any] {
        let data = try Data(contentsOf: url)
        let object = try PropertyListSerialization.propertyList(from: data, options: [], format: nil)
        return try XCTUnwrap(object as? [String: Any], "\(url.lastPathComponent) is not a plist dictionary")
    }

    /// Extracts all `NS…UsageDescription` purpose strings from an Info.plist.
    private func usageDescriptions(from info: [String: Any]) -> [String: String] {
        var result: [String: String] = [:]
        for (key, value) in info where key.hasSuffix("UsageDescription") {
            if let string = value as? String {
                result[key] = string
            }
        }
        return result
    }

    private func intValue(_ any: Any?) -> Int? {
        (any as? NSNumber)?.intValue
    }

    private func boolValue(_ any: Any?) -> Bool? {
        (any as? NSNumber)?.boolValue
    }

    // MARK: - project.yml helpers

    /// The two target bundle identifiers parsed from `project.yml`.
    private struct TargetBundleIDs {
        let app: String
        let shareExtension: String
    }

    /// Parses `PRODUCT_BUNDLE_IDENTIFIER` for the `SideQuest_iOS` and
    /// `ShareExtension` targets from `project.yml`. The app target is declared
    /// before the extension target, so the file splits cleanly into the two
    /// target blocks.
    private func targetBundleIdentifiers(fromProjectYAML yaml: String) throws -> TargetBundleIDs {
        let appHeader = "\n  SideQuest_iOS:\n"
        let extHeader = "\n  ShareExtension:\n"

        let appHeaderRange = try XCTUnwrap(yaml.range(of: appHeader), "SideQuest_iOS target not found in project.yml")
        let extHeaderRange = try XCTUnwrap(yaml.range(of: extHeader), "ShareExtension target not found in project.yml")

        let appBlock = String(yaml[appHeaderRange.upperBound..<extHeaderRange.lowerBound])
        let extBlock = String(yaml[extHeaderRange.upperBound...])

        let appID = try XCTUnwrap(
            bundleIdentifier(inBlock: appBlock),
            "PRODUCT_BUNDLE_IDENTIFIER missing for SideQuest_iOS"
        )
        let extID = try XCTUnwrap(
            bundleIdentifier(inBlock: extBlock),
            "PRODUCT_BUNDLE_IDENTIFIER missing for ShareExtension"
        )
        return TargetBundleIDs(app: appID, shareExtension: extID)
    }

    /// Extracts the first `PRODUCT_BUNDLE_IDENTIFIER` value within a YAML block.
    private func bundleIdentifier(inBlock block: String) -> String? {
        let pattern = "PRODUCT_BUNDLE_IDENTIFIER:\\s*([^\\s#]+)"
        guard
            let regex = try? NSRegularExpression(pattern: pattern),
            let match = regex.firstMatch(in: block, range: NSRange(block.startIndex..., in: block)),
            let valueRange = Range(match.range(at: 1), in: block)
        else {
            return nil
        }
        return String(block[valueRange])
    }

    /// Whether the main app target embeds the Share Extension as a dependency
    /// (an associated app extension, Req 13.2).
    private func appTargetEmbedsShareExtension(inProjectYAML yaml: String) -> Bool {
        let appHeader = "\n  SideQuest_iOS:\n"
        let extHeader = "\n  ShareExtension:\n"
        guard
            let appHeaderRange = yaml.range(of: appHeader),
            let extHeaderRange = yaml.range(of: extHeader)
        else {
            return false
        }
        let appBlock = yaml[appHeaderRange.upperBound..<extHeaderRange.lowerBound]
        return appBlock.contains("- target: ShareExtension")
    }

    // MARK: - Config file resolution

    /// Resolves a config file path relative to the repo's `ios` directory,
    /// derived from this test source file's location via `#filePath`.
    private func configURL(_ relativePath: String) -> URL {
        var url = iosDirectory
        for component in relativePath.split(separator: "/") {
            url.appendPathComponent(String(component))
        }
        return url
    }

    /// `<repo>/ios`, four levels up from this file
    /// (`ios/SideQuestKit/Tests/SideQuestKitTests/<file>.swift`).
    private var iosDirectory: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // SideQuestKitTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // SideQuestKit
            .deletingLastPathComponent()   // ios
    }
}
