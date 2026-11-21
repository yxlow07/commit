// notify-sync: wakes the user's phones after a change so they sync within a minute (PRD sync rule 7).
//
// Called by a Supabase Database Webhook on insert/update/delete of the synced tables (see
// app/SYNC_SETUP.md). Sends an FCM HTTP v1 *data* message {type: "sync"} to every device token of
// the row's owner; the app then runs a sync, redraws the widget and reschedules alarms.
//
// Secrets (supabase secrets set ... or Edge Functions → Secrets in the dashboard):
//   FIREBASE_SERVICE_ACCOUNT  JSON key of a Firebase service account (Cloud Messaging permission)
//   WEBHOOK_SECRET            shared secret, sent by the webhook in the x-webhook-secret header
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by the Edge runtime.

import { createClient } from "npm:@supabase/supabase-js@2.49.4";

type WebhookPayload = {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  record: Record<string, unknown> | null;
  old_record: Record<string, unknown> | null;
};

type ServiceAccount = { client_email: string; private_key: string; project_id: string };

const serviceAccount: ServiceAccount = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "{}");
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

Deno.serve(async (req) => {
  if (req.headers.get("x-webhook-secret") !== Deno.env.get("WEBHOOK_SECRET")) {
    return new Response("forbidden", { status: 403 });
  }
  const payload = (await req.json()) as WebhookPayload;
  if (payload.table === "device_tokens") return new Response("ignored");

  const userId = (payload.record?.user_id ?? payload.old_record?.user_id) as string | undefined;
  if (!userId) return new Response("no user");

  const { data: tokens, error } = await admin.from("device_tokens").select("token").eq("user_id", userId);
  if (error) return new Response(error.message, { status: 500 });
  if (!tokens?.length) return new Response("no devices");

  const accessToken = await googleAccessToken();
  const results = await Promise.all(tokens.map(({ token }) => sendSyncPing(token, accessToken)));
  return Response.json({ sent: results.filter(Boolean).length });
});

/** Sends one data message; removes tokens FCM reports as gone. */
async function sendSyncPing(token: string, accessToken: string): Promise<boolean> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        data: { type: "sync" },
        // Kept for a day: a phone that's off gets one "sync" ping when it comes back (collapse_key keeps one).
        android: { priority: "HIGH", ttl: "86400s", collapse_key: "sync" },
      },
    }),
  });
  if (res.status === 404 || res.status === 400) {
    const body = await res.text();
    if (body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT")) {
      await admin.from("device_tokens").delete().eq("token", token);
    }
    return false;
  }
  return res.ok;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

/** OAuth access token for FCM from the service account (JWT bearer grant, RS256 via Web Crypto). */
async function googleAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const jwt = `${unsigned}.${base64url(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token request failed: ${res.status} ${await res.text()}`);
  const json = await res.json();
  cachedToken = { value: json.access_token, expiresAt: now + json.expires_in };
  return cachedToken.value;
}

function base64url(input: string | Uint8Array): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const raw = atob(b64);
  const der = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) der[i] = raw.charCodeAt(i);
  return der.buffer;
}
