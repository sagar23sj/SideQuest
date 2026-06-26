# SideQuest (Action Tracker) — Refined UI/UX Design Spec

> Handoff document for design → Jetpack Compose implementation.
> Source of truth: the 15 Stitch screens in `design/stitch/` + the product UX
> spec. This document audits what exists, defines the design system extracted
> from the Stitch designs, lays out the navigation shell and end-to-end flow,
> and proposes targeted divergences where they improve the product.

---

## 1. Product

A personal "quest board" that turns content saved while browsing (reels,
articles, links, products) into tracked, actionable items, organized into
user-defined buckets and surfaced through daily reminders. Plus voice
journaling (speak → transcript → extracted actions) and daily memory games
with organization leaderboards.

Brand voice: **SideQuest** — encouraging, lightly gamified, never childish.
Accomplishment is the emotional core (completion counter, streaks, leaderboards).

---

## 2. Implementation status (updated after this pass)

Screens live under `app/src/main/kotlin/com/actiontracker/ui/`. The theme,
navigation shell, and all previously-missing screens are now built.

| Stitch screen | Compose status |
| --- | --- |
| Share-capture (01) | Built (`capture/CategorizationSheet.kt`) — now also reachable via in-app FAB |
| Home Board (04) | Built — restyled by theme, opens item detail + bucket mgmt |
| Action Plan (09) | Built — back nav wired |
| Voice Journal (03) | Built — opens review after save |
| Reminder Settings | Built |
| Voice Journal Review (06) | **Built** (`voice/VoiceReviewScreen.kt`, wired to `VoiceJournalRepository`) |
| Bucket Management (05) | **Built** (`bucket/BucketManagementScreen.kt`, wired to `BucketRepository`) |
| Create Bucket (08) | **Built** (`bucket/CreateBucketScreen.kt`, incl. shopping toggle) |
| Shopping Item Detail (07) | Toggle + strings in place; inline detail fields pending the `Bucket` shopping flag |
| Games Hub (02) | **Built** (`games/GamesHubScreen.kt`) |
| Spelling Bee (10) | **Built** (`games/SpellingBeeScreen.kt`, local puzzle stand-in) |
| Word Guess (11) | **Built** (`games/WordGuessScreen.kt`, local puzzle stand-in) |
| Leaderboard (12) | **Built** (`leaderboard/LeaderboardScreen.kt`, 3 tabs + no-org prompt) |
| Join Organization (13) | **Built** (`auth/JoinOrganizationScreen.kt`) |
| Login (14) | **Built** (`auth/LoginScreen.kt`) |
| User Profile (15) | **Built** (`profile/ProfileScreen.kt`) |

Navigation: a real Navigation-Compose graph ([ActionTrackerNavHost]) with a
four-tab bottom shell (Board / Games / Voice / Profile) + center capture FAB
replaces the old two-state `RootScreen` enum.

### Backend-wiring TODOs (UI complete, data layer pending)
- **Games / Leaderboard**: no client repository yet (logic is Go-backend only).
  Screens render from local stand-ins; feed them from a games/leaderboard
  repository when added, and replace the local word/score checks.
- **Auth / Org / Profile**: `AuthApi` + token storage exist in the data layer;
  screens are presentational. Hook submit/join to an auth view model.
- **Shopping bucket flag**: the create-bucket toggle is captured but not
  persisted — needs an `isShoppingBucket` field on the `Bucket` model + Room
  entity before the inline product/source/purchased fields can render.
- **Fonts**: Outfit/Inter are referenced via [BrandFontFamily]; drop the TTFs
  into `res/font/` and point the families at them to switch from the system
  sans-serif stand-in.

## 2b. Original implementation status (pre-pass, for reference)

Screens live under `app/src/main/kotlin/com/actiontracker/ui/`. Backend logic
for games, leaderboards, sync, and accounts exists per `tasks.md`.

