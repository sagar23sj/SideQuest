# Requirements Document

## Introduction

SideQuest is a mobile application (primary target Android, with iOS as a desired secondary platform) that helps people convert content they save while browsing social media into tracked, actionable tasks. Today users save interesting reels, videos, articles, and suggestions to scattered places (saved collections, self-chats) and rarely revisit them. SideQuest provides a "task board" experience: shared content is captured, categorized into user-defined buckets (such as travel, cooking, stocks, shopping), assigned an action timeframe, and surfaced through reminders so users actually follow through and feel a sense of accomplishment.

Reminders in SideQuest are primarily **task-based**: when the User adds a task they can attach a reminder time and an "until" date, optionally recurring daily until that date or until the task is marked done. Tasks with no reminder are gathered into a single collective evening nudge. A separate optional global daily notification lets the User nudge themselves to open the app at a chosen time. Completing a task is a deliberate in-app gesture (press-and-hold) so that following through brings the User back into SideQuest to discover what else is there.

The product also includes two additional capabilities the user wants in the same app:
1. **Voice Journaling** - users speak into the app, receive transcripts, and have actionable items extracted and filed under relevant buckets.
2. **Memory Games** - integrated games (Spelling Bee, Wordle-style) playable daily within an organization, with leaderboards across day, week, and month that reset monthly to encourage consistency and engagement.

This document defines the requirements for these capabilities using EARS patterns and INCOSE quality rules.

## Glossary

- **App** / **SideQuest**: The SideQuest mobile application running on a user device.
- **User**: A person who installs and uses SideQuest.
- **Capture_Service**: The component that receives shared content from external applications via the OS share mechanism.
- **Shared_Item**: A piece of external content (link, video reference, image, or text) sent to the App through the OS share sheet.
- **Link_Preview**: Metadata fetched from a shared link, including a title, a thumbnail image, and a source name.
- **Preview_Service**: The App component that retrieves Link_Preview metadata for a shared link.
- **Action_Item**: A tracked task created from a Shared_Item, voice journal entry, or manual entry, with a bucket, timeframe, and status.
- **Bucket**: A user-defined category (for example travel, cooking, stocks, shopping/wishlist) used to group Action_Items. A "wishlist" is simply a Bucket the User named for things they want to buy; it is not a special type.
- **Timeframe**: The user-selected target window for acting on an Action_Item; one of "today", "within a day", "within a week", or a specific calendar date.
- **Action_Status**: The state of an Action_Item; one of "not started", "in progress", or "completed".
- **Board**: The primary view that displays Action_Items grouped by Bucket in order of creation.
- **Completion_Counter**: A displayed count of Action_Items the User has marked completed.
- **Notification_Service**: The component that schedules and delivers reminders, including per-task reminders, the collective evening nudge, and the optional global daily notification.
- **Task_Reminder**: An optional reminder attached to an Action_Item, consisting of a time of day and an "until" date, optionally recurring daily until the until-date or until the Action_Item is marked completed (whichever comes first).
- **Collective_Evening_Nudge**: A single daily notification, delivered in the evening, that summarizes Action_Items which are not completed and have no Task_Reminder set.
- **Global_Daily_Notification**: An optional self-reminder notification the User can enable at a chosen time of day to prompt themselves to open SideQuest; it is an ordinary notification, not an exact alarm.
- **LLM_Service**: An external large language model service used to generate notification text, action suggestions, task descriptions, and to extract actions from transcripts.
- **Action_Plan**: An ordered set of sub-actions (steps) attached to an Action_Item to accomplish a larger task.
- **Voice_Journal_Entry**: An audio recording captured in the App together with its generated transcript.
- **Transcription_Service**: The App component or external service that converts recorded audio into text.
- **Game**: An in-app memory game; initially a Spelling_Bee game and a Word_Guess game.
- **Spelling_Bee**: A game where the User forms words from a fixed set of letters.
- **Word_Guess**: A Wordle-style game where the User guesses a hidden word within a limited number of attempts.
- **Organization**: A group of Users (for example a company) whose game scores are compared on shared leaderboards.
- **Leaderboard**: A ranked list of Users by game score for a given period (day, week, or month).
- **Account**: A User's authenticated identity within the App.

