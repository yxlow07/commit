# Commit Web App: Design Spec

Nov 23, 2026 · @YX

## How to use this spec

The web app at commit.yxuan.dev should feel like the same product as the phone, laid out for a wide screen, a mouse and a keyboard. This spec covers only what differs on the web. Everything else comes from the **App Design Spec**: principles, colour tokens, the due label rules, the todo row, copy rules. Where the two disagree on a web-only question, this spec wins; on anything shared, the App Design Spec wins.

| Carries over unchanged | Changes on the web |
| --- | --- |
| The six design principles, including the commitment-colour exception on Today | Layout: a sidebar plus one main column instead of a bottom nav |
| Colour tokens, light and dark, and theme files v2 | Type sizes in rem, slightly smaller display sizes |
| Due label rules and the todo row (due left, checkbox right) | Hover states, keyboard shortcuts, visible focus |
| Hanken Grotesk, tabular figures | Pickers: a calendar popover and typed time instead of scroll wheels |
| "All done for Study.", sentence case, commas not middle dots | Editing in a side panel, not a popup |
| Text tabs with the 18 x 2 dash under the selected tab | Undo toasts instead of confirmations |
| Workouts live with commitments, not in their own section | History can show up to a year (the phone keeps 12 weeks on screen) |

Stack, as built: Next.js static export, Tailwind, React Query, Recharts, Supabase Realtime. Nothing in this spec needs a server.

## Tokens on the web

### CSS variables

Rename the web's variables to the token names, so a theme file maps one-to-one with no translation layer. `ThemeProvider` writes them on `:root` (and inside `@media (prefers-color-scheme: dark)` for Automatic).

| Variable | Token | Replaces |
| --- | --- | --- |
| `--ink` | `ink` | `--fg`, `--primary` |
| `--on-ink` | `onInk` | `--on-primary` |
| `--text-strong` | `textStrong` | (new) |
| `--muted` | `muted` | `--fg-muted` |
| `--faint` | `faint` | (new) |
| `--track` | `track` | `--line` |
| `--fill` | `fill` | `--surface-2`, `--primary-soft` |
| `--surface` | `surface` | `--surface` |
| `--background` | `background` | `--bg` |
| `--danger` | `danger` | `--danger` |
| `--scrim` | `scrim` | (new) |
| `--hover` | `ink` at 5 % (8 % dark) | `--surface-3` |

Expose them as Tailwind colours (`bg-surface`, `text-muted`…) and remove the raw grey utility classes (`text-gray-500` and similar), so nothing bypasses the theme. Commitment colours are the only colours not from tokens.

### Type

Same family and weights as the app. Root size 16 px; sizes in rem. Display sizes are smaller than on the phone, because the web shows more at once.

| Style | Web size / weight | Where |
| --- | --- | --- |
| `display.xl` | 4.5rem (72) / 300, tracking -0.04em | Today's day number |
| `display.l` | 3.5rem (56) / 300 | Best set |
| `title.screen` | 2.25rem (36) / 700, tracking -0.02em | Page titles |
| `title.section` | 1.25rem (20) / 600 | Sidebar group names, panel title |
| `body.l` | 1.0625rem (17) / 400 | Today summary |
| `body` | 1rem (16) / 400 | Todo titles, fields |
| `label` | 0.9375rem (15) / 500 | Buttons, tabs, sidebar items |
| `meta` | 0.875rem (14) / 400 | Due labels, second lines |
| `caption` | 0.8125rem (13) / 500 | Section headings, counts |
| `micro` | 0.75rem (12) / 400 | Axis words, shortcut hints |

Use `font-variant-numeric: tabular-nums` on every time, date, count and weight. Body line height 1.45; titles 1.1.

### Spacing, shape and breakpoints

- Spacing steps: 4, 8, 12, 16, 20, 24, 32, 48 px.
- Radii: buttons, fields, round add and icon tiles 12; dialogs and the edit panel's floating form 20; segmented track 14 (segments 11); checkboxes circular; heatmap cells 3.
- Todo row: min height 48 px (44 px hit areas still apply).
- Breakpoints: **wide** 1200 px and up, **desktop** 900–1199, **narrow** below 900, **phone** below 600.
- Main column max width 720 px. Line length for prose stays under 75 characters.

### Focus and hover

