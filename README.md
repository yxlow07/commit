# Commit

<img src="app/icon/play-store-icon-512.png" alt="" width="96" align="right">

Your todos, chores and workouts on an Android home-screen widget, synced with a web app for the PC.

Commit groups everything into **commitments** (Study, Work, Gym…). The widget shows one commitment per tab, so you can tick things off, add todos and run a whole workout without opening the app. The web app at **[commit.yxuan.dev](https://commit.yxuan.dev)** shows the same data and updates live when the phone changes something.

## Download

Get the latest APK from **[Releases](https://github.com/yxlow07/commit/releases)** and open it on your phone. Android will ask you to allow installs from that source.

- Needs **Android 12** or later.
- The app works fully offline. Sign in with Google under **Settings → Account** to sync with the web app.

## Features

**Widget**
- One tab per commitment; switch tabs, tick todos and add new ones (a small popup over the home screen) without opening the app.
- Tap a todo to edit it in the same popup.
- See-through panel that follows your theme, light or dark.

**Todos and chores**
- Due dates and times, reminders (at the time or a lead time before), and a red **Late** label when overdue.
- Repeating chores: every N days, weeks, months or years after you finish them, or on chosen weekdays (swipe across the days to pick several).
- Commitments in your own order (drag the handle), each with a colour, archived and restored from Settings.

**Workouts**
- Routines (Push Day, Pull Day, Leg Day…) inside a workout commitment, with exercises as sets × reps × weight, or timed (a plank).
- Run a workout from the widget or the app: adjust reps and weight with − / +, tap **Done set**, and a rest timer starts.
- A big per-set timer while you lift. When a rest runs out the phone alerts you and the timer turns red and counts up until you tap **Next set** (or **Next exercise** after the last set).

**History**
- Heatmap of completions over 12 weeks, the day-by-day timeline, streaks for chores, and every workout session with its sets (editable afterwards).

**Daily recaps**
- A morning notification with today's plan and a night one with what you finished and what's due tomorrow, at times you choose.

**Themes**
- Automatic, Light and Dark built in, or import your own theme file (a JSON file of colours and corner rounding; see [`themes/`](themes/README.md)).

## Repository

| Path | What it is |
| --- | --- |
| [`app/`](app/) | Android app: Kotlin, Jetpack Compose, Room, Glance (widget), WorkManager, AlarmManager |
| [`web/`](web/README.md) | Web app: Next.js static site, Tailwind, TanStack Query, Supabase in the browser; hosted on Cloudflare Pages |
| [`supabase/`](supabase/) | Postgres migrations (tables, row-level security) and the `notify-sync` Edge Function |
| [`themes/`](themes/README.md) | Theme file format, JSON schema and examples |
| [`app/PRD.md`](app/PRD.md) | Product requirements |
| [`app/App Design Spec.md`](app/App%20Design%20Spec.md) | Visual design rules (tokens, type, components) |
| [`app/SYNC_SETUP.md`](app/SYNC_SETUP.md) | Setting up Supabase, Google sign-in and Firebase push for sync |

## Building the Android app

Open the project in Android Studio (or use `./gradlew`), with the Android SDK for API 37.

```sh
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:testDebugUnitTest  # unit tests
```

Without any keys the app builds and runs with sync turned off. To build with sync, add the values from [`app/SYNC_SETUP.md`](app/SYNC_SETUP.md) to `local.properties` in the project root (git-ignored):

```properties
supabase.url=https://<project-ref>.supabase.co
supabase.anonKey=<anon / publishable key>
google.webClientId=<web OAuth client ID>.apps.googleusercontent.com
```

### Release builds

Release APKs are signed with a keystore kept outside the repo. Point `local.properties` at it:

```properties
release.storeFile=/path/to/commit-release.jks
release.storePassword=<password>
release.keyAlias=commit
release.keyPassword=<password>
```

```sh
./gradlew :app:assembleRelease    # signed APK in app/build/outputs/apk/release/
```

Before each release, raise `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts). Google sign-in in a release build needs the keystore's SHA-1 fingerprint registered on the Android OAuth client in Google Cloud Console.

## Running the web app

```sh
cd web
npm install
npm run dev     # http://localhost:3000
```

`web/.env.local` holds the public Supabase URL and anon key. Tests, checks and deployment are described in [`web/README.md`](web/README.md).

## Sync, in short

Every change on the phone is marked dirty and pushed to Supabase within seconds, and the phone pulls anything newer. Supabase stamps each row with its own clock, so the last write to reach the server wins. Row-level security limits every query to your own rows, so the web app talks to Supabase directly with no server of its own. When the web app changes something, an Edge Function sends the phone a Firebase push so it syncs straight away.
