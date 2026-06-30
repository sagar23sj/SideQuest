# Requirements Document

## Introduction

SideQuest already ships as an Android-first application backed by a standalone Go backend that exposes all functionality over a REST/JSON API defined by an OpenAPI 3 contract (`backend/api/openapi.yaml`). The existing `action-tracker-app` spec was explicitly designed to be "iOS-ready": the backend is reused unchanged, the shared domain logic (offline-first sync with last-writer-wins-and-tombstones) is specified once and validated by the same Correctness Properties on each client, and the design maps an explicit iOS reuse path (SwiftUI + a local store implementing the same contracts; Swift models generated from the same OpenAPI schema).

This document defines the requirements for the **iOS Client**: a native SwiftUI iPhone application that consumes the existing Go backend through the same OpenAPI contract and delivers feature parity with the Android client across capture, buckets, board, completion counter, reminders, and action planning, together with accounts and cross-device sync. The scope of this spec is the iOS client only — it does not change the backend, the OpenAPI contract, or the Android client. Where this document references shared behavior (sync protocol), the requirement is that the iOS client conforms to the already-defined contract and produces results identical to the Android client and the server.

The requirements emphasize the platform-specific concerns that differ from Android: registering as an iOS share target via a Share Extension, an on-device local source of truth, local and push notifications scheduled through the iOS notification system and anchored to local time, iOS notification permission flows, background synchronization via the iOS background task scheduler, and App Store distribution constraints.

## Glossary

- **App** / **SideQuest_iOS**: The native iOS SideQuest application running on an iPhone.
- **Backend**: The existing standalone Go backend that exposes SideQuest functionality over REST/JSON.
- **API_Contract**: The existing OpenAPI 3 document (`backend/api/openapi.yaml`) that defines all client/server requests, responses, and data schemas.
- **Generated_Models**: Swift data types generated from the API_Contract that the App uses for all backend communication and local persistence mapping.
- **Android_Client**: The existing SideQuest Android application, used as the feature-parity reference.
- **User**: A person who installs and uses SideQuest_iOS.
- **Local_Store**: The App's on-device database that serves as the local source of truth for display and editing (offline-first).
- **Share_Extension**: The iOS app extension that registers SideQuest_iOS as a share target so content can be shared into the App from other applications.
- **Shared_Item**: A piece of external content (link, text, image, or video reference) sent to the App through the iOS share sheet.
- **Action_Item**: A tracked task created from a Shared_Item or manual entry, with a bucket, timeframe, and status, as defined by the API_Contract.
- **Bucket**: A user-defined category used to group Action_Items, as defined by the API_Contract.
- **Timeframe**: The user-selected target window for acting on an Action_Item; one of "today", "within a day", "within a week", or a specific calendar date.
- **Action_Status**: The state of an Action_Item; one of "not started", "in progress", or "completed".
- **Board**: The primary view that displays Action_Items grouped by Bucket in ascending order of creation time.
- **Completion_Counter**: A displayed count of Action_Items the User has marked completed.
- **Notification_Service**: The App component that schedules and delivers local notifications through the iOS user-notification system, including Task_Reminders, the Collective_Evening_Nudge, and the Global_Daily_Notification.
- **Task_Reminder**: An optional reminder attached to an Action_Item, consisting of a time of day and an "until" date, optionally recurring daily until the until-date or until the Action_Item is marked completed.
- **Collective_Evening_Nudge**: A single daily notification, delivered in the evening, summarizing Action_Items that are not completed and have no Task_Reminder set.
- **Global_Daily_Notification**: An optional self-reminder notification the User can enable at a chosen time of day to prompt opening SideQuest_iOS.
- **Sync_Service**: The App component that pushes local changes to and pulls remote changes from the Backend using the API_Contract sync endpoints.
- **Background_Scheduler**: The iOS background task mechanism the App uses to run deferred synchronization while the App is not in the foreground.
- **LLM_Proxy**: The Backend endpoint used to generate notification text, action suggestions, and task descriptions.
- **Account**: A User's authenticated identity, as defined by the API_Contract.
- **Correctness_Property**: A portable behavioral property defined in the `action-tracker-app` design that the App's domain logic must satisfy identically to the Android_Client and the Backend.
- **Minimum_iOS_Version**: The lowest iOS major version on which the App is supported.

