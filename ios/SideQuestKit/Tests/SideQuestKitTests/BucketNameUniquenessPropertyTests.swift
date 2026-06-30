import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property-based tests for **Reused Property 5 — Bucket names unique per
/// account** (iOS design "Reused properties" table; sibling
/// `action-tracker-app` Property 5).
///
/// **Validates: Requirements 9.2**
///
/// Property 5 statement (as it applies to the iOS Swift domain logic): for any
/// account and set of existing buckets, a candidate bucket name that — after
/// trimming surrounding whitespace and comparing case-insensitively — matches
/// an existing **non-deleted** bucket name **for the same account** is rejected
/// as a duplicate. Names differing only in letter case or surrounding
/// whitespace from an existing one therefore collide. Names that are unique
/// within the account — or that match only buckets belonging to OTHER accounts,
/// or only tombstoned (deleted) buckets — are accepted (length permitting).
///
/// Subject under test:
///   * `Domain.validateBucketName(_:accountId:existing:excludingBucketId:)`
///   * `Domain.isBucketNameAvailable(...)`
///   * `Domain.normalizeBucketName(...)`
///
/// These tests deliberately constrain generated candidate names to the valid
/// 1–50 trimmed-character window so that the *length* rule (Req 9.3,
/// Property 19, task 4.3) never fires here and the *uniqueness* behaviour is
/// isolated. Each property runs ≥100 iterations (the design mandates a minimum
/// of 100; we configure 200 for extra coverage).
final class BucketNameUniquenessPropertyTests: XCTestCase {

    // MARK: - Iteration count (design: ≥100 iterations per property)

    private static let checkArgs = CheckerArguments(maxAllowableSuccessfulTests: 200)

    // MARK: - Fixed pools (small, so collisions occur frequently)

    /// A handful of accounts so the per-account scoping is meaningfully
    /// exercised (collisions and non-collisions across accounts).
    private static let accountIds = ["acct-A", "acct-B", "acct-C"]

    /// Base names with valid trimmed lengths (1–50 chars). Kept short and
    /// reused so that random buckets frequently share a normalized name,
    /// driving the duplicate path.
    private static let baseNames = ["Travel", "Work", "ideas", "Home", "Fitness", "to-do"]

    private static let epoch = Date(timeIntervalSince1970: 0)

    // MARK: - Generators

    /// Produces a case/whitespace variant of `base`: same letters (so the
    /// *normalized* form is unchanged), but with toggled casing and/or added
    /// surrounding whitespace. Because only leading/trailing whitespace is
    /// added, the trimmed length equals `base.count`, keeping it within the
    /// valid window.
    private static func variantGen(of base: String) -> Gen<String> {
        let transforms: [(String) -> String] = [
            { $0 },                                   // identity
            { $0.uppercased() },                      // ALL CAPS
            { $0.lowercased() },                      // all lower
            { " " + $0 },                             // leading space
            { $0 + " " },                             // trailing space
            { "  " + $0 + "  " },                     // surrounding spaces
            { "\t" + $0 + "\n" },                     // surrounding tab/newline
            { string in                               // alternating case
                String(string.enumerated().map { idx, ch in
                    idx.isMultiple(of: 2)
                        ? Character(ch.uppercased())
                        : Character(ch.lowercased())
                })
            }
        ]
        return Gen.fromElements(of: transforms).map { $0(base) }
    }

    /// A stored bucket name: a base name run through a random case/whitespace
    /// variant, so persisted names vary in case/whitespace yet collide on
    /// normalization.
    private static var storedNameGen: Gen<String> {
        Gen.fromElements(of: baseNames).flatMap(variantGen(of:))
    }

    /// A single bucket with a random account, name, and deleted flag. The `id`
    /// is a placeholder; callers reassign unique ids by index.
    private static var bucketGen: Gen<Bucket> {
        Gen.zip(
            Gen.fromElements(of: accountIds),
            storedNameGen,
            Gen<Bool>.fromElements(of: [true, false])
        ).map { account, name, deleted in
            makeBucket(id: "tmp", account: account, name: name, deleted: deleted)
        }
    }

    /// A bounded-length list of buckets with distinct ids.
    private static var bucketListGen: Gen<[Bucket]> {
        Gen<Int>.fromElements(in: 0...8).flatMap { count in
            Gen.sequence(Array(repeating: bucketGen, count: count))
        }.map(assignUniqueIds)
    }

