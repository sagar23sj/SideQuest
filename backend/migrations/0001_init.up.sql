-- REVIEW REQUIRED: human DBA review needed before production.
-- This initial schema mirrors the domain entities in the design's Data Models
-- section. Column types, index choices, partitioning, and constraints have NOT
-- been validated against production data volumes or access patterns. A DBA must
-- review indexes/types/volumes (and consider FK on-delete behavior, leaderboard
-- materialization strategy, and audio/thumbnail storage references) before this
-- runs anywhere near production. Later tasks (23 accounts, 24 sync, 25 games,
-- 26 leaderboards) refine these tables.
--
-- Applied via golang-migrate. IDs are client-generated UUIDs (created offline),
-- so they are TEXT/UUID values supplied by the client, not server-generated.
-- All syncable tables carry sync metadata columns: updated_at, version, deleted.

BEGIN;

-- Organizations -----------------------------------------------------------
CREATE TABLE organizations (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Accounts ----------------------------------------------------------------
CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    email           TEXT        NOT NULL,
    display_name    TEXT        NOT NULL,
    -- Password hash uses argon2id/bcrypt (see golang-security); nullable to
    -- support future federated/social sign-in. Never store plaintext.
    password_hash   TEXT,
    org_id          UUID        REFERENCES organizations (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT accounts_email_unique UNIQUE (email)
);

CREATE INDEX idx_accounts_org_id ON accounts (org_id);

-- Buckets -----------------------------------------------------------------
CREATE TABLE buckets (
    id                  UUID PRIMARY KEY,
    account_id          UUID        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name                TEXT        NOT NULL,
    is_shopping         BOOLEAN     NOT NULL DEFAULT FALSE,
    not_started_color   TEXT        NOT NULL,
    in_progress_color   TEXT        NOT NULL,
    completed_color     TEXT        NOT NULL,
    -- Sync metadata
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 1,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Bucket name is unique per account (Req 2.6). Normalization (trim,
    -- case-insensitive) is enforced in application code before write; this
    -- constraint is a backstop on the stored value.
    CONSTRAINT buckets_account_name_unique UNIQUE (account_id, name)
);

CREATE INDEX idx_buckets_account_id ON buckets (account_id);

-- Action items ------------------------------------------------------------
-- action_status: 1=not_started, 2=in_progress, 3=completed (0=unknown).
-- content_type:  1=link, 2=text, 3=image, 4=video_ref (0=unknown).
-- timeframe_kind:1=today, 2=within_a_day, 3=within_a_week, 4=specific_date.
CREATE TABLE action_items (
    id                  UUID PRIMARY KEY,
    account_id          UUID        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    bucket_id           UUID        NOT NULL REFERENCES buckets (id) ON DELETE CASCADE,
    title               TEXT        NOT NULL,
    description         TEXT,                       -- nullable: may be LLM-generated
    content_type        SMALLINT    NOT NULL,
    source_content      TEXT,                       -- nullable raw text/link/media ref
    status              SMALLINT    NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,       -- client-authoritative ordering within bucket
    is_wishlist_item    BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Timeframe (oneOf discriminator + payload)
    timeframe_kind      SMALLINT    NOT NULL,
    timeframe_date      DATE,                       -- non-null only when kind = specific_date

    -- Link preview (nullable group; present for link items)
    preview_title       TEXT,
    preview_thumb_url   TEXT,
    preview_source_name TEXT,
    preview_raw_url     TEXT,
    preview_resolved    BOOLEAN,

    -- Wishlist fields (nullable group; present in shopping buckets)
    wishlist_product    TEXT,
    wishlist_source     TEXT,
    wishlist_purchased  BOOLEAN,

    -- Sync metadata
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 1,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_action_items_account_id ON action_items (account_id);
CREATE INDEX idx_action_items_bucket_created ON action_items (bucket_id, created_at);
-- Sync pull is "everything changed since token"; index supports it.
CREATE INDEX idx_action_items_account_updated ON action_items (account_id, updated_at);

-- Action plans ------------------------------------------------------------
CREATE TABLE action_plans (
    id              UUID PRIMARY KEY,
    action_item_id  UUID        NOT NULL REFERENCES action_items (id) ON DELETE CASCADE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 1,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT action_plans_item_unique UNIQUE (action_item_id)
);

-- Sub-actions are ordered steps within a plan. Modeled as rows (rather than
-- JSON) so ordering/uniqueness is enforceable. order_index forms a contiguous
-- sequence per plan (Req 9.5).
CREATE TABLE sub_actions (
    id              UUID PRIMARY KEY,
    action_plan_id  UUID        NOT NULL REFERENCES action_plans (id) ON DELETE CASCADE,
    text            TEXT        NOT NULL,
    order_index     INTEGER     NOT NULL,
    completed       BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT sub_actions_plan_order_unique UNIQUE (action_plan_id, order_index)
);

CREATE INDEX idx_sub_actions_plan ON sub_actions (action_plan_id);

-- Voice journal entries ---------------------------------------------------
CREATE TABLE voice_journal_entries (
    id                      UUID PRIMARY KEY,
    account_id              UUID        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    audio_ref               TEXT        NOT NULL,    -- object storage key / local path
    transcript              TEXT,                    -- null when transcription failed
    transcription_failed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    -- Sync metadata
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 1,
    deleted                 BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_vje_account_id ON voice_journal_entries (account_id);

-- Links a voice journal entry to the action items extracted from it.
CREATE TABLE voice_journal_extracted_items (
    voice_journal_entry_id  UUID NOT NULL REFERENCES voice_journal_entries (id) ON DELETE CASCADE,
    action_item_id          UUID NOT NULL REFERENCES action_items (id) ON DELETE CASCADE,
    PRIMARY KEY (voice_journal_entry_id, action_item_id)
);

-- Game results ------------------------------------------------------------
-- game_type: 1=spelling_bee, 2=word_guess (0=unknown).
CREATE TABLE game_results (
    id              UUID PRIMARY KEY,
    account_id      UUID        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    org_id          UUID        REFERENCES organizations (id) ON DELETE SET NULL,
    game_type       SMALLINT    NOT NULL,
    play_date       DATE        NOT NULL,
    score           INTEGER     NOT NULL,
    completed_at    TIMESTAMPTZ NOT NULL,
    -- Sync metadata
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 1,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Replay guard (Req 11.4): one scored result per account/game/day.
    CONSTRAINT game_results_unique_per_day UNIQUE (account_id, game_type, play_date)
);

CREATE INDEX idx_game_results_org_date ON game_results (org_id, play_date);

-- Leaderboard aggregates --------------------------------------------------
-- Materialized per-user totals keyed by (org, game, period, period_key).
-- period: 1=day, 2=week, 3=month. period_key examples: 2025-06-14, 2025-W24,
-- 2025-06. Recomputed/incremented on score writes (task 26).
CREATE TABLE leaderboard_aggregates (
    org_id          UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    game_type       SMALLINT    NOT NULL,
    period          SMALLINT    NOT NULL,
    period_key      TEXT        NOT NULL,
    account_id      UUID        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    total_score     INTEGER     NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, game_type, period, period_key, account_id)
);

-- Supports ranked reads (descending total) for a given board.
CREATE INDEX idx_leaderboard_board_score
    ON leaderboard_aggregates (org_id, game_type, period, period_key, total_score DESC);

COMMIT;
