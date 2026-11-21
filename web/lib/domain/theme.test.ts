import { describe, expect, it } from "vitest";
import { readFileSync } from "fs";
import { resolve } from "path";
import {
  BUILTIN_DARK,
  BUILTIN_DYNAMIC,
  BUILTIN_LIGHT,
  parseTheme,
  resolveActiveTheme,
  THEME_DEFAULTS,
  themeCssVariables,
  cssColor,
} from "./theme";

function readExample(name: string): string {
  const p1 = resolve(process.cwd(), "public/themes", name);
  try {
    return readFileSync(p1, "utf-8");
  } catch {
    const p2 = resolve(process.cwd(), "../themes/examples", name);
    return readFileSync(p2, "utf-8");
  }
}

describe("theme", () => {
  it("parses valid ocean.json example", () => {
    const json = readExample("ocean.json");
    const res = parseTheme(json);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    expect(res.spec.name).toBe("Ocean");
    expect(res.spec.light.primary).toBe("#1E5AA8");
    expect(res.spec.dark.primary).toBe("#A4C8FF");
    expect(res.spec.cornerRadius).toBe(20);
    expect(res.spec.font).toBe("rounded");
  });

  it("parses valid forest.json example with defaults filled", () => {
    const json = readExample("forest.json");
    const res = parseTheme(json);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    expect(res.spec.name).toBe("Forest");
    expect(res.spec.light.primary).toBe("#2E6B30");
    // dark set was missing in forest.json, so it gets filled from defaults
    expect(res.spec.dark.primary).toBe(THEME_DEFAULTS.dark.primary);
    expect(res.spec.dark.background).toBe(THEME_DEFAULTS.dark.background);
    // missing light.error is filled
    expect(res.spec.light.error).toBe(THEME_DEFAULTS.light.error);
    expect(res.spec.cornerRadius).toBe(8);
    expect(res.spec.font).toBe("serif");
  });

  it("parses valid high-contrast.json example", () => {
    const json = readExample("high-contrast.json");
    const res = parseTheme(json);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    expect(res.spec.name).toBe("High contrast");
    expect(res.spec.cornerRadius).toBe(4);
    expect(res.spec.font).toBe("default");
  });

  it("rejects unsupported or missing schemaVersion", () => {
    const r1 = parseTheme(JSON.stringify({ schemaVersion: 2, name: "Test" }));
    expect(r1.ok).toBe(false);
    if (!r1.ok) {
      expect(r1.errors.some((e) => e.includes("schemaVersion 2 isn't supported"))).toBe(true);
    }

    const r2 = parseTheme(JSON.stringify({ name: "Test" }));
    expect(r2.ok).toBe(false);
    if (!r2.ok) {
      expect(r2.errors.some((e) => e.includes("schemaVersion is missing"))).toBe(true);
    }
  });

  it("rejects invalid name", () => {
    const r1 = parseTheme(JSON.stringify({ schemaVersion: 1, name: "  " }));
    expect(r1.ok).toBe(false);
    if (!r1.ok) {
      expect(r1.errors.some((e) => e.includes("name can't be empty"))).toBe(true);
    }

    const r2 = parseTheme(JSON.stringify({ schemaVersion: 1, name: "a".repeat(41) }));
    expect(r2.ok).toBe(false);
    if (!r2.ok) {
      expect(r2.errors.some((e) => e.includes("name is longer than 40 characters"))).toBe(true);
    }
  });

  it("rejects invalid colours", () => {
    const r1 = parseTheme(
      JSON.stringify({
        schemaVersion: 1,
        name: "Bad",
        light: { primary: "#12345" },
      })
    );
    expect(r1.ok).toBe(false);
    if (!r1.ok) {
      expect(r1.errors.some((e) => e.includes('light.primary: "#12345" is not a colour like #1E5AA8.'))).toBe(true);
    }
  });

  it("rejects invalid radius and font", () => {
    const r1 = parseTheme(
      JSON.stringify({
        schemaVersion: 1,
        name: "Bad",
        shape: { cornerRadius: 40 },
        font: "comic-sans",
      })
    );
    expect(r1.ok).toBe(false);
    if (!r1.ok) {
      expect(r1.errors.some((e) => e.includes("shape.cornerRadius must be a number from 0 to 32"))).toBe(true);
      expect(r1.errors.some((e) => e.includes("font must be one of default, serif, mono or rounded"))).toBe(true);
    }
  });

  it("rejects malformed json and oversized text", () => {
    const r1 = parseTheme("not json");
    expect(r1.ok).toBe(false);
    if (!r1.ok) expect(r1.errors[0]).toContain("isn't valid JSON");

    const r2 = parseTheme(JSON.stringify({ schemaVersion: 1, name: "Big", extra: "x".repeat(33 * 1024) }));
    expect(r2.ok).toBe(false);
    if (!r2.ok) expect(r2.errors[0]).toContain("larger than 32 KB");
  });

  it("resolves active themes", () => {
    expect(resolveActiveTheme(null, []).id).toBe(BUILTIN_DYNAMIC);
    expect(resolveActiveTheme(BUILTIN_DYNAMIC, []).id).toBe(BUILTIN_DYNAMIC);
    expect(resolveActiveTheme(BUILTIN_LIGHT, []).mode).toBe("light");
    expect(resolveActiveTheme(BUILTIN_DARK, []).mode).toBe("dark");

    const customTheme = {
      id: "theme-1",
      user_id: "user-1",
      updated_at: 100,
      deleted: 0 as const,
      name: "Ocean",
      json: readExample("ocean.json"),
    };
    const resolved = resolveActiveTheme("theme-1", [customTheme]);
    expect(resolved.id).toBe("theme-1");
    expect(resolved.spec.name).toBe("Ocean");
    expect(resolved.mode).toBe("system");

    // Missing custom theme falls back to dynamic
    expect(resolveActiveTheme("missing", [customTheme]).id).toBe(BUILTIN_DYNAMIC);
  });

  it("generates CSS variables", () => {
    const theme = resolveActiveTheme(BUILTIN_LIGHT, []);
    const css = themeCssVariables(theme);
    expect(css).toContain("color-scheme: light;");
    expect(css).toContain("--primary: #4F5BD5;");
    expect(css).toContain("--radius: 16px;");
  });
  it("writes #AARRGGBB theme colours as CSS #RRGGBBAA", () => {
    expect(cssColor("#801E5AA8")).toBe("#1E5AA880");
    expect(cssColor("#1E5AA8")).toBe("#1E5AA8");
  });
});
