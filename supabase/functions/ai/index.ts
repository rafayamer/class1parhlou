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
import { PROMPT_VERSION, type Video } from "./core.ts";
import { DEFAULT_PRICES, creditBalance, grantExpiry, needsMonthlyGrant, priceOf, whatsLeft, type LedgerRow, type Prices } from "./credits.ts";
import { updateMemory } from "./examiner.ts";

const APP_KEY = Deno.env.get("PARHLOU_APP_KEY") ?? "";
const CACHE_MS = 30_000;

interface Live { at: number; contents: Content[]; enabled: boolean; perInstall: number; total: number; photosPerDay: number;
  monthlyCredits: number; creditMonths: number; prices: Prices }
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
    monthlyCredits: Number(setting("monthly_credits", 1000)),
    creditMonths: Number(setting("credit_months", 4)),
    prices: { ...DEFAULT_PRICES, ...(setting("credit_prices", {}) as Prices) },
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

  // ── Credits: the month's grant is given on the first request of the month; each request costs its price. ──────
  // (Before the credits table exists, everything works as before, without credits.)
  const credits = await creditState(db, caller, userId, settings);
  if ((body as any)?.task === "credits") {
    return json({ result: credits ? { balance: credits.balance, resetsOn: credits.resetsOn, monthly: settings.monthlyCredits,
      prices: settings.prices, left: whatsLeft(credits.balance, settings.prices) } : null });
  }
  if ((body as any)?.task === "feedback") {
    const text = String((body as any)?.text ?? "").trim().slice(0, 3000);
    if (!text) return json({ error: "BAD_REQUEST" }, 400);
    const { error } = await db.from("feedback").insert({ holder: caller, user_id: userId, text,
      screen: String((body as any)?.screen ?? "").slice(0, 80) || null, app_version: String((body as any)?.appVersion ?? "").slice(0, 40) || null });
    return error ? json({ error: "NOT_AVAILABLE" }, 503) : json({ result: { ok: true } });
  }
  const price = priceOf(body, settings.prices);
  if (credits && price.credits > 0 && credits.balance < price.credits) {
    return json({ error: "CREDITS_USED_UP", resetsOn: credits.resetsOn }, 402);
  }

  // Lesson videos the student asked for ("Watch a lesson on this topic"): a cached YouTube search, no AI call.
  if ((body as any)?.task === "videos") {
    const q = String((body as any)?.query ?? "").replace(/[^\p{L}\p{N} .,'+-]/gu, " ").replace(/\s+/g, " ").trim().slice(0, 120);
    if (q.length < 3) return json({ error: "BAD_REQUEST" }, 400);
    if ((mine ?? 0) >= settings.perInstall) return json({ error: "QUOTA_LIMIT" }, 429);
    const found = await videos(db, `class 9 maths ${q}`.slice(0, 120));
    if (credits && price.credits > 0) await spend(db, caller, userId, price);
    await db.from("ai_usage").insert({ caller, task: "videos", model: "youtube", prompt_version: PROMPT_VERSION, input_tokens: 0, output_tokens: 0, attempts: 1, ok: true });
    return json({ result: { videos: found } });
  }

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
  // Ask Ustaad remembers the student: his summary of them is added to what the phone sends about them.
  const isUstaad = (body as any)?.task === "ustaad";
  const memory = isUstaad ? await loadMemory(db, caller) : null;
  if (memory?.summary) (body as any).profile = `${String((body as any).profile ?? "").slice(0, 1200)}\nWhat Ustaad remembers: ${memory.summary}`;
  const outcome = await handle(body, pick(settings.contents, body), (url, init) => fetch(url, init), apiKey, settings.perInstall - (mine ?? 0),
    { videos: (q) => videos(db, q), photoQuotaLeft,
      flag: async (category, message) => { try { await db.from("safety_flags").insert({ holder: caller, user_id: userId, category, excerpt: message.slice(0, 500) }); } catch { /* table not created yet */ } } });
  if (outcome.usage?.ok && credits && price.credits > 0) await spend(db, caller, userId, price);
  if (isUstaad && outcome.status === 200) {
    const reply = (outcome.body as any)?.result;
    await saveChat(db, caller, userId, memory, String((body as any)?.message ?? "") || "(sent a photo of a question)",
      [reply?.reply, reply?.finalAnswer ? `Final answer: ${reply.finalAnswer}` : ""].filter(Boolean).join("\n"), apiKey);
  }
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

// ── Credits ──────────────────────────────────────────────────────────────────────────────────────────────────────────
async function creditState(db: any, holder: string, userId: string | null, s: Live): Promise<{ balance: number; resetsOn: string } | null> {
  try {
    const since = new Date(Date.now() - (s.creditMonths + 1) * 31 * 86_400_000).toISOString();
    const { data, error } = await db.from("credit_ledger").select("kind,amount,created_at,expires_at").eq("holder", holder).gte("created_at", since);
    if (error) return null; // table not created yet: credits are off
    let rows: LedgerRow[] = data ?? [];
    const now = new Date();
    if (needsMonthlyGrant(rows, now) && s.monthlyCredits > 0) {
      const g = { holder, user_id: userId, kind: "grant", amount: s.monthlyCredits, expires_at: grantExpiry(now, s.creditMonths) };
      await db.from("credit_ledger").insert(g);
      rows = [...rows, { kind: "grant", amount: g.amount, created_at: now.toISOString(), expires_at: g.expires_at }];
    }
    return creditBalance(rows, now);
  } catch { return null; }
}
async function spend(db: any, holder: string, userId: string | null, p: { task: string; credits: number }) {
  try { await db.from("credit_ledger").insert({ holder, user_id: userId, kind: "spend", amount: p.credits, task: p.task }); } catch { /* best effort */ }
}

// ── Memory and saved chats (kept 30 days, at most 200 messages a student) ─────────────────────────────────────────
async function loadMemory(db: any, holder: string): Promise<{ summary: string; messagesSince: number } | null> {
  try {
    const { data, error } = await db.from("student_memory").select("summary,messages_since").eq("holder", holder).maybeSingle();
    if (error) return null;
    return { summary: data?.summary ?? "", messagesSince: data?.messages_since ?? 0 };
  } catch { return null; }
}
async function saveChat(db: any, holder: string, userId: string | null, memory: { summary: string; messagesSince: number } | null,
                        student: string, ustaad: string, apiKey: string) {
  if (memory === null) return; // tables not created yet
  try {
    await db.from("chat_messages").insert([
      { holder, user_id: userId, role: "student", text: student.slice(0, 4000) },
      { holder, user_id: userId, role: "ustaad", text: ustaad.slice(0, 4000) || "…" },
    ]);
    // Keep chats small: 30 days, and the newest 200 messages.
    await db.from("chat_messages").delete().eq("holder", holder).lt("created_at", new Date(Date.now() - 30 * 86_400_000).toISOString());
    const { data: cut } = await db.from("chat_messages").select("id").eq("holder", holder).order("id", { ascending: false }).range(200, 200);
    if (cut?.[0]?.id) await db.from("chat_messages").delete().eq("holder", holder).lte("id", cut[0].id);
    // Every 10 messages, Ustaad updates what he knows about the student.
    let since = memory.messagesSince + 1;
    let summary = memory.summary;
    if (since >= 10) {
      const { data: recent } = await db.from("chat_messages").select("role,text").eq("holder", holder).order("id", { ascending: false }).limit(20);
      const out = await updateMemory(memory.summary, (recent ?? []).reverse(), (u, i) => fetch(u, i), apiKey);
      await db.from("ai_usage").insert({ caller: holder, task: "memory_update", model: out.usage.model, prompt_version: out.usage.promptVersion,
        input_tokens: out.usage.inputTokens, output_tokens: out.usage.outputTokens, attempts: 1, ok: out.usage.ok });
      if (out.summary) { summary = out.summary; since = 0; }
    }
    await db.from("student_memory").upsert({ holder, user_id: userId, summary, messages_since: since, updated_at: new Date().toISOString() });
  } catch { /* chats are a convenience; never fail the reply */ }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
