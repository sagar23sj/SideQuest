import XCTest
import Foundation
import Dispatch
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Property 15 — Notification text is bounded and
/// fails soft** (task 13.11).
///
/// **Validates: Requirements 7.16, 7.17**
///
/// > For any LLM_Proxy outcome, the text used for a notification is at most 200
/// > characters; and when the LLM result is unavailable, errored, or not
/// > returned within the timeout, the notification is delivered using non-empty
/// > default text.
///
/// ## What is exercised
///
/// The property drives the pure bounding/fail-soft resolution shared by every
/// caller:
///
/// - `LlmNotificationText.bounded(_:)` — the ≤200-character bound (Req 7.16).
/// - `LLMService.resolvedNotificationText(for:default:)` — collapsing any
///   ``LlmResult`` into the non-empty, bounded string actually delivered,
///   falling back to the bounded default on the timeout / error / unavailable
///   paths (Req 7.17).
///
/// The LLM outcome is held fixed per case by `FixedResultService` so the
/// resolution is tested independent of the network, across:
///   * `.ok` with arbitrary, over-long, blank, whitespace-only, and
///     grapheme-cluster (emoji) text,
///   * `.timedOut`,
///   * `.unavailable`,
/// and across arbitrary **non-blank** default copy (including over-long
/// defaults that must themselves be bounded). The default is constrained to be
/// non-blank because that is the production contract for fail-soft copy
/// (`NotificationDefaults` are non-empty constants) and is what guarantees the
/// delivered text is non-empty.
final class LLMNotificationTextPropertyTests: XCTestCase {

    // MARK: - Fixed-outcome service (drives resolution without the network)

    /// An ``LLMService`` that always reports a fixed ``LlmResult`` so the
    /// fail-soft resolution can be exercised deterministically for every
    /// outcome.
    private struct FixedResultService: LLMService {
        let result: LlmResult
        func notificationText(for items: [ActionItemSummary]) async -> LlmResult { result }
    }

    /// Items are irrelevant to the fail-soft resolution (the outcome is fixed),
    /// so a single representative summary is reused throughout.
    private static let sampleItems = [
        ActionItemSummary(title: "Book flight", bucketName: "Travel", dueLabel: "today")
    ]

    // MARK: - Property: resolvedNotificationText is bounded and fails soft

    /// Property 15 / Req 7.16, 7.17 — for any outcome and any non-blank default,
    /// the resolved notification text is non-empty, at most 200 characters, and
    /// follows the fail-soft rule: the `.ok` branch delivers the bounded proxy
    /// text (or the bounded default when the proxy text is blank), while the
    /// `.timedOut` / `.unavailable` branches deliver the bounded default.
    func testResolvedNotificationTextIsBoundedAndFailsSoft() {
        property(
            "resolved notification text is non-empty, ≤200 chars, and falls soft to the bounded default",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(Gen.zip(llmResultGen, defaultTextGen)) { result, defaultText in
            let resolved = Self.runBlocking {
                await FixedResultService(result: result)
                    .resolvedNotificationText(for: Self.sampleItems, default: defaultText)
            }
            let boundedDefault = LlmNotificationText.bounded(defaultText)

            let withinBound = resolved.count <= LlmNotificationText.maxLength
            let nonEmpty = !resolved.isEmpty

            let failSoftCorrect: Bool
            switch result {
            case let .ok(text):
                let boundedText = LlmNotificationText.bounded(text)
                failSoftCorrect = boundedText.isEmpty
                    ? (resolved == boundedDefault)
                    : (resolved == boundedText)
            case .timedOut, .unavailable:
                failSoftCorrect = (resolved == boundedDefault)
            }

            return (withinBound <?> "resolved text is at most \(LlmNotificationText.maxLength) characters")
                ^&&^
                (nonEmpty <?> "resolved text is non-empty (non-blank default fallback)")
                ^&&^
                (failSoftCorrect <?> "ok delivers bounded proxy text; blank/timeout/unavailable deliver the bounded default")
        }
    }

    // MARK: - Property: bounded(_:) never exceeds the limit (Req 7.16)

    /// Property 15 / Req 7.16 — for *any* string (including blank, over-long,
    /// and multi-scalar emoji), `bounded(_:)` yields at most 200 characters and
    /// equals the leading-trimmed, character-wise prefix of the input, so a
    /// grapheme cluster is never cut in half.
    func testBoundedNeverExceedsMaxLength() {
        property(
            "bounded text is at most \(LlmNotificationText.maxLength) characters and is the trimmed character prefix",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(arbitraryTextGen) { raw in
            let bounded = LlmNotificationText.bounded(raw)
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)

            let withinBound = bounded.count <= LlmNotificationText.maxLength
            let matchesOracle = trimmed.count <= LlmNotificationText.maxLength
                ? (bounded == trimmed)
                : (bounded == String(trimmed.prefix(LlmNotificationText.maxLength)))

            return (withinBound <?> "bounded text never exceeds the limit")
                ^&&^
                (matchesOracle <?> "bounded text is the trimmed, character-wise prefix")
        }
    }

    // MARK: - Async bridge

    /// Runs an `async` operation to completion from a synchronous SwiftCheck
    /// property body and returns its result.
    private static func runBlocking<T>(_ operation: @escaping () async -> T) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let box = ResultBox<T>()
        Task {
            box.value = await operation()
            semaphore.signal()
        }
        semaphore.wait()
        return box.value!
    }

