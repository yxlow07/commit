"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useTable } from "@/lib/data/store";
import { addDays, daysBetween, startOfDay } from "@/lib/domain/dates";
import { cssColor, weekdayIndex } from "@/lib/domain/format";
import { isLate, openLabel, relativeDay } from "@/lib/domain/labels";
import { openItems, recurrenceLabel, type TodoItem } from "@/lib/domain/recurrence";
import type { Commitment, Routine } from "@/lib/domain/types";
import { usePageTitle } from "@/components/AppShell";
import { CommitmentDialog } from "@/components/CommitmentDialog";
import { usePanel } from "@/components/EditPanel";
import { useCommitments } from "@/components/Sidebar";
import { InlineAdd, TodoRow, TodoSection, tint, useTicks, type RowData } from "@/components/Todos";
import { EmptyText, IconButton, Icons, Loading, RoundAdd, SectionHeading, usePhone, useNow } from "@/components/ui";

const weekdayLong = new Intl.DateTimeFormat("en-GB", { weekday: "long" });
const monthLong = new Intl.DateTimeFormat("en-GB", { month: "long" });

const planned = (r: Routine, day: number) => (r.planned_days & (1 << weekdayIndex(day))) !== 0;

/**
 * Today (web spec, "Today"): an agenda across all commitments (the sidebar already lists them):
 * Late, Today, Next 7 days, and the workouts planned today. Below 600 px, the phone app's Today.
 */
export default function TodayPage() {
  usePageTitle("Today");
  const params = useSearchParams();
  const router = useRouter();
  const phone = usePhone();
  const panel = usePanel();
  const now = useNow();
  const commitments = useCommitments();
  const todosQ = useTable("todos");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");

  // Old links (?c=<id>) open the commitment page.
  const legacy = params.get("c");
  useEffect(() => { if (legacy) router.replace(`/c?id=${legacy}`); }, [legacy, router]);

  const todoCommitments = commitments.filter((c) => c.type !== "workout");
  const byId = new Map(commitments.map((c) => [c.id, c]));
  const open = useMemo(
    () => openItems((todosQ.data ?? []).filter((t) => byId.get(t.commitment_id)?.type === "standard"), now),
    [todosQ.data, now, commitments], // eslint-disable-line react-hooks/exhaustive-deps
  );
  const routines = (routinesQ.data ?? []).filter((r) => byId.get(r.commitment_id)?.type === "workout");
  const today = startOfDay(now);
  const plannedToday = routines.filter((r) => planned(r, today)).sort((a, b) => a.sort_order - b.sort_order);

  const late = open.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
  const dueToday = open.filter((i) => !isLate(i.dueAt, i.hasTime, now) && i.dueAt != null && startOfDay(i.dueAt) === today).length;
  const summary = [
    dueToday === 0 && late === 0 ? "Nothing due today." : late === 0 ? `${dueToday} due today.` : dueToday === 0 ? `${late} overdue.` : `${dueToday} due today, ${late} overdue.`,
    plannedToday.length ? `${plannedToday.map((r) => r.name).join(" and ")} planned.` : "",
  ].filter(Boolean).join(" ");
  const loading = todosQ.isPending || routinesQ.isPending;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-4">
        <div className="flex items-center gap-4">
          <div className="flex gap-4 [align-items:last_baseline]">
            <span className="text-display-xl tabular-nums">{new Date(now).getDate()}</span>
            <span className="flex flex-col">
              <span className="text-body-l font-medium">{weekdayLong.format(now)}</span>
              <span className="text-body-l text-muted">{monthLong.format(now)}</span>
            </span>
          </div>
          {phone && <RoundAdd label="Add todo" className="ml-auto" onClick={() => panel.openNew()} />}
        </div>
        {!loading && <p className="text-body-l text-text-strong">{summary}</p>}
      </header>

      {loading ? <Loading /> : phone ? (
        <PhoneToday commitments={commitments} open={open} routines={routines} now={now} />
      ) : (
        <Agenda open={open} commitments={byId} todoCommitments={todoCommitments} plannedToday={plannedToday}
          exerciseCount={(id) => (exercisesQ.data ?? []).filter((e) => e.routine_id === id).length} now={now} />
      )}
    </div>
  );
}