| Stitch screen | Compose status | Action |
| --- | --- | --- |
| Share-capture (01) | Built (`capture/CategorizationSheet.kt`) | Restyle to brand |
| Home Board (04) | Built (`board/BoardScreen.kt`) | Restyle, real thumbnails, nav shell |
| Action Plan (09) | Built (`detail/ItemDetailScreen.kt`) | Restyle, shopping fields, LLM suggest |
| Voice Journal (03) | Built (`voice/VoiceJournalScreen.kt`) | Restyle (record UI only) |
| Reminder Settings | Built (`reminder/`), no Stitch screen | Restyle |
| Voice Journal Review (06) | **Missing** | Build (transcript + extracted-action confirm) |
| Bucket Management (05) | **Missing** (logic exists) | Build |
| Create Bucket (08) | **Missing** (logic exists) | Build (incl. shopping toggle) |
| Shopping Item Detail (07) | **Missing** (logic exists) | Build as inline section on Action Plan |
| Games Hub (02) | **Missing** | Build |
| Spelling Bee (10) | **Missing** (backend done) | Build |
| Word Guess (11) | **Missing** (backend done) | Build |
| Leaderboard (12) | **Missing** (backend done) | Build |
| Join Organization (13) | **Missing** (backend done) | Build |
| Login (14) | Minimal | Build |
| User Profile (15) | **Missing** | Build |

Navigation today: only `BOARD ↔ REMINDER_SETTINGS` (a `RootScreen` enum in
`MainActivity.kt`). No nav library, no bottom bar.

---

## 3. Design system (extracted from the Stitch Tailwind configs)

The current Compose theme (`ui/theme/Color.kt`) is still the stock M3 purple
template and must be replaced. The Stitch designs use a **Material 3 Expressive**
scheme seeded from a warm terracotta (`#9f4122`).

### 3.1 Color — Light scheme

| Role | Hex | Role | Hex |
| --- | --- | --- | --- |
| primary | `#9f4122` | on-primary | `#ffffff` |
| primary-container | `#ff8a65` | on-primary-container | `#752305` |
| secondary | `#6d4ea2` | on-secondary | `#ffffff` |
| secondary-container | `#c5a3ff` | on-secondary-container | `#533487` |
| tertiary | `#006a63` | on-tertiary | `#ffffff` |
| tertiary-container | `#53bbb1` | on-tertiary-container | `#004842` |
| error | `#ba1a1a` | error-container | `#ffdad6` |
| background / surface | `#f4faff` | on-surface | `#001f2a` |
| surface-variant | `#c9e7f7` | on-surface-variant | `#56423c` |
| surface-container-lowest | `#ffffff` | surface-container-low | `#e6f6ff` |
| surface-container | `#d9f2ff` | surface-container-high | `#ceedfd` |
| surface-container-highest | `#c9e7f7` | outline | `#89726b` |
| outline-variant | `#ddc0b8` | inverse-surface | `#163440` |
| inverse-on-surface | `#e0f4ff` | inverse-primary | `#ffb59e` |

### 3.2 Color — Dark scheme

The Stitch "Dark" screens (Voice Journal, Word Guess) reuse the light token map
with Tailwind `dark:` utilities rather than a full dark token set. For Compose,
generate the dark scheme from seed `#9f4122` with the Material Theme Builder.
Sensible dark anchors:

| Role | Hex |
| --- | --- |
| primary | `#ffb59e` |
| on-primary | `#5e1700` |
| primary-container | `#7f2a0d` |
| on-primary-container | `#ffdbd0` |
| secondary | `#d4bbff` |
| tertiary | `#71d7cd` |
| background / surface | `#0f1417` |
| surface-container | `#1b2127` |
| on-surface | `#dfe3e7` |
| outline | `#89726b` |

> Keep `dynamicColor = false` by default so the SideQuest brand renders
> consistently; offer "Use system colors" as an opt-in in Profile. This diverges
> from the current `dynamicColor = true` default, which would override the brand.

### 3.3 Typography (Outfit + Inter)

| Token | Family | Weight | Size / Line |
| --- | --- | --- | --- |
| display-lg / headline | Outfit | 700 | 32 / 40 |
| title-lg | Outfit | 600 | 22 / 28 |
| title-md | Outfit | 600 | 16 / 24 |
| body-lg | Inter | 400 | 16 / 24 |
| body-md | Inter | 400 | 14 / 20 |
| label-md | Inter | 500 | 12 / 16 |
| label-sm | Inter | 500 | 11 / 16 |

Bundle Outfit + Inter as downloadable or packaged fonts; do not rely on the
Google Fonts CDN at runtime.

### 3.4 Shape & spacing

- Radius: small 8, default 16, large 32, full pill. FAB = 20dp squircle.
- Spacing scale: xs 4, sm 8, md 16, lg 24, xl 32; screen margin 20, gutter 16.
- Elevation: prefer tonal surfaces + soft ambient colored shadows
  (`shadow ≈ rgba(159,65,34,0.25)`) over heavy Material elevation.
