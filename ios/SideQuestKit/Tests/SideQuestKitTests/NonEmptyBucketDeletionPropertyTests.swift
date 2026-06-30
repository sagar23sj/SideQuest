import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Reused Property 6 — Deleting a non-empty bucket
/// reassigns or deletes all items** (iOS design "Reused properties" table;
/// sibling `action-tracker-app` Property 6). Task 7.2.
///
/// **Validates: Requirements 9.4, 9.5**
///
/// Property 6 statement (as it applies to the iOS Swift domain logic): for any
/// list of `ActionItem`s and any non-empty bucket `bucketId`,
/// `Domain.applyBucketDeletion(items:bucketId:strategy:)` preserves **total
/// item accounting** — no item contained in the deleted bucket is silently
/// lost, and items outside the bucket are never touched:
///
/// 1. **Reassign moves all contained items to the target with no loss**
///    (Req 9.4) — for `.reassign(target)` with `target != bucketId`:
///    - the returned list has the *same size* as the input (nothing dropped,
///      nothing duplicated): the multiset of item ids is unchanged;
///    - every item that was in `bucketId` now has `bucketId == target`, and
///      `reassignedItemIds` is exactly those ids in their original order;
///    - no item remains in the deleted bucket, and `deletedItemIds` is empty;
///    - items outside `bucketId` keep their `bucketId` and relative order.
///
/// 2. **Delete removes exactly the contained items** (Req 9.4, 9.5) — for
///    `.deleteItems`:
///    - the returned list size decreases by *precisely* the bucket's item
///      count: `output.count == input.count - containedCount`;
///    - `deletedItemIds` is exactly the contained ids (original order) and
///      `reassignedItemIds` is empty;
///    - no remaining item references `bucketId`;
///    - items outside `bucketId` survive unchanged, in their original order.
///
/// ## Strategy / generators (constraining to the input space intelligently)
///
/// Each trial generates a bounded pool of items over a tiny set of bucket ids
/// (so collisions and shared buckets are common) and then derives the deleted
/// `bucketId` from an item that actually exists, guaranteeing the bucket is
/// **non-empty** — the precondition Property 6 is about. The reassign target is
/// drawn from the same pool, so trials include the documented no-op case
/// (`target == bucketId`) as well as genuine moves; the assertions below hold
/// for both. Item ids are re-keyed to be unique so the multiset accounting is
/// unambiguous.
///
/// Runs ≥100 iterations (the design mandates a minimum of 100; configured to
/// 200 here for extra coverage), matching the sibling property tests.
final class NonEmptyBucketDeletionPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    private static let epoch = Date(timeIntervalSince1970: 0)
    private static let accountIds = ["acct-A", "acct-B"]

    /// The small pool of bucket ids items may reference; deliberately tiny so a
    /// single trial mixes the deleted bucket, the reassign target, and
    /// untouched buckets, with frequent collisions.
    private static let bucketIds = ["bk-1", "bk-2", "bk-3"]

    private static let createdAtGen: Gen<Date> = Gen<Int>.fromElements(in: 0...5)
        .map { Date(timeIntervalSince1970: TimeInterval($0)) }

    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)
    private static let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

    // MARK: - Generators

    /// A single item with a placeholder id; callers assign unique ids by index.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            Gen.fromElements(of: bucketIds),
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

    /// A **non-empty** list of items (1...12) with distinct ids, so "exactly
    /// once" accounting is unambiguous and at least one bucket is non-empty.
    private static var nonEmptyItemListGen: Gen<[ActionItem]> {
        Gen<Int>.fromElements(in: 1...12).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map(assignUniqueIds)
    }

    private static let targetGen = Gen<String>.fromElements(of: bucketIds)

    // MARK: - Helpers

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

    // MARK: - Property 6: reassign moves all contained items, no loss (Req 9.4)

    /// `.reassign(target)` of a non-empty bucket preserves every item (same id
    /// multiset, same size), reassigns exactly the contained ids, deletes none,
    /// moves every contained item to `target`, and leaves all other items
    /// (`bucketId` and order) untouched.
    func testReassignMovesAllContainedItemsWithoutLoss() {
        property("reassign deletes a non-empty bucket without losing items (Property 6, Req 9.4)",
                 arguments: Self.checkArgs)
            <- forAll(Self.nonEmptyItemListGen, Self.targetGen) { (items: [ActionItem], target: String) in
                // Delete a bucket that is guaranteed non-empty: the bucket of
                // the first item.
                let bucketId = items[0].bucketId
                let containedIds = items.filter { $0.bucketId == bucketId }.map { $0.id }

                let outcome = Domain.applyBucketDeletion(
                    items: items,
                    bucketId: bucketId,
                    strategy: .reassign(targetBucketId: target)
                )

                // No loss / no duplication: id multiset unchanged.
                let sameMultiset = Self.idCounts(outcome.items.map { $0.id })
                    == Self.idCounts(items.map { $0.id })

                // Accounting fields: exactly the contained ids reassigned, none deleted.
                let accountingExact = outcome.reassignedItemIds == containedIds
                    && outcome.deletedItemIds.isEmpty

                // Every contained item now lives in the target bucket.
                let movedToTarget = outcome.items
                    .filter { containedIds.contains($0.id) }
                    .allSatisfy { $0.bucketId == target }

                // Items not contained keep their bucket and relative order.
                let othersPreserved = outcome.items.filter { !containedIds.contains($0.id) }
                    == items.filter { !containedIds.contains($0.id) }

                // When the target differs from the deleted bucket, nothing
                // remains in the deleted bucket (it is emptied).
                let sourceEmptied = target == bucketId
                    || !outcome.items.contains { $0.bucketId == bucketId }

                return (sameMultiset <?> "reassign must preserve the item id multiset (no loss)")
                    ^&&^ (accountingExact <?> "reassignedItemIds must equal contained ids; none deleted")
                    ^&&^ (movedToTarget <?> "every contained item must move to the target bucket")
                    ^&&^ (othersPreserved <?> "items outside the deleted bucket must be untouched and ordered")
                    ^&&^ (sourceEmptied <?> "a distinct target leaves no item in the deleted bucket")
            }
    }

    // MARK: - Property 6: delete removes exactly the contained items (Req 9.4, 9.5)

    /// `.deleteItems` of a non-empty bucket removes precisely the contained
    /// items (count drops by the contained count), reports exactly those ids as
    /// deleted (none reassigned), leaves no remaining item in the bucket, and
    /// preserves every other item unchanged and in order.
    func testDeleteItemsRemovesExactlyContainedItems() {
        property("deleteItems removes exactly the bucket's items (Property 6, Req 9.4, 9.5)",
                 arguments: Self.checkArgs)
            <- forAll(Self.nonEmptyItemListGen) { (items: [ActionItem]) in
                let bucketId = items[0].bucketId
                let contained = items.filter { $0.bucketId == bucketId }
                let containedIds = contained.map { $0.id }

                let outcome = Domain.applyBucketDeletion(
                    items: items,
                    bucketId: bucketId,
                    strategy: .deleteItems
                )

                // Count drops by exactly the bucket's item count.
                let countExact = outcome.items.count == items.count - contained.count

                // Accounting fields: exactly the contained ids deleted, none reassigned.
                let accountingExact = outcome.deletedItemIds == containedIds
                    && outcome.reassignedItemIds.isEmpty

                // No remaining item references the deleted bucket.
                let sourceEmptied = !outcome.items.contains { $0.bucketId == bucketId }

                // Remaining items are exactly the non-contained items, unchanged
                // and in their original relative order.
                let survivorsPreserved = outcome.items == items.filter { $0.bucketId != bucketId }

                return (countExact <?> "deleteItems must drop exactly the contained count")
                    ^&&^ (accountingExact <?> "deletedItemIds must equal contained ids; none reassigned")
                    ^&&^ (sourceEmptied <?> "no remaining item may reference the deleted bucket")
                    ^&&^ (survivorsPreserved <?> "non-contained items must survive unchanged and ordered")
            }
    }
}
