import type { Theme } from "./types";

/**
 * Port of the phone's domain/theme/ThemeSpec.kt (F9). Theme format v2 uses the design spec's tokens
 * (app/App Design Spec.md); v1 files (Material keys) are still read and mapped. See themes/README.md.
 */

export type ThemeFont = "default" | "serif" | "mono" | "rounded";
export type ThemeMode = "system" | "light" | "dark";

export interface ThemeColors {
  ink: string;
  onInk: string;
  textStrong: string;
  muted: string;
  faint: string;
  track: string;
  fill: string;
  surface: string;
  background: string;
  danger: string;
  scrim: string;
}

export const COLOR_KEYS: (keyof ThemeColors)[] = [
  "ink", "onInk", "textStrong", "muted", "faint", "track", "fill", "surface", "background", "danger", "scrim",
];

/** v1 (Material) key → v2 token, so themes imported before v2 keep working. */
const V1_KEYS: Partial<Record<keyof ThemeColors, string>> = {
  ink: "onSurface", onInk: "surface", textStrong: "onSurface", muted: "onSurfaceVariant",
  faint: "outline", track: "outline", fill: "surfaceVariant", surface: "surface",
  background: "background", danger: "error",
};

export interface ThemeSpec {
  name: string;
  light: ThemeColors;
  dark: ThemeColors;
  /** Corner radius of the widget and dialogs (dp on the phone). */
  cornerRadius: number;
  font: ThemeFont;
}

export const THEME_DEFAULTS: {
  light: ThemeColors;
  dark: ThemeColors;
  cornerRadius: number;
  font: ThemeFont;
  spec: (name: string) => ThemeSpec;
} = {
  cornerRadius: 28,
  font: "default",
  light: {
    ink: "#1F2123", onInk: "#FFFFFF", textStrong: "#45474B", muted: "#6B6E73", faint: "#9C9FA4",
    track: "#E3E4E6", fill: "#EEEFF0", surface: "#FFFFFF", background: "#F6F6F5", danger: "#B42318", scrim: "#731F2123",
  },
  dark: {
    ink: "#ECEDEE", onInk: "#17181A", textStrong: "#C9CBCE", muted: "#A3A6AB", faint: "#6B6E73",
    track: "#2E3033", fill: "#2A2C2F", surface: "#17181A", background: "#0F1011", danger: "#F4877B", scrim: "#99000000",
  },
  spec(name: string): ThemeSpec {
    return {
      name,
      light: { ...THEME_DEFAULTS.light },
      dark: { ...THEME_DEFAULTS.dark },
      cornerRadius: THEME_DEFAULTS.cornerRadius,
      font: THEME_DEFAULTS.font,
    };
  },
};

export const BUILTIN_AUTOMATIC = "builtin:system";
/** Material You before the design spec; stored ids from then mean Automatic now. */
export const BUILTIN_LEGACY_DYNAMIC = "builtin:dynamic";
export const BUILTIN_LIGHT = "builtin:light";
export const BUILTIN_DARK = "builtin:dark";

export interface ActiveTheme {
  id: string;
  name: string;
  spec: ThemeSpec;
  mode: ThemeMode;
}

export type ThemeParseResult =
  | { ok: true; spec: ThemeSpec }
  | { ok: false; errors: string[] };

const SCHEMA_VERSION = 2;
const MAX_BYTES = 32 * 1024;
const MAX_NAME = 40;
const MAX_RADIUS = 40;
const COLOR_REGEX = /^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/;

export function parseTheme(text: string): ThemeParseResult {
  if (new TextEncoder().encode(text).length > MAX_BYTES) {
    return { ok: false, errors: ["The file is larger than 32 KB; a theme is a few hundred bytes."] };
  }

  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch {
    return { ok: false, errors: ["This isn't valid JSON."] };
  }

  if (!root || typeof root !== "object" || Array.isArray(root)) {
    return { ok: false, errors: ['A theme must be a JSON object, like { "schemaVersion": 2, "name": "Ocean" }.'] };
  }

  const obj = root as Record<string, unknown>;
  const errors: string[] = [];

  const version = obj.schemaVersion;
  if (version === undefined) {
    errors.push(`schemaVersion is missing; this app reads version ${SCHEMA_VERSION}.`);
  } else if (version !== 1 && version !== SCHEMA_VERSION) {
    errors.push(`schemaVersion ${JSON.stringify(version)} isn't supported; this app reads version ${SCHEMA_VERSION}.`);
  }
  const v1 = version === 1;

  const rawName = obj.name;
  let name = "";
  if (rawName === undefined) {
    errors.push("name is missing.");
  } else if (typeof rawName !== "string") {
    errors.push("name must be text.");
  } else {
    name = rawName.trim();
    if (name.length === 0) errors.push("name can't be empty.");
    else if (name.length > MAX_NAME) errors.push(`name is longer than ${MAX_NAME} characters.`);
  }

  if (obj.light === undefined && obj.dark === undefined) {
    errors.push("This file has no colours section (light or dark).");
  }

  function parseColors(element: unknown, path: string, defaults: ThemeColors): ThemeColors {
    if (element === undefined || element === null) return { ...defaults };
    if (typeof element !== "object" || Array.isArray(element)) {
      errors.push(`${path} must be an object of colours, like { "ink": "#1F2123" }.`);
      return { ...defaults };
    }
    const colorsObj = element as Record<string, unknown>;
    const res: ThemeColors = { ...defaults };
    for (const key of COLOR_KEYS) {
      const sourceKey = v1 ? V1_KEYS[key] : key;
      if (!sourceKey) continue;
      const val = colorsObj[sourceKey];
      if (val === undefined) continue;
      if (typeof val !== "string" || !COLOR_REGEX.test(val)) {
        errors.push(`${path}.${sourceKey}: ${JSON.stringify(val)} is not a colour like #1F2123.`);
      } else {
        res[key] = val;
      }
    }
    return res;
  }

  const light = parseColors(obj.light, "light", THEME_DEFAULTS.light);
  const dark = parseColors(obj.dark, "dark", THEME_DEFAULTS.dark);

  let cornerRadius = THEME_DEFAULTS.cornerRadius;
  if (obj.shape !== undefined) {
    if (typeof obj.shape !== "object" || obj.shape === null || Array.isArray(obj.shape)) {
      errors.push('shape must be an object, like { "cornerRadius": 28 }.');
    } else {
      const val = (obj.shape as Record<string, unknown>).cornerRadius;
      if (val !== undefined) {
        if (typeof val !== "number" || isNaN(val) || val < 0 || val > MAX_RADIUS) {
          errors.push("shape.cornerRadius must be a number from 0 to 40.");
        } else {
          cornerRadius = val;
        }
      }
    }
  }

  let font: ThemeFont = "default";
  if (obj.font !== undefined) {
    if (obj.font === "default" || obj.font === "serif" || obj.font === "mono" || obj.font === "rounded") {
      font = obj.font;
    } else {
      errors.push("font must be one of default, serif, mono or rounded.");
    }
  }

  if (errors.length > 0) return { ok: false, errors };
  return { ok: true, spec: { name, light, dark, cornerRadius, font } };
}

