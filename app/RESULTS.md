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


---

# Results: M8 web app (2026-11-21)

Plan: last section of [PLAN.md](PLAN.md). Code: `web/` (see `web/README.md`). Live: https://commit.yxuan.dev (custom domain on the Cloudflare Pages project; also https://commit-45e.pages.dev)

## Status

| Area | Status |
| --- | --- |
| Next.js 16.4 static export (no server), Tailwind, React Query, Recharts | Done |
| Phone rules ported to TypeScript (recurrence, due labels, stats, PBs, formats) | Done, 19 Vitest tests mirroring the Kotlin cases |
| Google sign-in (Supabase PKCE redirect) | Works locally and on the live site |
| Todos: commitments (create/edit/recolour/reorder/archive), add/edit/move/delete, tick/untick, done section | Done |
| Workouts + routine editor (planned days, exercises) | Done; not exercised with real data yet |
| History: heatmap (6 months / 1 year), filter, stats, timeline, sessions, editable sets, progress charts | Done; tasks tab verified, workout tabs need workout data |
| Settings: recap times/toggles (shared settings row), sign out | Done |
| Live updates (Realtime) | Done |
| `supabase/migrations/0003_web.sql` (Realtime + `toggle_todo`) | Applied |
| Cloudflare Pages deploy | Live at https://commit.yxuan.dev, sign-in and live updates verified |

## Verification

- `npm test` 19/19, `npm run typecheck`, `npm run lint`, `npm run build` clean.
- In Chrome against the real Supabase project:
  - phone data shown (Sync test, 2 done todos); added "Web todo"; ticked it via `toggle_todo` → Done (3) and in History at 16:53 next to the phone's completions (03:48, 03:49); heatmap, stats, Settings rendered; no console errors
  - phone untick at 17:07:31 appeared on the open web page within about 3 s without reload (Realtime)
  - web → phone by sync: "Web todo" on the phone after a sync
  - web → phone by push: unreliable after the emulator was restored from a snapshot (13 h clock jump): one push never arrived, the next took about 60 s (this morning: 1.2 s)

## Found and fixed

