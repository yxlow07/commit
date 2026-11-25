"use client";

import { createContext, use, useContext, useEffect, useState, type ReactNode } from "react";
import { browser } from "react-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Session, User } from "@supabase/supabase-js";
import { isConfigured, supabase } from "@/lib/data/supabase";
import { useRealtime } from "@/lib/data/realtime";
import { EditPanel, PanelProvider, usePanel } from "./EditPanel";
import { OfflineLine, OnlineProvider } from "./Online";
import { Shortcuts } from "./Shortcuts";
import { BottomNav, TopNav } from "./Nav";
import { ThemeProvider } from "./ThemeProvider";
import { ToastProvider } from "./Toast";
import { Button, Logo } from "./ui";

const UserContext = createContext<User | null>(null);

/** The signed-in user (always set inside the app shell). */
export function useUser(): User {
  const user = useContext(UserContext);
  if (!user) throw new Error("useUser outside the signed-in app");
  return user;
}

/** Page title for the narrow top bar and the browser tab. */
const TitleContext = createContext<(title: string) => void>(() => {});
export function usePageTitle(title: string) {
  const set = useContext(TitleContext);
  useEffect(() => set(title), [set, title]);
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
        <ThemeProvider>
          <ToastProvider>
            <OnlineProvider>
              <PanelProvider>
                <SignedIn userId={session.user.id}>{children}</SignedIn>
              </PanelProvider>
            </OnlineProvider>
          </ToastProvider>
        </ThemeProvider>
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

/**
 * The shell, laid out like the app: one left-aligned column (680 px max) centred on the page, the
 * app's four tabs above it (600 px and up) or as its bottom bar (phone). The edit panel opens
 * beside the column: it pushes the column on wide screens and slides over it below 1200 px.
 */
function SignedIn({ userId, children }: { userId: string; children: ReactNode }) {
  useRealtime(userId);
  const panel = usePanel();
  const [title, setTitle] = useState("");

  return (
    <TitleContext value={setTitle}>
      {/* React hoists <title> into <head>; the layout's metadata has none, so this is the only one. */}
      <title>{title ? `${title} – Commit` : "Commit"}</title>
      <div className="min-h-dvh">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-surface focus:p-3">Skip to content</a>
        <div className={`transition-[padding] duration-200 ${panel.current ? "lg:pr-[400px]" : ""}`}>
          <div className="mx-auto w-full max-w-[728px] px-6 sm:px-8">
            <TopNav />
            <main id="main" className="pb-32 pt-12 sm:pb-24 sm:pt-10">
              <OfflineLine />
              {children}
            </main>
          </div>
        </div>
        <BottomNav />
        <EditPanel />
        <Shortcuts />
      </div>
    </TitleContext>
  );
}

export function Splash() {
  return <><title>Commit</title><div className="flex min-h-dvh items-center justify-center text-meta text-muted">Loading…</div></>;
}

/** Sign in (web spec, "Sign in"): same Google account as the phone, through Supabase Auth. */
function SignIn() {
  const [error, setError] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    const q = new URLSearchParams(window.location.search + "&" + window.location.hash.slice(1));
    const e = q.get("error");
    if (!e) return null;
    return e === "access_denied" ? "Sign-in was cancelled." : "Couldn't reach Google. Try again.";
  });
  async function signIn() {
    setError(null);
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: window.location.origin + window.location.pathname + window.location.search },
    });
    if (error) setError("Couldn't reach Google. Try again.");
  }
  return (
    <main className="flex min-h-dvh items-center justify-center p-6">
      <title>Sign in – Commit</title>
      <div className="flex w-full max-w-[360px] flex-col items-center gap-4 text-center">
        <Logo size={64} />
        <h1 className="text-title-screen">Commit</h1>
        <p className="text-body text-muted">Your commitments, on your phone and here.</p>
        <Button variant="primary" onClick={signIn} className="mt-2 w-full">Continue with Google</Button>
        {error && <p role="alert" className="text-meta text-danger">{error}</p>}
      </div>
    </main>
  );
}

function Setup() {
  return (
    <div className="flex min-h-dvh items-center justify-center p-6 text-center text-meta text-muted">
      Supabase isn&apos;t configured. Add NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY to web/.env.local (see web/README.md).
    </div>
  );
}
