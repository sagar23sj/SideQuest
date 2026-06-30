import Foundation

#if canImport(Security)
import Security

// MARK: - Keychain-backed token store (Apple platforms only)
//
// The iOS Keychain is the required home for the auth tokens (Req 10.4). This
// type is compiled only when the `Security` framework is available, so the
// shared module still builds on non-Apple hosts (where ``InMemoryTokenStore``
// stands in for tests).
//
// The whole `TokenPair` is stored as a single generic-password item, with its
// value being the JSON the shared `SideQuestCoding` encoder produces. The item
// is scoped to the shared **Keychain access group** (``AppGroup/keychainAccessGroup``)
// so both the main app and the Share Extension processes can read it, and it
// uses `kSecAttrAccessibleAfterFirstUnlock` so background sync / the extension
// can read tokens while the device is locked (after the first unlock since
// boot), without exposing them before first unlock.

/// Errors surfaced by ``KeychainTokenStore`` when a keychain operation fails or
/// a stored item cannot be decoded.
public enum KeychainError: Error, Equatable {

    /// A `SecItem*` call returned a non-success `OSStatus`. Carries the raw
    /// status for diagnostics.
    case unhandledStatus(OSStatus)

    /// A keychain item was found but its data could not be decoded into a
    /// ``TokenPair`` (corruption or a format change).
    case malformedData
}

/// A ``TokenStore`` backed by the iOS Keychain, scoped to a shared access group
/// so the main app and Share Extension share one set of tokens (Req 10.4).
public struct KeychainTokenStore: TokenStore {

    /// Keychain `kSecAttrService` — namespaces this app's auth item.
    private let service: String
    /// Keychain `kSecAttrAccount` — the single token-pair record key.
    private let account: String
    /// Shared keychain access group, or `nil` to use the app's default group
    /// (e.g. in unit/host environments without the entitlement).
    private let accessGroup: String?

    /// Creates a keychain store.
    ///
    /// - Parameters:
    ///   - service: service attribute namespacing the item. Defaults to the
    ///     app's auth service id.
    ///   - account: account attribute identifying the single token record.
    ///   - accessGroup: the shared keychain access group. Defaults to
    ///     ``AppGroup/keychainAccessGroup`` so tokens are shared across the
    ///     app and the Share Extension. Pass `nil` to fall back to the app's
    ///     private keychain (useful where the shared-group entitlement is not
    ///     present).
    public init(
        service: String = "com.sidequest.auth",
        account: String = "tokenPair",
        accessGroup: String? = AppGroup.keychainAccessGroup
    ) {
        self.service = service
        self.account = account
        self.accessGroup = accessGroup
    }

    public func loadTokens() throws -> TokenPair? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        switch status {
        case errSecSuccess:
            guard let data = result as? Data else { throw KeychainError.malformedData }
            do {
                return try SideQuestCoding.makeDecoder().decode(TokenPair.self, from: data)
            } catch {
                throw KeychainError.malformedData
            }
        case errSecItemNotFound:
            return nil
        default:
            throw KeychainError.unhandledStatus(status)
        }
    }

    public func saveTokens(_ tokens: TokenPair) throws {
        let data = try SideQuestCoding.makeEncoder().encode(tokens)
        let query = baseQuery()

        // Try to update an existing item first; if none exists, add one. This
        // avoids a delete+add race and keeps the item's accessibility attribute
        // stable across updates.
        let update = SecItemUpdate(
            query as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )

        switch update {
        case errSecSuccess:
            return
        case errSecItemNotFound:
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            let add = SecItemAdd(addQuery as CFDictionary, nil)
            guard add == errSecSuccess else { throw KeychainError.unhandledStatus(add) }
        default:
            throw KeychainError.unhandledStatus(update)
        }
    }

    public func clearTokens() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        // Deleting a non-existent item is a no-op, not a failure (idempotent).
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unhandledStatus(status)
        }
    }

    /// The attributes that uniquely identify this app's token item.
    private func baseQuery() -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }
}

#endif
