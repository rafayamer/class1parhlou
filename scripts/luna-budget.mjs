// Spend tracking for content generation. Prices are per million tokens (GPT-6 Luna, as published Nov 2026).
import { appendFileSync, existsSync, mkdirSync, readFileSync } from "node:fs";
import { S } from "./subject.mjs";

// Each subject keeps its own cost log, so each has its own budget.
const LOG = process.env.COST_LOG ?? `${S.root}/cost-log.jsonl`;
mkdirSync(S.root, { recursive: true });
export const PRICE = { input: 0.10, cachedInput: 0.01, output: 0.50 };
export const CAP = Number(process.env.BUDGET_USD ?? (S.science ? 15 : 10));

export function costOf(usage) {
  if (!usage) return 0;
  const cached = usage.input_tokens_details?.cached_tokens ?? 0;
  const input = (usage.input_tokens ?? 0) - cached;
  return (input * PRICE.input + cached * PRICE.cachedInput + (usage.output_tokens ?? 0) * PRICE.output) / 1e6;
}

export function logCost(stage, item, usage) {
  appendFileSync(LOG, JSON.stringify({ at: new Date().toISOString(), stage, item, usage, usd: costOf(usage) }) + "\n");
}

export function costSoFar() {
  if (!existsSync(LOG)) return 0;
  return readFileSync(LOG, "utf8").split("\n").filter(Boolean).reduce((s, l) => s + (JSON.parse(l).usd ?? 0), 0);
}

const KEY = process.env.OPENAI_API_KEY ?? "proxy-injected";

const headers = { Authorization: `Bearer ${KEY}`, "Content-Type": "application/json" };
const sleep = (ms) => new Promise((s) => setTimeout(s, ms));
const textOf = (j) => (j.output ?? []).flatMap((o) => o.content ?? []).filter((c) => c.type === "output_text").map((c) => c.text).join("");

/**
 * One Responses API call with retries; logs its cost and returns the output text.
 * Long jobs run in OpenAI's background mode and are polled, so no connection is held open for minutes
 * (the network proxy drops long requests with a 502).
 */
export async function luna(body, stage, item, { background = true } = {}) {
  if (costSoFar() >= CAP) throw new Error(`Budget cap $${CAP} reached`);
  for (let attempt = 1; ; attempt++) {
    try {
      const r = await fetch("https://api.openai.com/v1/responses", {
        method: "POST", headers,
        body: JSON.stringify({ model: "gpt-6-luna", ...body, ...(background ? { background: true, store: true } : { store: false }) }),
      });
      let j = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(`${r.status} ${JSON.stringify(j).slice(0, 300)}`);
      while (j.status === "queued" || j.status === "in_progress") {
        await sleep(4000);
        const p = await fetch(`https://api.openai.com/v1/responses/${j.id}`, { headers });
        if (p.ok) j = await p.json();
      }
      logCost(stage, item, j.usage);
      if (j.status && j.status !== "completed") throw new Error(`response ${j.status}: ${JSON.stringify(j.incomplete_details ?? j.error ?? {})}`);
      return textOf(j);
    } catch (e) {
      if (attempt >= 4) throw new Error(`${item}: ${e.message}`);
      await sleep(3000 * attempt);
    }
  }
}
