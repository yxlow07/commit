import { describe, expect, it } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";
import {
  BUILTIN_AUTOMATIC,
  BUILTIN_DARK,
  BUILTIN_LEGACY_DYNAMIC,
  BUILTIN_LIGHT,
  cssColor,
  parseTheme,
  resolveActiveTheme,
  THEME_DEFAULTS,
  themeCssVariables,
  webRadius,
  type ThemeSpec,
} from "./theme";
import type { Theme } from "./types";

/** The example files in themes/examples (the same files ThemeParserTest reads on the phone). */
function readExample(name: string): string {
  return readFileSync(resolve(process.cwd(), "../themes/examples", name), "utf-8");
}

function ok(json: string): ThemeSpec {
  const res = parseTheme(json);
  if (!res.ok) throw new Error(`expected ok, got ${res.errors.join("; ")}`);
  return res.spec;
}

function errors(json: string): string[] {
  const res = parseTheme(json);
  if (res.ok) throw new Error("expected errors");
  return res.errors;
}

describe("theme (ThemeParserTest)", () => {
  it("parses the ocean example", () => {
    const spec = ok(readExample("ocean.json"));
    expect(spec.name).toBe("Ocean");
    expect(spec.light.ink).toBe("#14283D");
    expect(spec.dark.ink).toBe("#E3ECF5");
    expect(spec.light.scrim).toBe(THEME_DEFAULTS.light.scrim);
    expect(spec.cornerRadius).toBe(32);
    expect(spec.font).toBe("rounded");
  });

  it("parses the forest example with defaults filled", () => {
    const spec = ok(readExample("forest.json"));
    expect(spec.light.ink).toBe("#1F2A1E");
    expect(spec.light.danger).toBe(THEME_DEFAULTS.light.danger);
    expect(spec.dark).toEqual(THEME_DEFAULTS.dark);
    expect(spec.cornerRadius).toBe(12);
    expect(spec.font).toBe("serif");
  });

  it("parses the high-contrast example", () => {
    const spec = ok(readExample("high-contrast.json"));
    expect(spec.name).toBe("High contrast");
    expect(spec.light.ink).toBe("#000000");
    expect(spec.cornerRadius).toBe(8);
    expect(spec.font).toBe("default");
  });

  it("maps a version 1 file to tokens", () => {
    const spec = ok(readExample("v1/ocean.json"));
    expect(spec.light.ink).toBe("#141C24");
    expect(spec.light.muted).toBe("#4A5866");
    expect(spec.light.fill).toBe("#E6EEF6");
    expect(spec.light.danger).toBe("#BA1A1A");
    expect(spec.light.onInk).toBe("#FFFFFF");
    expect(spec.dark.ink).toBe("#DDE5EE");
    expect(spec.cornerRadius).toBe(20);
  });

  it("the public copies match the examples", () => {
    for (const n of ["ocean.json", "forest.json", "high-contrast.json"]) {
      expect(readFileSync(resolve(process.cwd(), "public/themes", n), "utf-8")).toBe(readExample(n));
    }
  });

  it("rejects unsupported or missing schemaVersion", () => {
    expect(errors('{"schemaVersion": 3, "name": "X", "light": {}}')[0]).toContain("isn't supported");
    expect(errors('{"name": "X", "light": {}}')[0]).toContain("schemaVersion is missing");
  });

  it("name rules", () => {
    expect(errors('{"schemaVersion": 2, "light": {}}')).toContain("name is missing.");
    expect(errors('{"schemaVersion": 2, "name": "  ", "light": {}}')).toContain("name can't be empty.");
    expect(errors('{"schemaVersion": 2, "name": 5, "light": {}}')).toContain("name must be text.");
    expect(errors(`{"schemaVersion": 2, "name": "${"x".repeat(41)}", "light": {}}`)[0]).toContain("longer than 40");
  });

  it("no colours section", () => {
    expect(errors('{"schemaVersion": 2, "name": "X"}')).toEqual(["This file has no colours section (light or dark)."]);
  });

  it("invalid colours name the field", () => {
    const e = errors('{"schemaVersion": 2, "name": "X", "light": {"ink": "#12345", "muted": 7}, "dark": "blue"}');
    expect(e.some((m) => m.startsWith("light.ink:") && m.includes("is not a colour like #1F2123"))).toBe(true);
    expect(e.some((m) => m.startsWith("light.muted:"))).toBe(true);
    expect(e.some((m) => m.startsWith("dark must be an object"))).toBe(true);
  });

  it("radius rules", () => {
    expect(errors('{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": 41}}')[0]).toContain("0 to 40");
    expect(errors('{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": "8"}}')[0]).toContain("0 to 40");
    expect(ok('{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": 0}}').cornerRadius).toBe(0);
  });

  it("font rules", () => {
    expect(errors('{"schemaVersion": 2, "name": "X", "light": {}, "font": "comic"}')[0]).toContain("font must be one of");
  });

  it("refuses oversized files and malformed JSON", () => {
    expect(errors(`{"schemaVersion": 2, "name": "X", "light": {}, "pad": "${"x".repeat(33 * 1024)}"}`)[0]).toContain("larger than 32 KB");
    expect(errors("{not json")).toEqual(["This isn't valid JSON."]);
    expect(errors("[1, 2]")[0]).toContain("must be a JSON object");
  });

  it("resolves built-ins, the old Material You id, and imported themes", () => {
    expect(resolveActiveTheme(null, []).id).toBe(BUILTIN_AUTOMATIC);
    expect(resolveActiveTheme(BUILTIN_LEGACY_DYNAMIC, []).id).toBe(BUILTIN_AUTOMATIC);
    expect(resolveActiveTheme(BUILTIN_LIGHT, []).mode).toBe("light");
    expect(resolveActiveTheme(BUILTIN_DARK, []).mode).toBe("dark");
    expect(resolveActiveTheme("gone", []).id).toBe(BUILTIN_AUTOMATIC);
    const json = readExample("ocean.json");
    const t: Theme = { id: "t1", user_id: "u", updated_at: 0, deleted: 0, name: "Ocean", json };
    const active = resolveActiveTheme("t1", [t]);
    expect(active.name).toBe("Ocean");
    expect(active.mode).toBe("system");
  });

  it("writes CSS variables from the tokens", () => {
    const css = themeCssVariables(resolveActiveTheme(BUILTIN_LIGHT, []));
    expect(css).toContain("color-scheme: light;");
    expect(css).toContain("--ink: #1F2123;");
    expect(css).toContain("--on-ink: #FFFFFF;");
    expect(css).toContain("--danger: #B42318;");
    expect(css).toContain("--radius-dialog: 20px;");
    expect(css).not.toContain("--fg");
    expect(webRadius(28)).toBe(20);
  });

  it("writes #AARRGGBB theme colours as CSS #RRGGBBAA", () => {
    expect(cssColor("#801E5AA8")).toBe("#1E5AA880");
    expect(cssColor("#1E5AA8")).toBe("#1E5AA8");
  });
});
