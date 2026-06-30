import XCTest
import Foundation
import SwiftCheck
@testable import SideQuestKit

/// Property-based test for **Reused Property 1 — Unsupported content is rejected
/// and never persisted** (task 8.2).
///
/// **Validates: Requirements 4.4**
///
/// > IF the Share_Extension receives a Shared_Item whose type is not one of
/// > links, text, images, or video references, THEN THE App SHALL display a
/// > message stating the content type is not supported, SHALL discard the
/// > Shared_Item without creating an Action_Item, and SHALL terminate the
/// > categorization flow.
///
/// This re-implements the sibling Android property (`UnsupportedContentRejectionPropertyTest`)
/// against the Swift `ContentClassifier` / `DefaultCaptureService` so the two
/// clients are equivalent on the capture-classification contract.
///
/// ## Strategy
///
/// For each trial SwiftCheck generates a `SharedItem` whose attachments carry
/// **only** type identifiers drawn from a curated pool of UTIs that the app does
/// not capture (archives, PDFs, audio, contacts, vendor blobs, …). Each pool
/// entry is verified — both by construction and by the assertions below — to
/// match none of the supported kinds (link / image / video / text), so the
/// generated item is guaranteed to be unsupported.
///
/// The item is run through the real `DefaultCaptureService`. The property
/// asserts the two universal post-conditions that together mean "rejected and
/// never persisted":
///
/// 1. `classify(_:)` returns `.unsupported` — the categorization flow shows the
///    "content type not supported" message and ends (Req 4.4);
/// 2. `beginCapture(_:)` returns `nil` — no `CaptureDraft` is produced, so the
///    only persistence entry point (`CaptureDraft.makeActionItem` →
///    `ActionItemRepository.create`) is unreachable and **no Action_Item can be
///    created** for this item (Req 4.4).
///
/// ## Generator notes (constraining to the valid input space)
///
/// - **Only unsupported UTIs.** Every type identifier comes from
///   ``unsupportedIdentifiers``, a hand-checked pool none of whose entries
///   contains the substrings the classifier keys on (`"url"`/`.url` suffix,
///   `"image"`, `"movie"`/`"video"`/`"mpeg"`, `"text"`). This keeps every trial
///   firmly in the *unsupported* region of the input space — a supported
///   attachment would be a false negative for this property.
/// - **Multiple attachments and identifiers.** Items carry 1...4 attachments,
///   each with 1...3 identifiers, so the property covers the multi-attachment /
///   multi-UTI shapes a real share delivers — none of which should ever flip the
///   result to supported.
/// - **Decoy payloads.** Some attachments also carry a `url` or `text` payload
///   even though their declared types are unsupported. Classification is
///   *type-driven* (it inspects `typeIdentifiers`, not payloads), so the
///   presence of a URL/text value must not rescue an unsupported type — these
///   trials prove that.
final class UnsupportedContentRejectionPropertyTests: XCTestCase {

    private let service = DefaultCaptureService()

    /// Reused Property 1 / Req 4.4: any item whose attachment types are all
    /// unsupported classifies as `.unsupported` and yields no capture draft, so
    /// it is discarded and never persisted.
    func testUnsupportedContentIsRejectedAndNeverPersisted() {
        property(
            "Reused Property 1: unsupported content classifies as .unsupported and yields no draft (never persisted)",
            arguments: CheckerArguments(maxAllowableSuccessfulTests: 200)
        ) <- forAllNoShrink(itemGen) { item in
            // (1) Classified as unsupported -> "not supported" message + end (Req 4.4).
            let classifiedUnsupported = self.service.classify(item.attachments) == .unsupported

            // (2) No draft -> the persistence path (draft.makeActionItem ->
            //     repository.create) is unreachable, so no Action_Item is created
            //     and nothing is persisted (Req 4.4).
            let noDraft = self.service.beginCapture(item) == nil

            return (classifiedUnsupported && noDraft)
                <?> "classify==.unsupported && beginCapture==nil"
        }
    }
}

// MARK: - Generators

/// A hand-checked pool of UTI strings the app does not capture. None contains
/// the substrings the classifier keys on (`url`, `image`, `movie`, `video`,
/// `mpeg`, `text`) nor ends in `.url`, so every entry classifies as
/// ``SharedContentType/unsupported``.
private let unsupportedIdentifiers: [String] = [
    "com.acme.proprietary-blob",
    "public.archive",
    "public.zip-archive",
    "com.pkware.zip-archive",
    "com.adobe.pdf",
    "public.vcard",
    "public.calendar-event",
    "public.contact",
    "public.audio",
    "public.mp3",
    "public.aiff-audio",
    "com.apple.application-bundle",
    "public.executable",
    "public.json",
    "public.data",
    "public.folder",
    "public.spreadsheet",
    "public.presentation",
    "public.font",
    "com.acme.widget",
]

private let unsupportedIdentifierGen = Gen<String>.fromElements(of: unsupportedIdentifiers)

/// A decoy payload some attachments carry even though their declared types are
/// unsupported; classification must ignore it (it is type-driven).
private let decoyURLGen: Gen<URL?> = Gen<URL?>.fromElements(of: [
    nil,
    URL(string: "https://example.com/decoy"),
    URL(string: "file:///tmp/decoy.bin"),
])

private let decoyTextGen: Gen<String?> = Gen<String?>.fromElements(of: [
    nil,
    "decoy text payload",
    "https://example.com/looks-like-a-link",
])

/// One attachment whose type identifiers are all unsupported, optionally
/// carrying a decoy URL/text payload.
private let attachmentGen: Gen<SharedAttachment> = Gen.compose { c in
    let count = c.generate(using: Gen<Int>.choose((1, 3)))
    return SharedAttachment(
        typeIdentifiers: c.generate(using: unsupportedIdentifierGen.proliferate(withSize: count)),
        url: c.generate(using: decoyURLGen),
        text: c.generate(using: decoyTextGen)
    )
}

/// A shared item carrying 1...4 unsupported attachments.
private let itemGen: Gen<SharedItem> = Gen.compose { c in
    let count = c.generate(using: Gen<Int>.choose((1, 4)))
    return SharedItem(
        attachments: c.generate(using: attachmentGen.proliferate(withSize: count))
    )
}
