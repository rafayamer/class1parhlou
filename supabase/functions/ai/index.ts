// Supabase Edge Function: POST /functions/v1/ai
// Holds the OpenAI key (secret OPENAI_API_KEY) and calls gpt-6-luna. The phone never sees the key.
// Deployed with verify_jwt = false because the app has no sign-in yet; it protects itself with the app key
// check, a per-install daily limit and a project-wide daily cap.
import { createClient } from "jsr:@supabase/supabase-js@2";
import content from "./content/sample.json" with { type: "json" };
import { handle } from "./handler.ts";

const DAILY_LIMIT = Number(Deno.env.get("PARHLOU_DAILY_AI_CALLS") ?? "300");
// Hard ceiling for the whole project per day, so a leaked address cannot run up the OpenAI bill.
const DAILY_TOTAL = Number(Deno.env.get("PARHLOU_DAILY_TOTAL_AI_CALLS") ?? "2000");
const APP_KEY = Deno.env.get("PARHLOU_APP_KEY") ?? "";

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  if (!APP_KEY || request.headers.get("apikey") !== APP_KEY) return json({ error: "AUTH_REQUIRED" }, 401);
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) return json({ error: "AI_NOT_CONFIGURED" }, 503);

  // Until sign-in exists, callers are counted per install id. Replace with the signed-in user id.
  const caller = (request.headers.get("x-parhlou-install") ?? "").slice(0, 64);
  if (!/^[A-Za-z0-9-]{16,64}$/.test(caller)) return json({ error: "AUTH_REQUIRED" }, 401);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await db.from("ai_usage").select("id", { count: "exact", head: true }).eq("caller", caller).gte("created_at", since);

  const { count: total } = await db.from("ai_usage").select("id", { count: "exact", head: true }).gte("created_at", since);
  if ((total ?? 0) >= DAILY_TOTAL) return json({ error: "QUOTA_LIMIT" }, 429);

  let body: unknown;
  try { body = await request.json(); } catch { return json({ error: "BAD_REQUEST" }, 400); }

  const outcome = await handle(body, content as any, (url, init) => fetch(url, init), apiKey, DAILY_LIMIT - (count ?? 0));
  if (outcome.usage) {
    await db.from("ai_usage").insert({
      caller, task: outcome.usage.task, model: outcome.usage.model, prompt_version: outcome.usage.promptVersion,
      input_tokens: outcome.usage.inputTokens, output_tokens: outcome.usage.outputTokens,
      attempts: outcome.usage.attempts, ok: outcome.usage.ok,
    });
  }
  return json(outcome.body, outcome.status);
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
