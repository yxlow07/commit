# Results: M1 (Local core) + M2 (Widget)

Date: 2026-11-21. Plan: [PLAN.md](PLAN.md). PRD: [PRD.md](PRD.md).

## Status

M1 and M2 are implemented and verified on the emulator (Medium Phone, API 37).

| Area | Status |
| --- | --- |
| Room schema, all 9 PRD tables with sync columns | Done |
| Commitments: create, rename, recolour, reorder, archive, type (F1.1, F1.2) | Done |
| Todos: title, optional date/time, done/undone, edit, move, soft delete (F2.1–F2.3) | Done |
| Overdue in red, due today in bold, always with a text label (F2.4) | Done |
| Widget tabs, list, checkboxes, "+ Add" popup (F3.1–F3.4) | Done |
| Resizable widget, list grows with height (F3.6) | Done |
| Hide commitments from the widget (F1.3, partly) | Done ("Show on widget" switch). Custom tab order follows the commitment order |

## Verification

**Build:** `./gradlew :app:assembleDebug` passes. The schema is exported to `app/schemas/dev.yxuan.commit.data.db.AppDatabase/1.json` (commitments, todos, completions, routines, exercises, workout_sessions, workout_sets, themes, settings).

**Unit tests** (`./gradlew :app:testDebugUnitTest`): 8 of 8 pass.
- `DueFormatterTest` (5): no date, today with and without time, earlier today counts as overdue, overdue day count, tomorrow / weekday / date / other year
- `VisibleTabsTest` (3): tab overflow always keeps the selected tab visible

**Instrumented tests** (`./gradlew :app:connectedDebugAndroidTest`): 6 of 6 pass in `DataLayerTest`.
- Open todos sort by due date with undated ones last
- Lists exclude done, deleted and other-commitment todos; delete is soft
- Unticking restores a todo
- Writes set `dirty`, `updated_at` and trimmed titles, and notify the change listener
- The widget query only includes visible, non-archived commitments
- Reordering and archiving commitments

**By hand on the emulator:**
- Created Study, Work and Gym (workout) commitments; added todos with no date, a date only, and a date and time
- Ticked a todo in the app and it moved to "Done (1)"
- Placed the widget at 4x2: tabs with colour dots, selected tab highlighted, todos in due order
- Ticking on the widget removed the row; switching tabs worked without opening the app
- Work tab with no todos shows "All done here"; Gym tab shows the workout placeholder
- "+ Add" from the Work tab: popup over the home screen, Work preselected, keyboard up; Save returned to the home screen and the todo appeared on the widget
- Tapping a widget row opens that todo's editor
- No crashes in logcat

## Changes from the plan

