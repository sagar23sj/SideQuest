import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Property 2 — Captured items are visible across the
/// extension/main-app process boundary** (task 8.5).
///
/// **Validates: Requirements 4.10**
///
/// > For any Action_Item created through the Share Extension's confirm path,
/// > after the write commits to the shared App Group store, reading the store
/// > from the main app returns an item field-by-field equal to the captured
/// > item.
///
/// ## Strategy
///
/// The Share Extension and the main app are **separate iOS processes** that
/// share one SQLite file in the App Group container (design: "Process and
/// storage boundaries"; Req 4.10). The store is opened in WAL mode with a
/// coordinated `DatabasePool`, which is exactly what lets one process write
/// while another reads the same file.
///
/// We model that boundary with **two independent `SideQuestDatabase` instances
/// opened on the same on-disk file path** — each `DatabasePool` is its own set
/// of SQLite connections, so a write committed through one instance and a read
/// performed through the other crosses the same connection boundary that the
/// extension/main-app split crosses in production. (An in-memory database can't
/// model this — it isn't shared across connections — and the kit's
/// `DatabasePool` has no in-memory mode regardless.)
///
/// For each generated case:
/// 1. Open the **extension-side** store and a separate **main-app-side** store
///    on the same unique temp path.
/// 2. Seed the chosen bucket through the extension store (the captured item's
///    `bucketId` foreign key must resolve).
/// 3. Write the item through an `ActionItemRepository` over the *extension*
///    store via `create(_:)` — the same durable, atomic write the capture
///    confirm path uses. Its return value is the **captured item** (the
///    repository stamps the authoritative `dirty`/`created` sync metadata), and
///    is what the property compares against.
/// 4. Read every live item back through a *separate* `ActionItemRepository`
///    over the *main-app* store, locate the captured item by `id`, and assert
///    it is field-by-field equal to the captured item (`ActionItem` is
///    `Equatable`, so `==` is a field-by-field comparison).
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **Dates** are generated on whole-second boundaries because GRDB persists
///   `Date` columns at millisecond precision; whole seconds round-trip exactly,
///   so the cross-instance read is byte-for-byte equal to the captured item.
///   The injected creation instant (`now`) is likewise whole-second so the
///   stamped `sync.updatedAt` round-trips. The `Timeframe.specificDate` payload
///   is generated on UTC-midnight boundaries (`day * 86_400`) so it round-trips
///   through the `yyyy-MM-dd` calendar-date column with no time-of-day to lose.
/// - **The item references the seeded bucket** so the `actionItem.bucketId`
///   foreign key always resolves and `create(_:)` commits.
/// - **Strings** are drawn from a curated, SQLite-safe character set (no
///   NUL/control bytes), with a few non-ASCII scalars to exercise unicode.
final class CrossProcessVisibilityPropertyTests: XCTestCase {

    /// Property 2 / Req 4.10: an item written through the extension store is
    /// read back field-by-field equal through a separate main-app store.
    func testCapturedItemIsVisibleAcrossProcessBoundary() {
        property(
            "an item committed via the extension store is read back unchanged via the main-app store",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.visibilityHolds(scenario)
        }
    }

    // MARK: - Boundary crossing under test

    /// Seeds a bucket and writes an item through the extension store, then reads
    /// it back through a separate store opened on the same file. Returns whether
    /// the read-back item equals the captured item field-by-field.
    private func visibilityHolds(_ scenario: Scenario) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestCrossProcess-\(UUID().uuidString).sqlite"

        // WAL keeps sidecar files; remove all three on the way out.
        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            // Two independent instances on the same file model the two
            // processes sharing the App Group store.
            let extensionStore = try SideQuestDatabase(path: path)
            let mainAppStore = try SideQuestDatabase(path: path)

            // The captured item's bucket must exist (foreign key). The
            // extension writes into a bucket that already exists in the store.
            let bucket = scenario.bucketSeed.makeBucket(id: scenario.bucketId)
            try extensionStore.saveBucket(bucket)

            // Write through the extension-side repository — the same create
            // path the confirm step uses. The returned value is the captured
            // item (with repository-stamped sync metadata).
            let now = scenario.now
            let extensionRepo = ActionItemRepository(database: extensionStore, now: { now })
            let item = scenario.itemSeed.makeItem(
                id: scenario.itemId,
                bucketId: scenario.bucketId,
                createdAt: scenario.createdAt
            )
            let captured = try extensionRepo.create(item)

            // Read through a *separate* repository over the main-app store.
            let mainAppRepo = ActionItemRepository(database: mainAppStore)
            let readBack = try mainAppRepo.fetchAll()

            guard let visible = readBack.first(where: { $0.id == captured.id }) else {
                return false // the captured item never crossed the boundary
            }
            // `ActionItem: Equatable` → field-by-field equality (Req 4.10).
            return visible == captured
        } catch {
            XCTFail("Cross-process visibility threw: \(error)")
            return false
        }
    }
}

// MARK: - Scenario

