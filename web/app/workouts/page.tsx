"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createRoutine, deleteRoutine, moveRoutine } from "@/lib/data/mutations";
import { weekdaysLabel } from "@/lib/domain/format";
import type { Commitment } from "@/lib/domain/types";
import { Button, Card, ColorDot, Empty, ErrorText, IconButton, Icons, Input, Spinner, useAction } from "@/components/ui";

/** Routines of each workout commitment (F5.1). Workouts are run on the phone. */
export default function WorkoutsPage() {
  const commitmentsQ = useTable("commitments");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");

  const workoutCommitments = useMemo(
    () => (commitmentsQ.data ?? []).filter((c) => c.type === "workout" && !c.archived).sort((a, b) => a.sort_order - b.sort_order),
    [commitmentsQ.data],
  );

  if (commitmentsQ.isPending || routinesQ.isPending || exercisesQ.isPending) return <Spinner />;
  const error = commitmentsQ.error ?? routinesQ.error ?? exercisesQ.error;
  if (error) return <ErrorText error={error} />;

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">Workouts</h1>
      {workoutCommitments.length === 0 && (
        <Empty>
          No workout commitments yet. Create one on the <Link href="/" className="text-primary underline">Todos</Link> page with type “Workout”.
        </Empty>
      )}
      {workoutCommitments.map((c) => <CommitmentRoutines key={c.id} commitment={c} />)}
    </div>
  );
}

function CommitmentRoutines({ commitment }: { commitment: Commitment }) {
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const qc = useQueryClient();
  const router = useRouter();
  const [name, setName] = useState("");
  const action = useAction();

  const routines = (routinesQ.data ?? []).filter((r) => r.commitment_id === commitment.id).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
  const exerciseCount = (routineId: string) => (exercisesQ.data ?? []).filter((e) => e.routine_id === routineId).length;

  async function add() {
    let id = "";
    const ok = await action.run(async () => {
      const created = await createRoutine(qc, commitment.id, name, routines.length ? Math.max(...routines.map((r) => r.sort_order)) + 1 : 0);
      id = created.id;
    });
    if (ok) {
      setName("");
      router.push(`/routine?id=${id}`);
    }
  }

  return (
    <Card>
      <h2 className="mb-3 flex items-center gap-2 font-semibold"><ColorDot color={commitment.color} />{commitment.name}</h2>
      <ul className="flex flex-col divide-y divide-line">
        {routines.length === 0 && <li className="py-3 text-sm text-fg-muted">No routines yet.</li>}
        {routines.map((r, i) => {
          const count = exerciseCount(r.id);
          const days = weekdaysLabel(r.planned_days);
          return (
            <li key={r.id} className="flex items-center gap-2 py-2">
              <Link href={`/routine?id=${r.id}`} className="min-w-0 flex-1">
                <span className="block font-medium">{r.name}</span>
                <span className="block text-xs text-fg-muted">{count === 1 ? "1 exercise" : `${count} exercises`}{days ? ` · ${days}` : ""}</span>
              </Link>
              <IconButton label={`Move ${r.name} up`} disabled={i === 0} onClick={() => moveRoutine(qc, routines, r.id, -1)}>{Icons.up}</IconButton>
              <IconButton label={`Move ${r.name} down`} disabled={i === routines.length - 1} onClick={() => moveRoutine(qc, routines, r.id, 1)}>{Icons.down}</IconButton>
              <IconButton label={`Delete ${r.name}`} onClick={() => { if (confirm(`Delete routine “${r.name}”?`)) action.run(() => deleteRoutine(qc, r.id)); }}>{Icons.trash}</IconButton>
            </li>
          );
        })}
      </ul>
      <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (name.trim()) add(); }}>
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New routine, e.g. Push Day" className="flex-1" />
        <Button variant="primary" type="submit" disabled={!name.trim() || action.busy}>Add routine</Button>
      </form>
      <ErrorText error={action.error} />
    </Card>
  );
}
