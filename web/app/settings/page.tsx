"use client";

import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useUser } from "@/components/AppShell";
import { useTable } from "@/lib/data/store";
import {
  createTheme,
  DEFAULT_SETTINGS,
  deleteTheme,
  saveSettings,
  settingsId,
} from "@/lib/data/mutations";
import { supabase } from "@/lib/data/supabase";
import { pad } from "@/lib/domain/dates";
import {
  BUILTIN_DARK,
  BUILTIN_DYNAMIC,
  BUILTIN_LIGHT,
  parseTheme,
  parseThemeOrNull,
  THEME_DEFAULTS,
  type ThemeSpec,
} from "@/lib/domain/theme";
import type { Settings } from "@/lib/domain/types";
import {
  Button,
  Card,
  ErrorText,
  Icons,
  Input,
  Modal,
  Spinner,
  useAction,
} from "@/components/ui";

const toTime = (minutes: number) => `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
const toMinutes = (time: string) => {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
};

/** Recap times, theme picker and account (F6.5, F9.3). */
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

      <ThemeSection
        activeId={s.active_theme_id}
        onSelect={(id) => save({ active_theme_id: id })}
        userId={user.id}
      />

      <Card className="flex flex-col gap-4">
        <h2 className="font-semibold">Daily recaps on the phone</h2>
        <RecapRow
          label="Morning recap"
          hint="Due today, overdue, chores and today's workout"
          minutes={s.morning_recap_time}
          enabled={s.morning_recap_enabled === 1}
          onTime={(m) => save({ morning_recap_time: m })}
          onToggle={(on) => save({ morning_recap_enabled: on ? 1 : 0 })}
        />
        <RecapRow
          label="Night recap"
          hint="What you did today and what's due tomorrow"
          minutes={s.night_recap_time}
          enabled={s.night_recap_enabled === 1}
          onTime={(m) => save({ night_recap_time: m })}
          onToggle={(on) => save({ night_recap_enabled: on ? 1 : 0 })}
        />
        <p className="text-xs text-fg-muted">
          Recurring chores remind you at the morning recap time on the day they&apos;re due. Changes reach the phone on its next sync.
        </p>
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

function ThemeSection({
  activeId,
  onSelect,
  userId,
}: {
  activeId: string | null;
  onSelect: (id: string | null) => void;
  userId: string;
}) {
  const qc = useQueryClient();
  const themesQ = useTable("themes");
  const fileRef = useRef<HTMLInputElement>(null);
  const [urlModal, setUrlModal] = useState(false);
  const [url, setUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const importedThemes = themesQ.data ?? [];

  async function handleImportJson(text: string) {
    setError(null);
    const parsed = parseTheme(text);
    if (!parsed.ok) {
      setError(parsed.errors.join("\n"));
      return;
    }
    setBusy(true);
    try {
      await createTheme(qc, parsed.spec.name, text);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  async function handleImportUrl() {
    if (!url.trim()) return;
    setError(null);
    setBusy(true);
    try {
      const res = await fetch(url.trim());
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      const text = await res.text();
      await handleImportJson(text);
      setUrlModal(false);
      setUrl("");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Theme</h2>
        <div className="flex gap-2">
          <input
            ref={fileRef}
            type="file"
            accept=".json,application/json"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                file.text().then(handleImportJson);
                e.target.value = "";
              }
            }}
          />
          <Button variant="secondary" onClick={() => fileRef.current?.click()} disabled={busy}>
            Import file
          </Button>
          <Button variant="secondary" onClick={() => setUrlModal(true)} disabled={busy}>
            Import link
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <p className="text-xs font-medium text-fg-muted">Built-in</p>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          <ThemeOption
            name="Material You"
            subtitle="Default palette"
            selected={!activeId || activeId === BUILTIN_DYNAMIC}
            onSelect={() => onSelect(null)}
            spec={THEME_DEFAULTS.spec("Material You")}
          />
          <ThemeOption
            name="Light"
            subtitle="Forced light"
            selected={activeId === BUILTIN_LIGHT}
            onSelect={() => onSelect(BUILTIN_LIGHT)}
            spec={THEME_DEFAULTS.spec("Light")}
          />
          <ThemeOption
            name="Dark"
            subtitle="Forced dark"
            selected={activeId === BUILTIN_DARK}
            onSelect={() => onSelect(BUILTIN_DARK)}
            spec={THEME_DEFAULTS.spec("Dark")}
          />
        </div>
      </div>

      {importedThemes.length > 0 && (
        <div className="flex flex-col gap-2">
          <p className="text-xs font-medium text-fg-muted">Imported</p>
          <div className="flex flex-col gap-2">
            {importedThemes.map((t) => {
              const spec = parseThemeOrNull(t.json);
              return (
                <div key={t.id} className="flex items-center justify-between rounded-lg border border-line p-3">
                  <div className="flex items-center gap-3">
                    <input
                      type="radio"
                      name="theme"
                      checked={activeId === t.id}
                      onChange={() => onSelect(t.id)}
                      className="h-4 w-4 accent-[var(--primary)]"
                    />
                    <div>
                      <p className="font-medium">{t.name}</p>
                      {spec && (
                        <p className="text-xs text-fg-muted">
                          {spec.font} · {spec.cornerRadius}px
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {spec && (
                      <div className="flex gap-1.5 mr-2">
                        <span className="h-4 w-4 rounded-full border border-line" style={{ background: spec.light.primary }} />
                        <span className="h-4 w-4 rounded-full border border-line" style={{ background: spec.light.secondary }} />
                        <span className="h-4 w-4 rounded-full border border-line" style={{ background: spec.light.background }} />
                      </div>
                    )}
                    <button
                      type="button"
                      aria-label="Delete theme"
                      onClick={() => deleteTheme(qc, t.id, activeId, userId)}
                      className="text-fg-muted hover:text-danger p-1"
                    >
                      {Icons.trash}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {error && <ErrorText error={error} />}

      <Modal open={urlModal} onClose={() => setUrlModal(false)} title="Import theme from link">
        <div className="flex flex-col gap-3">
          <p className="text-sm text-fg-muted">Enter a link to a theme JSON file (https://):</p>
          <Input
            placeholder="https://commit.yxuan.dev/themes/ocean.json"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
          />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setUrlModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleImportUrl} disabled={busy || !url.trim()}>
              {busy ? "Importing…" : "Import"}
            </Button>
          </div>
        </div>
      </Modal>
    </Card>
  );
}

function ThemeOption({
  name,
  subtitle,
  selected,
  onSelect,
  spec,
}: {
  name: string;
  subtitle: string;
  selected: boolean;
  onSelect: () => void;
  spec: ThemeSpec;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`flex flex-col gap-2 rounded-lg border p-3 text-left transition ${
        selected ? "border-primary bg-primary-soft/20 ring-2 ring-primary/30" : "border-line bg-surface hover:bg-surface-2"
      }`}
    >
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold">{name}</span>
        <div className="flex gap-1">
          <span className="h-3.5 w-3.5 rounded-full border border-line" style={{ background: spec.light.primary }} />
          <span className="h-3.5 w-3.5 rounded-full border border-line" style={{ background: spec.light.secondary }} />
        </div>
      </div>
      <span className="text-xs text-fg-muted">{subtitle}</span>
    </button>
  );
}

function RecapRow({
  label,
  hint,
  minutes,
  enabled,
  onTime,
  onToggle,
}: {
  label: string;
  hint: string;
  minutes: number;
  enabled: boolean;
  onTime: (m: number) => void;
  onToggle: (on: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{label}</p>
        <p className="text-xs text-fg-muted">{hint}</p>
      </div>
      <Input
        type="time"
        aria-label={`${label} time`}
        defaultValue={toTime(minutes)}
        key={minutes}
        onBlur={(e) => {
          if (e.target.value && toMinutes(e.target.value) !== minutes) onTime(toMinutes(e.target.value));
        }}
      />
      <input
        type="checkbox"
        aria-label={`${label} on`}
        checked={enabled}
        onChange={(e) => onToggle(e.target.checked)}
        className="h-5 w-5 accent-[var(--primary)]"
      />
    </div>
  );
}
