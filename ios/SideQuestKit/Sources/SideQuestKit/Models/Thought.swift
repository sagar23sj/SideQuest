import Foundation

/// A "thought of the day" shown during the loading experience (design: Data
/// Models → `Thought`; Req 12).
///
/// `Thought` is a client-only model backing the on-device built-in set of ≥30
/// motivational messages (Req 12.3); it is not part of the OpenAPI contract.
/// `text` is 1...280 characters; the length rule is enforced by the thought
/// provider in a later task.
public struct Thought: Codable, Equatable, Identifiable {

    public var id: Int
    public var text: String

    public init(id: Int, text: String) {
        self.id = id
        self.text = text
    }
}

extension Thought {

    /// The inclusive length bounds for a thought's `text` (Req 12.1, 12.3):
    /// at least 1 and at most 280 characters.
    public static let textLengthRange = 1...280

    /// Whether `text` satisfies the 1...280 character rule (Req 12.1).
    ///
    /// Length is measured in Swift `Character`s (grapheme clusters) so the rule
    /// matches how a reader perceives the message. Used by the thought provider
    /// to fail soft to ``defaultFallback`` if a selected entry is malformed
    /// (Req 12.5) and by the property tests to validate the corpus.
    public static func isValidText(_ text: String) -> Bool {
        textLengthRange.contains(text.count)
    }

    /// The non-empty default fallback thought shown when the deterministically
    /// selected thought cannot be retrieved (Req 12.5).
    ///
    /// Its text is well within 1...280 characters so it always satisfies
    /// ``isValidText(_:)``. Returning it lets the loading experience complete
    /// without surfacing an error to the user.
    public static let defaultFallback = Thought(
        id: 0,
        text: "Every small step you take today is progress worth celebrating."
    )
}