- Focus: 2 px `--ink` outline, 2 px offset, on `:focus-visible` only. Never remove outlines without this replacement.
- Hover: `--hover` background on rows and text buttons; primary buttons go to `--ink` at 88 %. No movement, no shadows, no colour changes on hover.
- Pointer cursor only on things that act; rows that open the edit panel use the default cursor with the hover background.

## App shell

One persistent sidebar for navigation and commitments, one main column for the current page, and an edit panel that opens beside the list when a todo is selected. The URL always reflects what is open, so reload and the back button work.

### Wide (1200 px and up)

```
+-------------+------------------------------+------------------+
| Commit      |                              |  Edit todo   [x] |
|             |  Study                       |                  |
| Today       |  4 open, 1 overdue           |  Title           |
| History     |                              |  [Problem set 3] |
| Settings    |  Open   Done                 |  Commitment      |
|             |  ----                        |  [Study|Work|..] |
| Commitments+|  Overdue                     |  Due             |
|  Study    4 |  Late  Data Structures   ( ) |  [Fri][17:00]    |
|  Work     2 |  This week                   |  Repeat          |
|  Project  3 |  18:00 Read chapter 4    ( ) |  [ ] ...         |
|  Home     3 |  Fri   Problem set 3     ( ) |                  |
|             |                              |  [trash] Save    |
| Workouts  + |  + Add a todo                |                  |
|  Gym        |                              |                  |
+-------------+------------------------------+------------------+
  260 px          main, max 720, centred       380 px panel
```

### Desktop (900–1199 px)

Sidebar 240 px, main column fills the rest. The edit panel (380 px) slides over the right side of the main column instead of pushing it; the list stays readable to its left. No scrim: the panel is `surface` against the `background` page, with a 1 px `track` edge on its left (the one place a line is allowed, because it marks a layer, not a group).

### Narrow (600–899 px)

The sidebar collapses into a 56 px top bar: menu button, page title, round add button. The menu opens the sidebar as a drawer over a `scrim`. The edit panel takes the full width of the main column.

### Phone (below 600 px)

Match the phone app: bottom nav with Today, History, Settings; commitments listed on Today with counts and chevrons; the edit panel becomes a full-screen sheet with a back arrow. People who open the site on their phone should not have to learn a second layout.

### Sidebar

- Top: the heatmap icon (24 px) and "Commit" in `label` Bold. Clicking it goes to Today.
- Pages: Today, History, Settings as text items, 36 px tall. Current page Bold `--ink`; others Regular `--muted`.
- **Commitments** group: `caption` heading with a + button on the right (new commitment). One row per todo commitment: name in `label`, open count on the right in the commitment's colour (tabular). The open commitment is Bold `--ink`. On hover a grip appears on the left (drag to reorder) and a pencil on the right replaces the count (edit).
- **Workouts** group, 24 px below: same pattern for workout commitments; the count slot shows the next planned weekday ("Mon") in `--muted` instead of a number.
- The sidebar scrolls on its own if it overflows; the main column keeps its position.

### Main column

- 48 px top padding, 32 px sides, centred, max 720 px.
- Every page starts with its title in `title.screen` and an optional one-line summary in `meta` `--muted`.
- Text tabs (Open / Done, Tasks / Workouts) sit under the summary, left-aligned, with the 18 x 2 px dash under the selected tab.

## Web components

The shared components (todo row, checkbox, buttons, stepper, segmented control, text field, section heading, text tabs) follow the App Design Spec. This section covers how they behave with a mouse and keyboard, plus the components only the web has.

### Todo row on the web

- Layout as in the app: due label column (56 px), title, checkbox on the right.
- Hover: `--hover` background across the row, radius 12. The row is a button that opens the edit panel; the checkbox is a separate button and does not open it.
- Selected (its panel is open): same `--hover` background stays on, title Bold.
- Ticking: the row stays in place, checked and struck through, for 4 seconds or until the list is left, then fades out over 150 ms. Unticking before then restores it. This matches the widget's behaviour.
- Recurring rows show the recurrence as the second line ("Every 4 days", "Weekdays").

### Inline add

At the end of every todo list: a row reading "Add a todo" in `--muted` with a + in the due column.

