// Full content audit of the Class 9 Maths course against the printed textbook pages (images, not the earlier OCR text).
//   node --use-env-proxy scripts/audit-content.mjs <book.pdf>
// 1. Proof-read: for every exercise (and the worked examples before it), two independent readers compare the app's items
//    with the page images: missing items, extra items, and any difference in a word, number, sign, symbol or label
//    (question text, MCQ stem and options, and the book's own steps of worked examples).
// 2. Settle: fixes both readers agree on are accepted; anything else goes to an arbiter with the pages.
// 3. Steps: every exercise solution (the app's own working) is checked by an examiner; wrong ones are re-solved and
//    re-checked; anything still unsettled is listed for a teacher.
// Writes content/class9-maths/audit-fixes.json (applied by assemble-course.mjs) and docs/content-audit-class9-maths.md.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { luna, costSoFar } from "./luna-budget.mjs";

import { S } from "./subject.mjs";
const PHY = S.key === "physics";
const IDP = S.idPrefix;
const ROOT = S.root;
// The audit can read a newer printing whose pages are shifted (Physics: the PECTAA printing has two syllabus pages first).
const PAGE_OFFSET = Number(process.env.PAGE_OFFSET ?? 0);
const WORK = join(ROOT, "work", "audit");
mkdirSync(WORK, { recursive: true });
const [pdf] = process.argv.slice(2);
if (!pdf || !existsSync(pdf)) throw new Error("usage: audit-content.mjs <book.pdf>");
const bundle = JSON.parse(readFileSync(join(ROOT, "bundle.json"), "utf8"));
const str = { type: "string" }, strs = { type: "array", items: { type: "string" } }, int = { type: "integer" }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });

