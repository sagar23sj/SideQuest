-- REVIEW REQUIRED: human DBA review needed before production.
-- Reverses 0002_sync.up.sql.

BEGIN;

DROP INDEX IF EXISTS idx_action_items_account_sync_seq;

ALTER TABLE action_items
    DROP COLUMN IF EXISTS sync_seq;

DROP SEQUENCE IF EXISTS action_items_sync_seq;

COMMIT;
