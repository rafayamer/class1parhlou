// Aligns every exercise answer with the textbook's answer key, using three sources:
//   1. the book's answer-key pages, re-read twice at high resolution (whole page, then top and bottom halves);
//   2. an independent teacher-written solution set for the same book (read page by page; used only to verify);
//   3. our own checked working (u-N-solutions.json).
// Where they disagree, an examiner decides with high reasoning effort, and a question whose answer was wrong is
// solved again towards the right answer and re-checked. Nothing here is shown to students by source name.
//
//   node --use-env-proxy scripts/align-answers.mjs <book.pdf> <indepDir> [unit ...]
// indepDir holds u01.pdf … u13.pdf. Every stage is cached in content/class9-maths/work/align-*.json.
// Output per unit: content/class9-maths/units/u-N-align.json (applied by assemble-course.mjs).
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync, unlinkSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { luna, costSoFar } from "./luna-budget.mjs";
import { SYNTAX } from "./textmatch.mjs";

const ROOT = "content/class9-maths";
const WORK = join(ROOT, "work");
mkdirSync(WORK, { recursive: true });
const [bookPdf, indepDir, ...unitArgs] = process.argv.slice(2);
const UNITS = unitArgs.length ? unitArgs.map(Number) : Array.from({ length: 13 }, (_, i) => i + 1);
const KEY_PAGES = Array.from({ length: 19 }, (_, i) => 262 + i); // PDF pages 262–280 ("ANSWERS")

const str = { type: "string" }, strs = { type: "array", items: str }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
const enumOf = (...v) => ({ type: "string", enum: v });