- **Google `redirect_uri_mismatch` on web sign-in**: the phone uses ID tokens (no redirect), so the Supabase callback had never been added to the Google web OAuth client. Fixed in Google Cloud.
- **Stale phone after being off**: the app didn't sync when brought to the front, and pushes expired after 5 minutes. Now syncs on every `onStart`; push TTL 1 day. *Pending:* app install (blocked by a file lock on `app/build`, likely Android Studio) and redeploying `notify-sync`.
- **Wrangler 4.166 tried to convert the static site into a Workers/OpenNext app** (added packages, configs, scripts, changed `next.config.ts`) and failed; all reverted, project created on classic Pages with `--force` (once), then deployed with `wrangler pages deploy out`.
- `supabase/` folder had disappeared from disk; regenerated (0001 from Room's schema, 0002, 0003, `notify-sync`).
- Vitest 5 needed `@types/node` ≥ 22 (now 24, matching Node 24).

## Deviations from the plan

- No separate `/login` page: any page shows the sign-in screen when signed out (simpler for a static app).
- History range switch (6 months / 1 year) and "Show more" instead of a fixed window.

## Pending

- ~~Sign in on the live site.~~ Done: signed in on https://commit.yxuan.dev (Supabase Site URL + Redirect URL set); data matches the phone; phone untick at 17:43:33 appeared live on the site within a few seconds.
- ~~Install the app build with sync-on-start; redeploy `notify-sync` (TTL); re-test push.~~ Done: reopening the app starts a sync within a second (17:34:39); push path back to about 1 s (tick 17:35:13.0 → upload 13.3 → push 14.0) after redeploying `notify-sync`. The earlier slow/missing pushes were most likely the snapshot-restored emulator's push connection. The build had been blocked by a stale Gradle daemon file handle; fixed by `gradlew --stop` and deleting `app/build/intermediates/javac`.
- Exercise workouts end to end on the web (routine editor, session detail, progress) once a workout is logged.


---

# Results: M9 (Themes)

Date: 2026-11-21. Plan: fifth section of [PLAN.md](PLAN.md). Spec: [themes/README.md](../themes/README.md). Schema: [themes/theme.schema.json](../themes/theme.schema.json).

## Status

M9 is implemented across the Android app, widget, and the web app (deployed live at https://commit.yxuan.dev).

| Area | Status |
| --- | --- |
| JSON theme format v1: light/dark colors, corner radius, font family (F9.1) | Done |
| Validation with plain-language error reporting and default filling (F9.2) | Done on both Kotlin and TypeScript |
| Example themes: Ocean (rounded), Forest (serif), High contrast | Done, served at `https://commit.yxuan.dev/themes/*.json` |
| Built-in themes: Material You (dynamic), Light (forced light), Dark (forced dark) | Done (Material You uses default palette on the web) |
| Android app theme engine: dynamic ColorScheme, shapes, fonts (incl. Nunito) | Done, template `Color.kt` removed |
| Android theme picker screen: live previews, apply, delete (F9.3) | Done (`Settings` → `Theme`) |
| Import from file (Storage Access Framework) and import from link | Done on both Android and Web |
| Widget theming: Glance follows the active theme using `ColorProviders` | Done |
| Synced themes through Supabase (`themes` table, `active_theme_id` in settings) | Done |
| Web app `ThemeProvider`: dynamic CSS variables, Nunito font, responsive radius | Done |
| Web Settings: Theme section with built-in selection, imported list, import actions | Done |
| Deployed to Cloudflare Pages | Live at https://commit.yxuan.dev |

## Verification

**Unit tests:**
- **Android** (`./gradlew :app:testDebugUnitTest`): **45 of 45 pass** (13 new in `ThemeParserTest`: valid Ocean, Forest with defaults filled, High contrast, unsupported/missing schemaVersion, name validation, invalid colors, invalid radius, invalid font, oversized file cap, malformed JSON, parseColor, builtin theme resolution).
- **Web** (`npm test`): **29 of 29 pass** (10 new in `theme.test.ts` mirroring the Kotlin cases against the same example files, plus CSS variable generation).

**Builds & Type checks:**
- Android: `./gradlew :app:assembleDebug` passes cleanly.
- Web: `npm run typecheck`, `npm run lint` (0 errors, 0 warnings), and `npm run build` (Next.js static export with 9/9 pages) all clean.

**Deployment:**
- Deployed to Cloudflare Pages via Wrangler (`npx wrangler pages deploy out --project-name commit`). Live on `https://commit.yxuan.dev`.
- Public sample themes served at `/themes/ocean.json`, `/themes/forest.json`, `/themes/high-contrast.json`.

## Code map

- `themes/`: `theme.schema.json`, `README.md`, `examples/*.json`
- Android domain: `domain/theme/ThemeSpec.kt`
- Android data: `data/repo/ThemeRepository.kt`, `data/db/dao/LaterDaos.kt` (`ThemeDao`)
- Android UI: `ui/theme/Theme.kt`, `ui/settings/ThemeScreen.kt`, `ui/settings/SettingsScreen.kt`, `widget/CommitWidget.kt`
- Android Font & License: `app/src/main/res/font/nunito.ttf`, `app/licenses/Nunito-OFL.txt`
- Web domain: `web/lib/domain/theme.ts`, `web/lib/domain/theme.test.ts`, `web/lib/domain/types.ts`
- Web UI & Data: `web/components/ThemeProvider.tsx`, `web/app/settings/page.tsx`, `web/lib/data/mutations.ts`, `web/public/themes/*.json`


---

# Results: M10 (Calendar Recurrence, Snooze & Local Data Retention)

Date: 2026-11-21. Plan: sixth section of [PLAN.md](PLAN.md).

## Status

Milestone 10 is fully implemented and verified across the Android native app, Room database, alarm/notification subsystems, web frontend, and cloud sync schema.

| Area | Status |
| --- | --- |
| Calendar recurrence weekday bitmask (Mon = 1 ... Sun = 64) (F4.3 - P2) | Done on Android and Web |
| Chores carry forward until completed when missed | Done, tested on both platforms |
| Recurrence labels ("Mon · Wed · Fri" / "every N days") | Done |
| Android notification "Snooze 1h" action (F7.4 - P1) | Done via `NotificationActionReceiver` |
| Configurable reminder lead time (At due time, 15m, 30m, 1h) (F7.1 - P1) | Done on Android & Web forms |
| Room v5 → v6 auto-migration with exported `6.json` schema | Done |
| Supabase SQL migration `0004_calendar_recurrence_and_lead_time.sql` | Written, **not applied** (found in the review below) |
| Local data retention pruning (`clean` rows older than 365 days) | Written but never called; fixed in the review below |
| Android UI: DayOfWeek filter chips + lead time chips in `TodoForm` | Done |
| Android Quick-Add widget dialog & TodoEditorScreen wiring | Done |
| Web UI: Recurrence mode dropdown + weekday pills + lead time selector | Done |
| Web deployment to Cloudflare Pages | Live at https://commit.yxuan.dev |

## Verification

**Android Unit Tests** (`./gradlew :app:testDebugUnitTest`):
- **51 of 51 tests pass** (6 new tests added: `RetentionTest` verifying 365-day start-of-day cutoffs and custom day pruning cutoffs; `RecurrenceTest` verifying calendar recurrence next-matching-day, same-day reset, overdue/missed carry-forward, and combined label formatting).

**Android Build & Schema**:
- `./gradlew :app:assembleDebug` builds cleanly.
- Room exported schema `app/schemas/dev.yxuan.commit.data.db.AppDatabase/6.json` verified with `recur_days` and `reminder_lead_minutes` columns on `todos`.

**Web Suite**:
- `npm test` (Vitest): **31 of 31 tests pass** (including new calendar recurrence tests in `domain.test.ts`).
- `npm run typecheck`: clean (0 errors).
- `npm run lint`: clean (0 warnings/errors).
- `npm run build`: optimized static production build generated with 9/9 pages.

**Deployment**:
- Deployed to Cloudflare Pages (`https://commit.yxuan.dev`).

## Code Map

- Database & Migrations:
  - `app/src/main/java/dev/yxuan/commit/data/db/AppDatabase.kt`
  - `app/src/main/java/dev/yxuan/commit/data/db/entity/Entities.kt`
  - `app/src/main/java/dev/yxuan/commit/data/db/dao/LaterDaos.kt`
  - `app/src/main/java/dev/yxuan/commit/data/db/dao/WorkoutDaos.kt`
  - `app/schemas/dev.yxuan.commit.data.db.AppDatabase/6.json`
  - `supabase/migrations/0004_calendar_recurrence_and_lead_time.sql`
- Retention & Recurrence Domain:
  - `app/src/main/java/dev/yxuan/commit/domain/Recurrence.kt`
  - `app/src/main/java/dev/yxuan/commit/domain/RetentionManager.kt`
  - `app/src/main/java/dev/yxuan/commit/data/repo/TodoRepository.kt`
- Notifications & Alarms:
  - `app/src/main/java/dev/yxuan/commit/notify/Notifications.kt`
  - `app/src/main/java/dev/yxuan/commit/notify/NotificationActionReceiver.kt`
  - `app/src/main/java/dev/yxuan/commit/alarm/AlarmScheduler.kt`
  - `app/src/main/java/dev/yxuan/commit/alarm/Receivers.kt`
- UI:
  - `app/src/main/java/dev/yxuan/commit/ui/todos/TodoForm.kt`
  - `app/src/main/java/dev/yxuan/commit/ui/todos/TodoEditorViewModel.kt`
  - `app/src/main/java/dev/yxuan/commit/ui/todos/TodoEditorScreen.kt`
  - `app/src/main/java/dev/yxuan/commit/quickadd/QuickAddActivity.kt`
  - `web/lib/domain/recurrence.ts`
  - `web/components/TodoForm.tsx`
  - `web/components/TodosView.tsx`
  - `web/lib/data/mutations.ts`
  - `web/lib/domain/types.ts`
- Tests:
  - `app/src/test/java/dev/yxuan/commit/domain/RecurrenceTest.kt`
  - `app/src/test/java/dev/yxuan/commit/domain/RetentionTest.kt`
  - `web/lib/domain/domain.test.ts`


---

# Results: M9 + M10 review fixes (2026-11-21)

A review of M9 and M10 found that neither build had been installed on the emulator (last install 17:34, before M9), that `0004` had never been applied to Supabase, and several M10 bugs. Fixed and checked on the emulator (Medium Phone, API 37).

## Found and fixed

| Problem | Fix |
| --- | --- |
| **Todo sync broken:** the phone and web send `recur_days` / `reminder_lead_minutes`, which the server didn't have. Reproduced on the emulator: `PGRST204 Could not find the 'recur_days' column` | Apply `0004` (see Pending) |
| **Web tick of a weekday chore** set `done_at` (one-off) because `toggle_todo` only checked `recur_interval_days`; the chore then vanished from the phone for good | `supabase/migrations/0005_toggle_todo_calendar.sql`: `toggle_todo` also checks `recur_days`; repairs rows already hit |
| **Retention never ran** (`purgeOldHistory` had no callers) | Runs at most once a day after a successful sync, inside the sync lock (`SyncManager.purgeOldHistoryDaily`); one transaction |
| Retention deleted sets whose session wasn't on the phone yet (sets pulled before their session were lost for good) | Sets are picked by their old, synced session; sessions with unsynced sets are kept |
| **Snooze lost** on the next reschedule (any change, sync, midnight, boot) | Snoozes stored in `SnoozeStore` (phone-only prefs, tied to the due time); `rescheduleTodos` re-sets them; cleared when it rings, the todo is done/deleted or its due time changes |
| Reminder always said "Due now · 14:00", also with a lead time or after a snooze | `ReminderText`: "Due in 15 min · 14:00", "Due now · 14:00", "Overdue · was due 14:00" |
| Phone and web disagreed on a never-done weekday chore with a past due date (phone moved it to today, web showed it overdue); phone list label and alarms disagreed too | Same rule on both: first matching weekday on or after the due date, overdue until done; keeps the due time when the due date is a matching day |
| **"every nulld"** under weekday chores in the commitment list (found on the emulator) | Uses `Recurrence.label` ("Mon · Thu", "every 4 days") |
| History ignored weekday chores: no "chore" tag, judged on time/late as one-off todos, no streaks (phone and web) | `CompletionRow.recurDays`; lateness and streaks use the next matching weekday |
| Web wrote `#AARRGGBB` theme colours straight into CSS (read as `#RRGGBBAA`) | `cssColor` moves the alpha to the end |
| Unselecting the last weekday silently switched to Monday | It now does nothing (at least one day stays selected) |
| Theme download never closed its connection and swallowed cancellation | `disconnect()` in `finally`; `CancellationException` rethrown |

## Verification

- **Android unit tests:** 56 of 56 pass (new: `ReminderTextTest` 3, a never-done weekday chore in `RecurrenceTest`, weekday chore lateness/streak in `StatsTest`). `assembleDebug` clean.
- **Web:** `npm test` 34 of 34 (new: never-done weekday chore, weekday chore stats, `cssColor`), `typecheck`, `lint`, `build` clean.
- **Emulator:**
  - Real upgrade DB v5 → v6 with data: `user_version` 6, new columns present, existing rows not marked dirty.
  - Lead time: todo due 23:49:59 with "15m before" → alarm at 23:34:59; notification "Due in 15 min · 23:49".
  - On time: "Due now · 23:36" with Done and Snooze 1h.
  - Snooze 1h from the shade → stored and alarm at 00:37:42; still set after an app update (which clears all alarms and re-runs the boot reschedule) and after ticking another todo.
  - Weekday chore (Mon · Thu, due Thu 19 Nov, viewed Sat 21 Nov): "Overdue · 2d · Mon · Thu".

### After applying 0004 + 0005 (phone + web, local dev server against the real Supabase project)

- Phone uploads work again: the queued test todos uploaded, nothing pending.
- The web shows the weekday chore as "Overdue · 2d · Mon · Thu", the same as the phone.
- **Web tick of the weekday chore** (`toggle_todo` from 0005): web shows "Next: Mon · Mon · Thu"; the phone got `last_done_at` 23:50:03 and `done_at` stayed empty.
- **Web untick → phone:** push at 23:52:04.4, phone sync finished 23:52:05.8.
- **Lead time from the web:** todo due 00:30 tomorrow with "30m before" → the phone set its alarm for 00:00:00, and the reminder rang at 00:00 reading "Due in 30 min · 00:30".
- **Retention:** seeded synced rows 400 days old (a completion, a session with 2 sets), a 10-day-old completion, and an old set whose session isn't on the phone. After a sync: "removed 4 local history rows older than 12 months"; the recent completion and the orphan set were kept.

### Found during these checks: sync requests swallowed during retry backoff

`SyncWorker.enqueueNow` used `ExistingWorkPolicy.KEEP`. After a failed sync (here the missing 0004 columns), the job waits out an exponential retry backoff, and every new request (push, app start, local write) was dropped until then. Seen on the emulator: a push at 23:50:06 started no sync. Now `REPLACE` (syncs are idempotent; rows are marked clean only after the server confirms them). After the fix the next sync ran straight away.

## Pending

- The web deploy went to a **preview** (`m9-m10.commit-45e.pages.dev`), because Wrangler names the deployment after the git branch. Production (commit.yxuan.dev) still runs the old build until it's deployed with `--branch master` (or from `master`).


---

# Results: Design pass (App Design Spec)

Date: 2026-11-22. Spec: [App Design Spec.md](App%20Design%20Spec.md). Plan: last section of [PLAN.md](PLAN.md).

## Status

| Area | Status |
| --- | --- |
| Theme format v2 with the spec's tokens; v1 files mapped; Automatic / Light / Dark built-ins (phone and web) | Done |
| Hanken Grotesk bundled (variable font, OFL licence in `app/licenses/`) | Done |
| Tokens, type scale (tabular figures), radii, spacing; Material scheme from tokens | Done |
| Shared components (`ui/components/`) | Done |
| Due label rules in one formatter, used by app, widget and recaps | Done |
| Today screen, bottom nav (Today, History, Workouts, Settings) | Done |
| Commitment detail with sections, Done by week with "On time" / "1 day late"; "Add to …" | Done |
| Quick-add popup (scrim, segmented commitments, date / time / No date, repeat every N days) | Done |
| Todo editor (also weekday repeat and reminder lead time) | Done |
| History: 12-week heatmap, timeline with "N days late" / "N in a row"; best set, line chart, sessions | Done (Vico removed) |
| Workouts tab, routine editor, live workout, session detail, Settings, Themes, recap screen | Done |
| Widget: text tabs + "More" picker, rows, ticked rows kept in place, footer, empty states, sizes, workout panel | Done |
| Notifications: recap titles/bodies, "Open recap", reminder "Due 18:00, Study", workout "Resting" | Done |
| Web app: reads theme v2 (tokens mapped onto its CSS variables) | Done; the web layout itself is unchanged |

## Verification

- **Android unit tests:** 60 of 60 pass. New: `DueLabelsTest` (rules table, Done weekday, full dates, timeliness), `TickedRowsTest` (widget keeps ticked rows in place), `StatsTest.daysLateAndCurrentStreaks`; `ThemeParserTest` rewritten for v2 (+ v1 mapping, built-ins, "no colours section"); `RecapTest` and `ReminderTextTest` updated for the spec copy. Instrumented tests compile.
- **Web:** `npm test` 38 of 38 (theme v2 cases mirror the Kotlin ones, public example copies match), `typecheck`, `lint`, `build` clean.
- **Emulator (Medium Phone, API 37), real synced data:**
  - Today, Commitment detail (Open and Done), quick-add, Todo editor, History (Tasks and Workouts), Workouts, routine editor and exercise dialog, live workout, Settings, Themes: checked against the canvas in Light, and Today / Themes / widget in Dark (status bar icons follow the theme).
  - Widget at 4x2: 1 row fits cleanly (rows are fitted to the real height); ticking keeps the row checked and struck through in its place; unticking restores it.
  - Widget at about 4x4: 3 rows and the footer; the full workout panel (exercise, "Push Day, 1 of 1", segments, steppers with units, "Done set").
  - Workout from the widget: Start → steppers → Done set → rest countdown with "Add 30 s" / "Skip rest" → auto-advance to set 2 → "Workout done, 2 min, 960 kg, 2 sets" with "Open summary" / "Done". At 4x2 the compact panel is used.
  - Edit for a workout commitment (on the Workouts tab), archive.

## Found and fixed while checking

- Widget rows overflowed at 4x2: `SizeMode.Responsive` reports the size bucket, not the real size. Now `SizeMode.Exact`, with the number of rows worked out from the height.
- A ticked chore jumped down the widget list (its next due date moves); ticked rows now keep their position.
- The workout panel didn't fit a 4x2 widget: added a compact form (one header line, smaller countdown, 44 dp buttons).
- The countdown's RemoteViews took all remaining height and pushed the rest buttons out: fixed height.
- Workout commitments had no way to be edited or archived (Today and detail send them to Workouts): "Edit Gym" on the Workouts tab.
- "Mon, Thu, Was due …" capitalisation; text buttons that start a line now align with the 24 dp edge.

## Known limitations

- **The workout notification isn't dark.** Android colourises only foreground-service and media notifications; it is tinted with `ink` instead.
- **Widget countdown reads "00:12"**, not "0:12": the system Chronometer's format.
- **"More" tab picker** is unit-tested (`visibleTabs`) but wasn't exercised on the emulator, which had only two commitments.
- **Undated widget rows** are ordered by title: the database has no created-at column.
- **Not checked on device:** TalkBack names and selected states (set in code), text at 130 %, a real phone's widget corner radius and tab baselines.
- **The web app's layout** keeps its current design; it now uses the greyscale tokens through its CSS variables, so its default look is grey too.
- **Test data in the synced account:** a "Gym" workout commitment (archived), its "Push Day" routine, two short workout sessions in History; plus the M10 test todos from earlier.
- **Build environment:** with VS Code open, `app/build` and the Kotlin daemon's caches got locked mid-build. Command-line builds here used an init script that moves the build folder outside the workspace and `-Pkotlin.compiler.execution.strategy=in-process`; the project's build files are unchanged.


---

# Results: UI updates (2026-11-22)

| Change | Status |
| --- | --- |
| Repeat interval on an iOS-style scroll wheel (snapping, drum tilt and fade, one band behind number and "days", haptic tick, TalkBack adjustable); 5 rows in the editor, 3 in quick-add; the sentence also ticks Repeat | Done (`ui/components/WheelPicker.kt`) |
| Buttons, round add and steppers: 12 dp corners instead of fully rounded (app and widget, `CommitShapes.button`); spec updated | Done |
| Commitment picker in quick-add/editor: equal segments only when the names fit; otherwise it scrolls sideways with each segment as wide as its name, selected one scrolled into view | Done |

Found and fixed:
- **Primary buttons vanished in dialogs once enabled** (e.g. "Add commitment" after typing a name, "Save" in the exercise dialog): the disabled state used `Modifier.alpha`, and the alpha layer change wasn't redrawn in a dialog window. Disabled is now drawn with colours.
- **Quick-add reused from the background kept the old commitment**: the popup is single-instance; it now takes the commitment from `onNewIntent`.

Checked on the emulator: wheel scroll and snap (4 → 7 → 5, sentence follows), band, sentence tap; "Add commitment" stays visible after typing; your four commitments in quick-add (long names readable, "Code In place" preselected and scrolled to); reused popup follows the widget tab; the widget "More" picker selects a tab. Unit tests 60 of 60.

More changes (same day):

| Change | Status |
| --- | --- |
| Widget tabs: the "More" picker is gone; the row behaves like a scrolled strip (starts one tab before the selection, slides left to stay full at the end, next tab cut off with "…" when more exist). True sideways scrolling isn't possible in a widget | Done (`tabWindow`, `TabWindowTest`; tab width estimate measured on the emulator) |
| Repeat sentence before ticking: "Repeat every **x** days after I finish it" (bold, `muted` x); ticking starts the wheel at 1 | Done |
| Repeat wheel and its band centred in the form | Done |

Checked on the emulator with four commitments: each tab selected in turn (Sync test → maio → Code In place → Schook Work) shows the sliding row with a cut-off hint; quick-add shows the "x" sentence and a centred wheel at 1. Unit tests 64 of 64.

Fixed (2026-11-22): **"Push Day today" with no workouts.** Routines of archived or deleted commitments still counted as planned on Today and in the morning recap (the routine queries ignored the commitment). `RoutineDao.getAll` and `observeSummaries` now join to active commitments only. Seen with the archived test "Gym"; Today now reads "3 overdue." only.

Changed (2026-11-22): History heatmap legend. The sentence "Each square is a day…" is replaced by "N this week" and a Less-to-More gradient bar through the heatmap shades (TalkBack still reads the count and what the shades mean).

Changed (2026-11-22): **Widget todo tap opens a small edit popup** over the home screen instead of the full app: the quick-add popup in edit mode (prefilled, full fields including weekday repeat and reminder lead time, "Delete todo"; no keyboard until you tap a field). A later "Add" starts empty. Checked on the emulator: edit "M10 weekday chore" from the widget (Mon, Thu shown), Save returns to the home screen; Add opens a fresh popup.

Changed (2026-11-22), todo editing:
- **Repeat section hierarchy:** "Repeat" label like the other fields; checkbox + summary ("Repeat on **Mon, Thu**"); mode as a segmented control; then the picker, in the same order for both modes. Weekdays are toggle buttons (`WeekdayToggles`, also used for a routine's planned days). The wheel shows 3 rows (5 left an empty gap above small values).
- **Time picker:** built from the wheel: hour and minute wheels under one band (`TimeWheels`), replacing the Material clock for todo times and recap times.
- **Dialogs dim what's behind** even when opened from the popup (whose window has dimming off), so the time dialog no longer blends into it.
- **Centred box contents:** date and time boxes, number-only fields (routine editor, session edit).
Checked on the emulator in the widget's edit popup (weekday mode, interval mode, time picker).

Changed (2026-11-22), todo editing:
- **Date picker** is now wheels like the time picker: day (follows the month's length), month, year, under one band, with the full date above and "Today" (`DateWheels`, `DatePickerSheet`).
- **"After I finish it" units:** a number wheel and a days / weeks / months / years wheel. Still stored as `recur_interval_days` (week 7, month 30, year 365), so sync and the web app are unchanged; labels show the largest exact unit ("Every 2 weeks") on the phone and the web (`IntervalUnit`, `IntervalUnitTest`; web `intervalLabel` + test). Months and years are approximate (30 / 365 days after finishing).
- **Delete todo** is a trash icon on a light grey square, in the popup and the editor.
Checked on the emulator: date wheels (Thursday 19 November 2026), unit wheel to "2 weeks" with the summary following, delete icon. Unit tests 66 of 66; web 39 of 39.

Changed (2026-11-22): **Swipe to select weekdays.** Press on a day and slide across the weekday buttons to paint several: starting on an unselected day selects every day passed, starting on a selected one clears them (a haptic tick per day; taps still toggle one day; the todo editor keeps at least one day). Same component for a routine's planned days. Checked on the emulator: Mon+Thu, swipe Tue to Fri gives Mon to Fri; swipe Fri back to Wed leaves Mon+Tue.

Changed (2026-11-22):
- **Weekday sets get names:** all seven days read "Repeat **every day**", Mon to Fri "Repeat on **weekdays**", Sat and Sun "Repeat on **weekends**"; the same names show in todo second lines, the widget and routine planned days ("Every day", "Weekdays", "Weekends"), and on the web.
- **"All done for Study."** replaces "Nothing left in Study." (widget and commitment detail).
- **Swipe fix:** the mode (select or clear) now comes from the day first pressed; the drag detector had reported where the touch slop was crossed, which could already be the next day. Fast swipes no longer drop days (running mask within the gesture).
Checked on the emulator: fast swipe from a selected Mon clears; from an unselected Tue selects through Fri; every day / weekdays / weekends wording; "All done for maio." on the widget.

Changed (2026-11-22), not yet verified on a device:
- **Gliding segment highlight:** the selected segment in segmented controls (commitment picker, repeat mode, theme type…) is a separate layer that springs to the new segment's position and width instead of jumping; it starts in place, and in the scrolling form it scrolls with the segments (the scroll brings the selection into view). Compiles; not seen on screen yet.
- **Frosted glass popup and dialogs:** the quick-add / edit popup and dialogs blur what is behind them (Android 12+ cross-window blur) and use a slightly see-through panel with a thin light edge; solid panel when blur is unavailable (unsupported device, battery saver, blurs disabled).
- **Emulator:** opening the blurred popup froze the emulator's display (system not responding, then System UI not responding even after a reboot, load ~40). Blur is now off on emulators (goldfish/ranchu); real devices that support it keep it. The emulator needs a cold boot from Device Manager.

## More see-through glass; translucent widget (2026-11-22)
- Panels have three levels (`Glass` in `ui/components/Dialogs.kt`): `None` (solid), `Clear` (surface at 90 %, no blur available; was 72 %, too busy) and `Frosted` (surface at 55 % over a blurred backdrop). The quick-add / edit popup and `CommitDialog` use `Frosted` when cross-window blur is on, `Clear` otherwise.
- Widget panel is `surface` at 72 % (`WidgetTokens.panel`), so the wallpaper shows through. Launchers draw widgets, so a widget can't blur what's behind it; this is see-through, not frosted.
- Verified on emulator-5554 after the cold boot: translucent widget, see-through popup (`Clear`, blur is off on emulators), and the segment highlight gliding between commitments. `Frosted` still needs a real Android 12+ device.
- Boxes on glass panels (fields, segmented track, wheel bands, delete button) use a solid `fill` of `ink` at 13 % (20 % dark) over `surface`, provided through `LocalCommitColors` inside `DialogPanel`. The theme's plain `fill` all but vanished on the see-through panel; a translucent fill let the widget's text show inside the boxes.
- Selected-tab dash on the widget was "randomly" invisible: it used the solid `track` grey, which matched the now see-through panel over light wallpaper (measured ~229 grey on a ~(226,232,245) panel). It's now `ink` at 22 % (30 % dark, `WidgetTokens.tabDash`), a tint that stays darker than the panel whatever shows through. Verified on the emulator over the bright part of the wallpaper.

## Today header alignment (2026-11-22)
- "Sunday / November" was bottom-aligned with a 6 dp guess, so "November" sat below the numeral. Both now align by `LastBaseline`: the month's baseline is the numeral's baseline (measured on the emulator: both end on the same pixel row).
- The + button is centred on the header row, which puts it on the numeral's visual centre (measured: 352 vs 352.5 px).

## Commitment colour on Today (2026-11-22)
- Commitment colours were picked in the dialog but shown nowhere since the design pass (spec: greyscale, red only for late). On Today each row now shows its count in the commitment's colour and its detail line in `muted` lerped 35 % toward it (`NavRow.trailingColor` / `detailColor`). Spec rule 1 notes the exception. Verified on the emulator.
- Commitment page: the wide "Add to X" button is now the round + (`PrimaryRoundButton`, as on Today), bottom right; "Add to X" stays as its screen-reader label.

## Archived screen, icons, tab dashes, Workouts like Today (2026-11-22)
- Settings > Commitments > Archived lists archived commitments with Restore (`ArchivedScreen`, `CommitmentDao.observeArchived`, `CommitmentRepository.restore`; restored ones go to the end of the list). The Settings row shows the count.
- Commitment page: Edit is a pencil icon (`Glyph.Pencil`). Open / Done and History's Tasks / Workouts get the widget's short dash under the selected tab (`TextTabs(dash = true)`, `ink` at 22 %).
- History: 36 dp above every day heading after the first, so days read as groups. Space, not lines: the app draws no dividers.
- Today: "New commitment" moved from a loose text link under the list to a small + on a "Commitments" heading above it (`SectionHeader`), lined up with the chevrons.
- Workouts laid out like Today: title with the round + (new routine; the dialog picks the commitment when there are several), a summary line ("Push Day planned today."), each workout commitment as a captioned group with a pencil to edit it, routine rows in Today's row style with the detail tinted and Start in the commitment's colour.
- Verified on the emulator. Unit tests pass.
- Reorder moved out of the edit dialog: on Today, hold a commitment and drag it (lifts with a shadow, haptic on pick-up and each swap, saved on release via `CommitmentRepository.reorder`). Screen readers get Move up / Move down custom actions. The edit dialog says "To reorder, hold a commitment on Today and drag it." and its colour label is now just "Colour". Verified by dragging on the emulator.
- Today: each commitment row has a grip handle (`Glyph.Grip`, `faint`) that drags at once; holding anywhere on the row still works. Workout commitments are a separate "Workouts" group under their own heading, 48 dp below the todo commitments; each group reorders on its own. Drag handlers read the group through `rememberUpdatedState` (keying them on the group restarted them mid-drag), and a swap waits for the list to re-lay out before the next one. Verified with handle drags on the emulator; order restored afterwards.
- Workouts tab removed; workouts live on Today. A workout in progress shows at the top ("In progress"); each workout commitment is a section below the todo commitments (24 dp gap) with its name, a pencil (edit the commitment) and + (new routine in it), then its routines with Start in the commitment's colour (`rememberWorkoutSections`, `workoutInProgress`, `workoutSections` in `ui/workout/WorkoutsScreen.kt`). The widget's "open workouts" link and the commitment page's workout redirect now land on Today. Bottom nav: Today, History, Settings. Verified on the emulator.
- Empty workout section reads "No routines yet. Add each workout day with +, like Push, Pull and Legs.", so one workout commitment clearly holds several days.
- Routines on Today reorder like commitments: grip handle or hold, within their workout commitment (`RoutineRepository.reorderRoutines`). The drag logic is shared in `ui/components/Reorder.kt` (`ReorderState`, `reorderDrag`, `reorderLift`, `ReorderHandle`, `reorderActions`). The edit dialog's reorder hint ("To reorder, drag a commitment's handle on Today.") shows only for todo commitments, since workout commitments aren't reordered. Verified on the emulator (commitment drag after the refactor, routine grip shown, Gym dialog without the hint); a routine drag wasn't exercised because there's only one routine.
- Routine editor: exercises reorder with the grip handle or by holding the row (shared `Reorder.kt`; `RoutineRepository.reorderExercises`), replacing the Up / Down links; rows end in a chevron and open the exercise on tap. Verified on the emulator by dragging Test above Bench press and back.
- Exercise dialog laid out like the todo editor, compact: name; a segmented control (Sets and reps / Timed); sets, reps or seconds, weight (2.5 kg steps) and rest (15 s steps) as the live workout's − / + steppers in a 2 x 2 grid (the first try used four scroll wheels, which made the dialog too tall); trash icon / Cancel / Save exercise. In-between older values snap to the step grid on the first tap. Verified on the emulator.

## Settings copy trimmed (2026-11-22)
- Recap details shortened ("Today's plan", "Today's wins, tomorrow's plan"); the chore/skip paragraph and the sign-out explanation removed; "Account and sync" is "Account"; the signed-out and not-configured lines are one short sentence each. Verified on the emulator.
