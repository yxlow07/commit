"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { deleteSession, deleteSet, editSet, restoreRow, restoreSession } from "@/lib/data/mutations";
import { sessionSummaries } from "@/lib/data/history";
import { durationMinutes, formatVolume, formatWeight } from "@/lib/domain/format";
import { shortDate } from "@/lib/domain/labels";
import type { Exercise, WorkoutSet } from "@/lib/domain/types";
import { usePageTitle } from "@/components/AppShell";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { useToast } from "@/components/Toast";
import { BackLink, Button, EmptyText, IconButton, Icons, Loading, PageTitle, SectionHeading } from "@/components/ui";

/** Workout session (web spec, "Workout session"): every set as done; fix or delete any of them. */
export default function SessionPage() {
  const id = useSearchParams().get("id");
  const sessionsQ = useTable("workout_sessions");
  const setsQ = useTable("workout_sets");
  const routinesQ = useTable("routines");
  const exercisesQ = useTable("exercises");
  const session = sessionsQ.data?.find((s) => s.id === id);
  const qc = useQueryClient();
  const router = useRouter();
  const toast = useToast();
  const online = useOnline();
  const summary = session ? sessionSummaries([session], (setsQ.data ?? []).filter((s) => s.session_id === session.id), routinesQ.data ?? [])[0] : undefined;
  usePageTitle(summary?.routineName ?? "Workout");

  if (sessionsQ.isPending || setsQ.isPending || routinesQ.isPending || exercisesQ.isPending) return <Loading />;
  if (!session) return <EmptyText>This workout doesn&apos;t exist any more. <Link href="/history?tab=workouts" className="font-bold hover:underline">Back to History</Link></EmptyText>;

  const sets = (setsQ.data ?? []).filter((s) => s.session_id === session.id && !s.skipped);
  const exerciseById = new Map((exercisesQ.data ?? []).map((e) => [e.id, e]));
  const groups = new Map<string, { exercise: Exercise | undefined; sets: WorkoutSet[] }>();
  for (const s of [...sets].sort((a, b) => (exerciseById.get(a.exercise_id)?.sort_order ?? 0) - (exerciseById.get(b.exercise_id)?.sort_order ?? 0) || a.set_no - b.set_no)) {
    const g = groups.get(s.exercise_id) ?? { exercise: exerciseById.get(s.exercise_id), sets: [] };
    g.sets.push(s);
    groups.set(s.exercise_id, g);
  }
  async function removeSession() {
    if (!session) return;
    const setIds = (setsQ.data ?? []).filter((x) => x.session_id === session.id).map((x) => x.id);
    try {
      await deleteSession(qc, session.id, setIds);
      router.push("/history?tab=workouts");
      toast({ message: `${summary?.routineName ?? "Workout"} session deleted`, action: { label: "Undo", run: () => restoreSession(qc, session.id, setIds) } });
    } catch {
      toast({ message: "Couldn't delete. Check your connection." });
    }
  }

  const line = [
    shortDate(session.started_at),
    `${durationMinutes(session.started_at, session.ended_at)} min`,
    sets.length === 1 ? "1 set" : `${sets.length} sets`,
    formatVolume(summary?.volume ?? 0),
  ].join(", ");

  return (
    <div className="flex flex-col gap-10">
      <PageTitle
        title={summary?.routineName ?? "Workout"}
        summary={line}
        before={
          <div className="flex items-start justify-between gap-4">
            <BackLink href="/history?tab=workouts" label="History" />
            <Button variant="quiet" className="-mr-3" disabled={!online} title={online ? undefined : OFFLINE_REASON} onClick={removeSession}>Delete session</Button>
          </div>
        }
      />
      {groups.size === 0 && <EmptyText>No sets were logged.</EmptyText>}
      {[...groups.values()].map((g, i) => (
        <section key={i} className="flex flex-col">
          <SectionHeading className="pb-1">{g.exercise?.name ?? "Exercise"}</SectionHeading>
          <ul>{g.sets.map((s) => <SetRow key={s.id} set={s} />)}</ul>
        </section>
      ))}
    </div>
  );
}

/** "Set 1", then reps and weight as numbers that become fields on click (Enter saves, Esc cancels). */
function SetRow({ set }: { set: WorkoutSet }) {
  const qc = useQueryClient();
  const toast = useToast();
  const online = useOnline();
  const [editing, setEditing] = useState<"reps" | "weight" | null>(null);
  const [text, setText] = useState("");
  const timed = set.reps === 0 && set.weight === 0;

  async function commit(field: "reps" | "weight") {
    const n = Number(text.replace(",", "."));
    setEditing(null);
    if (!Number.isFinite(n) || n < 0) return;
    const reps = field === "reps" ? Math.round(n) : set.reps;
    const weight = field === "weight" ? n : set.weight;
    if (reps === set.reps && weight === set.weight) return;
    try {
      await editSet(qc, set.id, reps, weight);
    } catch {
      toast({ message: "Couldn't save. Check your connection." });
    }
  }

  async function remove() {
    try {
      await deleteSet(qc, set.id);
      toast({ message: `Set ${set.set_no} deleted`, action: { label: "Undo", run: () => restoreRow(qc, "workout_sets", set.id) } });
    } catch {
      toast({ message: "Couldn't delete. Check your connection." });
    }
  }

  const value = (field: "reps" | "weight", shown: string, label: string) =>
    editing === field ? (
      <input
        autoFocus
        aria-label={label}
        inputMode="decimal"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onBlur={() => commit(field)}
        onKeyDown={(e) => {
          if (e.key === "Enter") { e.preventDefault(); commit(field); }
          if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); setEditing(null); }
        }}
        className="h-10 w-20 rounded-xl bg-fill px-2 text-center text-body-l tabular-nums outline-none focus:outline-2 focus:outline-ink"
      />
    ) : (
      <button type="button" disabled={!online} title={online ? `Edit ${label.toLowerCase()}` : OFFLINE_REASON}
        onClick={() => { setText(String(field === "reps" ? set.reps : set.weight)); setEditing(field); }}
        className="h-10 cursor-pointer rounded-xl px-2 text-body-l tabular-nums hover:bg-fill">
        {shown}
      </button>
    );

  return (
    <li className="group -mx-3 flex min-h-14 items-center gap-2 rounded-xl px-3 hover:bg-hover">
      <span className="w-16 shrink-0 text-body text-muted">Set {set.set_no}</span>
      {timed ? <span className="flex-1 text-body-l">Timed</span> : (
        <span className="flex flex-1 items-center gap-1">
          {value("reps", `${set.reps}`, "Reps")}
          <span className="text-muted">×</span>
          {value("weight", formatWeight(set.weight), "Weight in kg")}
        </span>
      )}
      <IconButton label={`Delete set ${set.set_no}`} disabled={!online} className="opacity-0 focus-visible:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100" onClick={remove}>
        {Icons.trash}
      </IconButton>
    </li>
  );
}
