// Supabase Edge Function: POST /functions/v1/ai
// Holds the OpenAI key (secret OPENAI_API_KEY) and calls gpt-6-luna. The phone never sees the key.
// Deployed with verify_jwt = false so older builds without sign-in keep working; it checks the app key itself,
// verifies a student's session token when one is sent, and applies per-student and project-wide daily limits.
// Content (questions, keys, rubrics, notes) and limits are read from the database (app_content, app_config),
// so they can change without redeploying. The bundled sample is only a fallback.
import { createClient } from "jsr:@supabase/supabase-js@2";
import bundled from "./content/sample.json" with { type: "json" };
import { handle, markMcqs, markRecord, type Content } from "./handler.ts";
import { searchVideos } from "./youtube.ts";
import type { Video } from "./core.ts";

const APP_KEY = Deno.env.get("PARHLOU_APP_KEY") ?? "";
const CACHE_MS = 30_000;

interface Live { at: number; contents: Content[]; enabled: boolean; perInstall: number; total: number; photosPerDay: number }
let cache: Live | null = null;

async function live(db: any): Promise<Live> {
  if (cache && Date.now() - cache.at < CACHE_MS) return cache;
  const [{ data: rows }, { data: config }] = await Promise.all([
    db.from("app_content").select("bundle").eq("status", "PUBLISHED"),
    db.from("app_config").select("key,value"),
  ]);
  const setting = (key: string, fallback: unknown) => (config ?? []).find((c: any) => c.key === key)?.value ?? fallback;
  const contents = (rows ?? []).map((r: any) => r.bundle as Content).filter((b: any) => b?.subjectId && Array.isArray(b.written));
  cache = {
    at: Date.now(),
    contents: contents.length ? contents : [bundled as unknown as Content],
    enabled: setting("ai_enabled", true) === true,
    perInstall: Number(setting("daily_calls_per_install", 300)),
    total: Number(setting("daily_calls_total", 2000)),
    photosPerDay: Number(setting("daily_photo_questions", 20)),
  };
  return cache;
}

/** Picks the subject bundle a request is about: by question id, or by the learner's subjects for the tutor. */
function pick(contents: Content[], body: any): Content {
  const id = body?.questionId;
  if (id) {
    const hit = contents.find((c) => c.written.some((w: any) => w.id === id) || c.mcqs.some((m: any) => m.id === id));
    if (hit) return hit;
  }
  const subjects: string[] = Array.isArray(body?.subjectIds) ? body.subjectIds : [];
  return contents.find((c) => subjects.includes(c.subjectId)) ?? contents[0];
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  if (!APP_KEY || request.headers.get("apikey") !== APP_KEY) return json({ error: "AUTH_REQUIRED" }, 401);
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) return json({ error: "AI_NOT_CONFIGURED" }, 503);

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  // Signed-in students send their session token; limits and marks are then per student.
  // Older builds without sign-in send an install id instead.
  let userId: string | null = null;
  const bearer = (request.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (bearer && bearer !== APP_KEY && bearer.split(".").length === 3) {
    const { data, error } = await db.auth.getUser(bearer);
    if (error || !data?.user) return json({ error: "SESSION_EXPIRED" }, 401);
    userId = data.user.id;
  }
  const caller = userId ?? (request.headers.get("x-parhlou-install") ?? "").slice(0, 64);
  if (!userId && !/^[A-Za-z0-9-]{16,64}$/.test(caller)) return json({ error: "AUTH_REQUIRED" }, 401);
  const settings = await live(db);
  if (!settings.enabled) return json({ error: "AI_SWITCHED_OFF" }, 503);

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const [{ count: mine }, { count: total }] = await Promise.all([
    db.from("ai_usage").select("id", { count: "exact", head: true }).eq("caller", caller).gte("created_at", since),
    db.from("ai_usage").select("id", { count: "exact", head: true }).gte("created_at", since),
  ]);
  // Project-wide ceiling, so a leaked address cannot run up the OpenAI bill.
  if ((total ?? 0) >= settings.total) return json({ error: "QUOTA_LIMIT" }, 429);

  let body: unknown;
  try { body = await request.json(); } catch { return json({ error: "BAD_REQUEST" }, 400); }

  if ((body as any)?.task === "mark_mcqs") {
    const firstId = Object.keys((body as any)?.answers ?? {})[0];
    const m = markMcqs(body, pick(settings.contents, { questionId: firstId }), userId);
    if (m.rows.length) await db.from("ai_marks").insert(m.rows);
    return json(m.body, m.status);
  }

  // Answer-script pages have their own daily allowance: one full paper is 10–20 pages.
  if ((body as any)?.task === "read_script") {
    const { count: pages } = await db.from("ai_usage").select("id", { count: "exact", head: true })
      .eq("caller", caller).eq("task", "read_script").gte("created_at", since);
    if ((pages ?? 0) >= SCRIPT_PAGES_PER_DAY) return json({ error: "SCRIPT_PAGE_LIMIT" }, 429);
  }
  let photoQuotaLeft = settings.photosPerDay;
  if ((body as any)?.task === "ustaad" && (body as any)?.image) {
    const { count: photos } = await db.from("ai_usage").select("id", { count: "exact", head: true })
      .eq("caller", caller).eq("task", "ustaad_photo").gte("created_at", since);
    photoQuotaLeft = settings.photosPerDay - (photos ?? 0);
  }
  const outcome = await handle(body, pick(settings.contents, body), (url, init) => fetch(url, init), apiKey, settings.perInstall - (mine ?? 0),
    { videos: (q) => videos(db, q), photoQuotaLeft });
  if (outcome.usage) {
    await db.from("ai_usage").insert({
      caller, task: outcome.usage.task, model: outcome.usage.model, prompt_version: outcome.usage.promptVersion,
      input_tokens: outcome.usage.inputTokens, output_tokens: outcome.usage.outputTokens,
      attempts: outcome.usage.attempts, ok: outcome.usage.ok,
    });
    const mark = markRecord(body, (outcome.body as any)?.result, userId, outcome.usage);
    if (mark) await db.from("ai_marks").insert(mark);
  }
  return json(outcome.body, outcome.status);
});

// Lesson videos for a topic: memory cache, then the video_cache table (if present), then the YouTube Data API.
const videoMemo = new Map<string, Video[]>();
async function videos(db: any, query: string): Promise<Video[]> {
  const key = query.toLowerCase().replace(/\s+/g, " ").trim().slice(0, 120);
  if (!key) return [];
  if (videoMemo.has(key)) return videoMemo.get(key)!;
  try {
    const { data } = await db.from("video_cache").select("results").eq("query", key).maybeSingle();
    if (data?.results) { videoMemo.set(key, data.results); return data.results; }
  } catch { /* table not created yet: search directly */ }
  const found = await searchVideos(key, Deno.env.get("YOUTUBE_API_KEY") ?? "", (url) => fetch(url));
  if (found.length) {
    videoMemo.set(key, found);
    try { await db.from("video_cache").upsert({ query: key, results: found }); } catch { /* optional cache */ }
  }
  return found;
}

const SCRIPT_PAGES_PER_DAY = 60;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
