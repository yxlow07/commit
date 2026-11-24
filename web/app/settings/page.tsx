"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useUser, usePageTitle } from "@/components/AppShell";
import { useTable } from "@/lib/data/store";
import { DEFAULT_SETTINGS, createTheme, deleteTheme, restoreCommitment, restoreRow, saveSettings, settingsId } from "@/lib/data/mutations";
import { supabase } from "@/lib/data/supabase";
import { BUILTIN_AUTOMATIC, BUILTIN_DARK, BUILTIN_LEGACY_DYNAMIC, BUILTIN_LIGHT, THEME_DEFAULTS, cssColor, parseTheme, parseThemeOrNull, type ThemeColors } from "@/lib/domain/theme";
import type { Settings } from "@/lib/domain/types";
import { OFFLINE_REASON, useOnline } from "@/components/Online";
import { TimeButton } from "@/components/Pickers";
import { useToast } from "@/components/Toast";
import { Button, Dialog, EmptyText, Field, IconButton, Icons, Loading, Switch, TextField, useNow } from "@/components/ui";

/** Settings (web spec, "Settings"): one page, sections 48 px apart. */
export default function SettingsPage() {
  usePageTitle("Settings");
  const user = useUser();
  const qc = useQueryClient();
  const toast = useToast();
  const settingsQ = useTable("settings");
  const [saved, setSaved] = useState(0);

  if (settingsQ.isPending) return <><h1 className="text-title-screen">Settings</h1><Loading /></>;
  const row = settingsQ.data?.find((s) => s.id === settingsId(user.id)) ?? settingsQ.data?.[0];
  const s = { ...DEFAULT_SETTINGS, ...row };
  const save = (changes: Partial<Settings>): Promise<unknown> =>
    saveSettings(qc, user.id, row, changes)
      .then(() => setSaved(Date.now()))
      .catch(() => toast({ message: "Couldn't save. Check your connection.", action: { label: "Try again", run: () => save(changes) } }));

  return (
    <div className="flex flex-col gap-12">
      <h1 className="text-title-screen">Settings</h1>

      <Section title="Recaps" aside={saved ? <SavedNote key={saved} /> : null}>
        <RecapRow label="Morning recap" hint="Today's plan" minutes={s.morning_recap_time} enabled={s.morning_recap_enabled === 1}
          onTime={(m) => save({ morning_recap_time: m })} onToggle={(on) => save({ morning_recap_enabled: on ? 1 : 0 })} />
        <RecapRow label="Night recap" hint="Today's wins, tomorrow's plan" minutes={s.night_recap_time} enabled={s.night_recap_enabled === 1}
          onTime={(m) => save({ night_recap_time: m })} onToggle={(on) => save({ night_recap_enabled: on ? 1 : 0 })} />
      </Section>

      <ThemeSection activeId={s.active_theme_id} onSelect={(id) => save({ active_theme_id: id })} userId={user.id} />

      <ArchivedSection />

      <Section title="Account">
        <div className="flex items-center justify-between gap-3">
          <span className="min-w-0 truncate text-body">{user.email}</span>
          <Button variant="text" onClick={() => supabase.auth.signOut()}>Sign out</Button>
        </div>
      </Section>
    </div>
  );
}

function Section({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between">
        <h2 className="text-title-section">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  );
}

/** A quiet "Saved" that fades after 2 seconds (remounted by key on every save). */
function SavedNote() {
  const [shown, setShown] = useState(true);
  useEffect(() => {
    const t = setTimeout(() => setShown(false), 2000);
    return () => clearTimeout(t);
  }, []);
  return <span role="status" className={`text-micro text-muted transition-opacity duration-300 ${shown ? "opacity-100" : "opacity-0"}`}>Saved</span>;
}

