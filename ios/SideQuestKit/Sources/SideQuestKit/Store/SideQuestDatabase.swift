import Foundation
import GRDB

/// The local SQLite store — the offline-first source of truth (Req 5.1).
///
/// Backed by a GRDB `DatabasePool`, which always opens the database in **WAL**
/// (write-ahead logging) mode. WAL is what makes coordinated multi-process
/// access possible: the main app and the Share Extension are separate iOS
/// processes that open the *same* file in the App Group container, and WAL lets
/// one process read while another writes without corrupting the file (design:
/// "Process and storage boundaries"; Req 4.10).
///
/// Durability (Req 5.5): `DatabasePool.write` runs its body in a transaction
/// and only returns **after** the transaction has committed, and the
/// connections are configured with `PRAGMA synchronous = FULL` so a committed
/// transaction is flushed to disk before the write call returns. An operation
/// that reports success has therefore been persisted durably.
public final class SideQuestDatabase {

    /// The underlying GRDB pool. Exposed for the repository layer (task 6),
    /// which builds `ValueObservation` streams and performs reads/writes on it.
    public let dbPool: DatabasePool

    /// Opens (creating if needed) the database at `path` and runs all pending
    /// migrations. `path` is a real file path; `DatabasePool` does not support
    /// in-memory databases (those require a single-connection `DatabaseQueue`).
    public init(
        path: String,
        configuration: Configuration = SideQuestDatabase.makeConfiguration()
    ) throws {
        dbPool = try DatabasePool(path: path, configuration: configuration)
        try Self.migrator.migrate(dbPool)
    }

    /// Opens the database file inside the shared App Group container
    /// (`AppGroup.databaseURL`), the location shared by the main app and the
    /// Share Extension.
    ///
    /// Throws `SideQuestStoreError.appGroupUnavailable` when the container can't
    /// be resolved (a missing/misconfigured App Group entitlement — Req 13.7).
    public static func openShared() throws -> SideQuestDatabase {
        guard let url = AppGroup.databaseURL else {
            throw SideQuestStoreError.appGroupUnavailable(AppGroup.identifier)
        }
        return try SideQuestDatabase(path: url.path)
    }

    /// Builds the connection configuration used for every pooled connection.
    ///
    /// - `PRAGMA synchronous = FULL` makes commits durable across power loss in
    ///   WAL mode (Req 5.5). In WAL mode this fsyncs the WAL file on each
    ///   commit, which is what lets us treat a returned `write` as persisted.
    /// - A busy timeout means a connection waits for a transient lock (held
    ///   briefly by the other process or a WAL checkpoint) instead of failing
    ///   immediately, which is important for coordinated cross-process access.
    public static func makeConfiguration() -> Configuration {
        var configuration = Configuration()

        configuration.prepareDatabase { db in
            try db.execute(sql: "PRAGMA synchronous = FULL")
        }

        // Wait up to 10s for a contended lock rather than erroring out, so the
        // app and the Share Extension can write the shared file concurrently.
        configuration.busyMode = .timeout(10)

        return configuration
    }

    /// Performs a write transaction and returns only after it has durably
    /// committed (Req 5.5). Throwing inside `updates` rolls the transaction
    /// back, leaving the store at its prior state (atomic write).
    @discardableResult
    public func write<T>(_ updates: (Database) throws -> T) throws -> T {
        try dbPool.write(updates)
    }

    /// Performs a read against a consistent database snapshot.
    public func read<T>(_ value: (Database) throws -> T) throws -> T {
        try dbPool.read(value)
    }
}
