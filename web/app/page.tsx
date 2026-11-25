"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createRoutine, reorderCommitments, reorderRoutines } from "@/lib/data/mutations";
import { addDays, startOfDay } from "@/lib/domain/dates";
import { cssColor, weekdayIndex, weekdaysLabel } from "@/lib/domain/format";
import { isLate, relativeDay } from "@/lib/domain/labels";
import { openItems, type TodoItem } from "@/lib/domain/recurrence";
import type { Commitment, CommitmentType, Routine } from "@/lib/domain/types";
import { usePageTitle } from "@/components/AppShell";
import { CommitmentDialog } from "@/components/CommitmentDialog";
import { usePanel } from "@/components/EditPanel";
import { useCommitments } from "@/components/Nav";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { SortableList } from "@/components/Sortable";
import { useToast } from "@/components/Toast";
import { tint } from "@/components/Todos";
import { Button, Dialog, EmptyText, IconButton, Icons, Loading, RoundAdd, SectionHeader, TextField, useNow } from "@/components/ui";

const weekdayLong = new Intl.DateTimeFormat("en-GB", { weekday: "long" });
const monthLong = new Intl.DateTimeFormat("en-GB", { month: "long" });

const planned = (r: Routine, day: number) => (r.planned_days & (1 << weekdayIndex(day))) !== 0;

/**
 * Commitments (the app's first tab): the date, a one-line summary, then each commitment with its
 * open count, and a section per workout commitment with its routines.
 */
export default function CommitmentsPage() {
  usePageTitle("Groups");
  const params = useSearchParams();
  const router = useRouter();
  const panel = usePanel();
  const now = useNow();
  const qc = useQueryClient();
  const toast = useToast();
  const commitments = useCommitments();
  const todosQ = useTable("todos");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const [dialog, setDialog] = useState<{ commitment: Commitment | null; type: CommitmentType } | null>(null);

  // Old links (?c=<id>) open the commitment page.
  const legacy = params.get("c");
  useEffect(() => { if (legacy) router.replace(`/c?id=${legacy}`); }, [legacy, router]);

  const byId = useMemo(() => new Map(commitments.map((c) => [c.id, c])), [commitments]);
  const open = useMemo(
    () => openItems((todosQ.data ?? []).filter((t) => byId.get(t.commitment_id)?.type === "standard"), now),
    [todosQ.data, now, byId],
  );
  const routines = (routinesQ.data ?? []).filter((r) => byId.get(r.commitment_id)?.type === "workout")
    .sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
  const today = startOfDay(now);
  const todoGroup = commitments.filter((c) => c.type === "standard");
  const workoutGroup = commitments.filter((c) => c.type === "workout");
  const collectionGroup = commitments.filter((c) => c.type === "collection");
  const progress = useCollectionProgress();

  const late = open.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
  const dueToday = open.filter((i) => !isLate(i.dueAt, i.hasTime, now) && i.dueAt != null && startOfDay(i.dueAt) === today).length;
  const plannedToday = routines.filter((r) => planned(r, today));
  const summary = [
    dueToday === 0 && late === 0 ? "Nothing due today." : late === 0 ? `${dueToday} due today.` : dueToday === 0 ? `${late} overdue.` : `${dueToday} due today, ${late} overdue.`,
    plannedToday.length ? `${plannedToday.map((r) => r.name).join(" and ")} planned.` : "",
  ].filter(Boolean).join(" ");
  const loading = todosQ.isPending || routinesQ.isPending;
  const nextOrder = commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0;

  const saveOrder = (groupIds: string[]) => {
    const members = new Set(groupIds);
    const next = groupIds[Symbol.iterator]();
    const full = commitments.map((c) => (members.has(c.id) ? next.next().value! : c.id));
    reorderCommitments(qc, commitments, full).catch(() => toast({ message: "Couldn't save the order. Check your connection." }));
  };

  return (
    <div className="flex flex-col gap-12">
      <header className="flex flex-col gap-8">
        <div className="flex items-center gap-5">
          <div className="flex min-w-0 flex-1 gap-5 [align-items:last_baseline]">
            <span className="text-display-xl tabular-nums">{new Date(now).getDate()}</span>
            <span className="flex min-w-0 flex-col pb-1">
              <span className="truncate text-title-section font-medium">{weekdayLong.format(now)}</span>
              <span className="truncate text-title-section font-normal text-muted">{monthLong.format(now)}</span>
            </span>
          </div>
          <RoundAdd label="Add todo" onClick={() => panel.openNew()} />
        </div>
        <p className={`text-body-l text-text-strong ${loading ? "invisible" : ""}`}>{summary}</p>
      </header>

      {loading ? <Loading /> : (
        <>
          <section className="flex flex-col" aria-labelledby="h-commitments">
            <SectionHeader id="h-commitments" title="Commitments">
              <IconButton label="New commitment" onClick={() => setDialog({ commitment: null, type: "standard" })}>{Icons.plus}</IconButton>
            </SectionHeader>
            {todoGroup.length === 0 && <EmptyText className="py-2">Add your first commitment, like Study or Work.</EmptyText>}
            <SortableList
              items={todoGroup}
              getId={(c) => c.id}
              getLabel={(c) => c.name}
              onReorder={saveOrder}
              className="flex flex-col"
              renderItem={(c, handle) => <CommitmentRow commitment={c} open={open} now={now} handle={handle} />}
            />
          </section>

          {workoutGroup.map((c) => (
            <WorkoutSection key={c.id} commitment={c} routines={routines.filter((r) => r.commitment_id === c.id)}
              exerciseCount={(id) => (exercisesQ.data ?? []).filter((e) => e.routine_id === id).length}
              now={now} onEdit={() => setDialog({ commitment: c, type: "workout" })} />
          ))}

          {collectionGroup.length > 0 && (
            <section className="flex flex-col" aria-labelledby="h-collections">
              <SectionHeader id="h-collections" title="Collections">
                <IconButton label="New collection" onClick={() => setDialog({ commitment: null, type: "collection" })}>{Icons.plus}</IconButton>
              </SectionHeader>
              <SortableList
                items={collectionGroup}
                getId={(c) => c.id}
                getLabel={(c) => c.name}
                onReorder={saveOrder}
                className="flex flex-col"
                renderItem={(c, handle) => <CollectionRow commitment={c} progress={progress.get(c.id)} handle={handle} />}
              />
            </section>
          )}
        </>
      )}

      <CommitmentDialog open={dialog != null} onClose={() => setDialog(null)} commitment={dialog?.commitment ?? null}
        type={dialog?.type ?? "standard"} nextSortOrder={nextOrder} />
    </div>
  );
}

