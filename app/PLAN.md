# Plan: Commit — M1 (Local core) + M2 (Widget)

## Context
`app/PRD.md` describes "Commit": an offline-first Android widget + app for todos grouped by commitment, recurring chores, workouts, recaps, later synced to Supabase and a web app. The repo is still the blank Compose template (AGP 9.5.2 with built-in Kotlin, Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 31, package `dev.yxuan.commit`). This plan delivers the first two milestones so there is something usable on the phone: a Room-backed app for commitments/todos (M1) and the Glance home-screen widget with tabs, checkboxes and a quick-add popup (M2).

Decisions made with the user:
- Detailed scope: M1 + M2 only (M3–M9 later).
- DI: manual `AppContainer` on a custom `Application` (reachable from widget, receivers, workers).
- Room schema: **all** PRD tables now, with sync columns, to avoid migrations before M7.

## 1. Build setup
Files: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`.
- Add plugins: KSP (version matching Kotlin 2.2.10), Room Gradle plugin (`room { schemaDirectory("$projectDir/schemas") }`), `kotlin.plugin.serialization` (type-safe nav routes).
- Add libraries: Room (`room-runtime`, `room-ktx`, `room-compiler` via ksp), Glance (`glance-appwidget`, `glance-material3`), `navigation-compose`, `lifecycle-viewmodel-compose` + `lifecycle-runtime-compose`, `kotlinx-serialization-json`, `kotlinx-coroutines-test`, `room-testing`. Bump the stale `coreKtx`/`lifecycle`/`activityCompose` versions.
- Check latest stable versions at implementation time; confirm KSP works with AGP 9 built-in Kotlin (no `kotlin-android` plugin needed).

## 2. Data layer (M1) — `dev.yxuan.commit.data`
**Conventions for every synced entity:** `id: String` (client UUID, PK), `userId: String?` (null until sign-in in M7), `updatedAt: Long` (epoch ms; local clock now, server trigger later), `deleted: Boolean`, `dirty: Boolean` (unsynced flag; set on every local write). Times stored as epoch-millis `Long`; a `Converters` class handles enums (`CommitmentType { STANDARD, WORKOUT }`).

Entities (`data/db/entity/`), mirroring the PRD data model:
- `CommitmentEntity` — name, type, color (ARGB Int), sortOrder, showInWidget, archived
- `TodoEntity` — commitmentId, title, dueAt?, hasTime, doneAt?, recurIntervalDays?, lastDoneAt?
- `CompletionEntity` — todoId, doneAt
- `RoutineEntity`, `ExerciseEntity` (durationSeconds? = timed), `WorkoutSessionEntity`, `WorkoutSetEntity`, `ThemeEntity`, `SettingsEntity` (single row; recap times as minutes-of-day, toggles, activeThemeId)

Indexes per PRD: `(userId, updatedAt)` on all synced tables, `(userId, doneAt)` on todos/completions/sets, `(commitmentId, dueAt)` on todos. Foreign-key columns indexed; no hard FK cascades (soft deletes must sync).

Phone-only table: `sync_state` placeholder (`last_synced_at`) can wait for M7; the widget's selected tab lives in Glance state, not Room.

`AppDatabase` (version 1, `exportSchema = true`). DAOs:
- `CommitmentDao` — `observeActive()` (not deleted/archived, ordered by sortOrder), `observeWidgetTabs()` (+ showInWidget), upsert, reorder (batch update sortOrder).
- `TodoDao` — `observeOpenForCommitment(id)`: `deleted = 0 AND doneAt IS NULL ORDER BY dueAt IS NULL, dueAt` (F3.2; recurring "done" calculation added in M3), `observeAllForCommitment`, `getById`, upsert, `setDone(id, doneAt, now)`, `softDelete`, `move(id, commitmentId)`.
- Stub DAOs for the remaining tables (basic upsert/observe) so the schema compiles and is exported.

Repositories (`data/repo/`): `CommitmentRepository`, `TodoRepository`. They own the write rules: generate UUID, set `updatedAt = clock.now()`, `dirty = true`, soft delete only, and after each todo/commitment write call `WidgetUpdater.refresh()` (see §4). Inject a `Clock` (`java.time.Clock`) for testability.

`CommitApp : Application` holds `AppContainer` (db, repositories, clock, widget updater). Register in `AndroidManifest.xml`.

## 3. App screens (M1) — `dev.yxuan.commit.ui`
Navigation Compose with `@Serializable` routes, single `MainActivity`.
- **Commitments screen** (start): list with colour dot; create/rename/recolour dialog (fixed palette of ~10 colours), type picker (standard/workout) on create, drag-free reorder via up/down actions, archive. (F1.1, F1.2)
- **Commitment detail**: todos for that commitment — open ones sorted like the widget, done ones collapsed below; tap to edit, checkbox to toggle done/undone (F2.2). Workout commitments show a "Workout mode arrives in M4" placeholder.
- **Todo editor** (bottom sheet or screen): title, optional date, optional time (`DatePickerDialog`/`TimePicker` from M3), commitment dropdown (move, F2.3), delete (soft).
- Shared `DueLabel` formatter (`ui/format/DueFormatter.kt`): "Today 14:00", "Tomorrow", "Mon 24 Nov", "Overdue · 2d" — pure function, reused by widget. Overdue red / today bold (F2.4, P1 but trivial here; label text ensures colour isn't the only signal).
- ViewModels per screen exposing `StateFlow` from repository flows; created with a `viewModelFactory { initializer { … } }` reading `AppContainer`.
- Replace the template `Greeting` in `MainActivity.kt`; keep `ui/theme/` as is.

## 4. Widget (M2) — `dev.yxuan.commit.widget`
Built in the PRD's order: list → tabs → checkboxes → quick-add.
- `CommitWidget : GlanceAppWidget` + `CommitWidgetReceiver : GlanceAppWidgetReceiver`; `res/xml/commit_widget_info.xml` (min 4x2, `resizeMode="horizontal|vertical"`, `targetCellWidth/Height`, preview layout/description); receiver in manifest.
- `provideGlance`: load tabs + selected tab's open todos from Room (via `AppContainer`), then `provideContent { GlanceTheme { … } }`. Use `SizeMode.Responsive`/`Exact` so the list grows with height (F3.6).
- **State:** `PreferencesGlanceStateDefinition`, key `selected_commitment_id` (per widget instance). Default = first tab; fall back if that commitment was archived/deleted.
- **Tabs (F3.1):** horizontally laid-out `Row` of tab chips (Glance has no scrollable row; truncate/ellipsize names; cap visible tabs and rely on F1.3 `showInWidget` to limit). Click → `actionRunCallback<SelectTabAction>(actionParametersOf(commitmentId))` that writes state and calls `update`.
- **List (F3.2):** `LazyColumn` of rows: Glance `CheckBox` + title + due label (from shared `DueFormatter`). Empty state text. Row tap opens the app at that todo (`actionStartActivity` with deep-link extras).
- **Checkbox (F3.3):** `onCheckedChange = actionRunCallback<ToggleTodoAction>(todoId)` → `TodoRepository.setDone` → widget refresh; the row disappears on redraw because the query only returns open todos.
- **"+ Add" (F3.4):** button → `actionStartActivity<QuickAddActivity>(commitmentId)`.
- Workout-type tab shows a placeholder until M4.
- `WidgetUpdater` (`widget/WidgetUpdater.kt`): `suspend fun refresh() = CommitWidget().updateAll(context)`; called by repositories after writes so app edits also redraw the widget.

### Quick-add popup — `QuickAddActivity`
- Separate `ComponentActivity` with a translucent dialog theme in `res/values/themes.xml` (`windowIsTranslucent`, no title, dim background), manifest flags `excludeFromRecents="true"`, `taskAffinity=""`, `launchMode="singleInstance"`, `windowSoftInputMode="adjustResize|stateVisible"`.
- Compose card: autofocused title field, date/time chips (pickers), commitment dropdown defaulting to the extra; Save → `TodoRepository.add` → `finish()`; tapping outside / back = cancel. Keep it light (no DB read before first frame beyond the commitment list) to hit the <500 ms target.

## 5. Tests
- Unit (`app/src/test`): `DueFormatter` cases (today/tomorrow/overdue/no date/with and without time, fixed `Clock`), sort-order rule.
- Instrumented (`app/src/androidTest`): in-memory Room tests for `TodoDao.observeOpenForCommitment` ordering and filtering (deleted, done, other commitment), `CommitmentDao` ordering/archiving, repository write rules (dirty/updatedAt/soft delete). Replace the template `ExampleUnitTest`/`ExampleInstrumentedTest`.

## Critical files
- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`, `res/values/themes.xml`, `res/xml/commit_widget_info.xml`, `res/values/strings.xml`
- `app/src/main/java/dev/yxuan/commit/CommitApp.kt`, `AppContainer.kt`, `MainActivity.kt`
- `data/db/AppDatabase.kt`, `data/db/entity/*`, `data/db/dao/*`, `data/repo/*`
- `ui/commitments/*`, `ui/todos/*`, `ui/format/DueFormatter.kt`
- `widget/CommitWidget.kt`, `widget/CommitWidgetReceiver.kt`, `widget/actions/*`, `widget/WidgetUpdater.kt`, `quickadd/QuickAddActivity.kt`

