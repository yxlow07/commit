import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { demoClient } from "./demo";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

/** Local preview with sample data and no sign-in (`NEXT_PUBLIC_DEMO=1 npx next dev`); never set for a deploy. */
export const isDemo = process.env.NEXT_PUBLIC_DEMO === "1";

export const isConfigured = isDemo || (url.length > 0 && anonKey.length > 0);

/**
 * Browser Supabase client. Google sign-in uses the PKCE redirect flow; the session is kept in
 * localStorage. Every query is limited to the user's own rows by row-level security.
 */
export const supabase: SupabaseClient = isDemo
  ? (demoClient() as unknown as SupabaseClient)
  : createClient(url || "http://localhost", anonKey || "missing", {
      auth: { flowType: "pkce", persistSession: true, detectSessionInUrl: true, autoRefreshToken: true },
    });
