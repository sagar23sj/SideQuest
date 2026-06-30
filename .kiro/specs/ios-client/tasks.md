# Implementation Plan: iOS Client (SideQuest_iOS)

## Overview

This plan converts the iOS Client design into incremental Swift/SwiftUI coding tasks. It starts with the Xcode project, target, and App Group scaffolding, then builds the data models and local GRDB store, the portable domain logic (validated by the reused sibling Correctness Properties), and the platform features (capture/Share Extension, board + completion, buckets/planning, notifications, sync, auth, loading), finishing with end-to-end wiring and distribution validation.

Each task builds on prior tasks and ends by integrating into the running app — no orphaned code. Property-based tests use **SwiftCheck** with a **minimum of 100 iterations** per property; each property test references a specific Correctness Property from the design. Sub-tasks marked with `*` are optional (tests) and can be skipped for a faster MVP.

## Tasks

- [x] 1. Set up Xcode project, targets, and shared App Group scaffolding
  - Create the `SideQuest_iOS` Xcode project with a main app target (SwiftUI lifecycle) and a Share Extension target, both iPhone-only
  - Declare a Minimum_iOS_Version in both targets and restrict device family to iPhone
  - Add the GRDB and SwiftCheck dependencies (SPM)
  - Define the shared App Group identifier (identical in both targets) and add the App Group entitlement to both targets
  - Create the shared Swift module/folder for code reused by both targets (models, store, domain logic)
  - _Requirements: 1.1, 1.2, 1.4, 13.2_

- [x] 2. Define data models from the OpenAPI schema
  - [x] 2.1 Generate/author Swift `Generated_Models` structs from `backend/api/openapi.yaml`
    - Author `ActionItem`, `Bucket`, `ActionPlan`, `SubAction`, `Account`, `Thought`, `SyncMeta`, `TaskReminder`, `TimeOfDay`, and the `ActionStatus`/`ContentType`/`Timeframe` enums as `Codable` per the design's Data Models section
    - Implement `Timeframe` Codable encoding as discriminator + payload so values round-trip; ensure `specificDate` carries a `Date`
    - Ensure on-the-wire JSON matches the contract used by the Android client and backend
    - _Requirements: 2.1, 2.2, 3.3_

  - [x] 2.2 Write unit tests for model Codable round-trips
    - Test JSON encode/decode for every model, including all `Timeframe` variants and `ContentType` values
    - _Requirements: 2.2, 3.3_

- [x] 3. Implement the local GRDB store in the App Group container
  - [x] 3.1 Create the GRDB `DatabasePool` over the App Group SQLite file with WAL mode
    - Open `SideQuest.sqlite` in the App Group container with coordinated multi-process access (WAL)
    - Define GRDB migrations and table mappings for all models; persist `Timeframe` as discriminator + payload
    - Commit writes durably before reporting an operation complete
    - _Requirements: 5.1, 5.5, 4.10_

  - [x] 3.2 Implement a client identifier generator for new entities
    - Generate client-side UUIDs for all new entities so records can be created offline without coordination
    - _Requirements: 5.7_

  - [x] 3.3 Write property test for client identifier uniqueness
    - **Property 4: Client-generated identifiers are globally unique**
    - **Validates: Requirements 5.7**

  - [x] 3.4 Write property test for persistence round trip
    - **Reused Property 31: Persistence round trip survives restart, edits, and deletes**
    - **Validates: Requirements 5.4**

