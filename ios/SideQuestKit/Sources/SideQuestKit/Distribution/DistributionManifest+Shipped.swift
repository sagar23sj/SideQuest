import Foundation

// MARK: - The shipped distribution manifest (task 18.2)
//
// `DistributionManifest.shipped` mirrors the declarations that are actually
// configured in the build:
//
//   * App/Info.plist
//       - UIBackgroundModes: fetch, processing
//       - BGTaskSchedulerPermittedIdentifiers: SyncBackgroundTasks.allIdentifiers
//       - NSPhotoLibraryUsageDescription: non-empty, names SideQuest + feature
//   * App/SideQuest_iOS.entitlements
//       - com.apple.security.application-groups: [AppGroup.identifier]
//       - aps-environment (push notifications)
//       - keychain-access-groups (shared token storage)
//   * ShareExtension/Info.plist
//       - the share-target activation rule (validated structurally by task 18.3)
//   * ShareExtension/ShareExtension.entitlements
//       - com.apple.security.application-groups: [AppGroup.identifier]
//       - keychain-access-groups
//   * project.yml
//       - SideQuest_iOS bundle id = DistributionConfig.appBundleID
//       - ShareExtension bundle id = DistributionConfig.shareExtensionBundleID
//       - ShareExtension embedded as a dependency of SideQuest_iOS (associated
//         app extension)
//
// Keeping this constant in lock-step with those files is what
// `DistributionConfig.validateShippedConfiguration()` checks, and what task
// 18.3 cross-verifies by parsing the real plists/entitlements. If a build-config
// declaration is added, removed, or changed, this value must change with it.

public extension DistributionManifest {

    /// The distribution declarations as configured in the shipped build. Passing
    /// this to ``DistributionConfig/validateForSubmission(_:)`` must yield
    /// ``SubmissionValidationResult/passed``.
    static let shipped = DistributionManifest(
        app: TargetDeclarations(
            bundleIdentifier: DistributionConfig.appBundleID,
            appGroups: [AppGroup.identifier],
            entitlementKeys: [
                DistributionConfig.appGroupEntitlementKey,
                DistributionConfig.pushNotificationEntitlementKey,
                DistributionConfig.keychainAccessGroupEntitlementKey
            ],
            backgroundModes: DistributionConfig.requiredBackgroundModes,
            permittedBackgroundTaskIdentifiers: SyncBackgroundTasks.allIdentifiers,
            usageDescriptions: [
                "NSPhotoLibraryUsageDescription":
                    "SideQuest accesses your photo library so you can capture photos "
                    + "and videos as action items to follow up on later."
            ],
            associatedAppExtensionBundleIDs: [DistributionConfig.shareExtensionBundleID]
        ),
        shareExtension: TargetDeclarations(
            bundleIdentifier: DistributionConfig.shareExtensionBundleID,
            appGroups: [AppGroup.identifier],
            entitlementKeys: [
                DistributionConfig.appGroupEntitlementKey,
                DistributionConfig.keychainAccessGroupEntitlementKey
            ]
        ),
        registeredBackgroundTaskIdentifiers: SyncBackgroundTasks.allIdentifiers
    )
}