## Verification
1. `./gradlew :app:assembleDebug` builds; `app/schemas/.../1.json` is generated with all tables and indexes.
2. `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest` (emulator/device API 31+) pass.
3. Manual on device/emulator (`./gradlew installDebug`):
   - Create Study/Work/Project A commitments, rename, recolour, reorder, archive one; add todos with/without dates and times; edit, move, delete, tick/untick.
   - Add the widget (4x2), switch tabs without the app opening, tick a todo and see it vanish in <1 s, resize taller and see more rows.
   - "+ Add" from the widget: popup over the home screen, commitment preselected to current tab, Save returns to home screen and the new todo appears on the widget.
   - Airplane mode on throughout — everything still works.
   - Edit a todo in the app → widget updates.

## Open PRD questions — not blocking M1/M2
Widget theming, planned-workout source for the morning recap, workout-tab idle state, sync build-vs-PowerSync, web framework. These are decided before M4/M5/M7/M8.


---

# Plan: Commit — M3 (Recurring + reminders) + M4 (Workout)

## Context
M1 (Room + app screens) and M2 (Glance widget, quick-add) are done and verified (see `app/RESULTS.md`). The next two PRD milestones are:
- **M3:** recurring chores that reset themselves, completion history, exact-alarm reminders, notification channels; also fixes the M2 limitation that "Today/Overdue" labels go stale at midnight.
- **M4:** workout routines editor, workout tab on the widget with steppers, auto-advancing rest/work timers with a live Chronometer, alert on timer end, ongoing lock-screen notification.

Decisions made with the user:
- Chore reset: **start of the due day** — next due date = local date of `last_done_at` + N days; it reappears at that midnight. The "chore due" notification fires at the morning recap time (default 07:30) that day, not at midnight.
- Workout tab with no active session: **routine picker** (each routine with Start).
- Exact alarms: **`USE_EXACT_ALARM`** (auto-granted). Still guard with `canScheduleExactAlarms()` and fall back to inexact.
- Weights in **kg, ±2.5** steppers (reps ±1).

Existing code to build on: `TodoRepository`/`CommitmentRepository` (`data/repo/`), `DataChangeListener` (hook for alarms), `AppContainer` + `DeviceClock`, `DueFormatter` (`ui/format/`), `CommitWidget` + `WidgetActions` + `WidgetUpdater` (`widget/`), `TodoForm` (`ui/todos/`), stub DAOs in `data/db/dao/LaterDaos.kt`, entities in `data/db/entity/Entities.kt`.

---

## M3 — Recurring todos, alarms, notifications

### 3.1 Recurrence rules (pure Kotlin) — `domain/Recurrence.kt`
- `nextDueDate(todo, zone): LocalDate?` — recurring: `lastDoneAt` date + `recurIntervalDays`; never done → `dueAt` date, or today if no due date.
- `isDone(todo, now, zone)` — one-off: `doneAt != null`; recurring: `today < nextDueDate` (F4.4, calculated, never stored).
- `effectiveDue(todo)` → (epoch ms, hasTime) used for sorting and `DueFormatter` labels, so a chore shows "Today" / "Overdue · 2d" / "Fri" (F3.5).
- Unit-tested with fixed `Clock`s across month ends and DST.

### 3.2 Data
- `TodoDao`: open-list queries keep `done_at IS NULL` (recurring rows never set `done_at`) but return recurring rows too; the **calculated** done/sort is applied in Kotlin via `Recurrence` (repository maps to a `TodoItem(todo, due, isDone)` list). The SQL `ORDER BY` stays for one-off todos; final sort by `effectiveDue` in Kotlin.
- `CompletionDao`: `latestForTodo`, `softDelete`, `maxDoneAt(todoId)`; observe by day for later history (M6).
- `TodoRepository.setDone(id, done)` becomes recurrence-aware inside `db.withTransaction`:
  - recurring tick → insert `CompletionEntity` (dirty) and set `last_done_at = now`;
  - recurring untick → soft-delete the latest completion and recompute `last_done_at = max(remaining)` (F4.5);
  - one-off unchanged. Every completion is logged for one-off todos too (gives one history source for M6).
- `add/update` take `recurIntervalDays: Int?`. `TodoForm` gets a "Repeat every [N] days" toggle + number field (F4.1, F4.2).
- No schema change needed in M3 (columns already exist).

