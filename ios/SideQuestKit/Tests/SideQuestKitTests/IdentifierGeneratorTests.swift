import XCTest
@testable import SideQuestKit

/// Example/edge-case unit tests for the client-side identifier generator
/// (Req 5.7). The exhaustive uniqueness property test is task 3.3.
final class IdentifierGeneratorTests: XCTestCase {

    /// Each call yields a distinct identifier (no coordination required).
    func testSuccessiveIdentifiersDiffer() {
        let generator = UUIDIdentifierGenerator()
        let first = generator.newIdentifier()
        let second = generator.newIdentifier()
        XCTAssertNotEqual(first, second)
    }

    /// Identifiers are emitted in lowercased canonical form so the wire format
    /// stays stable and string-equality (used by sync idempotency, Req 6.8)
    /// matches across platforms.
    func testIdentifierIsLowercased() {
        let id = UUIDIdentifierGenerator().newIdentifier()
        XCTAssertEqual(id, id.lowercased())
    }

    /// The emitted string is a valid RFC 4122 UUID.
    func testIdentifierIsAValidUUID() {
        let id = UUIDIdentifierGenerator().newIdentifier()
        XCTAssertNotNil(UUID(uuidString: id), "Generated id should parse as a UUID")
    }

    /// A modest batch contains no duplicates — a quick sanity check ahead of
    /// the full property test (task 3.3).
    func testBatchHasNoDuplicates() {
        let generator = UUIDIdentifierGenerator()
        let count = 10_000
        let ids = Set((0..<count).map { _ in generator.newIdentifier() })
        XCTAssertEqual(ids.count, count, "All generated identifiers should be unique")
    }

    /// The protocol is the injection seam: a deterministic test double can be
    /// substituted for reproducible assertions in higher layers.
    func testProtocolAllowsDeterministicSubstitution() {
        struct CountingGenerator: IdentifierGenerator {
            final class Box { var value = 0 }
            let box = Box()
            func newIdentifier() -> String {
                defer { box.value += 1 }
                return "id-\(box.value)"
            }
        }

        let generator: IdentifierGenerator = CountingGenerator()
        XCTAssertEqual(generator.newIdentifier(), "id-0")
        XCTAssertEqual(generator.newIdentifier(), "id-1")
    }
}