    // MARK: - Helpers

    private static func makeBucket(id: String, account: String, name: String, deleted: Bool) -> Bucket {
        Bucket(
            id: id,
            accountId: account,
            name: name,
            notStartedColor: "#100000",
            inProgressColor: "#001000",
            completedColor: "#000010",
            sync: SyncMeta(updatedAt: epoch, version: 1, deleted: deleted)
        )
    }

    /// Re-keys a list of buckets with unique, stable ids so the uniqueness
    /// check is never confused by accidental id collisions.
    private static func assignUniqueIds(_ buckets: [Bucket]) -> [Bucket] {
        buckets.enumerated().map { index, bucket in
            var copy = bucket
            copy.id = "b-\(index)"
            return copy
        }
    }

    /// Independent oracle for the duplicate condition, expressed directly from
    /// the Property 5 statement (trim + case-insensitive, same account,
    /// non-deleted, not excluded). Intentionally written plainly so it does not
    /// merely mirror the implementation under test.
    private static func isDuplicate(
        candidate: String,
        accountId: String,
        existing: [Bucket],
        excludingBucketId: String?
    ) -> Bool {
        let target = candidate.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return existing.contains { bucket in
            bucket.accountId == accountId
                && !bucket.sync.deleted
                && bucket.id != excludingBucketId
                && bucket.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == target
        }
    }

    // MARK: - Property 5: general oracle agreement (Req 9.2)

    /// For arbitrary buckets, account, and a valid-length candidate name, the
    /// validator returns `.duplicateName` exactly when a non-deleted bucket for
    /// the same account shares the candidate's normalized name, and `.valid`
    /// (carrying the trimmed candidate) otherwise.
    func testValidateMatchesDuplicateOracle() {
        let scenarioGen = Gen.zip(
            Self.bucketListGen,
            Gen.fromElements(of: Self.accountIds),
            Self.storedNameGen // candidate (valid length, varied case/whitespace)
        )

        property("validateBucketName == duplicate-oracle (Property 5, Req 9.2)",
                 arguments: Self.checkArgs)
            <- forAll(scenarioGen) { (buckets: [Bucket], account: String, candidate: String) in
                let result = Domain.validateBucketName(
                    candidate,
                    accountId: account,
                    existing: buckets,
                    excludingBucketId: nil
                )
                let trimmed = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                let expectDuplicate = Self.isDuplicate(
                    candidate: candidate,
                    accountId: account,
                    existing: buckets,
                    excludingBucketId: nil
                )
                // `isBucketNameAvailable` must agree with the duplicate oracle.
                let available = Domain.isBucketNameAvailable(
                    candidate,
                    accountId: account,
                    existing: buckets,
                    excludingBucketId: nil
                )
                let expected: BucketNameValidation = expectDuplicate
                    ? .duplicateName
                    : .valid(normalizedName: trimmed)
                return (result == expected) ^&&^ (available == !expectDuplicate)
            }
    }

    // MARK: - Property 5: case/whitespace variants collide (Req 9.2)

    /// A candidate that is any case/whitespace variant of an existing,
    /// non-deleted bucket name for the SAME account is always rejected as a
    /// duplicate — regardless of any other (noise) buckets present.
    func testCaseAndWhitespaceVariantsCollide() {
        let collisionGen: Gen<(buckets: [Bucket], account: String, candidate: String)> =
            Gen.zip(
                Gen.fromElements(of: Self.accountIds),
                Gen.fromElements(of: Self.baseNames),
                Self.bucketListGen
            ).flatMap { account, base, noise in
                Gen.zip(Self.variantGen(of: base), Self.variantGen(of: base))
                    .map { storedVariant, candidateVariant in
                        let target = Self.makeBucket(
                            id: "target",
                            account: account,
                            name: storedVariant,
                            deleted: false
                        )
                        // Target first so its id stays unique after re-keying.
                        let buckets = Self.assignUniqueIds([target] + noise)
                        return (buckets, account, candidateVariant)
                    }
            }

        property("case/whitespace variants collide (Property 5, Req 9.2)",
                 arguments: Self.checkArgs)
            <- forAll(collisionGen) { scenario in
                let result = Domain.validateBucketName(
                    scenario.candidate,
                    accountId: scenario.account,
                    existing: scenario.buckets,
                    excludingBucketId: nil
                )
                return result == .duplicateName
            }
    }

