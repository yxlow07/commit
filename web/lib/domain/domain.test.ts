/**
 * Same cases as the phone's unit tests (DueFormatterTest, RecurrenceTest, StatsTest, Weekdays),
 * run in the same time zone (Europe/London, see vitest.config.ts), so web and phone rules agree.
 */
import { describe, expect, it } from "vitest";
import { localDate } from "./dates";
import { formatDue } from "./due";
import { effectiveDue, intervalLabel, isDone, isRecurring, nextDueDay, nextMatchingDay, openItems, recurrenceLabel } from "./recurrence";
import { commitmentStats, lateness, perDay, progress, type CompletionRow, type ExercisePoint } from "./stats";
import { cssColor, formatVolume, formatWeight, hasWeekday, toggleWeekday, weekdayBit, weekdaysLabel } from "./format";
import type { Todo } from "./types";

const at = (y: number, m: number, d: number, h = 0, min = 0) => localDate(y, m, d, h, min);

describe("formatDue (DueFormatterTest)", () => {
  const now = at(2026, 11, 21, 12, 0); // Saturday
  it("no due date", () => expect(formatDue(null, false, now)).toEqual({ text: "", status: "none" }));
  it("today with and without time", () => {
    expect(formatDue(at(2026, 11, 21), false, now)).toEqual({ text: "Today", status: "today" });
    expect(formatDue(at(2026, 11, 21, 14), true, now)).toEqual({ text: "Today 14:00", status: "today" });
  });
  it("timed earlier today is overdue", () =>
    expect(formatDue(at(2026, 11, 21, 9, 30), true, now)).toEqual({ text: "Overdue · 09:30", status: "overdue" }));
  it("past days are overdue with day count", () => {
    expect(formatDue(at(2026, 11, 19), false, now)).toEqual({ text: "Overdue · 2d", status: "overdue" });
    expect(formatDue(at(2026, 11, 20, 23), true, now)).toEqual({ text: "Overdue · 1d", status: "overdue" });
  });
  it("upcoming", () => {
    expect(formatDue(at(2026, 11, 22), false, now).text).toBe("Tomorrow");
    expect(formatDue(at(2026, 11, 22, 9), true, now).text).toBe("Tomorrow 09:00");
    expect(formatDue(at(2026, 11, 26), false, now).text).toBe("Thu");
    expect(formatDue(at(2026, 12, 5), false, now).text).toBe("5 Dec");
    expect(formatDue(at(2027, 1, 10, 8, 15), true, now).text).toBe("10 Jan 2027 08:15");
  });
});

function chore(interval: number | null, opts: Partial<Todo> = {}): Todo {
  return {
    id: "t", user_id: "u", updated_at: 0, deleted: 0, commitment_id: "c", title: "Laundry",
    due_at: null, has_time: 0, done_at: null, recur_interval_days: interval,
    recur_days: null, reminder_lead_minutes: null, last_done_at: null, ...opts,
  };
}

