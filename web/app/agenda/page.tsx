"use client";

import { useMemo } from "react";
import { useTable } from "@/lib/data/store";
import { daysBetween, startOfDay } from "@/lib/domain/dates";
import { isLate, openLabel } from "@/lib/domain/labels";
import { openItems, recurrenceLabel, type TodoItem } from "@/lib/domain/recurrence";
import { usePageTitle } from "@/components/AppShell";
import { useCommitments } from "@/components/Nav";
import { InlineAdd, TodoRow, TodoSection, tint, useTicks, type RowData } from "@/components/Todos";
import { EmptyText, Loading, PageTitle, useNow } from "@/components/ui";

const SECTIONS = ["Overdue", "Today", "Tomorrow", "This week", "Later", "No date"] as const;

/**
 * Agenda (the app's Agenda tab): every open todo across commitments, sorted by when it's due, in
 * Overdue / Today / Tomorrow / This week / Later / No date. Each row names its commitment, tinted.
 */
export default function AgendaPage() {
  usePageTitle("Agenda");
  const now = useNow();
  const commitments = useCommitments();
  const todosQ = useTable("todos");
  const ticks = useTicks();
  const byId = useMemo(() => new Map(commitments.map((c) => [c.id, c])), [commitments]);
  const todoCommitments = commitments.filter((c) => c.type === "standard");

  const open = useMemo(
    () => openItems((todosQ.data ?? []).filter((t) => byId.get(t.commitment_id)?.type === "standard"), now),
    [todosQ.data, now, byId],
  );
  const items = ticks.merge(open);
  const today = startOfDay(now);
  const section = (i: TodoItem): (typeof SECTIONS)[number] => {
    if (i.dueAt == null) return "No date";
    if (isLate(i.dueAt, i.hasTime, now)) return "Overdue";
    const d = daysBetween(today, startOfDay(i.dueAt));
    return d <= 0 ? "Today" : d === 1 ? "Tomorrow" : d <= 6 ? "This week" : "Later";
  };
  const row = (i: TodoItem): RowData => {
    const c = byId.get(i.todo.commitment_id);
    return {
      item: i,
      due: openLabel(i.dueAt, i.hasTime, now),
      second: [c?.name, i.isRecurring ? recurrenceLabel(i.todo) : ""].filter(Boolean).join(", "),
      secondStyle: c ? tint(c.color) : undefined,
    };
  };

  const late = open.filter((i) => isLate(i.dueAt, i.hasTime, now)).length;
  const summary = open.length === 0 ? "" : late ? `${open.length} open, ${late} overdue.` : `${open.length} open.`;

  return (
    <div className="flex flex-col gap-10">
      <PageTitle title="Agenda" summary={todosQ.isPending ? undefined : summary || undefined} />
      {todosQ.isPending ? <Loading /> : (
        <div className="flex flex-col gap-7">
          {items.length === 0 && <EmptyText>Nothing open.</EmptyText>}
          {SECTIONS.map((title) => {
            const list = items.filter((i) => section(i) === title);
            if (!list.length) return null;
            return (
              <TodoSection key={title} title={title} danger={title === "Overdue"}>
                {list.map((i) => {
                  const t = ticks.ticked.get(i.todo.id);
                  return <TodoRow key={i.todo.id} row={row(i)} checked={!!t} fading={t?.fading} onToggle={(done) => (done ? ticks.tick(i) : ticks.untick(i))} />;
                })}
              </TodoSection>
            );
          })}
          {todoCommitments.length > 0 && <InlineAdd commitments={todoCommitments} />}
        </div>
      )}
    </div>
  );
}