- **Kotlin 2.2.10 → 2.4.21.** Current library versions (kotlinx-serialization 1.13, which pulls in stdlib 2.4.10) need Kotlin 2.4; the build failed on 2.2.
- **KSP 2.3.14.** This is the KSP line that works with AGP 9's built-in Kotlin and no longer has to match the Kotlin version.
- **Added `kotlinx-coroutines-android` 1.11.0.** The app was getting coroutines 1.9.0 through other libraries, which broke `runTest` in instrumented tests at runtime (`NoSuchMethodError`).
- **No migration-test schema assets.** Not needed at database version 1; add them with the first migration.
- **Widget data loading.** Tabs and todos are Room flows collected inside the Glance composition (preloaded once so the first frame isn't empty). Tab switches and ticks redraw straight from the database. `WidgetUpdater.updateAll` still runs after every write, for widgets whose session has closed.

## Library versions

AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14, Room 2.8.5, Glance 1.2.0, Navigation Compose 2.10.2, Lifecycle 2.11.1, Activity Compose 1.13.0, Core KTX 1.19.1, Compose BOM 2026.02.01, kotlinx-serialization 1.13.0, coroutines 1.11.0.

## Code map

- `CommitApp.kt`, `AppContainer.kt`: manual DI container; `DeviceClock` follows the current device time zone
- `data/db/`: `AppDatabase`, entities (`entity/Entities.kt`), DAOs (`dao/`)
- `data/repo/`: `CommitmentRepository`, `TodoRepository` (write rules: UUID, `updated_at`, `dirty`, soft delete), `DataChangeListener`, `Due`
- `ui/commitments/`, `ui/todos/`: screens and ViewModels; `TodoForm` is shared with the quick-add popup
- `ui/format/DueFormatter.kt`: due labels, shared by the app and the widget
- `widget/`: `CommitWidget`, actions (`SelectTabAction`, `ToggleTodoAction`), `WidgetUpdater`, receiver
- `quickadd/QuickAddActivity.kt`: translucent popup over the home screen

## Known limitations (for M3 and later)

- **Labels go stale at midnight.** "Today" and "Overdue" are worked out when the widget redraws, so they stay stale until the next data change. M3's alarms should trigger the redraw (a timer would break the PRD's no-polling rule).
- **Recurring todos aren't calculated yet.** The `recur_interval_days` and `last_done_at` columns exist, but the widget query ignores them until M3.
- **Date-only todos are stored as local midnight.** Changing time zone shifts which day they fall on. Revisit before sync (M7).
- **Tabs don't scroll.** Glance has no scrolling row, so tabs beyond the widget's width are cut off. The selected tab always stays visible.
- **No custom widget tab order** separate from the commitment order (rest of F1.3).
- **Widget preview** in the picker is the generic app icon; a proper preview layout can come later.

## Next

M3: recurring todos (interval mode, completions log), exact alarms, notification channels, and a midnight redraw of the widget.


---

# Results: M3 (Recurring + reminders) + M4 (Workout)

Date: 2026-11-21. Plan: second section of [PLAN.md](PLAN.md).

## Status

M3 and M4 are implemented and verified on the emulator (Medium Phone, API 37).

| Area | Status |
| --- | --- |
| Recurring todos, interval mode, reset at the start of the due day (F4.1, F4.2, F4.4) | Done |
| Completion log; untick removes the latest completion and recalculates `last_done_at` (F4.5) | Done (one-off todos are logged too) |
| Recurring todos on the widget with their next due date (F3.5) | Done |
| Notification channels: Reminders, Workout, Workout timer alerts, Daily recaps (F7.5) | Done |
| Due-time reminders with a "Done" action (F7.1, F7.4) | Done ("Snooze 1h" not built yet) |
| Chore-due reminder at the morning time, 07:30 by default (F7.3) | Done |
| Midnight widget redraw (fixes the M2 stale-label limitation) | Done |
| Alarms re-registered on boot, app update, time and time-zone change | Done |
| Notification permission request plus a banner when it's off | Done |
| Routines editor: exercises with sets, reps, weight, rest, timed (F5.1) | Done |
| Workout tab: routine picker, set progress, reps/weight steppers (F5.2, F5.3) | Done |
| Auto-advance after rest, longer rest between exercises, completion (F5.4) | Done |
| Timed exercises run hands-free (F5.5) | Done |
| Live Chronometer countdown on the widget; sound + vibration at timer end (F5.6) | Done |
| Ongoing lock-screen notification with countdown, "Done set", "+30s", "Skip rest" (F5.7) | Done |
| End / abandon; session saved with every set (F5.8) | Done (a session with no sets is discarded) |

## Verification

**Unit tests** (`./gradlew :app:testDebugUnitTest`): 22 of 22 pass.
- `RecurrenceTest` (6): late-day tick resets at the start of the due day, never-done chores, overdue chores keep their missed date, month end and DST, one-off todos, open-list order
- `WorkoutEngineTest` (8): start prefill, logging edited values, rest → next set with carried edits, longer rest between exercises then hands-free timed sets, late resolve replaying every ended timer, skip and +30s, no negative weight or reps, single-set completion and volume
- Earlier: `DueFormatterTest` (5), `VisibleTabsTest` (3)

