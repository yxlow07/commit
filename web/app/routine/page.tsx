"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import {
  createExercise, deleteExercise, deleteRoutine, reorderExercises, restoreRow, updateExercise, updateRoutine, type ExerciseInput,
} from "@/lib/data/mutations";
import { sessionSummaries } from "@/lib/data/history";
import { durationMinutes, formatVolume, formatWeight } from "@/lib/domain/format";
import { restLabel } from "@/lib/domain/labels";
import type { Exercise, Routine } from "@/lib/domain/types";
import { usePageTitle } from "@/components/AppShell";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { SortableList } from "@/components/Sortable";
import { useToast } from "@/components/Toast";
import {
  BackLink, Button, Dialog, EmptyText, Field, FieldGroup, IconTile, Icons, Loading, SectionHeading, Segmented, Stepper, TextField, WeekdayToggles,
} from "@/components/ui";

/** Routine editor (web spec, "Routine editor"): name, planned days, rest, ordered exercises. */
export default function RoutinePage() {
  const id = useSearchParams().get("id");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const routine = routinesQ.data?.find((r) => r.id === id);
  usePageTitle(routine?.name ?? "");

  if (routinesQ.isPending || exercisesQ.isPending) return <Loading />;
  if (!routine) return <EmptyText>This routine doesn&apos;t exist any more. <Link href="/" className="font-bold hover:underline">Back to Commitments</Link></EmptyText>;

  const exercises = (exercisesQ.data ?? []).filter((e) => e.routine_id === routine.id).sort((a, b) => a.sort_order - b.sort_order);
  return <Editor key={routine.id} routine={routine} exercises={exercises} back={{ href: "/", label: "Groups" }} />;
}

function Editor({ routine, exercises, back }: { routine: Routine; exercises: Exercise[]; back: { href: string; label: string } }) {
  const qc = useQueryClient();
  const router = useRouter();
  const toast = useToast();
  const online = useOnline();
  const [dialog, setDialog] = useState<{ open: boolean; exercise: Exercise | null }>({ open: false, exercise: null });

  const save = (changes: Partial<Routine>): Promise<unknown> =>
    updateRoutine(qc, routine.id, changes).catch(() => toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: () => save(changes) } }));

  async function remove() {
    try {
      await deleteRoutine(qc, routine.id);
      router.push(back.href);
      toast({ message: `${routine.name} deleted`, action: { label: "Undo", run: () => restoreRow(qc, "routines", routine.id) } });
    } catch {
      toast({ message: "Couldn't delete. Check your connection." });
    }
  }

  return (
    <div className="flex flex-col gap-10">
      <header className="flex flex-col">
        <div className="flex items-start justify-between gap-4">
          <BackLink href={back.href} label={back.label} />
          <Button variant="quiet" className="-mr-3" disabled={!online} title={online ? undefined : OFFLINE_REASON} onClick={remove}>Delete routine</Button>
        </div>
        <EditableTitle key={routine.name} value={routine.name} onSave={(name) => save({ name })} />
      </header>

      <FieldGroup label="Planned days">
        <WeekdayToggles label="Planned days" mask={routine.planned_days} onChange={(planned_days) => save({ planned_days })} />
      </FieldGroup>

      <div className="max-w-64">
        <Stepper label="Rest between exercises" value={routine.rest_between_exercises_s} step={15} max={600}
          format={restLabel} onChange={(v) => save({ rest_between_exercises_s: v })} />
      </div>

      <section className="flex flex-col">
        <SectionHeading className="pb-1">Exercises</SectionHeading>
        <SortableList
          items={exercises}
          getId={(e) => e.id}
          getLabel={(e) => e.name}
          onReorder={(ids) => reorderExercises(qc, exercises, ids).catch(() => toast({ message: "Couldn't save the order. Check your connection." }))}
          className="flex flex-col"
          renderItem={(e, handle) => (
            <div className="-ml-9 flex items-center">
              {handle}
              <button type="button" onClick={() => setDialog({ open: true, exercise: e })}
                className="-mr-3 flex min-h-16 min-w-0 flex-1 cursor-default items-center gap-3 rounded-xl px-3 py-2 text-left hover:bg-hover">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-body-l">{e.name}</span>
                  <span className="block truncate text-body text-muted">{describe(e)}</span>
                </span>
                <span className="text-faint">{Icons.chevron}</span>
              </button>
            </div>
          )}
        />
        <button type="button" disabled={!online} title={online ? undefined : OFFLINE_REASON} onClick={() => setDialog({ open: true, exercise: null })}
          className="-mx-3 flex min-h-14 w-[calc(100%+1.5rem)] cursor-pointer items-center gap-3 rounded-xl px-3 text-left text-body-l text-muted hover:bg-hover hover:text-ink disabled:cursor-not-allowed">
          {Icons.plus} Add exercise
        </button>
      </section>

      <RoutineHistory routineId={routine.id} />

      <ExerciseDialog
        open={dialog.open}
        exercise={dialog.exercise}
        onClose={() => setDialog({ open: false, exercise: null })}
        onSave={(input) => dialog.exercise
          ? updateExercise(qc, dialog.exercise.id, input)
          : createExercise(qc, routine.id, input, exercises.length ? Math.max(...exercises.map((x) => x.sort_order)) + 1 : 0)}
        onDelete={dialog.exercise ? async () => {
          const ex = dialog.exercise!;
          await deleteExercise(qc, ex.id);
          toast({ message: `${ex.name} deleted`, action: { label: "Undo", run: () => restoreRow(qc, "exercises", ex.id) } });
        } : undefined}
      />
    </div>
  );
}

