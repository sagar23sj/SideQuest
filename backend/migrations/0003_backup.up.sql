-- REVIEW REQUIRED: human DBA review needed before production.
-- Adds a per-account snapshot backup used by the offline-first client to
-- preserve all planner data (buckets, action items, plans, sub-actions) so it
-- survives uninstall / clear-data / new device. The client uploads a single
-- JSON snapshot on a background schedule and restores it on a fresh install.
-- This is a coarse whole-account backup (not per-row merge); finer-grained
-- sync is handled separately by the sync_seq machinery.
--
-- Applied via golang-migrate.

BEGIN;

CREATE TABLE IF NOT EXISTS account_backups (
    account_id  UUID        PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    payload     JSONB       NOT NULL,
    device_id   TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
