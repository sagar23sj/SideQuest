import Foundation

/// Errors raised by the local GRDB store when a stored row cannot be mapped
/// back to a domain model, or when the shared App Group container is missing.
///
/// These describe data that is impossible under the migrations/validation, so
/// they indicate corruption or a programming error rather than ordinary user
/// input.
public enum SideQuestStoreError: Error, Equatable {

    /// The App Group container could not be resolved — the entitlement is
    /// missing or misconfigured (itself a distribution-validation failure under
    /// Req 13.7). Carries the App Group identifier that was attempted.
    case appGroupUnavailable(String)

    /// A persisted `contentType` string did not match any `ContentType`.
    case invalidContentType(String)

    /// A persisted `status` string did not match any `ActionStatus`.
    case invalidStatus(String)

    /// A persisted timeframe discriminator did not match any known variant.
    case invalidTimeframeDiscriminator(String)

    /// A `specific_date` timeframe row was missing its date payload column.
    case missingTimeframeDate

    /// A `specific_date` timeframe payload was not a `yyyy-MM-dd` calendar date.
    case invalidTimeframeDate(String)
}

/// Maps domain values that are not plain columns onto the SQLite column shapes
/// used by the store records, and back again.
///
/// Two values need bespoke handling:
///
/// * `Timeframe` is persisted as a **discriminator + payload** (design:
///   "Persistence notes"), mirroring the Android type-converter approach so the
///   stored representation round-trips identically. The discriminator strings
///   are the *same* tokens the contract's `oneOf` discriminator uses
///   (`today | within_a_day | within_a_week | specific_date`), and the
///   `specific_date` payload is a `yyyy-MM-dd` calendar date formatted by the
///   shared `CalendarDate` formatter (UTC, POSIX) used by the wire models.
///
/// * `LinkPreview` is persisted as a JSON blob produced by the shared
///   `SideQuestCoding` coders, so a stored preview matches the wire format
///   byte-for-byte and survives the round trip.
enum StoreCoding {

    // MARK: Timeframe discriminator tokens

    static let timeframeToday = "today"
    static let timeframeWithinADay = "within_a_day"
    static let timeframeWithinAWeek = "within_a_week"
    static let timeframeSpecificDate = "specific_date"

    /// The discriminator token for a timeframe (the value stored in the
    /// `timeframeKind` column).
    static func discriminator(for timeframe: Timeframe) -> String {
        switch timeframe {
        case .today:          return timeframeToday
        case .withinADay:     return timeframeWithinADay
        case .withinAWeek:    return timeframeWithinAWeek
        case .specificDate:   return timeframeSpecificDate
        }
    }

    /// The `yyyy-MM-dd` payload for a timeframe (the value stored in the
    /// `timeframeDate` column), or `nil` for the non-dated variants.
    static func datePayload(for timeframe: Timeframe) -> String? {
        if case let .specificDate(date) = timeframe {
            return CalendarDate.formatter.string(from: date)
        }
        return nil
    }

    /// Reconstructs a `Timeframe` from its stored discriminator + payload.
    static func timeframe(discriminator: String, datePayload: String?) throws -> Timeframe {
        switch discriminator {
        case timeframeToday:       return .today
        case timeframeWithinADay:  return .withinADay
        case timeframeWithinAWeek: return .withinAWeek
        case timeframeSpecificDate:
            guard let payload = datePayload else {
                throw SideQuestStoreError.missingTimeframeDate
            }
            guard let date = CalendarDate.formatter.date(from: payload) else {
                throw SideQuestStoreError.invalidTimeframeDate(payload)
            }
            return .specificDate(date)
        default:
            throw SideQuestStoreError.invalidTimeframeDiscriminator(discriminator)
        }
    }

    // MARK: LinkPreview JSON blob

    /// Encodes an optional `LinkPreview` to a JSON blob for the `preview`
    /// column, using the shared contract coders. Returns `nil` for `nil`.
    static func encodePreview(_ preview: LinkPreview?) throws -> Data? {
        guard let preview else { return nil }
        return try SideQuestCoding.makeEncoder().encode(preview)
    }

    /// Decodes an optional `LinkPreview` from a stored JSON blob.
    static func decodePreview(_ data: Data?) throws -> LinkPreview? {
        guard let data else { return nil }
        return try SideQuestCoding.makeDecoder().decode(LinkPreview.self, from: data)
    }
}