## Requirements

### Requirement 1: Native iOS Client and Device Scope

**User Story:** As a User, I want a native SideQuest app on my iPhone, so that I can use SideQuest on iOS with the same capabilities as on Android.

#### Acceptance Criteria

1. THE App SHALL run on iPhone devices using the iOS operating system, and SHALL be distributed only to iPhone devices (not iPad or macOS).
2. THE App SHALL declare a Minimum_iOS_Version.
3. WHERE an iPhone runs an iOS version equal to or later than the Minimum_iOS_Version, THE App SHALL run on that iPhone with all User-facing screens available.
4. THE App SHALL present a native SwiftUI user interface for all User-facing screens.
5. IF an iPhone runs an iOS version earlier than the Minimum_iOS_Version, THEN THE App SHALL not be offered for installation on that iPhone.
6. THE App SHALL provide functional equivalence with the Android_Client for each of the following capabilities: capture, buckets, board, completion counter, reminders, and action planning, where functional equivalence means each capability produces equivalent observable outputs for equivalent User inputs.

### Requirement 2: Reuse of the Backend and API Contract

**User Story:** As a developer, I want the iOS client to consume the existing backend through the shared contract, so that both clients share one source of truth and stay behavior-compatible.

#### Acceptance Criteria

1. THE App SHALL communicate with the Backend exclusively over REST/JSON as defined by the API_Contract.
2. THE App SHALL use Generated_Models derived from the API_Contract for all request and response payloads.
3. THE App SHALL NOT require any change to the API_Contract, the Backend, or the Android_Client to operate.
4. WHERE the API_Contract defines an authentication scheme, THE App SHALL authenticate requests using that scheme.
5. IF the Backend returns a structured error response defined by the API_Contract, THEN THE App SHALL map the error to a User-facing message specific to that error category and SHALL preserve any unsaved User input.
6. IF the Backend returns an error response not defined by the API_Contract, or a request does not complete within 30 seconds, THEN THE App SHALL treat the failure as transient and SHALL retry the request up to 3 times.
7. IF authentication of a request fails, THEN THE App SHALL display a User-facing authentication-failure message and SHALL NOT automatically retry the request.

### Requirement 3: Portable Domain Logic and Correctness Properties

**User Story:** As a developer, I want the iOS client's shared logic to behave identically to Android and the server, so that users get consistent results across platforms.

#### Acceptance Criteria

1. THE App SHALL implement the portable domain logic (board aggregation, completion counting, timeframe and due-set resolution, bucket validation, action-plan progress, sync conflict resolution) in Swift.
2. THE App's portable domain logic SHALL satisfy, for every valid input to that logic, each Correctness_Property defined in the `action-tracker-app` design that governs the portable domain logic listed in criterion 1.
3. WHERE a Correctness_Property governs shared logic, THE App SHALL, for each input value also processed by the Backend and the Android_Client, produce an output that is field-by-field identical to both, with identical numeric values and, for ordered outputs (such as bucket grouping), identical element ordering.

### Requirement 4: Capture via iOS Share Extension

**User Story:** As a User, I want to share content from other iOS apps into SideQuest, so that I can turn saved content into tracked tasks.

#### Acceptance Criteria

