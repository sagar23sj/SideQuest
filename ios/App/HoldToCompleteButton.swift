import SwiftUI
import UIKit
import SideQuestKit

// MARK: - Press-and-hold completion control (Req 8.7, 8.8, 8.9)
//
// A thin SwiftUI shell over the portable hold math in `SideQuestKit`
// (`Domain.holdFillProportion` / `Domain.holdReachesCompletion`). The control:
//
//   * While pressed, samples the continuous hold time and renders a progressive
//     fill whose proportion equals `min(elapsedHold / 800ms, 1)` (Req 8.7).
//   * Completes only once the hold is sustained continuously for 800 ms, at
//     which point it fires haptic feedback, plays a short (<= 2000 ms)
//     celebration, and invokes `onComplete` so the board can persist the
//     "completed" status (Req 8.8).
//   * Cancels and resets the fill to empty with no status change if the press
//     is released (or dragged off) before the threshold (Req 8.9).
//
// All timing/threshold decisions are delegated to the pure domain layer; this
// view only owns presentation, gesture, haptics, and the celebration lifetime.
struct HoldToCompleteButton: View {

    /// Invoked exactly once when a hold reaches the completion threshold
    /// (Req 8.8). The board view model persists the "completed" status in
    /// response.
    let onComplete: () -> Void

    /// Tint for the fill and the idle ring. Defaults to the system accent.
    var tint: Color = .accentColor

    /// Honors the system "Reduce Motion" setting: when on, the celebratory
    /// burst is shown without scaling/opacity motion (Req: accessible,
    /// non-gratuitous animation).
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Whether the user is currently pressing the control. Flipping this drives
    /// the sampling loop in `.task(id:)`.
    @State private var isPressing = false

    /// The current continuous hold time in seconds, sampled while pressing and
    /// reset to zero on an early release. The displayed fill is derived from
    /// this via the domain math, so the view never stores a proportion that can
    /// drift from the elapsed time.
    @State private var elapsed: TimeInterval = 0

    /// Guards against firing completion more than once per hold.
    @State private var didComplete = false

    /// Whether the post-completion celebration is currently on screen.
    @State private var isCelebrating = false

    /// Diameter of the circular control. Comfortably exceeds the 44 pt minimum
    /// touch-target guideline.
    private let diameter: CGFloat = 48

    /// How long the celebration stays on screen. Kept well under the 2000 ms
    /// ceiling (Req 8.8).
    private let celebrationDuration: TimeInterval = 1.2

    /// The filled proportion to render, computed by the portable domain logic
    /// from the sampled hold time (Req 8.7).
    private var fillProportion: Double {
        Domain.holdFillProportion(elapsed: elapsed)
    }

    var body: some View {
        ZStack {
            ring
            progressArc
            icon
        }
        .frame(width: diameter, height: diameter)
        // Guarantee at least a 44 pt interactive target even if `diameter`
        // were reduced.
        .frame(minWidth: 44, minHeight: 44)
        .contentShape(Circle())
        .gesture(holdGesture)
        // Sampling loop: starts when `isPressing` becomes true and is cancelled
        // automatically when it becomes false (or the view disappears).
        .task(id: isPressing) {
            await runHoldSampling()
        }
        .overlay {
            if isCelebrating {
                celebration
            }
        }
        // The press-and-hold gesture is not operable by VoiceOver, so expose a
        // standard activate action as an accessible alternative (Req: accessible
        // path to completion).
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Mark completed")
        .accessibilityHint("Press and hold to mark this item completed.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            triggerCompletion()
        }
    }

    // MARK: - Subviews

    private var ring: some View {
        Circle()
            .strokeBorder(tint.opacity(0.35), lineWidth: 3)
    }

