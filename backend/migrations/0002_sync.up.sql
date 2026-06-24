-- REVIEW REQUIRED: human DBA review needed before production.
-- Adds the monotonic per-row sync sequence backing offline-first sync push/pull
-- (task 24; Req 13.4, 14.4). A pull returns rows whose sync_seq is greater than
-- the client's last sync token; the response's new token is the max sync_seq
-- seen. Using a single global BIGSERIAL-style sequence gives a total order of
-- changes that is cheap to index and query with "since".
--
-- Applied via golang-migrate.

BEGIN;

-- A shared sequence so sync tokens are globally monotonic across rows. Each
-- insert/update stamps the row with the next value.
CREATE SEQUENCE IF NOT EXISTS action_items_sync_seq;

ALTER TABLE action_items
    ADD COLUMN IF NOT EXISTS sync_seq BIGINT NOT NULL DEFAULT nextval('action_items_sync_seq');

-- Pull is "everything with sync_seq > since" for an account, ordered by seq.
CREATE INDEX IF NOT EXISTS idx_action_items_account_sync_seq
    ON action_items (account_id, sync_seq);

COMMIT;