## Requirements

### Requirement 1: Capture Shared Content

**User Story:** As a User, I want to share content from other apps into the App, so that I can turn saved content into tracked tasks instead of losing it.

#### Acceptance Criteria

1. THE App SHALL register as a share target in the operating system share sheet for links, text, images, and video references.
2. WHEN a User shares a Shared_Item to the App, THE Capture_Service SHALL receive the Shared_Item and start the categorization flow.
3. WHEN the Capture_Service receives a Shared_Item, THE App SHALL prompt the User to select a Bucket and a Timeframe before saving.
4. IF the Capture_Service receives a Shared_Item in an unsupported format, THEN THE App SHALL display a message stating the content type is not supported and SHALL discard the Shared_Item.
5. WHEN a User confirms the Bucket and Timeframe for a Shared_Item, THE App SHALL create an Action_Item with Action_Status "not started".

### Requirement 1a: Fetch Link Previews

**User Story:** As a User, I want shared links to show a title and thumbnail, so that I can recognize saved content at a glance without opening it.

#### Acceptance Criteria

1. WHEN the Capture_Service receives a Shared_Item that contains a link, THE Preview_Service SHALL request Link_Preview metadata for the link.
2. WHEN the Preview_Service retrieves Link_Preview metadata, THE App SHALL store the title, thumbnail image, and source name with the resulting Action_Item.
3. THE Board SHALL display the Link_Preview title and thumbnail for each Action_Item created from a link.
4. IF the Preview_Service cannot retrieve Link_Preview metadata, THEN THE App SHALL store the raw link and SHALL display the raw link in place of a preview.
5. IF the Preview_Service does not respond within the configured timeout, THEN THE App SHALL save the Action_Item with the raw link without blocking the capture flow.

### Requirement 2: Manage Buckets

**User Story:** As a User, I want to create and manage buckets, so that I can organize my action items by topic.

#### Acceptance Criteria

1. THE App SHALL allow the User to create a Bucket with a User-provided name.
2. WHEN a User creates a Bucket, THE App SHALL store the Bucket and make it available for selection during capture.
3. THE App SHALL allow the User to rename an existing Bucket.
4. THE App SHALL allow the User to delete a Bucket.
5. WHEN a User deletes a Bucket that contains Action_Items, THE App SHALL prompt the User to choose between reassigning the contained Action_Items to another Bucket or deleting them.
6. IF a User attempts to create a Bucket with a name that already exists, THEN THE App SHALL reject the creation and SHALL display a message that the name is in use.

### Requirement 3: Assign Action Timeframe

**User Story:** As a User, I want to set when I plan to act on an item, so that the App can remind me at the right time.

#### Acceptance Criteria

1. WHEN prompting for a Timeframe, THE App SHALL offer the options "today", "within a day", "within a week", and "specific date".
2. WHERE the User selects "specific date", THE App SHALL allow the User to pick a calendar date that is the current date or later.
3. IF the User selects a specific date earlier than the current date, THEN THE App SHALL reject the selection and SHALL display a message requesting a current or future date.
4. WHEN a Timeframe is assigned to an Action_Item, THE App SHALL store the Timeframe with the Action_Item.

### Requirement 4: Display the Action Board

**User Story:** As a User, I want to see my action items organized on a board, so that I can review what I want to act on.

#### Acceptance Criteria

1. THE Board SHALL display Action_Items grouped by Bucket.
2. WITHIN each Bucket, THE Board SHALL display Action_Items in ascending order of creation time.
3. THE Board SHALL display each Action_Item with a color indicator that represents its Action_Status.
4. WHERE an Action_Item has Action_Status "not started", THE Board SHALL display the indicator color assigned to "not started".
5. WHERE an Action_Item has Action_Status "completed", THE Board SHALL display the indicator color assigned to "completed".
6. THE App SHALL allow the User to change the Action_Status of an Action_Item.
7. WHEN a User changes the Action_Status of an Action_Item, THE Board SHALL update the displayed color indicator to match the new Action_Status.