    private final class ResultBox<T> {
        var value: T?
    }
}

// MARK: - Generators (constrain to the relevant input space)

/// A mix of letters, digits, punctuation, whitespace, accented characters, and
/// a single-grapheme emoji, so generated text spans the realistic proxy-text
/// space and the trimming edge cases.
private let textChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,!?'\"-\n\téüñ漢😀")

/// Characters that are never whitespace, used to anchor a generated default so
/// it is guaranteed non-blank after trimming.
private let nonBlankChars: [Character] =
    Array("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

/// Random text from length 0 up to past the 200-character bound.
private let plainTextGen: Gen<String> = Gen<Int>.choose((0, 400)).flatMap { size in
    Gen<Character>.fromElements(of: textChars).proliferate(withSize: size).map { String($0) }
}

/// Runs of a four-person family emoji (each a multi-scalar grapheme cluster) so
/// the bound is exercised against text where characters and scalars diverge.
private let emojiTextGen: Gen<String> = Gen<Int>.choose((0, 260)).map {
    String(repeating: "👨‍👩‍👧‍👦", count: $0)
}

/// Fixed edge cases: empty, whitespace-only, exactly at and just over the
/// bound, far over the bound, padded, short non-Latin, and long emoji.
private let edgeCaseTextGen: Gen<String> = Gen<String>.fromElements(of: [
    "",
    "   ",
    "\n\t  \n",
    String(repeating: "a", count: LlmNotificationText.maxLength),
    String(repeating: "a", count: LlmNotificationText.maxLength + 1),
    String(repeating: "z", count: 1000),
    "  trimmed on both sides  ",
    "短い",
    String(repeating: "🙂", count: 250)
])

/// Arbitrary proxy text: mostly random/plain (weighted) with emoji and the
/// fixed edge cases mixed in.
private let arbitraryTextGen: Gen<String> = Gen.one(of: [
    plainTextGen,
    plainTextGen,
    emojiTextGen,
    edgeCaseTextGen
])

/// Outcomes of an LLM_Proxy request. `.ok` is weighted higher so both the
/// proxy-text path and the fail-soft paths get ample coverage.
private let llmResultGen: Gen<LlmResult> = Gen.one(of: [
    arbitraryTextGen.map { LlmResult.ok($0) },
    arbitraryTextGen.map { LlmResult.ok($0) },
    Gen.pure(LlmResult.timedOut),
    Gen.pure(LlmResult.unavailable)
])

/// A random non-blank default: a non-whitespace anchor plus arbitrary trailing
/// content (possibly over-long, so the default itself must be bounded).
private let randomNonBlankDefaultGen: Gen<String> = Gen.compose { c in
    let anchor = c.generate(using: Gen<Character>.fromElements(of: nonBlankChars))
    let size = c.generate(using: Gen<Int>.choose((0, 400)))
    let rest = c.generate(using: Gen<Character>.fromElements(of: textChars).proliferate(withSize: size))
    return String(anchor) + String(rest)
}

/// Non-blank default copy: random non-blank text plus the production
/// `NotificationDefaults` constants and a few non-blank edge cases.
private let defaultTextGen: Gen<String> = Gen.one(of: [
    randomNonBlankDefaultGen,
    randomNonBlankDefaultGen,
    Gen<String>.fromElements(of: [
        NotificationDefaults.taskReminderTitle,
        NotificationDefaults.eveningNudgeBody,
        NotificationDefaults.globalDailyBody,
        NotificationDefaults.globalDailyTitle,
        String(repeating: "d", count: 300),
        "  padded default reminder  ",
        String(repeating: "🛎", count: 220)
    ])
])