- [x] 4. Implement the portable domain logic (pure Swift, no I/O)
  - [x] 4.1 Implement bucket-name validation
    - Normalize (trim + case-insensitive) uniqueness per account; enforce the 1–50 character length rule
    - _Requirements: 9.2, 9.3_

  - [x] 4.2 Write property test for bucket-name uniqueness
    - **Reused Property 5: Bucket names unique per account**
    - **Validates: Requirements 9.2**

  - [x] 4.3 Write property test for bucket-name length validation
    - **Property 19: Bucket-name length validation accepts exactly 1–50 trimmed characters**
    - **Validates: Requirements 9.3**

  - [x] 4.4 Implement timeframe / specific-date validation
    - Accept "today", "within a day", "within a week"; accept a specific date only when it is today-or-later in the device local time zone
    - _Requirements: 9.6, 9.7_

  - [x] 4.5 Write property test for specific-date timeframe validation
    - **Reused Property 7: Specific-date timeframe accepts today-or-later, rejects past**
    - **Validates: Requirements 9.7**

  - [x] 4.6 Implement board aggregation and ordering
    - Partition Action_Items by bucket without loss; within a bucket order by ascending `createdAt`, tie-broken by ascending id
    - _Requirements: 8.1_

  - [x] 4.7 Write property test for board partitioning
    - **Reused Property 8: Board partitions items by bucket without loss**
    - **Validates: Requirements 8.1**

  - [x] 4.8 Write property test for board ordering
    - **Reused Property 9: Items within a bucket ordered by ascending creation time**
    - **Validates: Requirements 8.1**

  - [x] 4.9 Implement status-to-color mapping and completion counter
    - Map each Action_Status to a distinct (injective) color from the bucket's per-status color map
    - Compute the completion counter as the number of "completed" items, clamped at zero
    - _Requirements: 8.2, 8.3, 8.5, 8.6_

  - [x] 4.10 Write property test for status-color matching
    - **Reused Property 10: Status indicator color always matches current status**
    - **Validates: Requirements 8.2, 8.3**

  - [x] 4.11 Write property test for status-to-color injectivity
    - **Property 17: Status-to-color mapping is injective per bucket**
    - **Validates: Requirements 8.2**

  - [x] 4.12 Write property test for the completion counter
    - **Reused Property 11: Completion counter equals the number of completed items**
    - **Validates: Requirements 8.5, 8.6**

  - [x] 4.13 Implement action-plan progress and reorder logic
    - Compute completed-vs-total sub-action counts; signal the "mark complete" prompt exactly when all sub-actions are done; reorder as a permutation that preserves contiguous ordering of unmoved sub-actions
    - _Requirements: 9.8, 9.9, 9.10_

  - [x] 4.14 Write property test for sub-action progress count
    - **Reused Property 16: Sub-action progress count is accurate**
    - **Validates: Requirements 9.9**

  - [x] 4.15 Write property test for the mark-complete prompt
    - **Reused Property 17: "Mark complete" prompt appears exactly when all sub-actions done**
    - **Validates: Requirements 9.10**

  - [x] 4.16 Write property test for sub-action reordering
    - **Reused Property 18: Reordering sub-actions is a permutation with contiguous ordering**
    - **Validates: Requirements 9.8**

  - [x] 4.17 Implement sync conflict resolution (last-writer-wins)
    - Deterministic last-writer-wins keyed on record update time, tie-broken by record id
    - _Requirements: 6.2_

  - [x] 4.18 Write property test for conflict resolution
    - **Reused Property 32: Conflict resolution is deterministic last-writer-wins**
    - **Validates: Requirements 6.2**

  - [x] 4.19 Write property test for cross-implementation equivalence
    - **Property 1: Cross-implementation equivalence of portable domain logic**
    - Drive the Swift domain logic with the shared golden input/output vectors and assert field-by-field, ordering-exact equality
    - **Validates: Requirements 1.6, 3.2, 3.3**

- [x] 5. Checkpoint - domain logic and store
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement repositories with reactive reads and pending-sync tracking
  - [x] 6.1 Implement repositories over the GRDB store
    - Provide create/edit/delete for Action_Items, Buckets, and Action_Plans, all reading/writing the local store
    - Expose GRDB `ValueObservation` streams so views update reactively
    - Mark every mutation `dirty` (pending sync) and keep it dirty until a successful push acknowledgment clears it
    - On commit failure, preserve prior persisted state, retain input, and surface a "not saved" indication
    - _Requirements: 5.2, 5.3, 5.6, 5.8_

  - [x] 6.2 Write property test for pending-sync tracking
    - **Property 5: Local mutations are marked pending and stay pending until acknowledged**
    - **Validates: Requirements 5.6**

  - [x] 6.3 Write property test for commit-failure atomicity
    - **Property 3: Capture and commit failures never leave partial state and retain user input**
    - **Validates: Requirements 4.6, 5.8**

- [x] 7. Implement bucket management
  - [x] 7.1 Implement bucket CRUD with delete-decision flows
    - Create/rename/delete buckets using the validation from task 4.1
    - On deleting a non-empty bucket with another bucket present, drive the reassign-or-delete decision (require confirmation); on the last bucket, drive the confirm-delete-contained-items prompt
    - _Requirements: 9.1, 9.4, 9.5_

  - [x] 7.2 Write property test for non-empty bucket deletion
    - **Reused Property 6: Deleting a non-empty bucket reassigns or deletes all items**
    - **Validates: Requirements 9.4, 9.5**

