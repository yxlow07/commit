# Commit: Product Requirements Document

## Overview

An Android home-screen widget that runs my day from the home screen: todos grouped by commitment, recurring chores, and a guided workout with rest timers. All of it works offline and syncs to a web app on my PC.

**Problem.** Todos for study, work and side projects live in different places. Checking or updating them means opening an app, so I stop doing it. Workouts need a separate timer app, and chores like laundry are easy to forget.

**Product.** One widget with a tab per commitment, where I can tick, add and run things without leaving the home screen. Behind it are a full app (history, routines, themes) and a web app with the same data.

### Goals

1. Tick off or add a todo from the widget in under 5 seconds, without opening the app.
2. Run a full workout from the widget or lock screen: sets, reps, weight and automatic rest timers.
3. Recurring chores reset themselves and remind me when due.
4. Start and end the day with a recap notification.
5. Everything works offline. Changes made on the PC show up on the phone within about a minute, and changes on the phone show on the PC within seconds.

### Non-goals (v1)

- iOS app or widget
- Sharing commitments with other people
- Browsing or importing themes from the widget (theme management lives in the app)
- Local AI

## Platforms and tech stack

The Android app is native Kotlin, so the widget and app share one codebase. Supabase is the shared copy that the phone and the web app both sync to.

| Layer | Choice | Why |
| --- | --- | --- |
| Android app UI | Kotlin, Jetpack Compose, Material 3 | Native; themes map to a Material 3 colour scheme |
| Widget | Jetpack Glance (+ `AndroidRemoteViews` for `Chronometer`) | Compose-style widget code; Chronometer gives a smooth countdown at no update cost |
| Local database | Room (SQLite) | Offline source of truth for app and widget |
| Background work | WorkManager (sync), AlarmManager (exact alarms) | Reliable sync; on-time reminders, rest timers and recaps |
| Backend | Supabase: Postgres, Auth (Google sign-in), Realtime, Edge Functions, RLS | One service for data, auth and the push trigger |
| Push | Firebase Cloud Messaging data messages | Wakes the phone to sync after web edits |
| Web app | Next.js or SvelteKit + supabase-js | Talks to Supabase directly, protected by row-level security |
| Sync engine | Build it myself first; PowerSync as the fallback | Learning value; PowerSync has Kotlin and web SDKs if conflicts get messy |

Target: Android 12+ (API 31), for widget checkboxes and current exact-alarm rules.

## Functional requirements

Requirements are tagged P0 (needed for v1), P1 (soon after) or P2 (later).

### F1. Commitments

- **F1.1 (P0)** Create, rename, recolour, reorder and archive commitments (e.g. Study, Work, Project A).
- **F1.2 (P0)** Each commitment has a type: `standard` (todos) or `workout`.
- **F1.3 (P1)** Choose which commitments appear as widget tabs, and in what order.

### F2. Todos

- **F2.1 (P0)** A todo has a title, an optional due date with optional time, and belongs to one commitment.
- **F2.2 (P0)** Mark done / undone. Done todos are kept for history, never deleted.
- **F2.3 (P0)** Edit, move to another commitment, delete (soft delete).
- **F2.4 (P1)** Overdue todos show in red; todos due today show in bold.

### F3. Widget

- **F3.1 (P0)** Tab row across the top, one tab per commitment; tapping a tab switches the list without opening the app.
- **F3.2 (P0)** Scrollable list of the selected commitment's undone todos, sorted by due date (no due date last), each with its due label.
- **F3.3 (P0)** Checkbox on each row marks the todo done in place. The row fades out on the next redraw.
- **F3.4 (P0)** A "+ Add" button opens a small dialog-style popup over the home screen (translucent activity): title field, date/time picker, commitment defaulting to the current tab. Save closes it and returns to the home screen.
- **F3.5 (P1)** Recurring todos show their next due date and reset automatically (F4).
- **F3.6 (P1)** Resizable: 4x2 minimum, list grows with height.

### F4. Recurring todos

- **F4.1 (P0)** Any todo can have a recurrence rule, so chores sit next to one-off todos in the same commitment.
- **F4.2 (P0)** Interval mode: due again N days after it was last done (e.g. laundry every 4 days).
- **F4.3 (P2)** Calendar mode: fixed days (e.g. every Sunday).
- **F4.4 (P0)** "Done" is calculated, not stored: done while now is before `last_done_at + interval`. At the reset time the todo reappears on the widget and a notification fires.
- **F4.5 (P0)** Every completion is logged, which gives history and streaks. Unticking removes the latest completion and recalculates `last_done_at`.

### F5. Workout mode

