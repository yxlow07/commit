"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useTable } from "@/lib/data/store";
import { reorderCommitments } from "@/lib/data/mutations";
import { openItems } from "@/lib/domain/recurrence";
import { addDays, startOfDay } from "@/lib/domain/dates";
import { cssColor, weekdayIndex } from "@/lib/domain/format";
import type { Commitment, CommitmentType, Routine } from "@/lib/domain/types";
import { CommitmentDialog } from "./CommitmentDialog";
import { SortableList } from "./Sortable";
import { useToast } from "./Toast";
import { IconButton, Icons, Logo, useNow } from "./ui";

export const PAGES = [
  { href: "/", label: "Today" },
  { href: "/history", label: "History" },
  { href: "/settings", label: "Settings" },
] as const;

/** Live commitments in their saved order. */
export function useCommitments(): Commitment[] {
  const q = useTable("commitments");
  return useMemo(
    () => (q.data ?? []).filter((c) => !c.archived).sort((a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name)),
    [q.data],
  );
}

const weekdayShort = new Intl.DateTimeFormat("en-GB", { weekday: "short" });

/** "Today", "Mon"… the next day any of [routines] is planned, or "". */
export function nextPlanned(routines: Routine[], now: number): string {
  const today = startOfDay(now);
  for (let i = 0; i < 7; i++) {
    const day = addDays(today, i);
    if (routines.some((r) => (r.planned_days & (1 << weekdayIndex(day))) !== 0)) return i === 0 ? "Today" : weekdayShort.format(day);
  }
  return "";
}

/** Which page tab is current, for the sidebar and the bottom nav. */
export function usePageActive() {
  const path = usePathname();
  return (href: string) =>
    href === "/" ? path === "/" || path === "/c" || path === "/routine"
      : href === "/history" ? path.startsWith("/history") || path.startsWith("/session")
        : path.startsWith(href);
}

/**
 * The sidebar (web spec, "Sidebar"): logo, pages, then Commitments and Workouts groups. Rows
 * reorder by their grip; on hover a pencil replaces the count.
 */
export function Sidebar() {
  const pageActive = usePageActive();
  const pathname = usePathname();
  const params = useSearchParams();
  const qc = useQueryClient();
  const toast = useToast();
  const now = useNow();
  const commitments = useCommitments();
  const todosQ = useTable("todos");
  const routinesQ = useTable("routines");
  const [dialog, setDialog] = useState<{ open: boolean; commitment: Commitment | null; type: CommitmentType }>({ open: false, commitment: null, type: "standard" });

  // The commitment the current page belongs to (a routine belongs to its workout commitment).
  const routineId = pathname === "/routine" ? params.get("id") : null;
  const activeId = pathname === "/c" ? params.get("id")
    : routineId ? routinesQ.data?.find((r) => r.id === routineId)?.commitment_id ?? null : null;

  const todos = todosQ.data ?? [];
  const routines = routinesQ.data ?? [];
  const todoGroup = commitments.filter((c) => c.type !== "workout");
  const workoutGroup = commitments.filter((c) => c.type === "workout");

  const saveGroup = (groupIds: string[]) => {
    const members = new Set(groupIds);
    const next = groupIds[Symbol.iterator]();
    const full = commitments.map((c) => (members.has(c.id) ? next.next().value! : c.id));
    reorderCommitments(qc, commitments, full).catch(() => toast({ message: "Couldn't save the order. Check your connection." }));
  };

  const group = (list: Commitment[], workout: boolean) => (
    <SortableList
      items={list}
      getId={(c) => c.id}
      getLabel={(c) => c.name}
      onReorder={saveGroup}
      className="flex flex-col"
      renderItem={(c, handle) => {
        const active = c.id === activeId;
        const count = workout
          ? nextPlanned(routines.filter((r) => r.commitment_id === c.id), now)
          : String(openItems(todos.filter((t) => t.commitment_id === c.id), now).length);
        return (
          <div className="-ml-8 flex h-9 items-center rounded-lg">
            {handle}
            <Link
              href={`/c?id=${c.id}`}
              data-commitment-link={c.id}
              aria-current={active ? "page" : undefined}
              className={`flex h-9 min-w-0 flex-1 items-center truncate rounded-lg px-2 text-label ${active ? "font-bold text-ink" : "font-normal text-muted hover:bg-hover hover:text-ink"}`}
            >
              <span className="truncate">{c.name}</span>
            </Link>
            <span
              className={`w-8 shrink-0 pr-1 text-right text-label tabular-nums group-hover/sort:hidden group-focus-within/sort:hidden ${workout ? "text-muted" : ""}`}
              style={workout ? undefined : { color: cssColor(c.color) }}
              aria-label={workout ? (count ? `Next planned ${count}` : undefined) : `${count} open`}
            >
              {workout ? count : count === "0" ? "" : count}
            </span>
            <span className="hidden group-focus-within/sort:flex group-hover/sort:flex">
              <IconButton label={`Edit ${c.name}`} onClick={() => setDialog({ open: true, commitment: c, type: c.type })}>
                {Icons.pencil}
              </IconButton>
            </span>
          </div>
        );
      }}
    />
  );

  return (
    <nav aria-label="Main" className="flex min-h-full flex-col gap-6 px-6 pb-8 pt-6">
      <Link href="/" className="flex h-9 items-center gap-2.5 self-start rounded-lg text-label font-bold text-ink">
        <Logo size={24} />
        Commit
      </Link>

      <ul className="flex flex-col">
        {PAGES.map((p) => {
          const active = pageActive(p.href) && !(p.href === "/" && activeId);
          return (
            <li key={p.href}>
              <Link href={p.href} aria-current={active ? "page" : undefined}
                className={`flex h-9 items-center rounded-lg px-2 -mx-2 text-label ${active ? "font-bold text-ink" : "font-normal text-muted hover:bg-hover hover:text-ink"}`}>
                {p.label}
              </Link>
            </li>
          );
        })}
      </ul>

      <section className="flex flex-col gap-1" aria-labelledby="side-commitments">
        <div className="flex h-8 items-center justify-between">
          <h2 id="side-commitments" className="text-caption text-muted">Commitments</h2>
          <IconButton label="New commitment" className="-mr-1.5" onClick={() => setDialog({ open: true, commitment: null, type: "standard" })}>{Icons.plus}</IconButton>
        </div>
        {todoGroup.length === 0 ? <p className="text-meta text-muted">Add your first commitment, like Study or Work.</p> : group(todoGroup, false)}
      </section>

      <section className="flex flex-col gap-1" aria-labelledby="side-workouts">
        <div className="flex h-8 items-center justify-between">
          <h2 id="side-workouts" className="text-caption text-muted">Workouts</h2>
          <IconButton label="New workout commitment" className="-mr-1.5" onClick={() => setDialog({ open: true, commitment: null, type: "workout" })}>{Icons.plus}</IconButton>
        </div>
        {workoutGroup.length > 0 && group(workoutGroup, true)}
      </section>

      <CommitmentDialog
        open={dialog.open}
        commitment={dialog.commitment}
        type={dialog.type}
        nextSortOrder={commitments.length ? Math.max(...commitments.map((c) => c.sort_order)) + 1 : 0}
        onClose={() => setDialog((d) => ({ ...d, open: false }))}
      />
    </nav>
  );
}
