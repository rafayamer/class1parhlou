// Supabase Edge Function: POST /functions/v1/ai
// Holds the OpenAI key (secret OPENAI_API_KEY) and calls gpt-6-luna. The phone never sees the key.
// Deployed with verify_jwt = false so older builds without sign-in keep working; it checks the app key itself,
// verifies a student's session token when one is sent, and applies per-student and project-wide daily limits.
// Content (questions, keys, rubrics, notes) and limits are read from the database (app_content, app_config),
// so they can change without redeploying. The bundled sample is only a fallback.
import { createClient } from "jsr:@supabase/supabase-js@2";
import bundled from "./content/sample.json" with { type: "json" };
import { handle, markMcqs, markRecord, pickContent, type Content } from "./handler.ts";
import { searchVideos } from "./youtube.ts";
import { PROMPT_VERSION, type Video } from "./core.ts";
import { allowanceFor, creditsFor, creditsLeft, estimates, monthStart, nextMonthStart, overCap, premiumMonth, usdOf } from "./credits.ts";
import { updateMemory } from "./examiner.ts";

const APP_KEY = Deno.env.get("PARHLOU_APP_KEY") ?? "";
const CACHE_MS = 30_000;

interface Live { at: number; contents: Content[]; enabled: boolean; perInstall: number; total: number; photosPerDay: number;
  /** Premium's monthly credits, worth monthlyUsd of AI time (default 1,000 credits = $3.50). */
  monthlyCredits: number;
  /** Money: what one million tokens cost, each Premium student's monthly AI allowance in US dollars, and the free plan's (0 = none). */
  usdIn: number; usdOut: number; monthlyUsd: number; freeUsd: number; dailyUsdTotal: number }
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
    // GPT-6 Luna's prices; 0 (never set) would make every call look free, so the published prices are used instead.
    usdIn: Number(setting("usd_per_million_input", 0)) || 0.10,
    usdOut: Number(setting("usd_per_million_output", 0)) || 0.50,
    monthlyUsd: Number(setting("monthly_usd_per_student", 3.5)),
    freeUsd: Number(setting("free_monthly_usd", 0)),
    dailyUsdTotal: Number(setting("daily_usd_total", 1)),
  };
  return cache;
}

/** Picks the subject bundle a request is about (see pickContent). */
const pick = (contents: Content[], body: any): Content => pickContent(contents, body);

