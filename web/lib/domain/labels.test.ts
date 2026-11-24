import { describe, expect, it } from "vitest";
import { localDate } from "./dates";
import { doneLabel, fullDate, isLate, openLabel, parseTime, restLabel, splitInterval, timeliness, weekTitle } from "./labels";

// Thursday 20 November 2025, 14:00 local.
const now = localDate(2025, 11, 20, 14, 0);

describe("due labels (port of DueLabels.kt)", () => {
  it("follows the design spec's rules", () => {
    expect(openLabel(null, false, now)).toEqual({ text: "", late: false });
    expect(openLabel(localDate(2025, 11, 19), false, now)).toEqual({ text: "Late", late: true });
    expect(openLabel(localDate(2025, 11, 20, 13, 0), true, now).late).toBe(true);
    expect(openLabel(localDate(2025, 11, 20, 18, 0), true, now).text).toBe("18:00");
    expect(openLabel(localDate(2025, 11, 20), false, now).text).toBe("Today");
    expect(openLabel(localDate(2025, 11, 21), false, now).text).toBe("Fri");
    expect(openLabel(localDate(2025, 11, 26), false, now).text).toBe("Wed");
    expect(openLabel(localDate(2025, 11, 28), false, now).text).toBe("28 Nov");
  });

  it("date-only todos are late from the next day", () => {
    expect(isLate(localDate(2025, 11, 20), false, now)).toBe(false);
    expect(isLate(localDate(2025, 11, 19), false, now)).toBe(true);
  });

  it("done labels, full dates and timeliness", () => {
    expect(doneLabel(localDate(2025, 11, 18, 9), now)).toBe("Tue");
    expect(doneLabel(localDate(2025, 11, 1, 9), now)).toBe("1 Nov");
    expect(fullDate(localDate(2025, 11, 28, 17, 0), true, now)).toBe("Fri 28 Nov, 17:00");
    expect(fullDate(localDate(2026, 1, 2), false, now)).toBe("Fri 2 Jan 2026");
    expect(timeliness(null, false, now)).toBe("No due date");
    expect(timeliness(localDate(2025, 11, 19), false, now)).toBe("1 day late");
    expect(timeliness(localDate(2025, 11, 20, 9), true, now)).toBe("Late");
    expect(timeliness(localDate(2025, 11, 20), false, now)).toBe("On time");
  });

  it("week titles", () => {
    expect(weekTitle(localDate(2025, 11, 17), now)).toBe("This week");
    expect(weekTitle(localDate(2025, 11, 12), now)).toBe("Last week");
    expect(weekTitle(localDate(2025, 11, 5), now)).toBe("3–9 Nov");
    expect(weekTitle(localDate(2025, 10, 29), now)).toBe("27 Oct–2 Nov");
  });
});

describe("typed time", () => {
  it.each([
    ["17", 17 * 60], ["1700", 17 * 60], ["17:00", 17 * 60], ["5pm", 17 * 60], ["5:30 pm", 17 * 60 + 30],
    ["12am", 0], ["12pm", 12 * 60], ["9.15", 9 * 60 + 15], ["0930", 9 * 60 + 30], ["7", 7 * 60],
  ])("%s", (input, minutes) => expect(parseTime(input)).toBe(minutes));

  it.each(["", "25", "17:75", "13pm", "noon", "1:2"])("rejects %s", (input) => expect(parseTime(input)).toBeNull());
});

describe("rest and repeat", () => {
  it("rest labels", () => {
    expect(restLabel(45)).toBe("45 s");
    expect(restLabel(60)).toBe("1 min");
    expect(restLabel(90)).toBe("1 min 30 s");
  });

  it("splits stored days into the largest exact unit", () => {
    expect(splitInterval(14)).toEqual({ count: 2, unit: "weeks" });
    expect(splitInterval(30)).toEqual({ count: 1, unit: "months" });
    expect(splitInterval(365)).toEqual({ count: 1, unit: "years" });
    expect(splitInterval(10)).toEqual({ count: 10, unit: "days" });
  });
});
