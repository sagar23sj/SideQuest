// swift-tools-version: 5.9
//
// SideQuestKit — the shared, portable Swift module reused by BOTH the
// SideQuest_iOS main app target and the Share Extension target.
//
// It is the single home for:
//   * Models  — the OpenAPI-derived `Generated_Models` (task 2)
//   * Store   — the GRDB/SQLite local store in the App Group container (task 3)
//   * Domain  — the pure, portable domain logic validated by the shared
//               Correctness Properties (task 4)
//
// Because the Share Extension and the main app run in separate iOS processes
// but must share identical models, store access, and domain behaviour, that
// code lives here once and is linked by both targets.
//
// Dependencies (Req: task 1 — "Add the GRDB and SwiftCheck dependencies (SPM)"):
//   * GRDB.swift — SQLite access for the local store (product dependency).
//   * SwiftCheck — property-based testing, used only by the test target.
import PackageDescription

let package = Package(
    name: "SideQuestKit",
    // iPhone-only client; the Minimum_iOS_Version (Req 1.2) is declared here
    // for the package and mirrored by the Xcode targets' deployment target.
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "SideQuestKit",
            targets: ["SideQuestKit"]
        )
    ],
    dependencies: [
        // GRDB powers the App Group SQLite store (design: "Local DB: GRDB").
        .package(url: "https://github.com/groue/GRDB.swift", from: "6.0.0"),
        // SwiftCheck drives the property-based tests for the Correctness
        // Properties (design: Testing Strategy). Test-only.
        .package(url: "https://github.com/typelift/SwiftCheck", from: "0.12.0")
    ],
    targets: [
        .target(
            name: "SideQuestKit",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift")
            ],
            path: "Sources/SideQuestKit"
        ),
        .testTarget(
            name: "SideQuestKitTests",
            dependencies: [
                "SideQuestKit",
                .product(name: "SwiftCheck", package: "SwiftCheck")
            ],
            path: "Tests/SideQuestKitTests"
        )
    ]
)