- Motion: `scale 0.95` press feedback, 200–300ms ease-out transitions.

---

## 4. Navigation shell (new)

Adopt Navigation-Compose with a single bottom nav and a centered FAB, matching
the Home Board design.

```
BottomNav:  Board  |  Games  |  [ + FAB ]  |  Voice  |  Profile
```

- **Board** (`dashboard`) — home, completion counter, grouped items.
- **Games** (`videogame_asset`) — Games Hub → Spelling Bee / Word Guess → Leaderboard.
- **FAB (+)** — primary capture/add. In-app it opens the categorization sheet for
  a manually added item; the OS share sheet remains the primary external capture.
- **Voice** (`mic`) — Voice Journal record → review.
- **Profile** (`account_circle`) — account, organization, reminders, theme.

Routes that are pushed (not tabs): Action Plan / Item Detail, Reminder Settings,
Bucket Management, Create/Edit Bucket, Leaderboard, Join Organization, Login.

Share Target stays a **separate activity** overlay — it never enters the nav graph.

---

## 5. End-to-end flow

```
[OS Share Sheet] --"Save to Action Tracker"--> Share Target overlay
    classify content
      |- unsupported -> "Content type not supported" + Discard (nothing saved)
      |- supported   -> Categorization sheet (bucket + timeframe) -> Save
                          -> item created (status: not started) -> return to source app
                          -> if link: background preview fetch updates row later

[App launch]
  not authenticated -> Login -> (optional) Join Organization
  authenticated     -> Board (default tab)

  Board   -- tap item --> Action Plan (+ shopping fields if shopping bucket)
  Board   -- top bar  --> Reminder Settings
  Board   -- manage   --> Bucket Management -> Create / Edit Bucket
  Games   ------------> Games Hub -> Spelling Bee / Word Guess -> Leaderboard (D/W/M)
  Voice   ------------> record -> stop -> Voice Journal Review
                          confirm extracted actions -> each -> categorize (bucket + timeframe)
  Profile ------------> account, organization, reminders, theme toggle
```

---

## 6. Screen specs (the gaps + restyles)

Each references its Stitch source in `design/stitch/`.

### 6.1 Home Board — `04_home-board` (restyle built screen)
- Header: avatar (left, opens Profile), centered "SideQuest" wordmark (Outfit
  bold, primary), trophy action (right, opens Leaderboard).
- Completion counter: hero card; large numeral; consider a circular progress
  ring (the design shows a `75%` ring) instead of a flat count for stronger
  reward feedback.
- Grouped `LazyColumn` with sticky bucket headers (bucket name in primary).
- Item row: status dot (tap → Not started / In progress / Completed dropdown,
  colored per bucket), thumbnail (real image loader — add Coil), title/preview,
  2-line truncation. Shopping items show product name + optional "purchased".