async function cached(name, run) {
  const f = join(WORK, `${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const r = await run();
  writeFileSync(f, JSON.stringify(r));
  return r;
}
async function structured(name, item, instructions, input, schema, effort = "medium", maxTokens = 40000) {
  const text = await luna({ max_output_tokens: maxTokens, reasoning: { effort }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `audit-${name}`, item);
  return JSON.parse(text);
}
async function pool(items, n, fn) {
  let next = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (next < items.length) { const i = next++; await fn(items[i], i); } }));
}
function page(n) {
  const base = join(tmpdir(), `audit-${n}`);
  n += PAGE_OFFSET;
  execFileSync("pdftoppm", ["-f", String(n), "-l", String(n), "-r", "150", "-jpeg", "-jpegopt", "quality=85", "-singlefile", pdf, base]);
  const b = readFileSync(`${base}.jpg`); unlinkSync(`${base}.jpg`);
  return `data:image/jpeg;base64,${b.toString("base64")}`;
}

// ── Where each exercise is printed (from the page headings), and what the app has for it ─────────────────────────
const pagesDir = join(ROOT, "pages");
const headings = [];
for (const f of readdirSync(pagesDir).filter((f) => /^p\d+\.md$/.test(f)).sort()) {
  const p = Number(f.slice(1, 4));
  if (p > 259) continue; // answer keys and the glossary follow
  for (const line of readFileSync(join(pagesDir, f), "utf8").split("\n")) {
    const m = /^#{0,4}\s*(REVIEW\s+)?EXERCISE\s+(\d+(?:\.\d+)?)/i.exec(line.trim());
    if (m) headings.push({ page: p, review: !!m[1], id: m[1] ? `R${m[2]}` : m[2] });
  }
}
const label = (h) => PHY ? (h.id.endsWith("E") ? `Chapter ${h.id.slice(0, -1)} worked examples` : `Chapter ${h.id} exercise`)
  : (h.review ? `Review Exercise ${h.id.slice(1)}` : `Exercise ${h.id}`);
// App items keyed by the book label they come from.
const items = [];
for (const c of bundle.subject.chapters) for (const m of c.modules) {
  const sec = /^(\d+\.\d+)/.exec(m.title)?.[1] ?? String(c.number);
  // Exercise labels are unique ("Ex 2.3 Q4(v)"); examples are numbered again in every section, so they carry their section.
  m.solutions.forEach((s, i) => items.push({ unit: c.number, module: m.id, index: i, kind: s.kind,
    source: s.kind === "EXAMPLE" && !PHY ? `${sec} ${s.source}` : s.source, question: s.question, steps: s.steps ?? [], answer: s.answer ?? "", bookAnswer: s.bookAnswer ?? "" }));
}
// The book's own MCQs ("b-<unit>-..."), printed in each unit's review exercise; labelled by their id.
const mcqItems = bundle.quiz.filter((q) => new RegExp(`^${IDP}b-\\d+-`).test(q.id) && !/-m-\d/.test(q.id) && !/-phy-m-\d/.test(q.id))
  .map((q) => ({ id: q.id, unit: Number(q.id.slice(IDP.length).split("-")[1]), source: q.id, stem: q.stem, options: q.options }));
const belongs = (src, h) => {
  if (h.review) return new RegExp(`^Review Ex(ercise)? ${h.id.slice(1)}\\b`).test(src);
  return new RegExp(`^Ex(ercise)? ${h.id.replace(".", "\\.")}\\b`).test(src) || (h.id.indexOf(".") < 0 && new RegExp(`^Ex(ercise)? ${h.id}\\b(?!\\.)`).test(src));
};
// Physics: per chapter, the end-of-chapter exercise (Sections A–E, with the book's MCQs) and the pages with worked examples.
function physicsBlocks() {
  const pagesOf = (re) => readdirSync(pagesDir).filter((f) => /^p\d+\.md$/.test(f)).sort().map((f) => ({ p: Number(f.slice(1, 4)), t: readFileSync(join(pagesDir, f), "utf8") }))
    .filter((x) => re.test(x.t)).map((x) => x.p);
  const out = [];
  for (const c of bundle.subject.chapters) {
    const u = c.number;
    const start = pagesOf(new RegExp(`^#\\s*Chapter\\s*${u}\\b`, "mi"))[0];
    const nextStart = pagesOf(new RegExp(`^#\\s*Chapter\\s*${u + 1}\\b`, "mi"))[0] ?? 194;
    const secA = pagesOf(/^#{0,4}\s*SECTION A\b/im).find((p) => p >= start && p < nextStart);
    const unitItems = items.filter((x) => x.unit === u);
    if (secA) out.push({ h: { id: `${u}`, review: true, page: secA }, unit: u, exPages: Array.from({ length: Math.min(nextStart - secA, 5) }, (_, k) => secA + k),
      examplePages: [], exItems: unitItems.filter((x) => x.kind === "EXERCISE"), exampleItems: [], mcqs: mcqItems.filter((q) => q.unit === u) });
    const exPages = pagesOf(/^\**\s*EXAMPLE\s+\d/im).filter((p) => p >= start && p < (secA ?? nextStart));
    const examples = unitItems.filter((x) => x.kind === "EXAMPLE");
    if (examples.length && exPages.length) out.push({ h: { id: `${u}E`, review: false, page: exPages[0] }, unit: u, exPages: [], examplePages: exPages.slice(0, 10),
      exItems: [], exampleItems: examples, mcqs: [] });
  }
  return out;
}
const blocks = PHY ? physicsBlocks() : headings.map((h, i) => {
  const next = headings[i + 1];
  const prev = headings[i - 1];
  const end = Math.min(next ? Math.max(next.page, h.page) : h.page + 2, h.page + (h.review ? 3 : 4));
  const exPages = Array.from({ length: end - h.page + 1 }, (_, k) => h.page + k);
  // Worked examples are printed between the previous exercise and this one (same unit only).
  const unit = Number(h.id.replace("R", "").split(".")[0]);
  // The unit starts on the page after the previous unit's review exercise (pages of the previous unit are left out).
  const exStart = prev ? (Number(prev.id.replace("R", "").split(".")[0]) === unit ? prev.page : prev.page + (prev.review ? 2 : 1)) : 1;
  const exItems = items.filter((x) => x.kind === "EXERCISE" && belongs(x.source, h));
  const exampleItems = h.review ? [] : items.filter((x) => x.kind === "EXAMPLE" && x.unit === unit && (x.source.startsWith(`${h.id} `) || !h.id.includes(".")));
  return { h, unit, exPages, examplePages: h.review ? [] : Array.from({ length: Math.min(h.page - exStart + 1, 16) }, (_, k) => exStart + k), exItems, exampleItems,
    mcqs: h.review ? mcqItems.filter((q) => q.unit === Number(h.id.slice(1))) : [] };
});
// Examples of a section with no exercise of its own (e.g. 5.3), and book MCQs of a unit with no review heading, go with the
// unit's last block, whose pages are widened back to the previous exercise.
if (!PHY) {
  const placed = new Set(blocks.flatMap((b) => b.exampleItems.map((x) => x.source)));
  const placedMcq = new Set(blocks.flatMap((b) => b.mcqs.map((q) => q.id)));
  for (const u of new Set(blocks.map((b) => b.unit))) {
    const own = blocks.filter((b) => b.unit === u);
    const last = own.at(-1);
    const lostEx = items.filter((x) => x.kind === "EXAMPLE" && x.unit === u && !placed.has(x.source));
    const lostMcq = mcqItems.filter((q) => q.unit === u && !placedMcq.has(q.id));
    if (lostEx.length) {
      const from = own.length > 1 ? own.at(-2).h.page : last.h.page;
      last.exampleItems.push(...lostEx);
      last.examplePages = Array.from({ length: Math.min(last.h.page - from + 1, 16) }, (_, k) => from + k);
    }
    last.mcqs.push(...lostMcq);
  }
}
// An example belongs to the exercise that follows its section (examples of section 2.3 sit before Exercise 2.3).
function exampleSection(x, h, prev) {
  const mod = bundle.subject.chapters.flatMap((c) => c.modules).find((m) => m.id === x.module);
  const sec = /^(\d+\.\d+)/.exec(mod?.title ?? "")?.[1];
  return sec ? sec === h.id : false;
}

if (process.env.DRY) {
  for (const b of blocks) console.log(label(b.h).padEnd(20), "pages", b.exPages.join(","), "| ex", b.exItems.length, "| examples", b.exampleItems.length, "(pages", b.examplePages[0] + "-" + b.examplePages.at(-1) + ")", "| mcq", b.mcqs.length);
  const covered = new Set(blocks.flatMap((b) => [...b.exItems, ...b.exampleItems].map((x) => x.source)));
  const orphans = items.filter((x) => !covered.has(x.source));
  console.log("items", items.length, "covered", covered.size, "orphans", orphans.length, orphans.slice(0, 12).map((x) => x.kind + ":" + x.source + " @" + x.module).join(" ; "));
  console.log("mcq total", mcqItems.length, "matched", blocks.reduce((s, b) => s + b.mcqs.length, 0), mcqItems.slice(0, 2));
  process.exit(0);
}
const CONVENTIONS = `${PHY ? "Pages may show items crossed out: that is only the smart-syllabus marking, NOT a deletion; those items stay in the app. Units, powers of ten and subscripts must match the book (m s⁻¹, 3.0 × 10⁸, v_f). " : ""}The app's conventions (NOT errors): each exercise part is written as the question's instruction, ": ", then the part
(e.g. "Express the following numbers in scientific notation: 2000000"); maths is plain Unicode on one line (√, ², ³, ⁻¹, ×, ÷, −, π, θ,
fractions as a/b with brackets where needed); a figure is described in [square brackets]; line breaks, bold and layout are ignored.
Everything else must match the book exactly: every word, number, sign, operator, variable, unit, subscript/superscript, label and option.`;
const READ = `You proof-read a ${S.short} learning app against the printed pages of the student's textbook (images). ${CONVENTIONS}
You get the app's items for one exercise of the book (and the worked examples printed before it).
Report:
App labels are given; use them exactly when you refer to an app item. Worked examples are labelled ${PHY ? `"Example <n.m>"` : `"<section> Example <n>"`}; the
book's multiple-choice questions are labelled by an id and are in the same order as printed.
- missing: anything printed in this exercise (every question and every part) or in the worked examples on these pages that the app does
  not have. label as the book numbers it (${PHY ? `"Numerical 2.3(a)", "Short 1.4", "CRQ 3.2", "Comprehensive 4.1", "MCQ 2.5", "Example 2.3"` : `"Ex 2.3 Q4(v)", "Review Ex 2 Q1(iii)", "Example 3"`}), kind EXERCISE / EXAMPLE / MCQ,
  text in the app's convention, options for an MCQ (else []), steps = the book's own worked solution lines for an EXAMPLE (else []).
- extra: labels of app items that are not in the book at all.
- wrong: app items whose text differs from the book in any way; field = question / stem / options / steps; corrected = the full corrected
  text in the app's convention (for options or steps: the corrected list, one entry per option/line, in correctedList); difference =
  one short line saying exactly what differs. Only compare worked-example steps with the book's printed solution lines.
Look at the pages carefully; never report a difference you can't see on the page.`;
const readSchema = obj({
  missing: arr(obj({ label: str, kind: { type: "string", enum: ["EXERCISE", "EXAMPLE", "MCQ"] }, text: str, options: strs, steps: strs })),
  extra: strs,
  wrong: arr(obj({ label: str, field: { type: "string", enum: ["question", "stem", "options", "steps"] }, corrected: str, correctedList: strs, difference: str })),
});
const ARBITER = `You are the final judge between two proof-readers of a ${S.short} learning app, looking at the printed textbook pages yourself.
${CONVENTIONS}
For each disputed point decide what the book really says. verdict: "app_correct" (no change), "fix" (use corrected / correctedList, in the
app's convention), "add" (a missing item; give text, options, steps), "remove" (the app item isn't in the book). reason: one short line.`;
const arbSchema = obj({ decisions: arr(obj({ key: str, verdict: { type: "string", enum: ["app_correct", "fix", "add", "remove"] },
  corrected: str, correctedList: strs, text: str, options: strs, steps: strs, reason: str })) });

const norm = (t) => String(t ?? "").normalize("NFKC").replace(/[−–—]/g, "-").replace(/\s+/g, " ").replace(/[“”]/g, '"').replace(/[‘’]/g, "'").trim().toLowerCase();
const normList = (l) => (l ?? []).map(norm).join("|");

const fixes = { questions: {}, steps: {}, mcq: {}, add: [], remove: [] };
const report = [];

await pool(blocks.filter((b) => b.exItems.length || b.mcqs.length || b.exampleItems.length || true), 6, async (b, bi) => {
  const pages = [...new Set([...b.examplePages, ...b.exPages])].sort((x, y) => x - y);
  const appList = [
    ...b.exampleItems.map((x) => ({ label: x.source, kind: "EXAMPLE", text: x.question, steps: x.steps })),
    ...b.exItems.map((x) => ({ label: x.source, kind: "EXERCISE", text: x.question })),
    ...b.mcqs.map((q) => ({ label: q.source, kind: "MCQ", text: q.stem, options: q.options })),
  ];
  const input = (extra = "") => [{ role: "user", content: [
    { type: "input_text", text: `${label(b.h)} of ${S.unitWord} ${b.unit}, printed on book pages ${pages.join(", ")} (images follow).${extra}\n\nAPP ITEMS:\n${JSON.stringify(appList)}` },
    ...pages.map((p) => ({ type: "input_image", image_url: page(p), detail: "high" })),
  ] }];
  const a = await cached(`read-a-${b.h.id}`, () => structured("proofread", `${b.h.id} a`, READ, input(), readSchema, "medium"));
  const c = await cached(`read-b-${b.h.id}`, () => structured("proofread", `${b.h.id} b`, `${READ}\nWork independently and carefully; another proof-reader will be compared with you.`, input(), readSchema, "high"));

  // Agreement: same label and field, same corrected text → accepted. Everything else → the arbiter.
  const disputes = [];
  const key = (w) => `${w.label}|${w.field}`;
  const bWrong = new Map(c.wrong.map((w) => [key(w), w]));
  const aWrong = new Map(a.wrong.map((w) => [key(w), w]));
  const accepted = [];
  for (const [k, w] of aWrong) {
    const v = bWrong.get(k);
    if (v && norm(v.corrected) === norm(w.corrected) && normList(v.correctedList) === normList(w.correctedList)) accepted.push(w);
    else disputes.push({ key: `wrong:${k}`, readerA: w, readerB: v ?? "no difference seen" });
  }
  for (const [k, v] of bWrong) if (!aWrong.has(k)) disputes.push({ key: `wrong:${k}`, readerA: "no difference seen", readerB: v });
  const am = new Map(a.missing.map((m) => [m.label, m])), bm = new Map(c.missing.map((m) => [m.label, m]));
  for (const l of new Set([...am.keys(), ...bm.keys()])) disputes.push({ key: `missing:${l}`, readerA: am.get(l) ?? "not missing", readerB: bm.get(l) ?? "not missing" });
  for (const l of new Set([...a.extra, ...c.extra])) disputes.push({ key: `extra:${l}`, readerA: a.extra.includes(l) ? "not in book" : "in book", readerB: c.extra.includes(l) ? "not in book" : "in book",
    appItem: appList.find((x) => x.label === l) ?? null });

  let decisions = [];
  if (disputes.length) {
    const out = await cached(`arb-${b.h.id}`, () => structured("arbitrate", `${b.h.id} arb`, ARBITER,
      [{ role: "user", content: [
        { type: "input_text", text: `${label(b.h)} of ${S.unitWord} ${b.unit}, pages ${pages.join(", ")}.\nAPP ITEMS:\n${JSON.stringify(appList)}\n\nDISPUTED POINTS:\n${JSON.stringify(disputes)}` },
        ...pages.map((p) => ({ type: "input_image", image_url: page(p), detail: "high" })),
      ] }], arbSchema, "high"));
    decisions = out.decisions;
  }
  const apply = (labelText, field, corrected, list, why) => {
    const isMcq = field === "stem" || field === "options";
    if (isMcq) (fixes.mcq[labelText] ??= {})[field] = field === "options" ? list : corrected;
    else if (field === "steps") fixes.steps[labelText] = list;
    else fixes.questions[labelText] = corrected;
    report.push({ unit: b.unit, exercise: label(b.h), label: labelText, change: field, why });
  };
  for (const w of accepted) apply(w.label, w.field, w.corrected, w.correctedList, w.difference);
  const pending = [];
  for (const d of decisions) {
    const [type, rest] = [d.key.slice(0, d.key.indexOf(":")), d.key.slice(d.key.indexOf(":") + 1)];
    if (d.verdict === "fix" && type === "wrong") { const [l, f] = rest.split("|"); apply(l, f, d.corrected, d.correctedList, d.reason); }
    else if (d.verdict === "add") {
      const m = am.get(rest) ?? bm.get(rest);
      fixes.add.push({ unit: b.unit, exercise: b.h.id, label: rest, kind: m?.kind ?? "EXERCISE", text: d.text || m?.text, options: d.options.length ? d.options : m?.options ?? [], steps: d.steps.length ? d.steps : m?.steps ?? [] });
      report.push({ unit: b.unit, exercise: label(b.h), label: rest, change: "added (was missing)", why: d.reason });
    } else if (d.verdict === "remove") {
      fixes.remove.push(rest);
      report.push({ unit: b.unit, exercise: label(b.h), label: rest, change: "removed (not in the book)", why: d.reason });
    } else if (d.verdict !== "app_correct") pending.push(d);
  }
  console.log(`${label(b.h)}: ${appList.length} items · ${accepted.length} agreed fixes · ${disputes.length} disputes. $${costSoFar().toFixed(3)}`);
});

// ── Steps: every exercise solution checked by an examiner; wrong ones re-solved and checked again ──────────────────
const CHECK = PHY ? `You are an independent Class 9 physics examiner (Punjab board). For each item, answer it yourself, then judge the app's answer:
ok = for a numerical: correct formula, substitution with units, every step follows, and the final value AND unit are right (and agree
with bookAnswer when one is given, to 3 significant figures or as asked); for a theory question: every point is scientifically correct
and the answer covers what is asked, as the textbook teaches it.` : `You are an independent Class 9 maths examiner (Punjab board). For each item, solve it yourself, then judge the app's working:
ok = every step follows correctly from the one before, nothing essential is skipped, and the final answer is right (and agrees with
bookAnswer when one is given; log-table answers within table precision and answers rounded as asked are fine).
problem: one short line saying what is wrong, or "".`;
const checkSchema = obj({ items: arr(obj({ id: str, ok: bool, problem: str })) });
const SOLVE = `You are a Class 9 ${S.short} teacher in Punjab writing a model ${PHY ? "answer" : "solution"} exactly the way the textbook teaches it, so a student
copying it earns full marks. steps = the full working, one line per step, plain Unicode ${PHY ? "maths and units (numericals: given, formula, substitution with units, answer with unit; theory: the textbook's points)" : "maths"}; answer = the final result (must agree with
bookAnswer when one is given). A previous solution was rejected for the reason given.`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, answer: str })) });
const exercise = items.filter((x) => x.kind === "EXERCISE" && x.steps.length && !fixes.remove.includes(x.source))
  .map((x, i) => ({ ...x, id: String(i), question: fixes.questions[x.source] ?? x.question }));
