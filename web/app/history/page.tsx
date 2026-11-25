"use client";

import { useMemo, useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useTable } from "@/lib/data/store";
import { completionRows, exercisePoints, sessionSummaries } from "@/lib/data/history";
import { choreStreaks, lateDaysByCompletion, lateness, perDay, progress, type CompletionRow } from "@/lib/domain/stats";
import { addDays, daysBetween, startOfDay, startOfWeek } from "@/lib/domain/dates";
import { cssColor, durationMinutes, formatVolume, formatWeight } from "@/lib/domain/format";
import { hhmm, lateDays, shortDate } from "@/lib/domain/labels";
import { usePageTitle } from "@/components/AppShell";
import { useCommitments } from "@/components/Nav";
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
      <header className="flex flex-wrap items-end justify-between gap-x-8 gap-y-2">
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

/** The app's heat shades, none to most: fill, track, faint, muted, ink (themes follow). */
const HEAT = ["var(--fill)", "var(--track)", "var(--faint)", "var(--muted)", "var(--ink)"];
const shade = (n: number) => (n >= 6 ? 4 : n >= 4 ? 3 : n >= 2 ? 2 : n >= 1 ? 1 : 0);
const shadeColor = (step: number) => HEAT[step];

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
  const filters = [{ id: "all", label: "All" }, ...live.filter((c) => c.type === "standard" && withRows.has(c.id)).map((c) => ({ id: c.id, label: c.name }))];
  const filtered = rows.filter((r) => commitment === "all" || r.commitmentId === commitment);
  const weekStart = startOfWeek(now);
  const thisWeek = filtered.filter((r) => r.doneAt >= weekStart).length;

  // This week by commitment: done and late.
  const table = live
    .filter((c) => c.type === "standard")
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
          <span className="text-body-l text-text-strong">{thisWeek} this week</span>
          <span className="flex items-center gap-2.5 text-meta text-muted" aria-hidden>
            Less
            <span className="h-2.5 w-24 rounded-full" style={{ background: `linear-gradient(to right, ${HEAT.slice(1).join(", ")})` }} />
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
        <section className="flex flex-col">
          <SectionHeading className="pb-1">This week</SectionHeading>
          <ul>
            {table.map(({ c, done, late }) => (
              <li key={c.id} className="flex min-h-14 items-center gap-4">
                <span className="min-w-0 flex-1 truncate text-body-l">{c.name}</span>
                {late > 0 && <span className="text-body tabular-nums text-danger">{late} late</span>}
                <span className="text-tab tabular-nums" style={{ color: cssColor(c.color) }} aria-label={`${done} done`}>{done}</span>
              </li>
            ))}
          </ul>
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
    <div className="flex flex-col gap-7">
      {[...groups].map(([d, list]) => (
        <section key={d} className="flex flex-col">
          <SectionHeading className="pb-1">{d === today ? "Today" : d === addDays(today, -1) ? "Yesterday" : longDay.format(d)}</SectionHeading>
          <ul>
            {list.map((r) => {
              const l = late.get(r.completionId);
              const streak = latest.has(r.completionId) ? streaks.get(r.todoId) ?? 0 : 0;
              const note = l != null ? { text: l === 0 ? "Late" : lateDays(l), danger: true } : streak > 1 ? { text: `${streak} in a row`, danger: false } : null;
              return (
                <li key={r.completionId} className="flex min-h-16 items-center gap-3 py-2">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-body-l">{r.title}</span>
                    <span className="block truncate text-meta" style={tint(r.commitmentColor)}>{r.commitmentName}, {hhmm(r.doneAt)}</span>
                  </span>
                  {note && <span className={`shrink-0 text-body ${note.danger ? "text-danger" : "text-muted"}`}>{note.text}</span>}
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
 * Heatmap as in the app: weeks as columns and Mon–Sun as rows, the grid filling the column (cells
 * square, gaps shrinking with the range), "N weeks ago" and "This week" under it. Cells are buttons
 * ("Thu 20 Nov, 5 finished"); arrow keys move between days, Enter or click filters to that day.
 */
function Heatmap({ counts, weeks, now, selected, onSelect }: { counts: Map<number, number>; weeks: number; now: number; selected: number | null; onSelect: (d: number | null) => void }) {
  const today = startOfDay(now);
  const first = addDays(startOfWeek(now), -7 * (weeks - 1));
  const gap = weeks >= 52 ? 2 : weeks >= 26 ? 4 : 6;
  const [focus, setFocus] = useState(selected ?? today);
  const grid = useRef<HTMLDivElement>(null);

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
    <div className={`flex flex-col gap-2 ${weeks <= 12 ? "max-w-[520px]" : ""}`}>
      <div ref={grid} role="grid" aria-label="Finished per day" className="grid w-full" onKeyDown={onKey}
        style={{ gridTemplateColumns: `repeat(${weeks}, minmax(0, 1fr))`, gap }}>
        {Array.from({ length: weeks }, (_, w) => {
          const monday = addDays(first, w * 7);
          return (
            <div key={w} role="row" className="flex flex-col" style={{ gap }}>
              {Array.from({ length: 7 }, (_, d) => {
                const date = addDays(monday, d);
                if (date > today) return <span key={d} className="aspect-square" />;
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
                    className={`aspect-square cursor-pointer ${weeks >= 52 ? "rounded-[2px]" : "rounded-[4px]"} ${date === selected ? "outline-2 outline-offset-1 outline-ink" : ""}`}
                    style={{ background: shadeColor(shade(n)) }}
                  />
                );
              })}
            </div>
          );
        })}
      </div>
      <div className="flex justify-between text-meta text-muted" aria-hidden>
        <span>{weeks >= 52 ? "A year ago" : weeks >= 26 ? "6 months ago" : `${weeks} weeks ago`}</span>
        <span>This week</span>
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
  const [byRoutine, setByRoutine] = useState(false);

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
            <span className="text-meta text-muted">Exercise</span>
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
            <p className="mt-2 text-body text-muted">Best set, since {dayMonth.format(mine[0].startedAt)}</p>
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
              <figcaption className="flex justify-between text-meta text-muted" aria-hidden>
                <span>{formatWeight(firstPoint.weight)}, {ago(firstPoint.at)}</span>
                <span>{startOfWeek(lastPoint.at) === startOfWeek(now) ? "This week" : dayMonth.format(lastPoint.at)}</span>
              </figcaption>
            </figure>
          )}
        </section>
      )}

      <section className="flex flex-col">
        <div className="flex min-h-11 items-end justify-between gap-4">
          <SectionHeading className="pb-2.5">Workouts</SectionHeading>
          <TextTabs label="Group" value={byRoutine ? "routine" : "sessions"} onChange={(v) => setByRoutine(v === "routine")}
            items={[{ id: "sessions", label: "Sessions" }, { id: "routine", label: "By routine" }]} />
        </div>
        {byRoutine ? (
          [...new Map(summaries.map((x) => [x.session.routine_id, x.routineName])).entries()].map(([rid, name]) => (
            <div key={rid} className="flex flex-col">
              <h3 className="pb-1 pt-5 text-body-l font-bold">{name}</h3>
              <ul>{summaries.filter((x) => x.session.routine_id === rid).map((x) => <SessionRow key={x.session.id} s={x} />)}</ul>
            </div>
          ))
        ) : (
          <ul>{summaries.map((x) => <SessionRow key={x.session.id} s={x} title={x.routineName} />)}</ul>
        )}
      </section>
    </div>
  );
}

const sessionWhen = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" });
const sessionTime = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

/** One session: the routine (or, grouped by routine, the date) over "Tue 24 Nov, 18:10, 52 min, 9 sets, 1,240 kg". */
function SessionRow({ s, title }: { s: ReturnType<typeof sessionSummaries>[number]; title?: string }) {
  const when = `${sessionWhen.format(s.session.started_at).replace(",", "")}, ${sessionTime.format(s.session.started_at)}`;
  const meta = `${durationMinutes(s.session.started_at, s.session.ended_at)} min, ${s.setCount === 1 ? "1 set" : `${s.setCount} sets`}, ${formatVolume(s.volume)}`;
  return (
    <li>
      <Link href={`/session?id=${s.session.id}`} className="-mx-3 flex min-h-16 items-center gap-3 rounded-xl px-3 py-2 hover:bg-hover">
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body-l tabular-nums">{title ?? when}</span>
          <span className="block truncate text-meta tabular-nums text-muted">{title ? `${when}, ${meta}` : meta}</span>
        </span>
        <span className="text-faint">{Icons.chevron}</span>
      </Link>
    </li>
  );
}