**Instrumented tests** (`./gradlew :app:connectedDebugAndroidTest`): 11 of 11 pass.
- `DataLayerTest` (8): the 6 from M1/M2, plus recurring tick/untick with the completion log, and one-off completion logging
- `WorkoutTest` (3): migration 1 → 2 with `MigrationTestHelper` (data kept, `active_workout` added); a full workout through `WorkoutRepository` with a test clock (edited values logged, alert on timer, edits carried, a late timer replays to completion, session ended); ending with no sets discards the session

**By hand on the emulator:**
- Chore "Laundry, every 1 day": shows "Today · every 1d"; ticking moves it to Done as "Next: Tomorrow"; it disappears from the widget; `dumpsys alarm` shows the reminder at tomorrow 07:30 (exact) and the midnight redraw
- Todo due 01:30: the notification arrived at 01:30:01 on the Reminders channel; "Done" ticked it, cleared the notification and updated the widget
- Reboot: all alarms (01:40 reminder, midnight, 07:30 chore) were re-registered within about 15 s of boot; the 01:40 reminder then arrived at 01:40:01 reading "Due now · 01:40"
- Built "Push Day" (Bench 2×8 @ 60 kg, rest 15 s; Plank 2×10 s, rest 10 s; 15 s between exercises) in the app
- From the widget: Start → steppers (7 reps, 62.5 kg) → Done set → Chronometer rest countdown with "Next: Bench · set 2" → alert, then auto-advance to set 2 with the edits carried
- From the notification shade: "Done set" → "Rest · Next: Plank" with countdown, "+30s", "Skip rest"; the alarm-clock status icon shows the timer
- The plank sets ran hands-free (rest → work → rest → work) to "Workout complete · 2 min · 875 kg · 4 sets" on the widget, with a sounding heads-up alert (875 = 2 × 7 × 62.5)
- A workout running across a reboot finished correctly when the alarms were restored after boot
- Timer accuracy with the screen off: the rest-end alert fired within about 0.1 s of the target, twice

## Changes from the plan

- **No foreground service, as planned:** alarms carry all workout timing; the ongoing notification is a plain notification.
- **Workout timer handling lives in the shared `AlarmReceiver`** (kind `WORKOUT_TIMER`) rather than a separate `WorkoutTimerReceiver`.
- **Room plugin supplies the schemas to instrumented tests**, so no `sourceSets` change was needed for `MigrationTestHelper`.
- **"Done early" on a timed set:** a running work timer can be ended early; the set still counts.
- **Reminder wording:** a due-time reminder reads "Due now · 01:30" instead of the list label "Overdue" (found during on-device testing and fixed).

## Known limitations

- **Deep Doze not verified.** The emulator never entered deep idle (`force-idle` stopped at INACTIVE, even on simulated battery). The alert was on time with the screen off; `setAlarmClock` is exempt from Doze by design, but check once on a real phone.
- **The ongoing notification can be swiped away** on Android 14+ (platform behaviour for non-foreground-service notifications). The workout keeps running and the widget still shows it.
- **"Snooze 1h"** (F7.4) and a configurable reminder lead time (F7.1 P1) are not built yet.
- **Calendar recurrence** (F4.3, P2) is not built.
- **Reminder times use the settings row**, but there is no settings screen yet (M5 adds recap times).
- **`connectedDebugAndroidTest` uninstalls the app**, which removes the widget and app data on the test device. Use a separate emulator for manual checks, or re-add the widget afterwards.

## Next

M5: morning and night recap notifications, built from Room when they fire (F6), and a settings screen for recap times and toggles.


---

# Results: M5 (Recaps) + M6 (History) + M7 (Sync)

Date: 2026-11-21. Plan: third section of [PLAN.md](PLAN.md). Sync setup: [SYNC_SETUP.md](SYNC_SETUP.md).

## Status

M5 and M6 are implemented and verified on the emulator. M7 is implemented and verified locally (PowerSync over Room, triggers, backfill), but **not yet end to end**: that needs the Supabase, PowerSync and Firebase projects described in `SYNC_SETUP.md`.

