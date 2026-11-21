"use client";

import { useState } from "react";
import type { Commitment, Todo } from "@/lib/domain/types";
import { fromInputs, toDateInput, toTimeInput } from "@/lib/domain/dates";
import type { TodoInput } from "@/lib/data/mutations";
import { Button, ErrorText, Field, Input, Select } from "./ui";

/** Title, optional date + time, repeat every N days, commitment (F2.1, F2.3, F4.1, F4.2). */
export function TodoForm({
  todo, commitments, defaultCommitmentId, onSubmit, onCancel, onDelete, submitLabel, busy, error,
}: {
  todo?: Todo;
  commitments: Commitment[];
  defaultCommitmentId: string;
  onSubmit: (input: TodoInput) => Promise<boolean | void>;
  onCancel?: () => void;
  onDelete?: () => void;
  submitLabel: string;
  busy: boolean;
  error: unknown;
}) {
  const [title, setTitle] = useState(todo?.title ?? "");
  const [date, setDate] = useState(todo?.due_at != null ? toDateInput(todo.due_at) : "");
  const [time, setTime] = useState(todo?.due_at != null && todo.has_time ? toTimeInput(todo.due_at) : "");
  const [repeat, setRepeat] = useState(todo?.recur_interval_days ? String(todo.recur_interval_days) : "");
  const [commitmentId, setCommitmentId] = useState(todo?.commitment_id ?? defaultCommitmentId);

  async function submit() {
    const days = parseInt(repeat, 10);
    const result = await onSubmit({
      title,
      commitmentId,
      dueAt: date ? fromInputs(date, time || undefined) : null,
      hasTime: Boolean(date && time),
      repeatDays: Number.isFinite(days) && days > 0 ? days : null,
    });
    if (result !== false && !todo) {
      setTitle("");
      setDate("");
      setTime("");
      setRepeat("");
    }
  }

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); if (title.trim()) submit(); }}>
      <div className="flex flex-wrap items-end gap-2">
        <Field label="Title" className="min-w-48 flex-1">
          <Input autoFocus={Boolean(todo)} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Add a todo…" />
        </Field>
        <Field label="Date">
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label="Time">
          <Input type="time" value={time} disabled={!date} onChange={(e) => setTime(e.target.value)} />
        </Field>
        <Field label="Repeat every (days)">
          <Input type="number" min={1} max={365} value={repeat} onChange={(e) => setRepeat(e.target.value)} placeholder="—" className="w-36" />
        </Field>
        {todo && (
          <Field label="Commitment">
            <Select value={commitmentId} onChange={(e) => setCommitmentId(e.target.value)}>
              {commitments.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </Field>
        )}
      </div>
      <ErrorText error={error} />
      <div className="flex items-center gap-2">
        <Button variant="primary" type="submit" disabled={!title.trim() || busy}>{submitLabel}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
        {onDelete && <Button variant="danger" onClick={onDelete} className="ml-auto">Delete</Button>}
      </div>
    </form>
  );
}