async function cached(name, run) {
  const f = join(WORK, `align-${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const result = await run();
  writeFileSync(f, JSON.stringify(result, null, 1));
  return result;
}
async function structured(name, item, instructions, input, schema, { effort = "medium", maxTokens = 30000, background = true } = {}) {
  const text = await luna({ max_output_tokens: maxTokens, reasoning: { effort }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `align-${name}`, item, { background });
  return JSON.parse(text);
}
async function pool(items, n, fn) {
  let next = 0;
  const out = new Array(items.length);
  await Promise.all(Array.from({ length: n }, async () => {
    while (next < items.length) { const i = next++; out[i] = await fn(items[i], i); }
  }));
  return out;
}
function render(pdf, page, dpi, crop) {
  const base = join(tmpdir(), `parhlou-align-${process.pid}-${page}-${dpi}-${crop ?? "full"}-${Math.random().toString(36).slice(2)}`);
  const args = ["-f", String(page), "-l", String(page), "-r", String(dpi), "-jpeg", "-jpegopt", "quality=85", "-singlefile"];
  if (crop) args.push("-x", "0", "-y", String(crop.y), "-W", String(crop.w), "-H", String(crop.h));
  execFileSync("pdftoppm", [...args, pdf, base]);
  const b64 = readFileSync(`${base}.jpg`).toString("base64");
  unlinkSync(`${base}.jpg`);
  return `data:image/jpeg;base64,${b64}`;
}
const image = (url) => ({ type: "input_image", image_url: url, detail: "high" });

/** "Ex 2.3 Q4(iv)" / "EXERCISE 2.3 Q 4 (iv)" / "Review Ex 2 Q1(i)" → one comparable key. */
export function refKey(s) {
  return s.toLowerCase().replace(/review\s*exercise/g, "reviewex").replace(/exercise/g, "ex").replace(/question/g, "q")
    .replace(/[^a-z0-9.()]/g, "").replace(/\(([ivxl]+)\)/g, "($1)");
}
const questionOf = (key) => key.replace(/\([^)]*\)$/, "").replace(/\([^)]*\)$/, "");
const REF_RULES = `Write every reference exactly as "Ex <n.m> Q<k>" or "Ex <n.m> Q<k>(<roman part>)", e.g. "Ex 2.3 Q4(iv)".
A unit whose exercise has no sub-number (e.g. "EXERCISE 8") is "Ex 8 Q<k>". A unit review exercise is "Review Ex <n> Q<k>" or "Review Ex <n> Q<k>(<part>)".
One item per lettered/roman part when parts have separate answers; a question without parts is just "Q<k>".
Write maths in plain Unicode (√, ², ³, ×, ÷, ±, ≠, ≤, ≥, ∈, ∪, ∩, π, θ, log, fractions as a/b). Copy answers exactly as printed; do not correct them.`;

// ── 1. The book's answer key, read twice ────────────────────────────────────────────────────────────────
const keyHeadings = (() => {
  // The exercise in effect at the top of each key page, from the earlier transcription (headings only).
  let current = "";
  const atStart = {};
  for (let p = 262; p <= 280; p++) {
    atStart[p] = current;
    const md = readFileSync(join(ROOT, "pages", `p${p}.md`), "utf8");
    for (const m of md.matchAll(/^\s*#*\s*(REVIEW EXERCISE\s*\d+|EXERCISE\s*\d+(?:\.\d+)?)/gim)) current = m[1].replace(/\s+/g, " ");
  }
  return atStart;
})();
const KEY_READ = `You read one page of the ANSWERS section at the back of a Pakistani Class 9 Mathematics textbook (Punjab, 2025).
List every answer printed on the image, in order, with the exercise and question it belongs to.
${REF_RULES}
If a question's answer is a figure/graph/table, write a short description starting "[figure]" or copy the table as text.
If text is cut off at the image edge, include only what is fully legible.`;
const keySchema = obj({ items: arr(obj({ ref: str, answer: str })) });
async function readKey() {
  const reads = await pool(KEY_PAGES, 6, async (p) => cached(`key-p${p}`, async () => {
    const ctx = keyHeadings[p] ? `The page continues ${keyHeadings[p]} from the previous page until a new heading appears.` : "";
    const full = await structured("key_read", `key p${p} A`, KEY_READ, [{ role: "user", content: [
      { type: "input_text", text: `Answer-key page (PDF page ${p}). ${ctx}` }, image(render(bookPdf, p, 220))] }], keySchema);
    // Second read: top and bottom halves at higher resolution (overlapping), so small print is legible.
    const W = 2100, H = 2775; // 504 × 666 pt at 300 dpi
    const halves = [];
    for (const [i, y] of [[1, 0], [2, 1250]]) {
      halves.push(await structured("key_read", `key p${p} B${i}`, KEY_READ, [{ role: "user", content: [
        { type: "input_text", text: `${i === 1 ? "Top" : "Bottom"} part of answer-key page (PDF page ${p}). ${ctx}${i === 2 ? " The top of this crop may repeat lines from the top part; the heading in effect may be any exercise listed on the top part — read headings carefully." : ""}` },
        image(render(bookPdf, p, 300, { y, w: W, h: 1525 }))] }], keySchema));
    }
    return { page: p, readA: full.items, readB: [...halves[0].items, ...halves[1].items] };
  }));
  const key = new Map(); // refKey → { a, b }
  const tail = (k) => /q\d+(?:\([a-z0-9]+\))*$/.exec(k)?.[0] ?? k;
  const near = (x, y) => { const n = (t) => t.replace(/\s+/g, ""); return n(x) === n(y) || n(x).includes(n(y)) || n(y).includes(n(x)); };
  for (const r of reads) {
    for (const it of r.readA) { const k = refKey(it.ref); const e = key.get(k) ?? { ref: it.ref }; e.a = e.a ? `${e.a}; ${it.answer}` : it.answer; key.set(k, e); }
    // A half-page crop may miss the exercise heading above it: take the exercise from the full-page read of the same
    // page when the question/part matches (the answer text decides between several candidates).
    const aKeys = r.readA.map((it) => ({ k: refKey(it.ref), answer: it.answer }));
    const usedA = new Set();
    for (const it of r.readB) {
      let k = refKey(it.ref);
      const cands = aKeys.filter((c) => tail(c.k) === tail(k) && !usedA.has(c.k));
      const pick = cands.find((c) => c.k === k && near(c.answer, it.answer)) ?? cands.find((c) => near(c.answer, it.answer))
        ?? (cands.some((c) => c.k === k) ? cands.find((c) => c.k === k) : cands.length === 1 ? cands[0] : null);
      if (pick) { k = pick.k; usedA.add(pick.k); }
      const e = key.get(k) ?? { ref: it.ref };
      if (!e.b) e.b = it.answer; else if (e.b !== it.answer) e.b = `${e.b}; ${it.answer}`;
      key.set(k, e);
    }
  }
  return key;
}

// ── 2. Independent solutions, page by page (units in parallel, pages in order within a unit) ──────────────
const INDEP_READ = `You read one page of a teacher-written solution booklet for a Pakistani Class 9 Mathematics textbook (Punjab, 2025).
For every exercise question or part whose solution ENDS on this page, give its reference and the final answer the solution reaches
(the concluding result only, not the working). For a proof write "proved: <statement>". For a construction or graph write "[figure] <what is drawn>".
For multiple-choice questions give the chosen option letter and its text.
${REF_RULES}
lastHeading = the exercise heading in effect at the bottom of the page (e.g. "Ex 2.3"), or the one given to you if none appears.
Skip title pages, adverts and examples worked in the textbook itself (only exercise questions).`;
const indepSchema = obj({ lastHeading: str, items: arr(obj({ ref: str, finalAnswer: str })) });
async function readIndep(unit) {
  const pdf = join(indepDir, `u${String(unit).padStart(2, "0")}.pdf`);
  if (!existsSync(pdf)) return [];
  const pages = Number(/Pages:\s+(\d+)/.exec(execFileSync("pdfinfo", [pdf]).toString())[1]);
  let heading = "";
  const items = [];
  for (let p = 1; p <= pages; p++) {
    const out = await cached(`indep-u${unit}-p${p}`, () => structured("indep_read", `indep u${unit} p${p}`, INDEP_READ, [{ role: "user", content: [
      { type: "input_text", text: `Unit ${unit} solutions, page ${p} of ${pages}. Exercise heading in effect at the top of this page: ${heading || "(none yet)"}.` },
      image(render(pdf, p, 130))] }], indepSchema, { effort: "low" }));
    heading = out.lastHeading || heading;
    items.push(...out.items);
  }
  return items;
}

// ── 3. Compare, decide, repair ───────────────────────────────────────────────────────────────────────────
const COMPARE = `You compare final answers to Class 9 maths exercise questions. For each item you get the question, OUR final answer,
two independent readings of the textbook's printed answer key (keyRead1, keyRead2; either may be empty or contain answers for neighbouring
parts — pick the part that belongs to THIS question) and an independent teacher's final answer ("indep", may be empty).
Return:
- key: the printed key answer for this exact question/part as best read (prefer where the two readings agree; "" if the key gives none).
- keyReadsAgree: the two readings agree for this part (true if only one has it but it is clearly legible).
- oursMatchesKey, indepMatchesKey, oursMatchesIndep: "yes", "no" or "na" (na when a side is empty or is a figure/proof that cannot be compared).
Mathematically equivalent forms match (2/4 = 1/2, x = 3 or {3}, 4.2 × 10⁻³ = 0.0042). Answers from four-figure log tables or rounding
agree within that precision (3 significant figures). Different sets of solutions, different signs or different numbers do NOT match.`;
const compareSchema = obj({ items: arr(obj({ id: str, key: str, keyReadsAgree: bool,
  oursMatchesKey: enumOf("yes", "no", "na"), indepMatchesKey: enumOf("yes", "no", "na"), oursMatchesIndep: enumOf("yes", "no", "na") })) });

const ARBITER = `You are the chief examiner for Class 9 Mathematics (Punjab board). Sources disagree about the final answer to the question below.
Solve it yourself carefully and completely from the question as printed, then say which final answer is right.
When page images are attached, read the question from the printed page yourself (our transcription may contain a misread).
winner: "ours", "key", "indep" or "none" (none = none of them is right, or the question is ambiguous/misprinted).
correctAnswer: the right final answer in the book's form. keyMisprint: true if the printed key is wrong.
keyApplies: false when the given key answer clearly belongs to a different question or part (for example it answers a
neighbouring part, or is a number that is not even the kind of answer this question asks for); then keyMisprint is false.
questionAsPrinted: when a page image shows the question differs from our transcription, the question exactly as printed (one part,
stating what to do, plain Unicode maths); otherwise "".
reason: one or two sentences a student could understand (no source names; say "your textbook's answer key").`;
const arbiterSchema = obj({ items: arr(obj({ id: str, winner: enumOf("ours", "key", "indep", "none"), correctAnswer: str, keyMisprint: bool, keyApplies: bool, questionAsPrinted: str, reason: str })) });

// Book pages that hold an exercise, from the page transcriptions: the page with its heading up to the next heading.
const PAGE_TEXT = Array.from({ length: 261 }, (_, i) => { const f = join(ROOT, "pages", `p${String(i + 1).padStart(3, "0")}.md`); return existsSync(f) ? readFileSync(f, "utf8") : ""; });
function exercisePages(source) {
  const m = /^(review\s*ex)\s*(\d+)|^ex\s*(\d+(?:\.\d+)?)/i.exec(source.trim());
  if (!m) return [];
  const heading = m[1] ? new RegExp(`REVIEW EXERCISE\\s*${m[2]}\\b`, "i") : new RegExp(`(^|\\n)\\s*#*\\s*EXERCISE\\s*${m[3].replace(".", "\\.")}(?![\\d.])`, "i");
  const start = PAGE_TEXT.findIndex((t) => heading.test(t));
  if (start < 0) return [];
  const pages = [start + 1];
  for (let p = start + 1; p < PAGE_TEXT.length && pages.length < 3; p++) {
    if (/(^|\n)\s*#*\s*(REVIEW )?EXERCISE\s*\d|(^|\n)#{1,2} \d+\.\d/.test(PAGE_TEXT[p])) break;
    pages.push(p + 1);
  }
  return pages;
}

const SOLVE = `You are a Class 9 maths teacher in Punjab writing model solutions exactly the way the textbook teaches (its method, notation and
layout), so a student copying your solution earns full marks in the board exam.
Each item gives the question and its verified correct final answer ("target"). Write the full working that reaches exactly that answer,
one line per step, no skipped steps. finalAnswer must equal the target (same form as the book). figure = "" unless it is a drawing.
accept = patterns that recognise a correct typed final answer (empty list for drawings and proofs).
${SYNTAX}`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, finalAnswer: str, figure: str, accept: arr(strs) })) });
const CHECK = `You are an independent Class 9 maths examiner. For each item, solve it yourself without trusting the candidate, then judge
candidateCorrect: the working and final answer are mathematically correct and complete. problem: one sentence on what is wrong, or "".`;
const checkSchema = obj({ items: arr(obj({ id: str, candidateCorrect: bool, problem: str })) });