1. THE App SHALL provide a Share_Extension that registers SideQuest_iOS as a share target for links, text, images, and video references in the iOS share sheet.
2. WHEN a User shares a Shared_Item to the App through the Share_Extension, THE App SHALL receive the Shared_Item and start the categorization flow.
3. WHEN the App receives a Shared_Item, THE App SHALL prompt the User to select exactly one Bucket and exactly one Timeframe before saving, and SHALL prevent saving until both a Bucket and a Timeframe are selected.
4. IF the Share_Extension receives a Shared_Item whose type is not one of links, text, images, or video references, THEN THE App SHALL display a message stating the content type is not supported, SHALL discard the Shared_Item without creating an Action_Item, and SHALL terminate the categorization flow.
5. WHEN a User confirms the selected Bucket and Timeframe for a Shared_Item, THE App SHALL create an Action_Item with Action_Status "not started" in the Local_Store.
6. IF creating the Action_Item in the Local_Store fails, THEN THE App SHALL display an error message indicating the Shared_Item could not be saved, SHALL NOT create a partial Action_Item, and SHALL retain the User's Bucket and Timeframe selections so the User can retry.
7. IF the User cancels the categorization flow before confirming the Bucket and Timeframe, THEN THE App SHALL discard the Shared_Item without creating an Action_Item.
8. WHEN the App receives a Shared_Item that contains a link, THE App SHALL request link preview metadata asynchronously and SHALL allow the User to complete and confirm the capture before the link preview metadata is retrieved.
9. IF link preview metadata is not retrieved within 5 seconds of the request, or the retrieval fails, THEN THE App SHALL save the Action_Item with the raw link and SHALL display the raw link in place of a preview.
10. WHERE the Share_Extension and the main App run in separate iOS processes, THE App SHALL persist captured Action_Items to storage shared between the Share_Extension and the main App so that captured Action_Items appear in the main App.

### Requirement 5: Offline-First Local Persistence

**User Story:** As a User, I want SideQuest to work without a network connection on my iPhone, so that I can capture and review tasks anytime.

#### Acceptance Criteria

1. THE App SHALL maintain a Local_Store on the device that serves as the source of truth for all displayed Action_Items, Buckets, and Action_Plans.
2. THE App SHALL render all Board and detail views exclusively from the Local_Store without requiring a network connection.
3. THE App SHALL allow the User to view, create, edit, and delete Action_Items and Buckets in the Local_Store regardless of whether a network connection is available.
4. THE App SHALL persist Action_Items, Buckets, and Action_Plans with no data loss across App restarts and device reboots.
5. WHEN a User creates, edits, or deletes an entity, THE App SHALL commit the change durably to the Local_Store before reporting the operation complete.
6. WHEN a User creates, edits, or deletes an entity, THE App SHALL mark the change for synchronization, and SHALL retain that pending-synchronization state until the change is confirmed synchronized with the Backend.
7. THE App SHALL generate client-side identifiers for new entities that are unique across all devices and the Backend so that records can be created without a network round-trip and without sync collisions.
8. IF committing a change to the Local_Store fails, THEN THE App SHALL preserve the prior persisted state, SHALL retain the User's input, and SHALL display an error indication that the change was not saved.

### Requirement 6: Background Synchronization

**User Story:** As a User, I want my SideQuest data to sync across my devices, so that what I capture on my iPhone is available everywhere.

#### Acceptance Criteria

1. THE Sync_Service SHALL push local changes to and pull remote changes from the Backend using the sync endpoints defined by the API_Contract.
2. THE Sync_Service SHALL resolve conflicts using deterministic last-writer-wins keyed on the record update time, and WHERE two conflicting records share the same update time THE Sync_Service SHALL break the tie deterministically by record identifier.
3. THE Sync_Service SHALL propagate deletes via tombstones consistent with the API_Contract sync protocol so that deletions reach all of the User's devices.
4. WHEN connectivity is restored after being unavailable, THE Sync_Service SHALL synchronize the local changes marked for synchronization in the Local_Store.
5. THE App SHALL register a Background_Scheduler task so that synchronization runs while the App is not in the foreground.
6. WHEN the App returns to the foreground, THE Sync_Service SHALL perform a synchronization pass.
7. WHEN a User signs in on the App for the first time on a device, THE App SHALL pull the User's Action_Items, Buckets, and Action_Plans from the Backend into the Local_Store.
8. THE Sync_Service SHALL make synchronization pushes idempotent keyed on the client-generated entity identifier so that retried pushes do not create duplicate records.
9. IF a synchronization push or pull fails, THEN THE Sync_Service SHALL retain the unsynchronized changes, SHALL retry up to a configured maximum number of attempts, and SHALL preserve the Local_Store state on total failure.
10. IF the first sign-in pull fails, THEN THE App SHALL avoid a partial import, SHALL display a message that data could not be retrieved, and SHALL retry on the next synchronization pass.

