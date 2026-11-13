// Live end-to-end check against the real Supabase project. Run from owner-panel/ after `npm run build`:
//   SUPABASE_ACCESS_TOKEN=sbp_... node --use-env-proxy e2e/live.mjs <project-ref>
// The service key is fetched at run time with the access token and kept in memory only. Throwaway accounts on the
// example.test domain are created and removed. The content check publishes the live bundle with one invisible change
// (a trailing space in the edition label) and then rolls back, so students end on the same content.
import { createClient } from "@supabase/supabase-js";
import { createRequire } from "node:module";
import { spawn } from "node:child_process";
import assert from "node:assert/strict";

const REF = process.argv[2];
const TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
assert(REF && TOKEN, "usage: SUPABASE_ACCESS_TOKEN=... node e2e/live.mjs <project-ref>");
const URL = `https://${REF}.supabase.co`;
const PUBLISHABLE = "sb_publishable_1B5nCScqVoPf4YyzKQ1caQ_-Gi2WaEU";
const SHOTS = process.env.SHOT_DIR;
const results = [];
const step = async (name, fn) => {
  try { await fn(); results.push(["PASS", name]); console.log("PASS", name); }
  catch (e) { results.push(["FAIL", name, e.message]); console.log("FAIL", name, "-", e.message); }
};

const mgmt = async (path, init = {}) => {
  const r = await fetch(`https://api.supabase.com/v1/projects/${REF}${path}`, { ...init, headers: { Authorization: `Bearer ${TOKEN}`, "Content-Type": "application/json", ...(init.headers ?? {}) } });
  if (!r.ok) throw new Error(`management ${path}: ${r.status} ${await r.text()}`);
  return r.json();
};
const sql = (query) => mgmt("/database/query", { method: "POST", body: JSON.stringify({ query }) });

const keys = await mgmt("/api-keys?reveal=true");
const service = keys.find((k) => k.name === "service_role")?.api_key ?? keys.find((k) => k.type === "secret")?.api_key;
assert(service, "no service key");
const admin = createClient(URL, service, { auth: { persistSession: false, autoRefreshToken: false } });
const anonClient = () => createClient(URL, PUBLISHABLE, { auth: { persistSession: false, autoRefreshToken: false } });

/** Creates a confirmed throwaway user and signs in as them, without sending any email. */
async function throwaway(tag) {
  const email = `e2e-${tag}-${Date.now()}@example.test`;
  const { data: u, error } = await admin.auth.admin.createUser({ email, email_confirm: true });
  if (error) throw error;
  const { data: link, error: le } = await admin.auth.admin.generateLink({ type: "magiclink", email });
  if (le) throw le;
  const client = anonClient();
  const { data: s, error: se } = await client.auth.verifyOtp({ token_hash: link.properties.hashed_token, type: "magiclink" });
  if (se) throw se;
  return { id: u.user.id, email, client, session: s.session };
}

