import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Reused Property 2 — Confirming capture creates a
/// not-started item preserving bucket/timeframe** (task 8.4).
///
/// **Validates: Requirements 4.5**
///
/// > WHEN a User confirms the selected Bucket and Timeframe for a Shared_Item,
/// > THE App SHALL create an Action_Item with Action_Status "not started" in the
/// > Local_Store.
///
/// This re-implements the sibling Android property (`ConfirmCapturePropertyTest`)
/// against the Swift `CaptureConfirmer` / `CaptureDraft.makeActionItem` confirm
/// path so the two clients are equivalent on the capture-confirm contract.
///
/// ## Strategy
///
/// For each trial SwiftCheck generates a complete, confirmable capture: a set of
/// seed buckets (1...3), a `CaptureDraft` over any supported `ContentType`, and a
/// `CategorizationSelection` whose bucket is one of the seeded buckets (its
/// generated index is reduced modulo the live bucket count so the foreign key
/// always resolves) and whose timeframe is any valid `Timeframe`.
///
/// The draft and selection are confirmed through the real `CaptureConfirmer`
/// over a real on-disk `SideQuestDatabase` + `ActionItemRepository` (the kit's
/// `DatabasePool` has no in-memory mode, so a unique temp file is used per
/// trial). The property asserts the universal post-conditions of a confirmed
/// capture:
///
/// 1. the result is `.saved` (a complete selection always commits — Req 4.5);
/// 2. the saved item has status `.notStarted` (Req 4.5);
/// 3. it preserves the confirmed `bucketId`, `timeframe`, and `accountId`
///    exactly (Req 4.5);
/// 4. the item is durably persisted — reading the store back yields exactly that
///    one item, byte-for-byte equal to the returned item (the create committed).
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **A bucket always exists** (count ≥ 1) and the selection's bucket index is
///   taken modulo the live count, so every confirmation targets a real bucket
///   and the create's `actionItem.bucketId` foreign key resolves — this trial is
///   about the *success* path, so a genuine failure would be a false negative.
/// - **The selection is always complete** (both a bucket and a timeframe), which
///   is the precondition of Req 4.5 ("WHEN a User confirms…"); the incomplete /
///   failure paths are covered by `CaptureConfirmationTests` and
///   `CommitFailureAtomicityPropertyTests`.
/// - **`Timeframe.specificDate`** payloads are generated on UTC-midnight
///   boundaries (`day * 86_400`) so the value round-trips through the
///   `yyyy-MM-dd` calendar-date column unchanged, keeping the persisted-equality
///   assertion exact.
/// - **The creation instant** is a fixed clock: the property is about status and
///   selection preservation, not timing, and a deterministic `createdAt` keeps
///   the persisted-equality comparison stable.
final class CaptureConfirmPropertyTests: XCTestCase {

    /// A fixed clock so the item's `createdAt`/`sync` timestamps are
    /// deterministic across the confirm and the read-back comparison.
    private let clock: RepositoryClock = {
        { Date(timeIntervalSince1970: 1_700_000_500) }
    }()

    /// Reused Property 2 / Req 4.5: confirming a capture creates a not-started
    /// item preserving the chosen bucket and timeframe, and persists it.
    func testConfirmingCaptureCreatesNotStartedItemPreservingSelection() {
        property(
            "Reused Property 2: a confirmed capture creates a persisted not-started item preserving bucket/timeframe",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(scenarioGen) { scenario in
            return self.confirmPreservesSelection(scenario)
        }
    }

    // MARK: - Property under test

    private func confirmPreservesSelection(_ scenario: Scenario) -> Bool {
        let path = NSTemporaryDirectory()
            + "SideQuestCaptureConfirmProp-\(UUID().uuidString).sqlite"

        // SQLite in WAL mode keeps sidecar files; remove all three afterwards.
        defer {
            for suffix in ["", "-wal", "-shm"] {
                try? FileManager.default.removeItem(atPath: path + suffix)
            }
        }

        do {
            let database = try SideQuestDatabase(path: path)
            let bucketRepo = BucketRepository(database: database, now: clock)
            let itemRepo = ActionItemRepository(database: database, now: clock)
            let confirmer = CaptureConfirmer(repository: itemRepo, now: clock)

            // Seed buckets so the confirmed bucket id always resolves.
            var bucketIds: [String] = []
            for seed in scenario.buckets {
                let id = UUID().uuidString.lowercased()
                _ = try bucketRepo.create(seed.makeBucket(id: id, accountId: scenario.accountId))
                bucketIds.append(id)
            }

            let bucketId = bucketIds[scenario.bucketIndex % bucketIds.count]
            let draft = scenario.draft.makeDraft()
            let selection = CategorizationSelection(
                bucketId: bucketId,
                timeframe: scenario.timeframe
            )

            let result = confirmer.confirm(
                draft: draft,
                selection: selection,
                accountId: scenario.accountId,
                preview: nil
            )

            // (1) A complete selection over an existing bucket always commits.
            guard case .saved(let item) = result else { return false }

            // (2) Not started; (3) preserves bucket, timeframe, account (Req 4.5).
            guard item.status == .notStarted else { return false }
            guard item.bucketId == bucketId else { return false }
            guard item.timeframe == scenario.timeframe else { return false }
            guard item.accountId == scenario.accountId else { return false }

            // (4) Durably persisted: exactly that one item, byte-for-byte equal.
            let persisted = try database.fetchAllActionItems()
            guard persisted.count == 1, persisted.first == item else { return false }

            return true
        } catch {
            XCTFail("Capture-confirm property trial threw unexpectedly: \(error)")
            return false
        }
    }
}

// MARK: - Scenario model

/// A generated confirmable capture: seed buckets, the account, the draft to
/// confirm, and the bucket index + timeframe forming the selection.
private struct Scenario {
    var accountId: String
    var buckets: [BucketSeed]
    var bucketIndex: Int
    var draft: DraftSeed
    var timeframe: Timeframe
}

// MARK: - Seeds

private struct BucketSeed {
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String