function RecapRow({ label, hint, minutes, enabled, onTime, onToggle }: {
  label: string; hint: string; minutes: number; enabled: boolean; onTime: (m: number) => void; onToggle: (on: boolean) => void;
}) {
  const now = useNow();
  const online = useOnline();
  return (
    <div className="flex min-h-14 items-center gap-3">
      <div className="min-w-0 flex-1">
        <p className="text-body">{label}</p>
        <p className="text-meta text-muted">{hint}</p>
      </div>
      <TimeButton label={`${label} time`} value={minutes} now={now} onChange={(m) => m !== minutes && onTime(m)} />
      <Switch label={label} checked={enabled} disabled={!online} onChange={onToggle} />
    </div>
  );
}

// ---------------- Theme ----------------

function ThemeSection({ activeId, onSelect, userId }: { activeId: string | null; onSelect: (id: string | null) => void; userId: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const online = useOnline();
  const themesQ = useTable("themes");
  const fileRef = useRef<HTMLInputElement>(null);
  const [linkOpen, setLinkOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const selectedId = !activeId || activeId === BUILTIN_LEGACY_DYNAMIC ? BUILTIN_AUTOMATIC : activeId;
  const options = [
    { id: BUILTIN_AUTOMATIC, name: "Automatic", source: "Default", colors: THEME_DEFAULTS.light },
    { id: BUILTIN_LIGHT, name: "Light", source: "Built in", colors: THEME_DEFAULTS.light },
    { id: BUILTIN_DARK, name: "Dark", source: "Built in", colors: THEME_DEFAULTS.dark },
    ...(themesQ.data ?? []).map((t) => ({ id: t.id, name: t.name, source: "Imported file", colors: parseThemeOrNull(t.json)?.light ?? THEME_DEFAULTS.light })),
  ];

  async function importJson(text: string): Promise<boolean> {
    setError(null);
    const parsed = parseTheme(text);
    if (!parsed.ok) { setError(parsed.errors.join(" ")); return false; }
    setBusy(true);
    try {
      const created = await createTheme(qc, parsed.spec.name, text);
      onSelect(created.id);
      return true;
    } catch {
      setError("Couldn't save the theme. Check your connection.");
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function importLink() {
    if (!url.trim()) return;
    setBusy(true);
    try {
      const res = await fetch(url.trim());
      if (!res.ok) throw new Error();
      if (await importJson(await res.text())) { setLinkOpen(false); setUrl(""); }
    } catch {
      setError("Couldn't download that link. Check the address and try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Section title="Theme">
      <ThemePreview />
      <div role="radiogroup" aria-label="Theme" className="flex flex-col">
        {options.map((o) => {
          const selected = o.id === selectedId;
          const imported = o.source === "Imported file";
          return (
            <div key={o.id} className="group flex items-center rounded-xl hover:bg-hover">
              <button type="button" role="radio" aria-checked={selected} onClick={() => onSelect(o.id === BUILTIN_AUTOMATIC ? null : o.id)}
                className="flex min-h-14 min-w-0 flex-1 cursor-pointer items-center gap-3 px-2 text-left">
                <Swatch colors={o.colors} />
                <span className="min-w-0 flex-1">
                  <span className={`block truncate text-body ${selected ? "font-bold" : ""}`}>{o.name}</span>
                  <span className="block text-meta text-muted">{o.source}</span>
                </span>
                {selected && <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>}
              </button>
              {imported && (
                <IconButton label={`Delete ${o.name}`} className="mr-1 opacity-0 focus-visible:opacity-100 group-hover:opacity-100 [@media(hover:none)]:opacity-100"
                  disabled={!online}
                  onClick={async () => {
                    try {
                      await deleteTheme(qc, o.id, activeId, userId);
                      toast({ message: `${o.name} deleted`, action: { label: "Undo", run: () => restoreRow(qc, "themes", o.id) } });
                    } catch { toast({ message: "Couldn't delete. Check your connection." }); }
                  }}>
                  {Icons.trash}
                </IconButton>
              )}
            </div>
          );
        })}
      </div>

      <input ref={fileRef} type="file" accept=".json,application/json" className="hidden"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) f.text().then(importJson); e.target.value = ""; }} />
      <div className="flex flex-wrap gap-2">
        <Button variant="primary" disabled={busy || !online} title={online ? undefined : OFFLINE_REASON} onClick={() => fileRef.current?.click()}>Import file</Button>
        <Button variant="secondary" disabled={busy || !online} title={online ? undefined : OFFLINE_REASON} onClick={() => { setError(null); setLinkOpen(true); }}>Import from link</Button>
      </div>
      <p className="text-meta text-muted">A theme is a JSON file of colours and corner rounding. Missing values fall back to Light.</p>
      {error && <p role="alert" className="text-meta text-danger">{error}</p>}

      <Dialog open={linkOpen} onClose={() => setLinkOpen(false)} title="Import from link">
        <form className="flex flex-col gap-5" onSubmit={(e) => { e.preventDefault(); importLink(); }}>
          <Field label="Link to a theme file" htmlFor="theme-url">
            <TextField id="theme-url" autoFocus type="url" placeholder="https://commit.yxuan.dev/themes/ocean.json" value={url} onChange={(e) => setUrl(e.target.value)} />
          </Field>
          {error && <p role="alert" className="text-meta text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button variant="quiet" onClick={() => setLinkOpen(false)}>Cancel</Button>
            <Button variant="primary" type="submit" disabled={busy || !url.trim()}>{busy ? "Importing…" : "Import"}</Button>
          </div>
        </form>
      </Dialog>
    </Section>
  );
}

/** 28 px swatch: the theme's surface with a 10 px dot of its text colour. */
function Swatch({ colors }: { colors: ThemeColors }) {
  return (
    <span aria-hidden className="flex size-7 shrink-0 items-center justify-center rounded-full border border-track" style={{ background: cssColor(colors.surface) }}>
      <span className="size-2.5 rounded-full" style={{ background: cssColor(colors.ink) }} />
    </span>
  );
}

/** The widget drawn small on a wallpaper colour, in the active theme's tokens. */
function ThemePreview() {
  return (
    <div aria-hidden className="flex h-44 items-center justify-center rounded-3xl bg-[linear-gradient(160deg,#7b8aa3,#3c4a5e)] p-6">
      <div className="flex w-60 flex-col gap-2.5 bg-surface p-4" style={{ borderRadius: "var(--radius-dialog)" }}>
        <div className="flex items-baseline gap-3">
          <span className="text-[1.375rem] font-light leading-none text-ink">Study</span>
          <span className="text-micro text-faint">Work</span>
          <span className="text-micro text-faint">Home</span>
        </div>
        {[["Late", "Problem set 3", true], ["18:00", "Read chapter 4", false], ["Fri", "Revise notes", false]].map(([due, title, late]) => (
          <div key={title as string} className="flex items-center gap-2 text-[0.6875rem]">
            <span className={`w-8 ${late ? "text-danger" : "text-muted"}`}>{due}</span>
            <span className="flex-1 truncate text-ink">{title}</span>
            <span className="size-3 rounded-full border border-ink" />
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------- Archived ----------------

function ArchivedSection() {
  const qc = useQueryClient();
  const toast = useToast();
  const online = useOnline();
  const commitmentsQ = useTable("commitments");
  const all = commitmentsQ.data ?? [];
  const archived = all.filter((c) => c.archived).sort((a, b) => b.updated_at - a.updated_at);
  const nextOrder = () => Math.max(-1, ...all.filter((c) => !c.archived).map((c) => c.sort_order)) + 1;

  return (
    <Section title="Commitments">
      <p className="text-caption text-muted">Archived</p>
      {archived.length === 0 ? <EmptyText>Nothing archived.</EmptyText> : (
        <ul className="flex flex-col">
          {archived.map((c) => (
            <li key={c.id} className="flex min-h-14 items-center gap-3">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-body">{c.name}</span>
                <span className="block text-meta text-muted">{c.type === "workout" ? "Workouts" : "Todos"}</span>
              </span>
              <Button variant="text" disabled={!online} title={online ? undefined : OFFLINE_REASON}
                onClick={() => restoreCommitment(qc, c.id, nextOrder()).catch(() => toast({ message: "Couldn't save. Check your connection." }))}>
                Restore
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
