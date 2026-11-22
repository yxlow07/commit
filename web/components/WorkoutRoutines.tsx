"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { createRoutine, reorderRoutines } from "@/lib/data/mutations";
import { weekdaysLabel } from "@/lib/domain/format";
import { cssColor } from "@/lib/domain/format";
import type { Commitment } from "@/lib/domain/types";
import { SortableList } from "./Sortable";
import { Button, Card, ErrorText, Input, useAction } from "./ui";

/**
 * A workout commitment's routines (F5.1), shown on the main page when it's selected (the phone shows
 * them on Today). Drag to reorder; a routine opens its editor. Workouts themselves run on the phone.
 */
export function WorkoutRoutines({ commitment }: { commitment: Commitment }) {
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const qc = useQueryClient();
  const router = useRouter();
  const [name, setName] = useState("");
  const action = useAction();

  const routines = (routinesQ.data ?? []).filter((r) => r.commitment_id === commitment.id).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name));
  const exerciseCount = (routineId: string) => (exercisesQ.data ?? []).filter((e) => e.routine_id === routineId).length;
  const today = (new Date().getDay() + 6) % 7; // Mon = 0, as the planned_days mask

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
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">{commitment.name}</h1>
      <Card>
        {routines.length === 0 ? (
          <p className="py-3 text-sm text-fg-muted">No routines yet. Add each workout day below, like Push, Pull and Legs.</p>
        ) : (
          <SortableList
            items={routines}
            getId={(r) => r.id}
            getLabel={(r) => r.name}
            onReorder={(ids) => action.run(() => reorderRoutines(qc, routines, ids))}
            className="flex flex-col divide-y divide-line"
            renderItem={(r, handle) => {
              const count = exerciseCount(r.id);
              const days = weekdaysLabel(r.planned_days);
              const planned = (r.planned_days & (1 << today)) !== 0;
              const detail = [planned ? "planned today" : "", count === 1 ? "1 exercise" : `${count} exercises`, days].filter(Boolean).join(" · ");
              return (
                <div className="flex items-center gap-2 py-2">
                  {handle}
                  <Link href={`/routine?id=${r.id}`} className="min-w-0 flex-1">
                    <span className="block font-medium">{r.name}</span>
                    <span className="block text-xs" style={{ color: `color-mix(in srgb, var(--fg-muted) 65%, ${cssColor(commitment.color)})` }}>{detail}</span>
                  </Link>
                </div>
              );
            }}
          />
        )}
        <form className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (name.trim()) add(); }}>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="New routine, e.g. Pull Day" className="flex-1" />
          <Button variant="primary" type="submit" disabled={!name.trim() || action.busy}>Add routine</Button>
        </form>
        <ErrorText error={action.error} />
      </Card>
    </div>
  );
}
