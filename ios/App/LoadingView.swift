import SwiftUI
import SideQuestKit

/// The launch loading experience: an uplifting "thought of the day" rendered in
/// the SideQuest visual style while the app loads (Req 12).
///
/// The thought is selected synchronously and on-device from
/// ``BuiltInThoughtProvider`` for the current local calendar date, so it is
/// available immediately at launch — well within the 500 ms budget (Req 12.1)
/// and with no network connection (Req 12.3). Selection is deterministic by
/// local date and fails soft to a default fallback, so this view always has a
/// non-empty thought to show (Req 12.2, 12.5).
///
/// Layout (Req 12.4): the message is centered and rendered with semantic,
/// Dynamic Type-aware fonts using the same typography and color conventions as
/// the rest of the app (mirroring `RootView`). It is never truncated — no line
/// limit is applied — and the content sits inside a centered, scrollable
/// container so even the largest accessibility text sizes stay fully visible on
/// the smallest supported screens (iPhone SE) without clipping.
struct LoadingView: View {

    /// The thought to display, resolved once for the view's lifetime.
    private let thought: Thought

    /// Creates the loading view, selecting the thought for `date` from
    /// `provider`.
    ///
    /// - Parameters:
    ///   - provider: The source of the thought of the day. Defaults to the
    ///     on-device ``BuiltInThoughtProvider`` (Req 12.3).
    ///   - date: The instant whose local calendar day selects the thought.
    ///     Defaults to now, so the displayed thought follows the device's local
    ///     date (Req 12.2).
    init(provider: ThoughtProvider = BuiltInThoughtProvider(), date: Date = Date()) {
        self.thought = provider.thought(forLocalDate: date)
    }

    var body: some View {
        // A centered, scrollable container guarantees the thought is fully
        // visible and centered across every supported screen size and Dynamic
        // Type setting: it centers normally, and scrolls only if the text would
        // otherwise overflow — so it is never truncated (Req 12.4).
        GeometryReader { proxy in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 24) {
                    Text("SideQuest")
                        .font(.largeTitle.bold())
                        .multilineTextAlignment(.center)

                    Text(thought.text)
                        .font(.title3)
                        .fontWeight(.medium)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                        // No line limit and no fixed truncation: the message
                        // always renders in full (Req 12.4).
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityLabel(Text("Thought of the day: \(thought.text)"))

                    ProgressView()
                        .padding(.top, 8)
                        .accessibilityLabel(Text("Loading SideQuest"))
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 32)
                .padding(.vertical, 24)
                // Center the content within the available height; grow to scroll
                // when the text is taller than the screen.
                .frame(minHeight: proxy.size.height)
            }
        }
        .accessibilityElement(children: .contain)
    }
}

#Preview {
    LoadingView()
}

#Preview("Long thought, large text") {
    LoadingView(
        provider: BuiltInThoughtProvider(
            thoughts: [
                Thought(
                    id: 1,
                    text: String(repeating: "Keep going, you are doing great. ", count: 8)
                )
            ]
        )
    )
    .environment(\.dynamicTypeSize, .accessibility5)
}
