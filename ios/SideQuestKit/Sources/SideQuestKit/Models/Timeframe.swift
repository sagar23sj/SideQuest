import Foundation

/// The target window for acting on an `ActionItem`.
///
/// Serialized as a **discriminated union**: a `kind` discriminator selects the
/// variant and the `specificDate` variant carries a `date` payload. This
/// mirrors the contract's `oneOf` + `discriminator` schema
/// (`backend/api/openapi.yaml` → `Timeframe`):
///
/// ```json
/// { "kind": "today" }
/// { "kind": "within_a_day" }
/// { "kind": "within_a_week" }
/// { "kind": "specific_date", "date": "2025-06-14" }
/// ```
///
/// The `kind` values are the contract's discriminator mapping
/// (`today | within_a_day | within_a_week | specific_date`). The
/// `specific_date` payload is a `format: date` calendar date (no time
/// component, encoded `yyyy-MM-dd`); `specificDate` therefore carries a `Date`
/// that is formatted/parsed by `CalendarDate.formatter` (UTC, POSIX) so it
/// round-trips identically to the Android client's `LocalDate` serialization.
///
/// The "today-or-later" rule for `specificDate` (Req 9.7) is enforced by the
/// timeframe-validation logic added in a later task, not by this data carrier.
public enum Timeframe: Codable, Equatable {
    case today
    case withinADay
    case withinAWeek
    case specificDate(Date)

    /// Coding keys for the discriminator and the optional date payload.
    private enum CodingKeys: String, CodingKey {
        case kind
        case date
    }

    /// The discriminator values, matching the contract's `discriminator.mapping`.
    private enum Kind: String, Codable {
        case today
        case withinADay = "within_a_day"
        case withinAWeek = "within_a_week"
        case specificDate = "specific_date"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let kind = try container.decode(Kind.self, forKey: .kind)
        switch kind {
        case .today:
            self = .today
        case .withinADay:
            self = .withinADay
        case .withinAWeek:
            self = .withinAWeek
        case .specificDate:
            let raw = try container.decode(String.self, forKey: .date)
            guard let date = CalendarDate.formatter.date(from: raw) else {
                throw DecodingError.dataCorruptedError(
                    forKey: .date,
                    in: container,
                    debugDescription: "Expected a yyyy-MM-dd calendar date, got \"\(raw)\"."
                )
            }
            self = .specificDate(date)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .today:
            try container.encode(Kind.today, forKey: .kind)
        case .withinADay:
            try container.encode(Kind.withinADay, forKey: .kind)
        case .withinAWeek:
            try container.encode(Kind.withinAWeek, forKey: .kind)
        case .specificDate(let date):
            try container.encode(Kind.specificDate, forKey: .kind)
            try container.encode(CalendarDate.formatter.string(from: date), forKey: .date)
        }
    }
}
