import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Scaffolding tests that confirm the shared module, its App Group
/// configuration, and the SwiftCheck property-testing harness are wired up.
///
/// These exist so the SwiftPM test target (with the SwiftCheck dependency)
/// compiles and runs as soon as a macOS/Xcode toolchain is available. The real
/// Correctness Property tests are added by tasks 3.x and 4.x.
final class AppGroupScaffoldingTests: XCTestCase {

    /// The shared App Group identifier referenced in code must match the value
    /// declared in both targets' entitlements files (Req 13.2). This is the
    /// single source of truth; the smoke test in task 18.3 cross-checks the
    /// entitlements files against it.
    func testAppGroupIdentifierMatchesEntitlements() {
        XCTAssertEqual(AppGroup.identifier, "group.com.sidequest.shared")
    }

    /// The shared SQLite file name used by the GRDB store (task 3) is stable.
    func testDatabaseFileNameIsStable() {
        XCTAssertEqual(AppGroup.databaseFileName, "SideQuest.sqlite")
    }

    /// Smoke check that the SwiftCheck harness runs a property at the
    /// project-standard minimum of 100 iterations. Real Correctness Property
    /// tests follow this same pattern.
    func testSwiftCheckHarnessRuns() {
        property("string round-trips through itself", arguments: CheckerArguments(maxAllowableSuccessfulTests: 100))
            <- forAll { (s: String) in
                return s == s
            }
    }
}
