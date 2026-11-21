"use client";

import { useState } from "react";
import type { Commitment, Todo } from "@/lib/domain/types";
import { fromInputs, toDateInput, toTimeInput } from "@/lib/domain/dates";
import type { TodoInput } from "@/lib/data/mutations";
import { Button, ErrorText, Field, Input, Select } from "./ui";

const WEEKDAYS = [
  { bit: 1, label: "M", title: "Monday" },
  { bit: 2, label: "T", title: "Tuesday" },
  { bit: 4, label: "W", title: "Wednesday" },
  { bit: 8, label: "T", title: "Thursday" },
  { bit: 16, label: "F", title: "Friday" },
  { bit: 32, label: "S", title: "Saturday" },
  { bit: 64, label: "S", title: "Sunday" },
];

/** Title, optional date + time, lead time, recurrence (interval or calendar), commitment (F2.1, F2.3, F4, F7). */
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
  const [leadMinutes, setLeadMinutes] = useState(todo?.reminder_lead_minutes != null ? String(todo.reminder_lead_minutes) : "0");
  
  const initialRecurMode = (todo?.recur_days ?? 0) > 0 ? "weekdays" : (todo?.recur_interval_days ?? 0) > 0 ? "interval" : "none";
  const [recurMode, setRecurMode] = useState<"none" | "interval" | "weekdays">(initialRecurMode);
  const [repeat, setRepeat] = useState(todo?.recur_interval_days ? String(todo.recur_interval_days) : "7");
  const [recurDays, setRecurDays] = useState(todo?.recur_days ?? 1);
  const [commitmentId, setCommitmentId] = useState(todo?.commitment_id ?? defaultCommitmentId);

  const toggleDay = (bit: number) => {
    setRecurDays((prev) => {
      const next = prev ^ bit;
      return next === 0 ? bit : next;
    });
  };

  async function submit() {
    const days = parseInt(repeat, 10);
    const lead = parseInt(leadMinutes, 10);
    const result = await onSubmit({
      title,
      commitmentId,
      dueAt: date ? fromInputs(date, time || undefined) : null,
      hasTime: Boolean(date && time),
      repeatDays: recurMode === "interval" && Number.isFinite(days) && days > 0 ? days : null,
      recurDays: recurMode === "weekdays" && recurDays > 0 ? recurDays : null,
      reminderLeadMinutes: Boolean(date && time) && Number.isFinite(lead) && lead >= 0 ? lead : null,
    });
    if (result !== false && !todo) {
      setTitle("");
      setDate("");
      setTime("");
      setLeadMinutes("0");
      setRecurMode("none");
      setRepeat("7");
      setRecurDays(1);
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
        {Boolean(date && time) && (
          <Field label="Reminder alert">
            <Select value={leadMinutes} onChange={(e) => setLeadMinutes(e.target.value)}>
              <option value="0">At due time</option>
              <option value="15">15m before</option>
              <option value="30">30m before</option>
              <option value="60">1h before</option>
            </Select>
          </Field>
        )}
        <Field label="Repeat">
          <Select value={recurMode} onChange={(e) => setRecurMode(e.target.value as "none" | "interval" | "weekdays")}>
            <option value="none">Does not repeat</option>
            <option value="interval">Every N days</option>
            <option value="weekdays">Specific weekdays</option>
          </Select>
        </Field>
        {recurMode === "interval" && (
          <Field label="Days">
            <Input type="number" min={1} max={365} value={repeat} onChange={(e) => setRepeat(e.target.value)} placeholder="7" className="w-20" />
          </Field>
        )}
        {todo && (
          <Field label="Commitment">
            <Select value={commitmentId} onChange={(e) => setCommitmentId(e.target.value)}>
              {commitments.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </Field>
        )}
      </div>

      {recurMode === "weekdays" && (
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-fg-muted">Repeat on days</span>
          <div className="flex gap-1.5">
            {WEEKDAYS.map((w) => {
              const active = (recurDays & w.bit) !== 0;
              return (
                <button
                  key={w.bit}
                  type="button"
                  title={w.title}
                  onClick={() => toggleDay(w.bit)}
                  className={`h-8 w-8 rounded-full text-xs font-semibold transition ${
                    active ? "bg-primary text-on-primary" : "border border-line bg-surface hover:bg-surface-2 text-fg-muted"
                  }`}
                >
                  {w.label}
                </button>
              );
            })}
          </div>
        </div>
      )}

      <ErrorText error={error} />
      <div className="flex items-center gap-2">
        <Button variant="primary" type="submit" disabled={!title.trim() || busy}>{submitLabel}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
        {onDelete && <Button variant="danger" onClick={onDelete} className="ml-auto">Delete</Button>}
      </div>
    </form>
  );
}