    /// The progressive fill: a trimmed circle that grows from empty to full as
    /// the hold proportion increases (Req 8.7).
    private var progressArc: some View {
        Circle()
            .trim(from: 0, to: fillProportion)
            .stroke(tint, style: StrokeStyle(lineWidth: 3, lineCap: .round))
            // Start the fill at the top and grow clockwise.
            .rotationEffect(.degrees(-90))
            // Animate only the collapse-to-empty on release; growth is driven
            // directly by frequent `elapsed` samples and needs no implicit
            // animation.
            .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: isPressing)
    }

    private var icon: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(fillProportion >= 1 ? tint : tint.opacity(0.5))
            .scaleEffect(reduceMotion ? 1 : 1 + 0.15 * fillProportion)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: fillProportion >= 1)
    }

    /// A brief celebratory flourish shown after completion (Req 8.8). With
    /// Reduce Motion enabled it appears statically without scaling/opacity
    /// motion.
    private var celebration: some View {
        Image(systemName: "checkmark.seal.fill")
            .font(.system(size: diameter * 0.9))
            .foregroundStyle(.green)
            .scaleEffect(reduceMotion ? 1 : (isCelebrating ? 1 : 0.2))
            .opacity(reduceMotion ? 1 : (isCelebrating ? 1 : 0))
            .animation(reduceMotion ? nil : .spring(response: 0.35, dampingFraction: 0.5), value: isCelebrating)
            .accessibilityHidden(true)
    }

    // MARK: - Gesture

    /// Detects press-down (`onChanged`) and release/drag-off (`onEnded`) using a
    /// zero-distance drag, which is the most reliable way to observe the full
    /// touch-down/touch-up lifecycle a press-and-hold needs.
    private var holdGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                if !isPressing {
                    beginHold()
                }
            }
            .onEnded { _ in
                endHold()
            }
    }

    private func beginHold() {
        didComplete = false
        elapsed = 0
        isPressing = true
    }

    /// Handles release. If the threshold was never reached this cancels the
    /// gesture and resets the fill to empty with no status change (Req 8.9).
    private func endHold() {
        isPressing = false
        guard !didComplete else { return }
        if reduceMotion {
            elapsed = 0
        } else {
            withAnimation(.easeOut(duration: 0.2)) { elapsed = 0 }
        }
    }

    // MARK: - Sampling loop

    /// Samples the continuous hold time at ~60 Hz while pressing and asks the
    /// domain layer whether the completion threshold has been reached
    /// (Req 8.7, 8.8). Returns immediately when not pressing, which is also how
    /// a release tears the loop down (via `.task(id: isPressing)`).
    private func runHoldSampling() async {
        guard isPressing else { return }

        let start = Date()
        while !Task.isCancelled {
            let now = Date().timeIntervalSince(start)
            elapsed = now

            if Domain.holdReachesCompletion(elapsed: now) {
                triggerCompletion()
                return
            }

            // ~60 fps sampling cadence.
            try? await Task.sleep(nanoseconds: 16_000_000)
        }
    }

    // MARK: - Completion

    /// Fires completion side effects exactly once: haptics, the celebration,
    /// and the `onComplete` callback that persists the status (Req 8.8). Used by
    /// both the sustained hold and the accessibility activate action.
    private func triggerCompletion() {
        guard !didComplete else { return }
        didComplete = true
        isPressing = false

        // Snap the fill to full so the moment of completion reads clearly.
        elapsed = Domain.holdCompletionThreshold

        // Success haptic (Req 8.8).
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        // Celebration with a bounded lifetime (Req 8.8: <= 2000 ms).
        if reduceMotion {
            isCelebrating = true
        } else {
            withAnimation { isCelebrating = true }
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(celebrationDuration * 1_000_000_000))
            if reduceMotion {
                isCelebrating = false
            } else {
                withAnimation { isCelebrating = false }
            }
        }

        onComplete()
    }
}

// MARK: - Previews

#Preview("Hold to complete") {
    HoldToCompleteButton(onComplete: {})
        .padding()
}
