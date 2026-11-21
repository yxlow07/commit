"use client";

import { createContext, use, useContext, useEffect, useState, type ReactNode } from "react";
import { browser } from "react-dom";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Session, User } from "@supabase/supabase-js";
import { isConfigured, supabase } from "@/lib/data/supabase";
import { useRealtime } from "@/lib/data/realtime";
import { Button } from "./ui";

const UserContext = createContext<User | null>(null);

/** The signed-in user (always set inside the app shell). */
export function useUser(): User {
  const user = useContext(UserContext);
  if (!user) throw new Error("useUser outside the signed-in app");
  return user;
}

/**
 * Everything below runs only in the browser (the session lives in localStorage), so the static
 * build prerenders the Suspense fallback from the root layout instead.
 */
export function AppShell({ children }: { children: ReactNode }) {
  use(browser("The app reads the Supabase session from localStorage."));
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 60_000, refetchOnWindowFocus: true } } }),
  );
  const session = useSession();

  if (!isConfigured) return <Setup />;
  if (session === undefined) return <Splash />;
  if (session === null) return <SignIn />;
  return (
    <QueryClientProvider client={queryClient}>
      <UserContext value={session.user}>
        <SignedIn userId={session.user.id}>{children}</SignedIn>
      </UserContext>
    </QueryClientProvider>
  );
}

function useSession(): Session | null | undefined {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = supabase.auth.onAuthStateChange((_event, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);
  return session;
}

function SignedIn({ userId, children }: { userId: string; children: ReactNode }) {
  useRealtime(userId);
  return (
    <div className="min-h-dvh">
      <Nav />
      <main className="mx-auto max-w-6xl px-4 pb-16 pt-4 sm:px-6">{children}</main>
    </div>
  );
}

const LINKS = [
  { href: "/", label: "Todos" },
  { href: "/workouts", label: "Workouts" },
  { href: "/history", label: "History" },
  { href: "/settings", label: "Settings" },
];

function Nav() {
  const path = usePathname();
  const active = (href: string) =>
    href === "/" ? path === "/" : path.startsWith(href) || (href === "/workouts" && path.startsWith("/routine")) || (href === "/history" && path.startsWith("/session"));
  return (
    <header className="sticky top-0 z-10 border-b border-line bg-bg/90 backdrop-blur">
      <nav className="mx-auto flex max-w-6xl items-center gap-1 overflow-x-auto px-4 py-2 sm:px-6">
        <span className="mr-3 text-lg font-semibold">Commit</span>
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            aria-current={active(l.href) ? "page" : undefined}
            className={`rounded-full px-3 py-1.5 text-sm transition ${active(l.href) ? "bg-primary-soft font-medium text-fg" : "text-fg-muted hover:bg-surface-2 hover:text-fg"}`}
          >
            {l.label}
          </Link>
        ))}
      </nav>
    </header>
  );
}

export function Splash() {
  return <div className="flex min-h-dvh items-center justify-center text-sm text-fg-muted">Loading Commit…</div>;
}

/** F10.1: same Google account as the phone, through Supabase Auth (redirect flow). */
function SignIn() {
  const [error, setError] = useState<string | null>(null);
  async function signIn() {
    setError(null);
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: window.location.origin + window.location.pathname },
    });
    if (error) setError(error.message);
  }
  return (
    <div className="flex min-h-dvh items-center justify-center p-6">
      <div className="flex w-full max-w-sm flex-col items-center gap-4 rounded-3xl border border-line bg-surface p-8 text-center">
        <h1 className="text-2xl font-semibold">Commit</h1>
        <p className="text-sm text-fg-muted">Your todos, chores and workouts, in sync with the phone. Sign in with the same Google account you use in the app.</p>
        <Button variant="primary" onClick={signIn} className="w-full">Sign in with Google</Button>
        {error && <p role="alert" className="text-sm text-danger">{error}</p>}
      </div>
    </div>
  );
}

function Setup() {
  return (
    <div className="flex min-h-dvh items-center justify-center p-6 text-center text-sm text-fg-muted">
      Supabase isn&apos;t configured. Add NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY to web/.env.local (see web/README.md).
    </div>
  );
}
