// Read-only screenshots of every panel page at phone and desktop width, light and dark, as a throwaway owner.
//   SUPABASE_ACCESS_TOKEN=... SHOT_DIR=/path node --use-env-proxy e2e/shots.mjs <project-ref>   (after `npx vite build`)
import { createClient } from "@supabase/supabase-js";
import { createRequire } from "node:module";
import { spawn } from "node:child_process";

const REF = process.argv[2];
const TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
const OUT = process.env.SHOT_DIR;
const URL = `https://${REF}.supabase.co`;
const mgmt = async (path, init = {}) => {
  const r = await fetch(`https://api.supabase.com/v1/projects/${REF}${path}`, { ...init, headers: { Authorization: `Bearer ${TOKEN}`, "Content-Type": "application/json" } });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
};
const keys = await mgmt("/api-keys?reveal=true");
const admin = createClient(URL, keys.find((k) => k.name === "service_role").api_key, { auth: { persistSession: false } });
const email = `e2e-shots-${Date.now()}@example.test`;
const { data: u } = await admin.auth.admin.createUser({ email, email_confirm: true });
await mgmt("/database/query", { method: "POST", body: JSON.stringify({ query: `insert into public.memberships (tenant_id, user_id, role) select id, '${u.user.id}', 'owner' from public.tenants where kind = 'platform' order by id limit 1` }) });
const { data: link } = await admin.auth.admin.generateLink({ type: "magiclink", email });
const anon = createClient(URL, "sb_publishable_1B5nCScqVoPf4YyzKQ1caQ_-Gi2WaEU", { auth: { persistSession: false } });
const { data: s } = await anon.auth.verifyOtp({ token_hash: link.properties.hashed_token, type: "magiclink" });

const { chromium } = createRequire(import.meta.url)("playwright");
const preview = spawn("npx", ["vite", "preview", "--port", "4174", "--strictPort"], { stdio: "ignore" });
await new Promise((r) => setTimeout(r, 2500));
const browser = await chromium.launch({ proxy: { server: process.env.HTTPS_PROXY, bypass: "localhost,127.0.0.1" } });
try {
  for (const [label, viewport] of [["phone", { width: 390, height: 844 }], ["desktop", { width: 1280, height: 860 }]]) {
    for (const scheme of ["light", "dark"]) {
      const ctx = await browser.newContext({ viewport, colorScheme: scheme, deviceScaleFactor: 1 });
      await ctx.addInitScript(([k, v]) => localStorage.setItem(k, v), [`sb-${REF}-auth-token`, JSON.stringify(s.session)]);
      const page = await ctx.newPage();
      for (const p of ["dashboard", "content", "settings", "learners", "ai-log"]) {
        await page.goto(`http://localhost:4174/#/${p}`);
        await page.waitForTimeout(2500);
        const wide = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
        if (wide) console.log(`HORIZONTAL SCROLL: ${p} ${label} ${scheme}`);
        await page.screenshot({ path: `${OUT}/panel-${p}-${label}-${scheme}.png`, fullPage: label === "phone" });
      }
      await ctx.close();
    }
  }
} finally {
  await browser.close(); preview.kill();
  await admin.auth.admin.deleteUser(u.user.id);
}
console.log("done");