| Area | Status |
| --- | --- |
| Morning recap: overdue, due today (incl. date-only, F7.2), chores, planned workout (F6.1) | Done |
| Night recap: done today per commitment, workout summary, still open, due tomorrow (F6.2) | Done |
| Recaps built from Room when they fire (F6.3) | Done |
| Expandable inbox notification, tap opens the recap screen (F6.4) | Done |
| Skip empty recaps, on/off toggles, times in a new Settings screen (F6.5) | Done |
| Planned workout = weekdays per routine (open PRD question), DB v3 | Done |
| Task timeline grouped by day, commitment filter (F8.1) | Done |
| Calendar heatmap, 26 weeks, tap a day to filter (F8.2) | Done |
| Per-commitment stats: this week, on time vs late, chore streaks (F8.3) | Done |
| Workout session list (F8.4) and session detail with editable / deletable sets (F8.5) | Done |
| Per-exercise progress: best-weight line with PB markers, volume columns, PB list (F8.6, Vico) | Done |
| Room 2.8 → Room 3.0.3 with the bundled SQLite driver | Done |
| DB v4: `dirty` columns dropped (PowerSync tracks uploads) | Done |
| PowerSync over Room: raw tables, CRUD triggers, backfill of pre-sync rows | Done, verified locally |
| Supabase connector, Google sign-in (Credential Manager), Account section | Done, needs accounts to test |
| Background sync: after writes, every 15 min, on FCM ping (WorkManager) | Done, needs accounts to test |
| Backend: Postgres schema + RLS, Sync Streams config, `notify-sync` Edge Function, setup guide | Written, not yet applied |
| Web app (F8.7, F10) | M8 |

## Verification

**Unit tests** (`./gradlew :app:testDebugUnitTest`): 32 of 32 pass.
- `RecapTest` (4): morning contents and summary, night grouping by commitment with workout summary, empty day skipped, weekday mask
- `StatsTest` (6): one-off on time vs late, chore lateness from previous completion + interval, this week from Monday, overdue chore has no streak, per-day heatmap counts, personal bests after a baseline
- Earlier: `RecurrenceTest` (6), `WorkoutEngineTest` (8), `DueFormatterTest` (5), `VisibleTabsTest` (3)

**Instrumented tests** (`./gradlew :app:connectedDebugAndroidTest`): 17 of 17 pass, on Room 3.
- `MigrationTest` (1): v1 → v4 with data (rows kept, `planned_days` default, `active_workout` present, `dirty` gone from all 9 tables), using Room 3's driver-based `MigrationTestHelper`
- `SyncIntegrationTest` (3), PowerSync 1.16 over our Room DB with the extension loaded:
  - Room writes are queued in order as `commitments:PUT, todos:PUT, completions:PUT, todos:PATCH, todos:PATCH` with the right column values; a soft delete uploads as a patch of `deleted`
  - backfill queues rows written before the triggers existed, as PUTs
  - the phone-only `active_workout` table is never queued
- `RecapHistoryTest` (3): recaps built from real rows, settings defaults until first change, editing/deleting a past set updates session volume, set count and progress
- `DataLayerTest` (8) and `WorkoutTest` (2): unchanged behaviour on Room 3 (including transactions through the new `withTransaction` helper)