/** The routine name as a title.screen field: click to edit, Enter or blur saves, Esc cancels. */
function EditableTitle({ value, onSave }: { value: string; onSave: (v: string) => void }) {
  const [text, setText] = useState(value);
  const commit = () => { const t = text.trim(); if (t && t !== value) onSave(t); else setText(value); };
  return (
    <input
      aria-label="Routine name"
      value={text}
      onChange={(e) => setText(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") { e.preventDefault(); (e.target as HTMLInputElement).blur(); }
        if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); setText(value); requestAnimationFrame(() => (e.target as HTMLInputElement).blur()); }
      }}
      className="-mx-3 w-[calc(100%+1.5rem)] cursor-text rounded-xl bg-transparent px-3 py-1 text-title-screen outline-none hover:bg-hover focus:bg-fill"
    />
  );
}

const historyDate = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" });
const historyTime = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

/** This routine's finished sessions, newest first, as in the app's routine screen. */
function RoutineHistory({ routineId }: { routineId: string }) {
  const sessionsQ = useTable("workout_sessions");
  const setsQ = useTable("workout_sets");
  const routinesQ = useTable("routines");
  const mine = sessionSummaries((sessionsQ.data ?? []).filter((x) => x.routine_id === routineId), setsQ.data ?? [], routinesQ.data ?? []);
  return (
    <section className="flex flex-col">
      <SectionHeading className="pb-1">History</SectionHeading>
      {mine.length === 0 ? <p className="py-2 text-body-l text-text-strong">No sessions yet. Finished workouts of this routine show here.</p> : (
        <ul>
          {mine.map((x) => (
            <li key={x.session.id}>
              <Link href={`/session?id=${x.session.id}`} className="-mx-3 flex min-h-16 items-center gap-3 rounded-xl px-3 py-2 hover:bg-hover">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-body-l tabular-nums">{historyDate.format(x.session.started_at).replace(",", "")}, {historyTime.format(x.session.started_at)}</span>
                  <span className="block truncate text-body tabular-nums text-muted">
                    {durationMinutes(x.session.started_at, x.session.ended_at)} min, {x.setCount === 1 ? "1 set" : `${x.setCount} sets`}, {formatVolume(x.volume)}
                  </span>
                </span>
                <span className="text-faint">{Icons.chevron}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** "4 × 8, 60 kg, rest 90 s" or "3 × 30 s, rest 60 s". */
function describe(e: Exercise): string {
  const work = e.duration_seconds ? `${e.sets} × ${e.duration_seconds} s` : `${e.sets} × ${e.reps}, ${formatWeight(e.weight)}`;
  return `${work}, rest ${e.rest_seconds} s`;
}

/** Exercise dialog: name; Sets and reps / Timed; steppers in a 2 x 2 grid; trash, Cancel, Save exercise. */
function ExerciseDialog({ open, exercise, onClose, onSave, onDelete }: {
  open: boolean; exercise: Exercise | null; onClose: () => void; onSave: (input: ExerciseInput) => Promise<unknown>; onDelete?: () => Promise<void>;
}) {
  return (
    <Dialog open={open} onClose={onClose} title={exercise ? "Edit exercise" : "New exercise"}>
      {open && <ExerciseForm exercise={exercise} onClose={onClose} onSave={onSave} onDelete={onDelete} />}
    </Dialog>
  );
}

function ExerciseForm({ exercise, onClose, onSave, onDelete }: {
  exercise: Exercise | null; onClose: () => void; onSave: (input: ExerciseInput) => Promise<unknown>; onDelete?: () => Promise<void>;
}) {
  const toast = useToast();
  const online = useOnline();
  const [name, setName] = useState(exercise?.name ?? "");
  const [timed, setTimed] = useState(Boolean(exercise?.duration_seconds));
  const [sets, setSets] = useState(exercise?.sets ?? 3);
  const [reps, setReps] = useState(exercise?.reps || 8);
  const [weight, setWeight] = useState(exercise?.weight ?? 0);
  const [rest, setRest] = useState(exercise?.rest_seconds ?? 90);
  const [duration, setDuration] = useState(exercise?.duration_seconds ?? 30);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (!name.trim()) return;
    setBusy(true);
    try {
      await onSave({ name, sets, reps: timed ? 0 : reps, weight: timed ? 0 : weight, restSeconds: rest, durationSeconds: timed ? duration : null });
      onClose();
    } catch {
      toast({ message: "Couldn't save. Check your connection." });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); save(); }}
      onKeyDown={(e) => { if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) { e.preventDefault(); save(); } }}>
      <Field label="Name" htmlFor="exercise-name">
        <TextField id="exercise-name" autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Bench press" />
      </Field>
      <Segmented label="Kind" value={timed ? "timed" : "reps"} onChange={(v) => setTimed(v === "timed")}
        items={[{ id: "reps", label: "Sets and reps" }, { id: "timed", label: "Timed" }]} />
      <div className="grid grid-cols-2 gap-x-6 gap-y-5">
        <Stepper label="Sets" value={sets} step={1} min={1} max={20} onChange={setSets} />
        {timed
          ? <Stepper label="Duration" value={duration} step={5} min={5} max={600} unit="seconds" onChange={setDuration} />
          : <Stepper label="Reps" value={reps} step={1} min={1} max={100} unit="reps" onChange={setReps} />}
        {!timed && <Stepper label="Weight" value={weight} step={2.5} max={500} unit="kg" onChange={setWeight} />}
        <Stepper label="Rest" value={rest} step={15} max={600} format={restLabel} onChange={setRest} />
      </div>
      <div className="flex items-center gap-2">
        {onDelete && (
          <IconTile label={`Delete ${exercise?.name ?? "exercise"}`} disabled={!online || busy}
            onClick={async () => { try { await onDelete(); onClose(); } catch { toast({ message: "Couldn't delete. Check your connection." }); } }}>
            {Icons.trash}
          </IconTile>
        )}
        <span className="flex-1" />
        <Button variant="quiet" onClick={onClose}>Cancel</Button>
        <Button variant="primary" type="submit" title={online ? undefined : OFFLINE_REASON} disabled={!name.trim() || busy || !online}>Save exercise</Button>
      </div>
    </form>
  );
}