### Requirement 7: Notifications and Reminders

**User Story:** As a User, I want SideQuest to remind me about my tasks on my iPhone, so that I follow through on what I planned.

#### Acceptance Criteria

1. WHEN a User first uses a feature that requires notifications, THE App SHALL request iOS permission to send notifications.
2. WHEN a User creates or edits an Action_Item, THE App SHALL allow the User to optionally attach a Task_Reminder consisting of a reminder time of day (hour and minute in the device's local time zone) and an "until" date.
3. WHERE a User attaches a Task_Reminder, THE App SHALL allow the User to choose whether the reminder recurs daily until the until-date or fires only once.
4. IF a User sets a Task_Reminder until-date earlier than the current date or more than 365 days after the current date, THEN THE App SHALL reject the selection, SHALL retain the User's other Task_Reminder values, and SHALL display a message requesting a date between the current date and 365 days ahead.
5. IF a User attempts to save a Task_Reminder without a reminder time of day, THEN THE App SHALL reject the Task_Reminder and SHALL display a message requesting a reminder time.
6. WHILE an Action_Item has an active Task_Reminder and is not completed, THE Notification_Service SHALL deliver a reminder for that Action_Item within 60 seconds of its reminder time.
7. WHERE a Task_Reminder is recurring, THE Notification_Service SHALL deliver the reminder at the reminder time on each day up to and including the until-date, until the Action_Item is marked completed.
8. WHEN an Action_Item with a Task_Reminder is marked completed, THE App SHALL cancel any pending reminders for that Action_Item.
9. WHEN the until-date of a Task_Reminder passes, THE Notification_Service SHALL stop delivering reminders for that Action_Item.
10. THE Notification_Service SHALL anchor each scheduled notification to the device's local time zone so that the notification fires at the intended wall-clock time when the device time zone changes.
11. WHEN the device restarts, THE App SHALL ensure pending notifications remain scheduled so that no reminder is lost across a reboot.
12. THE App SHALL allow the User to enable or disable the Collective_Evening_Nudge and to set its time of day as an hour and minute in the device's local time zone.
13. WHILE the Collective_Evening_Nudge is enabled, THE Notification_Service SHALL deliver one notification within 60 seconds of the configured evening time summarizing up to 20 Action_Items that are not completed and have no Task_Reminder set, and SHALL exclude Action_Items that have a Task_Reminder set.
14. IF the Collective_Evening_Nudge is enabled and there are no eligible Action_Items on a given day, THEN THE Notification_Service SHALL omit the nudge for that day.
15. THE App SHALL allow the User to enable or disable a Global_Daily_Notification and to set its time of day as an hour and minute in the device's local time zone, and WHILE enabled THE Notification_Service SHALL deliver one notification within 60 seconds of the configured time prompting the User to open SideQuest_iOS.
16. WHEN the Notification_Service prepares a Task_Reminder or a Collective_Evening_Nudge, THE App SHALL request notification text of at most 200 characters from the LLM_Proxy.
17. IF the LLM_Proxy does not return notification text within 5 seconds or returns an error, THEN THE Notification_Service SHALL deliver the notification using default text.
18. IF notification permission is denied, THEN THE App SHALL display a message explaining that reminders are unavailable and SHALL provide a link to the iOS notification settings.

### Requirement 8: Action Board, Completion Counter, and Status

**User Story:** As a User, I want to see and manage my action items on a board on my iPhone, so that I can review and complete what I plan to act on.

#### Acceptance Criteria

1. THE Board SHALL display Action_Items grouped by Bucket, and within each Bucket SHALL display Action_Items in ascending order of creation timestamp, and WHERE two Action_Items share the same creation timestamp THE Board SHALL order them by ascending unique identifier.
2. THE Board SHALL display each Action_Item with a color indicator, and SHALL map each distinct Action_Status value to a distinct color such that no two Action_Status values share the same color.
3. WHEN the User changes the Action_Status of an Action_Item and the change is persisted successfully, THE Board SHALL update the displayed color indicator to match the new Action_Status within 500 milliseconds.
4. IF the User changes the Action_Status of an Action_Item and the change fails to persist, THEN THE App SHALL retain the prior Action_Status, SHALL leave the displayed color indicator unchanged, and SHALL display an error indication informing the User that the status update did not save.
5. THE App SHALL display a Completion_Counter at the top of the Board that reflects the total number of Action_Items with Action_Status "completed", and the Completion_Counter value SHALL never be less than zero.
6. WHEN a User marks an Action_Item as "completed", THE App SHALL increase the Completion_Counter by one, and WHEN a User changes an Action_Item from "completed" to a non-completed Action_Status, THE App SHALL decrease the Completion_Counter by one.
7. THE App SHALL allow the User to mark an Action_Item completed using a press-and-hold control, and WHILE the press-and-hold is sustained THE App SHALL display a progressive fill animation whose filled proportion equals the elapsed hold time divided by 800 milliseconds, completing only after the hold is sustained continuously for 800 milliseconds.
8. WHEN the press-and-hold is sustained continuously for 800 milliseconds, THE App SHALL set the Action_Item Action_Status to "completed", SHALL provide haptic feedback, and SHALL play a celebration animation lasting no longer than 2000 milliseconds.
9. IF the User releases the press-and-hold control before 800 milliseconds elapse, THEN THE App SHALL cancel the gesture, SHALL reset the progressive fill animation to empty, and SHALL leave the Action_Status unchanged.

### Requirement 9: Buckets, Timeframes, and Action Planning

**User Story:** As a User, I want to organize tasks into buckets, set timeframes, and break tasks into steps on my iPhone, so that I can plan how to follow through.

#### Acceptance Criteria

1. THE App SHALL allow the User to create, rename, and delete a Bucket.
2. IF a User attempts to create or rename a Bucket to a name that, after trimming surrounding whitespace and ignoring letter case, matches an existing Bucket name for the Account, THEN THE App SHALL reject the operation, SHALL leave existing Buckets unchanged, and SHALL display a message that the name is in use.
3. IF a User attempts to create or rename a Bucket with a name that is empty, contains only whitespace, or exceeds 50 characters, THEN THE App SHALL reject the operation and SHALL display a message that the name must be 1 to 50 characters.
4. WHEN a User deletes a Bucket that contains Action_Items and at least one other Bucket exists, THE App SHALL prompt the User to choose between reassigning the contained Action_Items to another Bucket or deleting them, and SHALL require the User to confirm the choice before applying it.
5. WHEN a User deletes a Bucket that contains Action_Items and no other Bucket exists, THE App SHALL prompt the User to confirm deleting the contained Action_Items before applying the deletion.
6. WHEN prompting for a Timeframe, THE App SHALL offer the options "today", "within a day", "within a week", and "specific date".
7. IF the User selects a specific date earlier than the current date in the device's local time zone, THEN THE App SHALL reject the selection and SHALL display a message requesting a current or future date.
8. THE App SHALL allow the User to add an Action_Plan with 1 to 100 ordered sub-actions to an Action_Item, to mark an individual sub-action completed, and to reorder sub-actions while preserving the relative sequence of the sub-actions not moved.
9. THE App SHALL display the count of completed sub-actions relative to the total sub-actions for an Action_Item.
10. WHEN all sub-actions of an Action_Item are marked completed, THE App SHALL prompt the User to mark the Action_Item as "completed".

### Requirement 10: Accounts and Cross-Device Sync

**User Story:** As a User, I want to sign in to my SideQuest account on my iPhone, so that my data is tied to my identity and synced across my devices.

#### Acceptance Criteria

1. WHEN a User submits Account registration inputs that satisfy the API_Contract validation rules, THE App SHALL create the Account using the API_Contract within 10 seconds under normal network conditions.
2. WHILE a User is signed in, THE App SHALL associate the User's Action_Items, Buckets, and Action_Plans with the User's Account.
3. WHEN a User signs in on a new device and network connectivity is available, THE App SHALL make the User's Action_Items, Buckets, and Action_Plans available on that device within 30 seconds.
4. THE App SHALL store authentication tokens in the iOS Keychain.
5. WHEN an access token expires, THE App SHALL refresh the token using the iOS Keychain-stored credentials without modifying or deleting any data in the Local_Store.
6. IF Account creation fails, THEN THE App SHALL retain the User's entered registration inputs and display an error message indicating the failure reason.
7. IF token refresh fails, THEN THE App SHALL route the User to re-authentication while preserving the Local_Store unchanged.
8. IF a User submits sign-in credentials that the API_Contract does not accept, THEN THE App SHALL reject the sign-in attempt and display an error message indicating the credentials were not accepted.

### Requirement 11: Permission Flows

**User Story:** As a User, I want clear control over the permissions SideQuest requests on my iPhone, so that I understand and trust what the app accesses.

#### Acceptance Criteria

1. WHEN a notification feature is first used and iOS notification permission has not previously been requested, THE App SHALL trigger the iOS system notification permission prompt exactly once.
2. WHERE iOS requires a usage-description string for a requested permission, THE App SHALL provide a non-empty usage-description string that names SideQuest and identifies the specific feature requiring the permission.
3. IF a required permission is denied, THEN THE App SHALL keep every feature that does not depend on that permission fully operable.
4. IF a required permission is denied, THEN THE App SHALL display a user-activatable control that opens the SideQuest entry in the iOS Settings app.
5. IF iOS permission for a given capability has already been granted or denied, THEN THE App SHALL NOT trigger the iOS system permission prompt for that capability again.

### Requirement 12: Loading Experience

**User Story:** As a User, I want SideQuest to greet me with an uplifting thought while it loads, so that opening the app feels encouraging.

#### Acceptance Criteria

1. WHILE SideQuest_iOS is loading at launch, THE App SHALL display a "thought of the day" — a motivational message of 1 to 280 characters centered on accomplishment and well-being — within 500 milliseconds of loading start.
2. THE App SHALL select the thought of the day deterministically based on the device's local calendar date, such that the same thought is displayed for every launch occurring on the same local calendar date and a different thought is selected at the next local calendar date boundary.
3. THE App SHALL include a built-in set of at least 30 thoughts stored on the device, such that the loading experience displays a thought of the day with no network connection available.
4. WHILE SideQuest_iOS is loading at launch, THE App SHALL render the thought of the day using the SideQuest visual style — the same typography, color palette, and layout conventions used elsewhere in the App — with the message text fully visible, centered, and without truncation across all supported iOS device screen sizes.
5. IF the deterministically selected thought of the day cannot be retrieved from the built-in set, THEN THE App SHALL display a default fallback thought and complete the loading experience without surfacing an error indication to the User.

### Requirement 13: App Store Distribution and Signing

**User Story:** As a User, I want to install SideQuest from the App Store, so that I can get and update the app through the standard iOS channel.

#### Acceptance Criteria

1. THE App SHALL be packaged as a code-signed build using an Apple App Store distribution signing identity and a provisioning profile valid for App Store submission.
2. THE App SHALL declare an App ID, the Share_Extension as an associated app extension, and the App Group used to share storage between the Share_Extension and the main App, where the App Group identifier declared in the Share_Extension is identical to the one declared in the main App.
3. THE App SHALL declare in the App configuration every background-task identifier that the Background_Scheduler registers, such that each identifier the Background_Scheduler registers matches exactly one declared identifier and no declared identifier is left unregistered.
4. THE App SHALL declare the entitlements and capabilities required for notifications, background processing, and the App Group.
5. WHERE the App requests at runtime a permission governed by App Store review, THE App SHALL include a non-empty usage-description string corresponding to that permission.
6. IF the App configuration omits a usage-description string for a permission the App requests at runtime, THEN THE App SHALL fail App Store submission validation and SHALL not be distributed, with the submission result indicating the missing usage-description.
7. IF a required entitlement, capability, App Group, or App ID declaration is missing or does not match the distribution provisioning profile, THEN THE App SHALL fail App Store submission validation and SHALL not be distributed, with the submission result indicating the mismatched or missing declaration.
