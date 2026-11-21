"use client";

import { useMemo, type ReactNode } from "react";
import { useTable } from "@/lib/data/store";
import { resolveActiveTheme, themeCssVariables } from "@/lib/domain/theme";

export function ThemeProvider({ children }: { children: ReactNode }) {
  const settingsQ = useTable("settings");
  const themesQ = useTable("themes");

  const settings = settingsQ.data?.[0];
  const activeId = settings?.active_theme_id ?? null;

  const activeTheme = useMemo(() => {
    const themes = themesQ.data ?? [];
    return resolveActiveTheme(activeId, themes);
  }, [activeId, themesQ.data]);

  const css = useMemo(() => {
    return themeCssVariables(activeTheme);
  }, [activeTheme]);

  return (
    <>
      <style dangerouslySetInnerHTML={{ __html: css }} />
      {children}
    </>
  );
}
