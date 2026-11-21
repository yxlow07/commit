"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useUser } from "@/components/AppShell";
import { useTable } from "@/lib/data/store";
import { DEFAULT_SETTINGS, saveSettings, settingsId } from "@/lib/data/mutations";
import { supabase } from "@/lib/data/supabase";
import { pad } from "@/lib/domain/dates";
import type { Settings } from "@/lib/domain/types";
import { Button, Card, ErrorText, Input, Spinner, useAction } from "@/components/ui";

const toTime = (minutes: number) => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
const toMinutes = (time: string) => {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
};

/** Recap times and toggles (F6.5), shared with the phone through the user's settings row. */
export default function SettingsPage() {
  const user = useUser();
  const qc = useQueryClient();
  const settingsQ = useTable("settings");
  const action = useAction();

  if (settingsQ.isPending) return <Spinner />;
  const row = settingsQ.data?.find((s) => s.id === settingsId(user.id)) ?? settingsQ.data?.[0];
  const s = { ...DEFAULT_SETTINGS, ...row };
  const save = (changes: Partial<Settings>) => action.run(() => saveSettings(qc, user.id, row, changes));

  return (
    <div className="flex max-w-xl flex-col gap-4">
      <h1 className="text-xl font-semibold">Settings</h1>
      <Card className="flex flex-col gap-4">
        <h2 className="font-semibold">Daily recaps on the phone</h2>
        <RecapRow label="Morning recap" hint="Due today, overdue, chores and today's workout" minutes={s.morning_recap_time} enabled={s.morning_recap_enabled === 1}
          onTime={(m) => save({ morning_recap_time: m })} onToggle={(on) => save({ morning_recap_enabled: on ? 1 : 0 })} />
        <RecapRow label="Night recap" hint="What you did today and what's due tomorrow" minutes={s.night_recap_time} enabled={s.night_recap_enabled === 1}
          onTime={(m) => save({ night_recap_time: m })} onToggle={(on) => save({ night_recap_enabled: on ? 1 : 0 })} />
        <p className="text-xs text-fg-muted">Recurring chores remind you at the morning recap time on the day they&apos;re due. Changes reach the phone on its next sync.</p>
        <ErrorText error={action.error} />
      </Card>
      <Card className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-fg-muted">Signed in as</p>
          <p className="truncate font-medium">{user.email}</p>
        </div>
        <Button onClick={() => supabase.auth.signOut()}>Sign out</Button>
      </Card>
    </div>
  );
}

function RecapRow({ label, hint, minutes, enabled, onTime, onToggle }: {
  label: string; hint: string; minutes: number; enabled: boolean; onTime: (m: number) => void; onToggle: (on: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{label}</p>
        <p className="text-xs text-fg-muted">{hint}</p>
      </div>
      <Input type="time" aria-label={`${label} time`} defaultValue={toTime(minutes)} key={minutes}
        onBlur={(e) => { if (e.target.value && toMinutes(e.target.value) !== minutes) onTime(toMinutes(e.target.value)); }} />
      <input type="checkbox" aria-label={`${label} on`} checked={enabled} onChange={(e) => onToggle(e.target.checked)} className="h-5 w-5 accent-[var(--primary)]" />
    </div>
  );
}
