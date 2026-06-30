import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Reused Property 31 — Persistence round trip
/// survives restart, edits, and deletes** (task 3.4).
///
/// **Validates: Requirements 5.4**
///
/// > For any sequence of writes (insert), edits (update), and deletes, after a
/// > simulated restart the persisted state reflects exactly the applied
/// > operations — surviving items are byte-for-byte equal to what was written,
/// > edited values reflect the last edit, and deleted items are absent.
///
/// ## Strategy
///
/// This is a stateful, model-based property. For each case SwiftCheck generates
/// a random sequence of `Command`s (insert/update/delete over `Bucket`s and
/// `ActionItem`s). The commands are applied to **two** subjects in lockstep:
///
/// 1. a real `SideQuestDatabase` opened over a unique temp file, and
/// 2. an in-memory `Reference` model (the source of truth for "what should be
///    persisted").
///
/// We then **simulate a restart** — `SideQuestDatabase` is backed by a GRDB
/// `DatabasePool` over a file path (no in-memory mode), so a restart is modeled
/// by releasing the pool (closing all connections) and reopening a fresh
/// `SideQuestDatabase` at the *same path*. The reopened store is read back and
/// must equal the reference model field-by-field (`Equatable`), which proves
/// nothing was lost, every edit took its last value, and every delete stuck.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **Dates** are generated on whole-second boundaries because GRDB persists
///   `Date` columns at millisecond precision; whole seconds round-trip exactly,
///   guaranteeing the byte-for-byte equality the property asserts. The
///   `Timeframe.specificDate` payload is generated on UTC-midnight boundaries
///   (`day * 86_400`) so it round-trips through the `yyyy-MM-dd` calendar-date
///   column with no time-of-day component to lose.
/// - **Action items reference an existing bucket.** The schema enforces a
///   foreign key from `actionItem.bucketId` to `bucket.id` (with cascade
///   delete), so item operations are only generated against buckets that exist
///   in the current model; deleting a bucket cascade-removes its items in both
///   the database and the reference model.
/// - **Strings** are drawn from a curated character set (letters, digits,
///   spaces, punctuation, a few non-ASCII scalars) — varied, but free of
///   embedded NUL/control bytes that SQLite TEXT storage would mishandle, so
///   the test exercises real variation without spurious failures.
final class PersistenceRoundTripPropertyTests: XCTestCase {

    /// Reused Property 31 / Req 5.4: the reopened store equals the applied ops.
    func testPersistenceRoundTripSurvivesRestartEditsAndDeletes() {
        property(
            "persisted store reflects exactly the applied operations after a restart",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(commandsGen) { commands in
            return self.roundTripHolds(commands)
        }
    }

    // MARK: - Round trip under test

    /// Applies `commands` to a fresh on-disk store, simulates a restart by
    /// closing and reopening the store at the same path, and returns whether
    /// the reopened state equals the in-memory reference model.
    private func roundTripHolds(_ commands: [Command]) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestRoundTrip-\(UUID().uuidString).sqlite"

        // SQLite in WAL mode keeps sidecar files; remove all three on the way
        // out so temp cases don't accumulate.
        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            var reference = Reference()

            // Phase 1 — open, apply every operation, then release the pool to
            // simulate the app terminating (connections close on dealloc).
            var database: SideQuestDatabase? = try SideQuestDatabase(path: path)
            for command in commands {
                try apply(command, to: database!, reference: &reference)
            }
            database = nil

            // Phase 2 — reopen at the same path (simulated relaunch) and read
            // back everything that survived.
            let reopened = try SideQuestDatabase(path: path)
            let persistedBuckets = try reopened.fetchAllBuckets()
            let persistedItems = try reopened.fetchAllActionItems()

            let bucketsById = Dictionary(
                uniqueKeysWithValues: persistedBuckets.map { ($0.id, $0) }
            )
            let itemsById = Dictionary(
                uniqueKeysWithValues: persistedItems.map { ($0.id, $0) }
            )

            return bucketsById == reference.buckets
                && itemsById == reference.items
        } catch {
            XCTFail("Persistence round trip threw: \(error)")
            return false
        }
    }

    /// Applies one command to both the real store and the reference model.
    private func apply(
        _ command: Command,
        to database: SideQuestDatabase,
        reference: inout Reference
    ) throws {
        switch command {
        case .insertBucket(let seed):
            let bucket = seed.makeBucket(id: UUID().uuidString.lowercased())
            try database.saveBucket(bucket)
            reference.insertBucket(bucket)

        case .updateBucket(let index, let seed):
            guard let id = reference.bucketId(at: index) else { return }
            let bucket = seed.makeBucket(id: id)
            try database.saveBucket(bucket)
            reference.buckets[id] = bucket

        case .deleteBucket(let index):
            guard let id = reference.bucketId(at: index) else { return }
            try database.deleteBucket(id: id)
            reference.deleteBucket(id) // cascades to its items, mirroring the FK

        case .insertItem(let bucketIndex, let seed):
            // Items need an existing bucket (foreign key); skip when none yet.
            guard let bucketId = reference.bucketId(at: bucketIndex) else { return }
            let item = seed.makeItem(
                id: UUID().uuidString.lowercased(),
                bucketId: bucketId
            )
            try database.saveActionItem(item)
            reference.insertItem(item)

        case .updateItem(let index, let seed):
            guard let id = reference.itemId(at: index) else { return }
            // Keep the item in its existing (still-present) bucket.
            let bucketId = reference.items[id]!.bucketId
            let item = seed.makeItem(id: id, bucketId: bucketId)
            try database.saveActionItem(item)
            reference.items[id] = item

        case .deleteItem(let index):
            guard let id = reference.itemId(at: index) else { return }
            try database.deleteActionItem(id: id)
            reference.deleteItem(id)
        }
    }
}

