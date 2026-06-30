import SwiftUI

/// Builds a SwiftUI `Color` from a stored per-status color string (Req 8.2).
///
/// Bucket status colors are persisted and synced as raw strings — the contract
/// stores them as hex (`#RRGGBB`, e.g. `"#FF0000"`), matching the values the
/// Android client and backend exchange — so the board renders the indicator
/// directly from the stored value rather than from a fixed palette. The parser
/// is intentionally forgiving: it accepts an optional leading `#` and the common
/// 3/6/8-digit hex forms, and returns `nil` for empty or malformed values (for
/// example the empty colors carried by the synthetic placeholder bucket
/// `Domain.buildBoard` emits for items whose bucket is unknown). Callers fall
/// back to a neutral semantic color when parsing fails, so a missing or invalid
/// color never crashes or hides an item.
extension Color {

    /// Parses a hex color string of the form `#RGB`, `#RRGGBB`, or `#RRGGBBAA`
    /// (the leading `#` is optional). Returns `nil` when the value is empty or
    /// cannot be parsed.
    init?(hex: String) {
        var cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasPrefix("#") {
            cleaned.removeFirst()
        }
        guard !cleaned.isEmpty, let value = UInt64(cleaned, radix: 16) else {
            return nil
        }

        let red, green, blue, alpha: Double
        switch cleaned.count {
        case 3: // #RGB
            red = Double((value >> 8) & 0xF) / 15.0
            green = Double((value >> 4) & 0xF) / 15.0
            blue = Double(value & 0xF) / 15.0
            alpha = 1.0
        case 6: // #RRGGBB
            red = Double((value >> 16) & 0xFF) / 255.0
            green = Double((value >> 8) & 0xFF) / 255.0
            blue = Double(value & 0xFF) / 255.0
            alpha = 1.0
        case 8: // #RRGGBBAA
            red = Double((value >> 24) & 0xFF) / 255.0
            green = Double((value >> 16) & 0xFF) / 255.0
            blue = Double((value >> 8) & 0xFF) / 255.0
            alpha = Double(value & 0xFF) / 255.0
        default:
            return nil
        }

        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}