- [x] 8. Implement the Share Extension capture flow
  - [x] 8.1 Implement content classification and the categorization sheet
    - Declare `NSExtensionActivationRule` for links, text, images, and movies
    - Classify shared attachments; for unsupported types show "content type not supported", discard, and end the extension request
    - Host a SwiftUI sheet requiring exactly one Bucket and one Timeframe; disable save until both are chosen; discard on cancel before confirm
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.7_

  - [x] 8.2 Write property test for unsupported-content rejection
    - **Reused Property 1: Unsupported content is rejected and never persisted**
    - **Validates: Requirements 4.4**

  - [x] 8.3 Implement confirm-capture writing to the shared store
    - On confirm, create an Action_Item with status "not started" and write it to the shared App Group store
    - On store-write failure, show an error, create no partial item, and retain the user's selections for retry
    - _Requirements: 4.5, 4.6, 4.10_

  - [x] 8.4 Write property test for capture confirm
    - **Reused Property 2: Confirming capture creates a not-started item preserving bucket/timeframe**
    - **Validates: Requirements 4.5**

  - [x] 8.5 Write property test for cross-process visibility
    - **Property 2: Captured items are visible across the extension/main-app process boundary**
    - **Validates: Requirements 4.10**

- [x] 9. Implement the link preview service
  - [x] 9.1 Implement `PreviewService` over LinkPresentation
    - Fetch link metadata off the capture critical path with a 5-second timeout; allow confirming capture before metadata returns
    - On success store resolved preview fields; on timeout/failure store an unresolved preview whose `rawUrl` equals the original URL and complete capture; update reactively if a later fetch succeeds
    - _Requirements: 4.8, 4.9_

  - [x] 9.2 Write property test for unresolved-preview fallback
    - **Reused Property 4: Unresolved preview falls back to raw link without blocking capture**
    - **Validates: Requirements 4.9**

- [x] 10. Checkpoint - capture and persistence
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement the action board, completion counter, and hold gesture
  - [x] 11.1 Build the board view and completion counter UI
    - Render `BoardState` from the repository's reactive stream, grouped by bucket with the design's ordering and per-status colors
    - Display the completion counter at the top of the board; update the color indicator within 500 ms on a persisted status change
    - On a failed status change, retain prior status, leave the indicator unchanged, and show an error indication
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 11.2 Write property test for failed status change
    - **Property 16: A failed status change preserves the prior status and indicator**
    - **Validates: Requirements 8.4**

  - [x] 11.3 Implement the press-and-hold completion control
    - Drive a progressive fill whose proportion equals `min(elapsedHold / 800ms, 1)`; complete only at ≥800 ms continuous hold, then set status "completed", fire haptics, and play a ≤2000 ms celebration animation; releasing early cancels and resets with no status change
    - _Requirements: 8.7, 8.8, 8.9_

  - [x] 11.4 Write property test for hold-gesture progress
    - **Property 18: Press-and-hold progress is proportional and completes only at the threshold**
    - **Validates: Requirements 8.7, 8.9**

- [x] 12. Implement action planning and timeframe UI
  - [x] 12.1 Build the action-plan and timeframe screens
    - Add/edit an Action_Plan with 1–100 ordered sub-actions; mark a sub-action completed; reorder sub-actions; display completed/total; prompt to mark the item completed when all sub-actions are done
    - Offer the timeframe option set and reject past specific dates with a message using the validation from task 4.4
    - _Requirements: 9.6, 9.7, 9.8, 9.9, 9.10_

