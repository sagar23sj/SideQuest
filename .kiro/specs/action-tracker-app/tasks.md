# Implementation Plan: Action Tracker App

## Overview

This plan implements the Action Tracker App incrementally, Android-first with an iOS-ready architecture. Work is sequenced so the **core offline tracker** (share-sheet capture, buckets, board, timeframes, completion counter, local persistence) lands first as a fully usable milestone. Subsequent milestones layer on notifications + LLM enrichment, voice journaling, and finally the backend-dependent features (accounts/organizations, cross-device sync, games, leaderboards).

The pure logic (classification, board aggregation, validation, completion counting, due-set computation, games scoring/generation, leaderboard aggregation, sync conflict resolution) is built first and exercised by property-based tests. On the **Android client (Kotlin)** these use **Kotest** (or **jqwik**); on the **Go 1.26 backend** the shared logic (sync conflict resolution, leaderboard aggregation, puzzle generation) uses Go property testing (**gopter**/**rapid** or `testing/quick`). Property tests run a **minimum of 100 iterations** and each is tagged `Feature: action-tracker-app, Property {n}: {property_text}`. Each of the 32 Correctness Properties is implemented as a single property-based test placed close to the code it validates. Example/unit, integration, and smoke tests follow the design's Testing Strategy.

Tasks marked with `*` are optional (test hardening, backend-heavy work, and iOS-readiness) and can be deferred for a faster Android MVP. Core implementation tasks are never marked optional.

---

## Milestone A — Core Offline Tracker (usable MVP)

- [x] 1. Set up Android project and domain module
  - Create the Android app module (Kotlin + Jetpack Compose + Material 3) and a `:domain` Kotlin module for the client's pure logic
  - Add Hilt, Room, WorkManager, Retrofit/OkHttp, kotlinx.serialization, and Kotest (with the property-testing extension) to the build
  - Configure the test source sets and a Kotest project config; verify the build runs and an empty test passes
  - _Requirements: 14.1_

- [x] 2. Implement domain models and enums
  - [x] 2.1 Define core data classes and sealed types in `:domain`
    - Implement `ActionStatus`, `ContentType`, `Timeframe` (Today/WithinADay/WithinAWeek/SpecificDate), `LinkPreview`, `ActionItem`, `Bucket`, `ActionPlan`, `SubAction`, `WishlistFields`, and `SyncMeta`
    - Keep all types serializable so they align with the shared OpenAPI schema the Go backend also generates from
    - _Requirements: 14.2_

  - [x] 2.2 Write unit tests for model construction and serialization
    - Verify all `Timeframe` and `ContentType` variants serialize/deserialize round-trip
    - _Requirements: 14.2_

- [x] 3. Implement capture classification and timeframe validation (pure logic)
  - [x] 3.1 Implement content classification
    - Write `classify(SharedIntentData) -> ContentType | Unsupported` covering link, text, image, video reference, and unsupported MIME types
    - _Requirements: 1.4_

  - [x] 3.2 Write property test for unsupported-content rejection
    - **Property 1: Unsupported content is rejected and never persisted**
    - **Validates: Requirements 1.4**

  - [x] 3.3 Implement timeframe validation for specific dates
    - Reject specific dates earlier than the current date; accept today or later
    - _Requirements: 3.2, 3.3_

  - [x] 3.4 Write property test for specific-date acceptance rule
    - **Property 7: Specific-date timeframes accept today or later and reject the past**
    - **Validates: Requirements 3.3**

- [x] 4. Implement Room persistence layer for core entities
  - [x] 4.1 Create Room entities, DAOs, type converters, and the database
    - Map `ActionItem`, `Bucket`, `ActionPlan`/`SubAction`, `WishlistFields`, and `SyncMeta`; add type converters for `Timeframe` discriminator + payload
    - Expose Room-backed `Flow`s for reactive reads
    - _Requirements: 14.2, 14.3_

  - [x] 4.2 Write property test for persistence round trip
    - **Property 31: Persistence round trip survives restart, edits, and deletes** (cover all Timeframe variants; write→reload identical, edit reflected, delete absent)
    - **Validates: Requirements 14.2, 14.3, 3.4**

- [x] 5. Implement bucket management
  - [x] 5.1 Implement bucket CRUD with per-account name uniqueness
    - Create/rename/delete buckets; enforce normalized (trimmed, case-insensitive) name uniqueness before write; reject duplicates with an in-use message
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_

  - [x] 5.2 Write property test for bucket name uniqueness
    - **Property 5: Bucket names are unique per account**
    - **Validates: Requirements 2.6**

  - [x] 5.3 Implement reassign-or-delete flow for non-empty bucket deletion
    - Prompt to reassign contained items to a target bucket or delete them; preserve total item accounting
    - _Requirements: 2.5_

  - [x] 5.4 Write property test for non-empty bucket deletion
    - **Property 6: Deleting a non-empty bucket reassigns or deletes all of its items**
    - **Validates: Requirements 2.5**

  - [x] 5.5 Write unit tests for bucket CRUD branches
    - Cover create, rename, and delete of empty buckets
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 6. Implement the capture flow and repository (offline, no preview yet)
  - [x] 6.1 Implement the capture repository and confirm-capture logic
    - `beginCapture`/`confirmCapture`: on confirm create an `ActionItem` with status "not started", storing the selected bucket and timeframe; persist to Room
    - _Requirements: 1.5, 3.4_

  - [x] 6.2 Write property test for confirm-capture invariants
    - **Property 2: Confirming capture creates a not-started item preserving its bucket and timeframe**
    - **Validates: Requirements 1.5**

  - [x] 6.3 Implement the ShareTargetActivity and categorization sheet
    - Register the `intent-filter` for `ACTION_SEND`/`ACTION_SEND_MULTIPLE` (`text/plain`, `image/*`, `video/*`); receive intents, classify, show "not supported" + discard for unsupported types, otherwise launch the Bucket + Timeframe selection sheet
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 6.4 Write smoke + example tests for share dispatch
    - Manifest registers share target for text/image/video MIME types; share intent dispatch launches the categorization flow
    - _Requirements: 1.1, 1.2, 1.3_

- [x] 7. Implement board aggregation and completion counter (pure logic)
  - [x] 7.1 Implement board grouping, ordering, and status colors
    - Group items by bucket (no loss), sort ascending by `createdAt` within each group, and resolve each item's indicator color from the bucket's configured color for its current status
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 7.2 Write property test for board partitioning
    - **Property 8: The board partitions items by bucket without loss**
    - **Validates: Requirements 4.1**

  - [x] 7.3 Write property test for intra-bucket ordering
    - **Property 9: Items within a bucket are ordered by ascending creation time**
    - **Validates: Requirements 4.2**

  - [x] 7.4 Write property test for status indicator color
    - **Property 10: The status indicator color always matches the item's current status**
    - **Validates: Requirements 4.3, 4.4, 4.5, 4.7**

  - [x] 7.5 Implement status change and completion counter derivation
    - Allow changing an item's status; derive the Completion_Counter from the count of "completed" items, recomputed reactively
    - _Requirements: 4.6, 4.7, 5.2, 5.3, 5.4_

  - [x] 7.6 Write property test for completion counter
    - **Property 11: The completion counter equals the number of completed items**
    - **Validates: Requirements 5.2, 5.3, 5.4**

- [x] 8. Build the Board UI and wire the core tracker together
  - [x] 8.1 Implement Board screen, ViewModel, and item rows
    - Render grouped board from a `Flow<BoardState>`, show the Completion_Counter at the top, render status color indicators, and support status changes that update the indicator
    - Display raw source content/link for items (preview enrichment added in Milestone B)
    - _Requirements: 4.1, 4.2, 4.3, 4.6, 4.7, 5.1_

  - [x] 8.2 Write example/unit tests for Board UI branches
    - Counter rendered at top of board (5.1); status change persists (4.6)
    - _Requirements: 5.1, 4.6_

- [x] 9. Checkpoint — Core offline tracker
  - Ensure all tests pass, ask the user if questions arise.

---

## Milestone B — Link Previews, Action Plans, Wishlist

- [x] 10. Implement link preview enrichment (non-blocking)
  - [x] 10.1 Implement Preview_Service and preview-merge logic
    - Fetch Open Graph/Twitter Card metadata with a configurable timeout; on success store title/thumbnail/source with `resolved == true`; on failure/timeout store the raw link with `resolved == false` without blocking capture
    - _Requirements: 1.6, 1.7, 1.9, 1.10_

  - [x] 10.2 Write property test for resolved previews
    - **Property 3: A resolved link preview is stored faithfully**
    - **Validates: Requirements 1.7**

  - [x] 10.3 Write property test for unresolved/timeout fallback
    - **Property 4: An unresolved preview falls back to the raw link without blocking capture**
    - **Validates: Requirements 1.9, 1.10**

  - [x] 10.4 Wire preview fetch as a WorkManager job and reactive Board update
    - Kick off preview fetch off the capture critical path; update the Action_Item reactively via its Room-backed Flow; render title + thumbnail on the Board row
    - _Requirements: 1.8, 1.10_

  - [x] 10.5 Write example test for preview row rendering
    - Board row renders preview title + thumbnail for a link item
    - _Requirements: 1.8_

- [x] 11. Implement Action Plans (sub-action steps)
  - [x] 11.1 Implement Action_Plan logic: progress, completion prompt, reordering
    - Add ordered sub-actions; mark sub-actions complete; compute completed/total counts; surface "mark parent complete" prompt iff all sub-actions done; reorder as a permutation with contiguous ordering
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 11.2 Write property test for sub-action progress count
    - **Property 16: Sub-action progress count is accurate**
    - **Validates: Requirements 9.3**

  - [x] 11.3 Write property test for mark-complete prompt
    - **Property 17: The "mark complete" prompt appears exactly when all sub-actions are done**
    - **Validates: Requirements 9.4**

  - [x] 11.4 Write property test for sub-action reordering
    - **Property 18: Reordering sub-actions is a permutation with contiguous ordering**
    - **Validates: Requirements 9.5**

  - [x] 11.5 Build Action_Plan UI on the item detail screen
    - Add/edit/reorder sub-actions, completion toggles, progress display, and the parent-complete prompt
    - _Requirements: 9.1, 9.2, 9.3, 9.5_

- [x] 12. Implement shopping wishlist buckets
  - [x] 12.1 Implement wishlist designation and purchased logic
    - Designate a bucket as shopping; items in a shopping bucket are wishlist items with wishlist fields; record product name + optional source link; marking purchased sets `purchased == true` and status "completed"
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 12.2 Write property test for wishlist membership
    - **Property 14: Items in a shopping bucket are wishlist items**
    - **Validates: Requirements 8.2**

  - [x] 12.3 Write property test for purchased completion
    - **Property 15: Marking a wishlist item purchased completes it**
    - **Validates: Requirements 8.5**

  - [x] 12.4 Write example tests for wishlist editing
    - Wishlist field editing and purchased toggle
    - _Requirements: 8.1, 8.3, 8.4_

- [x] 13. Checkpoint — Previews, plans, wishlist
  - Ensure all tests pass, ask the user if questions arise.

---

## Milestone C — Notifications + LLM Enrichment

- [x] 14. Implement daily due-set computation (pure logic)
  - [x] 14.1 Implement "due today" resolution across all timeframes
    - Resolve today / within-a-day / within-a-week / specific-date against a given date; return exactly the items due that day
    - _Requirements: 6.4_

  - [x] 14.2 Write property test for the daily due-set
    - **Property 12: The daily due-set contains exactly the items due that day**
    - **Validates: Requirements 6.4**

- [x] 15. Implement the LLM client and fail-soft logic (client side)
  - [x] 15.1 Implement `LlmService` client against the backend LLM Proxy contract
    - Time-boxed calls returning `Ok`/`Unavailable`/`TimedOut`; notification text falls back to non-empty default; suggestions/description flows complete with an "unavailable" signal
    - (Backend LLM Proxy itself is implemented in Milestone E; stub/mocked for client work here)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 15.2 Write property test for LLM fail-soft behavior
    - **Property 13: LLM features fail soft**
    - **Validates: Requirements 7.4, 7.5**

  - [x] 15.3 Write example tests for LLM request triggers
    - Opening an item requests LLM suggestions (7.2); preparing a reminder requests LLM text (7.1)
    - _Requirements: 7.1, 7.2_

- [x] 16. Implement notifications and daily reminder scheduling
  - [x] 16.1 Implement notification permission, settings, and scheduling
    - Request POST_NOTIFICATIONS on first launch; enable/disable reminders; set reminder time; schedule the daily reminder via WorkManager at the chosen local time; on denied permission show explanation + deep link to OS settings
    - _Requirements: 6.1, 6.2, 6.3, 6.5_

  - [x] 16.2 Implement reminder delivery with LLM text and empty due-set fallback
    - At fire time gather items due that day, request LLM notification text (fall back to default on error/timeout), and when none are due send a "review upcoming" prompt
    - _Requirements: 6.4, 6.6, 7.1, 7.4_

  - [x] 16.3 Write example/smoke tests for reminders
    - Notification permission requested on first launch (6.1); reminder toggle/time persist (6.2, 6.3); empty due-set produces the review-upcoming reminder (6.6)
    - _Requirements: 6.1, 6.2, 6.3, 6.6_

- [x] 17. Checkpoint — Notifications + LLM
  - Ensure all tests pass, ask the user if questions arise.

---

## Milestone D — Voice Journaling

- [x] 18. Implement audio capture and Voice_Journal_Entry persistence
  - [x] 18.1 Implement audio recorder and entry storage
    - Request microphone permission on first record (block + route to settings if denied); capture audio until stopped; persist a Voice_Journal_Entry with the audio reference
    - _Requirements: 10.1, 10.2, 10.4_

  - [x] 18.2 Write integration test for audio capture
    - Audio capture start/stop yields a playable file reference
    - _Requirements: 10.2_

- [x] 19. Implement transcription and outcome storage
  - [x] 19.1 Implement Transcription client and success/failure handling
    - On stop, send audio to the Transcription Proxy contract; on success store transcript with `transcriptionFailed == false`; on failure retain audio, set `transcript == null`, `transcriptionFailed == true`, and show a failure message
    - _Requirements: 10.3, 10.4, 10.8_

  - [x] 19.2 Write property test for transcription outcomes
    - **Property 20: Transcription outcomes are stored correctly**
    - **Validates: Requirements 10.4, 10.8**

  - [x] 19.3 Write integration test for the transcription proxy
    - Transcription proxy returns a transcript for sample audio (1–2 cases against a mock)
    - _Requirements: 10.3_

- [x] 20. Implement action extraction and confirmation flow
  - [x] 20.1 Implement extract-and-confirm logic creating Action_Items
    - On transcript success request LLM extraction; present extracted items for confirmation; create exactly one Action_Item per confirmed item, linked to the originating Voice_Journal_Entry, and prompt for Bucket + Timeframe
    - _Requirements: 10.5, 10.6, 10.7_

  - [x] 20.2 Write property test for confirmation-gated creation
    - **Property 19: Extracted actions are created only on confirmation, one per confirmed item**
    - **Validates: Requirements 10.6, 10.7**

  - [x] 20.3 Write example test for extraction trigger
    - Transcript triggers LLM extraction
    - _Requirements: 10.5_

- [x] 21. Checkpoint — Voice journaling
  - Ensure all tests pass, ask the user if questions arise.

---

## Milestone E — Go Backend, Accounts, Sync, Games, Leaderboards

> The backend in this milestone is **Go 1.26** (`net/http` + pgx/PostgreSQL). The shared pure logic (sync conflict resolution, leaderboard aggregation, puzzle generation) is implemented in Go and validated with the same correctness properties used on the Kotlin client. The client-side counterparts of the sync/conflict logic remain in Kotlin.

- [x] 22. Scaffold the Go backend (Go 1.26 + PostgreSQL) and OpenAPI contract
  - Create the Go service (`net/http` enhanced routing, or chi), the PostgreSQL schema (pgx + sqlc, migrations via golang-migrate) mirroring the domain entities, and the OpenAPI 3 document shared by both clients (server types via oapi-codegen; Kotlin/Swift client models generated from the same spec)
  - Set up the LLM Proxy and Transcription Proxy adapters (pluggable provider), keeping provider keys server-side
  - _Requirements: 13.1, 7.1, 7.2, 7.3, 10.3, 10.5_

- [x] 23. Implement accounts and organizations
  - [x] 23.1 Implement account creation, org join, and data association
    - Account creation; optional org join at signup; associate Action_Items, Buckets, Voice_Journal_Entries, and Game results with the current account; store auth tokens in EncryptedSharedPreferences with silent refresh
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 23.2 Write property test for account association
    - **Property 29: Data created while signed in is associated with the current account**
    - **Validates: Requirements 13.3**

  - [x] 23.3 Write example test for signup flow
    - Account creation and org join during signup
    - _Requirements: 13.1, 13.2_

- [-] 24. Implement offline-first sync (push/pull, tombstones, LWW)
  - [x] 24.1 Implement deterministic last-writer-wins conflict resolution (pure logic)
    - Merge two concurrent versions by greater `updatedAt`; deterministic and order-independent; record the loser to a conflict log
    - _Requirements: 14.4_

  - [x] 24.2 Write property test for conflict resolution
    - **Property 32: Conflict resolution is deterministic last-writer-wins**
    - **Validates: Requirements 14.4**

  - [-] 24.3 Implement sync push/pull endpoints and client sync jobs
    - `POST /sync/push` and `GET /sync/pull?since=` with sync tokens; client writes locally then enqueues network-constrained WorkManager push/pull jobs with backoff; propagate deletes via tombstones; upload audio/thumbnails to object storage
    - Backend endpoints (auth-protected), the sync repository (LWW + tombstones + monotonic sync token), the 0002 migration, and the OpenAPI contract are implemented and tested. The client-side WorkManager push/pull jobs remain — they require the Android SDK, which is not available in this environment.
    - _Requirements: 13.4, 14.4_

  - [x] 24.4 Write property test for cross-device sync round trip
    - **Property 30: Sync makes data available across devices (round trip)**
    - **Validates: Requirements 13.4**

- [x] 25. Implement the games engine (Go backend, server-authoritative pure logic)
  - [x] 25.1 Implement deterministic puzzle generation (server-authoritative)
    - Generate Spelling_Bee and Word_Guess daily puzzles deterministically from (orgId, gameType, date) so all org members get identical puzzles
    - _Requirements: 11.1, 11.2_

  - [x] 25.2 Write property test for deterministic puzzles
    - **Property 21: Daily puzzles are deterministic per organization and date**
    - **Validates: Requirements 11.2**

  - [x] 25.3 Implement Word_Guess feedback and Spelling_Bee acceptance
    - Word_Guess per-letter correct/present/absent feedback handling duplicate letters; Spelling_Bee accepts a word iff all letters are allowed and the word is in the word list
    - _Requirements: 11.5, 11.6_

  - [x] 25.4 Write property test for Word_Guess feedback
    - **Property 24: Word_Guess feedback is correct, including duplicate letters**
    - **Validates: Requirements 11.5**

  - [x] 25.5 Write property test for Spelling_Bee acceptance
    - **Property 25: Spelling_Bee acceptance follows the allowed-letters and word-list rule**
    - **Validates: Requirements 11.6**

  - [x] 25.6 Implement scoring, completion, and replay guard
    - Record exactly one score on completion with score from final state; on replay of a completed day, return the recorded result without rescoring
    - _Requirements: 11.3, 11.4_

  - [x] 25.7 Write property test for single-score recording
    - **Property 22: Completing a game records exactly one score**
    - **Validates: Requirements 11.3**

  - [x] 25.8 Write property test for replay guard
    - **Property 23: Replaying a completed daily game does not rescore**
    - **Validates: Requirements 11.4**

- [x] 26. Implement leaderboard aggregation (Go backend pure logic + endpoints)
  - [x] 26.1 Implement period aggregation and ranking
    - On score recording, update daily/weekly/monthly per-user totals for the user's org; rank descending by total; isolate scores by period key (monthly reset, new daily/weekly boards at boundaries)
    - _Requirements: 12.2, 12.3, 12.4, 12.5, 12.6_

  - [x] 26.2 Write property test for three-board score updates
    - **Property 26: Recording a score updates all three period leaderboards by that score**
    - **Validates: Requirements 12.2**

  - [x] 26.3 Write property test for descending ranking
    - **Property 27: Leaderboards are ranked by descending total score**
    - **Validates: Requirements 12.3**

  - [x] 26.4 Write property test for period-key isolation
    - **Property 28: Leaderboards isolate scores by period key**
    - **Validates: Requirements 12.4, 12.5, 12.6**

- [ ] 27. Build games and leaderboard UI and wire to backend
  - [ ] 27.1 Implement game screens and the leaderboard screen
    - Spelling_Bee and Word_Guess play screens with feedback and replay-result display; daily/weekly/monthly leaderboard views per org; no-org user sees a join prompt on the leaderboard
    - _Requirements: 11.1, 11.4, 11.5, 12.1, 13.5_

  - [ ] 27.2 Write example/smoke tests for games and leaderboards
    - Both games available (11.1); three period boards shown per org (12.1); no-org user sees join prompt (13.5)
    - _Requirements: 11.1, 12.1, 13.5_

- [ ] 28. Final checkpoint — full feature integration
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster Android MVP. They cover test hardening, the Go backend scaffold, and iOS-readiness work. Core implementation tasks are never optional.
- Property-based tests (Properties 1–32) run a minimum of 100 iterations and are tagged `Feature: action-tracker-app, Property {n}: {property_text}`. Kotlin client logic uses Kotest/jqwik; Go backend shared logic uses gopter/rapid or `testing/quick`. Each property is placed close to the logic it validates so regressions surface early.
- Example/unit tests cover UI branches and concrete flows; integration tests cover external-service wiring (LLM proxy, STT, audio); smoke tests cover one-time configuration (share-target registration, permission requests, both games present, Android build/run).
- Clients (Android Kotlin, later iOS Swift) talk to the Go backend over the shared OpenAPI 3 contract. The shared pure logic (sync conflict resolution, leaderboard aggregation, puzzle generation) is implemented in Go on the server and in Kotlin on the client, with the same properties validating both; iOS later re-implements its client logic in Swift (e.g., SwiftCheck) against the same contract.
- Each task references specific requirement sub-clauses for traceability; checkpoints provide incremental validation between milestones.
