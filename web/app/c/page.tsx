"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createRoutine, reorderRoutines } from "@/lib/data/mutations";
import { daysBetween, startOfDay } from "@/lib/domain/dates";
import { weekdayIndex, weekdaysLabel } from "@/lib/domain/format";
import { doneLabel, fullDate, isLate, openLabel, timeliness, weekTitle } from "@/lib/domain/labels";
import { openItems, recurrenceLabel, todoItem, type TodoItem } from "@/lib/domain/recurrence";
import type { Commitment } from "@/lib/domain/types";
import { usePageTitle } from "@/components/AppShell";
import { CommitmentDialog } from "@/components/CommitmentDialog";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { useCommitments } from "@/components/Sidebar";
import { SortableList } from "@/components/Sortable";
import { useToast } from "@/components/Toast";
import { InlineAdd, TodoRow, TodoSection, tint, useTicks, useUntickDone, type RowData } from "@/components/Todos";
import { Button, EmptyText, IconButton, Icons, Loading, PageTitle, TextTabs, fieldClass, usePhone, useNow } from "@/components/ui";

/** Commitment page (web spec, "Commitment (todo)" and "Commitment (workout)"). */
export default function CommitmentPage() {
  const params = useSearchParams();
  const id = params.get("id");
  const commitmentsQ = useTable("commitments");
  const commitments = useCommitments();
  const commitment = commitmentsQ.data?.find((c) => c.id === id);
  const [editing, setEditing] = useState(false);
  const phone = usePhone();
  usePageTitle(commitment?.name ?? "");

  if (commitmentsQ.isPending) return <Loading />;
  if (!commitment) {
    return <EmptyText>This commitment doesn&apos;t exist any more. <Link href="/" className="font-bold underline-offset-2 hover:underline">Back to Today</Link></EmptyText>;
  }

  const title = (summary: string) => (
    <PageTitle
      title={commitment.name}
      summary={summary}
      before={phone ? <Link href="/" className="-ml-2 mb-2 flex size-11 items-center justify-center rounded-xl text-ink hover:bg-hover" aria-label="Back to Today">{Icons.back}</Link> : undefined}
    >
      <IconButton label={`Edit ${commitment.name}`} onClick={() => setEditing(true)}>{Icons.pencil}</IconButton>
    </PageTitle>
  );

  return (
    <>
      {commitment.type === "workout" ? <WorkoutCommitment commitment={commitment} title={title} /> : <TodoCommitment commitment={commitment} title={title} />}
      <CommitmentDialog open={editing} onClose={() => setEditing(false)} commitment={commitment}
        nextSortOrder={commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0} />
    </>
  );
}

// ---------------- Todos ----------------

function TodoCommitment({ commitment, title }: { commitment: Commitment; title: (summary: string) => React.ReactNode }) {
  const params = useSearchParams();
  const router = useRouter();
  const todosQ = useTable("todos");
  const now = useNow();
  const tab = params.get("tab") === "done" ? "done" : "open";
  const setTab = (t: "open" | "done") => {
    const next = new URLSearchParams(params.toString());
    if (t === "done") next.set("tab", "done"); else next.delete("tab");
    router.replace(`/c?${next.toString()}`, { scroll: false });
  };

  const mine = (todosQ.data ?? []).filter((t) => t.commitment_id === commitment.id);
  const open = openItems(mine, now);
  const late = open.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
  const summary = open.length === 0 ? "Nothing open" : `${open.length} open${late ? `, ${late} overdue` : ""}`;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4">
        {title(summary)}
        <TextTabs label="Show" value={tab} onChange={setTab} items={[{ id: "open", label: "Open" }, { id: "done", label: "Done" }]} />
      </div>
      {todosQ.isPending ? <Loading /> : tab === "open"
        ? <OpenList commitment={commitment} open={open} now={now} />
        : <DoneList items={mine.map((t) => todoItem(t, now)).filter((i) => i.isDone)} now={now} />}
    </div>
  );
}