/// One generated capture: the bucket to seed, the item to capture into it, and
/// the deterministic creation instant.
private struct Scenario {
    var bucketId: String
    var bucketSeed: BucketSeed
    var itemId: String
    var itemSeed: ItemSeed
    var createdAt: Date
    var now: Date
}

/// Generated field values for the seeded `Bucket` (the `id` is assigned by the
/// scenario).
private struct BucketSeed {
    var accountId: String
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String

    func makeBucket(id: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: notStartedColor,
            inProgressColor: inProgressColor,
            completedColor: completedColor,
            // The captured item's sync metadata is what the property checks;
            // the bucket just needs to exist for the foreign key.
            sync: .created(now: Date(timeIntervalSince1970: 1_700_000_000))
        )
    }
}

/// Generated field values for the captured `ActionItem`. The `id`, `bucketId`,
/// and `createdAt` are assigned by the scenario; `sync` is overwritten by
/// `ActionItemRepository.create(_:)`, so it is not generated here.
private struct ItemSeed {
    var accountId: String
    var title: String
    var description: String?
    var contentType: ContentType
    var sourceContent: String?
    var preview: LinkPreview?
    var timeframe: Timeframe
    var status: ActionStatus

    func makeItem(id: String, bucketId: String, createdAt: Date) -> ActionItem {
        ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            description: description,
            contentType: contentType,
            sourceContent: sourceContent,
            preview: preview,
            timeframe: timeframe,
            status: status,
            createdAt: createdAt,
            // Placeholder; `create(_:)` replaces this with `.created(now:)`.
            sync: SyncMeta(updatedAt: createdAt, version: 0, deleted: false)
        )
    }
}

// MARK: - Generators

/// Curated, SQLite-safe character set (no NUL/control bytes), with a few
/// non-ASCII scalars to exercise unicode handling.
private let stringChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-./:éüçñ✓—")

private let charGen = Gen<Character>.fromElements(of: stringChars)

/// Bounded-length strings (0...16 chars), possibly empty.
private let stringGen: Gen<String> = Gen<Int>.choose((0, 16)).flatMap { size in
    charGen.proliferate(withSize: size).map { String($0) }
}

private func optionalGen<T>(_ gen: Gen<T>) -> Gen<T?> {
    Gen.one(of: [Gen.pure(T?.none), gen.map { Optional($0) }])
}

private let accountIdGen = Gen<String>.fromElements(of: ["acct-1", "acct-2", "acct-3"])

private let colorGen = Gen<String>.fromElements(of: [
    "#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF", "#000000", "#FFFFFF"
])

/// Whole-second instants (GRDB stores `Date` at millisecond precision, so whole
/// seconds round-trip exactly across the read).
private let secondsDateGen: Gen<Date> = Gen<Int>.choose((1_600_000_000, 1_800_000_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0)) }

/// UTC-midnight dates for the `Timeframe.specificDate` calendar-date payload.
private let calendarDateGen: Gen<Date> = Gen<Int>.choose((16_000, 20_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0 * 86_400)) }

private let timeframeGen: Gen<Timeframe> = Gen.one(of: [
    Gen.pure(Timeframe.today),
    Gen.pure(Timeframe.withinADay),
    Gen.pure(Timeframe.withinAWeek),
    calendarDateGen.map { Timeframe.specificDate($0) }
])

private let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)
private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

private let linkPreviewGen: Gen<LinkPreview> = Gen.compose { c in
    LinkPreview(
        title: c.generate(using: optionalGen(stringGen)),
        thumbnailUrl: c.generate(using: optionalGen(stringGen)),
        sourceName: c.generate(using: optionalGen(stringGen)),
        rawUrl: c.generate(using: stringGen),
        resolved: c.generate()
    )
}

private let bucketSeedGen: Gen<BucketSeed> = Gen.compose { c in
    BucketSeed(
        accountId: c.generate(using: accountIdGen),
        name: c.generate(using: stringGen),
        notStartedColor: c.generate(using: colorGen),
        inProgressColor: c.generate(using: colorGen),
        completedColor: c.generate(using: colorGen)
    )
}

private let itemSeedGen: Gen<ItemSeed> = Gen.compose { c in
    ItemSeed(
        accountId: c.generate(using: accountIdGen),
        title: c.generate(using: stringGen),
        description: c.generate(using: optionalGen(stringGen)),
        contentType: c.generate(using: contentTypeGen),
        sourceContent: c.generate(using: optionalGen(stringGen)),
        preview: c.generate(using: optionalGen(linkPreviewGen)),
        timeframe: c.generate(using: timeframeGen),
        status: c.generate(using: statusGen)
    )
}

/// A fresh lowercase UUID, matching how the repository/store generate ids.
private let idGen: Gen<String> = Gen<Void>.pure(()).map { _ in UUID().uuidString.lowercased() }

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    Scenario(
        bucketId: c.generate(using: idGen),
        bucketSeed: c.generate(using: bucketSeedGen),
        itemId: c.generate(using: idGen),
        itemSeed: c.generate(using: itemSeedGen),
        createdAt: c.generate(using: secondsDateGen),
        now: c.generate(using: secondsDateGen)
    )
}