function lookup(map, key) {
  if (map.has(key)) return map.get(key);
  const q = questionOf(key);
  return q !== key && map.has(q) ? map.get(q) : null;
}
const ROMAN = ["i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx"];
function numberingGaps(refs) {
  // Every exercise: questions 1…N without gaps; every question's parts (i)…(n) without gaps.
  const byEx = new Map();
  for (const r of refs) {
    const m = /^(.*?)q(\d+)((?:\([a-z0-9]+\))*)$/.exec(r);
    if (!m) continue;
    const ex = byEx.get(m[1]) ?? new Map();
    const parts = ex.get(Number(m[2])) ?? new Set();
    const part = /^\(([ivx]+)\)/.exec(m[3])?.[1];
    if (part) parts.add(part);
    ex.set(Number(m[2]), parts);
    byEx.set(m[1], ex);
  }
  const gaps = [];
  for (const [ex, qs] of byEx) {
    const max = Math.max(...qs.keys());
    for (let q = 1; q <= max; q++) {
      if (!qs.has(q)) { if (!(ex.startsWith("reviewex") && q === 1)) gaps.push(`${ex} q${q}`); continue; }
      const parts = qs.get(q);
      if (!parts.size) continue;
      const hi = Math.max(...[...parts].map((p) => ROMAN.indexOf(p)));
      for (let i = 0; i <= hi; i++) if (!parts.has(ROMAN[i])) gaps.push(`${ex} q${q}(${ROMAN[i]})`);
    }
  }
  return gaps;
}

