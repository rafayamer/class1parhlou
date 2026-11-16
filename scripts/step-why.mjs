// "Why this step?" for every solution in the assembled course: one short reason per step, like a tutor explaining.
// Keyed by a hash of the unit, source and steps, so a changed solution never shows stale reasons.
//   node --use-env-proxy scripts/step-why.mjs [bundle.json]
// Writes content/class9-maths/why.json; assemble-course.mjs attaches it to each solution as "why".
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { luna, costSoFar } from "./luna-budget.mjs";

const ROOT = "content/class9-maths";
const OUT = `${ROOT}/why.json`;
const bundle = JSON.parse(readFileSync(process.argv[2] ?? `${ROOT}/bundle.json`, "utf8"));
const store = existsSync(OUT) ? JSON.parse(readFileSync(OUT, "utf8")) : {};
export const whyKey = (unit, s) => createHash("sha1").update(`${unit}|${s.kind}|${s.source}|${s.steps.join("\n")}`).digest("hex").slice(0, 16);

const str = { type: "string" }, strs = { type: "array", items: str };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const schema = obj({ items: { type: "array", items: obj({ id: str, why: strs }) } });
const PROMPT = `You are a friendly Class 9 maths tutor in Punjab. For each worked solution, explain WHY each step is done, in one short
sentence per step (at most 18 words), in simple English a 14-year-old understands: name the rule, law or idea used, or what the step
achieves. Return exactly one sentence per step, in order (same count as steps). Never just repeat the step.`;

const todo = [];
for (const c of bundle.subject.chapters) for (const m of c.modules) for (const s of m.solutions) {
  if (s.steps.length < 2) continue;
  const k = whyKey(c.number, s);
  if (!store[k]) todo.push({ id: k, question: s.question.slice(0, 400), steps: s.steps });
}
console.log(`${todo.length} solutions need reasons`);
let next = 0, done = 0;
await Promise.all(Array.from({ length: 6 }, async () => {
  while (next < todo.length) {
    const batch = todo.slice(next, next + 12); next += 12;
    try {
      const text = await luna({ max_output_tokens: 30000, reasoning: { effort: "low" }, instructions: PROMPT, input: JSON.stringify(batch),
        text: { format: { type: "json_schema", name: "why", strict: true, schema } } }, "step-why", `batch ${next}`);
      for (const it of JSON.parse(text).items) {
        const t = batch.find((b) => b.id === it.id);
        if (t && it.why.length === t.steps.length) { store[it.id] = it.why; done++; }
      }
      writeFileSync(OUT, JSON.stringify(store));
    } catch (e) { console.log(`batch failed: ${e.message}`); if (String(e.message).includes("Budget")) process.exit(2); }
  }
}));
writeFileSync(OUT, JSON.stringify(store));
console.log(`added ${done}; ${Object.keys(store).length} stored. Total spend $${costSoFar().toFixed(3)}`);
