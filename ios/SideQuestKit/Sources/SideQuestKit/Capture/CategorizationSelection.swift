import Foundation

// MARK: - Categorization selection gating (task 8.1, Req 4.3, 4.7)
//
// Pure, host-testable state for the categorization sheet's Bucket + Timeframe
// selection. The requirement is that saving is disabled until the user has
// chosen *exactly one* Bucket and *exactly one* Timeframe (Req 4.3). Keeping
// this gating logic here (rather than only in the SwiftUI sheet) lets it be unit
// tested without the iOS-only UI and gives the sheet a single source of truth
// for the Save button's enabled state.

/// The user's in-progress selections on the categorization sheet.
///
/// Both selections are single-valued optionals: a sheet can only ever hold one
/// chosen bucket and one chosen timeframe, which is exactly the "exactly one
/// each" rule (Req 4.3). ``canSave`` is the Save button's enabled state; the
/// sheet must keep Save disabled while it is `false`.
public struct CategorizationSelection: Equatable {

    /// The chosen bucket's identifier, or `nil` if none chosen yet.
    public var bucketId: String?

    /// The chosen timeframe, or `nil` if none chosen yet.
    public var timeframe: Timeframe?

    public init(bucketId: String? = nil, timeframe: Timeframe? = nil) {
        self.bucketId = bucketId
        self.timeframe = timeframe
    }

    /// `true` only when exactly one Bucket and exactly one Timeframe are chosen
    /// (Req 4.3). The sheet binds the Save button's `disabled` state to
    /// `!canSave`, so saving is impossible until both are selected.
    public var canSave: Bool {
        bucketId != nil && timeframe != nil
    }

    /// The confirmed `(bucketId, timeframe)` pair when ``canSave`` is `true`,
    /// otherwise `nil`. Callers (the confirm path, task 8.3) use this so they
    /// can only proceed with a fully-specified selection — there is no way to
    /// confirm a partial selection.
    public var confirmed: (bucketId: String, timeframe: Timeframe)? {
        guard let bucketId, let timeframe else { return nil }
        return (bucketId, timeframe)
    }
}