### Requirement 5: Track Completion Count

**User Story:** As a User, I want to see how many actions I have completed, so that I feel a sense of progress and accomplishment.

#### Acceptance Criteria

1. THE App SHALL display a Completion_Counter at the top of the Board.
2. WHEN a User marks an Action_Item as "completed", THE App SHALL increase the Completion_Counter by one.
3. WHEN a User changes an Action_Item from "completed" to a non-completed Action_Status, THE App SHALL decrease the Completion_Counter by one.
4. THE Completion_Counter SHALL reflect the total number of Action_Items with Action_Status "completed".

### Requirement 6: Task-Based Reminders

**User Story:** As a User, I want to attach a reminder to a specific task with its own time and an end date, so that SideQuest reminds me about that task when I actually plan to do it instead of with a single generic daily alert.

#### Acceptance Criteria

1. WHEN a User first launches SideQuest, THE App SHALL request operating system permission to send notifications.
2. WHEN a User creates or edits an Action_Item, THE App SHALL allow the User to optionally attach a Task_Reminder consisting of a reminder time of day and an "until" date.
3. WHERE a User attaches a Task_Reminder, THE App SHALL allow the User to choose whether the reminder recurs daily until the until-date or fires only once.
4. IF a User sets a Task_Reminder until-date earlier than the current date, THEN THE App SHALL reject the selection and SHALL display a message requesting a current or future date.
5. WHILE an Action_Item has an active Task_Reminder and is not completed, THE Notification_Service SHALL deliver a reminder for that Action_Item at its reminder time.
6. WHERE a Task_Reminder is recurring, THE Notification_Service SHALL deliver the reminder at the reminder time on each day up to and including the until-date, until the Action_Item is marked completed.
7. WHEN an Action_Item with a Task_Reminder is marked completed, THE App SHALL cancel any pending reminders for that Action_Item.
8. WHEN the until-date of a Task_Reminder passes, THE App SHALL stop delivering reminders for that Action_Item.
9. THE Notification_Service SHALL anchor each Task_Reminder to the device's local time zone so that the reminder fires at the intended wall-clock time when the device time zone changes.
10. WHEN the device restarts, THE App SHALL reschedule all pending Task_Reminders so that no reminder is lost across a reboot.
11. WHERE the operating system requires permission to schedule exact-time alarms, THE App SHALL request that permission so that Task_Reminders fire at their exact times.
12. IF notification permission is denied, THEN THE App SHALL display a message explaining that reminders are unavailable and SHALL provide a link to the operating system notification settings.

### Requirement 6a: Collective Evening Nudge

**User Story:** As a User, I want one evening summary of tasks I have not scheduled, so that untimed tasks are not forgotten without my having to set a reminder on each one.

#### Acceptance Criteria

1. THE App SHALL allow the User to enable or disable the Collective_Evening_Nudge and to set its time of day.
2. WHILE the Collective_Evening_Nudge is enabled, THE Notification_Service SHALL deliver one notification at the configured evening time summarizing Action_Items that are not completed and have no Task_Reminder set.
3. WHERE an Action_Item has a Task_Reminder set, THE Notification_Service SHALL exclude that Action_Item from the Collective_Evening_Nudge.
4. IF the Collective_Evening_Nudge is enabled and there are no eligible Action_Items on a given day, THEN THE Notification_Service SHALL NOT deliver an empty nudge that day.
5. WHEN a User opens the Collective_Evening_Nudge, THE App SHALL open SideQuest so the User can review and update the pending Action_Items.

### Requirement 6b: Global Daily Notification

**User Story:** As a User, I want an optional daily nudge to open SideQuest at a time I choose, so that I build a habit of checking in.

#### Acceptance Criteria

1. THE App SHALL allow the User to enable or disable a Global_Daily_Notification and to set its time of day.
2. WHILE the Global_Daily_Notification is enabled, THE Notification_Service SHALL deliver one notification at the configured time prompting the User to open SideQuest.
3. THE Global_Daily_Notification SHALL be delivered as an ordinary notification and SHALL NOT require exact-alarm scheduling.

