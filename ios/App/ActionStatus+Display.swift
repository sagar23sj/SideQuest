import SwiftUI
import SideQuestKit

/// UI-facing presentation for ``ActionStatus`` used by the board (Req 8.2, 8.3).
///
/// The status drives both the visible label and the spoken accessibility text.
/// Because color alone cannot convey status to every user, the board always
/// pairs the color indicator with this textual label, satisfying the
/// "don't rely on color alone" accessibility guideline.
extension ActionStatus {

    /// A short, human-readable name for the status, shown next to the color
    /// indicator and read by VoiceOver.
    var displayName: String {
        switch self {
        case .notStarted: return "Not started"
        case .inProgress: return "In progress"
        case .completed: return "Completed"
        }
    }

    /// Stable, sensible ordering for status pickers: not started → in progress
    /// → completed.
    static var orderedForPicker: [ActionStatus] {
        [.notStarted, .inProgress, .completed]
    }
}