/** Collected and total items per collection commitment. */
function useCollectionProgress(): Map<string, { collected: number; total: number }> {
  const groupsQ = useTable("collection_groups");
  const itemsQ = useTable("collection_items");
  return useMemo(() => {
    const owner = new Map((groupsQ.data ?? []).map((g) => [g.id, g.commitment_id]));
    const out = new Map<string, { collected: number; total: number }>();
    for (const i of itemsQ.data ?? []) {
      const c = owner.get(i.group_id);
      if (!c) continue;
      const p = out.get(c) ?? { collected: 0, total: 0 };
      p.total++;
      p.collected += i.collected;
      out.set(c, p);
    }
    return out;
  }, [groupsQ.data, itemsQ.data]);
}

/** A collection: "15 still to find" tinted, "10/25" large and light in its colour. */
function CollectionRow({ commitment: c, progress, handle }: { commitment: Commitment; progress?: { collected: number; total: number }; handle: React.ReactNode }) {
  const detail = !progress || progress.total === 0 ? "Nothing in it yet"
    : progress.collected === progress.total ? `All ${progress.total} collected` : `${progress.total - progress.collected} still to find`;
  return (
    <div className="-ml-9 flex items-center">
      {handle}
      <Link href={`/collection?id=${c.id}`} className="-mr-3 flex min-h-[72px] min-w-0 flex-1 items-center gap-4 rounded-xl px-3 py-2 hover:bg-hover">
        <span className="min-w-0 flex-1">
          <span className="block truncate text-row">{c.name}</span>
          <span className="block truncate text-body" style={tint(c.color)}>{detail}</span>
        </span>
        {progress && progress.total > 0 && <span className="text-tab tabular-nums" style={{ color: cssColor(c.color) }}>{progress.collected}/{progress.total}</span>}
        <span className="text-faint">{Icons.chevron}</span>
      </Link>
    </div>
  );
}

/** "1 overdue, next due tomorrow", "Next due Fri", "Nothing open". */
function commitmentDetail(items: TodoItem[], now: number): string {
  if (!items.length) return "Nothing open";
  const late = items.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
  const next = items.filter((i) => i.dueAt != null && !isLate(i.dueAt, i.hasTime, now)).sort((a, b) => a.dueAt! - b.dueAt!)[0];
  const nextText = next ? `next due ${relativeDay(startOfDay(next.dueAt!), now)}` : null;
  if (late && nextText) return `${late} overdue, ${nextText}`;
  if (late) return `${late} overdue`;
  return nextText ? nextText[0].toUpperCase() + nextText.slice(1) : "No due dates";
}