    // MARK: - Property 5: matches in OTHER accounts do not collide (Req 9.2)

    /// A candidate whose account owns NO bucket is accepted even when other
    /// accounts hold buckets with the same normalized name (uniqueness is
    /// per-account).
    func testOtherAccountMatchesAreAccepted() {
        // Candidate account is deliberately outside the pool the noise buckets
        // draw from, so no noise bucket can belong to it.
        let soloAccount = "acct-SOLO"
        let acceptGen: Gen<(buckets: [Bucket], candidate: String)> =
            Gen.zip(Self.bucketListGen, Self.storedNameGen).map { ($0, $1) }

        property("other-account matches are accepted (Property 5, Req 9.2)",
                 arguments: Self.checkArgs)
            <- forAll(acceptGen) { scenario in
                let result = Domain.validateBucketName(
                    scenario.candidate,
                    accountId: soloAccount,
                    existing: scenario.buckets,
                    excludingBucketId: nil
                )
                let trimmed = scenario.candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                return result == .valid(normalizedName: trimmed)
            }
    }

    // MARK: - Property 5: tombstoned buckets do not reserve their name (Req 9.2)

    /// A candidate that matches only deleted (tombstoned) buckets for its own
    /// account is accepted — deleted buckets do not reserve their name.
    func testTombstonedBucketsDoNotReserveName() {
        let account = "acct-A"
        let tombstoneGen: Gen<(buckets: [Bucket], candidate: String)> =
            Gen.zip(Gen.fromElements(of: Self.baseNames), Gen<Int>.fromElements(in: 1...5))
                .flatMap { base, count in
                    Gen.zip(
                        Self.variantGen(of: base),
                        Gen.sequence(Array(repeating: Self.variantGen(of: base), count: count))
                    ).map { candidateVariant, storedVariants in
                        let buckets = Self.assignUniqueIds(storedVariants.map { name in
                            Self.makeBucket(id: "tmp", account: account, name: name, deleted: true)
                        })
                        return (buckets, candidateVariant)
                    }
                }

        property("tombstoned buckets do not reserve their name (Property 5, Req 9.2)",
                 arguments: Self.checkArgs)
            <- forAll(tombstoneGen) { scenario in
                let result = Domain.validateBucketName(
                    scenario.candidate,
                    accountId: account,
                    existing: scenario.buckets,
                    excludingBucketId: nil
                )
                let trimmed = scenario.candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                return result == .valid(normalizedName: trimmed)
            }
    }

    // MARK: - Property 5: rename excludes the bucket being renamed (Req 9.2)

    /// When renaming, keeping a bucket's own name (or only changing its case or
    /// surrounding whitespace) is accepted because the bucket being renamed is
    /// excluded from the uniqueness check.
    func testRenameToOwnNameVariantIsAccepted() {
        let account = "acct-A"
        let renameGen: Gen<(buckets: [Bucket], candidate: String)> =
            Gen.zip(Gen.fromElements(of: Self.baseNames), Self.bucketListGen)
                .flatMap { base, noise in
                    Gen.zip(Self.variantGen(of: base), Self.variantGen(of: base))
                        .map { storedVariant, candidateVariant in
                            // The renamed bucket keeps a fixed id we then exclude.
                            let renamed = Self.makeBucket(
                                id: "renamed",
                                account: account,
                                name: storedVariant,
                                deleted: false
                            )
                            // Noise buckets are forced to OTHER accounts so they
                            // cannot independently collide with the candidate.
                            let otherNoise = noise.map { bucket -> Bucket in
                                var copy = bucket
                                copy.accountId = "acct-OTHER"
                                return copy
                            }
                            let buckets = Self.assignUniqueIds([renamed] + otherNoise)
                            // After re-keying, the renamed bucket is "b-0".
                            return (buckets, candidateVariant)
                        }
                }

        property("rename to own-name variant is accepted (Property 5, Req 9.2)",
                 arguments: Self.checkArgs)
            <- forAll(renameGen) { scenario in
                let result = Domain.validateBucketName(
                    scenario.candidate,
                    accountId: account,
                    existing: scenario.buckets,
                    excludingBucketId: "b-0" // the renamed bucket
                )
                let trimmed = scenario.candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                return result == .valid(normalizedName: trimmed)
            }
    }
}