**By hand on the emulator:**
- Recaps set to 02:10 and 02:15: the morning recap arrived at 02:10:04 ("2 due today · Push Day"; expanded: Pay rent · Home · Today, Laundry, Today: Push Day); the night recap at 02:15:04 ("1 done (1 Home) · 2 still open", 4 lines). Tapping the morning recap opened the recap screen with its sections
- Settings: time pickers and toggles; changing the morning time also moved the chore reminder alarm (both at 02:10 in `dumpsys alarm`)
- Routine editor: weekday chips; the routine list shows "1 exercise · Mon · Sat"
- History → Tasks: heatmap, filter chips, stats card, timeline. History → Workouts: two sessions; editing set 2 from 8 to 6 reps changed the session volume from 1,040 kg to 910 kg
- History → Progress: second session at 70 kg flagged as a personal best (larger accent dot, "Personal bests: 70 kg × 8"), volume columns per session
- **Real upgrade, Room 2 v3 → Room 3 v4:** installed the Room 3 build over the Room 2 build with data on it; all commitments, todos and both sessions (including the edited set) were intact, and the app ran without errors
- No keys: the app starts normally, Settings → Account & sync says sync isn't set up and points to the guide; no PowerSync or network activity

## Changes from the plan

- **Room 3 changes beyond the package rename:** `@TypeConverter(s)` → `@ColumnTypeConverter(s)`; `withTransaction` replaced by a small helper over `useWriterConnection { immediateTransaction { } }` (`data/db/Transactions.kt`); a SQLite driver is required (`BundledSQLiteDriver`); migration tests moved to the new driver-based helper and merged into one v1 → v4 test.
- **PowerSync only opens when keys are present** (plan said "always"): without sync configured the app does no PowerSync work at all, so a local-only build behaves exactly as before. Rows written before sync existed are covered by the one-time backfill at first sign-in.
- **Triggers recreated on every start** instead of only in a migration, so they always match the current columns.
- **Device tokens** go straight to Supabase (`device_tokens` table, own rows only) instead of a synced local table.
- **Postgres schema generated from Room's exported `4.json`**, so server and phone tables can't drift; booleans stay 0/1 and times stay epoch-ms on both sides.
- **Polish found during testing:** stronger heatmap colours (one completion was nearly invisible), readable heatmap dates for screen readers, "every day" instead of "every 1 days", chore lines labelled in recaps, "1 set done" instead of "1 sets done", and the night summary names the workout.

## Known limitations

- **M7 end-to-end is untested** until the accounts exist: sign-in, upload to Supabase, download into Room, the FCM ping, and the latency targets (phone → web within 10 s, web → phone within 1 min).
- **PowerSync's Room integration is beta.** If it misbehaves on real sync data, the fallback is PowerSync's own managed tables or the hand-written sync from the original PRD.
- **Conflicts:** last write to reach the server wins (server-clock `updated_at`). The PRD rule "newest `updated_at` wins" holds only roughly when both devices edit the same row offline.
- **12-month local retention** is not implemented: deleting old local rows would delete them on the server through the triggers. Needs a local-only purge path.
- **Settings row on first sign-in:** the phone's settings replace any settings the web app saved earlier.
- **Volume chart with one session** draws a single very wide column (cosmetic; normal from two sessions).
- **`connectedDebugAndroidTest` uninstalls the app**, wiping app data and removing the widget on the test device.
- `local.properties` contains a `SUPABASE_PASSWORD` entry. The app doesn't use it; keep it out of version control (the file is already git-ignored by the template).

## Next

- **To finish M7:** follow `SYNC_SETUP.md`, add the four keys to `local.properties`, sign in, and run its checklist.
- **M8:** web app (sign-in, CRUD with the same rules, Realtime, history), then **M9:** themes.


---

# Results: M7 reworked without PowerSync

Date: 2026-11-21. Plan change: last section of [PLAN.md](PLAN.md). Setup: [SYNC_SETUP.md](SYNC_SETUP.md) (now free tiers only).

## Status

| Area | Status |
| --- | --- |
| PowerSync removed (dependencies, schema/trigger code, sync config, tests, setup steps) | Done |
| DB v5: `dirty` flag back (default 1), set on every local write | Done |
| Hand-written sync engine: push, pull, conflicts, soft deletes, paging, idempotent | Done, verified against a fake server |
| Google sign-in via Supabase Auth, Account section with status and pending count | Kept, needs accounts to test |
| Background sync: after writes, every 15 min, app start, FCM ping | Kept, needs accounts to test |
| Supabase schema regenerated from v5 (no `dirty`, no PowerSync publication) | Written, not yet applied |

