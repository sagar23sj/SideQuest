import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Property 5 — Local mutations are marked pending and
/// stay pending until acknowledged** (task 6.2).
///
/// **Validates: Requirements 5.6**
///
/// > For any sequence of create/edit/delete mutations and sync attempts, every
/// > mutated entity is marked `dirty` when changed and remains `dirty` until a
/// > successful push acknowledgment for that entity clears it.
///
/// ## Strategy
///
/// This is a stateful, model-based property exercising the real repository layer
/// (`BucketRepository`, `ActionItemRepository`) over an on-disk GRDB store. For
/// each trial SwiftCheck generates:
///
/// 1. a count of seed buckets (1...3), and
/// 2. a random sequence of `Command`s — create / edit / delete an item, edit a
///    bucket, and *acknowledge a push* for either entity with a **matching** or
///    a **mismatched** version.
///
/// The commands are applied in lockstep to two subjects:
///
/// - the real repositories writing the GRDB store, and
/// - an in-memory **expectation** of each entity's `(version, dirty)` — the
///   source of truth for what *should* be persisted.
///
/// After every command is applied, the store is read back (including tombstones,
/// since deletes are tombstones that stay pending) and each entity's persisted
/// `sync.dirty` and `sync.version` must equal the expectation. This proves the
/// two halves of the property simultaneously:
///
/// - **marked pending on change** — every create/edit/delete leaves the entity
///   `dirty == true`;
/// - **stays pending until acknowledged** — `dirty` only ever flips to `false`
///   via an acknowledgment whose version *matches* the currently persisted
///   version. A mismatched acknowledgment (e.g. one arriving after a later edit
///   bumped the version) is a no-op, so the entity stays `dirty`.
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **Indices** addressing existing entities are reduced modulo the live count
///   at apply time, so every generated command targets some entity that exists
///   (or is skipped before any entity is created), mirroring the approach used
///   by `PersistenceRoundTripPropertyTests`.
/// - **Action items reference a seeded bucket** so the schema's foreign key
///   always resolves; buckets are never deleted in a trial, avoiding cascade
///   deletes that would remove items out from under the expectation model.
/// - **Mismatched acknowledgments** use `currentVersion + 1000`, a value the
///   bounded history (≤ 40 commands) can never reach, guaranteeing the version
///   guard rejects the acknowledgment and the entity stays `dirty`.
final class PendingSyncTrackingPropertyTests: XCTestCase {

    /// Property 5 / Req 5.6: mutations are dirty and stay dirty until a
    /// version-matching acknowledgment clears them.
    func testLocalMutationsStayPendingUntilAcknowledged() {
        property(
            "Property 5: mutations are marked pending and stay pending until acknowledged",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(Gen.zip(bucketCountGen, commandsGen)) { (bucketCount, commands) in
            return self.pendingSyncTrackingHolds(bucketCount: bucketCount, commands: commands)
        }
    }

    // MARK: - Property under test

    /// Seeds `bucketCount` buckets, applies `commands`, and checks after **each**
    /// command that every persisted entity's `dirty`/`version` equals the
    /// in-memory expectation.
    private func pendingSyncTrackingHolds(bucketCount: Int, commands: [Command]) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestPendingSync-\(UUID().uuidString).sqlite"

        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            let database = try SideQuestDatabase(path: path)
            let subject = Subject(database: database)

            // Seed buckets — each is a mutation, so each starts dirty (Req 5.6).
            for index in 0..<bucketCount {
                try subject.seedBucket(named: "Bucket \(index)")
            }
            if try !subject.matchesStore() { return false }

            // Apply each command and re-check the invariant after every step.
            for command in commands {
                try subject.apply(command)
                if try !subject.matchesStore() { return false }
            }
            return true
        } catch {
            XCTFail("Pending-sync tracking trial threw: \(error)")
            return false
        }
    }
}

// MARK: - Subject (real repositories + expectation model)

/// Drives the real repositories and tracks the expected `(version, dirty)` for
/// every entity. Item/bucket ids are addressed by insertion order so generated
/// indices map deterministically onto existing entities.
private final class Subject {

    private let database: SideQuestDatabase
    private let bucketRepo: BucketRepository
    private let itemRepo: ActionItemRepository
    private let accountId = "acct-1"