export function parseThemeOrNull(text: string): ThemeSpec | null {
  const res = parseTheme(text);
  return res.ok ? res.spec : null;
}

const AUTOMATIC: ActiveTheme = { id: BUILTIN_AUTOMATIC, name: "Automatic", spec: THEME_DEFAULTS.spec("Automatic"), mode: "system" };

export function resolveActiveTheme(activeId: string | null, themes: Theme[]): ActiveTheme {
  if (!activeId || activeId === BUILTIN_AUTOMATIC || activeId === BUILTIN_LEGACY_DYNAMIC) return AUTOMATIC;
  if (activeId === BUILTIN_LIGHT) return { id: BUILTIN_LIGHT, name: "Light", spec: THEME_DEFAULTS.spec("Light"), mode: "light" };
  if (activeId === BUILTIN_DARK) return { id: BUILTIN_DARK, name: "Dark", spec: THEME_DEFAULTS.spec("Dark"), mode: "dark" };
  const found = themes.find((t) => t.id === activeId);
  const spec = found ? parseThemeOrNull(found.json) : null;
  if (found && spec) return { id: found.id, name: found.name, spec, mode: "system" };
  return AUTOMATIC;
}

/**
 * Theme colours are Android-style `#AARRGGBB`; CSS reads 8-digit hex as `#RRGGBBAA`, so move the alpha
 * to the end. `#RRGGBB` is unchanged.
 */
export function cssColor(color: string): string {
  return color.length === 9 ? `#${color.slice(3)}${color.slice(1, 3)}` : color;
}

/**
 * The web's component radius for a theme. `cornerRadius` is the phone's widget/dialog radius (28 by
 * default); web cards and fields use about 4/7 of it, which keeps the default at the web's 16 px.
 */
export function webRadius(cornerRadius: number): number {
  return Math.round((cornerRadius * 4) / 7);
}

/** Formats CSS variables for light and dark modes based on the active theme. */
export function themeCssVariables(theme: ActiveTheme): string {
  const { spec, mode } = theme;

  function toVars(c: ThemeColors): string {
    return `
      --bg: ${cssColor(c.background)};
      --surface: ${cssColor(c.surface)};
      --surface-2: ${cssColor(c.fill)};
      --surface-3: ${cssColor(c.track)};
      --line: ${cssColor(c.track)};
      --fg: ${cssColor(c.ink)};
      --fg-muted: ${cssColor(c.muted)};
      --primary: ${cssColor(c.ink)};
      --primary-soft: ${cssColor(c.fill)};
      --on-primary: ${cssColor(c.onInk)};
      --danger: ${cssColor(c.danger)};
    `.trim();
  }

  const fontVal =
    spec.font === "serif"
      ? "serif"
      : spec.font === "mono"
      ? "monospace"
      : spec.font === "rounded"
      ? "var(--font-nunito), system-ui, sans-serif"
      : "var(--font-geist-sans), system-ui, sans-serif";

  const commonVars = `
    --radius: ${webRadius(spec.cornerRadius)}px;
    --font-theme: ${fontVal};
  `.trim();

  if (mode === "light") {
    return `
      :root {
        color-scheme: light;
        ${commonVars}
        ${toVars(spec.light)}
      }
    `;
  }

  if (mode === "dark") {
    return `
      :root {
        color-scheme: dark;
        ${commonVars}
        ${toVars(spec.dark)}
      }
    `;
  }

  return `
    :root {
      color-scheme: light dark;
      ${commonVars}
      ${toVars(spec.light)}
    }
    @media (prefers-color-scheme: dark) {
      :root {
        ${toVars(spec.dark)}
      }
    }
  `;
}
