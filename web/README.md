# Commit — web app (M8)

The PC side of Commit: the same commitments, todos, chores, routines and history as the Android app, with live updates when the phone changes something. Built with Next.js as a **static single-page app**: the browser talks to Supabase directly (row-level security limits every query to your own rows), so there is no server to run. Hosted for free on Cloudflare Pages.

## Run locally

```sh
cd web
npm install
npm run dev        # http://localhost:3000
```

`web/.env.local` (git-ignored) holds the two public Supabase values, the same as `supabase.url` / `supabase.anonKey` in `local.properties`:

```properties
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon / publishable key>
```

## Checks

```sh
npm test           # Vitest: the phone's rules ported to TypeScript, checked against the same cases
npm run typecheck
npm run lint
npm run build      # static site in web/out/
```

## One-time Supabase setup for the web app

1. **SQL Editor**: run `supabase/migrations/0003_web.sql` (Realtime on the synced tables + the `toggle_todo` function used for ticking todos).
2. **Authentication → URL Configuration**:
   - *Site URL*: your deployed address (e.g. `https://commit-xxx.pages.dev`), or `http://localhost:3000` until you deploy.
   - *Redirect URLs*: add `http://localhost:3000/**` and `https://<your-project>.pages.dev/**`.
   Google sign-in returns to these addresses; anything else is refused.

The Google provider and its OAuth web client are the ones already set up for the phone (see `app/SYNC_SETUP.md`); nothing changes in Google Cloud.

## Deploy to Cloudflare Pages (free)

```sh
cd web
npx wrangler login                                  # once; opens the browser
npm run build
npx wrangler pages deploy out --project-name commit # first run creates the project
```

Wrangler prints the `https://commit-….pages.dev` address. Add it to the Supabase redirect URLs (above) and use it as the Site URL. Later deploys: `npm run deploy`.

## How it works

| Part | Where |
| --- | --- |
| Rules ported from the phone (recurrence, due labels, stats, formats) | `lib/domain/` (tests in `domain.test.ts`) |
| Supabase client (Google sign-in, PKCE) | `lib/data/supabase.ts` |
| Per-table caches (React Query), writes with the phone's rules | `lib/data/store.ts`, `lib/data/mutations.ts` |
| Live updates from the phone (Realtime) | `lib/data/realtime.ts` |
| History joins | `lib/data/history.ts` |
| Pages | `app/` (`/`, `/workouts`, `/routine?id=`, `/history`, `/session?id=`, `/settings`) |

- Rows look exactly like the phone's: booleans are 0/1 and times are epoch milliseconds. The server stamps `user_id` and `updated_at`.
- Ticking a todo calls `toggle_todo`, which logs the completion and sets `done_at` or `last_done_at` in one transaction, as the phone does.
- Every web change fires the existing database webhook → `notify-sync` → FCM, so the phone syncs within about a minute.
