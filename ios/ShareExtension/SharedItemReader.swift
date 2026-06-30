import Foundation
import UniformTypeIdentifiers
import SideQuestKit

// MARK: - Shared item reader (task 8.1, Req 4.2)
//
// The iOS-only bridge between the system's `NSExtensionItem`/`NSItemProvider`
// share payload and `SideQuestKit`'s portable `SharedItem` value type. The
// extension's `ShareViewController` is a thin shell: it hands the system items
// to this reader, which lowers them into plain values so all classification and
// capture logic stays in the host-testable shared module (`ContentClassifier`,
// `DefaultCaptureService`).
//
// Lives in the Share Extension target (not `SideQuestKit`) because it depends on
// `NSItemProvider`, which is not available on non-Apple hosts.

/// Reads the system share payload into a portable ``SharedItem``.
enum SharedItemReader {

    /// Lowers the share sheet's `NSExtensionItem`s into a ``SharedItem``.
    ///
    /// Each attachment's registered Uniform Type Identifiers (UTIs) are captured
    /// verbatim so ``ContentClassifier`` can classify the item (Req 4.2), and
    /// any link/text payload is eagerly loaded so
    /// ``CaptureService/beginCapture(_:)`` can seed a draft without re-reading
    /// the providers. Image/video attachments are referenced by type only at
    /// this stage; their bytes are loaded later by the preview/capture path.
    ///
    /// - Parameter extensionItems: `extensionContext?.inputItems` cast to
    ///   `[NSExtensionItem]`.
    /// - Returns: A ``SharedItem`` describing every attachment delivered.
    static func read(_ extensionItems: [NSExtensionItem]) async -> SharedItem {
        let providers = extensionItems.flatMap { $0.attachments ?? [] }
        var attachments: [SharedAttachment] = []
        attachments.reserveCapacity(providers.count)

        for provider in providers {
            let typeIdentifiers = provider.registeredTypeIdentifiers
            let url = await loadURL(from: provider)
            // Only bother loading text when no link was found; the source app
            // often offers a link *and* its string form, and link wins by
            // classification priority, so the text payload would be unused.
            let text = url == nil ? await loadText(from: provider) : nil
            attachments.append(
                SharedAttachment(typeIdentifiers: typeIdentifiers, url: url, text: text)
            )
        }

        return SharedItem(attachments: attachments)
    }

    // MARK: - Payload loading

    /// Loads a shared web/file URL from a provider, or `nil` when it carries no
    /// URL (or loading fails).
    private static func loadURL(from provider: NSItemProvider) async -> URL? {
        let identifier = UTType.url.identifier
        guard provider.hasItemConformingToTypeIdentifier(identifier) else { return nil }
        let item = await loadItem(provider, typeIdentifier: identifier)
        if let url = item as? URL { return url }
        // Some sources deliver the URL as data/string; be defensive.
        if let data = item as? Data,
           let string = String(data: data, encoding: .utf8) {
            return URL(string: string.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        if let string = item as? String {
            return URL(string: string.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        return nil
    }

    /// Loads shared/typed text from a provider, preferring plain text and
    /// falling back to any text type, or `nil` when none is present.
    private static func loadText(from provider: NSItemProvider) async -> String? {
        for identifier in [UTType.plainText.identifier, UTType.text.identifier] {
            guard provider.hasItemConformingToTypeIdentifier(identifier) else { continue }
            let item = await loadItem(provider, typeIdentifier: identifier)
            if let string = item as? String { return string }
            if let string = item as? NSString { return string as String }
            if let data = item as? Data,
               let string = String(data: data, encoding: .utf8) {
                return string
            }
        }
        return nil
    }

    /// `async` wrapper over the completion-based
    /// `NSItemProvider.loadItem(forTypeIdentifier:options:)`. Returns the loaded
    /// item, or `nil` on error so a single failing attachment never aborts the
    /// whole read.
    private static func loadItem(
        _ provider: NSItemProvider,
        typeIdentifier: String
    ) async -> NSSecureCoding? {
        await withCheckedContinuation { continuation in
            provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil) { item, _ in
                continuation.resume(returning: item)
            }
        }
    }
}