- [x] 13. Implement notifications and reminders
  - [x] 13.1 Implement `NotificationService` permission and scheduling core
    - Request notification permission exactly once on first use of a notifying feature; on denial show an explanation and a deep link to iOS notification settings
    - Schedule all notifications with `UNCalendarNotificationTrigger` from local `DateComponents` (hour/minute, optional date) so fire times track local wall-clock time and persist across reboots; reschedule pending requests on launch
    - _Requirements: 7.1, 7.10, 7.11, 7.18, 11.1, 11.4_

  - [x] 13.2 Write property test for local wall-clock anchoring
    - **Property 12: Scheduled notifications are anchored to local wall-clock time**
    - **Validates: Requirements 7.10**

  - [x] 13.3 Write property test for the permission prompt
    - **Property 22: A permission prompt is triggered at most once per capability**
    - **Validates: Requirements 11.1, 11.5**

  - [x] 13.4 Implement Task_Reminder creation, validation, and occurrence scheduling
    - Allow attaching a reminder (time of day + until-date, optional daily recurrence); reject a missing time and an until-date outside [today, today+365], retaining other values with a message
    - Schedule occurrences as the day-set up to and including the until-date; cancel all pending reminders when the item is completed; stop after the until-date
    - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9_

  - [x] 13.5 Write property test for reminder occurrence day-set
    - **Property 10: Reminder occurrences are exactly the scheduled day-set up to the until-date**
    - **Validates: Requirements 7.7, 7.9**

  - [x] 13.6 Write property test for cancelling reminders on completion
    - **Property 11: Completing an item cancels all of its pending reminders**
    - **Validates: Requirements 7.8**

  - [x] 13.7 Write property test for until-date window validation
    - **Property 13: Until-date selection is accepted exactly within the valid window**
    - **Validates: Requirements 7.4**

  - [x] 13.8 Implement the evening nudge and global daily notification
    - Schedule one daily evening nudge summarizing up to 20 not-completed items that have no Task_Reminder; omit the nudge when no eligible items exist; allow enable/disable and time selection
    - Allow enable/disable and time selection for the optional Global_Daily_Notification
    - _Requirements: 7.12, 7.13, 7.14, 7.15_

  - [x] 13.9 Write property test for evening-nudge selection
    - **Property 14: The evening nudge selects only eligible items, capped at 20**
    - **Validates: Requirements 7.13, 7.14**

  - [x] 13.10 Implement `LLMService` notification text with fail-soft default
    - Request notification text (≤200 chars) from the LLM_Proxy with a 5-second timeout; on timeout/error/unavailable, deliver with non-empty default text
    - _Requirements: 7.16, 7.17_

  - [x] 13.11 Write property test for bounded, fail-soft notification text
    - **Property 15: Notification text is bounded and fails soft**
    - **Validates: Requirements 7.16, 7.17**

- [x] 14. Checkpoint - board, planning, notifications
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement auth, Keychain storage, and the backend client
  - [x] 15.1 Implement `AuthService` with Keychain token storage
    - Implement account registration (`POST /accounts`), sign-in (`POST /auth/login`), and refresh (`POST /auth/refresh`) per the contract's bearer/JWT scheme
    - Store access/refresh tokens in the iOS Keychain (shared access group); on access-token expiry refresh silently without touching the local store; on refresh failure route to re-authentication preserving the local store
    - On account-creation failure retain entered inputs and show the failure reason; on invalid sign-in show a credentials-not-accepted message
    - _Requirements: 2.4, 10.1, 10.4, 10.5, 10.6, 10.7, 10.8_

  - [x] 15.2 Write property test for token-refresh local-store preservation
    - **Property 20: Token refresh and refresh failure preserve the local store**
    - **Validates: Requirements 10.5, 10.7**

  - [x] 15.3 Implement `BackendClient` and contract error mapping
    - Thin `URLSession` client over `Generated_Models` communicating exclusively over REST/JSON
    - Map contract-defined structured errors to category-specific user-facing messages while preserving unsaved input; treat undefined errors and >30 s timeouts as transient with up to 3 retries; on auth failure show a message without auto-retry
    - _Requirements: 2.1, 2.5, 2.6, 2.7_

  - [x] 15.4 Write property test for contract error mapping
    - **Property 21: Contract-defined errors map to category-specific messages preserving input**
    - **Validates: Requirements 2.5**

- [x] 16. Implement the sync service and background scheduling
  - [x] 16.1 Implement `/sync/push` and `/sync/pull` with idempotent, bounded-retry behavior
    - Push local dirty changes and pull remote changes through the contract; clear `dirty` on push acknowledgment; associate created data with the current account
    - Make pushes idempotent keyed on the client-generated id; on push/pull failure retain changes, retry up to the configured maximum, and preserve local-store state on total failure
    - Propagate deletes via tombstones; resolve conflicts with the domain LWW logic from task 4.17
    - _Requirements: 6.1, 6.2, 6.3, 6.8, 6.9, 10.2_

  - [x] 16.2 Write property test for idempotent pushes
    - **Property 6: Synchronization pushes are idempotent by client identifier**
    - **Validates: Requirements 6.8**

  - [x] 16.3 Write property test for bounded-retry state preservation
    - **Property 8: Sync failures retain changes within a bounded retry count and preserve state**
    - **Validates: Requirements 2.6, 6.9**

  - [x] 16.4 Write property test for tombstone delete propagation
    - **Property 9: Deletes propagate via tombstones across a sync round trip**
    - **Validates: Requirements 6.3**

  - [x] 16.5 Write property test for account association across devices
    - **Reused Property 29: Data created while signed in is associated with the current account**
    - **Reused Property 30: Sync makes data available across devices (round trip)**
    - **Validates: Requirements 10.2, 10.3**

  - [x] 16.6 Implement first-sign-in full pull (atomic) and sync triggers
    - On first sign-in, perform an all-or-nothing full pull into the local store; on partial failure import nothing, show a message, and retry next pass
    - Run a sync pass on connectivity restore and on foreground entry; register `BGTaskScheduler` identifiers (declared in `Info.plist`) for background sync
    - _Requirements: 6.4, 6.5, 6.6, 6.7, 6.10_

  - [x] 16.7 Write property test for atomic first-sign-in pull
    - **Property 7: First-sign-in pull is atomic**
    - **Validates: Requirements 6.10**