- **F5.1 (P0)** Routines (e.g. Push Day) built in the app or on the web: ordered exercises, each with sets, target reps, weight and rest seconds; optional `duration_seconds` for timed exercises (plank).
- **F5.2 (P0)** The workout tab shows the routine picker, then the current exercise, set progress (3 of 4), reps and weight, and the rest timer.
- **F5.3 (P0)** Reps and weight are editable on the widget with +/- steppers before ticking "Done set". The logged set stores the actual values.
- **F5.4 (P0)** Tapping "Done set" starts the rest timer. When rest ends the workout advances automatically: next set, or next exercise after the last set (using the routine's longer rest-between-exercises), or "Workout complete".
- **F5.5 (P0)** Timed exercises run hands-free: the work period is a timer too, then rest, then the next step.
- **F5.6 (P0)** Countdown shown live on the widget (Chronometer). When a timer ends the phone vibrates and makes a sound.
- **F5.7 (P1)** Ongoing notification during a workout with the countdown, "Done set", "+30s" and "Skip rest" buttons, usable from the lock screen.
- **F5.8 (P1)** End or abandon a workout. A session is saved with start/end time and every set as done.

### F6. Daily recaps

- **F6.1 (P0)** Morning recap notification at a time I set (default 07:30): todos due today and overdue, recurring chores due today, today's planned workout if any.
- **F6.2 (P0)** Night recap notification at a time I set (default 22:00): what I completed today per commitment, what is still open or overdue, what is due tomorrow, today's workout summary (duration, volume).
- **F6.3 (P0)** Both are built from the local database when they fire, so they work offline and reflect web edits after the latest sync.
- **F6.4 (P1)** Expandable notification (inbox style) with a short list; tapping it opens a recap screen in the app with the full list.
- **F6.5 (P1)** Skip a recap when there is nothing to report, and turn each recap on or off.

### F7. Notifications

- **F7.1 (P0)** Due-date reminder for todos with a due time, at the due time (P1: configurable lead time, e.g. 1 hour before).
- **F7.2 (P1)** Todos with a date but no time: included in the morning recap instead of a separate reminder.
- **F7.3 (P0)** Recurring chore becomes due (F4.4), rest timer ends (F5.6), recaps (F6).
- **F7.4 (P1)** Notification actions: "Done" and "Snooze 1h" on todo reminders.
- **F7.5 (P0)** Separate notification channels (Reminders, Workout, Recaps) so each can be muted in Android settings.

### F8. History

- **F8.1 (P0)** Task timeline: completed todos and recurring completions, grouped by day, filterable by commitment.
- **F8.2 (P1)** Calendar heatmap of completions per day.
- **F8.3 (P1)** Per-commitment stats: completed this week, on time vs late, recurring streaks.
- **F8.4 (P0)** Workout session list: date, routine, duration, total volume.
- **F8.5 (P0)** Session detail showing every set as done; past sets are editable (fix a wrongly logged weight).
- **F8.6 (P1)** Progress chart per exercise: best weight and volume over time, personal-best markers.
- **F8.7 (P1)** Same history views on the web app, which also covers data older than the phone keeps (NF).

### F9. Themes (app)

- **F9.1 (P1)** A theme is a JSON file of design values only (colours, corner radius, optional font), with a `schemaVersion`. No code.
- **F9.2 (P1)** Import from a file or a link; validate against the schema; fill missing values from the default theme; reject invalid files with a clear message.
- **F9.3 (P1)** Theme picker in the app with preview; built-in light, dark and Material You themes.
- **F9.4 (P2)** Themes sync through Supabase and the web app uses the same JSON as CSS variables.

### F10. Web app

- **F10.1 (P1)** Sign in with the same Google account.
- **F10.2 (P1)** Manage commitments, todos, recurring chores and workout routines with the same rules as the app.
- **F10.3 (P1)** Live updates when the phone changes something (Supabase Realtime).
- **F10.4 (P2)** History and progress charts (F8.7).

## Architecture

&#91;embedded content: system architecture · phone, Supabase, web app\]

The widget, app and alarms only touch Room. WorkManager syncs Room with Supabase, and an FCM ping wakes the phone after web edits so the widget and alarms update.

## Data model

The same tables exist in Postgres (Supabase) and Room. Every synced table has a client-generated UUID `id`, `user_id`, `updated_at` (set by a Postgres trigger) and a `deleted` flag.

| Table | Key fields | Notes |
| --- | --- | --- |
| `commitments` | name, type (`standard` / `workout`), color, sort\_order, show\_in\_widget |  |
| `todos` | commitment\_id, title, due\_at, has\_time, done\_at, recur\_interval\_days, last\_done\_at | `done_at` for one-off todos; `recur_*` + `last_done_at` for recurring ones |
| `completions` | todo\_id, done\_at | One row per recurring completion; history and streaks |
| `routines` | commitment\_id, name, rest\_between\_exercises\_s, sort\_order | Workout templates |
| `exercises` | routine\_id, name, sets, reps, weight, rest\_seconds, duration\_seconds, sort\_order | `duration_seconds` set = timed exercise |
| `workout_sessions` | routine\_id, started\_at, ended\_at, notes | One per workout |
| `workout_sets` | session\_id, exercise\_id, set\_no, reps, weight, done\_at, skipped | Actual values as performed; editable in history |
| `themes` | name, json | P2: synced themes |
| `settings` | morning\_recap\_time, night\_recap\_time, recap toggles, active\_theme\_id | One row per user |

**Phone only (not synced):** the live workout session state (current exercise, set, `set_started_at`, `rest_ends_at`, edited reps and weight), the selected widget tab, the sync queue and `last_synced_at`.

**Indexes:** `(user_id, updated_at)` for sync, `(user_id, done_at)` for history, `(commitment_id, due_at)` for the widget list.

## Sync, offline and notification rules

The phone's Room database is the source of truth on the phone. The widget and popup only ever read and write Room, never the network.

### Sync

1. Every local write marks the row as unsynced and enqueues a one-off WorkManager job (network required).
2. Push: send unsynced rows to Supabase as upserts.
3. Pull: fetch rows where `updated_at > last_synced_at`, apply them to Room, update `last_synced_at`.
4. Conflicts: the newest `updated_at` wins (server clock via trigger, so phone and PC clocks never disagree).
5. Deletes are soft (`deleted = true`) so they sync like any other change.
6. Periodic WorkManager sync every 15 minutes as a safety net.
7. Web → phone: a database webhook calls an Edge Function, which sends an FCM data message; the phone runs a sync, redraws the widget and reschedules alarms.

### Timers and state

- Anything time-based stores a timestamp and is calculated when drawn: recurring "done" (`last_done_at + interval`) and the workout step (`rest_ends_at`). Alarms only vibrate, notify and redraw, so a late or missed alarm never leaves the wrong state on screen.
- Exact alarms via `setAlarmClock()` or `setExactAndAllowWhileIdle()`, handled in a `BroadcastReceiver` that works with the app closed.

### Alarm scheduling

- After every local change and every sync: cancel and reschedule alarms for affected todos (due reminders, recurring resets).
- Recap alarms reschedule themselves daily and are re-registered on boot and after time-zone changes.
- Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (or `USE_EXACT_ALARM`), `RECEIVE_BOOT_COMPLETED`. If exact alarms are refused, fall back to inexact alarms and show a banner in the app.

## Non-functional requirements

| Area | Requirement |
| --- | --- |
| Offline | Every widget and app action works with no network; changes sync when back online |
| Widget speed | Tick, tab switch and stepper taps redraw in under 1 s; quick-add popup opens in under 500 ms |
| Sync latency | Phone → web within 10 s when online; web → phone within 1 min via FCM |
| Timer accuracy | Rest-end alert within 2 s of the target, screen off and in Doze |
| Battery | No polling; work only on user action, alarms, FCM and the 15-minute periodic sync |
| Security | Google sign-in via Supabase Auth; RLS `user_id = auth.uid()` on every table; no secrets in the web bundle beyond the anon key |
| Data retention | Phone keeps the last 12 months of history; the web app reads older data from Supabase |
| Reliability | Alarms re-registered on boot and app update; sync is idempotent (safe to retry) |
| Accessibility | Widget text follows system font size; colour is never the only signal (overdue also shows a label) |

## Milestones

Each milestone ends with something usable on my phone. Sync comes after the offline app is solid.

1. **M1 Local core:** Room schema, app screens for commitments and todos.
2. **M2 Widget:** list, then tabs, then checkboxes, then the quick-add popup.
3. **M3 Recurring + reminders:** recurrence rules, completions, exact alarms, notification channels.
4. **M4 Workout:** routines editor, workout tab with steppers, auto-advancing timers, Chronometer, ongoing notification.
5. **M5 Recaps:** morning and night recap notifications and the recap screen.
6. **M6 History:** task timeline, workout sessions, editing past sets, progress charts.
7. **M7 Sync:** Supabase auth, schema with RLS, push/pull sync, FCM ping.
8. **M8 Web app:** sign-in, CRUD, Realtime, history.
9. **M9 Themes:** JSON schema, import, picker; then synced themes.

## Out of scope and open questions

### Out of scope for v1

- iOS app and widget
- Shared or collaborative commitments
- Notifications on the web app
- Calendar import (Google Calendar, Brightspace deadlines)
- 

### Assumptions I made (confirm or change)

- Due dates have an optional time; todos with only a date go in the morning recap rather than getting their own reminder.
- The widget shows only undone todos, sorted by due date, with undated todos last.

### Open questions

- [ ] Should the widget follow the active theme's colours? Theme management stays in the app either way; applying colours to the widget is cheap with Glance.
- [ ] How does the morning recap know "today's planned workout"? Options: a weekly schedule per routine (Mon = Push Day), or leave it out.
- [ ] Workout tab with no active session: show the last routine with a Start button, or a routine picker?
- [ ] Build sync myself or use PowerSync from the start?
- [ ] Web framework: Next.js or SvelteKit?