describe("recurrence (RecurrenceTest)", () => {
  it("chore done late in the day resets at start of due day", () => {
    const todo = chore(4, { last_done_at: at(2026, 11, 23, 20, 30) });
    expect(nextDueDay(todo, at(2026, 11, 23))).toBe(at(2026, 11, 27));
    expect(isDone(todo, at(2026, 11, 26, 23, 59))).toBe(true);
    expect(isDone(todo, at(2026, 11, 27, 0, 1))).toBe(false);
  });
  it("never-done chore is open and due on its first date or today", () => {
    const now = at(2026, 11, 21, 12);
    expect(isDone(chore(3), now)).toBe(false);
    expect(effectiveDue(chore(3), now)).toEqual({ dueAt: at(2026, 11, 21), hasTime: false });
    const first = at(2026, 11, 25, 9);
    expect(effectiveDue(chore(3, { due_at: first }), now)).toEqual({ dueAt: first, hasTime: false });
  });
  it("overdue chore keeps its missed due date", () => {
    const todo = chore(1, { last_done_at: at(2026, 11, 23, 8) });
    const now = at(2026, 11, 26, 12);
    expect(isDone(todo, now)).toBe(false);
    expect(effectiveDue(todo, now).dueAt).toBe(at(2026, 11, 24));
  });
  it("interval crosses month end and DST", () => {
    expect(nextDueDay(chore(7, { last_done_at: at(2026, 10, 22, 23, 30) }), 0)).toBe(at(2026, 10, 29));
    expect(nextDueDay(chore(10, { last_done_at: at(2026, 11, 25, 10) }), 0)).toBe(at(2026, 12, 5));
  });
  it("one-off todos use done_at", () => {
    const now = at(2026, 11, 21, 12);
    expect(isDone(chore(null), now)).toBe(false);
    expect(isDone(chore(null, { done_at: at(2026, 11, 20) }), now)).toBe(true);
    expect(isRecurring(chore(0))).toBe(false);
  });
  it("open items hide done chores and sort by effective due", () => {
    const now = at(2026, 11, 21, 12);
    const todos = [
      chore(null, { id: "undated", title: "C" }),
      chore(null, { id: "tomorrow", title: "D", due_at: at(2026, 11, 22) }),
      chore(4, { id: "done", title: "A", last_done_at: at(2026, 11, 20) }),
      chore(2, { id: "due", title: "B", last_done_at: at(2026, 11, 19) }),
    ];
    expect(openItems(todos, now).map((i) => i.todo.id)).toEqual(["due", "tomorrow", "undated"]);
  });
  it("calendar recurrence next matching day and carries forward when missed", () => {
    const mwf = 21; // Mon (1) + Wed (4) + Fri (16)
    const mon = at(2026, 11, 23); // Monday
    expect(nextMatchingDay(mwf, mon)).toBe(mon);
    expect(nextMatchingDay(mwf, at(2026, 11, 24))).toBe(at(2026, 11, 25)); // Tue -> Wed

    const todo = chore(null, { recur_days: mwf, last_done_at: at(2026, 11, 23, 21) });
    expect(nextDueDay(todo, at(2026, 11, 23))).toBe(at(2026, 11, 25));
    expect(isDone(todo, at(2026, 11, 24, 18))).toBe(true);
    expect(isDone(todo, at(2026, 11, 25, 1))).toBe(false);

    // Carries forward on Thursday
    const thu = at(2026, 11, 26, 10);
    expect(isDone(todo, thu)).toBe(false);
    expect(effectiveDue(todo, thu).dueAt).toBe(at(2026, 11, 25));
  });
  it("never-done calendar chore: first matching day on or after its due date, overdue when missed", () => {
    const mwf = 21;
    // Due Tue 24 Nov 18:00 (not a matching day) -> due Wed 25 Nov, no time.
    const tue = chore(null, { recur_days: mwf, due_at: at(2026, 11, 24, 18), has_time: 1 });
    expect(effectiveDue(tue, at(2026, 11, 24, 9))).toEqual({ dueAt: at(2026, 11, 25), hasTime: false });
    // Due Mon 23 Nov 18:00 (a matching day) -> keeps the time.
    const mon = chore(null, { recur_days: mwf, due_at: at(2026, 11, 23, 18), has_time: 1 });
    expect(effectiveDue(mon, at(2026, 11, 23, 9))).toEqual({ dueAt: at(2026, 11, 23, 18), hasTime: true });
    // Still not done on Thursday: stays due Monday (overdue), not moved to today.
    expect(nextDueDay(mon, at(2026, 11, 26, 10))).toBe(at(2026, 11, 23));
    expect(isDone(mon, at(2026, 11, 26, 10))).toBe(false);
    // No due date -> today's or the next matching day.
    expect(nextDueDay(chore(null, { recur_days: mwf }), at(2026, 11, 24, 10))).toBe(at(2026, 11, 25));
  });
  it("weekday labels name every day, weekdays and weekends", () => {
    expect(recurrenceLabel(chore(null, { recur_days: 127 }))).toBe("every day");
    expect(recurrenceLabel(chore(null, { recur_days: 31 }))).toBe("weekdays");
    expect(recurrenceLabel(chore(null, { recur_days: 96 }))).toBe("weekends");
  });
  it("interval labels use the largest exact unit", () => {
    expect(intervalLabel(1)).toBe("every day");
    expect(intervalLabel(4)).toBe("every 4 days");
    expect(intervalLabel(14)).toBe("every 2 weeks");
    expect(intervalLabel(30)).toBe("every month");
    expect(intervalLabel(730)).toBe("every 2 years");
  });
  it("recurrence label supports interval and calendar", () => {
    expect(recurrenceLabel(chore(3))).toBe("every 3 days");
    expect(recurrenceLabel(chore(1))).toBe("every day");
    expect(recurrenceLabel(chore(null, { recur_days: 21 }))).toBe("Mon · Wed · Fri");
    expect(weekdaysLabel(21)).toBe("Mon · Wed · Fri");
  });
});

