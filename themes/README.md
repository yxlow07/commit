# Commit themes

A theme is a small JSON file of design values: colours for light and dark, a corner radius and a font family. It contains no code. The same file works in the Android app (app, quick-add popup and widget) and on the web app, and an imported theme syncs between them.

## Import a theme

- **Phone:** Settings → Theme → *Import from file* (pick a `.json`) or *Import from link* (paste an `https://` address).
- **Web:** Settings → Theme → *Import from file* or *Import from link*.

Try the examples by link:

- `https://commit.yxuan.dev/themes/ocean.json`
- `https://commit.yxuan.dev/themes/forest.json`
- `https://commit.yxuan.dev/themes/high-contrast.json`

Built-in themes: **Material You** (wallpaper colours on the phone; the default palette on the web), **Light** and **Dark** (always light / always dark). Imported themes follow the system light/dark setting.

## Format (version 1)

```json
{
  "schemaVersion": 1,
  "name": "Ocean",
  "light": { "primary": "#1E5AA8", "onPrimary": "#FFFFFF", "...": "..." },
  "dark":  { "primary": "#A4C8FF", "...": "..." },
  "shape": { "cornerRadius": 20 },
  "font": "rounded"
}
```

| Field | Rule |
| --- | --- |
| `schemaVersion` | Required, must be `1` |
| `name` | Required, 1–40 characters |
| `light`, `dark` | Optional colour sets (keys below) |
| `shape.cornerRadius` | Optional, 0–32 (dp on the phone, px on the web) |
| `font` | Optional: `default`, `serif`, `mono` or `rounded` (Nunito) |

Colour keys (each `#RRGGBB` or `#AARRGGBB`): `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `error`, `onError`.

- **Anything missing is filled from the default theme**, so a theme can set just a few colours (see `examples/forest.json`, which has no `dark` set).
- Unknown keys are ignored. Files over 32 KB are refused.
- An invalid file is rejected with a message per problem, for example `light.primary: "#12345" is not a colour like #1E5AA8`.
- `theme.schema.json` is a JSON Schema for editors that support it.

Both apps implement the same rules: `app/src/main/java/dev/yxuan/commit/domain/theme/ThemeSpec.kt` and `web/lib/domain/theme.ts`, tested against the files in `examples/`.