const verdicts = new Map();
const batches = [];
for (let i = 0; i < exercise.length; i += 10) batches.push(exercise.slice(i, i + 10));
await pool(batches, 8, async (batch, bi) => {
  const out = await cached(`check-${bi}`, () => structured("check", `check ${bi}`, CHECK,
    JSON.stringify(batch.map((x) => ({ id: x.id, question: x.question, steps: x.steps, answer: x.answer, bookAnswer: x.bookAnswer }))), checkSchema, "medium"));
  for (const v of out.items) verdicts.set(v.id, v);
});
const bad = exercise.filter((x) => verdicts.get(x.id)?.ok === false);
const unsettled = [];
const reb = [];
for (let i = 0; i < bad.length; i += 6) reb.push(bad.slice(i, i + 6));
await pool(reb, 6, async (batch, bi) => {
  const sol = await cached(`resolve-${bi}`, () => structured("resolve", `resolve ${bi}`, SOLVE,
    JSON.stringify(batch.map((x) => ({ id: x.id, question: x.question, bookAnswer: x.bookAnswer, rejectedBecause: verdicts.get(x.id).problem }))), solveSchema, "high"));
  const again = await cached(`recheck-${bi}`, () => structured("check", `recheck ${bi}`, CHECK,
    JSON.stringify(sol.items.map((s) => { const x = batch.find((y) => y.id === s.id); return { id: s.id, question: x?.question, steps: s.steps, answer: s.answer, bookAnswer: x?.bookAnswer }; })), checkSchema, "high"));
  for (const s of sol.items) {
    const x = batch.find((y) => y.id === s.id); if (!x) continue;
    if (again.items.find((v) => v.id === s.id)?.ok) {
      fixes.steps[x.source] = s.steps; (fixes.answers ??= {})[x.source] = s.answer;
      report.push({ unit: x.unit, exercise: x.source.replace(/ Q.*/, ""), label: x.source, change: "solution re-worked", why: verdicts.get(x.id).problem });
    } else unsettled.push({ unit: x.unit, label: x.source, why: verdicts.get(x.id).problem });
  }
});

