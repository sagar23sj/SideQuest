-- REVIEW REQUIRED: human DBA review needed before production.
-- Reverses 0001_init.up.sql. Drop order respects foreign-key dependencies.

BEGIN;

DROP TABLE IF EXISTS leaderboard_aggregates;
DROP TABLE IF EXISTS game_results;
DROP TABLE IF EXISTS voice_journal_extracted_items;
DROP TABLE IF EXISTS voice_journal_entries;
DROP TABLE IF EXISTS sub_actions;
DROP TABLE IF EXISTS action_plans;
DROP TABLE IF EXISTS action_items;
DROP TABLE IF EXISTS buckets;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS organizations;

COMMIT;
