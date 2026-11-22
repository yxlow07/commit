"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useTable } from "@/lib/data/store";
import { completionRows, exercisePoints, sessionSummaries } from "@/lib/data/history";
import { commitmentStats, isRecurringRow, perDay, progress, type CompletionRow } from "@/lib/domain/stats";
import { addDays, startOfDay, startOfWeek } from "@/lib/domain/dates";
import { durationMinutes, formatVolume, formatWeight } from "@/lib/domain/format";
import { Card, Chip, ColorDot, Empty, ErrorText, Spinner, useNow } from "@/components/ui";

const TABS = [
  { id: "tasks", label: "Tasks" },
  { id: "workouts", label: "Workouts" },
  { id: "progress", label: "Progress" },
] as const;

/** History (F8 / F10.4): tasks timeline + heatmap + stats, workout sessions, exercise progress. */
export default function HistoryPage() {
  const params = useSearchParams();
  const router = useRouter();
  const tab = TABS.find((t) => t.id === params.get("tab"))?.id ?? "tasks";
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">History</h1>
      <div className="flex gap-1.5" role="tablist">
        {TABS.map((t) => (
          <Chip key={t.id} selected={tab === t.id} onClick={() => router.replace(`/history?tab=${t.id}`, { scroll: false })}>{t.label}</Chip>
        ))}
      </div>
      {tab === "tasks" ? <TasksTab /> : tab === "workouts" ? <WorkoutsTab /> : <ProgressTab />}
    </div>
  );
}

// ---------------- Tasks ----------------

const dayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" });
const longDayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
const timeFmt = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });

function TasksTab() {
  const completionsQ = useTable("completions");
  const todosQ = useTable("todos");
  const commitmentsQ = useTable("commitments");
  const now = useNow();
  const [weeks, setWeeks] = useState(26);
  const [commitment, setCommitment] = useState<string | null>(null);
  const [day, setDay] = useState<number | null>(null);
  const [limit, setLimit] = useState(100);

  const rows = useMemo(
    () => completionRows(completionsQ.data ?? [], todosQ.data ?? [], commitmentsQ.data ?? []),
    [completionsQ.data, todosQ.data, commitmentsQ.data],
  );
  if (completionsQ.isPending || todosQ.isPending || commitmentsQ.isPending) return <Spinner />;
  const error = completionsQ.error ?? todosQ.error ?? commitmentsQ.error;
  if (error) return <ErrorText error={error} />;

  const byCommitment = rows.filter((r) => !commitment || r.commitmentId === commitment);
  const filtered = byCommitment.filter((r) => day == null || startOfDay(r.doneAt) === day);
  const stats = commitmentStats(rows, now);

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="font-semibold">Completions</h2>
          <div className="flex gap-1.5">
            {[26, 52].map((w) => <Chip key={w} selected={weeks === w} onClick={() => setWeeks(w)}>{w === 26 ? "6 months" : "1 year"}</Chip>)}
          </div>
        </div>
        <Heatmap counts={perDay(byCommitment)} weeks={weeks} now={now} selected={day} onSelect={setDay} />
        {/* Gradient legend, as on the phone: what the shades mean, without a sentence. */}
        <div className="mt-2 flex items-center justify-end gap-2 text-xs text-fg-muted" aria-hidden>
          Less
          <span className="h-2 w-24 rounded-full" style={{ background: "linear-gradient(to right, var(--surface-2), color-mix(in srgb, var(--primary) 40%, transparent), var(--primary))" }} />
          More
        </div>
        {day != null && (
          <p className="mt-2 text-xs text-fg-muted">Showing {longDayFmt.format(day)} · <button className="text-primary underline" onClick={() => setDay(null)}>show all</button></p>
        )}
      </Card>

      {stats.length > 0 && (
        <>
          <div className="flex flex-wrap gap-1.5">
            <Chip selected={commitment == null} onClick={() => setCommitment(null)}>All</Chip>
            {[...stats].sort((a, b) => a.name.localeCompare(b.name)).map((s) => (
              <Chip key={s.commitmentId} selected={commitment === s.commitmentId} onClick={() => setCommitment(commitment === s.commitmentId ? null : s.commitmentId)}>
                <ColorDot color={s.color} />{s.name}
              </Chip>
            ))}
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {stats.filter((s) => !commitment || s.commitmentId === commitment).map((s) => (
              <Card key={s.commitmentId}>
                <p className="mb-1 flex items-center gap-2 font-medium"><ColorDot color={s.color} />{s.name}</p>
                <p className="text-sm">{s.doneThisWeek} done this week</p>
                {s.onTime + s.late > 0 && <p className="text-xs text-fg-muted">{s.onTime} on time · {s.late} late</p>}
                {s.choreStreaks.slice(0, 3).map(([title, n]) => <p key={title} className="text-xs text-fg-muted">{title}: {n} in a row</p>)}
              </Card>
            ))}
          </div>
        </>
      )}

      {filtered.length === 0 ? (
        <Empty>{rows.length === 0 ? "Completed todos and chores will show up here." : "Nothing completed for this filter."}</Empty>
      ) : (
        <Timeline rows={filtered.slice(0, limit)} now={now} />
      )}
      {filtered.length > limit && <button className="self-center text-sm text-primary underline" onClick={() => setLimit(limit + 200)}>Show more</button>}
    </div>
  );
}