### Requirement 6c: Mark a Task Done

**User Story:** As a User, I want completing a task to be a deliberate, satisfying gesture inside the app, so that following through feels rewarding and brings me back into SideQuest.

#### Acceptance Criteria

1. THE App SHALL allow the User to mark an Action_Item completed using a press-and-hold control rather than a checkbox.
2. WHILE the User holds the control, THE App SHALL display a progressive fill animation and SHALL complete the action only after the hold is sustained for the configured duration.
3. WHEN the press-and-hold completes, THE App SHALL set the Action_Item Action_Status to "completed", SHALL provide haptic feedback, and SHALL play a brief confetti celebration animation.
4. IF the User releases the control before the hold duration elapses, THEN THE App SHALL cancel the gesture and SHALL leave the Action_Status unchanged.
5. THE App SHALL NOT require the User to set "in progress" in the UI; the in-app interaction offers only "mark completed", while the Action_Status field SHALL still be stored so completed Action_Items can be listed with the Bucket they belonged to.

### Requirement 6d: Thought of the Day Loading Experience

**User Story:** As a User, I want the app to greet me with an uplifting thought while it loads, so that opening SideQuest feels encouraging and reinforces doing over consuming.

#### Acceptance Criteria

1. WHILE SideQuest is loading at launch, THE App SHALL display a "thought of the day" — a short motivational message centered on accomplishment, doing, and well-being rather than passive consumption.
2. THE App SHALL select the thought of the day deterministically per calendar day so the message is consistent across launches on the same day and changes the next day.
3. THE App SHALL provide a built-in set of thoughts so the loading experience works fully offline.
4. THE App SHALL render the loading experience in the SideQuest visual style (brand typography, color, and motion) consistent with the rest of the app.

### Requirement 7: LLM-Curated Notifications and Suggestions

**User Story:** As a User, I want reminders and action suggestions tailored to my tasks, so that the notifications are helpful rather than generic.

#### Acceptance Criteria

1. WHEN the Notification_Service prepares a Task_Reminder or a Collective_Evening_Nudge, THE App SHALL request notification text from the LLM_Service based on the relevant Action_Items.
2. WHEN a User opens an Action_Item, THE App SHALL allow the User to request suggested actions for the Action_Item from the LLM_Service.
3. WHEN the App requests a task description for an Action_Item, THE LLM_Service SHALL generate a description summarizing the Shared_Item content.
4. IF the LLM_Service is unavailable or returns an error, THEN THE Notification_Service SHALL deliver the notification using default text without LLM content.
5. IF the LLM_Service does not respond within the configured timeout, THEN THE App SHALL proceed without LLM content and SHALL inform the User that suggestions are unavailable.

### Requirement 8: Wishlist Buckets

**User Story:** As a User, I want to collect products I want to buy under one bucket, so that I have a single wishlist instead of separate carts in many apps.

#### Acceptance Criteria

1. THE App SHALL allow the User to create a Bucket (for example named "Wishlist") and capture products into it as ordinary Action_Items, without any special item type.
2. THE App SHALL allow the User to mark a wishlist Action_Item as "completed" (for example once purchased) using the same Action_Status mechanism as any other Action_Item.

### Requirement 9: Action Planning

**User Story:** As a User, I want to break a task into a set of steps, so that I can plan how to make it happen.

#### Acceptance Criteria

1. THE App SHALL allow the User to add an Action_Plan with one or more ordered sub-actions to an Action_Item.
2. THE App SHALL allow the User to mark an individual sub-action as completed.
3. THE App SHALL display the count of completed sub-actions relative to the total sub-actions for an Action_Item.
4. WHEN all sub-actions of an Action_Item are marked completed, THE App SHALL prompt the User to mark the Action_Item as "completed".
5. THE App SHALL allow the User to reorder sub-actions within an Action_Plan.

### Requirement 10: Voice Journaling

**User Story:** As a User, I want to speak into the app and have it capture my thoughts and extract action items, so that I can quickly record ideas hands-free.

#### Acceptance Criteria