- Click or press `N`: it becomes a text field in place. Enter saves the todo to the current commitment with no date and keeps the field open for the next one. Esc closes it.
- Shift+Enter (or the "More" text button that appears on the right while typing) saves the title and opens the edit panel to set a date, time or repeat.
- On Today (which mixes commitments), the field shows a small commitment picker on its right, defaulting to the last commitment used.

### Edit panel

Replaces the phone's popup on the web. It edits one todo with every field the todo can have.

- Header: "Edit todo" (or "New todo") in `title.section`, close button (x) on the right.
- Fields in this order, 20 px apart: Title, Commitment (segmented control; scrolls sideways when the names do not fit), Due (date button, time button, "No date" text button), Reminder (At due time, 15 min, 30 min, 1 hour before; shown only once a time is set), Repeat.
- Footer, pinned to the bottom: delete icon tile on the left, Cancel text button and primary "Save todo" on the right.
- **Saving:** edits are saved with Save or Ctrl/Cmd+Enter, not as you type, so a half-typed title never syncs to the phone. Leaving the panel with unsaved changes asks once: "Discard changes?" with "Keep editing" and "Discard".
- Esc or the close button closes it; the list keeps its scroll position and focus returns to the row.

### Date and time

Scroll wheels do not suit a mouse, so the web uses:

- **Date:** the date button opens a popover calendar: month name and year with previous and next arrows, a 7-column grid (Mon first), today ringed in `--ink`, the selected day filled `--ink` / `--on-ink`, past days in `--muted`. Under it, text buttons "Today", "Tomorrow", "Next Monday". Arrow keys move the day, Page Up and Page Down the month, Enter picks.
- **Time:** the time button becomes a text field that accepts "17", "1700", "17:00" or "5pm" and shows the result as "17:00". Under it, a short list of suggestions every 30 minutes, starting from the next half hour. Invalid input shows "Use a time like 17:00" in `--danger` and keeps the old value.
- The phone's wheels stay on the phone.

### Repeat

Same model and wording as the app: a checkbox with a summary ("Repeat every **4** days after I finish it", "Repeat on **weekdays**"), then a segmented control ("After I finish it" / "On weekdays").

- After I finish it: a number field (1–99, arrow keys step by 1) and a unit select (days, weeks, months, years). Stored as days, as on the phone.
- On weekdays: seven toggle buttons, 40 px square, radius 12. Click toggles one; press and drag across paints several (select or clear, set by the first day pressed), like the phone. At least one day stays selected. Each button announces "Monday, selected".

### Drag to reorder

- Commitments in the sidebar, routines on a workout commitment, exercises in the routine editor.
- A grip (6 dots, `--faint`) shows on the left on hover and on keyboard focus. Drag the grip, or focus it and press Space to lift, arrow keys to move, Space to drop, Esc to cancel. Announce each move ("Study, moved to position 2 of 4").
- While dragging, the row lifts with the `--hover` background; the others slide out of the way over 150 ms. The order saves on drop.

### Dialogs

Used only for small, separate tasks: new or edit commitment, new routine, exercise editor, import a theme from a link.

- `surface`, radius 20, max width 440 px, 24 px padding, centred over `--scrim`. Title in `title.section`.
- Actions bottom right; a destructive icon (archive, delete) bottom left as an icon tile, as on the phone.
- Esc and clicking the scrim close it; focus is trapped inside while open and returns to the opener after.

### Undo toast

Nothing asks "Are you sure?". Delete, archive and untick happen at once and show a toast instead.

- Bottom left of the main column, `--ink` background, `--on-ink` text, radius 12, 48 px tall: "Todo deleted" with an "Undo" text button in `--on-ink` Bold.
- Stays 6 seconds, longer while hovered or focused. One toast at a time; a new one replaces the old.
- Also used for errors that can be retried: "Couldn't save. Check your connection." with "Try again".

### Charts (Recharts)

- Line: 2 px `--ink`, no fill, no grid, no Y axis line; one `--track` baseline; a 4 px dot on the latest point only.
- Axis words in `micro` `--muted`: first and last labels only ("50 kg, 8 weeks ago" / "This week").
- Tooltip on hover and keyboard focus: `surface`, radius 12, 8 px padding, `meta` text: "14 Nov, 60 kg × 8". No shadow; a 1 px `--track` edge.
- Personal bests: the dot on a best is 6 px; nothing else changes colour.
- Every chart has a text alternative stating the range and the latest value.

