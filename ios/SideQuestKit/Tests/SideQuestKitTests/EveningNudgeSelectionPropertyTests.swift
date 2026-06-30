import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Property 14 — The evening nudge selects only
/// eligible items, capped at 20** (iOS design "Correctness Properties"). Task
/// 13.9.
///
/// **Validates: Requirements 7.13, 7.14**
///
/// Property 14 statement: *for any* set of `ActionItem`s on a given day, the
/// evening nudge's selected items are exactly those that are **not completed**
/// and have **no Task_Reminder set**, truncated to at most **20**; when that
/// eligible set is empty, no nudge is scheduled for the day.
///
/// This targets the pure, I/O-free
/// ``EveningNudgeSelection/eligibleItems(from:itemIdsWithReminder:limit:)`` — the
/// same function the iOS notification layer feeds — so it runs on any host.
///
/// ## Strategy / generators (constraining to the input space intelligently)
///
/// Each trial generates a bounded list of items with unique ids and random
/// statuses (drawn from every `ActionStatus`, so `completed` appears often).
/// The set of item ids that carry a Task_Reminder is built by independently
/// including each item's id, so reminder membership overlaps the item list
/// heavily — every trial mixes the four eligibility quadrants (completed/not ×
/// reminder/no-reminder). Lists are intentionally allowed to exceed 20 items so
/// the cap is exercised, and an empty list is reachable so the
/// no-eligible-items case is covered. A dedicated property additionally forces
/// the "every item ineligible" scenario to nail down Req 7.14.
///
/// Runs ≥100 iterations (the design mandates a minimum of 100; configured to
/// 200 here for extra coverage), matching the sibling property tests.
final class EveningNudgeSelectionPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    private static let epoch = Date(timeIntervalSince1970: 0)
    private static let accountIds = ["acct-A", "acct-B", "acct-C"]
    private static let statusGen = Gen<ActionStatus>.fromElements(of: ActionStatus.allCases)
    private static let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

    // MARK: - Generators

    /// A single item with a placeholder id and random status/content type;
    /// callers assign unique ids by index.
    private static var itemGen: Gen<ActionItem> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            statusGen,
            contentTypeGen
        ).map { account, status, contentType in
            ActionItem(
                id: "tmp",
                accountId: account,
                bucketId: "bucket",
                title: "item",
                description: nil,
                contentType: contentType,
                sourceContent: nil,
                preview: nil,
                timeframe: .today,
                status: status,
                createdAt: epoch,
                sync: SyncMeta(updatedAt: epoch, version: 1, deleted: false)
            )
        }
    }

    /// A bounded list of items with distinct ids. The count range straddles the
    /// 20-item cap so truncation is exercised on roughly half the trials.
    private static var itemListGen: Gen<[ActionItem]> {
        Gen<Int>.fromElements(in: 0...30).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }.map(assignUniqueIds)
    }

    /// An `(items, reminderIds)` pair where `reminderIds` is a random subset of
    /// the items' ids — built by independently flipping a coin per item — so
    /// reminder membership overlaps the list heavily.
    private static var itemsWithReminderSetGen: Gen<([ActionItem], Set<String>)> {
        itemListGen.flatMap { items in
            Gen.sequence(items.map { _ in Gen<Bool>.fromElements(of: [true, false]) })
                .map { flags -> ([ActionItem], Set<String>) in
                    let reminderIds = Set(
                        zip(items, flags).compactMap { item, hasReminder in
                            hasReminder ? item.id : nil
                        }
                    )
                    return (items, reminderIds)
                }
        }
    }

    /// A list in which **every** item is ineligible: each item is either
    /// `completed` or has its id placed in the reminder set (or both). Used to
    /// pin down Req 7.14 (empty selection ⇒ no nudge).
    private static var allIneligibleGen: Gen<([ActionItem], Set<String>)> {
        Gen<Int>.fromElements(in: 0...15).flatMap { count in
            Gen.sequence(Array(repeating: itemGen, count: count))
        }
        .map(assignUniqueIds)
        // Decide per item whether it is made ineligible by completion or reminder.
        .flatMap { items in
            Gen.sequence(items.map { _ in Gen<Bool>.fromElements(of: [true, false]) })
                .map { forceCompleted -> ([ActionItem], Set<String>) in
                    var reminderIds = Set<String>()
                    let adjusted: [ActionItem] = zip(items, forceCompleted).map { item, completed in
                        var copy = item
                        if completed {
                            copy.status = .completed
                        } else {
                            // Not completed ⇒ make ineligible via a reminder.
                            reminderIds.insert(copy.id)
                        }
                        return copy
                    }
                    return (adjusted, reminderIds)
                }
        }
    }

    // MARK: - Helpers

    /// Re-keys items with unique, stable ids so reminder-set accounting and the
    /// order/subsequence checks are never confused by id collisions.
    private static func assignUniqueIds(_ items: [ActionItem]) -> [ActionItem] {
        items.enumerated().map { index, item in
            var copy = item
            copy.id = "item-\(index)"
            return copy
        }
    }

    /// Independent reference implementation of the eligible-item selection used
    /// to assert exact equality (order + filter + cap together).
    private static func expectedSelection(
        _ items: [ActionItem],
        _ reminderIds: Set<String>
    ) -> [String] {
        let eligible = items.filter { $0.status != .completed && !reminderIds.contains($0.id) }
        return Array(eligible.prefix(EveningNudgeSelection.maxItems)).map { $0.id }
    }

    // MARK: - Property 14: selection equals eligible-in-order, capped at 20 (Req 7.13)

    /// The selection is exactly the not-completed, no-reminder items in their
    /// original input order, truncated to the first 20 — combining the filter,
    /// order-preservation, and cap aspects of Property 14.
    func testSelectionIsEligibleItemsInOrderCappedAtTwenty() {
        property("evening nudge = eligible items in order, capped at 20 (Property 14, Req 7.13)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemsWithReminderSetGen) { (input: ([ActionItem], Set<String>)) in
                let (items, reminderIds) = input
                let selectedIds = EveningNudgeSelection
                    .eligibleItems(from: items, itemIdsWithReminder: reminderIds)
                    .map { $0.id }

                let expected = Self.expectedSelection(items, reminderIds)
                return (selectedIds == expected)
                    <?> "selection must equal eligible items in input order, truncated to 20"
            }
    }

    // MARK: - Property 14: only eligible items are selected (Req 7.13)

    /// Every selected item is not completed and has no Task_Reminder; no
    /// ineligible item ever leaks into the nudge.
    func testSelectionContainsOnlyEligibleItems() {
        property("every selected item is not-completed and reminder-free (Property 14, Req 7.13)",
                 arguments: Self.checkArgs)
            <- forAll(Self.itemsWithReminderSetGen) { (input: ([ActionItem], Set<String>)) in
                let (items, reminderIds) = input
                let selected = EveningNudgeSelection
                    .eligibleItems(from: items, itemIdsWithReminder: reminderIds)

                let allEligible = selected.allSatisfy { item in
                    item.status != .completed && !reminderIds.contains(item.id)
                }
                let withinCap = selected.count <= EveningNudgeSelection.maxItems
                return (allEligible && withinCap)
                    <?> "selection must contain only eligible items and never exceed 20"
            }
    }

    // MARK: - Property 14: empty selection when nothing is eligible (Req 7.14)

    /// When no item is eligible (every item is completed and/or carries a
    /// reminder), the selection is empty — the caller's cue to omit the nudge.
    func testSelectionIsEmptyWhenNothingEligible() {
        property("no eligible items ⇒ empty selection, so no nudge (Property 14, Req 7.14)",
                 arguments: Self.checkArgs)
            <- forAll(Self.allIneligibleGen) { (input: ([ActionItem], Set<String>)) in
                let (items, reminderIds) = input
                let selected = EveningNudgeSelection
                    .eligibleItems(from: items, itemIdsWithReminder: reminderIds)
                return selected.isEmpty
                    <?> "selection must be empty when every item is ineligible"
            }
    }
}
