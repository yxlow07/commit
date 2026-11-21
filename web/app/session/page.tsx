"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { deleteSet, editSet } from "@/lib/data/mutations";
import { sessionSummaries } from "@/lib/data/history";
import { durationMinutes, formatVolume } from "@/lib/domain/format";
import type { Exercise, WorkoutSet } from "@/lib/domain/types";
import { Button, Card, Empty, ErrorText, IconButton, Icons, Input, Spinner, useAction } from "@/components/ui";

const dateFmt = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Every set of a session as done; fix a wrongly logged set or delete it (F8.5). */
export default function SessionPage() {
  const id = useSearchParams().get("id");
  const sessionsQ = useTable("workout_sessions");
  const setsQ = useTable("workout_sets");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");

  if (sessionsQ.isPending || setsQ.isPending || routinesQ.isPending || exercisesQ.isPending) return <Spinner />;
  const session = sessionsQ.data?.find((s) => s.id === id);
  if (!session) return <Empty>Workout not found. <Link href="/history?tab=workouts" className="text-primary underline">Back to history</Link></Empty>;

  const sets = (setsQ.data ?? []).filter((s) => s.session_id === session.id);
  const summary = sessionSummaries([session], sets, routinesQ.data ?? [])[0];
  const exerciseById = new Map((exercisesQ.data ?? []).map((e) => [e.id, e]));
  const groups = new Map<string, { exercise: Exercise | undefined; sets: WorkoutSet[] }>();
  for (const s of [...sets].sort((a, b) => (exerciseById.get(a.exercise_id)?.sort_order ?? 0) - (exerciseById.get(b.exercise_id)?.sort_order ?? 0) || a.set_no - b.set_no)) {
    const g = groups.get(s.exercise_id) ?? { exercise: exerciseById.get(s.exercise_id), sets: [] };
    g.sets.push(s);
    groups.set(s.exercise_id, g);
  }

  return (
    <div className="flex flex-col gap-4">
      <Link href="/history?tab=workouts" className="text-sm text-primary">← History</Link>
      <div>
        <h1 className="text-xl font-semibold">{summary?.routineName ?? "Workout"}</h1>
        <p className="text-sm text-fg-muted">
          {dateFmt.format(session.started_at)} · {durationMinutes(session.started_at, session.ended_at)} min · {formatVolume(summary?.volume ?? 0)} · {sets.length} sets
        </p>
      </div>
      {groups.size === 0 && <Empty>No sets were logged.</Empty>}
      {[...groups.values()].map((g, i) => (
        <Card key={i}>
          <h2 className="mb-2 font-semibold">{g.exercise?.name ?? "Exercise"}</h2>
          <ul className="divide-y divide-line">
            {g.sets.map((s) => <SetRow key={s.id} set={s} />)}
          </ul>
        </Card>
      ))}
    </div>
  );
}

function SetRow({ set }: { set: WorkoutSet }) {
  const qc = useQueryClient();
  const [reps, setReps] = useState(String(set.reps));
  const [weight, setWeight] = useState(String(set.weight));
  const action = useAction();
  const timed = set.reps === 0 && set.weight === 0;
  const dirty = Number(reps) !== set.reps || Number(weight) !== set.weight;

  return (
    <li className="flex flex-wrap items-center gap-2 py-2">
      <span className="w-14 text-sm text-fg-muted">Set {set.set_no}</span>
      {timed ? (
        <span className="flex-1 text-sm">Timed</span>
      ) : (
        <div className="flex flex-1 items-center gap-2 text-sm">
          <Input type="number" min={0} aria-label="Reps" value={reps} onChange={(e) => setReps(e.target.value)} className="w-20" />
          <span>×</span>
          <Input type="number" min={0} step={0.5} aria-label="Weight in kg" value={weight} onChange={(e) => setWeight(e.target.value)} className="w-24" />
          <span>kg</span>
          {dirty && <Button variant="primary" disabled={action.busy} onClick={() => action.run(() => editSet(qc, set.id, Number(reps) || 0, Number(weight) || 0))}>Save</Button>}
        </div>
      )}
      <IconButton label={`Delete set ${set.set_no}`} onClick={() => action.run(() => deleteSet(qc, set.id))}>{Icons.trash}</IconButton>
      <ErrorText error={action.error} />
    </li>
  );
}