async function alignUnit(unit, key) {
  const sol = JSON.parse(readFileSync(join(ROOT, "units", `u-${unit}-solutions.json`), "utf8"));
  const items = sol.sections.flatMap((s) => s.solutions.map((x) => ({ ...x, section: s.number })))
    .filter((x) => x.kind === "EXERCISE").map((x, i) => ({ ...x, id: String(i), ref: refKey(x.source) }));
  const indepItems = await readIndep(unit);
  const indep = new Map();
  for (const it of indepItems) { const k = refKey(it.ref); if (!indep.has(k)) indep.set(k, it.finalAnswer); }
  const unitPrefix = (k) => k.startsWith(`ex${unit}.`) || k.startsWith(`ex${unit}q`) || k.startsWith(`reviewex${unit}q`);

  // Compare in batches.
  const rows = items.map((x) => {
    const k = lookup(key, x.ref);
    return { id: x.id, question: x.question, ours: x.answer, oursChecked: x.checked, keyRead1: k?.a ?? "", keyRead2: k?.b ?? "", indep: lookup(indep, x.ref) ?? "" };
  });
  const cmp = new Map();
  for (let b = 0; b < rows.length; b += 20) {
    const out = await cached(`cmp-u${unit}-${b / 20 + 1}`, () => structured("compare", `u${unit} cmp ${b / 20 + 1}`, COMPARE, JSON.stringify(rows.slice(b, b + 20)), compareSchema));
    for (const v of out.items) cmp.set(v.id, v);
  }

  // Disputes: ours ≠ key, or no key and ours ≠ indep, or ours was never checked.
  const disputes = items.filter((x) => {
    const c = cmp.get(x.id);
    if (!c) return false;
    if (!x.checked) return true;
    if (c.key && c.oursMatchesKey === "no") return true;
    return !c.key && c.oursMatchesIndep === "no";
  });
  const verdicts = new Map();
  const arbInput = (x) => ({ id: x.id, source: x.source, question: x.question, ours: { steps: x.steps, finalAnswer: x.answer },
    key: cmp.get(x.id).key, indep: rows[Number(x.id)].indep });
  // When the key and the independent solution agree against us, our reading of the question itself may be wrong:
  // those go one at a time with the printed exercise pages attached.
  const twoAgainst = (x) => cmp.get(x.id).indepMatchesKey === "yes" && cmp.get(x.id).oursMatchesKey === "no";
  const plain = disputes.filter((x) => !twoAgainst(x));
  for (let b = 0; b < plain.length; b += 6) {
    const batch = plain.slice(b, b + 6).map(arbInput);
    const ids = batch.map((x) => x.id).join("-");
    const out = await cached(`arb3-u${unit}-${ids}`, () => structured("arbiter", `u${unit} arb ${ids}`, ARBITER, JSON.stringify(batch), arbiterSchema, { effort: "high" }));
    for (const v of out.items) verdicts.set(v.id, v);
  }
  for (const x of disputes.filter(twoAgainst)) {
    const pages = exercisePages(x.source);
    const out = await cached(`arb3-u${unit}-p${x.id}`, () => structured("arbiter", `u${unit} arb page ${x.id}`, ARBITER, [{ role: "user", content: [
      { type: "input_text", text: JSON.stringify([arbInput(x)]) + (pages.length ? `\nPrinted textbook pages holding this exercise follow (book PDF pages ${pages.join(", ")}).` : "") },
      ...pages.map((p) => image(render(bookPdf, p, 150)))] }], arbiterSchema, { effort: "high" }));
    for (const v of out.items) verdicts.set(v.id, v);
  }

  // Re-solve where our answer lost, then re-check.
  const redo = disputes.filter((x) => { const v = verdicts.get(x.id); return v && v.winner !== "ours" && v.winner !== "none"; });
  const fixed = new Map();
  for (let b = 0; b < redo.length; b += 6) {
    const batch = redo.slice(b, b + 6).map((x) => ({ id: x.id, question: verdicts.get(x.id).questionAsPrinted || x.question, target: verdicts.get(x.id).correctAnswer }));
    const out = await cached(`fix3-u${unit}-${redo.slice(b, b + 6).map((x) => x.id).join("-")}`, () => structured("solutions", `u${unit} fix ${b / 6 + 1}`, SOLVE, JSON.stringify(batch), solveSchema, { maxTokens: 40000 }));
    const chk = await cached(`fixchk3-u${unit}-${redo.slice(b, b + 6).map((x) => x.id).join("-")}`, () => structured("solution_check", `u${unit} fixchk ${b / 6 + 1}`, CHECK,
      JSON.stringify(out.items.map((s) => ({ id: s.id, question: verdicts.get(s.id).questionAsPrinted || items[Number(s.id)].question, candidateSteps: s.steps, candidateFinalAnswer: s.finalAnswer }))), checkSchema));
    const ok = new Map(chk.items.map((c) => [c.id, c]));
    for (const s of out.items) if (ok.get(s.id)?.candidateCorrect) fixed.set(s.id, s);
  }

  // Result per question.
  const result = [];
  for (const x of items) {
    const c = cmp.get(x.id), v = verdicts.get(x.id), f = fixed.get(x.id);
    const bookAnswer = v && v.keyApplies === false ? "" : (c?.key ?? "");
    let status, note = x.note, replace = null;
    if (!c) status = "not compared";
    else if (!disputes.includes(x)) status = bookAnswer ? "matches key" : (c.oursMatchesIndep === "yes" ? "confirmed" : "no key");
    else if (v?.winner === "ours") {
      status = bookAnswer && c.oursMatchesKey === "no" ? "key misprint" : "confirmed";
      note = status === "key misprint" ? `Your textbook's answer key shows ${bookAnswer}. ${v.reason}` : "";
      if (!x.checked) replace = { checked: true };
    } else if (f) {
      status = v.keyMisprint ? "corrected (key misprint)" : "corrected";
      note = v.keyMisprint && bookAnswer ? `Your textbook's answer key shows ${bookAnswer}. ${v.reason}` : "";
      replace = { steps: f.steps, answer: f.finalAnswer, figure: f.figure, accept: f.accept, checked: true,
        ...(v.questionAsPrinted ? { question: v.questionAsPrinted } : {}) };
    } else {
      status = "unresolved";
      note = bookAnswer ? "" : x.note;
      replace = { checked: false };
    }
    result.push({ source: x.source, section: x.section, bookAnswer, keyReadsAgree: c?.keyReadsAgree ?? null, indep: rows[Number(x.id)].indep,
      status, note, ...(replace ? { replace } : {}), ...(v ? { examiner: v.reason } : {}) });
  }
  const ourRefs = items.map((x) => x.ref);
  const indepOnly = [...indep.keys()].filter((k) => unitPrefix(k) && !ourRefs.includes(k) && !ourRefs.some((r) => questionOf(r) === k || questionOf(k) === r)
    && !/^reviewex\d+q1(\(|$)/.test(k));
  const report = { unit, counts: Object.fromEntries(Object.entries(result.reduce((a, r) => ({ ...a, [r.status]: (a[r.status] ?? 0) + 1 }), {}))),
    numberingGaps: numberingGaps(ourRefs), inIndependentNotOurs: indepOnly, items: result };
  writeFileSync(join(ROOT, "units", `u-${unit}-align.json`), JSON.stringify(report, null, 1));
  console.log(`unit ${unit}: ${JSON.stringify(report.counts)} gaps=${report.numberingGaps.length} indepOnly=${indepOnly.length} total $${costSoFar().toFixed(3)}`);
  return report;
}

const key = await readKey();
console.log(`answer key: ${key.size} entries, total $${costSoFar().toFixed(3)}`);
writeFileSync(join(WORK, "align-key-merged.json"), JSON.stringify(Object.fromEntries(key), null, 1));
await pool(UNITS, 5, (u) => alignUnit(u, key).catch((e) => console.log(`unit ${u}: FAILED ${e.message}`)));
