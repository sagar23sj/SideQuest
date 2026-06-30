import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Reused Property 8 — Board partitions items by
/// bucket without loss** (iOS design "Reused properties" table; sibling
/// `action-tracker-app` Property 8). Task 4.7.
///
/// **Validates: Requirements 8.1**
///
/// Property 8 statement (as it applies to the iOS Swift domain logic): for any
/// set of `ActionItem`s and `Bucket`s, `Domain.buildBoard(items:buckets:)`
/// partitions the items across the resulting groups so that:
///
/// 1. **No loss, no duplication** — every input item appears *exactly once*
///    across all groups. Concretely, the multiset of item ids taken over all
///    `BoardGroup.items` equals the multiset of input item ids. Items whose
///    `bucketId` matches no supplied bucket are not dropped; they are surfaced
///    in synthetic placeholder groups (see `Domain.buildBoard`).
/// 2. **Correct partition key** — within every group, each item's `bucketId`
///    equals that group's `bucket.id`. No item lands in the wrong group.
///
/// ## Strategy / generators (constraining to the input space intelligently)
///
/// Each trial generates a small pool of *known* bucket ids (turned into real
/// `Bucket`s) and a disjoint pool of *unknown* bucket ids (never given a
/// bucket). Items are then generated to reference either pool, so a single
/// trial mixes items that map to existing buckets with items that reference
/// ids absent from the bucket list — exercising both the normal partition and
/// the placeholder-group fallback. Duplicate `createdAt` values and shared
/// bucket ids are common (the pools are deliberately tiny), so grouping and the
/// "exactly once" accounting are stressed across many collisions.
///
/// Runs ≥100 iterations (the design mandates a minimum of 100; configured to
/// 200 here for extra coverage), matching the sibling property tests.
final class BoardPartitioningPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    private static let epoch = Date(timeIntervalSince1970: 0)
    private static let accountIds = ["acct-A", "acct-B", "acct-C"]

    /// Bucket ids that WILL be backed by a real `Bucket` in the board input.
    private static let knownBucketIds = ["bk-1", "bk-2", "bk-3"]

    /// Bucket ids that will NEVER be backed by a `Bucket`, so items referencing
    /// them must surface in placeholder groups rather than being lost.
    private static let unknownBucketIds = ["bk-UNKNOWN-1", "bk-UNKNOWN-2"]

    /// All bucket ids an item may reference (mix of known + unknown).
    private static var anyBucketIdGen: Gen<String> {
        Gen.fromElements(of: knownBucketIds + unknownBucketIds)
    }

    /// A small whole-second spread so ties on `createdAt` happen often.
    private static let createdAtGen: Gen<Date> = Gen<Int>.fromElements(in: 0...5)
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)
    private static let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

    // MARK: - Generators

    /// A single item with a placeholder id; callers assign unique ids by index.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            anyBucketIdGen,
            createdAtGen,
            statusGen,
            contentTypeGen
        ).map { account, bucketId, createdAt, status, contentType in
            ActionItem(
                id: "tmp",
                accountId: account,
                bucketId: bucketId,
                title: "item",
                description: nil,
                contentType: contentType,
                sourceContent: nil,
                preview: nil,
                timeframe: .today,
                status: status,
                createdAt: createdAt,
                sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
            )
        }
    }

    /// A bounded list of items with distinct ids (so "exactly once" accounting
    /// is unambiguous).
    private static var itemListGen: Gen<[ActionItem]> {
        Gen<Int>.fromElements(in: 0...12).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map(assignUniqueIds)
    }

    /// A subset (possibly empty, possibly all) of the known bucket ids, turned
    /// into real `Bucket`s in a random order. Items may also reference unknown
    /// ids that are intentionally never in this list.
    private static var bucketListGen: Gen<[Bucket]> {
        // Each known id is independently included or not, then shuffled.
        Gen.sequence(knownBucketIds.map { id in
            Gen<Bool>.fromElements(of: [true, false]).map { include in include ? id : nil }
        }).map { included in
            included.compactMap { $0 }.map(makeBucket(id:))
        }
    }

    // MARK: - Helpers

    private static func makeBucket(id: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountIds[0],
            name: "bucket-\(id)",
            notStartedColor: "#100000",
            inProgressColor: "#001000",
            completedColor: "#000010",
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
        )
    }

    /// Re-keys items with unique, stable ids so the multiset comparison is
    /// never confused by accidental id collisions from the generator.
    private static func assignUniqueIds(_ items: [ActionItem]) -> [ActionItem] {
        items.enumerated().map { index, item in
            var copy = item
            copy.id = "item-\(index)"
            return copy
        }
    }

    /// Multiset of ids, represented as id -> count.
    private static func idCounts(_ ids: [String]) -> [String: Int] {
        ids.reduce(into: [:]) { counts, id in counts[id, default: 0] += 1 }
    }

    // MARK: - Property 8: no item is lost or duplicated (Req 8.1)

    /// The multiset of item ids across every group equals the multiset of input
    /// item ids: nothing is dropped and nothing is duplicated, including items
    /// whose `bucketId` has no matching bucket (surfaced in placeholder groups).
    func testEveryItemAppearsExactlyOnce() {
        property("board preserves every item exactly once (Property 8, Req 8.1)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemListGen, Self.bucketListGen) { (items: [ActionItem], buckets: [Bucket]) in
                let board = Domain.buildBoard(items: items, buckets: buckets)
                let outputIds = board.groups.flatMap { group in group.items.map { $0.item.id } }

                let inputCounts = Self.idCounts(items.map { $0.id })
                let outputCounts = Self.idCounts(outputIds)

                return (outputCounts == inputCounts)
                    <?> "output id multiset must equal input id multiset"
            }
    }

    // MARK: - Property 8: every item lands in the group for its bucketId (Req 8.1)

    /// Within every group, each item's `bucketId` equals the group's
    /// `bucket.id` — items are partitioned by the correct key.
    func testEveryItemMatchesItsGroupBucket() {
        property("every grouped item's bucketId matches its group (Property 8, Req 8.1)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemListGen, Self.bucketListGen) { (items: [ActionItem], buckets: [Bucket]) in
                let board = Domain.buildBoard(items: items, buckets: buckets)
                let allItemsMatchTheirGroup = board.groups.allSatisfy { group in
                    group.items.allSatisfy { $0.item.bucketId == group.bucket.id }
                }
                return allItemsMatchTheirGroup
                    <?> "each item's bucketId must equal its group's bucket id"
            }
    }
}
