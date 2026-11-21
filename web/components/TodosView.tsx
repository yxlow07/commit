"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { Commitment } from "@/lib/domain/types";
import { openItems, recurrenceLabel, todoItem, type TodoItem } from "@/lib/domain/recurrence";
import { formatDue } from "@/lib/domain/due";
import { useTable } from "@/lib/data/store";
import { createTodo, deleteTodo, moveCommitment, setTodoDone, updateCommitment, updateTodo } from "@/lib/data/mutations";
import { CommitmentDialog } from "./CommitmentDialog";
import { TodoForm } from "./TodoForm";
import { Button, Card, Checkbox, ColorDot, Empty, ErrorText, IconButton, Icons, Spinner, useAction, useNow } from "./ui";

/** Main view (F10.2): commitments on the left, the selected commitment's todos on the right. */
export function TodosView() {
  const commitmentsQ = useTable("commitments");
  const todosQ = useTable("todos");
  const now = useNow();
  const router = useRouter();
  const params = useSearchParams();
  const qc = useQueryClient();
  const [dialog, setDialog] = useState<{ open: boolean; commitment: Commitment | null }>({ open: false, commitment: null });
  const archive = useAction();

  const commitments = useMemo(
    () => (commitmentsQ.data ?? []).filter((c) => !c.archived).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name)),
    [commitmentsQ.data],
  );
  const todos = useMemo(() => todosQ.data ?? [], [todosQ.data]);
  const selected = commitments.find((c) => c.id === params.get("c")) ?? commitments.find((c) => c.type === "standard") ?? commitments[0];
  const select = (id: string) => router.replace(`/?c=${id}`, { scroll: false });

  if (commitmentsQ.isPending || todosQ.isPending) return <Spinner />;
  if (commitmentsQ.error || todosQ.error) return <ErrorText error={commitmentsQ.error ?? todosQ.error} />;

  const openCount = (c: Commitment) => openItems(todos.filter((t) => t.commitment_id === c.id), now).length;

  return (
    <div className="grid gap-6 md:grid-cols-[16rem_1fr]">
      <aside className="flex flex-col gap-1">
        <div className="mb-1 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-fg-muted">Commitments</h2>
          <IconButton label="New commitment" onClick={() => setDialog({ open: true, commitment: null })}>{Icons.plus}</IconButton>
        </div>
        {commitments.length === 0 && <p className="text-sm text-fg-muted">Add a commitment like Study or Work to get started.</p>}
        {commitments.map((c, i) => (
          <div
            key={c.id}
            className={`group flex items-center gap-2 rounded-xl px-2 py-1.5 ${c.id === selected?.id ? "bg-primary-soft" : "hover:bg-surface-2"}`}
          >
            <button type="button" onClick={() => select(c.id)} className="flex min-w-0 flex-1 items-center gap-2 text-left text-sm">
              <ColorDot color={c.color} />
              <span className="truncate">{c.name}</span>
              <span className="ml-auto text-xs text-fg-muted">{c.type === "workout" ? "Workout" : openCount(c) || ""}</span>
            </button>
            <div className="flex opacity-0 transition group-hover:opacity-100 group-focus-within:opacity-100">
              <IconButton label={`Move ${c.name} up`} disabled={i === 0} onClick={() => moveCommitment(qc, commitments, c.id, -1)}>{Icons.up}</IconButton>
              <IconButton label={`Move ${c.name} down`} disabled={i === commitments.length - 1} onClick={() => moveCommitment(qc, commitments, c.id, 1)}>{Icons.down}</IconButton>
              <IconButton label={`Edit ${c.name}`} onClick={() => setDialog({ open: true, commitment: c })}>{Icons.edit}</IconButton>
              <IconButton label={`Archive ${c.name}`} onClick={() => archive.run(() => updateCommitment(qc, c.id, { archived: 1 }))}>{Icons.archive}</IconButton>
            </div>
          </div>
        ))}
        <ErrorText error={archive.error} />
      </aside>

      <section className="min-w-0">
        {!selected ? (
          <Empty>Create a commitment to start adding todos.</Empty>
        ) : selected.type === "workout" ? (
          <Card>
            <h1 className="mb-2 flex items-center gap-2 text-xl font-semibold"><ColorDot color={selected.color} size={12} />{selected.name}</h1>
            <p className="text-sm text-fg-muted">This is a workout commitment. Manage its routines on the <Link className="text-primary underline" href="/workouts">Workouts</Link> page.</p>
          </Card>
        ) : (
          <TodoList commitment={selected} commitments={commitments.filter((c) => c.type === "standard")} now={now} />
        )}
      </section>

      <CommitmentDialog
        open={dialog.open}
        commitment={dialog.commitment}
        nextSortOrder={commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0}
        onClose={() => setDialog({ open: false, commitment: null })}
        onCreated={select}
      />
    </div>
  );
}

