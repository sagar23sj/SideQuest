import Foundation

/// Contract endpoint paths (`backend/api/openapi.yaml`), shared by every service
/// that talks to the backend so routes are declared once and stay in lockstep
/// with the contract. Paths are relative to the `BackendClient` transport's base
/// URL.
public enum BackendEndpoints {

    // MARK: Accounts / auth (Req 2.4, 10.x — used by AuthService, task 15.1)

    /// `POST /accounts` — create an account and sign in.
    public static let accounts = "/accounts"

    /// `POST /auth/login` — authenticate a credential pair.
    public static let login = "/auth/login"

    /// `POST /auth/refresh` — exchange a refresh token for a new pair.
    public static let refresh = "/auth/refresh"

    // MARK: Sync (Req 6.x — used by SyncService, task 16)

    /// `POST /sync/push` — push local changes.
    public static let syncPush = "/sync/push"

    /// `GET /sync/pull` — pull changes since a sync token.
    public static let syncPull = "/sync/pull"

    // MARK: LLM proxy (Req 7.16 — used by LLMService, task 13.10)

    /// `POST /llm/notification-text` — generate reminder notification text.
    public static let llmNotificationText = "/llm/notification-text"
}