// ── Student: sign in, sync, server marks, export, delete ────────────────────
const student = await throwaway("student");
const gateway = (body) => fetch(`${URL}/functions/v1/ai`, {
  method: "POST",
  headers: { apikey: PUBLISHABLE, Authorization: `Bearer ${student.session.access_token}`, "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

await step("student registers and saves settings", async () => {
  const { error } = await student.client.rpc("register_learner", { p_display_name: "E2E Tester" });
  if (error) throw error;
  const { error: se } = await student.client.from("learner_settings").upsert({ user_id: student.id, settings: { board: "PB", grade: 9 } });
  if (se) throw se;
});

await step("progress sync is append-only and duplicate-proof", async () => {
  const rows = [{ client_id: "ev-e2e-0000001", at: new Date().toISOString(), key: "m-1-1", code: "Q:80" }];
  const a = await student.client.from("learner_events").upsert(rows, { onConflict: "user_id,client_id", ignoreDuplicates: true });
  const b = await student.client.from("learner_events").upsert(rows, { onConflict: "user_id,client_id", ignoreDuplicates: true });
  if (a.error || b.error) throw a.error ?? b.error;
  const { count } = await student.client.from("learner_events").select("id", { count: "exact", head: true });
  assert.equal(count, 1);
  const forged = await student.client.from("learner_events").insert({ client_id: "ev-e2e-forged01", at: new Date().toISOString(), key: "paper:x", code: "E:100" });
  assert(forged.error, "a phone must not write a verified exam mark");
  const marks = await student.client.from("ai_marks").insert({ user_id: student.id, question_id: "x", awarded: 1, max_marks: 1, status: "CHECKED", model: "m", prompt_version: "p" });
  assert(marks.error, "a phone must not write AI marks");
});

await step("server marks MCQs from its own key and records them", async () => {
  const r = await gateway({ task: "mark_mcqs", paperId: "sample-chapter-test", answers: { p1: 3, p2: 0 } });
  assert.equal(r.status, 200, await r.clone().text());
  const body = await r.json();
  assert.equal(body.result.results.length, 2);
  const { data } = await student.client.from("ai_marks").select("question_id,model").eq("paper_id", "sample-chapter-test");
  assert.equal(data.length, 2);
  assert(data.every((m) => m.model === "answer-key"));
});

await step("Luna marks a written answer and the server records it", async () => {
  const r = await gateway({ task: "mark_written", questionId: "s1", paperId: "sample-chapter-test", answer: "0.45 = 45/100 = 9/20. 9 and 20 are integers so 0.45 is rational.", images: [] });
  assert.equal(r.status, 200, await r.clone().text());
  const body = await r.json();
  assert(Array.isArray(body.result.criteria));
  const { data } = await student.client.from("ai_marks").select("question_id,awarded,max_marks,model").eq("question_id", "s1");
  assert.equal(data.length, 1);
  assert.equal(Number(data[0].max_marks), 3);
  console.log("   Luna gave", data[0].awarded, "/", data[0].max_marks, "with", data[0].model);
});

await step("an expired or forged session is refused", async () => {
  const r = await fetch(`${URL}/functions/v1/ai`, { method: "POST", headers: { apikey: PUBLISHABLE, Authorization: "Bearer a.b.c", "Content-Type": "application/json" }, body: "{}" });
  assert.equal(r.status, 401);
});

await step("export contains the student's own data", async () => {
  const { data, error } = await student.client.rpc("export_my_data");
  if (error) throw error;
  assert.equal(data.profile.display_name, "E2E Tester");
  assert.equal(data.settings.board, "PB");
  assert.deepEqual(data.events.map((e) => e.code), ["Q:80"]);
  assert.equal(data.ai_marks.length, 3); // two MCQs and one written answer
});

await step("account deletion removes the user and every row", async () => {
  const { error } = await student.client.rpc("delete_my_account");
  if (error) throw error;
  const [left] = await sql(`select
      (select count(*) from auth.users where id = '${student.id}') as users,
      (select count(*) from public.learner_events where user_id = '${student.id}') as events,
      (select count(*) from public.learner_settings where user_id = '${student.id}') as settings,
      (select count(*) from public.ai_marks where user_id = '${student.id}') as marks,
      (select count(*) from public.ai_usage where caller = '${student.id}') as usage,
      (select count(*) from public.student_profiles where user_id = '${student.id}') as profiles`);
  assert.deepEqual(Object.values(left).map(Number), [0, 0, 0, 0, 0, 0], JSON.stringify(left));
});

// ── Owner panel in a real browser ──────────────────────────────────────────
const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const preview = spawn("npx", ["vite", "preview", "--port", "4173", "--strictPort"], { stdio: "ignore" });
await new Promise((r) => setTimeout(r, 2500));
const browser = await chromium.launch({ proxy: process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY, bypass: "localhost,127.0.0.1" } : undefined });
const owner = await throwaway("owner");
const [{ v: originalVersion, b: originalBundle }] = await sql(`select version as v, bundle as b from public.app_content where status = 'PUBLISHED' order by subject_id limit 1`);

async function openPanel(user, path = "") {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 860 } });
  await ctx.addInitScript(([key, value]) => localStorage.setItem(key, value), [`sb-${REF}-auth-token`, JSON.stringify(user.session)]);
  const page = await ctx.newPage();
  page.on("dialog", (d) => d.accept());
  await page.goto(`http://localhost:4173/${path}`);
  return { ctx, page };
}
const shot = async (page, name) => { if (SHOTS) await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true }); };

