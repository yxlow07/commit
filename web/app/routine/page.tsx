"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createExercise, deleteExercise, moveExercise, updateExercise, updateRoutine, type ExerciseInput } from "@/lib/data/mutations";
import { WEEKDAYS, formatWeight, hasWeekday, toggleWeekday } from "@/lib/domain/format";
import type { Exercise, Routine } from "@/lib/domain/types";
import { Button, Card, Chip, Empty, ErrorText, Field, IconButton, Icons, Input, Spinner, useAction } from "@/components/ui";

/** Routine editor (F5.1): name, rest between exercises, planned weekdays, ordered exercises. */
export default function RoutinePage() {
  const id = useSearchParams().get("id");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");

  if (routinesQ.isPending || exercisesQ.isPending) return <Spinner />;
  const routine = routinesQ.data?.find((r) => r.id === id);
  if (!routine) return <Empty>Routine not found. <Link href="/workouts" className="text-primary underline">Back to workouts</Link></Empty>;

  const exercises = (exercisesQ.data ?? []).filter((e) => e.routine_id === routine.id).sort((a, b) => a.sort_order - b.sort_order);
  return (
    <div className="flex flex-col gap-4">
      <Link href="/workouts" className="text-sm text-primary">← Workouts</Link>
      <RoutineFields key={routine.id + routine.updated_at} routine={routine} />
      <Card>
        <h2 className="mb-2 font-semibold">Exercises</h2>
        <ExerciseList routine={routine} exercises={exercises} />
      </Card>
    </div>
  );
}

function RoutineFields({ routine }: { routine: Routine }) {
  const qc = useQueryClient();
  const [name, setName] = useState(routine.name);
  const [rest, setRest] = useState(String(routine.rest_between_exercises_s));
  const [days, setDays] = useState(routine.planned_days);
  const action = useAction();
  const dirty = name !== routine.name || Number(rest) !== routine.rest_between_exercises_s || days !== routine.planned_days;

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Name" className="min-w-48 flex-1"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Rest between exercises (s)"><Input type="number" min={0} value={rest} onChange={(e) => setRest(e.target.value)} className="w-40" /></Field>
      </div>
      <div>
        <p className="mb-1 text-xs font-medium text-fg-muted">Planned days (shown in the morning recap)</p>
        <div className="flex flex-wrap gap-1.5">
          {WEEKDAYS.map((d, i) => <Chip key={d} selected={hasWeekday(days, i)} onClick={() => setDays(toggleWeekday(days, i))}>{d}</Chip>)}
        </div>
      </div>
      <ErrorText error={action.error} />
      <div>
        <Button variant="primary" disabled={!dirty || !name.trim() || action.busy}
          onClick={() => action.run(() => updateRoutine(qc, routine.id, { name: name.trim(), rest_between_exercises_s: Math.max(0, Number(rest) || 0), planned_days: days }))}>
          Save routine
        </Button>
      </div>
    </Card>
  );
}