1. WHEN a User starts a voice journal recording, THE App SHALL request operating system permission to access the microphone if permission has not been granted.
2. WHILE a recording is in progress, THE App SHALL capture audio until the User stops the recording.
3. WHEN a User stops a recording, THE Transcription_Service SHALL convert the recorded audio into a text transcript.
4. WHEN a transcript is generated, THE App SHALL store the Voice_Journal_Entry containing the audio reference and the transcript.
5. WHEN a transcript is generated, THE App SHALL request the LLM_Service to extract actionable items from the transcript.
6. WHEN the LLM_Service returns extracted actionable items, THE App SHALL present the items to the User for confirmation before creating Action_Items.
7. WHEN a User confirms an extracted actionable item, THE App SHALL create an Action_Item and SHALL prompt the User to assign a Bucket and Timeframe.
8. IF the Transcription_Service fails to produce a transcript, THEN THE App SHALL retain the audio recording and SHALL display a message that transcription failed.

### Requirement 11: Memory Games

**User Story:** As a User, I want to play memory games in the app, so that I can engage my brain and build a daily habit.

#### Acceptance Criteria

1. THE App SHALL provide a Spelling_Bee Game and a Word_Guess Game.
2. THE App SHALL present one daily puzzle per Game that is identical for all Users within an Organization on the same calendar day.
3. WHEN a User completes a daily Game, THE App SHALL record the User's score for that Game and day.
4. IF a User attempts to replay a daily Game that the User has already completed on the same day, THEN THE App SHALL prevent a second scoring attempt and SHALL display the User's recorded result.
5. WHEN a User submits a guess in the Word_Guess Game, THE App SHALL indicate which letters are correct, which are present in another position, and which are absent.
6. WHEN a User submits a word in the Spelling_Bee Game, THE App SHALL accept the word only if the word uses the allowed letters and exists in the App word list.

### Requirement 12: Game Leaderboards

**User Story:** As a User, I want to see daily, weekly, and monthly leaderboards within my organization, so that I stay motivated through friendly competition.

#### Acceptance Criteria

1. THE App SHALL display a daily Leaderboard, a weekly Leaderboard, and a monthly Leaderboard for each Organization.
2. WHEN a User's Game score is recorded, THE App SHALL update the daily, weekly, and monthly Leaderboards for the User's Organization.
3. THE App SHALL rank Users on each Leaderboard in descending order of total score for the corresponding period.
4. WHEN a new calendar month begins, THE App SHALL reset the monthly Leaderboard so that the new month starts with no recorded scores.
5. WHEN a new calendar day begins, THE App SHALL start a new daily Leaderboard for that day.
6. WHEN a new calendar week begins, THE App SHALL start a new weekly Leaderboard for that week.

### Requirement 13: Accounts and Organization Membership

**User Story:** As a User, I want an account associated with my organization, so that my data is saved and my game scores appear on the right leaderboards.

#### Acceptance Criteria

1. THE App SHALL allow a User to create an Account.
2. WHEN a User creates an Account, THE App SHALL allow the User to join an Organization.
3. WHILE a User is signed in, THE App SHALL associate the User's Action_Items, Buckets, Voice_Journal_Entries, and Game scores with the User's Account.
4. WHEN a User signs in on a new device, THE App SHALL make the User's Action_Items, Buckets, and Voice_Journal_Entries available on that device.
5. IF a User who is not a member of any Organization opens a Leaderboard, THEN THE App SHALL display a message prompting the User to join an Organization.

### Requirement 14: Data Persistence and Cross-Platform Support

**User Story:** As a User, I want the App to work on my phone and keep my data safe, so that I can rely on it over time.

#### Acceptance Criteria

1. THE App SHALL run on the Android operating system.
2. THE App SHALL persist Action_Items, Buckets, Action_Plans, and Voice_Journal_Entries across App restarts.
3. WHEN a User edits or deletes an Action_Item, THE App SHALL persist the change.
4. IF a network connection is unavailable, THEN THE App SHALL allow the User to view and edit locally stored Action_Items and SHALL synchronize changes when a connection is restored.
