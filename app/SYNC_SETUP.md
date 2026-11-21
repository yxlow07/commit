# Sync setup (M7): Supabase + Google sign-in + Firebase push

Everything here fits in free tiers: Supabase Free, Google sign-in (free) and Firebase Cloud Messaging (free). The app only syncs when it's built with sync keys; without them, sync stays off and everything stays on the phone. Set this up once, then use **Settings → Account & sync → Sign in with Google**.

How sync works (hand-written, `sync/SyncEngine.kt`):

1. Every change on the phone marks the row `dirty`.
2. **Push**: dirty rows are upserted into Supabase through its REST API (PostgREST), then marked clean.
3. **Pull**: the phone fetches rows whose server `updated_at` is newer than its last sync and writes them into its database. The widget and alarms refresh.
4. Supabase stamps `updated_at` with its own clock, so the last write to reach the server wins. Phone and PC clocks never need to agree.
5. Sync runs after phone changes (within seconds when online), every 15 minutes, when the app starts, and when an FCM push says the web app changed something.

Files in this repo:

| File | What it is |
| --- | --- |
| `supabase/migrations/0001_init.sql` | Postgres tables (mirror the phone's tables), row-level security, `user_id` and `updated_at` triggers |
| `supabase/functions/notify-sync/index.ts` | Edge Function that sends the FCM "sync" ping after web edits |

---

## 1. Supabase project (free)

1. Create a project at supabase.com (Free plan). The database password isn't needed by the app.
2. Copy **Project URL** and the **anon / publishable key** (Project Settings → API).
3. Apply the schema: open **SQL Editor**, paste `supabase/migrations/0001_init.sql`, run it.
   (Or with the CLI: `supabase link --project-ref <ref>` then `supabase db push`.)

> Free projects pause after about a week without any requests. The app's 15-minute background sync keeps it active while you use the phone. A paused project can be resumed from the dashboard; the phone keeps working offline in the meantime and catches up afterwards.

## 2. Google sign-in (free)

1. In Google Cloud Console → **APIs & Services → Credentials** (set up the OAuth consent screen first if asked; "External", testing mode with your own account as a test user is enough):
   - Create an **OAuth client ID → Web application**. Copy its **client ID** and **secret**.
   - Create an **OAuth client ID → Android**: package `dev.yxuan.commit`, SHA-1 of your signing key. For debug builds run
     `./gradlew :app:signingReport` and use the `SHA1` under `Variant: debug`.
2. In Supabase → **Authentication → Providers → Google**: enable it and paste the **web** client ID and secret. Leave "Skip nonce check" **off** (the app sends a nonce).

## 3. Keys for the app (`local.properties`, never committed)

Add to `local.properties` in the project root:

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.anonKey=<anon / publishable key>
google.webClientId=<web OAuth client ID>.apps.googleusercontent.com
```

Rebuild (`./gradlew :app:installDebug`). All three keys must be set; if any is missing, sync stays off.

> `local.properties` also has a `SUPABASE_PASSWORD` entry. The app never reads it, and it isn't needed for this setup. You can delete it.

## 4. Push: wake the phone after web edits (optional, free)

Without push, web edits reach the phone the next time it syncs (app start, or within 15 minutes). With push, they arrive in about a minute.

1. Create a Firebase project (it can reuse the Google Cloud project from step 2), add an **Android app** with package `dev.yxuan.commit`, and download `google-services.json` into `app/`. The build applies the Google Services plugin automatically once the file exists.
2. Firebase → Project settings → **Service accounts → Generate new private key**. Keep this JSON secret and never commit it.
3. Deploy the function and set its secrets:

   ```sh
   supabase functions deploy notify-sync
   supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat path/to/service-account.json)"
   supabase secrets set WEBHOOK_SECRET="<long random string>"
   ```

4. Supabase → **Database → Webhooks → Create**: for each synced table (`commitments`, `todos`, `completions`, `routines`, `exercises`, `workout_sessions`, `workout_sets`, `themes`, `settings`), events **Insert, Update, Delete**, type **Supabase Edge Functions** → `notify-sync`, with the HTTP header `x-webhook-secret: <same secret>`.

## 5. Check it works

1. Phone: **Settings → Account & sync → Sign in with Google**. The status line should change to "Synced just now".
2. Supabase **Table editor**: your commitments, todos and workouts are there (the first sync uploads everything made before sync was set up).
3. Edit a todo title in the table editor. The phone updates within about a minute with push, or on the next app start or 15-minute sync without it.
4. Tick a todo on the widget. Within a few seconds the row's `done_at` is set in Supabase.
5. Airplane mode: make changes, then turn it off. They upload, and the status line shows "N changes to upload" until then.

## Notes and limits

- **Conflicts**: the last write to reach the server wins. Fine for one person on a phone and a PC.
- **Settings row**: on first sign-in the phone's settings become the user's settings row (`settings-<user id>`), replacing any row the web app made earlier.
- **Signing out** stops syncing and keeps the local data. Signing in later with a *different* Google account would upload this phone's data into that account too.
- **Echo pings**: the phone's own uploads also trigger the webhook, so it gets a ping and runs a quick sync that changes nothing. Harmless.
- **12-month local retention** (PRD) isn't implemented yet; local history is kept indefinitely for now.