- [x] 17. Implement the loading experience (thought of the day)
  - [x] 17.1 Implement `ThoughtProvider` and the loading view
    - Ship a built-in on-device set of ≥30 thoughts (1–280 chars each); select deterministically by local calendar date; on selection failure return a non-empty default fallback without surfacing an error
    - Render the thought within 500 ms in the SideQuest visual style, fully visible and centered without truncation across supported screen sizes
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 17.2 Write property test for deterministic, fail-soft thought selection
    - **Property 23: Thought-of-the-day selection is deterministic by local date and fails soft**
    - **Validates: Requirements 12.2, 12.5**

- [x] 18. Wire the app together and finalize distribution configuration
  - [x] 18.1 Wire views, view models, repositories, and services into the app
    - Compose the app entry point: loading screen → board, with view models bound to repository `ValueObservation` streams and the sync/notification/auth services
    - Register background tasks at launch, request permissions on first use, and reschedule pending notifications on launch
    - Provide functional equivalence with the Android client across capture, buckets, board, completion counter, reminders, and action planning
    - _Requirements: 1.4, 1.6, 6.5, 6.6_

  - [x] 18.2 Implement the distribution configuration and submission-validation check
    - Declare the App ID, the Share Extension as an associated app extension, the shared App Group (identical in both targets), all `BGTaskScheduler` identifiers the app registers, and the entitlements/capabilities for notifications, background processing, and App Group
    - Provide non-empty usage-description strings naming SideQuest and the feature for each runtime-prompted permission
    - Implement a validation function that fails when a usage-description is missing or when an entitlement/App Group/App ID declaration is missing or mismatched, indicating the offending declaration
    - _Requirements: 11.2, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

  - [x] 18.3 Write smoke tests for distribution configuration
    - Assert `Info.plist` declares the Share Extension `NSExtensionActivationRule` for link/text/image/movie; App Group identifier identical across targets; every registered `BGTaskScheduler` identifier matches a declared identifier and none is unregistered; entitlements and non-empty usage-descriptions present
    - _Requirements: 4.1, 11.2, 13.2, 13.3, 13.4, 13.5_

  - [x] 18.4 Write integration tests for backend wiring
    - Exercise `/accounts`, `/auth/login`, `/auth/refresh` against a mocked backend including Keychain token storage; `/sync/push` and `/sync/pull` round trip making data available on a second device; notification delivery within 60 s of the local time and survival across a simulated reboot
    - _Requirements: 2.4, 6.1, 6.7, 7.6, 7.11, 10.1, 10.3, 10.4_

- [x] 19. Final checkpoint - ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (property, unit, integration, and smoke tests) and can be skipped for a faster MVP.
- Each task references specific requirements for traceability; property-test tasks additionally cite the exact Correctness Property from the design.
- Property-based tests use SwiftCheck with a minimum of 100 iterations per property; reused sibling properties are re-implemented against the Swift domain logic and tagged with their sibling reference.
- Checkpoints provide incremental validation at natural integration boundaries.
- Platform/OS integration (Share Extension registration, BGTaskScheduler, notification delivery timing, Keychain) and UI placement are covered by example, integration, and smoke tests rather than property tests.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "3.1", "3.2", "4.1", "4.4", "4.6", "4.9", "4.13", "4.17"] },
    { "id": 3, "tasks": ["3.3", "3.4", "4.2", "4.3", "4.5", "4.7", "4.8", "4.10", "4.11", "4.12", "4.14", "4.15", "4.16", "4.18", "4.19"] },
    { "id": 4, "tasks": ["6.1", "9.1", "13.1", "15.1", "15.3", "17.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "7.1", "8.1", "8.3", "9.2", "13.2", "13.3", "13.4", "13.10", "15.2", "15.4", "16.1", "17.2"] },
    { "id": 6, "tasks": ["7.2", "8.2", "8.4", "8.5", "11.1", "11.3", "12.1", "13.5", "13.6", "13.7", "13.8", "13.11", "16.2", "16.3", "16.4", "16.5", "16.6"] },
    { "id": 7, "tasks": ["11.2", "11.4", "13.9", "16.7", "18.1", "18.2"] },
    { "id": 8, "tasks": ["18.3", "18.4"] }
  ]
}
```
