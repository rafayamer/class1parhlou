// Works out which book figure belongs to which example, exercise question or part, by walking the page transcriptions
// in order (unit → exercise → question → part → [FIGURE]). Figures on the answer pages are the book's answer figures.
//   node scripts/figure-links.mjs
// Reads content/class9-maths/figures.json (from book-figures.mjs); writes content/class9-maths/figure-links.json:
//   { question: { "<unit>|<ex>|<q>|<part>": [ids] }, example: { "<unit>|<n>": [ids] }, answer: { "<unit>|<ex>|<q>|<part>": [ids] } }
// where <ex> is "6.5", "8" or "r2" (review exercise 2) and <part> may be "".
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { S } from "./subject.mjs";

const ROOT = S.root;
const figures = JSON.parse(readFileSync(join(ROOT, "figures.json"), "utf8"));
const byPage = new Map();
for (const f of figures) byPage.set(`${f.page}-${f.index}`, f.id);
const ANSWER_FROM = 262, LAST = 280;
const PART = /^\((i{1,3}|iv|vi{0,3}|ix|x{1,3}|[a-h])\)/;

const out = { question: {}, example: {}, answer: {} };
const add = (map, key, id) => { (map[key] ??= []).includes(id) || map[key].push(id); };

let unit = 0, ex = null, q = null, part = null, example = null;
out.activity = {};
// ── Physics: one exercise per chapter (Sections A–E, items "Q1.4."), examples "EXAMPLE 1.2:", activities ──────────────
if (S.key === "physics") {
  const KIND = { B: "Short", C: "CRQ", D: "Comprehensive", E: "Numerical" };
  let section = null, activity = null;
  for (let p = 1; p <= 260; p++) {
    const f = join(ROOT, "pages", `p${String(p).padStart(3, "0")}.md`);
    if (!existsSync(f)) continue;
    let figIndex = 0;
    for (const raw of readFileSync(f, "utf8").split("\n")) {
      const line = raw.replace(/\*\*/g, "").replace(/^#+\s*/, "").trim();
      if (!line) continue;
      let m;
      if ((m = /^Chapter\s+(\d+)\b/i.exec(line))) { unit = Number(m[1]); section = null; q = null; example = null; activity = null; }
      else if ((m = /^SECTION\s+([A-E])\b/i.exec(line))) { section = m[1].toUpperCase(); q = null; part = null; example = null; activity = null; }
      else if ((m = /^EXAMPLE\s+(\d+(?:\.\d+)?)/i.exec(line))) { example = m[1]; activity = null; section = null; }
      else if ((m = /^ACTIVITY\s*([0-9.]+)/i.exec(line))) { activity = m[1].includes(".") ? m[1] : `${unit}.${m[1]}`; example = null; section = null; }
      else if ((m = /^(\d{1,2})\.(\d{1,2})\s+[A-Z]/.exec(line)) && !section) { example = null; activity = null; }
      if (section && (m = /^Q?(\d{1,2}\.\d{1,2})\./.exec(line))) { q = m[1]; part = null; }
      for (const t of line.matchAll(/\[FIGURE[^\]]*\]|(?:^|\s)\(([a-h])\)\s/g)) {
        if (t[0].trim().startsWith("[FIGURE")) {
          figIndex++;
          const id = byPage.get(`${p}-${figIndex}`);
          if (!id) continue;
          if (section && KIND[section] && q) add(out.question, `${unit}|${KIND[section]} ${q}|${part ?? ""}`, id);
          else if (example) add(out.example, `${unit}|${example}`, id);
          else if (activity) add(out.activity, `Activity ${activity}`, id);
        } else if (section && q) part = t[1];
      }
    }
  }
  writeFileSync(join(ROOT, "figure-links.json"), JSON.stringify(out, null, 1));
}
// Question-level figures and parts per question, to spread figures over parts afterwards.
const perQuestion = new Map(); // key unit|ex|q → { figs: [], parts: [] , answer }
const qRecord = (answer) => {
  const k = `${answer ? "A" : "Q"}|${unit}|${ex}|${q}`;
  if (!perQuestion.has(k)) perQuestion.set(k, { figs: [], parts: [], answer, base: `${unit}|${ex}|${q}` });
  return perQuestion.get(k);
};

