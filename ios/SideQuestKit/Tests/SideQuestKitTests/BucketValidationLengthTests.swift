import XCTest
import SwiftCheck
@testable import SideQuestKit

/// Property tests for the bucket-name length rule of
/// `Domain.validateBucketName(_:accountId:existing:excludingBucketId:)`
/// (`Sources/SideQuestKit/Domain/BucketValidation.swift`).
///
/// ## Property 19: Bucket-name length validation accepts exactly 1–50 trimmed characters
///
/// *For any* candidate bucket name, creation or rename is accepted (with
/// respect to the length rule) if and only if the name's length after trimming
/// surrounding whitespace is between 1 and 50 inclusive; empty,
/// whitespace-only, and over-50 names are rejected.
///
/// **Validates: Requirements 9.3**
///
/// Req 9.3: "IF a User attempts to create or rename a Bucket with a name that
/// is empty, contains only whitespace, or exceeds 50 characters, THEN THE App
/// SHALL reject the operation and SHALL display a message that the name must be
/// 1 to 50 characters."
///
/// The length is measured in `Character`s (grapheme clusters), matching the
/// implementation's `trimmed.count`. To exercise that consistently the
/// generator builds the trimmed "core" from standalone grapheme clusters (no
/// combining marks, ZWJ joiners, or regional-indicator pairs that would merge
/// across a boundary), so the core's `Character` count equals the number of
/// units used to build it.
final class BucketValidationLengthTests: XCTestCase {

    /// One generated case: a `candidate` as the user would type it (with random
    /// surrounding whitespace), the `core` it trims down to (no surrounding
    /// whitespace), and the known trimmed length in `Character`s.
    private struct Sample: CustomStringConvertible {
        let candidate: String
        let core: String
        let trimmedLength: Int

        var description: String {
            "Sample(trimmedLength: \(trimmedLength), core: \(core.debugDescription), candidate: \(candidate.debugDescription))"
        }
    }

    // MARK: Generators

    /// Standalone grapheme clusters that never combine with a neighbor, so a
    /// joined run of N of them has exactly N `Character`s. Deliberately mixes
    /// ASCII, precomposed accented letters, CJK, and single-scalar emoji to keep
    /// the grapheme-counting honest. None are whitespace.
    private static let coreUnitGen: Gen<String> = Gen<String>.fromElements(
        of: ["a", "B", "z", "Q", "7", "0", "ñ", "é", "ü", "空", "猫", "👍", "🙂", "x", "M", "-", "_"]
    )

    /// A single whitespace character that `trimmingCharacters(in:.whitespacesAndNewlines)` removes.
    private static let whitespaceUnitGen: Gen<String> = Gen<String>.fromElements(
        of: [" ", "\t", "\n", "\u{00A0}"]
    )

    /// A run of 0–3 surrounding whitespace characters (may be empty).
    private static let whitespaceRunGen: Gen<String> =
        Gen<Int>.fromElements(in: 0...3).flatMap { count in
            BucketValidationLengthTests.whitespaceUnitGen
                .proliferate(withSize: count)
                .map { $0.joined() }
        }

    /// Trimmed length distribution. Weighted to hit the spec boundaries (0, 1,
    /// 50, 51) frequently while still sweeping a wider span around them.
    private static let trimmedLengthGen: Gen<Int> = Gen<Int>.frequency([
        (2, Gen.pure(0)),                                      // empty / whitespace-only
        (2, Gen.pure(1)),                                      // min boundary (accept)
        (2, Gen.pure(Domain.maxBucketNameLength)),             // 50: max boundary (accept)
        (2, Gen.pure(Domain.maxBucketNameLength + 1)),         // 51: just over (reject)
        (4, Gen<Int>.fromElements(in: 0...60)),                // wider sweep
    ])

    private static let sampleGen: Gen<Sample> =
        BucketValidationLengthTests.trimmedLengthGen.flatMap { length in
            Gen.zip(
                BucketValidationLengthTests.coreUnitGen
                    .proliferate(withSize: length)
                    .map { $0.joined() },
                Gen.zip(
                    BucketValidationLengthTests.whitespaceRunGen,
                    BucketValidationLengthTests.whitespaceRunGen
                )
            ).map { core, surrounding in
                let (leading, trailing) = surrounding
                return Sample(
                    candidate: leading + core + trailing,
                    core: core,
                    trimmedLength: length
                )
            }
        }

    // MARK: Property 19

    /// Property 19 / Req 9.3: with no existing buckets (so the uniqueness check
    /// never fires), `validateBucketName` returns `.valid(normalizedName: core)`
    /// exactly when the trimmed length is in `1...50`, and `.invalidLength`
    /// when the trimmed length is `0` (empty / whitespace-only) or `> 50`.
    func testLengthValidationAcceptsExactlyOneToFiftyTrimmedCharacters() {
        property(
            "accepted iff trimmed Character-length is in 1...50",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 100)
        ) <- forAllNoShrink(BucketValidationLengthTests.sampleGen) { sample in
            // Guard: the generator must produce a candidate whose trimmed form
            // is exactly `core` with the intended length, otherwise the oracle
            // below would be comparing against the wrong expectation.
            let actualTrimmed = sample.candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard actualTrimmed == sample.core,
                  sample.core.count == sample.trimmedLength
            else {
                return false
            }

            let expected: BucketNameValidation
            if sample.trimmedLength >= Domain.minBucketNameLength,
               sample.trimmedLength <= Domain.maxBucketNameLength {
                expected = .valid(normalizedName: sample.core)
            } else {
                expected = .invalidLength
            }

            let actual = Domain.validateBucketName(
                sample.candidate,
                accountId: "acct-1",
                existing: []
            )

            return actual == expected
        }
    }

    // MARK: Targeted boundary examples (Property 19 / Req 9.3)

    /// An empty name is rejected for length (Req 9.3).
    func testEmptyNameIsInvalidLength() {
        XCTAssertEqual(
            Domain.validateBucketName("", accountId: "acct-1", existing: []),
            .invalidLength
        )
    }

    /// A whitespace-only name trims to empty and is rejected for length (Req 9.3).
    func testWhitespaceOnlyNameIsInvalidLength() {
        XCTAssertEqual(
            Domain.validateBucketName("   \t\n ", accountId: "acct-1", existing: []),
            .invalidLength
        )
    }

    /// A single character (the minimum) is accepted and persisted trimmed (Req 9.3).
    func testSingleCharacterNameIsValid() {
        XCTAssertEqual(
            Domain.validateBucketName("  a  ", accountId: "acct-1", existing: []),
            .valid(normalizedName: "a")
        )
    }

    /// Exactly 50 characters (the maximum) is accepted (Req 9.3).
    func testFiftyCharacterNameIsValid() {
        let name = String(repeating: "a", count: 50)
        XCTAssertEqual(
            Domain.validateBucketName("  \(name)  ", accountId: "acct-1", existing: []),
            .valid(normalizedName: name)
        )
    }

    /// 51 characters (one over the maximum) is rejected for length (Req 9.3).
    func testFiftyOneCharacterNameIsInvalidLength() {
        let name = String(repeating: "a", count: 51)
        XCTAssertEqual(
            Domain.validateBucketName(name, accountId: "acct-1", existing: []),
            .invalidLength
        )
    }
}