function Timeline({ rows, now }: { rows: CompletionRow[]; now: number }) {
  const groups = new Map<number, CompletionRow[]>();
  for (const r of rows) {
    const d = startOfDay(r.doneAt);
    groups.set(d, [...(groups.get(d) ?? []), r]);
  }
  const today = startOfDay(now);
  return (
    <div className="flex flex-col gap-3">
      {[...groups].map(([d, list]) => (
        <section key={d}>
          <h3 className="mb-1 text-sm font-semibold text-primary">{d === today ? "Today" : d === addDays(today, -1) ? "Yesterday" : dayFmt.format(d)}</h3>
          <ul className="divide-y divide-line rounded-2xl border border-line bg-surface">
            {list.map((r) => (
              <li key={r.completionId} className="flex items-center gap-3 px-4 py-2.5">
                <ColorDot color={r.commitmentColor} />
                <span className="min-w-0 flex-1 truncate">{r.title}</span>
                <span className="text-xs text-fg-muted">{r.commitmentName} · {timeFmt.format(r.doneAt)}{isRecurringRow(r) ? " · chore" : ""}</span>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** Calendar heatmap (F8.2): a column per week (Mon–Sun), newest on the right; click a day to filter. */
function Heatmap({ counts, weeks, now, selected, onSelect }: { counts: Map<number, number>; weeks: number; now: number; selected: number | null; onSelect: (d: number | null) => void }) {
  const today = startOfDay(now);
  const first = addDays(startOfWeek(now), -7 * (weeks - 1));
  return (
    <div className="flex gap-[3px] overflow-x-auto pb-1" role="grid" aria-label="Completions per day">
      {Array.from({ length: weeks }, (_, w) => (
        <div key={w} className="flex flex-col gap-[3px]" role="row">
          {Array.from({ length: 7 }, (_, d) => {
            const date = addDays(first, w * 7 + d);
            const n = counts.get(date) ?? 0;
            const future = date > today;
            return (
              <button
                key={d}
                type="button"
                role="gridcell"
                disabled={future}
                aria-label={`${longDayFmt.format(date)}: ${n} completed`}
                title={`${dayFmt.format(date)}: ${n}`}
                onClick={() => onSelect(date === selected ? null : date)}
                className={`h-3.5 w-3.5 rounded-[3px] ${date === selected ? "ring-2 ring-fg" : ""}`}
                style={{ background: future ? "transparent" : n === 0 ? "var(--surface-2)" : `color-mix(in srgb, var(--primary) ${heat(n)}%, transparent)` }}
              />
            );
          })}
        </div>
      ))}
    </div>
  );
}

/** Same steps as the phone: 1, 2–3, 4–5, 6–8, 9+ (one completion must stand out). */
const heat = (n: number) => (n >= 9 ? 100 : n >= 6 ? 85 : n >= 4 ? 70 : n >= 2 ? 55 : 40);

// ---------------- Workouts ----------------

function useWorkoutData() {
  const sessionsQ = useTable("workout_sessions");
  const setsQ = useTable("workout_sets");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const pending = sessionsQ.isPending || setsQ.isPending || routinesQ.isPending || exercisesQ.isPending;
  const error = sessionsQ.error ?? setsQ.error ?? routinesQ.error ?? exercisesQ.error;
  return { pending, error, sessions: sessionsQ.data ?? [], sets: setsQ.data ?? [], routines: routinesQ.data ?? [], exercises: exercisesQ.data ?? [] };
}

function WorkoutsTab() {
  const { pending, error, sessions, sets, routines } = useWorkoutData();
  const summaries = useMemo(() => sessionSummaries(sessions, sets, routines), [sessions, sets, routines]);
  if (pending) return <Spinner />;
  if (error) return <ErrorText error={error} />;
  if (summaries.length === 0) return <Empty>Finished workouts will show up here.</Empty>;
  return (
    <ul className="divide-y divide-line rounded-2xl border border-line bg-surface">
      {summaries.map((s) => (
        <li key={s.session.id}>
          <Link href={`/session?id=${s.session.id}`} className="block px-4 py-3 hover:bg-surface-2">
            <span className="block font-medium">{s.routineName}</span>
            <span className="block text-xs text-fg-muted">
              {dayFmt.format(s.session.started_at)} · {durationMinutes(s.session.started_at, s.session.ended_at)} min · {formatVolume(s.volume)} · {s.setCount} sets
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

// ---------------- Progress ----------------

const shortDate = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

function ProgressTab() {
  const { pending, error, sessions, sets, exercises } = useWorkoutData();
  const points = useMemo(() => exercisePoints(sets, exercises, sessions), [sets, exercises, sessions]);
  const names = useMemo(() => [...new Map(points.map((p) => [p.exerciseName.toLowerCase(), p.exerciseName])).values()].sort(), [points]);
  const [selected, setSelected] = useState<string | null>(null);

  if (pending) return <Spinner />;
  if (error) return <ErrorText error={error} />;
  if (names.length === 0) return <Empty>Log a few workouts to see your progress.</Empty>;

  const exercise = names.find((n) => n.toLowerCase() === selected?.toLowerCase()) ?? names[0];
  const data = progress(points.filter((p) => p.exerciseName.toLowerCase() === exercise.toLowerCase())).map((p, i) => ({
    i, date: shortDate.format(p.point.startedAt), weight: p.point.bestWeight, volume: Math.round(p.point.volume), reps: p.point.bestReps, pb: p.isPersonalBest, at: p.point.startedAt,
  }));
  const pbs = data.filter((d) => d.pb).reverse();

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-1.5">
        {names.map((n) => <Chip key={n} selected={n === exercise} onClick={() => setSelected(n)}>{n}</Chip>)}
      </div>
      <Card>
        <h2 className="mb-2 font-semibold">Best weight (kg)</h2>
        <div className="h-64">
          <ResponsiveContainer>
            <LineChart data={data} margin={{ top: 10, right: 16, bottom: 0, left: -10 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
              <XAxis dataKey="date" stroke="var(--fg-muted)" fontSize={12} />
              <YAxis stroke="var(--fg-muted)" fontSize={12} />
              <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--line)", color: "var(--fg)" }}
                formatter={(v) => [formatWeight(Number(v)), "Best"]} />
              <Line type="monotone" dataKey="weight" stroke="var(--primary)" strokeWidth={2}
                dot={(props) => {
                  const { cx, cy, payload, index } = props as { cx: number; cy: number; payload: { pb: boolean }; index: number };
                  return <circle key={index} cx={cx} cy={cy} r={payload.pb ? 7 : 3.5} fill={payload.pb ? "#e68a00" : "var(--primary)"} stroke="var(--surface)" strokeWidth={1.5} />;
                }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <p className="mt-1 text-xs text-fg-muted">Larger orange dots are personal bests.</p>
      </Card>
      <Card>
        <h2 className="mb-2 font-semibold">Volume per session (kg)</h2>
        <div className="h-56">
          <ResponsiveContainer>
            <BarChart data={data} margin={{ top: 10, right: 16, bottom: 0, left: -10 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
              <XAxis dataKey="date" stroke="var(--fg-muted)" fontSize={12} />
              <YAxis stroke="var(--fg-muted)" fontSize={12} />
              <Tooltip contentStyle={{ background: "var(--surface)", border: "1px solid var(--line)", color: "var(--fg)" }}
                formatter={(v) => [formatVolume(Number(v)), "Volume"]} cursor={{ fill: "var(--surface-2)" }} />
              <Bar dataKey="volume" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>
      {pbs.length > 0 && (
        <Card>
          <h2 className="mb-2 font-semibold">Personal bests</h2>
          <ul className="divide-y divide-line">
            {pbs.map((p) => (
              <li key={p.at} className="flex justify-between py-2 text-sm">
                <span>{formatWeight(p.weight)} × {p.reps}</span>
                <span className="text-fg-muted">{longDayFmt.format(p.at)}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