## Screens

| Route | Screen |
| --- | --- |
| `/` | Today |
| `/c?id=…` | Commitment (todo or workout) |
| `/c?id=…&todo=…` | Commitment with a todo's edit panel open |
| `/routine?id=…` | Routine editor |
| `/session?id=…` | Workout session |
| `/history?tab=tasks` and `tab=workouts` | History |
| `/settings` | Settings |

Any route shows the sign-in screen when signed out, then returns to that route.

### Sign in

- Centred on `background`, max width 360 px: the icon at 64 px, "Commit" in `title.screen`, one line in `body` `--muted`: "Your commitments, on your phone and here." Then a primary button "Continue with Google".
- A failed sign-in shows the reason under the button in `--danger`: "Sign-in was cancelled." or "Couldn't reach Google. Try again."

### Today

On the phone Today lists commitments; on the web the sidebar already does, so Today is an **agenda across all commitments**.

- Header: day number in `display.xl` with the weekday and month stacked beside it (bottom-aligned by baseline, as in the app). The summary under it in `body.l` `--text-strong`: "4 due today, 1 overdue. Push Day planned."
- Sections, each hidden when empty: **Late** (heading in `--danger`), **Today**, **Next 7 days**. Rows are todo rows whose second line names the commitment ("Study") in `muted` tinted 35 % toward that commitment's colour, the same tint as on the phone's Today.
- **Planned today:** one line per workout planned today: "Push Day" with "Gym, 5 exercises" under it and a chevron to the routine. Workouts are run on the phone, so there is no Start button on the web.
- Inline add at the end, with the commitment picker.
- Nothing due: "Nothing due today." in `body.l` `--text-strong`, followed by the Next 7 days section if it has anything.
- Below 600 px: the phone app's Today layout instead (commitment rows with counts and chevrons).

### Commitment (todo)

- Title ("Study") and summary ("4 open, 1 overdue"). A pencil icon button beside the title opens the edit-commitment dialog.
- Open / Done text tabs.
- **Open:** sections Overdue, This week, Later, No date, as in the app. Inline add at the end.
- **Done:** grouped by week, newest first ("This week", "Last week", "3–9 Nov"); the second line says "On time", "1 day late" or "No due date". "Show earlier" text button at the end loads 8 more weeks.
- Empty: "All done for Study." in `body.l` `--text-strong`, then the inline add.

### Commitment (workout)

- Title ("Gym"), summary ("Push Day planned today." or "3 routines"), pencil to edit the commitment.
- Routines as rows: name in `body`, second line "5 exercises, Mon, Thu" in the tinted `muted`, chevron. Hover shows the grip for reordering.
- "+ New routine" row at the end, like inline add.
- Empty: "No routines yet. Add each workout day, like Push, Pull and Legs."

### Routine editor

- Back link "Gym" above the title. Title is the routine's name as an editable field styled as `title.screen` (click to edit, Enter or blur saves).
- **Planned days:** the seven weekday toggles (same drag-to-paint behaviour).
- **Rest between exercises:** stepper, 15 s steps, shown as "1 min 30 s".
- **Exercises:** rows with the name and "4 × 8, 60 kg, rest 90 s" (or "3 × 30 s, rest 60 s" for timed) in `meta` `--muted`, grip on hover, chevron. Click opens the exercise dialog (name; Sets and reps / Timed segmented control; steppers in a 2 x 2 grid; trash, Cancel, Save exercise).
- "+ Add exercise" row at the end. Delete routine is an icon tile at the bottom left, with an undo toast.

### Workout session

- Back link "History". Title: routine name; summary: "Thu 20 Nov, 52 min, 16 sets, 6,420 kg".
- Sets grouped by exercise, a `caption` heading per exercise. Each set row: "Set 1" in `--muted`, then reps and weight as numbers that become fields on click (Enter saves, Esc cancels), and a trash icon on hover. Totals in the summary update straight away.

### History: Tasks