const MAX_BODY_BYTES = 16_000_000;
/** Tasks that never call OpenAI. */
const FREE_TASKS = ["feedback", "mark_mcqs", "videos"];
/** What works without an account (nothing that calls OpenAI or YouTube, or keeps per-caller rows). */
const GUEST_TASKS = ["feedback", "mark_mcqs", "credits"];

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  if (!APP_KEY || request.headers.get("apikey") !== APP_KEY) return json({ error: "AUTH_REQUIRED" }, 401);
  // Refuse oversized requests before reading them (5 photos of up to ~2.8 MB each, plus text).
  if (Number(request.headers.get("content-length") ?? 0) > MAX_BODY_BYTES) return json({ error: "TOO_LARGE" }, 413);
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
  const task = String((body as any)?.task ?? "");

  // Without an account only feedback, MCQ checking and the allowance screen work. Anything else, and anything that writes
  // per-caller rows (credits, chats, memory), needs a real account: a made-up install id per request must get nothing.
  if (!userId && !GUEST_TASKS.includes(task)) return json({ error: "SIGN_IN_REQUIRED" }, 401);

  // ── Credits: Premium gets monthlyCredits a month (worth monthlyUsd of AI time), counted from the day they paid and ending
  // with Premium. Every AI call is charged at its real cost. The free plan gets free_monthly_usd (0 = no AI at all).
  const plan = userId ? await planOf(db, userId) : { plus: false, paidUntil: null };
  const now = new Date();
  const month = plan.plus && plan.paidUntil ? premiumMonth(plan.paidUntil, now) : { start: monthStart(now).slice(0, 10), resetsOn: nextMonthStart(now) };
  const { allowed, capUsd: cap } = allowanceFor(userId ? plan.plus : false, settings.monthlyUsd, settings.freeUsd);
  const usd = userId ? await spend$(db, caller, `${month.start}T00:00:00.000Z`) : 0;
  const allowance = { used: Math.round(usd * 100) / 100, cap, resetsOn: month.resetsOn, plan: plan.plus ? "plus" : "free" };
  if (task === "credits") {
    // One credit is always worth the same (Premium's monthlyUsd / monthlyCredits); a plan's month holds what its cap buys.
    const monthly = allowed && cap > 0 ? Math.round(creditsFor(cap, settings.monthlyUsd, settings.monthlyCredits)) : 0;
    return json({ result: {
      plan: allowance.plan, premiumUntil: plan.paidUntil, monthly, resetsOn: month.resetsOn,
      balance: monthly > 0 ? creditsLeft(usd, cap, monthly) : 0,
      estimates: estimates(await taskCosts(db), settings.usdIn, settings.usdOut, settings.monthlyUsd, settings.monthlyCredits),
      usd: allowance,
    } });
  }
  const free = FREE_TASKS.includes(task);
  // Project-wide money ceiling per 24 hours: a last line of defence for the OpenAI balance, whatever happens.
  if (!free && settings.dailyUsdTotal > 0 && await spend$(db, null, since) >= settings.dailyUsdTotal) return json({ error: "QUOTA_LIMIT" }, 429);
  if (!free && !allowed) return json({ error: "PREMIUM_REQUIRED" }, 402);
  if (!free && overCap(usd, cap)) return json({ error: "CREDITS_USED_UP", resetsOn: allowance.resetsOn, usd: allowance }, 402);
  if (task === "feedback") {
    const text = String((body as any)?.text ?? "").trim().slice(0, 3000);
    if (!text) return json({ error: "BAD_REQUEST" }, 400);
    // 20 a day per student; all students without an account share 300 a day.
    const { count: sent } = await (userId
      ? db.from("feedback").select("id", { count: "exact", head: true }).eq("user_id", userId).gte("created_at", since)
      : db.from("feedback").select("id", { count: "exact", head: true }).is("user_id", null).gte("created_at", since));
    if ((sent ?? 0) >= (userId ? 20 : 300)) return json({ error: "QUOTA_LIMIT" }, 429);
    const { error } = await db.from("feedback").insert({ holder: caller, user_id: userId, text,
      screen: String((body as any)?.screen ?? "").slice(0, 80) || null, app_version: String((body as any)?.appVersion ?? "").slice(0, 40) || null });
    return error ? json({ error: "NOT_AVAILABLE" }, 503) : json({ result: { ok: true } });
  }
  // Lesson videos the student asked for ("Watch a lesson on this topic"): a cached YouTube search, no AI call.
  if ((body as any)?.task === "videos") {
    const q = String((body as any)?.query ?? "").replace(/[^\p{L}\p{N} .,'+-]/gu, " ").replace(/\s+/g, " ").trim().slice(0, 120);
    if (q.length < 3) return json({ error: "BAD_REQUEST" }, 400);
    if ((mine ?? 0) >= settings.perInstall) return json({ error: "QUOTA_LIMIT" }, 429);
    // The app sends the subject's name; older builds only had Mathematics.
    const subject = String((body as any)?.subject ?? "maths").replace(/[^\p{L} ]/gu, "").toLowerCase().slice(0, 30) || "maths";
    const found = await videos(db, (/\b(class\s*9|9th\s*class)\b/i.test(q) ? q : `class 9 ${subject} ${q}`).slice(0, 120));
    await logUsage(db, { caller, task: "videos", model: "youtube", prompt_version: PROMPT_VERSION, input_tokens: 0, output_tokens: 0, attempts: 1, ok: true });
    return json({ result: { videos: found } });
  }

  if (task === "mark_mcqs") {
    const firstId = Object.keys((body as any)?.answers ?? {})[0];
    const m = markMcqs(body, pick(settings.contents, { questionId: firstId }), userId);
    if (m.rows.length) {
      // Saved marks: at most 3,000 a day per student (a full paper is about 20).
      const { count: saved } = await db.from("ai_marks").select("id", { count: "exact", head: true }).eq("user_id", userId).gte("created_at", since);
      if ((saved ?? 0) + m.rows.length > 3000) return json({ error: "QUOTA_LIMIT" }, 429);
      await db.from("ai_marks").insert(m.rows);
    }
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
  let outcome: Awaited<ReturnType<typeof handle>>;
  try {
    outcome = await handle(body, pick(settings.contents, body), (url, init) => fetch(url, init), apiKey, settings.perInstall - (mine ?? 0),
      { videos: (q) => videos(db, q), photoQuotaLeft,
        flag: async (category, message) => { try { await db.from("safety_flags").insert({ holder: caller, user_id: userId, category, excerpt: message.slice(0, 500) }); } catch { /* table not created yet */ } } });
  } catch (e) {
    // Never pass internal details to the phone; the log keeps them for the owner.
    console.error("ai task failed", task, e instanceof Error ? e.message : String(e));
    return json({ error: "AI_UNAVAILABLE" }, 503);
  }
  if (isUstaad && outcome.status === 200) {
    const reply = (outcome.body as any)?.result;
    await saveChat(db, caller, userId, memory, String((body as any)?.message ?? "") || "(sent a photo of a question)",
      [reply?.reply, reply?.finalAnswer ? `Final answer: ${reply.finalAnswer}` : ""].filter(Boolean).join("\n"), apiKey, settings);
  }
  if (outcome.usage) {
    await logUsage(db, {
      caller, task: outcome.usage.task, model: outcome.usage.model, prompt_version: outcome.usage.promptVersion,
      input_tokens: outcome.usage.inputTokens, output_tokens: outcome.usage.outputTokens,
      attempts: outcome.usage.attempts, ok: outcome.usage.ok,
      usd: usdOf(outcome.usage.inputTokens, outcome.usage.outputTokens, settings.usdIn, settings.usdOut),
      ...(outcome.usage.ok ? {} : { error: outcome.usage.error ?? "unknown" }),
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

// ── Usage and money ──────────────────────────────────────────────────────────────────────────────────────────────────────────
/**
 * Logs one AI call. Before the database has the newer columns (cost, failure reason), the call is logged without them.
 */
async function logUsage(db: any, row: Record<string, unknown>) {
  let { error } = await db.from("ai_usage").insert(row);
  if (error && "error" in row) { const { error: _reason, ...rest } = row; row = rest; ({ error } = await db.from("ai_usage").insert(row)); }
  if (error && "usd" in row) { const { usd: _drop, ...rest } = row; await db.from("ai_usage").insert(rest); }
}

/**
 * What AI calls cost since [since], in US dollars: one caller's, or everyone's when [caller] is null. Summed in the database
 * (ai_spend), because the REST API returns at most 1,000 rows and adding them up here undercounted heavy use. Before that
 * function exists, falls back to adding up rows; 0 if the cost column is missing too.
 */
async function spend$(db: any, caller: string | null, since: string): Promise<number> {
  try {
    const { data, error } = await db.rpc("ai_spend", { p_caller: caller, p_since: since });
    if (!error && data !== null && data !== undefined) return Number(data);
    let q = db.from("ai_usage").select("usd").gte("created_at", since).gt("usd", 0);
    if (caller) q = q.eq("caller", caller);
    const { data: rows, error: e2 } = await q;
    if (e2) return 0;
    return (rows ?? []).reduce((s: number, r: any) => s + Number(r.usd ?? 0), 0);
  } catch { return 0; }
}

/**
 * The student's plan: Premium while paid_until is today or later. plus is null when plans are not set up yet (then AI works as
 * before plans).
 */
async function planOf(db: any, userId: string): Promise<{ plus: boolean | null; paidUntil: string | null }> {
  try {
    const { data, error } = await db.from("subscriptions").select("paid_until").eq("user_id", userId).maybeSingle();
    if (error) return { plus: null, paidUntil: null };
    const until = data?.paid_until ? String(data.paid_until) : null;
    return { plus: !!until && until >= new Date().toISOString().slice(0, 10), paidUntil: until };
  } catch { return { plus: null, paidUntil: null }; }
}

/** The average real cost of each kind of AI call over the last 30 days (ai_task_costs), cached for 10 minutes. */
let costs: { at: number; byTask: Record<string, { usd: number; calls: number }> } | null = null;
async function taskCosts(db: any): Promise<Record<string, { usd: number; calls: number }>> {
  if (costs && Date.now() - costs.at < 600_000) return costs.byTask;
  const byTask: Record<string, { usd: number; calls: number }> = {};
  try {
    const { data, error } = await db.rpc("ai_task_costs", { p_since: new Date(Date.now() - 30 * 86_400_000).toISOString() });
    if (!error) for (const r of data ?? []) byTask[String(r.task)] = { usd: Number(r.avg_usd ?? 0), calls: Number(r.calls ?? 0) };
  } catch { /* function not created yet: typical costs are used */ }
  costs = { at: Date.now(), byTask };
  return byTask;
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
                        student: string, ustaad: string, apiKey: string, settings: Live) {
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
      await logUsage(db, { caller: holder, task: "memory_update", model: out.usage.model, prompt_version: out.usage.promptVersion,
        input_tokens: out.usage.inputTokens, output_tokens: out.usage.outputTokens, attempts: 1, ok: out.usage.ok,
        usd: usdOf(out.usage.inputTokens, out.usage.outputTokens, settings.usdIn, settings.usdOut) });
      if (out.summary) { summary = out.summary; since = 0; }
    }
    await db.from("student_memory").upsert({ holder, user_id: userId, summary, messages_since: since, updated_at: new Date().toISOString() });
  } catch { /* chats are a convenience; never fail the reply */ }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