function row(id: string, todo: string, done: number, extra: Partial<CompletionRow> = {}): CompletionRow {
  return {
    completionId: id, todoId: todo, title: todo, commitmentId: "study", commitmentName: "Study", commitmentColor: 0,
    doneAt: done, dueAt: null, hasTime: false, recurIntervalDays: null, ...extra,
  };
}

describe("stats (StatsTest)", () => {
  const now = at(2026, 11, 21, 20); // Saturday; the week started Monday 16 Nov
  const noon = (d: number) => at(2026, 11, d, 12);

  it("one-off on time and late", () => {
    const rows = [
      row("a", "Essay", at(2026, 11, 18, 23), { dueAt: at(2026, 11, 18) }),
      row("b", "Quiz", at(2026, 11, 19, 15), { dueAt: at(2026, 11, 19, 14), hasTime: true }),
      row("c", "Notes", at(2026, 11, 20, 9)),
    ];
    expect(Object.fromEntries(lateness(rows))).toEqual({ a: false, b: true });
  });
  it("chore lateness uses previous completion plus interval", () => {
    const rows = [14, 16, 19, 21].map((d, i) => row(`c${i}`, "Laundry", noon(d), { commitmentId: "home", commitmentName: "Home", recurIntervalDays: 2 }));
    const late = lateness(rows);
    expect(rows.map((r) => late.get(r.completionId) ?? false)).toEqual([false, false, true, false]);
    const s = commitmentStats(rows, now)[0];
    expect(s.choreStreaks).toEqual([["Laundry", 1]]);
    expect(s.doneThisWeek).toBe(3);
    expect([s.onTime, s.late]).toEqual([2, 1]);
  });
  it("weekday chore lateness uses the next matching day", () => {
    // Mon + Thu (mask 9), first due Mon 9 Nov: done Tue 10th (late), Thu 12th, Mon 16th, Thu 19th (on time).
    const rows = [10, 12, 16, 19].map((d, i) => row(`w${i}`, "Bins", noon(d), { commitmentId: "home", commitmentName: "Home", dueAt: at(2026, 11, 9), recurDays: 9 }));
    const late = lateness(rows);
    expect(rows.map((r) => late.get(r.completionId) ?? false)).toEqual([true, false, false, false]);
    const s = commitmentStats(rows, now)[0];
    expect(s.choreStreaks).toEqual([["Bins", 3]]);
    expect([s.onTime, s.late]).toEqual([3, 1]);
  });
  it("this week counts from Monday", () => {
    const rows = [15, 16, 19, 21].map((d, i) => row(`r${i}`, `Read ${i}`, noon(d)));
    expect(commitmentStats(rows, now)[0].doneThisWeek).toBe(3);
  });
  it("overdue chore has no streak", () => {
    const rows = [17, 18, 19].map((d, i) => row(`o${i}`, "Water plants", noon(d), { recurIntervalDays: 1 }));
    expect(commitmentStats(rows, now)[0].choreStreaks).toEqual([]);
  });
  it("per day counts local days", () => {
    const rows = [row("x", "A", at(2026, 11, 20, 23, 30)), row("y", "B", at(2026, 11, 20, 8)), row("z", "C", at(2026, 11, 21, 0, 30))];
    expect(Object.fromEntries(perDay(rows))).toEqual({ [at(2026, 11, 20)]: 2, [at(2026, 11, 21)]: 1 });
  });
  it("personal bests after baseline", () => {
    const p = (d: number, w: number): ExercisePoint => ({ exerciseName: "Bench", sessionId: `s${d}`, startedAt: noon(d), bestWeight: w, bestReps: 8, volume: w * 24 });
    expect(progress([p(10, 60), p(12, 62.5), p(14, 62.5), p(17, 60), p(19, 65)]).map((x) => x.isPersonalBest))
      .toEqual([false, true, false, false, true]);
  });
});

describe("formatting (Weekdays / WorkoutFormat)", () => {
  it("weekday mask", () => {
    const mask = weekdayBit(0) | weekdayBit(3);
    expect(mask).toBe(1 | 8);
    expect(hasWeekday(mask, 0)).toBe(true);
    expect(hasWeekday(mask, 1)).toBe(false);
    expect(weekdaysLabel(mask)).toBe("Mon · Thu");
    expect(toggleWeekday(mask, 3)).toBe(1);
  });
  it("weights, volume and colours", () => {
    expect(formatWeight(60)).toBe("60 kg");
    expect(formatWeight(62.5)).toBe("62.5 kg");
    expect(formatVolume(3450)).toBe("3,450 kg");
    expect(cssColor(0xff4f6bed | 0)).toBe("#4f6bed");
  });
});