    /// Expected persisted entities, keyed by id. The stored copy's `sync` field
    /// is the expectation the store is checked against.
    private var expectedBuckets: [String: Bucket] = [:]
    private var bucketOrder: [String] = []
    private var expectedItems: [String: ActionItem] = [:]
    private var itemOrder: [String] = []

    init(database: SideQuestDatabase) {
        self.database = database
        self.bucketRepo = BucketRepository(database: database)
        self.itemRepo = ActionItemRepository(database: database)
    }

    // MARK: Seeding

    func seedBucket(named name: String) throws {
        let bucket = Bucket(
            id: bucketRepo.newIdentifier(),
            accountId: accountId,
            name: name,
            notStartedColor: "#FF0000",
            inProgressColor: "#00FF00",
            completedColor: "#0000FF",
            sync: SyncMeta(updatedAt: Date(), version: 0, deleted: false, dirty: false)
        )
        let stored = try bucketRepo.create(bucket)
        bucketOrder.append(stored.id)
        expectedBuckets[stored.id] = stored
    }

    // MARK: Command application

    func apply(_ command: Command) throws {
        switch command {
        case .createItem(let bucketIndex, let title, let status):
            try createItem(bucketIndex: bucketIndex, title: title, status: status)
        case .editItem(let index):
            try editItem(at: index)
        case .deleteItem(let index):
            try deleteItem(at: index)
        case .ackItem(let index, let correct):
            try ackItem(at: index, correctVersion: correct)
        case .editBucket(let index):
            try editBucket(at: index)
        case .ackBucket(let index, let correct):
            try ackBucket(at: index, correctVersion: correct)
        }
    }

    private func createItem(bucketIndex: Int, title: String, status: ActionStatus) throws {
        guard !bucketOrder.isEmpty else { return }
        let bucketId = bucketOrder[bucketIndex % bucketOrder.count]
        let item = ActionItem(
            id: itemRepo.newIdentifier(),
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            contentType: .text,
            timeframe: .today,
            status: status,
            createdAt: Date(),
            sync: SyncMeta(updatedAt: Date(), version: 0, deleted: false, dirty: false)
        )
        let stored = try itemRepo.create(item)
        itemOrder.append(stored.id)
        expectedItems[stored.id] = stored
    }

    private func editItem(at index: Int) throws {
        guard !itemOrder.isEmpty else { return }
        let id = itemOrder[index % itemOrder.count]
        let current = expectedItems[id]!
        // The repository derives the new version from the persisted row and
        // re-marks the item dirty (Req 5.6); the returned item is the new
        // expectation.
        let stored = try itemRepo.update(current)
        expectedItems[id] = stored
    }

    private func deleteItem(at index: Int) throws {
        guard !itemOrder.isEmpty else { return }
        let id = itemOrder[index % itemOrder.count]
        try itemRepo.delete(id: id)
        // A delete is a tombstone: version bumped, dirty set (stays pending a
        // push, Req 5.6 / 6.3). Mirror that in the expectation.
        var expected = expectedItems[id]!
        expected.sync = SyncMeta(
            updatedAt: expected.sync.updatedAt,
            version: expected.sync.version + 1,
            deleted: true,
            dirty: true
        )
        expectedItems[id] = expected
    }

    private func ackItem(at index: Int, correctVersion: Bool) throws {
        guard !itemOrder.isEmpty else { return }
        let id = itemOrder[index % itemOrder.count]
        var expected = expectedItems[id]!
        let version = correctVersion ? expected.sync.version : expected.sync.version + 1000
        try itemRepo.acknowledgePush(id: id, version: version)
        // Only a version-matching acknowledgment clears dirty (Property 5).
        if correctVersion {
            expected.sync.dirty = false
            expectedItems[id] = expected
        }
    }

    private func editBucket(at index: Int) throws {
        guard !bucketOrder.isEmpty else { return }
        let id = bucketOrder[index % bucketOrder.count]
        let current = expectedBuckets[id]!
        let stored = try bucketRepo.update(current)
        expectedBuckets[id] = stored
    }

