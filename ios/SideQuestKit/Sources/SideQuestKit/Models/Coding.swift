import Foundation

/// JSON coding configuration for the `Generated_Models`.
///
/// The shared OpenAPI contract (`backend/api/openapi.yaml`) is the single
/// source of truth for the on-the-wire JSON. It models the two server-issued
/// timestamps — `SyncMeta.updatedAt` and `ActionItem.createdAt` (and
/// `Account.createdAt`) — as `format: date-time` strings, which the Go backend
/// serializes as RFC 3339 (the same shape `encoding/json` emits for a
/// `time.Time`). Swift represents these as `Date`, so every model in this
/// module MUST be encoded and decoded with the coders produced here to land on
/// the contract's wire format.
///
/// `Timeframe.specificDate` is a `format: date` (calendar-date-only) value, not
/// a date-time. It is handled separately inside `Timeframe`'s custom `Codable`
/// (see `CalendarDate`), independent of the date-time strategy configured here.
///
/// The decoder is deliberately tolerant: the backend's `time.Time` JSON
/// encoding includes fractional seconds when the instant carries sub-second
/// precision (RFC 3339 "nano") but omits them otherwise, so we accept both.
public enum SideQuestCoding {

    /// RFC 3339 formatter that includes fractional seconds (e.g.
    /// `2025-06-14T10:30:00.123Z`). Used first when decoding.
    private static let dateTimeWithFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    /// RFC 3339 formatter without fractional seconds (e.g.
    /// `2025-06-14T10:30:00Z`). Used for encoding and as a decode fallback.
    private static let dateTime: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    /// A `JSONEncoder` configured to emit the contract's wire format.
    ///
    /// Date-time fields are written as RFC 3339 strings (no fractional seconds,
    /// which the Go backend parses without issue).
    public static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(dateTime.string(from: date))
        }
        return encoder
    }

    /// A `JSONDecoder` configured to read the contract's wire format.
    ///
    /// Accepts RFC 3339 date-time strings with or without fractional seconds.
    public static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            if let date = dateTimeWithFractionalSeconds.date(from: raw)
                ?? dateTime.date(from: raw) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Expected an RFC 3339 date-time string, got \"\(raw)\"."
            )
        }
        return decoder
    }
}

/// Calendar-date-only (`format: date`) conversion for the `Timeframe`
/// `specificDate` payload, e.g. `2025-06-14`.
///
/// This mirrors the Android client's `LocalDateIso8601Serializer`
/// (ISO-8601 `yyyy-MM-dd`) so a specific-date timeframe round-trips identically
/// across platforms. The formatter is pinned to the Gregorian calendar, the
/// POSIX locale, and UTC so the textual date never shifts with the device's
/// locale or time zone.
enum CalendarDate {

    static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
