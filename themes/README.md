# Commit themes

A theme is a small JSON file of design values: colours for light and dark, the corner rounding of the widget and dialogs, and a font family. It contains no code. The same file works in the Android app (app, quick-add popup and widget) and on the web app, and an imported theme syncs between them.

The colour keys are the design tokens from `app/App Design Spec.md`. The interface is greyscale by design; `danger` is the only accent and means overdue or late.

## Import a theme

- **Phone:** Settings → Themes → *Import file* (pick a `.json`) or *Import from link* (paste an `https://` address).
- **Web:** Settings → Theme → *Import from file* or *Import from link*.

Try the examples by link:

- `https://commit.yxuan.dev/themes/ocean.json`
- `https://commit.yxuan.dev/themes/forest.json`
- `https://commit.yxuan.dev/themes/high-contrast.json`

Built-in themes: **Automatic** (the default: Light or Dark following the system), **Light** and **Dark** (always light / always dark). Imported themes follow the system light/dark setting.

## Format (version 2)

```json
{
  "schemaVersion": 2,
  "name": "Ocean",
  "light": { "ink": "#14283D", "muted": "#556A7F", "...": "..." },
  "dark":  { "ink": "#E3ECF5", "...": "..." },
  "shape": { "cornerRadius": 32 },
  "font": "rounded"
}
```

| Field | Rule |
| --- | --- |
| `schemaVersion` | Required: `2` (version `1` files are still read, see below) |
| `name` | Required, 1–40 characters |
| `light`, `dark` | At least one of them; colour sets (keys below) |
| `shape.cornerRadius` | Optional, 0–40: the widget and dialog corners (dp on the phone, px on the web). Default 28 |
| `font` | Optional: `default` (Hanken Grotesk), `serif`, `mono` or `rounded` (Nunito). App only; the widget always uses the system font |

Colour keys (each `#RRGGBB` or `#AARRGGBB`):

| Key | Used for |
| --- | --- |
| `ink` | Primary text, selected tab, checkboxes, primary buttons |
| `onInk` | Text and icons on `ink` |
| `textStrong` | Summary sentences, empty-state text |
| `muted` | Secondary text, unselected tabs, due labels |
| `faint` | Chevrons, the current set segment (never text) |
| `track` | Future set segments, chart baseline |
| `fill` | Text fields, stepper buttons, secondary buttons |
| `surface` | Widget, dialogs, bottom navigation |
| `background` | App screen background |
| `danger` | Overdue and late labels only |
| `scrim` | Behind the quick-add popup (use alpha, e.g. `#731F2123`) |

- **Anything missing is filled from Light (in `light`) or Dark (in `dark`)**, so a theme can set just a few colours (see `examples/forest.json`, which has no `dark` set).
- Keep text colours (`ink`, `textStrong`, `muted`, `danger`) at 4.5:1 contrast or more against `surface` and `background`.
- Unknown keys are ignored. Files over 32 KB are refused.
- An invalid file is rejected with a message per problem, for example `light.ink: "#12345" is not a colour like #1F2123`, or `This file has no colours section (light or dark).`
- `theme.schema.json` is a JSON Schema for editors that support it.

### Version 1 files

Themes written for version 1 (Material keys: `primary`, `onSurface`, `surfaceVariant`, `error`…) still import. Their keys are mapped: `onSurface` → `ink` and `textStrong`, `surface` → `surface` and `onInk`, `onSurfaceVariant` → `muted`, `outline` → `faint` and `track`, `surfaceVariant` → `fill`, `background` → `background`, `error` → `danger`. Other version 1 keys are ignored. Example: `examples/v1/ocean.json`.

Both apps implement the same rules: `app/src/main/java/dev/yxuan/commit/domain/theme/ThemeSpec.kt` and `web/lib/domain/theme.ts`, tested against the files in `examples/`.