    func makeBucket(id: String, accountId: String) -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: notStartedColor,
            inProgressColor: inProgressColor,
            completedColor: completedColor,
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: true)
        )
    }
}

/// Generated fields for a `CaptureDraft` over any supported content type. A link
/// draft carries a URL so `makeActionItem` attaches the unresolved fallback
/// preview, exercising that branch of the confirm path.
private struct DraftSeed {
    var title: String
    var contentType: ContentType

    func makeDraft() -> CaptureDraft {
        switch contentType {
        case .link:
            let url = URL(string: "https://example.com/" + UUID().uuidString.lowercased())
            return CaptureDraft(contentType: .link, title: title, sourceContent: title, linkURL: url)
        case .text:
            return CaptureDraft(contentType: .text, title: title, sourceContent: title)
        case .image:
            return CaptureDraft(contentType: .image, title: title)
        case .videoRef:
            return CaptureDraft(contentType: .videoRef, title: title)
        }
    }
}

// MARK: - Generators

/// Curated, SQLite-safe character set (no NUL/control bytes), with a few
/// non-ASCII scalars to exercise unicode handling.
private let stringChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-./éü✓")

private let stringGen: Gen<String> = Gen<Int>.choose((1, 16)).flatMap { size in
    Gen<Character>.fromElements(of: stringChars).proliferate(withSize: size).map { String($0) }
}

private let accountIdGen = Gen<String>.fromElements(of: ["acct-1", "acct-2", "acct-3"])

private let colorGen = Gen<String>.fromElements(of: [
    "#FF0000", "#00FF00", "#0000FF", "#123456", "#ABCDEF", "#000000", "#FFFFFF"
])

/// Index used to address a seeded bucket; reduced modulo the live count.
private let indexGen = Gen<Int>.choose((0, 64))

private let contentTypeGen = Gen<ContentType>.fromElements(of: ContentType.allCases)

/// UTC-midnight dates for the `Timeframe.specificDate` calendar-date payload, so
/// the value round-trips exactly through the `yyyy-MM-dd` column.
private let calendarDateGen: Gen<Date> = Gen<Int>.choose((16_000, 20_000))
    .map { Date(timeIntervalSince1970: TimeInterval($0 * 86_400)) }

private let timeframeGen: Gen<Timeframe> = Gen.one(of: [
    Gen.pure(Timeframe.today),
    Gen.pure(Timeframe.withinADay),
    Gen.pure(Timeframe.withinAWeek),
    calendarDateGen.map { Timeframe.specificDate($0) }
])

private let bucketSeedGen: Gen<BucketSeed> = Gen.compose { c in
    BucketSeed(
        name: c.generate(using: stringGen),
        notStartedColor: c.generate(using: colorGen),
        inProgressColor: c.generate(using: colorGen),
        completedColor: c.generate(using: colorGen)
    )
}

private let draftSeedGen: Gen<DraftSeed> = Gen.compose { c in
    DraftSeed(
        title: c.generate(using: stringGen),
        contentType: c.generate(using: contentTypeGen)
    )
}

private let scenarioGen: Gen<Scenario> = Gen.compose { c in
    // At least one bucket so the confirmed bucket id always resolves (success path).
    let bucketCount = c.generate(using: Gen<Int>.choose((1, 3)))
    return Scenario(
        accountId: c.generate(using: accountIdGen),
        buckets: c.generate(using: bucketSeedGen.proliferate(withSize: bucketCount)),
        bucketIndex: c.generate(using: indexGen),
        draft: c.generate(using: draftSeedGen),
        timeframe: c.generate(using: timeframeGen)
    )
}
