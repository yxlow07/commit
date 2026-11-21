"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { supabase } from "./supabase";
import { putRow, removeRow, tableKey } from "./store";
import { TABLES, type TableName, type Tables } from "@/lib/domain/types";

/**
 * Live updates (F10.3): listens to row changes on every synced table and patches the matching
 * cache, so a tick on the phone shows up here within a second or two. Row-level security applies
 * to these events too. After a (re)connect everything is refetched once to catch up.
 */
export function useRealtime(userId: string) {
  const qc = useQueryClient();
  useEffect(() => {
    let channel = supabase.channel(`commit-${userId}`);
    for (const table of TABLES) {
      channel = channel.on("postgres_changes", { event: "*", schema: "public", table }, (payload) => {
        if (payload.eventType === "DELETE") {
          const id = (payload.old as { id?: string }).id;
          if (id) removeRow(qc, table, id);
        } else {
          putRow(qc, table as TableName, payload.new as Tables[TableName]);
        }
      });
    }
    channel.subscribe((status) => {
      if (status === "SUBSCRIBED") {
        for (const table of TABLES) qc.invalidateQueries({ queryKey: tableKey(table) });
      }
    });
    return () => {
      supabase.removeChannel(channel);
    };
  }, [qc, userId]);
}