### 3.3 Notification channels — `notify/Notifications.kt`
Created in `CommitApp.onCreate`: **Reminders** (default importance), **Workout** (low, silent, for the ongoing notification), **Workout alerts** (high, sound + vibration, timer end), **Recaps** (created now, used in M5). (F7.5)
- `POST_NOTIFICATIONS` runtime request from `MainActivity` on first launch; a small banner on the commitments screen if denied.

### 3.4 Alarms — `alarm/AlarmScheduler.kt`, `alarm/AlarmReceiver.kt`
- `AlarmScheduler` wraps `AlarmManager`. One `PendingIntent` per (kind, id) with a stable request code; kinds: `TODO_DUE`, `CHORE_DUE`, `MIDNIGHT`, `WORKOUT_TIMER` (M4).
  - Reminders: `setExactAndAllowWhileIdle`; workout timers: `setAlarmClock` (best Doze accuracy). If `!canScheduleExactAlarms()` → `setWindow` fallback.
- `rescheduleTodos()`: reads all non-deleted todos (small data set), cancels then re-sets:
  - one-off with time, undone, due in future → `TODO_DUE` at `due_at` (F7.1). Date-only → none (goes to morning recap in M5, F7.2).
  - recurring → `CHORE_DUE` at next due date + morning recap time (from `settings` row, default 07:30).
  - deleted/done → cancel.
- `MIDNIGHT` alarm: daily at next local midnight → refresh widget (fixes stale labels; resurfaces chores) and re-arm.
- Hooked into `AppContainer`'s `DataChangeListener` (after widget refresh), so every local change reschedules (PRD "Alarm scheduling").
- `AlarmReceiver` (not exported): builds the notification from Room at fire time (title, due label, tap → `MainActivity` with `todo_id`), "Done" action → `NotificationActionReceiver` marks done (F7.4 partial; Snooze left for later), refreshes the widget. Uses `goAsync()` + coroutine for DB work.
- `BootReceiver`: `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET` → `rescheduleTodos()`, re-arm midnight, restore any running workout timer (M4), refresh widget.
- Manifest: `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`; receivers registered.

### 3.5 UI touches
- Detail screen and widget use `TodoItem` (calculated done) — a ticked chore disappears until its next due day; detail screen shows "Repeats every 4 days · next Fri" under it.
- Done section shows completed one-off todos; chores show a small "Done today" state.

---

## M4 — Workout mode

### 4.1 Data
- Expand `RoutineDao`/`ExerciseDao` (observe/get/upsert/softDelete/reorder), `WorkoutSessionDao` (active session, end), `WorkoutSetDao` (insert, observe for session).
- **Phone-only live state** table `active_workout` (single row, not synced): `sessionId, routineId, exerciseIndex, setNo, phase (READY|WORK|REST|COMPLETE), phaseEndsAt?, reps, weight, updatedAt`. Database **version 2** via Room `@AutoMigration(from = 1, to = 2)`; re-enable `androidTest` schema assets and add a `MigrationTestHelper` test.
- `RoutineRepository` (routines + exercises CRUD, sort order like commitments) and `WorkoutRepository` (start/doneSet/adjust/addRest/skip/end; all writes through the engine, dirty flags on synced rows, then `DataChangeListener`).

### 4.2 Workout engine (pure Kotlin) — `domain/WorkoutEngine.kt`
State machine over `(state, exercises, now)`; everything is derived from timestamps so a late alarm never shows wrong state (PRD "Timers and state"):
- `resolve(state, now)`: while a timed phase has ended, advance — WORK(timed) → log set → REST; REST → next set READY (or WORK for timed exercises, hands-free F5.5); after the last set → longer `rest_between_exercises_s` → next exercise; after the last exercise → COMPLETE.
- `doneSet(state, now)` (logs actual reps/weight, F5.3), `adjustReps(±1)`, `adjustWeight(±2.5, ≥0)`, `addRest(+30s)`, `skipRest`, `end`.
- Reps/weight prefill from the exercise targets when a new exercise starts; edits carry to the next set of the same exercise.
- Fully unit-tested (sequence of sets, timed exercise, last set/last exercise, late resolve, skip/+30s).

### 4.3 Timers & alerts
- On any phase with `phaseEndsAt`: `AlarmScheduler.setWorkoutTimer(phaseEndsAt)` via `setAlarmClock` (within 2 s in Doze, NF).
- `WorkoutTimerReceiver`: `resolve` + persist, post a **Workout alerts** notification (sound + vibration, auto-cancel) (F5.6), arm the next timer if the new phase is timed, refresh widget + ongoing notification.

### 4.4 Widget workout tab (F5.2–F5.6)
- No active session → routine picker rows: name + exercise count + "Start" (`StartWorkoutAction`).
- Active → exercise name, "Set 3 of 4", reps `[−] 8 [+]`, weight `[−] 60 kg [+]`, big "Done set" (READY) — or the countdown during REST/WORK with "+30s" / "Skip", and "End" link. COMPLETE → "Workout complete · 42 min · 3,450 kg" + Done.
- Countdown: `AndroidRemoteViews` with a layout containing a `Chronometer`; `setChronometer(base = elapsedRealtime + (endsAt − now), started = true)` + `setChronometerCountDown(true)` — ticks smoothly with no widget updates.
- Actions in `widget/WorkoutActions.kt` (`StartWorkoutAction`, `StepRepsAction`, `StepWeightAction`, `DoneSetAction`, `AddRestAction`, `SkipRestAction`, `EndWorkoutAction`) → `WorkoutRepository`.
- `CommitWidget` also collects the `active_workout` flow; compact layout at 4×2, roomier when taller.

### 4.5 Ongoing notification (F5.7)
- `WorkoutNotification`: ongoing, **Workout** channel, `VISIBILITY_PUBLIC` (lock screen), `setUsesChronometer(true)` + `setChronometerCountDown(true)` + `setWhen(phaseEndsAt)` during timers; actions "Done set", "+30s", "Skip rest" (via `NotificationActionReceiver`). Posted on start, updated on every state change, removed on end. No foreground service needed — alarms carry the timing.