writeFileSync(join(ROOT, "audit-fixes.json"), JSON.stringify(fixes, null, 1));
// ── Report ───────────────────────────────────────────────────────────────────────────────────────────────────────
const units = [...new Set(blocks.map((b) => b.unit))].sort((x, y) => x - y);
const lines = [`# Class 9 ${S.name}: content audit against the textbook`, "",
  `Every exercise, worked example and ${PHY ? "book MCQ" : "review MCQ"} was proof-read against the printed pages by two independent readers, with an arbiter for anything they didn't agree on; every exercise solution was checked by an examiner and re-worked where needed. AI cost so far: $${costSoFar().toFixed(2)}.`, "",
  `| ${S.unitWord} | Exercises | Examples | Exercise parts | ${PHY ? "Book" : "Review"} MCQs | Fixes |`, "|---|---|---|---|---|---|"];
for (const u of units) {
  const bs = blocks.filter((b) => b.unit === u);
  const fixesU = report.filter((r) => r.unit === u).length;
  lines.push(`| ${u} | ${PHY ? "Sections A–E" : `${bs.filter((b) => !b.h.review).length} + review`} | ${new Set(bs.flatMap((b) => b.exampleItems.map((x) => x.source))).size} | ${bs.reduce((s, b) => s + b.exItems.length, 0)} | ${bs.reduce((s, b) => s + b.mcqs.length, 0)} | ${fixesU} |`);
}
lines.push("", "## Every change", "", `| ${S.unitWord} | Where | Item | Change | Why |`, "|---|---|---|---|---|");
for (const r of report.sort((x, y) => x.unit - y.unit || x.label.localeCompare(y.label, undefined, { numeric: true }))) lines.push(`| ${r.unit} | ${r.exercise} | ${r.label} | ${r.change} | ${String(r.why).replace(/\|/g, "/")} |`);
lines.push("", "## For a teacher to confirm", "", unsettled.length ? "" : "Nothing: every item was settled.");
for (const u of unsettled) lines.push(`- ${S.unitWord} ${u.unit}, ${u.label}: ${u.why}`);
writeFileSync(`docs/content-audit-class9-${S.key}.md`, lines.join("\n") + "\n");
console.log(`Audit done: ${report.length} changes, ${unsettled.length} for a teacher. $${costSoFar().toFixed(3)}`);