- FAB: 20dp squircle, `add`, primary with ambient shadow.
- States: loading (centered spinner + "Loading your board…"), empty ("No action
  items yet. Share content into Action Tracker to get started.").

### 6.2 Bucket Management — `05_bucket-management` (new)
- Reorderable list of buckets with drag handles; each shows icon, name, item
  count, and a "Shopping features enabled" chip when applicable.
- Per-row overflow: Rename, Edit colors, Delete.
- Delete with items → reassign-or-delete dialog (move items to another bucket,
  or delete them).
- Entry to Create Bucket.

### 6.3 Create / Edit Bucket — `08_create-bucket` (new)
- Name field; reject case-insensitive duplicates ("name is in use").
- Three status color pickers: not started / in progress / completed.
- **Shopping bucket** switch — the entire "shopping" feature. When on, items in
  this bucket expose product name + source link + purchased affordance.
- This is the only bucket variation; no privileged bucket type in nav or layout.

### 6.4 Action Plan / Item Detail — `09_action-plan` + `07_shopping-item-detail` (restyle + extend)
- Progress: "x / y completed" + linear progress bar.
- Parent-complete prompt card when all sub-actions done → "Mark completed".
- Add-step field + ordered sub-action list (checkbox, strikethrough, up/down
  reorder; propose drag handles).
- For shopping-bucket items: inline product name + source link fields +
  purchased toggle (purchased → item completed). Merge `07` as a section here
  rather than a separate destination.
- Add LLM "Suggest actions" button and generated description (fail-soft:
  "unavailable" state, never an error).

### 6.5 Voice Journal — `03_voice-journal` (restyle, dark) + Review `06` (new)
- Record screen (built): record/stop full-width toggle, polite live-region
  status ("Recording…", "Recording saved.", "Transcribing…"), permission-denied
  card with deep link, transcription-failed card.
- **Review (new)**: show transcript; list extracted candidate actions with
  checkboxes to confirm/deselect; confirmed items flow into the categorization
  pattern (bucket + timeframe each).

### 6.6 Games Hub — `02_games-hub` (new)
- Two daily game cards (Spelling Bee, Word Guess) with today's status
  (play / completed result). Entry to Leaderboard.

### 6.7 Spelling Bee — `10_spelling-bee` (new)
- Hex letter layout with required center letter, current-word display,
  found-words list, score. Accept word only if it uses allowed letters and is in
  the word list. Replay guard: show recorded result if already completed today.

### 6.8 Word Guess — `11_word-guess` (new, dark)
- Guess grid + on-screen keyboard with colored key states: correct
  (right letter+position) / present / absent. Limited attempts. Replay guard.

### 6.9 Leaderboard — `12_leaderboard` (new)
- Segmented control: Daily / Weekly / Monthly. Ranked descending by score.
- Monthly resets monthly; daily/weekly roll at boundaries.
- No-org state → prompt to Join Organization.

### 6.10 Auth — `14_login`, `13_join-organization`, `15_user-profile` (new)
- Login / sign-up; optional org join at signup; sign-in on a new device pulls
  data via sync. Profile hosts account, org, reminder settings, and theme toggle.

### 6.11 Share Capture — `01_share-capture` (restyle built sheet)
- Modal bottom sheet over the source app. Title "Save to a bucket", 2-line
  preview, radio bucket list (empty → "Create a bucket first"), timeframe chips
  (Today / Within a day / Within a week / Specific date → DatePicker, past dates
  rejected inline), full-width Save (spinner, disabled until valid).

---

## 7. Cross-cutting behaviors (must preserve)

- **Offline-first**: never block on network; no blocking error for network
  failures. Enrichment (previews, LLM, transcription) layers in progressively.
- **Fail-soft enrichment**: LLM features show "unavailable", not errors;
  reminders fall back to default text.
- **Status as primary visual language**: three statuses, colored per bucket.
- **Buckets are uniform**: name + 3 status colors; optional shopping behavior is
  the only variation.
- **Permissions**: notifications requested once on first launch; microphone on
  first record. Denied states deep-link to OS settings with plain-language copy.
- **Accessibility (first-class)**: content descriptions on every interactive
  element; live-region announcements for status changes, recording state, and
  transcription progress; counters/progress announced as a single merged unit;
  maintain 4.5:1 contrast (verify the coral primary-container against its
  on-color in both themes).

---

## 8. Proposed divergences (improvements over the current build)

1. **Replace the stock purple theme** with the SideQuest expressive scheme
   above, and default `dynamicColor = false` so the brand is consistent;
   offer system colors as an opt-in. (Current default is dynamic-on.)
2. **Completion counter as a progress ring** (per the `75%` design) rather than
   a flat number — stronger accomplishment signal.
3. **Adopt Navigation-Compose** with the 5-item bottom shell + FAB, replacing
   the two-state `RootScreen` enum.
4. **Add Coil** for real link/preview thumbnails (board currently uses a
   placeholder box; no image loader on the classpath).
5. **Merge Shopping Item Detail into Action Plan** as an inline section instead
   of a separate destination — matches "shopping is a bucket behavior, not a
   screen."
6. **Add an in-app FAB capture path** so users can add items without the OS
   share sheet, reusing the categorization sheet.
7. **Streaks / subtle gamification** in Profile and the counter to reinforce the
   SideQuest theme (optional, backend permitting).

---

## 9. Suggested implementation order

1. Theme overhaul (Color/Type/Theme + fonts + Coil) — unblocks every restyle.
2. Navigation shell (bottom nav + FAB) — unblocks reaching built screens.
3. Restyle built screens (Board, Action Plan, Voice record, Capture, Reminders).
4. Bucket Management + Create/Edit Bucket (+ shopping toggle + inline shopping fields).
5. Voice Journal Review (transcript + extracted-action confirmation).
6. Games Hub → Spelling Bee → Word Guess → Leaderboard.
7. Auth: Login → Join Organization → Profile.
```
