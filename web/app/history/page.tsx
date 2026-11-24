"use client";

import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useTable } from "@/lib/data/store";
import { completionRows, exercisePoints, sessionSummaries } from "@/lib/data/history";
import { choreStreaks, lateDaysByCompletion, lateness, perDay, progress, type CompletionRow } from "@/lib/domain/stats";
import { addDays, daysBetween, startOfDay, startOfWeek } from "@/lib/domain/dates";
import { durationMinutes, formatWeight } from "@/lib/domain/format";
import { hhmm, lateDays, shortDate } from "@/lib/domain/labels";
import { usePageTitle } from "@/components/AppShell";
import { useCommitments } from "@/components/Sidebar";
import { tint } from "@/components/Todos";
import { Button, EmptyText, ErrorText, Icons, Loading, SectionHeading, SelectField, TextTabs, useNow } from "@/components/ui";

type Tab = "tasks" | "workouts";

/** History (web spec, "History: Tasks" and "History: Workouts"). */
export default function HistoryPage() {
  usePageTitle("History");
  const params = useSearchParams();
  const router = useRouter();
  const tab: Tab = params.get("tab") === "workouts" || params.get("tab") === "progress" ? "workouts" : "tasks";
  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-wrap items-baseline gap-x-8 gap-y-2">
        <h1 className="text-title-screen">History</h1>
        <TextTabs label="History" size="body" value={tab} onChange={(t) => router.replace(`/history?tab=${t}`, { scroll: false })}
          items={[{ id: "tasks", label: "Tasks" }, { id: "workouts", label: "Workouts" }]} />
      </header>
      {tab === "tasks" ? <TasksTab /> : <WorkoutsTab />}
    </div>
  );
}

// ---------------- Tasks ----------------

const RANGES = [
  { id: "12", label: "12 weeks" },
  { id: "26", label: "6 months" },
  { id: "52", label: "1 year" },
] as const;
type Range = (typeof RANGES)[number]["id"];

const longDay = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long" });
const monthInitial = new Intl.DateTimeFormat("en-GB", { month: "narrow" });

/** Heat steps as on the phone (1, 2–3, 4–5, 6–8, 9+), mixed from fill toward ink so themes follow. */
const SHADES = [0, 20, 40, 60, 80, 100];
const shade = (n: number) => (n >= 9 ? 5 : n >= 6 ? 4 : n >= 4 ? 3 : n >= 2 ? 2 : n >= 1 ? 1 : 0);
const shadeColor = (step: number) => (step === 0 ? "var(--fill)" : `color-mix(in srgb, var(--ink) ${SHADES[step]}%, var(--fill))`);

