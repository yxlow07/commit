"use client";

import { createContext, use, useContext, useEffect, useState, type ReactNode } from "react";
import { browser } from "react-dom";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Session, User } from "@supabase/supabase-js";
import { isConfigured, supabase } from "@/lib/data/supabase";
import { useRealtime } from "@/lib/data/realtime";
import { EditPanel, PanelProvider, usePanel } from "./EditPanel";
import { OfflineLine, OnlineProvider } from "./Online";
import { Shortcuts } from "./Shortcuts";
import { PAGES, Sidebar, usePageActive } from "./Sidebar";
import { ThemeProvider } from "./ThemeProvider";
import { ToastProvider } from "./Toast";
import { Button, IconButton, Icons, Logo, RoundAdd } from "./ui";

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
 * The shell (web spec, "App shell"): a sidebar and one main column, with the edit panel beside
 * the list. Wide (1200+): the panel pushes the column; desktop (900+): it slides over; narrow
 * (600+): a top bar and a drawer; phone: the phone app's bottom nav.
 */
function SignedIn({ userId, children }: { userId: string; children: ReactNode }) {
  useRealtime(userId);
  const panel = usePanel();
  const pathname = usePathname();
  const [title, setTitle] = useState("");
  // The drawer belongs to the page it was opened on, so navigating closes it.
  const [drawerPath, setDrawerPath] = useState<string | null>(null);
  const drawer = drawerPath === pathname;
  const setDrawer = (open: boolean) => setDrawerPath(open ? pathname : null);

  return (
    <TitleContext value={setTitle}>
      {/* React hoists <title> into <head>; the layout's metadata has none, so this is the only one. */}
      <title>{title ? `${title} – Commit` : "Commit"}</title>
      <div className="min-h-dvh">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-surface focus:p-3">Skip to content</a>

        <div className="fixed inset-y-0 left-0 hidden w-60 overflow-y-auto md:block lg:w-[260px]">
          <Sidebar />
        </div>

        {/* Narrow: top bar, the sidebar as a drawer over the scrim. */}
        <header className="sticky top-0 z-20 hidden h-14 items-center gap-2 bg-background/95 px-3 backdrop-blur sm:flex md:hidden">
          <IconButton label="Menu" aria-expanded={drawer} onClick={() => setDrawer(true)}>{Icons.menu}</IconButton>
          <span className="min-w-0 flex-1 truncate text-label font-bold">{title}</span>
          <RoundAdd label="Add todo" className="size-10" onClick={() => panel.openNew()} />
        </header>
        {drawer && (
          <div className="fixed inset-0 z-40 md:hidden" onKeyDown={(e) => e.key === "Escape" && setDrawer(false)}>
            <div className="fade-in absolute inset-0 bg-scrim" onClick={() => setDrawer(false)} />
            <div className="fade-in absolute inset-y-0 left-0 w-[280px] overflow-y-auto bg-surface">
              <Sidebar />
            </div>
          </div>
        )}

        <main id="main" className={`pb-28 sm:pb-16 md:pl-60 lg:pl-[260px] ${panel.current ? "lg:pr-[380px]" : ""}`}>
          <div className="mx-auto max-w-[720px] px-6 pt-12 sm:px-8 sm:pt-8 md:pt-12">
            <OfflineLine />
            {children}
          </div>
        </main>

        <BottomNav />
        <EditPanel />
        <Shortcuts />
      </div>
    </TitleContext>
  );
}

/** Phone: Today, History, Settings as text, as in the app. */
function BottomNav() {
  const active = usePageActive();
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-20 grid h-[72px] grid-cols-3 bg-surface sm:hidden">
      {PAGES.map((p) => (
        <Link key={p.href} href={p.href} aria-current={active(p.href) ? "page" : undefined}
          className={`flex items-center justify-center text-[0.875rem] ${active(p.href) ? "font-bold text-ink" : "text-muted"}`}>
          {p.label}
        </Link>
      ))}
    </nav>
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
