import { createClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

export const isConfigured = url.length > 0 && anonKey.length > 0;

/**
 * Browser Supabase client. Google sign-in uses the PKCE redirect flow; the session is kept in
 * localStorage. Every query is limited to the user's own rows by row-level security.
 */
export const supabase = createClient(url || "http://localhost", anonKey || "missing", {
  auth: { flowType: "pkce", persistSession: true, detectSessionInUrl: true, autoRefreshToken: true },
});