/** Grip (on hover), name in `row` with its detail tinted below, the open count large and light in its colour, chevron. */
function CommitmentRow({ commitment: c, open, now, handle }: { commitment: Commitment; open: TodoItem[]; now: number; handle: React.ReactNode }) {
  const mine = open.filter((i) => i.todo.commitment_id === c.id);
  return (
    <div className="-ml-9 flex items-center">
      {handle}
      <Link href={`/c?id=${c.id}`} className="-mr-3 flex min-h-[72px] min-w-0 flex-1 items-center gap-4 rounded-xl px-3 py-2 hover:bg-hover">
        <span className="min-w-0 flex-1">
          <span className="block truncate text-row">{c.name}</span>
          <span className="block truncate text-body" style={tint(c.color)}>{commitmentDetail(mine, now)}</span>
        </span>
        {mine.length > 0 && <span className="text-tab tabular-nums" style={{ color: cssColor(c.color) }}>{mine.length}</span>}
        <span className="text-faint">{Icons.chevron}</span>
      </Link>
    </div>
  );
}

/** A workout commitment as in the app: its name as the heading with edit and new routine, then its routines. */
function WorkoutSection({ commitment, routines, exerciseCount, now, onEdit }: {
  commitment: Commitment; routines: Routine[]; exerciseCount: (id: string) => number; now: number; onEdit: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const online = useOnline();
  const [naming, setNaming] = useState(false);
  const today = startOfDay(now);
  const when = (r: Routine) => (planned(r, today) ? "planned today" : planned(r, addDays(today, 1)) ? "planned tomorrow" : null);

  return (
    <section className="flex flex-col" aria-label={commitment.name}>
      <SectionHeader title={commitment.name}>
        <IconButton label={`Edit ${commitment.name}`} onClick={onEdit}>{Icons.pencil}</IconButton>
        <IconButton label={`New routine in ${commitment.name}`} disabled={!online} title={online ? undefined : OFFLINE_REASON} onClick={() => setNaming(true)}>{Icons.plus}</IconButton>
      </SectionHeader>
      {routines.length === 0 && <EmptyText className="py-2">No routines yet. Add each workout day with +, like Push, Pull and Legs.</EmptyText>}
      <SortableList
        items={routines}
        getId={(r) => r.id}
        getLabel={(r) => r.name}
        onReorder={(ids) => reorderRoutines(qc, routines, ids).catch(() => toast({ message: "Couldn't save the order. Check your connection." }))}
        className="flex flex-col"
        renderItem={(r, handle) => {
          const n = exerciseCount(r.id);
          const parts = [n === 1 ? "1 exercise" : `${n} exercises`, weekdaysLabel(r.planned_days), when(r)].filter(Boolean);
          return (
            <div className="-ml-9 flex items-center">
              {handle}
              <Link href={`/routine?id=${r.id}`} className="-mr-3 flex min-h-[72px] min-w-0 flex-1 items-center gap-4 rounded-xl px-3 py-2 hover:bg-hover">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-row">{r.name}</span>
                  <span className="block truncate text-body" style={tint(commitment.color)}>{parts.join(", ")}</span>
                </span>
                <span className="text-faint">{Icons.chevron}</span>
              </Link>
            </div>
          );
        }}
      />
      <NewRoutineDialog open={naming} onClose={() => setNaming(false)} commitment={commitment}
        nextOrder={routines.length ? Math.max(...routines.map((r) => r.sort_order)) + 1 : 0} />
    </section>
  );
}

/** "New routine": a name, then the routine editor opens (as in the app). */
function NewRoutineDialog({ open, onClose, commitment, nextOrder }: { open: boolean; onClose: () => void; commitment: Commitment; nextOrder: number }) {
  const qc = useQueryClient();
  const router = useRouter();
  const toast = useToast();
  const [name, setName] = useState("");
  async function add() {
    if (!name.trim()) return;
    try {
      const r = await createRoutine(qc, commitment.id, name, nextOrder);
      setName("");
      onClose();
      router.push(`/routine?id=${r.id}`);
    } catch {
      toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: add } });
    }
  }
  return (
    <Dialog open={open} onClose={onClose} title="New routine">
      <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); add(); }}>
        <TextField autoFocus aria-label="Name" placeholder="Name, like Push" value={name} onChange={(e) => setName(e.target.value)} />
        <div className="flex justify-end gap-2">
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={!name.trim()}>Add routine</Button>
        </div>
      </form>
    </Dialog>
  );
}
