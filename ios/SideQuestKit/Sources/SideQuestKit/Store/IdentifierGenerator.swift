import Foundation

/// Produces client-side identifiers for new entities.
///
/// All SideQuest entities use **client-generated** identifiers so a record can
/// be created entirely offline — with no server round-trip and no coordination
/// between devices — and still be globally unique, so retried/late syncs never
/// collide (design: "Data Models" — "IDs are client-generated UUIDs"; Req 5.7).
///
/// Entity `id` fields are `String` (see `ActionItem`, `Bucket`, `ActionPlan`,
/// `SubAction`), so this generator yields `String`s rather than `UUID`s.
///
/// The protocol is the injection seam: production code uses
/// `UUIDIdentifierGenerator`, while tests can substitute a deterministic or
/// counter-based generator to make assertions reproducible. This is a leaf
/// utility consumed by the repositories (task 6) and the capture flow (task 8).
public protocol IdentifierGenerator {

    /// Returns a fresh identifier that is globally unique without coordination.
    func newIdentifier() -> String
}

/// Default `IdentifierGenerator` backed by `Foundation.UUID` (RFC 4122 v4).
///
/// UUID v4 supplies 122 random bits, which makes accidental collisions across
/// all devices and the backend negligibly unlikely, satisfying the
/// "unique across all devices and the Backend" requirement without any
/// central allocation (Req 5.7).
///
/// Identifiers are emitted in **lowercased** canonical form
/// (e.g. `"f47ac10b-58cc-4372-a567-0e02b2c3d479"`). `UUID.uuidString` is
/// uppercased by default; lowercasing keeps the wire format stable and matching
/// across platforms, since UUID comparison is case-insensitive but string
/// equality (used by sync idempotency keyed on the id, Req 6.8) is not.
public struct UUIDIdentifierGenerator: IdentifierGenerator {

    public init() {}

    public func newIdentifier() -> String {
        UUID().uuidString.lowercased()
    }
}
