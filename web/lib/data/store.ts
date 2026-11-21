"use client";

import { useQuery, type QueryClient } from "@tanstack/react-query";
import { supabase } from "./supabase";
import type { TableName, Tables } from "@/lib/domain/types";

/**
 * One cached list per table with all of the user's live (not deleted) rows. One person's data is
 * small, so lists load whole; Realtime events and writes patch these caches in place.
 */
export const tableKey = (table: TableName) => ["table", table] as const;

const PAGE = 1000; // PostgREST's default row limit per request

export async function fetchTable<T extends TableName>(table: T): Promise<Tables[T][]> {
  const rows: Tables[T][] = [];
  for (let from = 0; ; from += PAGE) {
    const { data, error } = await supabase
      .from(table)
      .select("*")
      .eq("deleted", 0)
      .order("id")
      .range(from, from + PAGE - 1);
    if (error) throw error;
    rows.push(...(data as Tables[T][]));
    if (!data || data.length < PAGE) return rows;
  }
}

export function useTable<T extends TableName>(table: T) {
  return useQuery({ queryKey: tableKey(table), queryFn: () => fetchTable(table), staleTime: Infinity });
}

/** Puts a row into its table's cache, or removes it once soft-deleted. */
export function putRow<T extends TableName>(qc: QueryClient, table: T, row: Tables[T]) {
  qc.setQueryData<Tables[T][]>(tableKey(table), (rows) => {
    if (!rows) return rows;
    const rest = rows.filter((r) => r.id !== row.id);
    return row.deleted ? rest : [...rest, row];
  });
}

export function removeRow(qc: QueryClient, table: TableName, id: string) {
  qc.setQueryData<{ id: string }[]>(tableKey(table), (rows) => rows?.filter((r) => r.id !== id));
}