// MARK: - In-memory reference model

/// The expected persisted state: what *should* be in the store after the
/// applied operations. Insertion order is tracked so generated indices can
/// address existing entities deterministically.
private struct Reference {
    private(set) var bucketOrder: [String] = []
    var buckets: [String: Bucket] = [:]
    private(set) var itemOrder: [String] = []
    var items: [String: ActionItem] = [:]

    /// Maps an arbitrary generated index onto an existing bucket id, or `nil`
    /// when there are no buckets yet.
    func bucketId(at index: Int) -> String? {
        guard !bucketOrder.isEmpty else { return nil }
        return bucketOrder[index % bucketOrder.count]
    }

    /// Maps an arbitrary generated index onto an existing item id, or `nil`
    /// when there are no items yet.
    func itemId(at index: Int) -> String? {
        guard !itemOrder.isEmpty else { return nil }
        return itemOrder[index % itemOrder.count]
    }

    mutating func insertBucket(_ bucket: Bucket) {
        bucketOrder.append(bucket.id)
        buckets[bucket.id] = bucket
    }

    mutating func insertItem(_ item: ActionItem) {
        itemOrder.append(item.id)
        items[item.id] = item
    }

    /// Deletes a bucket and cascade-removes its items, mirroring the schema's
    /// `onDelete: .cascade` foreign key.
    mutating func deleteBucket(_ id: String) {
        buckets[id] = nil
        bucketOrder.removeAll { $0 == id }
        let orphaned = Set(items.values.filter { $0.bucketId == id }.map { $0.id })
        for orphan in orphaned { items[orphan] = nil }
        itemOrder.removeAll { orphaned.contains($0) }
    }

    mutating func deleteItem(_ id: String) {
        items[id] = nil
        itemOrder.removeAll { $0 == id }
    }
}

// MARK: - Commands

/// One step in a randomly generated history of store mutations. Update/delete
/// commands carry an index that is reduced modulo the current entity count, so
/// every generated command is applicable to whatever state exists at that point
/// (or is skipped when no entity exists yet).
private enum Command: CustomStringConvertible {
    case insertBucket(BucketSeed)
    case updateBucket(Int, BucketSeed)
    case deleteBucket(Int)
    case insertItem(Int, ItemSeed)
    case updateItem(Int, ItemSeed)
    case deleteItem(Int)

    var description: String {
        switch self {
        case .insertBucket:            return "insertBucket"
        case .updateBucket(let i, _):  return "updateBucket(@\(i))"
        case .deleteBucket(let i):     return "deleteBucket(@\(i))"
        case .insertItem(let i, _):    return "insertItem(bucket@\(i))"
        case .updateItem(let i, _):    return "updateItem(@\(i))"
        case .deleteItem(let i):       return "deleteItem(@\(i))"
        }
    }
}

/// Generated field values for a `Bucket` (the `id` is assigned at apply time).
private struct BucketSeed {
    var accountId: String
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String
    var sync: SyncMeta

    func makeBucket(id: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: notStartedColor,
            inProgressColor: inProgressColor,
            completedColor: completedColor,
            sync: sync
        )
    }
}

/// Generated field values for an `ActionItem` (the `id` and `bucketId` are
/// assigned at apply time so the foreign key always resolves).
private struct ItemSeed {
    var accountId: String
    var title: String
    var description: String?
    var contentType: ContentType
    var sourceContent: String?
    var preview: LinkPreview?
    var timeframe: Timeframe
    var status: ActionStatus
    var createdAt: Date
    var sync: SyncMeta

    func makeItem(id: String, bucketId: String) -> ActionItem {
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
            sync: sync
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

/// Index used to address an existing entity; reduced modulo the live count.
private let indexGen = Gen<Int>.choose((0, 64))

/// Whole-second instants (GRDB stores `Date` at millisecond precision, so whole
/// seconds round-trip exactly).
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

private let syncMetaGen: Gen<SyncMeta> = Gen.compose { c in
    SyncMeta(
        updatedAt: c.generate(using: secondsDateGen),
        version: c.generate(using: Gen<Int>.choose((0, 1_000_000)).map(Int64.init)),
        deleted: c.generate(),
        dirty: c.generate()
    )
}

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
        completedColor: c.generate(using: colorGen),
        sync: c.generate(using: syncMetaGen)
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
        status: c.generate(using: statusGen),
        createdAt: c.generate(using: secondsDateGen),
        sync: c.generate(using: syncMetaGen)
    )
}

/// A single random mutation, weighted toward inserts so histories actually
/// build up state to edit and delete.
private let commandGen: Gen<Command> = Gen<Command>.frequency([
    (4, bucketSeedGen.map { Command.insertBucket($0) }),
    (2, Gen.zip(indexGen, bucketSeedGen).map { Command.updateBucket($0.0, $0.1) }),
    (1, indexGen.map { Command.deleteBucket($0) }),
    (5, Gen.zip(indexGen, itemSeedGen).map { Command.insertItem($0.0, $0.1) }),
    (3, Gen.zip(indexGen, itemSeedGen).map { Command.updateItem($0.0, $0.1) }),
    (2, indexGen.map { Command.deleteItem($0) })
])

/// A bounded history of 0...40 commands.
private let commandsGen: Gen<[Command]> = Gen<Int>.choose((0, 40)).flatMap { count in
    commandGen.proliferate(withSize: count)
}