- Title "History" with Tasks / Workouts text tabs beside it on the same baseline, as in the app.
- Filter row: All, then each commitment as a text tab. Range on the right: "12 weeks", "6 months", "1 year" as text tabs.
- **Heatmap:** weeks as columns, Mon to Sun as rows. Cells 12 px at 12 weeks and 6 months, 10 px at 1 year, 3 px gaps, radius 3. Month initials above the first week of each month in `micro`. Hover or focus shows "Thu 20 Nov, 5 finished". Click a day to filter the timeline to it; the cell gets a 2 px `--ink` ring and a line appears: "Showing Thu 20 Nov" with "Show all".
- Legend row under it, as in the app: "27 this week" on the left; "Less", the gradient bar and "More" on the right.
- **This week by commitment:** a compact table with no borders: commitment name, done, late. Late counts above zero in `--danger`; zero shows as an empty cell.
- **Timeline** grouped by day, 32 px between days; notes only for exceptions ("1 day late", "6 in a row"). "Show earlier" loads more.

### History: Workouts

- Exercise filter as text tabs; with more than 6 exercises, a select labelled "Exercise" instead.
- Best set in `display.l` ("60 kg × 8") with "Best set, since 14 Nov" under it, then the line chart (full main-column width, 160 px tall).
- Sessions: routine name, "52 min, 16 sets" under it, date on the right, chevron to the session.

### Settings

One page, sections 48 px apart, each with a `title.section` heading:

- **Recaps:** morning and night rows: name, "Today's plan" or "Today's wins, tomorrow's plan" under it in `--muted`, a time field (24-hour, typed as above) and a switch. Changes save straight away with a quiet "Saved" in `micro` `--muted` that fades after 2 seconds.
- **Theme:** the preview (the widget drawn small on the theme's wallpaper colour), the radio list of themes (swatch, name, source, tick), then "Import file" (primary) and "Import from link" (secondary). Errors under the buttons in `--danger`.
- **Commitments:** "Archived" list, each with a Restore text button. Empty: "Nothing archived."
- **Account:** the signed-in email and a "Sign out" text button.

## Keyboard, live updates and failure states

### Keyboard shortcuts

Single-key shortcuts work only when no text field has focus. Press `?` to see them in a dialog.

| Keys | Action |
| --- | --- |
| `N` | Add a todo (opens the inline add in the current list) |
| `↑` `↓` or `J` `K` | Move between todo rows |
| `Enter` | Open the focused todo in the edit panel |
| `X` | Tick or untick the focused todo |
| `Delete` | Delete the focused todo (with an undo toast) |
| `Esc` | Close the panel, dialog, popover or inline add |
| `Ctrl` / `Cmd` + `Enter` | Save in the edit panel or a dialog |
| `G` then `T`, `H` or `S` | Go to Today, History or Settings |
| `[` and `]` | Previous and next commitment in the sidebar |

Show the key in a tooltip on hover where a shortcut exists ("Add todo (N)"), in `micro`.

### Live updates

Changes made on the phone arrive through Realtime within a few seconds.

- New, changed and removed rows update in place with no banner. A new row fades in over 150 ms; nothing else animates.
- Keyboard focus stays on the same todo when the list changes around it. If that todo disappears, focus moves to the next row.
- **The todo open in the edit panel changes elsewhere:** with no unsaved edits, the panel updates quietly. With unsaved edits, the panel keeps them and shows a line above the footer: "Changed on another device." with "Load their version". Nothing is overwritten without asking.
- **It is deleted elsewhere:** the panel shows "This todo was deleted on another device." and a Close button; Save is disabled.
- Theme changes apply at once, without a reload.

### Loading

- Pages show their title straight away. Lists wait up to 300 ms for data before showing "Loading…" in `meta` `--muted` where the list will be. No skeletons, no spinners.
- Revisiting a page shows the cached data at once and refreshes it behind the scenes.

### Offline

The web app writes straight to Supabase and has no offline queue.

- When the browser goes offline, a line appears at the top of the main column: "You're offline. Changes can't be saved until you reconnect." in `meta`, on `--fill`, radius 12, 12 px padding.
- Save buttons, checkboxes and inline add are disabled (with that reason as their tooltip). Reading still works from the cache.
- On reconnect the line disappears and everything refetches.

### Errors

- A save that fails keeps the panel open with the values intact and shows the toast "Couldn't save. Check your connection." with "Try again".
- A tick that fails puts the checkbox back and shows the same toast.
- If the session expires, the sign-in screen appears; after signing in, the same route opens again. An open, unsaved edit panel is kept in `sessionStorage` and restored.

## Accessibility and copy

### Accessibility

- Contrast 4.5:1 for all text in both themes. `--faint` is never used for text on the web (the widget's faint tabs are a phone-only exception).
- Every control is reachable and usable by keyboard, in reading order. The sidebar is a `nav` landmark, the main column `main`, the edit panel a labelled `complementary` region; dialogs use `role="dialog"` with `aria-modal`.
- Text tabs and filters are `role="tablist"` with `aria-selected`; weekday toggles are buttons with `aria-pressed`; the theme list is a `radiogroup`.
- Checkboxes are real checkboxes (or buttons with `role="checkbox"`) named "Mark done: Problem set 3".
- Hit areas at least 44 x 44 px on narrow and phone widths, 32 x 32 px with a mouse on wide and desktop.
- Heatmap cells are buttons with names ("Thu 20 Nov, 5 finished"); arrow keys move between days.
- Live changes from another device are not announced (they would interrupt), except in the edit panel, where the "Changed on another device" line is a polite live region.
- `prefers-reduced-motion`: no fades or sliding; reordered rows jump into place.
- Zoom to 200 % works without horizontal scrolling (the layout drops to the narrow or phone breakpoint).

### Copy

The App Design Spec's copy rules apply. On the web, also:

- Name buttons after the action, the same as on the phone: "Save todo", "Add to Study", "Import file", "Restore".
- Toasts say what happened, in the past tense, with the object: "Todo deleted", "Gym archived", "Problem set 3 marked not done".
- Use "phone" and "another device", never "client" or "sync", in anything people read. The only exception is the Account section, which may say "Synced".

## Changes from the current web app, and acceptance

### What changes

The current web app already has the sidebar groups, drag to reorder, workouts merged into commitments, the Archived list, the heatmap legend and theme v2 colours. Build the rest in this order, so each step is usable on its own:

1. **Tokens:** rename the CSS variables to token names, expose them to Tailwind, remove raw grey classes, load Hanken Grotesk with `next/font` (tabular figures on).
2. **Shell:** pages (Today, History, Settings) move into the top of the sidebar; any remaining top nav goes. Add the four breakpoints, including the phone layout with a bottom nav.
3. **Today agenda** at `/`; the current main page becomes `/c?id=…`.
4. **Todo row and edit panel:** hover and selected states, tick-then-fade, the panel replacing inline editing, unsaved-changes guard, the panel's state in the URL.
5. **Inline add** with Enter-to-save and Shift+Enter for more.
6. **Pickers:** calendar popover, typed time with suggestions, repeat with units and drag-to-paint weekdays.
7. **Undo toasts** in place of any confirm dialog for delete, archive and untick.
8. **History:** 12 weeks / 6 months / 1 year ranges, day filter from the heatmap, the "This week by commitment" table; progress lives on the Workouts tab (best set and chart), not a separate tab.
9. **Keyboard shortcuts** and the `?` dialog.
10. **Failure states:** offline line, failed-save toast, changed or deleted on another device in the panel.

### Acceptance checklist

- [ ] Every colour comes from a token variable; switching theme on the phone changes the web at once, light and dark
- [ ] Hanken Grotesk with tabular figures everywhere numbers appear
- [ ] Due labels and todo rows match the phone exactly (compare the same commitment side by side)
- [ ] Wide, desktop, narrow and phone layouts match the wireframes; at 375 px wide the site looks like the phone app
- [ ] Today shows Late, Today, Next 7 days and Planned today across all commitments
- [ ] A todo can be added, edited, moved, ticked and deleted without touching the mouse
- [ ] Ticked rows stay for 4 seconds and can be unticked; delete and archive show an undo toast that works
- [ ] Editing a todo on the phone while it is open on the web never loses the web's unsaved edits
- [ ] Going offline shows the line and disables saving; reconnecting recovers without a reload
- [ ] Routines, exercises and commitments reorder by drag and by keyboard, with announcements
- [ ] History ranges, day filter and charts work; every chart has a text alternative
- [ ] Keyboard focus is always visible; Esc closes the top-most layer; focus returns to where it came from
- [ ] 200 % zoom and `prefers-reduced-motion` behave as described
- [ ] Axe (or Lighthouse accessibility) reports no contrast or naming errors on every route