/** Open: Overdue, This week, Later, No date (empty sections hidden), as in the app. */
function OpenList({ commitment, open, now }: { commitment: Commitment; open: TodoItem[]; now: number }) {
  const ticks = useTicks();
  const items = ticks.merge(open);
  const today = startOfDay(now);

  const second = (i: TodoItem): string | undefined => {
    const parts: string[] = [];
    if (i.isRecurring) parts.push(recurrenceLabel(i.todo));
    const d = i.dueAt;
    if (d != null) {
      if (isLate(d, i.hasTime, now)) parts.push(`was due ${fullDate(d, i.hasTime, now)}`);
      else if (startOfDay(d) !== today) parts.push(fullDate(d, i.hasTime, now));
      else if (i.hasTime) parts.push("Today");
    }
    const s = parts.join(", ");
    return s ? s[0].toUpperCase() + s.slice(1) : undefined;
  };
  const row = (i: TodoItem): RowData => ({ item: i, due: openLabel(i.dueAt, i.hasTime, now), second: second(i) });
  const late = items.filter((i) => isLate(i.dueAt, i.hasTime, now));
  const rest = items.filter((i) => !late.includes(i));
  const week = rest.filter((i) => i.dueAt != null && daysBetween(today, i.dueAt) <= 6);
  const later = rest.filter((i) => i.dueAt != null && !week.includes(i));
  const undated = rest.filter((i) => i.dueAt == null);
  const render = (list: TodoItem[]) => list.map((i) => {
    const t = ticks.ticked.get(i.todo.id);
    return <TodoRow key={i.todo.id} row={row(i)} checked={!!t} fading={t?.fading} onToggle={(done) => (done ? ticks.tick(i) : ticks.untick(i))} />;
  });

  return (
    <div className="flex flex-col gap-6">
      {items.length === 0 && <EmptyText className="px-2">All done for {commitment.name}.</EmptyText>}
      {late.length > 0 && <TodoSection title="Overdue" danger>{render(late)}</TodoSection>}
      {week.length > 0 && <TodoSection title="This week">{render(week)}</TodoSection>}
      {later.length > 0 && <TodoSection title="Later">{render(later)}</TodoSection>}
      {undated.length > 0 && <TodoSection title="No date">{render(undated)}</TodoSection>}
      <InlineAdd commitmentId={commitment.id} />
    </div>
  );
}

/** Done: grouped by week, newest first; "Show earlier" loads 8 more weeks. */
function DoneList({ items, now }: { items: TodoItem[]; now: number }) {
  const untick = useUntickDone();
  const [weeks, setWeeks] = useState(8);
  const rows = items
    .map((i) => ({ i, at: (i.isRecurring ? i.todo.last_done_at : i.todo.done_at) ?? 0 }))
    .filter((r) => r.at > 0)
    .sort((a, b) => b.at - a.at);
  const cutoff = startOfDay(now) - weeks * 7 * 86_400_000;
  const shown = rows.filter((r) => r.at >= cutoff);
  const groups = new Map<string, typeof rows>();
  for (const r of shown) {
    const k = weekTitle(r.at, now);
    groups.set(k, [...(groups.get(k) ?? []), r]);
  }
  const second = (i: TodoItem, at: number) => {
    if (!i.isRecurring) return timeliness(i.todo.due_at, i.todo.has_time === 1, at);
    const next = i.dueAt != null ? openLabel(i.dueAt, false, now).text : "";
    const label = recurrenceLabel(i.todo);
    return [label[0].toUpperCase() + label.slice(1), next ? `next ${next === "Today" ? "today" : next}` : ""].filter(Boolean).join(", ");
  };

  if (rows.length === 0) return <EmptyText className="px-2">Nothing done yet.</EmptyText>;
  return (
    <div className="flex flex-col gap-6">
      {[...groups].map(([title, list]) => (
        <TodoSection key={title} title={title}>
          {list.map(({ i, at }) => {
            const late = !i.isRecurring && /late/i.test(second(i, at));
            return (
              <TodoRow key={i.todo.id} checked onToggle={() => untick(i)}
                row={{ item: i, due: { text: doneLabel(at, now), late: false }, second: second(i, at), secondDanger: late }} />
            );
          })}
        </TodoSection>
      ))}
      {shown.length < rows.length && (
        <Button variant="text" className="self-start" onClick={() => setWeeks((w) => w + 8)}>Show earlier</Button>
      )}
    </div>
  );
}