function ExerciseList({ routine, exercises }: { routine: Routine; exercises: Exercise[] }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const action = useAction();
  const nextOrder = exercises.length ? Math.max(...exercises.map((e) => e.sort_order)) + 1 : 0;

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col divide-y divide-line">
        {exercises.length === 0 && <li className="py-3 text-sm text-fg-muted">No exercises yet.</li>}
        {exercises.map((e, i) =>
          editing === e.id ? (
            <li key={e.id} className="py-3">
              <ExerciseForm exercise={e} submitLabel="Save" onCancel={() => setEditing(null)}
                onSubmit={async (input) => { if (await action.run(() => updateExercise(qc, e.id, input))) setEditing(null); }} />
            </li>
          ) : (
            <li key={e.id} className="flex items-center gap-2 py-2">
              <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setEditing(e.id)}>
                <span className="block font-medium">{e.name}</span>
                <span className="block text-xs text-fg-muted">{describe(e)}</span>
              </button>
              <IconButton label={`Move ${e.name} up`} disabled={i === 0} onClick={() => moveExercise(qc, exercises, e.id, -1)}>{Icons.up}</IconButton>
              <IconButton label={`Move ${e.name} down`} disabled={i === exercises.length - 1} onClick={() => moveExercise(qc, exercises, e.id, 1)}>{Icons.down}</IconButton>
              <IconButton label={`Edit ${e.name}`} onClick={() => setEditing(e.id)}>{Icons.edit}</IconButton>
              <IconButton label={`Delete ${e.name}`} onClick={() => action.run(() => deleteExercise(qc, e.id))}>{Icons.trash}</IconButton>
            </li>
          ),
        )}
      </ul>
      <div className="rounded-xl bg-surface-2 p-3">
        <p className="mb-2 text-sm font-medium">Add exercise</p>
        <ExerciseForm key={nextOrder} submitLabel="Add" onSubmit={(input) => action.run(() => createExercise(qc, routine.id, input, nextOrder))} />
      </div>
      <ErrorText error={action.error} />
    </div>
  );
}

/** "3 × 8 @ 60 kg · rest 90 s" or "2 × 30 s · rest 60 s". */
function describe(e: Exercise): string {
  const work = e.duration_seconds ? `${e.sets} × ${e.duration_seconds} s` : `${e.sets} × ${e.reps} @ ${formatWeight(e.weight)}`;
  return `${work} · rest ${e.rest_seconds} s`;
}

function ExerciseForm({ exercise, submitLabel, onSubmit, onCancel }: {
  exercise?: Exercise; submitLabel: string; onSubmit: (input: ExerciseInput) => unknown; onCancel?: () => void;
}) {
  const [name, setName] = useState(exercise?.name ?? "");
  const [timed, setTimed] = useState(Boolean(exercise?.duration_seconds));
  const [sets, setSets] = useState(String(exercise?.sets ?? 3));
  const [reps, setReps] = useState(String(exercise?.reps ?? 8));
  const [weight, setWeight] = useState(String(exercise?.weight ?? 0));
  const [rest, setRest] = useState(String(exercise?.rest_seconds ?? 90));
  const [duration, setDuration] = useState(String(exercise?.duration_seconds ?? 30));
  const valid = name.trim() && Number(sets) > 0 && (!timed || Number(duration) > 0);

  return (
    <form className="flex flex-wrap items-end gap-2"
      onSubmit={(ev) => {
        ev.preventDefault();
        if (!valid) return;
        onSubmit({
          name, sets: Number(sets), reps: timed ? 0 : Number(reps) || 0, weight: timed ? 0 : Number(weight.replace(",", ".")) || 0,
          restSeconds: Number(rest) || 0, durationSeconds: timed ? Number(duration) : null,
        });
      }}>
      <Field label="Name" className="min-w-40 flex-1"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Sets"><Input type="number" min={1} value={sets} onChange={(e) => setSets(e.target.value)} className="w-20" /></Field>
      {timed ? (
        <Field label="Seconds"><Input type="number" min={1} value={duration} onChange={(e) => setDuration(e.target.value)} className="w-24" /></Field>
      ) : (
        <>
          <Field label="Reps"><Input type="number" min={0} value={reps} onChange={(e) => setReps(e.target.value)} className="w-20" /></Field>
          <Field label="Weight (kg)"><Input type="number" min={0} step={0.5} value={weight} onChange={(e) => setWeight(e.target.value)} className="w-24" /></Field>
        </>
      )}
      <Field label="Rest (s)"><Input type="number" min={0} value={rest} onChange={(e) => setRest(e.target.value)} className="w-24" /></Field>
      <label className="flex items-center gap-2 pb-2 text-sm"><input type="checkbox" checked={timed} onChange={(e) => setTimed(e.target.checked)} className="h-4 w-4 accent-[var(--primary)]" />Timed</label>
      <Button variant="primary" type="submit" disabled={!valid}>{submitLabel}</Button>
      {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
    </form>
  );
}