function Agenda({ open, commitments, todoCommitments, plannedToday, exerciseCount, now }: {
  open: TodoItem[]; commitments: Map<string, Commitment>; todoCommitments: Commitment[]; plannedToday: Routine[];
  exerciseCount: (routineId: string) => number; now: number;
}) {
  const ticks = useTicks();
  const items = ticks.merge(open);
  const today = startOfDay(now);
  const row = (item: TodoItem): RowData => {
    const c = commitments.get(item.todo.commitment_id);
    return {
      item,
      due: openLabel(item.dueAt, item.hasTime, now),
      second: [c?.name, item.isRecurring ? recurrenceLabel(item.todo) : ""].filter(Boolean).join(", "),
      secondStyle: c ? tint(c.color) : undefined,
    };
  };
  const lateItems = items.filter((i) => isLate(i.dueAt, i.hasTime, now));
  const todayItems = items.filter((i) => !lateItems.includes(i) && i.dueAt != null && startOfDay(i.dueAt) === today);
  const weekItems = items.filter((i) => i.dueAt != null && !lateItems.includes(i) && !todayItems.includes(i) && daysBetween(today, i.dueAt) <= 7);
  const render = (list: TodoItem[]) => list.map((i) => {
    const t = ticks.ticked.get(i.todo.id);
    return <TodoRow key={i.todo.id} row={row(i)} checked={!!t} fading={t?.fading} onToggle={(done) => (done ? ticks.tick(i) : ticks.untick(i))} />;
  });

  return (
    <div className="flex flex-col gap-6">
      {lateItems.length > 0 && <TodoSection title="Late" danger>{render(lateItems)}</TodoSection>}
      {todayItems.length > 0 && <TodoSection title="Today">{render(todayItems)}</TodoSection>}
      {lateItems.length === 0 && todayItems.length === 0 && <EmptyText className="px-2">Nothing due today.</EmptyText>}
      {weekItems.length > 0 && <TodoSection title="Next 7 days">{render(weekItems)}</TodoSection>}

      {plannedToday.length > 0 && (
        <section className="flex flex-col gap-1">
          <SectionHeading className="px-2">Planned today</SectionHeading>
          <ul>
            {plannedToday.map((r) => {
              const c = commitments.get(r.commitment_id);
              const n = exerciseCount(r.id);
              return (
                <li key={r.id}>
                  <Link href={`/routine?id=${r.id}`} className="flex min-h-12 items-center gap-3 rounded-xl px-2 py-2.5 hover:bg-hover">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-body">{r.name}</span>
                      <span className="block truncate text-meta" style={c ? tint(c.color) : undefined}>{c?.name}, {n === 1 ? "1 exercise" : `${n} exercises`}</span>
                    </span>
                    <span className="text-faint">{Icons.chevron}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {todoCommitments.length > 0 && <InlineAdd commitments={todoCommitments} />}
    </div>
  );
}

/** Below 600 px: the phone app's Today, a row per commitment with its count and a chevron. */
function PhoneToday({ commitments, open, routines, now }: { commitments: Commitment[]; open: TodoItem[]; routines: Routine[]; now: number }) {
  const [dialog, setDialog] = useState(false);

  const today = startOfDay(now);
  const detail = (c: Commitment): string => {
    if (c.type === "workout") {
      const mine = routines.filter((r) => r.commitment_id === c.id);
      if (!mine.length) return "No routines yet";
      const t = mine.find((r) => planned(r, today));
      if (t) return `${t.name} today`;
      for (let i = 1; i <= 7; i++) {
        const d = addDays(today, i);
        const r = mine.find((x) => planned(x, d));
        if (r) return `Next: ${r.name} ${relativeDay(d, now)}`;
      }
      return "No workout planned";
    }
    const items = open.filter((i) => i.todo.commitment_id === c.id);
    if (!items.length) return "Nothing open";
    const late = items.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
    const next = items.filter((i) => i.dueAt != null && !isLate(i.dueAt, i.hasTime, now)).sort((a, b) => a.dueAt! - b.dueAt!)[0];
    const nextText = next ? `next due ${relativeDay(startOfDay(next.dueAt!), now)}` : null;
    if (late && nextText) return `${late} overdue, ${nextText}`;
    if (late) return `${late} overdue`;
    return nextText ? nextText[0].toUpperCase() + nextText.slice(1) : "No due dates";
  };

  return (
    <section className="flex flex-col gap-1">
      <div className="flex items-center justify-between">
        <SectionHeading>Commitments</SectionHeading>
        <IconButton label="New commitment" className="-mr-3" onClick={() => setDialog(true)}>{Icons.plus}</IconButton>
      </div>
      {commitments.length === 0 && <EmptyText>Add your first commitment.</EmptyText>}
      <ul>
        {commitments.map((c) => {
          const count = c.type === "workout" ? null : open.filter((i) => i.todo.commitment_id === c.id).length;
          return (
            <li key={c.id}>
              <Link href={`/c?id=${c.id}`} className="-mx-2 flex min-h-16 items-center gap-3 rounded-xl px-2 py-2 hover:bg-hover">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[1.25rem] font-medium">{c.name}</span>
                  <span className="block truncate text-meta" style={tint(c.color)}>{detail(c)}</span>
                </span>
                {count != null && <span className="text-[1.875rem] font-light tabular-nums" style={{ color: cssColor(c.color) }}>{count}</span>}
                <span className="text-faint">{Icons.chevron}</span>
              </Link>
            </li>
          );
        })}
      </ul>
      <CommitmentDialog open={dialog} onClose={() => setDialog(false)} commitment={null}
        nextSortOrder={commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0} />
    </section>
  );
}