for (let p = 1; p <= (S.key === "physics" ? 0 : LAST); p++) {
  const f = join(ROOT, "pages", `p${String(p).padStart(3, "0")}.md`);
  if (!existsSync(f)) continue;
  const answer = p >= ANSWER_FROM;
  if (p === ANSWER_FROM) { ex = null; q = null; part = null; }
  let figIndex = 0;
  for (const raw of readFileSync(f, "utf8").split("\n")) {
    const line = raw.replace(/\*\*/g, "").replace(/^#+\s*/, "").trim();
    if (!line) continue;
    let m;
    if (!answer && (m = /^(\d{1,2})\.(\d{1,2})(?:\.\d+)*\s+[A-Z]/.exec(line))) { unit = Number(m[1]); }
    if ((m = /^REVIEW EXERCISE\s*(\d+)/i.exec(line))) { unit = Number(m[1]); ex = `r${m[1]}`; q = null; part = null; example = null; }
    else if ((m = /^EXERCISE\s*(\d+)(?:\.(\d+))?\b/i.exec(line))) { unit = Number(m[1]); ex = m[2] ? `${m[1]}.${m[2]}` : m[1]; q = null; part = null; example = null; }
    else if (!answer && (m = /^EXAMPLE\s+(\d+)/i.exec(line))) { example = Number(m[1]); ex = null; q = null; part = null; }
    else if (!answer && /^(Unit|UNIT)\b/.test(line)) { /* chapter headers */ }
    if (ex && (m = /^(?:Q\s*)?(\d{1,2})\.(?:\s|$)/.exec(line))) { q = Number(m[1]); part = null; }
    // Walk the line's tokens in order: parts and figures.
    const rest = line.replace(/^(?:Q\s*)?\d{1,2}\.\s*/, "");
    let lineStartsWithPart = false;
    for (const t of rest.matchAll(/\[FIGURE[^\]]*\]|\((i{1,3}|iv|vi{0,3}|ix|x{1,3}|[a-h])\)/g)) {
      if (t[0].startsWith("[FIGURE")) {
        figIndex++;
        const id = byPage.get(`${p}-${figIndex}`);
        if (!id) continue;
        if (example != null && !ex) { add(out.example, `${unit}|${example}`, id); continue; }
        if (!ex || q == null) continue;
        const rec = qRecord(answer);
        if (part && lineStartsWithPart) add(answer ? out.answer : out.question, `${unit}|${ex}|${q}|${part}`, id);
        else rec.figs.push(id);
      } else {
        if (t.index === 0) lineStartsWithPart = true;
        part = t[1];
        if (ex && q != null) { const rec = qRecord(answer); if (!rec.parts.includes(part)) rec.parts.push(part); }
      }
    }
    // A line that is only a part marker followed by text: figures on the next lines belong to that part.
    if (PART.test(rest) && !/\((i{1,3}|iv|vi{0,3}|ix|x{1,3}|[a-h])\)/.test(rest.slice(4))) lineStartsWithPart = true;
  }
}
// Spread question-level figures: one per part when the counts match, else to the whole question.
for (const rec of perQuestion.values()) {
  const map = rec.answer ? out.answer : out.question;
  if (!rec.figs.length) continue;
  if (rec.parts.length > 1 && rec.figs.length === rec.parts.length) rec.figs.forEach((id, i) => add(map, `${rec.base}|${rec.parts[i]}`, id));
  else rec.figs.forEach((id) => add(map, `${rec.base}|`, id));
}
if (S.key !== "physics") writeFileSync(join(ROOT, "figure-links.json"), JSON.stringify(out, null, 1));
// Descriptions travel with the images, for screen readers (TalkBack) in the app.
// (One index for every subject: each run keeps the other subjects' entries.)
const indexFile = "android/app/src/main/assets/figures/index.json";
const index = existsSync(indexFile) ? JSON.parse(readFileSync(indexFile, "utf8")) : {};
for (const k of Object.keys(index)) if (k.startsWith(S.idPrefix) && (S.idPrefix || !/^[a-z]+-p\d/.test(k))) delete index[k];
for (const f of figures) index[f.id] = f.description;
writeFileSync(indexFile, JSON.stringify(Object.fromEntries(Object.entries(index).sort())));
const n = (m) => Object.values(m).reduce((s, l) => s + l.length, 0);
console.log(`linked: ${n(out.question)} to questions, ${n(out.example)} to examples, ${n(out.answer)} to book answers, ${n(out.activity)} to activities (of ${figures.length} figures)`);
