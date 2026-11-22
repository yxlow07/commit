# Commitments App: Design Spec

Nov 22, 2026 · @YX

## How to use this spec

This document is the visual source of truth for the Android app and widget. It sits next to the PRD, which defines behaviour and data; this spec defines how things look and respond.

- **Clickable reference:** [Commitments App: Screens and Widget](https://claude.ai/artifact/P8K9bw8y8aMjgkscm4JtQD), nine screens on a canvas. Press Play on any screen to try it. If the canvas and this doc disagree, this doc wins.
- **Stack:** Jetpack Compose + Material 3 for the app, Jetpack Glance for the widget. Map every value below to a theme token; never hard-code hex values in screens.
- **Build order:** tokens and theme first, then the shared components (section 4), then the widget, then app screens. The widget has platform limits that change some values (section 5).

## Design principles

1. **Grey by default, red only for problems.** The interface is greyscale. The single accent, red, means overdue or late and nothing else. Never use it for emphasis or branding.
2. **Type does the hierarchy.** One family. Selected = bold ink, unselected = regular grey. No pills, chips, underlines or coloured backgrounds to show selection, except the segmented control in forms.
3. **One loud element.** Big numbers in a light weight (rest timer, today's date, best set, the selected commitment name). Everything else stays small and quiet.
4. **Space separates, not lines.** No divider rules between list rows and no cards with shadows. Group with spacing (see section 3).
5. **Show exceptions, not normal states.** Mark a todo as late; do not mark it as on time. Show a streak; do not show "0 day streak".
6. **Same row everywhere.** A todo looks and behaves the same in the widget, the commitment screen and history: due label left, title in the middle, checkbox right.

## Tokens

These map to the theme JSON from the PRD. Token names are the keys a theme file sets; anything a theme leaves out falls back to Light.

### Colour

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| `ink` | #1F2123 | #ECEDEE | Primary text, selected tab, checkbox stroke and fill, primary button |
| `onInk` | #FFFFFF | #17181A | Text and icons on `ink` (primary button, checked tick) |
| `textStrong` | #45474B | #C9CBCE | Summary sentences, empty-state text |
| `muted` | #6B6E73 | #A3A6AB | Secondary text, unselected tabs, due labels |
| `faint` | #9C9FA4 | #6B6E73 | Chevrons, current set segment. Never for text |
| `track` | #E3E4E6 | #2E3033 | Future set segments, chart baseline |
| `fill` | #EEEFF0 | #2A2C2F | Text fields, stepper buttons, secondary button, segmented track |
| `surface` | #FFFFFF | #17181A | Widget, dialog, bottom nav, notifications |
| `background` | #F6F6F5 | #0F1011 | App screen background |
| `danger` | #B42318 | #F4877B | Overdue and late labels only |
| `scrim` | rgba(31,33,35,0.45) | rgba(0,0,0,0.6) | Behind the quick-add popup |

Heatmap shades, lightest to darkest: #E6E7E8, #C9CBCE, #9C9FA4, #6B6E73, #1F2123 (dark theme: reverse toward `ink`).

### Type

Family: **Hanken Grotesk** (bundle the TTF in the app). Use tabular figures (`fontFeatureSettings = "tnum"`) for every time, date, count and weight.

| Style | Size / weight | Tracking | Where |
| --- | --- | --- | --- |
| `display.xl` | 96 / Light 300 | -3 | Today's date, widget rest timer |
| `display.l` | 64 / Light 300 | -2 | Best set on Workouts |
| `display.m` | 48 / Light 300 | 0 | Timer in the workout notification |
| `title.screen` | 40 / Bold 700 | -1 | Screen titles (Study, History, Themes) |
| `tab.selected` | 30 / Light 300 | -1 | Selected commitment in the widget |
| `title.dialog` | 20 / Bold 700 | 0 | Popup title, commitment names on Today (20 / Medium 500) |
| `body.l` | 17 / Regular | 0 | Today summary, notification titles (17 / Bold) |
| `body` | 16 / Regular | 0 | Todo titles, field text |
| `label` | 15 / Medium 500 | 0 | Tabs, buttons, filters |
| `meta` | 14 / Regular | 0 | Due labels, secondary lines |
| `caption` | 13 / Medium 500 | 0 | Section headings (Overdue, This week), footer counts |
| `micro` | 12 / Regular | 0 | Chart axis words, units under stepper numbers |

### Spacing and shape

- Spacing steps: 4, 8, 12, 16, 20, 24, 36, 48 dp.
- App screen padding: 24 dp sides, 48 dp top. Widget padding: 20 dp left, 16 dp right, 16 dp top, 14 dp bottom.
- Todo row: min height 52 dp in the widget, 56 dp in the app. No dividers.
- Gap between sections: 24 dp. Gap between a section heading and its first row: 4 dp.
- Touch targets: at least 44 x 44 dp, even when the visible mark is smaller (checkbox is 22 dp inside a 44 dp hit area).
- Corner radius: widget and dialog 28, theme preview 24, fields 12, segmented track 14 (segments 11), buttons 12 (text, round add and stepper buttons alike), checkboxes and avatars circular, heatmap cells 3.

## Components

Build these once and reuse them in the app. The widget needs Glance versions of the todo row, checkbox and tabs (section 5).

### Todo row

```
| due (56dp) | title (flex, 1 line, ellipsis)      | [ ] |
|            | second line: recurrence or detail   |     |
```

- Due label: `meta`, fixed 56 dp column, left. Title: `body`, `ink`. Optional second line: `meta`, `muted` (recurrence such as "Every 4 days", or a full date in the app).
- Checkbox on the right, its 44 dp hit area overlapping the row's right padding by 8–10 dp so the circle lines up with the widget's edge.
- Tapping the title (not the checkbox) opens the todo for editing in the app. In the widget, tapping the title opens the app on that todo.

### Due label rules

One rule set everywhere, so the left column always reads the same way:

| Situation | Label | Colour |
| --- | --- | --- |
| Past due, not done | `Late` | `danger` |
| Due today with a time | `18:00` (24-hour) | `muted` |
| Due today, no time | `Today` | `muted` |
| Due in the next 6 days | Weekday: `Sun`, `Mon`… | `muted` |
| Due later | `28 Nov` | `muted` |
| No due date | Empty (keep the column width) |  |
| Done tab and history | The weekday it was finished | `muted` |

Recurring chores use the same rules for their next due date.

### Checkbox

- 22 dp circle, 1.5 dp `ink` stroke, inside a 44 dp hit area.
- Checked: `ink` fill, 12 dp `onInk` tick. The title turns `muted` with a strikethrough.
- In the widget the checked row stays visible until the next redraw after leaving the tab, then disappears. Unticking before then restores it.
- Content description: "Mark done: \<title>" / "Mark not done: \<title>".

### Commitment tabs (widget)

- One horizontal row of names that slides with the selection when it overflows (see Platform limits). Gap 14 dp.
- Selected: `tab.selected` (30 / Light, `ink`) with a short 18 x 2 dp `track` dash centred 3 dp below the name (a hint, not a highlight). Others: 15 / Regular in `faint` (about 2.6:1 on light; a deliberate trade-off so the selected tab clearly leads). All sit on one text baseline.
- Each tab has 12 dp padding above and below so the hit area is at least 44 dp.
- Tapping a tab swaps the list in place. The selected tab scrolls fully into view.

### Buttons

| Type | Look | Used for |
| --- | --- | --- |
| Primary | `ink` fill, `onInk` text, 48 dp tall, 12 dp corners, `label` | Save todo, Done set, Skip rest, Add to Study |
| Primary round | 44–48 dp `ink` square with 12 dp corners, 18–20 dp plus icon in `onInk` | Add todo in the widget and on Today |
| Secondary | `fill` background, `ink` text | Add 30 s, Import from link |
| Text | No background, `ink` Bold (or `muted` Medium for low-priority actions) | Cancel, Edit, Open recap, No date |

### Stepper (reps and weight)

- &#91;−\] value \[+\] in a row. Buttons: 44 dp `fill` squares with 12 dp corners and a 22 dp Regular minus or plus.
- Value: 28 / Light, tabular, `ink`. Unit under it in `micro`, `muted` ("reps", "kg").
- Reps step by 1, weight by 2.5 kg, neither below 0. The value logged for the set is whatever is showing when Done set is tapped.

### Set progress

One segment per set, 4 dp tall, 4 dp gap, radius 2. Done sets `ink`, current set `faint`, future sets `track`. Text beside it: "Set 3 of 4" in `meta`.

### Segmented control (forms only)

Track: `fill`, radius 14, 3 dp padding. Segments: 42 dp tall, radius 11, `label`. Selected segment: `surface` background, Bold, shadow 0 1 2 at 12% `ink`. Used for the commitment picker in quick-add.

### Text field

`fill` background, no border, 48 dp tall, radius 12, 14 dp horizontal padding, `body` text. Label above it in `caption`, `muted`. Focus: 2 dp `ink` outline.

### Section heading

`caption` (13 / Medium), `muted`, sentence case: "Overdue", "This week", "No date". The Overdue heading uses `danger`. Never all caps.

### Bottom nav (app)

72 dp tall, `surface`, four equal text items: Today, History, Workouts, Settings. `label` at 14: selected Bold `ink`, others Regular `muted`. Text only, no icons.

## Widget

The widget is a white panel with the commitment tabs on top, the selected commitment's todos as a list, and a footer with the open count and a round add button. Reference: the "Widget · todos" screen on the canvas.

```
+--------------------------------------------+
| Study  Work  Project  Home  Gym            |  tabs (selected large)
|                                            |
| Late   Data Structures lab 5          ( )  |  Late in danger
| 18:00  Read chapter 4                 ( )  |
| Fri    Problem set 3                  ( )  |
|        Revise lecture notes           ( )  |
|                                            |
| 4 open, 1 overdue                     (+)  |
+--------------------------------------------+
```

### Layout

- Background `surface`, corner radius = the system widget radius (`system_app_widget_background_radius` on Android 12+), falling back to 28 dp.
- Rows use the todo row component. List order: Late first, then by due date and time, then undated items, in the order created.
- Footer: `caption` count on the left ("4 open, 1 overdue", or "All done"), primary round add button (44 dp) on the right.
- Gym is the last tab. Selecting it switches the whole widget to the workout layout (section 6).

### Sizes

Use `SizeMode.Responsive` with three layouts:

| Size | Shows |
| --- | --- |
| Small (3 x 2 cells) | Tabs, the first 2 rows, no footer count (add button only) |
| Medium (4 x 3, the default) | Tabs, up to 4 rows, footer |
| Large (4 x 4 and up) | Tabs, all rows in a scrolling `LazyColumn`, footer |

### States

| State | What shows |
| --- | --- |
| Normal | As above |
| Everything in the tab done | Rows disappear on the next redraw; the list area shows "Nothing left in Study." in `textStrong`, indented to the title column |
| No commitments yet | "Add your first commitment" and a primary button that opens the app on the new-commitment screen |
| Dark mode | Same layout with dark tokens (use `GlanceTheme` or day/night `ColorProvider`s) |

### Interactions

| Tap | Result | Opens the app? |
| --- | --- | --- |
| A tab | Switches the list in place | No |
| Checkbox | Marks done or not done, writes to Room, schedules sync | No |
| A todo's title | Opens that todo in the app | Yes |
| Add button | Opens the quick-add popup (translucent activity) over the home screen | Popup only |

### Platform limits (Glance and RemoteViews)

These change the design on the widget only. The app follows the tokens exactly.

- **No custom fonts.** Widgets render text with system fonts, so the widget uses the device's default sans-serif. Hanken Grotesk is app-only.
- **No Light weight.** Glance offers Normal, Medium and Bold. Set the selected tab and the rest timer in Normal at the specified size. If the large numbers look too heavy on device, render them as a bitmap (`ImageProvider`) instead, but only for the rest timer.
- **No baseline alignment.** Put the tabs in a `Row` with bottom alignment and add about 4 dp bottom padding to the small tabs so their baselines meet the large one. Check on a real device.
- **No sideways scrolling.** Widgets (RemoteViews) have no horizontal scroll view and can't detect swipes. The tab row behaves like a scrolled strip instead: it starts one tab before the selected one, slides left to stay full near the end, and the next tab is shown cut off ("Sch…") when more exist. Tapping a tab further along selects it and the row slides to it. If the selected name is very long, its size drops from 30 to 24 sp.
- **Checkbox look.** Glance's `CheckBox` cannot be styled as a thin circle. Use a 44 dp clickable `Box` holding one of two vector drawables (empty circle, filled circle with tick), with `actionRunCallback` and a content description.
- **Live countdown.** Use a `Chronometer` through `AndroidRemoteViews` set to count down to `rest_ends_at`. The widget itself never redraws every second.

## Workout widget and notifications

### Workout layout (Gym tab)

Same panel and tab row, with Gym selected. Below the tabs:

1. Exercise name in `body.l` Bold on the left ("Bench press"); routine and position on the right in `caption` `muted` ("Push Day, 2 of 5").
2. Set progress bar with "Set 3 of 4".
3. **Working state:** two steppers side by side (reps, kg), then a full-width primary "Done set" button.
4. **Resting state:** the countdown in `display.xl`, centred, with "Rest, then set 4" under it in `meta` `muted`. Below: secondary "Add 30 s" and primary "Skip rest", side by side.

When rest ends, the workout moves to the next step automatically and the widget returns to the working state for the next set. After the last set of the last exercise it shows "Workout done" with the duration and a text button "Open summary".

No active workout: show the routine planned for today (or the last one used) as a single row with a primary "Start Push Day" button, and a text button "Choose another".

### Notifications

System notifications keep Android's standard frame. Only the content follows this spec.

| Notification | Title | Body | Actions |
| --- | --- | --- | --- |
| Morning recap | "4 things today, 1 overdue" | Inbox style: one line per item, title left, due label or commitment right; Late items use `danger` where the system allows colour | Open recap |
| Night recap | "6 done today, 2 still open" | Two or three plain sentences: what is still open, what is due tomorrow, workout duration | Open recap |
| Workout (ongoing) | "Resting" | "Next: bench press, set 4 of 4" plus a live countdown (chronometer) | Add 30 s, Skip rest, Done set |
| Todo reminder | The todo title | "Due 18:00, Study" | Done, Snooze 1 h |

The workout notification is the only one with a dark background (use the ongoing, colourised style). That makes it easy to spot on the lock screen.

## Quick-add popup

A translucent activity themed as a dialog, opened by the widget's add button and by Add todo in the app. It must open in under 500 ms with the keyboard already up.

```
  scrim over the home screen
  +--------------------------------------+
  | New todo                             |
  | Title                                |
  | [ Problem set 4                    ] |
  | Commitment                           |
  | [ Study | Work | Project | Home ]    |  segmented control
  | Due                                  |
  | [ Fri 28 Nov ] [ 17:00 ]   No date   |
  | [ ] Repeat every 4 days after I      |
  |     finish it                        |
  |                   Cancel  [Save todo]|
  +--------------------------------------+
```

- Surface `surface`, radius 28, 20 dp side padding, 24 dp top, 20 dp gap between groups. Scrim `scrim`.
- Title field gets focus on open. Save is disabled until the title has a character.
- Commitment defaults to the tab the widget was showing. Gym is not offered (workouts are planned in the app).
- Due: tapping the date opens the system date picker, tapping the time opens the time picker. The time button reads "Add time" in `muted` until set. "No date" clears both.
- Repeat: a checkbox (the sentence ticks it too). Unticked, the sentence reads "Repeat every **x** days after I finish it" with a bold, `muted` x. Ticking shows a centred scroll wheel (1–365, starting at 1) with one selection band behind the number and "days"; the number in the sentence turns bold `ink`.
- Save closes the popup, writes to Room, and the widget redraws with the new row. Cancel or tapping the scrim closes without saving. Back does the same.

## App screens

All screens use `background`, 24 dp side padding and the bottom nav, except Commitment detail and Quick-add, which are pushed screens without the nav.

### Today (nav: Today)

- Header: today's day number in `display.xl`, with the weekday (`body.l` Medium) and month (`body.l` `muted`) stacked beside it, bottom-aligned. Primary round add button (48 dp) top right.
- Summary under it in `body.l` `textStrong`, max 30 characters per line: "4 due today, 1 overdue. Push Day tonight." Plain text, no highlighted words. With nothing due: "Nothing due today."
- Commitment list: one 64 dp row per commitment. Name in 20 / Medium, one detail line in `meta` `muted` ("One overdue, next due today"), open count in 30 / Light `textStrong` on the right, then a 16 dp chevron in `faint`. The whole row opens Commitment detail (Gym opens Workouts).

### Commitment detail (pushed)

- Top bar: back arrow (44 dp) left, Edit text button right.
- Title in `title.screen` ("Study"), one line under it in `meta` `muted` ("4 open, 1 overdue").
- Open / Done switch as two text tabs (`label` 16, selected Bold `ink`, other Regular `muted`).
- Open: sections Overdue, This week, Later, No date (hide empty sections). Done: grouped by week, newest first, second line says "On time", "1 day late" or "No due date".
- Rows are the shared todo row: due label left, checkbox right.
- Primary button "Add to Study" fixed 24 dp from the bottom right.

### History: Tasks (nav: History)

- Title "History" with Tasks / Workouts text tabs on the right of the same baseline.
- Filter row: All, then one text tab per commitment (selected Bold `ink`).
- Heatmap: 12 columns (weeks) x 7 rows (days), 3 dp gaps, radius 3, five shades. Labels under it: "12 weeks ago" left, "This week" right, in `micro`. Then one sentence in `meta` `textStrong`: "Each square is a day. Darker means more finished. 27 this week."
- Timeline grouped by day ("Today", "Thursday 20 November" as section headings). Each row: title, "Study, 10:40" under it, and a right-side note only for exceptions: "1 day late" in `danger`, "6 in a row" in `muted`.

### History: Workouts

- Same title row with Workouts selected. Exercise filter as text tabs (Bench, Overhead, Squat…).
- Best set in `display.l` ("60 kg × 8"), "Best set, since 14 Nov" under it.
- Line chart: 2 dp `ink` line, no fill, no gridlines, one `track` baseline, a 4 dp dot on the latest point. Axis words under it: "50 kg, 8 weeks ago" left, "This week" right. Content description states the range.
- Sessions: routine name, "52 min, 16 sets" under it, date on the right. Tapping opens the session, where any set can be edited.

### Themes (nav: Settings)

- Title "Themes". Preview: the widget drawn small on the theme's wallpaper colour, so the effect is visible before choosing.
- Theme list: 28 dp swatch (surface colour with a 10 dp dot of the text colour), name, source ("Default", "Built in", "Imported file"). Selected: name in Bold and a tick on the right. Whole row is a radio button.
- Buttons: primary "Import file", secondary "Import from link". Under them: "A theme is a JSON file of colours and corner rounding. Missing values fall back to Light."
- A failed import shows the reason in `danger` under the buttons, for example "This file has no colours section."

## Copy rules and accessibility

### Copy

- Sentence case everywhere. No all-caps labels.
- Buttons say exactly what happens: "Save todo", "Done set", "Skip rest", "Import file". The same action keeps the same name across screens and notifications.
- Use numerals for counts in summaries ("4 due today"), words only in notification titles where a sentence reads better.
- Times are 24-hour ("18:00"); dates are "Fri 28 Nov" or "28 Nov". No ordinal suffixes.
- Join metadata with commas ("Study, 10:40"), not middle dots.
- Empty and error states say what to do next: "Nothing left in Study.", "Add your first commitment", "This file has no colours section." No apologies, no exclamation marks.

### Accessibility

- Text contrast at least 4.5:1 against its background in both themes. `faint` is never used for text.
- Every touch target is at least 44 x 44 dp.
- Icon-only buttons have content descriptions: "Add todo", "Back to Today", "Fewer reps", "More weight".
- Tabs and filters expose their selected state (`Role.Tab` with selected semantics in Compose).
- Red is never the only signal: overdue items also say "Late".
- Text follows the system font size. The widget's row height grows with it rather than cutting text.
- Respect reduced motion: no animations except the checked row fading out (150 ms), which is skipped when animations are off.

## Acceptance checklist

- [ ] All colours, type styles, spacing and radii come from theme tokens; switching to Dark changes app and widget together
- [ ] Due labels follow the rules table in every place a todo appears
- [ ] Todo rows look the same in the widget, Commitment detail and History (due left, checkbox right)
- [ ] Widget: tab switch, tick and untick work without opening the app; title tap opens the todo; add opens the popup only
- [ ] Widget tab row slides with the selection and cuts off the next tab when more exist; small tab baselines line up with the selected tab on a real device
- [ ] Widget small, medium and large layouts match the sizes table
- [ ] Rest countdown runs smoothly via Chronometer and the step advances when rest ends, with the screen off
- [ ] Quick-add opens in under 500 ms with the keyboard up and defaults to the widget's current commitment
- [ ] Empty states: all done in a tab, no commitments yet, nothing due today
- [ ] Red appears only for Late, overdue headings and errors
- [ ] TalkBack reads every control with a clear name and selected state
- [ ] Text scaled to 130% does not clip in the widget or app
