"use client";

import { useMemo } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTable } from "@/lib/data/store";
import { addDays, startOfDay } from "@/lib/domain/dates";
import { weekdayIndex } from "@/lib/domain/format";
import type { Commitment, Routine } from "@/lib/domain/types";

/** The app's four tabs, in its order. */
export const PAGES = [
  { href: "/", label: "Groups" },
  { href: "/agenda", label: "Agenda" },
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

/** Which tab a page belongs to: a commitment and its routines live under Commitments, a session under History. */
export function usePageActive() {
  const path = usePathname();
  return (href: string) =>
    href === "/" ? path === "/" || path.startsWith("/c") || path.startsWith("/routine")
      : href === "/history" ? path.startsWith("/history") || path.startsWith("/session")
        : path.startsWith(href);
}

/**
 * 600 px and up: the tabs as a row of text above the page, left-aligned with it. Selected is Bold
 * `ink`, the rest Regular `muted`, exactly like the app's bottom bar.
 */
export function TopNav() {
  const active = usePageActive();
  return (
    <nav aria-label="Main" className="hidden h-16 items-center gap-8 sm:flex">
      {PAGES.map((p) => (
        <Link key={p.href} href={p.href} aria-current={active(p.href) ? "page" : undefined}
          className={`-mx-1 rounded-lg px-1 py-2 text-body-l ${active(p.href) ? "font-bold text-ink" : "text-muted hover:text-ink"}`}>
          {p.label}
        </Link>
      ))}
    </nav>
  );
}

/** Phone: the app's bottom bar, four text tabs on `surface`. */
export function BottomNav() {
  const active = usePageActive();
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-20 grid h-[72px] grid-cols-4 bg-surface pb-[env(safe-area-inset-bottom)] sm:hidden">
      {PAGES.map((p) => (
        <Link key={p.href} href={p.href} aria-current={active(p.href) ? "page" : undefined}
          className={`flex items-center justify-center text-body ${active(p.href) ? "font-bold text-ink" : "text-muted"}`}>
          {p.label}
        </Link>
      ))}
    </nav>
  );
}
