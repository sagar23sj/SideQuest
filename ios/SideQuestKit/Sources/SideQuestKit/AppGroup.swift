import Foundation

/// Shared App Group configuration for SideQuest_iOS.
///
/// The Share Extension and the main app are **separate iOS processes** that
/// share one SQLite database located in a shared App Group container (design:
/// "Process and storage boundaries"). Both targets must reference the *same*
/// App Group identifier — Req 13.2 requires the identifier declared in the
/// Share Extension to be identical to the one declared in the main app.
///
/// This type is the single source of truth for that identifier in code. The
/// same string is also declared in both targets' `.entitlements` files
/// (`App/SideQuest_iOS.entitlements` and `ShareExtension/ShareExtension.entitlements`),
/// which is what the OS and App Store validation actually check. Keeping the
/// code constant here means every process derives the shared container URL the
/// same way, with no risk of drift.
public enum AppGroup {

    /// The App Group identifier shared by the main app and the Share Extension.
    ///
    /// MUST stay identical to the `com.apple.security.application-groups` value
    /// in both targets' entitlements files (Req 13.2).
    public static let identifier = "group.com.sidequest.shared"

    /// File name of the shared SQLite database inside the App Group container.
    /// Used by the GRDB store in task 3.
    public static let databaseFileName = "SideQuest.sqlite"

    /// Shared **Keychain access group** that scopes the auth tokens so both the
    /// main app and the Share Extension can read them (design: "The Keychain
    /// entries used for tokens are scoped to a shared access group so both
    /// processes can authenticate sync if needed"; Req 10.4).
    ///
    /// This is distinct from the App Group above: App Groups share a *file*
    /// container, while a Keychain access group shares *keychain items*. At
    /// runtime the OS prefixes this value with the app's team identifier, so the
    /// `keychain-access-groups` entitlement in both targets is declared as
    /// `$(AppIdentifierPrefix)com.sidequest.shared`. The default
    /// ``KeychainTokenStore`` uses this identifier; the value passed to the
    /// keychain APIs matches the (team-prefixed) entitlement entry.
    public static let keychainAccessGroup = "com.sidequest.shared"

    /// URL of the shared App Group container directory, or `nil` if the App
    /// Group entitlement is missing/misconfigured (which would itself be a
    /// distribution-validation failure under Req 13.7).
    public static var containerURL: URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: identifier
        )
    }

    /// URL of the shared SQLite database file inside the App Group container.
    /// Returns `nil` when the container is unavailable. The GRDB `DatabasePool`
    /// (task 3.1) opens this path in WAL mode for coordinated multi-process
    /// access.
    public static var databaseURL: URL? {
        containerURL?.appendingPathComponent(databaseFileName)
    }
}
