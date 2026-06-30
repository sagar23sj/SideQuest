import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for the client-side identifier generator (task 3.3).
///
/// **Property 4: Client-generated identifiers are globally unique**
/// *For any* number of new entities created across any number of simulated
/// devices without coordination, the set of generated identifiers contains no
/// duplicates, so records can be created offline without sync collisions.
///
/// **Validates: Requirements 5.7**
///
/// Modelling note: "multiple devices without coordination" is modelled as
/// several *independent* `UUIDIdentifierGenerator` instances, each minting its
/// own batch of identifiers with no shared state between them. The union of
/// every batch must contain no duplicates, and each identifier must be
/// well-formed — a valid RFC 4122 UUID emitted in lowercased canonical form
/// (the wire format string-equality that sync idempotency relies on, Req 6.8).
///
/// Harness note: uses **SwiftCheck** with the project-standard minimum of 100
/// iterations (`maxAllowableSuccessfulTests: 100`), matching the existing
/// `AppGroupScaffoldingTests` harness.
final class ClientIdentifierUniquenessTests: XCTestCase {

    /// Number of independent, uncoordinated "devices" to simulate per trial.
    /// Bounded so each trial stays fast while still exercising many generators.
    private static let deviceCount: Gen<Int> = .fromElements(in: 1...8)

    /// Identifiers minted by each device per trial. Includes 0 so the empty
    /// case (a device that creates nothing) is covered.
    private static let idsPerDevice: Gen<Int> = .fromElements(in: 0...50)

    func testClientGeneratedIdentifiersAreGloballyUnique() {
        property(
            "Property 4: client-generated identifiers are globally unique across uncoordinated devices",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 100)
        ) <- forAll(Self.deviceCount, Self.idsPerDevice) { (deviceCount: Int, idsPerDevice: Int) in
            // Each "device" is an independent generator with no shared state —
            // exactly the offline, no-coordination scenario of Req 5.7.
            var allIdentifiers: [String] = []
            allIdentifiers.reserveCapacity(deviceCount * idsPerDevice)

            for _ in 0..<deviceCount {
                let generator = UUIDIdentifierGenerator()
                for _ in 0..<idsPerDevice {
                    allIdentifiers.append(generator.newIdentifier())
                }
            }

            // No duplicates anywhere in the union of every device's batch.
            let noDuplicates = Set(allIdentifiers).count == allIdentifiers.count

            // Every identifier is a valid UUID emitted in lowercased form.
            let allWellFormed = allIdentifiers.allSatisfy { id in
                UUID(uuidString: id) != nil && id == id.lowercased()
            }

            return (noDuplicates <?> "no duplicate identifiers across devices")
                ^&&^ (allWellFormed <?> "all identifiers are lowercased valid UUIDs")
        }
    }
}