### 4.6 App screens
- Workout commitment detail (replaces the M1 placeholder): routines list, add/rename/reorder/delete, "Start" button.
- Routine editor: name, rest between exercises; exercise list with add/edit dialog (name, sets, reps, weight, rest s, "Timed" toggle + duration s), reorder up/down, delete (F5.1).
- Live workout screen in the app mirroring the widget (useful for testing and when the widget isn't placed).
- End / abandon (F5.8): End saves `ended_at`; ending with zero sets soft-deletes the session.

---

## Critical files
- New: `domain/Recurrence.kt`, `domain/WorkoutEngine.kt`, `alarm/AlarmScheduler.kt`, `alarm/AlarmReceiver.kt`, `alarm/BootReceiver.kt`, `alarm/WorkoutTimerReceiver.kt`, `notify/Notifications.kt`, `notify/WorkoutNotification.kt`, `notify/NotificationActionReceiver.kt`, `data/repo/RoutineRepository.kt`, `data/repo/WorkoutRepository.kt`, `widget/WorkoutActions.kt`, `res/layout/widget_chronometer.xml`, `ui/workout/*`
- Modified: `data/db/AppDatabase.kt` (v2, auto-migration), `data/db/entity/Entities.kt` (`ActiveWorkoutEntity`), `data/db/dao/*`, `data/repo/TodoRepository.kt`, `AppContainer.kt`, `CommitApp.kt`, `MainActivity.kt`, `widget/CommitWidget.kt`, `ui/todos/TodoForm.kt` + detail screen, `AndroidManifest.xml`, `app/build.gradle.kts` (androidTest schema assets)

## Verification
1. `./gradlew :app:assembleDebug`; `app/schemas/.../2.json` generated.
2. Unit tests: `Recurrence` (start-of-day reset, untick recompute inputs, DST), `WorkoutEngine` (full routine walk, timed exercises, late alarms, +30s/skip, completion volume).
3. Instrumented: recurring tick/untick with completions (`last_done_at` recomputed), migration 1→2 (`MigrationTestHelper`), workout repository start → sets logged with actual values → session ended.
4. Emulator, manual:
   - Chore "every 1 day": tick → disappears; `adb shell cmd alarm` / advance clock past midnight → reappears, notification at 07:30.
   - Todo due in 2 min → notification on time; "Done" action ticks it; screen off + `adb shell dumpsys deviceidle force-idle` → still fires.
   - Reboot emulator → `dumpsys alarm | grep dev.yxuan.commit` shows alarms restored.
   - Build "Push Day" (bench 3×8 @60, rest 60 s; plank 2×30 s). From the widget: Start → steppers → Done set → Chronometer counts down → vibrate/sound → next set; plank runs hands-free; last set → longer rest → complete summary. Repeat from the lock-screen notification (+30s, Skip rest).
   - Doze: rest timer end alert within ~2 s with screen off.
5. Append M3/M4 sections to `app/PLAN.md` and results to `app/RESULTS.md`.


---

# Plan: Commit — M5 (Recaps) + M6 (History) + M7 (Sync)

## Context
M1–M4 are done and verified (`app/RESULTS.md`): Room data layer, widget, recurring chores + exact-alarm reminders, workout mode. Next three PRD milestones:
- **M5:** morning and night recap notifications built from Room when they fire (F6), a settings screen, and the "planned workout" for the morning recap.
- **M6:** history — task timeline, heatmap, per-commitment stats, workout sessions with editable past sets, per-exercise progress charts (F8).
- **M7:** sync with Supabase, phone works offline and syncs both ways (PRD "Sync").

Decisions made with the user:
- Planned workout = **weekdays per routine** (chips in the routine editor), DB v3 column.
- Charts with the **Vico** library (line/column charts). Vico has no calendar heatmap, so the heatmap is a small Compose grid.
- Sync with **PowerSync** (not hand-written). PowerSync's Room integration (beta) requires **Room 3.0**, a bundled SQLite driver and **raw tables** with CRUD triggers; Supabase stays the backend.
- Accounts not created yet: build M7 **ready to run**, keys read from `local.properties` (never committed); with no keys, sync is off and the app works exactly as now. A setup guide walks through creating Supabase, PowerSync and Firebase projects.

Existing code to reuse: `AlarmScheduler` + `AlarmReceiver`/`BootReceiver` (`alarm/`), `Notifications` (`notify/`), `Recurrence`/`TodoItem` (`domain/`), `DueFormatter`/`WorkoutFormat` (`ui/format/`), repositories in `data/repo/`, `DataChangeListener` in `AppContainer`, `SettingsDao` (`data/db/dao/LaterDaos.kt`), Glance `WidgetUpdater`.

---

## M5 — Recaps + settings

### 5.1 Data (DB v3)
- `routines.planned_days INTEGER NOT NULL DEFAULT 0` — weekday bitmask (Mon = 1 … Sun = 64). `@AutoMigration(2, 3)`.
- `SettingsRepository`: `observe()`, `get()` returning defaults when no row; `update(...)` creates the single row on first write (dirty/updated_at rules as other repos). After a change: reschedule recap + chore alarms.
- New queries: completions done in a time range joined with todo/commitment (`CompletionDao`), sessions ended in a range with set totals (`WorkoutSessionDao`), routines planned for a weekday.

### 5.2 Recap builder (pure) — `domain/Recap.kt`
`RecapBuilder(clock)` takes plain lists and returns `Recap(kind, title, sections: List<Section(title, lines)>, isEmpty)`:
- **Morning (F6.1):** overdue todos, due today (timed and date-only, F7.2), chores due today (`Recurrence`), today's planned routines ("Today: Push Day").
- **Night (F6.2):** completed today per commitment (from `completions`), still open/overdue, due tomorrow, today's workout summary (duration, volume via `WorkoutEngine.volume`).
- Unit-tested with fixed clocks.

### 5.3 Alarms + notification
- `AlarmKind.MORNING_RECAP` / `NIGHT_RECAP`: `setExactAndAllowWhileIdle` at the next occurrence of the settings time; re-armed after firing, on settings change, boot/time-zone change (add to `BootReceiver` and `CommitApp.onCreate`).
- `AlarmReceiver` builds the recap from Room **at fire time** (F6.3), skips if empty or disabled (F6.5), posts on the **Recaps** channel with `InboxStyle` (up to 6 lines + "+N more", F6.4); tap opens the recap screen.

### 5.4 UI
- **Settings screen** (gear in the commitments top bar): morning/night time pickers + on/off switches; note that chore reminders use the morning time. In M7 it also gets the Account section.
- **Recap screen** (`RecapRoute(kind)`): the full recap built live, sections as lists.
- **Routine editor:** weekday chips for `planned_days`; routines list shows "Mon · Thu".

---

## M6 — History

### 6.1 Data
- Queries: completions with todo title + commitment for a date range and optional commitment filter; completions per day (heatmap counts); sessions with routine name, duration, volume, set count; sets for a session with exercise names; per-exercise-name series (best weight + volume per session).
- `HistoryRepository` with these flows; `WorkoutRepository.editSet(id, reps, weight)` / `deleteSet(id)` (soft) for F8.5.

### 6.2 Pure calculations — `domain/Stats.kt`
- Per commitment: completed this week; on time vs late (completion `done_at` vs the due date at the time — `due_at` for one-off, previous next-due for chores); recurring streak (consecutive completions each within its interval). Unit-tested.
- Personal bests: running max of best weight per exercise; PB flags on sessions.

### 6.3 UI — History screen (history icon in the commitments top bar), three tabs
- **Tasks (F8.1–F8.3):** heatmap of the last 26 weeks (Compose grid, 5 colour steps from the commitment-neutral primary; tap a day to filter), commitment filter chips, stats cards, timeline grouped by day.
- **Workouts (F8.4, F8.5):** session list (date, routine, duration, volume) → session detail: sets grouped by exercise; tap a set to edit reps/weight; delete.
- **Progress (F8.6):** exercise picker (by name across routines) → Vico line chart of best weight per session with PB markers, column chart of volume; list of PBs.
- Dependency: `com.patrykandpatrick.vico:compose-m3` (latest stable 2.x at implementation time).

---

## M7 — Sync with PowerSync + Supabase

### 7.1 Room 3 upgrade (first, on its own)
- Move from Room 2.8 to **Room 3.0** (check package/artifact names and API changes in the Room 3 release notes; keep KSP). Use `BundledSQLiteDriver` (`androidx.sqlite:sqlite-bundled`) with `loadPowerSyncExtension()`.
- Migration DB v4 via auto-migration: **drop the `dirty` columns** (PowerSync tracks pending uploads in its own queue) and remove `dirty` from entities, DAOs and repositories. `active_workout` stays local-only.
- All existing unit + instrumented tests must pass on Room 3 before going further.
- Fallback if Room 3 blocks: stay on Room 2.8 with the last `integration-room` release before 1.14.1 (which supports Room 2.x).

### 7.2 PowerSync client — `sync/`
- Artifacts: `com.powersync:core`, `com.powersync:integration-room`, `com.powersync:connector-supabase` (+ supabase-kt auth, pulled in by the connector).
- `SyncSchema`: one `RawTable` per synced table (commitments, todos, completions, routines, exercises, workout_sessions, workout_sets, themes, settings) with explicit `put`/`delete` statements.
- CRUD triggers via `powersync_create_raw_table_crud_trigger` for each table, created in a Room open callback (re-created after future migrations).
- `PowerSyncDatabase.opened(pool = RoomConnectionPool(db, schema), …)` — opened always (so writes are queued even while signed out); `connect(connector)` only when signed in and keys present.
- **Backfill**: on first sign-in, queue a `PUT` for every existing row (rows written before triggers existed would otherwise never upload). One-time flag.
- **Settings row**: on sign-in, re-key the local settings row to `settings-<user_id>` so phone and web share one row.
- After each completed download (watch `currentStatus`): redraw widget + `AlarmScheduler.rescheduleTodos()` + recaps (PRD rule 7).
- `SyncConfig` from `BuildConfig` fields read from `local.properties` (`supabase.url`, `supabase.anonKey`, `powersync.url`, `google.webClientId`); missing → sync disabled, nothing connects.

### 7.3 Auth (Google sign-in)
- Credential Manager (`androidx.credentials` + `googleid`) → Google ID token → `supabase.auth.signInWith(IDToken) { provider = Google }`.
- Settings → **Account** section: Sign in with Google / signed-in email / Sign out (disconnect + keep local data); sync status (connected, last synced, pending uploads).

### 7.4 Background sync + push
- `SyncWorker` (WorkManager): connect, wait until uploads are done and the download is complete (or 45 s), refresh widget/alarms, disconnect when the app isn't in the foreground.
  - One-off expedited job after every local write (network required) — keeps phone → web within seconds even from the widget (NF).
  - Periodic every 15 min (PRD rule 6).
- **FCM**: `CommitMessagingService` stores the token in a `device_tokens` table (synced, own rows only) and enqueues `SyncWorker` on a data message. The Google Services plugin is applied only when `app/google-services.json` exists, so the build works without Firebase.
- Supabase **Edge Function** `notify-sync` (TypeScript/Deno): called by a database webhook on changes, sends an FCM HTTP v1 data message to the user's tokens (PRD rule 7).

### 7.5 Backend files (in repo, applied by the user)
- `supabase/migrations/0001_init.sql`: tables mirroring Room (same names/columns, `id text primary key`), `updated_at` trigger (server clock), `user_id` forced to `auth.uid()` on insert/update by trigger, RLS `user_id = auth.uid()` on every table, PRD indexes, `device_tokens`, publication for PowerSync.
- `powersync/sync-config.yaml`: per-user streams (`WHERE user_id = auth.user_id()`) for every synced table.
- `supabase/functions/notify-sync/index.ts` + webhook setup notes.
- `app/SYNC_SETUP.md`: step-by-step: create Supabase project, enable Google provider, Google Cloud OAuth clients (web + Android SHA-1), PowerSync instance connected to Supabase, Firebase project + `google-services.json`, service-account secret for the Edge Function, fill `local.properties`.

### 7.6 Not in M7
- 12-month local retention: deleting old local history would fire the delete triggers and delete it on the server too; needs a local-only purge path. Left for later.
- Web app (M8).

---

## Critical files
- M5: `data/db/entity/Entities.kt` (planned_days), `data/db/AppDatabase.kt` (v3), `data/repo/SettingsRepository.kt`, `domain/Recap.kt`, `alarm/AlarmScheduler.kt`, `alarm/Receivers.kt`, `notify/RecapNotification.kt`, `ui/settings/*`, `ui/recap/*`, `ui/workout/RoutineEditorScreen.kt`
- M6: `data/repo/HistoryRepository.kt`, `domain/Stats.kt`, `ui/history/*` (tasks, workouts, session detail, progress), `gradle/libs.versions.toml` (Vico)
- M7: `gradle/libs.versions.toml` + `app/build.gradle.kts` (Room 3, sqlite-bundled, PowerSync, credentials, WorkManager, Firebase), all entities/DAOs/repos (drop `dirty`), `sync/*` (schema, connector, SyncManager, SyncWorker, MessagingService), `ui/settings/AccountSection.kt`, `supabase/**`, `powersync/sync-config.yaml`, `app/SYNC_SETUP.md`, `AndroidManifest.xml`

## Verification
1. Each milestone: `assembleDebug`, all unit tests, instrumented tests (incl. `MigrationTestHelper` 2→3 and 3→4) pass.
2. M5 unit: `RecapBuilder` morning/night contents, empty → skipped; planned-day matching. Manual: set recap time 2 min ahead → InboxStyle notification on time with correct lines; tap → recap screen; toggle off → nothing; reboot → recap alarm restored (`dumpsys alarm`).
3. M6 unit: stats (this week, on time vs late, streaks), PB detection. Manual: complete todos/chores and two workouts → timeline, heatmap, stats, session list; edit a past set → volume updates; progress chart shows PB marker.
4. M7 without keys: app runs, Settings shows "Sync not configured", everything offline as before, no network calls. With keys (after the user follows `SYNC_SETUP.md`): sign in → existing data appears in Supabase tables; edit in the Supabase table editor → phone updates within ~1 min (FCM) and widget/alarms refresh; phone edit → row in Supabase within ~10 s; airplane mode edits sync on reconnect.
5. Use a second emulator (or reinstall + re-add widget) for manual checks, since `connectedDebugAndroidTest` uninstalls the app.
6. Append M5–M7 to `app/PLAN.md` and results to `app/RESULTS.md`.


---

# Plan change: M7 without PowerSync (2026-11-21)

**Why:** to avoid a paid service. PowerSync is removed; Google sign-in stays (free on Supabase); sync goes back to the PRD's original hand-written design, straight to Supabase's free tier.

- DB v5 brings back the phone-only `dirty` flag (default 1, so rows created before sync upload on the first sync); every local write sets it again.
- `sync/SyncEngine.kt`: push dirty rows (PostgREST upsert), mark clean only if unchanged during the upload; pull rows with server `updated_at > last_synced_at` per table (paged, with a 5-minute overlap window), never overwriting rows with unpushed edits. Rows travel as JSON built and applied with SQLite JSON functions, so the engine is table-agnostic.
- `SyncManager`: Google sign-in → Supabase session, one sync at a time, repeats while changes are pending; WorkManager after writes / every 15 min / on FCM ping; widget + alarms refresh when rows were pulled.
- Kept: Room 3, Google sign-in, Account section, FCM + `notify-sync` Edge Function, RLS schema (regenerated from v5 without `dirty` and without the PowerSync publication).
- Removed: PowerSync dependencies, `powersync/sync-config.yaml`, PowerSync tests and setup steps.


---

# Plan: Commit — M8 (Web app)

## Context
M1–M7 are done: the Android app works offline and syncs with Supabase (hand-written sync, verified end to end, see `app/RESULTS.md`). M8 is the PC side from the PRD: a web app with the same data and rules, live updates when the phone changes something, and history. Web edits already reach the phone through the existing webhook → `notify-sync` Edge Function → FCM ping.

Decisions made with the user:
- **Next.js (React)**, built as a **static single-page app** (`output: "export"`): the browser talks to Supabase directly (protected by RLS), no server code.
- **Free hosting on Cloudflare Pages** (direct upload with Wrangler; no card).
- **Full scope**: sign-in (F10.1), manage commitments / todos / chores / routines with the same rules as the app (F10.2), live updates (F10.3), history and progress charts (F10.4 / F8.7).
- Code in **`web/`** in this repo.

Everything stays on free tiers (Supabase Free, Cloudflare Pages Free, Google sign-in).

## 1. Backend additions — `supabase/migrations/0003_web.sql`
- **Realtime**: add the 9 synced tables to the `supabase_realtime` publication (RLS still applies to change events).
- **`toggle_todo(todo_id text, done boolean)`** SQL function (security invoker, so RLS applies) doing what the phone's `TodoRepository.setDone` does in one transaction: log/soft-delete a `completions` row; one-off → `done_at`; recurring → `last_done_at` (recomputed from remaining completions on untick). Keeps web and phone history identical.
- Supabase Auth URL config (dashboard, documented): Site URL + redirect URLs for `http://localhost:3000` and the Pages domain.

## 2. Project setup — `web/`
- Next.js (App Router, TypeScript, latest stable at implementation time), Tailwind CSS, `@supabase/supabase-js`, `@tanstack/react-query`, `recharts`; Vitest for unit tests.
- `next.config` with `output: "export"`; dynamic pages use query strings (`/routine?id=…`) so no server is needed.
- `web/.env.local` (git-ignored): `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` (public by design; RLS protects data). Values come from the same keys as `local.properties`.

## 3. Shared rules ported to TypeScript — `web/lib/domain/`
Straight ports of the Kotlin code, with the same unit-test cases (Vitest), so phone and web behave the same:
- `recurrence.ts` ← `domain/Recurrence.kt` (start-of-day reset, `isDone`, `nextDueDate`, `effectiveDue`, open-list order)
- `due.ts` ← `ui/format/DueFormatter.kt` (Today / Tomorrow / weekday / Overdue · 2d)
- `stats.ts` ← `domain/Stats.kt` (this week, on time vs late, chore streaks, per-day counts, personal bests)
- `weekdays.ts`, `format.ts` (weight, volume, durations) ← `Weekdays.kt`, `WorkoutFormat.kt`
- `types.ts`: row types mirroring the Postgres tables (booleans as 0/1, times as epoch ms).

## 4. Data layer — `web/lib/data/`
- `supabase.ts`: browser client (PKCE, session persisted in localStorage).
- `queries.ts`: one React Query per table (`select * where deleted = 0` for live views; history queries by date range). Data volume is one person's, so lists load whole.
- `realtime.ts`: one channel per signed-in user subscribing to `postgres_changes` on all 9 tables → patch/invalidate the matching query, so phone edits appear within a second or two (F10.3).
- `mutations.ts`: writes with the app's rules: client UUIDs (`crypto.randomUUID()`), soft deletes, `sort_order` reorders, `toggle_todo` RPC for ticks. `user_id` / `updated_at` are set by the existing server triggers, and the webhook pings the phone.

## 5. Pages (all client components)
- `/login`: "Sign in with Google" (`signInWithOAuth`, redirect back to the app).
- `/` **Todos** (main view): commitments sidebar (colour dot, open count; create / rename / recolour / reorder / archive / type / show-on-widget), selected commitment's open todos in widget order with due labels (overdue red + label, today bold), done section collapsed, add form (title, date, optional time, repeat every N days), inline edit, move, delete, tick/untick.
- `/workouts`: routines of workout commitments with exercise counts and planned days; `/routine?id=`: routine editor (name, rest between exercises, planned weekdays, exercises: sets, reps, weight, rest, timed + seconds, reorder, delete).
- `/history`: tabs **Tasks** (26-week heatmap, commitment filter, stats cards, timeline by day), **Workouts** (sessions: date, routine, duration, volume, sets → `/session?id=` with editable/deletable sets, F8.5), **Progress** (per-exercise best weight line with PB markers + volume bars, Recharts). Unlike the phone, history isn't limited to 26 weeks (PRD F8.7: web reads older data).
- `/settings`: recap times and toggles (the shared `settings-<user id>` row), signed-in account, sign out.
- Layout: top nav (Todos, Workouts, History, Settings), responsive, light/dark following the system (full themes are M9).

## 6. Deploy — Cloudflare Pages
- `npm run build` → `web/out/`; `npx wrangler pages deploy out --project-name commit` (user logs in to Cloudflare once with `npx wrangler login`).
- Add the `*.pages.dev` URL to Supabase Auth redirect URLs.
- `web/README.md`: run locally, test, deploy, Supabase/Cloudflare settings.

## Critical files
- New: `supabase/migrations/0003_web.sql`, `web/` (Next.js project: `app/**/page.tsx`, `lib/domain/*.ts` + tests, `lib/data/*.ts`, `components/*`), `web/README.md`
- Updated: `app/SYNC_SETUP.md` (web section), `app/PLAN.md`, `app/RESULTS.md`
- Reused as the source of truth for ports: `domain/Recurrence.kt`, `ui/format/DueFormatter.kt`, `domain/Stats.kt`, `domain/Weekdays.kt`, `ui/format/WorkoutFormat.kt`, `data/repo/TodoRepository.kt` (setDone rules), `supabase/migrations/0001_init.sql` (column names/types)

## Verification
1. `npm run test` (Vitest): ported rule tests mirror the Kotlin cases (recurrence, due labels, stats, PBs).
2. `npm run build` produces a static `out/`; `npm run lint` / type check clean.
3. Local (`npm run dev`), signed in with the same Google account:
   - The phone's data appears (Sync test / Water plants…).
   - Create a todo, tick a chore, edit a routine on the web → appears on the phone within about a minute (FCM ping) and the widget redraws.
   - Tick on the phone → the web list updates live within a couple of seconds without reload (Realtime).
   - History: timeline, heatmap, sessions, editing a past set updates volume; progress chart with PB markers.
   - A second Google account sees none of this data (RLS).
4. Deploy to Cloudflare Pages; repeat sign-in and one live-update check on the deployed URL.
5. Append M8 to `app/PLAN.md` and results to `app/RESULTS.md`.


---

# Plan: Commit — M9 (Themes)

## Context
M1–M8 are done: the Android app (widget, recurring chores, workouts, recaps, history), hand-written sync with Supabase, and the web app live at https://commit.yxuan.dev. M9 is the last PRD milestone: themes (F9.1–F9.4). Today the app still uses the template's purple theme / Material You, the widget uses Glance defaults, and the web has fixed colour tokens in `web/app/globals.css`.

Decisions made with the user:
- **Full scope**: theme format + import + picker + built-ins on the phone (F9.1–F9.3), **and** synced themes + the web app using the same theme as CSS variables (F9.4).
- **The widget follows the theme** (open PRD question).
- **A theme sets** light and dark colour sets, a corner radius and a font family from a fixed list (`default`, `serif`, `mono`, `rounded`). Design values only, no code (F9.1).

The database is ready: `themes(id, name, json)` and `settings.active_theme_id` exist on phone and server and are already synced (`SyncEngine.TABLES` includes `themes`). No schema change.

## 1. Theme format (shared) — `themes/`
- `themes/theme.schema.json` (JSON Schema, for people writing themes) and `themes/README.md` (fields, example, how to import).
- Format v1:
  ```json
  { "schemaVersion": 1, "name": "Ocean",
    "light": { "primary": "#1E5AA8", "onPrimary": "#FFFFFF", "primaryContainer": "…", "onPrimaryContainer": "…",
               "secondary": "…", "onSecondary": "…", "secondaryContainer": "…", "onSecondaryContainer": "…",
               "background": "…", "onBackground": "…", "surface": "…", "onSurface": "…",
               "surfaceVariant": "…", "onSurfaceVariant": "…", "outline": "…", "error": "…", "onError": "…" },
    "dark": { …same keys… },
    "shape": { "cornerRadius": 16 },
    "font": "rounded" }
  ```
- Rules (identical on phone and web): `schemaVersion` must be 1; `name` 1–40 chars; colours `#RRGGBB` or `#AARRGGBB`; `cornerRadius` 0–32; `font` one of the four; file ≤ 32 KB; unknown keys ignored. **Missing values are filled from the default theme** (F9.2); a theme with only `light` gets the default dark set. Invalid files are rejected with a list of clear messages ("light.primary: '#12345' is not a colour like #1E5AA8").
- `themes/examples/`: 3 example themes (Ocean, Forest, High contrast), also served by the web app at `https://commit.yxuan.dev/themes/*.json`, so they double as import-by-link samples. Both test suites run against these files.

## 2. Built-in themes
- **Material You** (`builtin:dynamic`, default = today's behaviour): wallpaper colours on the phone; on the web (no wallpaper colours) it uses the Default palette.
- **Light** (`builtin:light`) and **Dark** (`builtin:dark`): Commit's own palette, forced light / forced dark.
- Imported themes follow the system light/dark setting using their two colour sets.
- `settings.active_theme_id`: built-in id, an imported theme's id, or null (= Material You). It lives in the synced settings row, so phone and web use the same theme.

## 3. Android
- `domain/theme/ThemeSpec.kt`: data classes + `ThemeParser.parse(json): Result<ThemeSpec>` with validation and default filling (kotlinx.serialization `JsonElement`, so messages can name the field). Unit tests: valid/invalid/partial/fill-from-default, built-ins, the example files.
- `data/repo/ThemeRepository.kt`: `observeThemes()`, `observeActive(): Flow<ActiveTheme>` (settings + themes → built-in or parsed spec, falling back to Material You if the theme was deleted or no longer parses), `import(json)` (validate → insert `themes` row, dirty), `importFromUrl(url)` (https only, 32 KB cap, 10 s timeout), `select(id)`, `delete(id)` (soft; if active, fall back to Material You). Writes go through the change listener, so they sync, and the widget redraws.
- `ui/theme/Theme.kt`: `CommitTheme(active)` builds the Material 3 `ColorScheme` from the spec (or dynamic / forced light / dark), `Shapes` from the radius, and `Typography` with the font family (`serif` → Serif, `mono` → Monospace, `rounded` → bundled **Nunito** variable font in `res/font`, OFL-licensed). `MainActivity` and `QuickAddActivity` collect `observeActive()`.
- **Settings → Theme** screen (`ui/settings/ThemeScreen.kt`, F9.3): cards for each built-in and imported theme with a live preview (primary button, card, checkbox, chip, text samples in that theme's colours, radius and font), tap to apply, delete imported ones; **Import from file** (Storage Access Framework, `application/json`) and **Import from link** (URL field); validation errors shown in plain words.
- **Widget**: `CommitWidget` collects the active theme; `GlanceTheme(colors = ColorProviders(light, dark))` built from the spec (Material You = `GlanceTheme.colors` dynamic default; Light/Dark = the same scheme for both). Theme changes redraw all widgets.

## 4. Web
- `web/lib/domain/theme.ts`: port of the parser/validation/defaults with the same tests (Vitest, same example files).
- `web/components/ThemeProvider.tsx`: reads `settings.active_theme_id` + `themes` (React Query, with Realtime already live), writes CSS variables for light and dark (a `<style>` with `:root` + `@media (prefers-color-scheme: dark)`, or one forced set), mapping the spec onto the existing tokens (`--bg`, `--surface`, `--surface-2/3`, `--line`, `--fg`, `--fg-muted`, `--primary`, `--primary-soft`, `--on-primary`, `--danger`) plus new `--radius` and `--font`. Components switch their fixed `rounded-*` classes to the radius variable; Nunito is loaded via `next/font` for `rounded`.
- `web/lib/domain/types.ts`: add `Theme` / `themes` to the cached tables (and Realtime).
- **Settings → Theme** section: same list with previews, apply, delete, import from file (`<input type=file>`) or link (fetch; works for links that allow cross-origin reads, such as raw GitHub/Gist and our own `/themes/*.json`).
- `web/public/themes/`: copies of the example themes.

## Critical files
- New: `themes/README.md`, `themes/theme.schema.json`, `themes/examples/*.json`; app `domain/theme/ThemeSpec.kt`, `data/repo/ThemeRepository.kt`, `ui/settings/ThemeScreen.kt`, `res/font/nunito.ttf` + test; web `lib/domain/theme.ts` + test, `components/ThemeProvider.tsx`, `public/themes/*.json`
- Modified: app `ui/theme/Theme.kt` (+ drop template `Color.kt`), `MainActivity.kt`, `quickadd/QuickAddActivity.kt`, `widget/CommitWidget.kt`, `ui/settings/SettingsScreen.kt`, `ui/Routes.kt`, `AppContainer.kt`, `data/db/dao/LaterDaos.kt` (ThemeDao queries); web `app/layout.tsx`, `app/globals.css`, `components/ui.tsx` (radius), `app/settings/page.tsx`, `lib/domain/types.ts`, `lib/data/mutations.ts`
- Docs: `app/PLAN.md`, `app/RESULTS.md`

## Verification
1. Unit tests: Kotlin `ThemeParserTest` and web `theme.test.ts` pass on the same cases and example files (valid, partial fill, invalid messages, size limit, built-ins). Existing suites still pass; `assembleDebug`, web `typecheck` / `lint` / `build` clean.
2. Instrumented: `ThemeRepository` import → row saved dirty; select → `settings.active_theme_id`; delete active → falls back to Material You.
3. Emulator: import Ocean by link (`https://commit.yxuan.dev/themes/ocean.json`) → preview → apply → app, quick-add popup and widget switch to Ocean; Light / Dark force the mode; Material You restores wallpaper colours; a broken JSON shows clear errors and changes nothing.
4. Cross-device: apply a theme on the phone → the web (commit.yxuan.dev) switches within seconds (Realtime) with the same colours, radius and font; apply on the web → the phone switches after its next sync (push, about a second).
5. Deploy the web (`npm run deploy`); append M9 to `app/PLAN.md` and results to `app/RESULTS.md`.


---

# Plan: Commit — M10 (Calendar Recurrence, Snooze & Local Data Retention)

## Context
Following the completion of M9 (Themes), M10 addresses the remaining core and reliability requirements from the PRD:
1. **Calendar Recurrence (F4.3 - P2)**: Recurrence based on specific days of the week (e.g. Mon, Wed, Fri) via weekday bitmask alongside the existing interval-based recurrence (every N days). Chores carry forward until done if missed.
2. **Reminder Snooze & Lead Time (F7.1, F7.4 - P1)**: Configurable reminder lead time (At due time, 15m, 30m, 1h before) and a "Snooze 1h" action directly from the reminder notification.
3. **12-Month Local Data Retention Policy (PRD Non-Functional Requirement)**: The phone keeps 12 months of completion and workout history to bound local storage usage while the full history remains in Supabase. Only clean rows (`dirty = 0`) are purged locally so sync deletion mutations are never generated.

## Scope & Implementation

### 1. Database & Migrations
- Android Room bumped from version 5 to 6 (`AutoMigration(from = 5, to = 6)` in `AppDatabase.kt`). Added `recur_days: Int? = null` and `reminder_lead_minutes: Int? = null` to `TodoEntity`.
- Exported Room schema `6.json`.
- Prune queries added to `CompletionDao` (`pruneOld`), `WorkoutSessionDao` (`pruneOld`), and `WorkoutSetDao` (`pruneOrphaned`).
- Supabase SQL migration: `supabase/migrations/0004_calendar_recurrence_and_lead_time.sql` adding `recur_days` and `reminder_lead_minutes` to `todos`.

### 2. Domain & Data Layers
- `domain/Recurrence.kt`: support calendar recurrence bitmask alongside interval recurrence:
  - `isRecurring`: checks interval > 0 or recurDays > 0.
  - `nextDueDate`: evaluates next matching weekday strictly after `lastDoneAt` (or search from initial start/due date if never done, carrying forward).
  - `nextMatchingDay`: iterates forward to find the next active weekday bit.
  - `label`: formatted as "Mon · Wed · Fri" or "every N days".
- `domain/RetentionManager.kt`:
  - `cutoffMillis`: start-of-day timestamp 365 days ago.
  - `purgeLocalHistory`: deletes synced (`dirty = 0`) completions and workout sessions older than cutoff.
- `data/repo/TodoRepository.kt`: updated `add`, `update`, and added `purgeOldHistory`.

### 3. Notifications & Alarms
- `alarm/AlarmScheduler.kt`:
  - `rescheduleTodos`: schedules `TODO_DUE` at `todo.dueAt - (todo.reminderLeadMinutes ?: 0) * 60_000L`.
  - `snoozeTodo`: re-schedules `TODO_DUE` alarm for 1 hour into the future.
- `notify/Notifications.kt`: adds "Snooze 1h" action button to `todoReminder()`.
- `notify/NotificationActionReceiver.kt`: handles `ACTION_TODO_SNOOZE` calling `alarmScheduler.snoozeTodo`.
- `alarm/Receivers.kt`: uses `Recurrence.label(todo)` in reminder alarms.

### 4. UI (Android & Web)
- Android Compose:
  - `ui/todos/TodoForm.kt`: recurrence mode switcher (Interval vs Specific weekdays with 7 DayOfWeek FilterChips) and reminder lead time chips (At due time, 15m, 30m, 1h).
  - `TodoEditorViewModel.kt`, `TodoEditorScreen.kt`, `QuickAddActivity.kt`: wired to store and edit `recurDays` and `reminderLeadMinutes`.
- Web App (`web/`):
  - `web/lib/domain/recurrence.ts`: updated with `recur_days`, `nextMatchingDay`, `weekdaysLabel`, `recurrenceLabel`.
  - `web/components/TodoForm.tsx`: recurrence mode select, Mon..Sun toggle buttons, reminder alert lead select.
  - `web/components/TodosView.tsx`: displays `recurrenceLabel(todo)`.

### 5. Verification
- Android Unit Tests: 51/51 pass (`:app:testDebugUnitTest`).
- Android Build: `:app:assembleDebug` succeeds with Room 6 schema.
- Web Unit Tests: 31/31 pass (`npm test`).
- Web Typecheck & Lint: clean (`npm run typecheck`, `npm run lint`).
- Web Build: static export succeeded.
- Web Deploy: Cloudflare Pages deployment completed.