function TasksTab() {
  const completionsQ = useTable("completions");
  const todosQ = useTable("todos");
  const commitmentsQ = useTable("commitments");
  const live = useCommitments();
  const now = useNow();
  const [range, setRange] = useState<Range>("12");
  const [commitment, setCommitment] = useState("all");
  const [day, setDay] = useState<number | null>(null);
  const [days, setDays] = useState(30);

  const rows = useMemo(
    () => completionRows(completionsQ.data ?? [], todosQ.data ?? [], commitmentsQ.data ?? []),
    [completionsQ.data, todosQ.data, commitmentsQ.data],
  );
  const late = useMemo(() => lateDaysByCompletion(rows), [rows]);
  const lateFlags = useMemo(() => lateness(rows), [rows]);
  const streaks = useMemo(() => choreStreaks(rows, now), [rows, now]);

  if (completionsQ.isPending || todosQ.isPending || commitmentsQ.isPending) return <Loading />;
  const error = completionsQ.error ?? todosQ.error ?? commitmentsQ.error;
  if (error) return <ErrorText error={error} />;
  if (rows.length === 0) return <EmptyText>Finished todos and chores will show up here.</EmptyText>;

  const withRows = new Set(rows.map((r) => r.commitmentId));
  const filters = [{ id: "all", label: "All" }, ...live.filter((c) => c.type !== "workout" && withRows.has(c.id)).map((c) => ({ id: c.id, label: c.name }))];
  const filtered = rows.filter((r) => commitment === "all" || r.commitmentId === commitment);
  const weekStart = startOfWeek(now);
  const thisWeek = filtered.filter((r) => r.doneAt >= weekStart).length;

  // This week by commitment: done and late.
  const table = live
    .filter((c) => c.type !== "workout")
    .map((c) => {
      const mine = rows.filter((r) => r.commitmentId === c.id && r.doneAt >= weekStart);
      return { c, done: mine.length, late: mine.filter((r) => lateFlags.get(r.completionId) === true).length };
    })
    .filter((x) => x.done > 0);

  const timeline = filtered.filter((r) => (day == null ? r.doneAt >= startOfDay(now) - days * 86_400_000 : startOfDay(r.doneAt) === day));
  const older = day == null && filtered.some((r) => r.doneAt < startOfDay(now) - days * 86_400_000);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
        <TextTabs label="Commitment" value={commitment} onChange={(id) => { setCommitment(id); setDay(null); }} items={filters} />
        <TextTabs label="Range" value={range} onChange={setRange} items={[...RANGES]} />
      </div>

      <section className="flex flex-col gap-3" aria-label="Finished per day">
        <Heatmap counts={perDay(filtered)} weeks={Number(range)} now={now} selected={day} onSelect={setDay} />
        <div className="flex items-center justify-between gap-4">
          <span className="text-meta text-text-strong">{thisWeek} this week</span>
          <span className="flex items-center gap-2 text-micro text-muted" aria-hidden>
            Less
            <span className="h-2 w-[88px] rounded-full" style={{ background: `linear-gradient(to right, ${[1, 2, 3, 4, 5].map(shadeColor).join(", ")})` }} />
            More
          </span>
        </div>
        {day != null && (
          <p className="flex items-center gap-2 text-meta text-text-strong">
            Showing {shortDate(day)}
            <Button variant="text" onClick={() => setDay(null)}>Show all</Button>
          </p>
        )}
      </section>

      {table.length > 0 && day == null && commitment === "all" && (
        <section className="flex flex-col gap-2">
          <SectionHeading>This week by commitment</SectionHeading>
          <table className="w-full max-w-md text-body">
            <thead>
              <tr className="text-left text-caption text-muted">
                <th className="py-1 font-medium">Commitment</th>
                <th className="w-20 py-1 text-right font-medium">Done</th>
                <th className="w-20 py-1 text-right font-medium">Late</th>
              </tr>
            </thead>
            <tbody>
              {table.map(({ c, done, late }) => (
                <tr key={c.id}>
                  <td className="py-1.5">{c.name}</td>
                  <td className="py-1.5 text-right tabular-nums">{done}</td>
                  <td className="py-1.5 text-right tabular-nums text-danger">{late || ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {timeline.length === 0 ? (
        <EmptyText>{day != null ? "Nothing finished that day." : "Nothing finished in the last month."}</EmptyText>
      ) : (
        <Timeline rows={timeline} now={now} late={late} streaks={streaks} />
      )}
      {older && <Button variant="text" className="self-start" onClick={() => setDays((d) => d + 30)}>Show earlier</Button>}
    </div>
  );
}

/** Grouped by day, 32 px apart; notes only for exceptions ("1 day late", "6 in a row"). */
function Timeline({ rows, now, late, streaks }: { rows: CompletionRow[]; now: number; late: Map<string, number>; streaks: Map<string, number> }) {
  const groups = new Map<number, CompletionRow[]>();
  for (const r of rows) {
    const d = startOfDay(r.doneAt);
    groups.set(d, [...(groups.get(d) ?? []), r]);
  }
  const today = startOfDay(now);
  // The streak note goes on a chore's latest completion only.
  const latest = new Set<string>();
  const seen = new Set<string>();
  for (const r of rows) if (!seen.has(r.todoId)) { seen.add(r.todoId); latest.add(r.completionId); }

  return (
    <div className="flex flex-col gap-8">
      {[...groups].map(([d, list]) => (
        <section key={d} className="flex flex-col gap-1">
          <SectionHeading className="px-2">{d === today ? "Today" : d === addDays(today, -1) ? "Yesterday" : longDay.format(d)}</SectionHeading>
          <ul>
            {list.map((r) => {
              const l = late.get(r.completionId);
              const streak = latest.has(r.completionId) ? streaks.get(r.todoId) ?? 0 : 0;
              const note = l != null ? { text: l === 0 ? "Late" : lateDays(l), danger: true } : streak > 1 ? { text: `${streak} in a row`, danger: false } : null;
              return (
                <li key={r.completionId} className="flex min-h-12 items-center gap-3 rounded-xl px-2 py-2">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-body">{r.title}</span>
                    <span className="block truncate text-meta" style={tint(r.commitmentColor)}>{r.commitmentName}, {hhmm(r.doneAt)}</span>
                  </span>
                  {note && <span className={`shrink-0 text-meta ${note.danger ? "text-danger" : "text-muted"}`}>{note.text}</span>}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

/**
 * Heatmap: weeks as columns, Mon–Sun rows, month initials above each month's first week. Cells are
 * buttons ("Thu 20 Nov, 5 finished"); arrow keys move between days, Enter or click filters.
 */
function Heatmap({ counts, weeks, now, selected, onSelect }: { counts: Map<number, number>; weeks: number; now: number; selected: number | null; onSelect: (d: number | null) => void }) {
  const today = startOfDay(now);
  const first = addDays(startOfWeek(now), -7 * (weeks - 1));
  const size = weeks >= 52 ? 10 : 12;
  const [focus, setFocus] = useState(selected ?? today);
  const grid = useRef<HTMLDivElement>(null);
  const scroller = useRef<HTMLDivElement>(null);
  useEffect(() => { scroller.current?.scrollTo({ left: scroller.current.scrollWidth }); }, [weeks]);

  function onKey(e: KeyboardEvent) {
    const step = { ArrowUp: -1, ArrowDown: 1, ArrowLeft: -7, ArrowRight: 7 }[e.key];
    if (step == null) return;
    e.preventDefault();
    const next = addDays(focus, step);
    if (next < first || next > today) return;
    setFocus(next);
    requestAnimationFrame(() => grid.current?.querySelector<HTMLButtonElement>(`[data-day="${next}"]`)?.focus());
  }

  const named = (d: number) => `${shortDate(d)}, ${counts.get(d) ?? 0} finished`;
  return (
    <div ref={scroller} className="overflow-x-auto pb-1">
      <div ref={grid} role="grid" aria-label="Finished per day" className="inline-flex gap-[3px]" onKeyDown={onKey}>
        {Array.from({ length: weeks }, (_, w) => {
          const monday = addDays(first, w * 7);
          const newMonth = w === 0 || new Date(monday).getMonth() !== new Date(addDays(monday, -7)).getMonth();
          return (
            <div key={w} role="row" className="flex flex-col gap-[3px]">
              <span aria-hidden className="h-4 text-micro leading-none text-muted" style={{ width: size }}>{newMonth ? monthInitial.format(monday) : ""}</span>
              {Array.from({ length: 7 }, (_, d) => {
                const date = addDays(monday, d);
                if (date > today) return <span key={d} style={{ width: size, height: size }} />;
                const n = counts.get(date) ?? 0;
                return (
                  <button
                    key={d}
                    type="button"
                    role="gridcell"
                    data-day={date}
                    tabIndex={date === focus ? 0 : -1}
                    aria-label={named(date)}
                    aria-selected={date === selected}
                    title={named(date)}
                    onFocus={() => setFocus(date)}
                    onClick={() => onSelect(date === selected ? null : date)}
                    className={`cursor-pointer rounded-[3px] ${date === selected ? "ring-2 ring-ink ring-offset-1 ring-offset-background" : ""}`}
                    style={{ width: size, height: size, background: shadeColor(shade(n)) }}
                  />
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ---------------- Workouts ----------------

const dayMonth = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

function WorkoutsTab() {
  const sessionsQ = useTable("workout_sessions");
  const setsQ = useTable("workout_sets");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const now = useNow();
  const summaries = useMemo(
    () => sessionSummaries(sessionsQ.data ?? [], setsQ.data ?? [], routinesQ.data ?? []),
    [sessionsQ.data, setsQ.data, routinesQ.data],
  );
  const points = useMemo(
    () => exercisePoints(setsQ.data ?? [], exercisesQ.data ?? [], sessionsQ.data ?? []),
    [setsQ.data, exercisesQ.data, sessionsQ.data],
  );
  const names = useMemo(() => [...new Map(points.map((p) => [p.exerciseName.toLowerCase(), p.exerciseName])).values()].sort(), [points]);
  const [selected, setSelected] = useState<string | null>(null);

  if (sessionsQ.isPending || setsQ.isPending || routinesQ.isPending || exercisesQ.isPending) return <Loading />;
  if (summaries.length === 0) return <EmptyText>Finished workouts will show up here.</EmptyText>;

  const exercise = names.find((n) => n.toLowerCase() === selected?.toLowerCase()) ?? names[0];
  const mine = exercise ? points.filter((p) => p.exerciseName.toLowerCase() === exercise.toLowerCase()) : [];
  const flagged = progress(mine);
  const best = [...mine].sort((a, b) => b.bestWeight - a.bestWeight || b.bestReps - a.bestReps)[0];
  const data = flagged.map(({ point, isPersonalBest }, i) => ({
    i, at: point.startedAt, weight: point.bestWeight, reps: point.bestReps, pb: isPersonalBest, last: i === flagged.length - 1,
  }));
  const firstPoint = data[0];
  const lastPoint = data[data.length - 1];
  const weeksAgo = firstPoint ? Math.floor(daysBetween(firstPoint.at, now) / 7) : 0;
  const ago = (at: number) => (startOfWeek(at) === startOfWeek(now) ? "This week" : weeksAgo <= 1 ? "Last week" : `${weeksAgo} weeks ago`);

  return (
    <div className="flex flex-col gap-8">
      {names.length > 0 && (
        names.length > 6 ? (
          <label className="flex max-w-64 flex-col gap-1.5">
            <span className="text-caption text-muted">Exercise</span>
            <SelectField value={exercise} onChange={(e) => setSelected(e.target.value)}>
              {names.map((n) => <option key={n} value={n}>{n}</option>)}
            </SelectField>
          </label>
        ) : (
          <TextTabs label="Exercise" value={exercise} onChange={setSelected} items={names.map((n) => ({ id: n, label: n }))} />
        )
      )}

      {best && (
        <section className="flex flex-col gap-4">
          <div>
            <p className="text-display-l tabular-nums">{formatWeight(best.bestWeight)} × {best.bestReps}</p>
            <p className="text-meta text-muted">Best set, since {dayMonth.format(mine[0].startedAt)}</p>
          </div>
          {data.length > 1 && (
            <figure className="flex flex-col gap-1">
              <div className="h-40" role="img"
                aria-label={`Best weight for ${exercise}: ${formatWeight(firstPoint.weight)} on ${dayMonth.format(firstPoint.at)}, ${formatWeight(lastPoint.weight)} on ${dayMonth.format(lastPoint.at)}, best ${formatWeight(best.bestWeight)}.`}>
                <ResponsiveContainer>
                  <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
                    <XAxis dataKey="i" hide />
                    <YAxis hide domain={["dataMin - 5", "dataMax + 5"]} />
                    <ReferenceLine y={Math.max(0, Math.min(...data.map((d) => d.weight)) - 5)} stroke="var(--track)" />
                    <Tooltip
                      cursor={{ stroke: "var(--track)" }}
                      content={({ active, payload }) => {
                        const p = active && payload?.[0]?.payload as (typeof data)[number] | undefined;
                        if (!p) return null;
                        return (
                          <div className="rounded-xl border border-track bg-surface p-2 text-meta text-ink">
                            {dayMonth.format(p.at)}, {formatWeight(p.weight)} × {p.reps}
                          </div>
                        );
                      }}
                    />
                    <Line type="monotone" dataKey="weight" stroke="var(--ink)" strokeWidth={2} isAnimationActive={false}
                      activeDot={{ r: 4, fill: "var(--ink)", stroke: "var(--surface)" }}
                      dot={(props) => {
                        const { cx, cy, payload, index } = props as { cx: number; cy: number; payload: (typeof data)[number]; index: number };
                        if (!payload.pb && !payload.last) return <g key={index} />;
                        return <circle key={index} cx={cx} cy={cy} r={payload.pb ? 3 : 2} fill="var(--ink)" />;
                      }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <figcaption className="flex justify-between text-micro text-muted" aria-hidden>
                <span>{formatWeight(firstPoint.weight)}, {ago(firstPoint.at)}</span>
                <span>{startOfWeek(lastPoint.at) === startOfWeek(now) ? "This week" : dayMonth.format(lastPoint.at)}</span>
              </figcaption>
            </figure>
          )}
        </section>
      )}

      <section className="flex flex-col gap-1">
        <SectionHeading className="px-2">Sessions</SectionHeading>
        <ul>
          {summaries.map((s) => (
            <li key={s.session.id}>
              <Link href={`/session?id=${s.session.id}`} className="flex min-h-12 items-center gap-3 rounded-xl px-2 py-2.5 hover:bg-hover">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-body">{s.routineName}</span>
                  <span className="block text-meta text-muted">{durationMinutes(s.session.started_at, s.session.ended_at)} min, {s.setCount === 1 ? "1 set" : `${s.setCount} sets`}</span>
                </span>
                <span className="text-meta tabular-nums text-muted">{shortDate(s.session.started_at)}</span>
                <span className="text-faint">{Icons.chevron}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