// ---------------- Workouts ----------------

function WorkoutCommitment({ commitment, title }: { commitment: Commitment; title: (summary: string) => React.ReactNode }) {
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const qc = useQueryClient();
  const toast = useToast();
  const now = useNow();
  const routines = (routinesQ.data ?? []).filter((r) => r.commitment_id === commitment.id).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
  const today = weekdayIndex(now);
  const plannedToday = routines.find((r) => (r.planned_days & (1 << today)) !== 0);
  const summary = plannedToday ? `${plannedToday.name} planned today.` : routines.length === 1 ? "1 routine" : `${routines.length} routines`;
  const count = (id: string) => (exercisesQ.data ?? []).filter((e) => e.routine_id === id).length;

  return (
    <div className="flex flex-col gap-6">
      {title(summary)}
      {routinesQ.isPending ? <Loading /> : (
        <div className="flex flex-col">
          {routines.length === 0 && <EmptyText className="mb-4 px-2">No routines yet. Add each workout day, like Push, Pull and Legs.</EmptyText>}
          <SortableList
            items={routines}
            getId={(r) => r.id}
            getLabel={(r) => r.name}
            onReorder={(ids) => reorderRoutines(qc, routines, ids).catch(() => toast({ message: "Couldn't save the order. Check your connection." }))}
            className="flex flex-col"
            renderItem={(r, handle) => {
              const n = count(r.id);
              const days = weekdaysLabel(r.planned_days);
              return (
                <div className="-ml-6 flex items-center rounded-xl">
                  {handle}
                  <Link href={`/routine?id=${r.id}`} className="flex min-h-12 min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2.5 hover:bg-hover">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-body">{r.name}</span>
                      <span className="block truncate text-meta" style={tint(commitment.color)}>
                        {[n === 1 ? "1 exercise" : `${n} exercises`, days].filter(Boolean).join(", ")}
                      </span>
                    </span>
                    <span className="text-faint">{Icons.chevron}</span>
                  </Link>
                </div>
              );
            }}
          />
          <NewRoutine commitment={commitment} nextOrder={routines.length ? Math.max(...routines.map((r) => r.sort_order)) + 1 : 0} />
        </div>
      )}
    </div>
  );
}

/** "+ New routine" at the end, like inline add; Enter creates it and opens the editor. */
function NewRoutine({ commitment, nextOrder }: { commitment: Commitment; nextOrder: number }) {
  const qc = useQueryClient();
  const router = useRouter();
  const toast = useToast();
  const online = useOnline();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");

  async function add() {
    if (!name.trim()) return;
    try {
      const r = await createRoutine(qc, commitment.id, name, nextOrder);
      router.push(`/routine?id=${r.id}`);
    } catch {
      toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: add } });
    }
  }

  if (!open) {
    return (
      <button type="button" disabled={!online} title={online ? undefined : OFFLINE_REASON} onClick={() => setOpen(true)}
        className="flex min-h-12 w-full cursor-pointer items-center gap-3 rounded-xl px-2 text-left text-body text-muted hover:bg-hover disabled:cursor-not-allowed">
        {Icons.plus} New routine
      </button>
    );
  }
  return (
    <div className="flex min-h-12 items-center gap-3 px-2">
      <span aria-hidden className="text-muted">{Icons.plus}</span>
      <input autoFocus aria-label="Routine name" placeholder="Pull Day" value={name} onChange={(e) => setName(e.target.value)}
        onBlur={() => { if (!name.trim()) setOpen(false); }}
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); add(); }
          if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); setName(""); setOpen(false); }
        }}
        className={`${fieldClass} h-11 flex-1`} />
    </div>
  );
}