## Verification

- **Unit tests:** 32 of 32 pass (unchanged).
- **Instrumented tests:** 20 of 20 pass.
  - `MigrationTest` (1): v1 → v5 with data; `dirty` present again and 1 on existing rows.
  - `SyncEngineTest` (6), real Room database plus an in-memory server that behaves like the Supabase tables (server-clock `updated_at`, `user_id` stamp, ordered and paged pulls):
    - dirty rows upload with the right columns (never `dirty`), then come back with the server's `updated_at` and `user_id`, and nothing is pending
    - web inserts and edits are pulled into Room and its Flows; pulled rows aren't re-uploaded
    - conflict: a phone edit made offline after a PC edit reaches the server last and wins on both sides
    - an edit made while its row is uploading stays pending and uploads on the next round
    - soft deletes sync both ways
    - 1,200 server rows are pulled in pages; the cursor moves to the newest; the next round's overlap re-read changes nothing (re-reads don't trigger widget/alarm refreshes)
  - Earlier suites (`DataLayerTest` 8, `RecapHistoryTest` 3, `WorkoutTest` 2) unchanged.
- **Emulator, no keys:** the app runs; Settings → Account & sync says sync isn't set up and asks for the Supabase and Google keys.

## Found and fixed

- The pull's 5-minute overlap window (which catches late-committed rows) counted re-read rows as changes, which would have redrawn the widget and rescheduled alarms on every sync. Rows already at the same server `updated_at` are now skipped, and only real changes are counted.

## Known limitations

- **End to end untested** until the Supabase project and Google OAuth clients exist: sign-in, real upload/download, the FCM ping, and the latency targets.
- **Last write to reach the server wins** (as the PRD specifies, server clock).
- **Supabase Free pauses idle projects** after about a week without requests; the 15-minute background sync keeps it active while the phone is in use.
- **Signing in with a different Google account later** would upload this phone's data into that account.
- **12-month local retention** is still not implemented.

## Next

Follow `SYNC_SETUP.md` (3 keys: `supabase.url`, `supabase.anonKey`, `google.webClientId`), sign in, and run its checklist. Then M8: web app.


---

# Results: sync verified end to end (2026-11-21)

With the real Supabase project, Google OAuth clients and Firebase project from `SYNC_SETUP.md`, on the API 37 emulator:

| Check | Result |
| --- | --- |
| Google sign-in (Credential Manager → Supabase Auth) | Works; session survives reinstall and app restart |
| Phone → server | New commitment and todo uploaded; rows in Supabase with `user_id` set; sync job finished within seconds of each save |
| Push token registered | One row in `device_tokens` |
| Web → phone, app open | Table-editor edit ("Water plants web") applied on the phone |
| Web → phone, app in background, by push | Push received 03:47:36.8, sync finished 1.2 s later, phone shows "Water plants 2" |
| Widget/app tick → server | Sync finished about 0.5 s after the tap; nothing pending |
| Offline | Changes queue ("2 changes to upload"); uploaded about 3 s after the network returned |

Found and fixed during these checks:

- **Server trigger blanked the owner on dashboard edits** ("null value in column user_id"): it always set `user_id := auth.uid()`, which is null in the Table editor. Now the owner is set on insert and never changed by updates (`supabase/migrations/0002_fix_row_meta_owner.sql`, also folded into `0001` for new projects).
- **Sign-in errors** now say what to do ("No Google account on this device…", "Sign-in cancelled").
- **Status line** says "Synced just now" instead of "0 minutes ago", and shows the pending count right after a change, also while offline.
- Added a `CommitSync` log tag for push receipt and sync failures.

Expected behaviour seen: the phone's own uploads trigger "echo" pushes (one per changed table), each followed by a quick sync that changes nothing.

Not re-run this round: instrumented tests (they uninstall the app and sign out); unit tests 32 of 32 pass and androidTest compiles.