function TodoList({ commitment, commitments, now }: { commitment: Commitment; commitments: Commitment[]; now: number }) {
  const todosQ = useTable("todos");
  const qc = useQueryClient();
  const add = useAction();
  const [showDone, setShowDone] = useState(false);

  const mine = (todosQ.data ?? []).filter((t) => t.commitment_id === commitment.id);
  const all = mine.map((t) => todoItem(t, now));
  const sortedOpen = openItems(mine, now); // undone, in widget order (F3.2)
  const done = [
    ...all.filter((i) => i.isDone && i.isRecurring),
    ...all.filter((i) => i.isDone && !i.isRecurring).sort((a, b) => (b.todo.done_at ?? 0) - (a.todo.done_at ?? 0)),
  ];

  return (
    <div className="flex flex-col gap-4">
      <h1 className="flex items-center gap-2 text-xl font-semibold"><ColorDot color={commitment.color} size={12} />{commitment.name}</h1>
      <Card>
        <TodoForm
          commitments={commitments}
          defaultCommitmentId={commitment.id}
          submitLabel="Add"
          busy={add.busy}
          error={add.error}
          onSubmit={(input) => add.run(() => createTodo(qc, { ...input, commitmentId: commitment.id }))}
        />
      </Card>
      <ul className="flex flex-col divide-y divide-line rounded-2xl border border-line bg-surface">
        {sortedOpen.length === 0 && <li><Empty>Nothing to do here.</Empty></li>}
        {sortedOpen.map((item) => <TodoRow key={item.todo.id} item={item} commitments={commitments} now={now} />)}
      </ul>
      {done.length > 0 && (
        <div>
          <Button variant="ghost" onClick={() => setShowDone((v) => !v)} aria-expanded={showDone}>
            Done ({done.length}) {showDone ? "▴" : "▾"}
          </Button>
          {showDone && (
            <ul className="mt-2 flex flex-col divide-y divide-line rounded-2xl border border-line bg-surface">
              {done.map((item) => <TodoRow key={item.todo.id} item={item} commitments={commitments} now={now} />)}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function TodoRow({ item, commitments, now }: { item: TodoItem; commitments: Commitment[]; now: number }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const action = useAction();
  const todo = item.todo;
  const due = formatDue(item.dueAt, item.hasTime, now);

  if (editing) {
    return (
      <li className="p-4">
        <TodoForm
          todo={todo}
          commitments={commitments}
          defaultCommitmentId={todo.commitment_id}
          submitLabel="Save"
          busy={action.busy}
          error={action.error}
          onCancel={() => setEditing(false)}
          onDelete={async () => { if (await action.run(() => deleteTodo(qc, todo.id))) setEditing(false); }}
          onSubmit={async (input) => {
            const ok = await action.run(() => updateTodo(qc, todo.id, input));
            if (ok) setEditing(false);
            return ok;
          }}
        />
      </li>
    );
  }

  const details = [
    item.isDone && item.isRecurring ? `Next: ${formatDue(item.dueAt, false, now).text}` : !item.isDone ? due.text : "",
    item.isRecurring ? recurrenceLabel(todo) : "",
  ].filter(Boolean).join(" · ");

  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <Checkbox checked={item.isDone} label={`Mark ${todo.title} ${item.isDone ? "not done" : "done"}`} onChange={(v) => action.run(() => setTodoDone(qc, todo, v))} />
      <button type="button" onClick={() => setEditing(true)} className="min-w-0 flex-1 text-left">
        <span className={`block truncate ${item.isDone ? "text-fg-muted line-through" : due.status === "today" ? "font-semibold" : ""}`}>{todo.title}</span>
        {details && <span className={`block text-xs ${!item.isDone && due.status === "overdue" ? "font-medium text-danger" : "text-fg-muted"}`}>{details}</span>}
      </button>
      <ErrorText error={action.error} />
    </li>
  );
}
