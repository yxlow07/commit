import type { Theme } from "./types";

export type ThemeFont = "default" | "serif" | "mono" | "rounded";
export type ThemeMode = "system" | "light" | "dark";

export interface ThemeColors {
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;
  secondary: string;
  onSecondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  background: string;
  onBackground: string;
  surface: string;
  onSurface: string;
  surfaceVariant: string;
  onSurfaceVariant: string;
  outline: string;
  error: string;
  onError: string;
}

export const COLOR_KEYS: (keyof ThemeColors)[] = [
  "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
  "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
  "background", "onBackground", "surface", "onSurface",
  "surfaceVariant", "onSurfaceVariant", "outline", "error", "onError",
];

export interface ThemeSpec {
  name: string;
  light: ThemeColors;
  dark: ThemeColors;
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
  cornerRadius: 16,
  font: "default",
  light: {
    primary: "#4F5BD5", onPrimary: "#FFFFFF", primaryContainer: "#DFE2FF", onPrimaryContainer: "#0B1468",
    secondary: "#5C5D72", onSecondary: "#FFFFFF", secondaryContainer: "#E1E0F9", onSecondaryContainer: "#191A2C",
    background: "#F7F7FB", onBackground: "#1B1B21", surface: "#FFFFFF", onSurface: "#1B1B21",
    surfaceVariant: "#F0F0F6", onSurfaceVariant: "#5F5F6B", outline: "#E2E2EA",
    error: "#C62828", onError: "#FFFFFF",
  },
  dark: {
    primary: "#AAB3FF", onPrimary: "#141A5C", primaryContainer: "#343A73", onPrimaryContainer: "#DFE2FF",
    secondary: "#C5C4DD", onSecondary: "#2E2F42", secondaryContainer: "#444559", onSecondaryContainer: "#E1E0F9",
    background: "#121216", onBackground: "#E7E7EE", surface: "#1B1B21", onSurface: "#E7E7EE",
    surfaceVariant: "#26262E", onSurfaceVariant: "#A1A1AD", outline: "#2F2F39",
    error: "#FF8A80", onError: "#690005",
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

export const BUILTIN_DYNAMIC = "builtin:dynamic";
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

const SCHEMA_VERSION = 1;
const MAX_BYTES = 32 * 1024;
const MAX_NAME = 40;
const MAX_RADIUS = 32;
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
    return { ok: false, errors: ['A theme must be a JSON object, like { "schemaVersion": 1, "name": "Ocean" }.'] };
  }

  const obj = root as Record<string, unknown>;
  const errors: string[] = [];

  if (obj.schemaVersion === undefined) {
    errors.push(`schemaVersion is missing; this app reads version ${SCHEMA_VERSION}.`);
  } else if (obj.schemaVersion !== SCHEMA_VERSION) {
    errors.push(`schemaVersion ${obj.schemaVersion} isn't supported; this app reads version ${SCHEMA_VERSION}.`);
  }

  const rawName = obj.name;
  let name = "";
  if (rawName === undefined) {
    errors.push("name is missing.");
  } else if (typeof rawName !== "string") {
    errors.push("name must be text.");
  } else {
    name = rawName.trim();
    if (name.length === 0) {
      errors.push("name can't be empty.");
    } else if (name.length > MAX_NAME) {
      errors.push(`name is longer than ${MAX_NAME} characters.`);
    }
  }

  function parseColors(element: unknown, path: string, defaults: ThemeColors): ThemeColors {
    if (element === undefined || element === null) return { ...defaults };
    if (typeof element !== "object" || Array.isArray(element)) {
      errors.push(`${path} must be an object of colours, like { "primary": "#1E5AA8" }.`);
      return { ...defaults };
    }
    const colorsObj = element as Record<string, unknown>;
    const res: ThemeColors = { ...defaults };
    for (const key of COLOR_KEYS) {
      const val = colorsObj[key];
      if (val === undefined) continue;
      if (typeof val !== "string" || !COLOR_REGEX.test(val)) {
        errors.push(`${path}.${key}: ${JSON.stringify(val)} is not a colour like #1E5AA8.`);
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
      errors.push('shape must be an object, like { "cornerRadius": 16 }.');
    } else {
      const shapeObj = obj.shape as Record<string, unknown>;
      if (shapeObj.cornerRadius !== undefined) {
        const val = shapeObj.cornerRadius;
        if (typeof val !== "number" || isNaN(val) || val < 0 || val > MAX_RADIUS) {
          errors.push("shape.cornerRadius must be a number from 0 to 32.");
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

  if (errors.length > 0) {
    return { ok: false, errors };
  }

  return { ok: true, spec: { name, light, dark, cornerRadius, font } };
}

export function parseThemeOrNull(text: string): ThemeSpec | null {
  const res = parseTheme(text);
  return res.ok ? res.spec : null;
}

export function resolveActiveTheme(activeId: string | null, themes: Theme[]): ActiveTheme {
  if (!activeId || activeId === BUILTIN_DYNAMIC) {
    return {
      id: BUILTIN_DYNAMIC,
      name: "Material You",
      spec: THEME_DEFAULTS.spec("Material You"),
      mode: "system",
    };
  }
  if (activeId === BUILTIN_LIGHT) {
    return {
      id: BUILTIN_LIGHT,
      name: "Light",
      spec: THEME_DEFAULTS.spec("Light"),
      mode: "light",
    };
  }
  if (activeId === BUILTIN_DARK) {
    return {
      id: BUILTIN_DARK,
      name: "Dark",
      spec: THEME_DEFAULTS.spec("Dark"),
      mode: "dark",
    };
  }
  const found = themes.find((t) => t.id === activeId);
  if (found) {
    const spec = parseThemeOrNull(found.json);
    if (spec) {
      return {
        id: found.id,
        name: found.name,
        spec,
        mode: "system",
      };
    }
  }
  // Fall back to Dynamic / Default
  return {
    id: BUILTIN_DYNAMIC,
    name: "Material You",
    spec: THEME_DEFAULTS.spec("Material You"),
    mode: "system",
  };
}

/** Formats CSS variables for light and dark modes based on the active theme. */
/**
 * Theme colours are Android-style `#AARRGGBB`; CSS reads 8-digit hex as `#RRGGBBAA`, so move the alpha
 * to the end. `#RRGGBB` is unchanged.
 */
export function cssColor(color: string): string {
  return color.length === 9 ? `#${color.slice(3)}${color.slice(1, 3)}` : color;
}

export function themeCssVariables(theme: ActiveTheme): string {
  const { spec, mode } = theme;

  function toVars(c: ThemeColors): string {
    return `
      --bg: ${cssColor(c.background)};
      --surface: ${cssColor(c.surface)};
      --surface-2: ${cssColor(c.surfaceVariant)};
      --surface-3: ${cssColor(c.outline)};
      --line: ${cssColor(c.outline)};
      --fg: ${cssColor(c.onSurface)};
      --fg-muted: ${cssColor(c.onSurfaceVariant)};
      --primary: ${cssColor(c.primary)};
      --primary-soft: ${cssColor(c.primaryContainer)};
      --on-primary: ${cssColor(c.onPrimary)};
      --danger: ${cssColor(c.error)};
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
    --radius: ${spec.cornerRadius}px;
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

  // System mode
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