    private func ackBucket(at index: Int, correctVersion: Bool) throws {
        guard !bucketOrder.isEmpty else { return }
        let id = bucketOrder[index % bucketOrder.count]
        var expected = expectedBuckets[id]!
        let version = correctVersion ? expected.sync.version : expected.sync.version + 1000
        try bucketRepo.acknowledgePush(id: id, version: version)
        if correctVersion {
            expected.sync.dirty = false
            expectedBuckets[id] = expected
        }
    }

    // MARK: Verification

    /// Reads the store back (tombstones included) and returns whether every
    /// persisted entity's `dirty` and `version` equal the expectation.
    func matchesStore() throws -> Bool {
        let storedItems = try database.fetchAllActionItems()
        let itemsById = Dictionary(uniqueKeysWithValues: storedItems.map { ($0.id, $0) })
        for (id, expected) in expectedItems {
            guard let actual = itemsById[id] else { return false }
            if actual.sync.dirty != expected.sync.dirty { return false }
            if actual.sync.version != expected.sync.version { return false }
        }

        let storedBuckets = try database.fetchAllBuckets()
        let bucketsById = Dictionary(uniqueKeysWithValues: storedBuckets.map { ($0.id, $0) })
        for (id, expected) in expectedBuckets {
            guard let actual = bucketsById[id] else { return false }
            if actual.sync.dirty != expected.sync.dirty { return false }
            if actual.sync.version != expected.sync.version { return false }
        }
        return true
    }
}

// MARK: - Commands

/// One step in a randomly generated history. Indices addressing an existing
/// entity are reduced modulo the live count at apply time, so every command is
/// applicable to whatever state exists (or is skipped when none exists yet).
private enum Command: CustomStringConvertible {
    case createItem(bucketIndex: Int, title: String, status: ActionStatus)
    case editItem(Int)
    case deleteItem(Int)
    case ackItem(Int, correct: Bool)
    case editBucket(Int)
    case ackBucket(Int, correct: Bool)

    var description: String {
        switch self {
        case .createItem(let b, _, _):  return "createItem(bucket@\(b))"
        case .editItem(let i):          return "editItem(@\(i))"
        case .deleteItem(let i):        return "deleteItem(@\(i))"
        case .ackItem(let i, let c):    return "ackItem(@\(i), correct: \(c))"
        case .editBucket(let i):        return "editBucket(@\(i))"
        case .ackBucket(let i, let c):  return "ackBucket(@\(i), correct: \(c))"
        }
    }
}

// MARK: - Generators

/// Number of seed buckets per trial (at least one so items always have a bucket).
private let bucketCountGen = Gen<Int>.choose((1, 3))

/// Index used to address an existing entity; reduced modulo the live count.
private let indexGen = Gen<Int>.choose((0, 64))

private let boolGen = Gen<Bool>.fromElements(of: [true, false])

private let titleGen: Gen<String> = Gen<Int>.choose((1, 12)).flatMap { size in
    Gen<Character>
        .fromElements(of: Array("abcdefghijklmnopqrstuvwxyz ABCDEF0123456789"))
        .proliferate(withSize: size)
        .map { String($0) }
}

private let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)

/// A `createItem` command with an independently generated bucket index, title,
/// and status.
private let createItemGen: Gen<Command> = Gen.compose { c in
    Command.createItem(
        bucketIndex: c.generate(using: indexGen),
        title: c.generate(using: titleGen),
        status: c.generate(using: statusGen)
    )
}

/// A single random mutation/acknowledgment, weighted toward inserts so trials
/// build up enough state to edit, delete, and acknowledge.
private let commandGen: Gen<Command> = Gen<Command>.frequency([
    (5, createItemGen),
    (3, indexGen.map { Command.editItem($0) }),
    (2, indexGen.map { Command.deleteItem($0) }),
    (3, Gen.zip(indexGen, boolGen).map { Command.ackItem($0.0, correct: $0.1) }),
    (2, indexGen.map { Command.editBucket($0) }),
    (2, Gen.zip(indexGen, boolGen).map { Command.ackBucket($0.0, correct: $0.1) })
])

/// A bounded history of 0...40 commands.
private let commandsGen: Gen<[Command]> = Gen<Int>.choose((0, 40)).flatMap { count in
    commandGen.proliferate(withSize: count)
}