try {
  await step("panel sign-in page renders", async () => {
    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto("http://localhost:4173/");
    await page.getByText("Email me a sign-in link").waitFor();
    await shot(page, "panel-signin");
    await ctx.close();
  });

  await step("a non-owner account is refused by the database", async () => {
    const { ctx, page } = await openPanel(owner);
    await page.getByRole("heading", { name: "Not the owner account" }).waitFor({ timeout: 15000 });
    await ctx.close();
  });

  await sql(`insert into public.memberships (tenant_id, user_id, role)
             select id, '${owner.id}', 'owner' from public.tenants where kind = 'platform' order by id limit 1`);

  await step("owner sees the dashboard, settings, learners and AI log", async () => {
    const { ctx, page } = await openPanel(owner);
    await page.getByText("Signed-up learners").waitFor({ timeout: 15000 });
    await shot(page, "panel-dashboard");
    await page.getByRole("link", { name: "AI settings" }).click();
    await page.getByText("AI tutor and AI marking are on").waitFor();
    await shot(page, "panel-settings");
    await page.getByRole("link", { name: "Learners" }).click();
    await page.getByText("signed-up learners", { exact: false }).waitFor();
    await page.getByRole("link", { name: "AI log" }).click();
    await page.getByText("The latest 200 AI calls").waitFor();
    await page.locator("td", { hasText: "Mark written answer" }).first().waitFor({ timeout: 10000 });
    await shot(page, "panel-ailog");
    await ctx.close();
  });

  await step("owner edits, saves a draft, publishes and rolls back", async () => {
    const { ctx, page } = await openPanel(owner, "#/content");
    await page.getByText("Ready to publish: every check passes.").waitFor({ timeout: 15000 });
    await shot(page, "panel-content");
    const edition = page.getByLabel("Book edition");
    await edition.fill((await edition.inputValue()) + " ");
    await page.getByRole("button", { name: "Save draft" }).click();
    await page.getByText("Draft saved.").waitFor();
    await page.getByRole("button", { name: "Publish" }).click();
    await page.getByText(`Published as version ${originalVersion + 1}.`).waitFor({ timeout: 15000 });
    await page.getByRole("tab", { name: "History" }).click();
    await page.getByRole("row", { name: new RegExp(`^${originalVersion}\\b`) }).getByRole("button", { name: "Roll back to this" }).click();
    await page.getByText(`Version ${originalVersion} is live again as version ${originalVersion + 2}.`).waitFor({ timeout: 15000 });
    await ctx.close();
    const [{ b }] = await sql(`select bundle as b from public.app_content where status = 'PUBLISHED' and subject_id = '${originalBundle.subjectId}'`);
    assert.deepEqual(b, originalBundle, "live content must end exactly as it started");
  });

  await step("a broken rubric blocks publishing in the editor", async () => {
    const { ctx, page } = await openPanel(owner, "#/content");
    await page.getByText("Ready to publish").waitFor({ timeout: 15000 });
    await page.getByRole("tab", { name: "Written questions" }).click();
    await page.getByLabel("Marks", { exact: true }).first().fill("9");
    await page.getByText("problem", { exact: false }).first().waitFor();
    assert(await page.getByRole("button", { name: "Publish" }).isDisabled());
    await shot(page, "panel-problems");
    await ctx.close();
  });
} finally {
  await browser.close();
  preview.kill();
  await admin.auth.admin.deleteUser(owner.id).catch(() => {});
  await sql(`delete from public.content_drafts where updated_by = '${owner.id}'`).catch(() => {});
}

const failed = results.filter((r) => r[0] === "FAIL");
console.log(`\n${results.length - failed.length}/${results.length} live checks passed`);
process.exit(failed.length ? 1 : 0);
