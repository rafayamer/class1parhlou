// Full book coverage for one unit: every worked example, every exercise question (each part) and every book MCQ,
// each with a step-by-step solution in the book's method, checked against the book's answer key and by an
// independent re-solve. Run after build-unit.mjs (it reads that unit's section split).
//   node --use-env-proxy scripts/build-solutions.mjs <unit number>
// Writes content/class9-maths/units/u-<n>-solutions.json and a coverage report. Stages are cached in work/.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { matchesAny, SYNTAX } from "./textmatch.mjs";

const ROOT = "content/class9-maths";
const UNIT = Number(process.argv[2]);
const outline = JSON.parse(readFileSync(join(ROOT, "work", `u${UNIT}-outline.json`), "utf8"));
const cache = (name) => join(ROOT, "work", `u${UNIT}-sol-${name}.json`);
async function stage(name, run) {
  const f = cache(name);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const result = await run();
  writeFileSync(f, JSON.stringify(result, null, 1));
  return result;
}
async function structured(name, instructions, input, schema, maxTokens = 40000) {
  const text = await luna({ max_output_tokens: maxTokens, reasoning: { effort: "medium" }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, name, `unit ${UNIT}`);
  return JSON.parse(text);
}
const str = { type: "string" }, strs = { type: "array", items: str }, bool = { type: "boolean" }, int = { type: "integer" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });

// ── Count what the book contains, straight from the page text (no AI) ─────────────────────────────────────
const ROMAN = /\((?:i|ii|iii|iv|v|vi|vii|viii|ix|x|xi|xii|xiii|xiv|xv|xvi|xvii|xviii|xix|xx)\)/g;
function bookCounts(text) {
  const lines = text.split("\n");
  let inExercise = false, questions = 0, parts = 0, partsInQuestion = 0, examples = 0;
  const close = () => { parts += Math.max(1, partsInQuestion); partsInQuestion = 0; };
  for (const raw of lines) {
    const line = raw.trim();
    if (/^EXAMPLE\s+\d+/i.test(line)) examples++;
    if (/^#{0,4}\s*(REVIEW\s+)?EXERCISE\b/i.test(line)) { inExercise = true; continue; }
    if (/^#{1,4}\s*\d+\.\d+/.test(line) || /^EXAMPLE\s+\d+/i.test(line)) { if (inExercise && questions) close(); inExercise = false; questions = 0; continue; }
    if (!inExercise) continue;
    if (/^(Q\s*)?\d{1,2}\.\s/.test(line)) { if (questions) close(); questions++; }
    if (questions) partsInQuestion += (line.match(ROMAN) ?? []).length;
  }
  if (inExercise && questions) close();
  return { examples, exerciseItems: parts };
}

// ── 1. Inventory: every example, exercise part and MCQ, verbatim ──────────────────────────────────────────
const INVENTORY = `You list EVERYTHING in one section of a Pakistani Class 9 Mathematics textbook, copying the book's words exactly.
- examples: EVERY worked example ("EXAMPLE n"), with its question, the book's solution as one line per step, and its final answer.
- exercise: EVERY exercise question and EVERY part separately ((i), (ii), ... each its own item). "question" must be complete on its own:
  the question's instruction plus the part, e.g. "Rationalise the denominator: 3/(√5 − √2)". "source" like "Ex ${UNIT}.2 Q3(ii)" or "Review Ex ${UNIT} Q4".
  bookAnswer: the book's answer from the answer key when given, else "". drawing: true if it needs a graph, number line, construction or diagram.
  Multiple-choice items are NOT exercise items.
- mcqs: EVERY multiple-choice item (review exercises "Encircle the correct option"), stem and options verbatim, answerIndex from the answer key (0-based) or -1.
Do not skip anything and do not merge parts. Do not solve anything.`;
const invSchema = obj({
  examples: arr(obj({ label: str, question: str, solution: strs, answer: str })),
  exercise: arr(obj({ source: str, question: str, bookAnswer: str, drawing: bool })),
  mcqs: arr(obj({ source: str, stem: str, options: strs, answerIndex: int })),
});
const sections = [];
for (const [i, sec] of outline.sections.entries()) {
  const expected = bookCounts(sec.text);
  const input = `UNIT ${UNIT} ${outline.unitTitle}, SECTION ${sec.number} ${sec.title}\n\nSECTION TEXT:\n${sec.text}\n\nANSWER KEY:\n${sec.bookAnswers || "(none printed)"}`;
  let inv = await stage(`inv-${i + 1}`, () => structured("section_inventory", INVENTORY, input, invSchema));
  // Too few items against the page count: ask once more, saying how many the pages show.
  if (expected.exerciseItems && inv.exercise.length < expected.exerciseItems * 0.9) {
    inv = await stage(`inv-${i + 1}-retry`, () => structured("section_inventory", INVENTORY,
      `${input}\n\nNOTE: the exercise has about ${expected.exerciseItems} question parts and ${expected.examples} examples. Your previous list had ${inv.exercise.length}. List every one.`, invSchema));
  }
  sections.push({ sec, inv, expected });
  console.log(`  ${sec.number}: ${inv.examples.length}/${expected.examples} examples, ${inv.exercise.length}/${expected.exerciseItems} exercise parts, ${inv.mcqs.length} MCQs. $${costSoFar().toFixed(3)}`);
}

// ── 2. Solve every exercise item in the book's method ─────────────────────────────────────────────────────
const SOLVE = `You are a Class 9 maths teacher in Punjab writing model solutions exactly the way the textbook teaches (its method, notation and
layout), so a student copying your solution earns full marks in the board exam.
For each item: steps = the full working, one line per step (no skipped steps); finalAnswer = the final result (must agree with bookAnswer when given);
for a drawing item, describe each construction/plotting step and put what the finished figure shows in "figure", else "".
For a proof, the steps are the proof and finalAnswer is the statement proved.
accept = patterns that recognise a correct typed final answer (empty list for drawings and proofs).
${SYNTAX}`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, finalAnswer: str, figure: str, accept: arr(strs) })) });
const CHECK = `You are an independent Class 9 maths examiner. For each item, solve it yourself without trusting the candidate, then judge:
- candidateCorrect: the candidate's working and final answer are mathematically correct and complete (for proofs: the proof is valid; for drawings: the steps would produce a correct figure).
- matchesBook: the candidate's final answer agrees with the book's answer (true when no book answer is given).
Answers from four-figure log tables or rounded as asked are equivalent within that precision; decimals agreeing to 3 significant figures are equivalent.
problem: one sentence on what is wrong, or "".`;
const checkSchema = obj({ items: arr(obj({ id: str, candidateCorrect: bool, matchesBook: bool, problem: str })) });

const all = sections.flatMap((s, si) => s.inv.exercise.map((q, k) => ({ ...q, id: `${si}:${k}` })));
const solved = new Map();
for (let b = 0; b < all.length; b += 8) {
  const batch = all.slice(b, b + 8);
  const out = await stage(`solve-${b / 8 + 1}`, () => structured("solutions", SOLVE,
    JSON.stringify(batch.map(({ id, question, bookAnswer, drawing }) => ({ id, question, bookAnswer, drawing }))), solveSchema));
  for (const s of out.items) solved.set(s.id, s);
}
async function check(items, name) {
  const verdicts = new Map();
  for (let b = 0; b < items.length; b += 8) {
    const batch = items.slice(b, b + 8);
    const out = await stage(`${name}-${b / 8 + 1}`, () => structured("solution_check", CHECK,
      JSON.stringify(batch.map((q) => ({ id: q.id, question: q.question, bookAnswer: q.bookAnswer, drawing: q.drawing, candidateSteps: solved.get(q.id)?.steps ?? [], candidateFinalAnswer: solved.get(q.id)?.finalAnswer ?? "" }))),
      checkSchema, 30000));
    for (const v of out.items) verdicts.set(v.id, v);
  }
  return verdicts;
}
let verdicts = await check(all, "check");
// One retry for anything that failed, with the examiner's reason.
const failed = all.filter((q) => { const v = verdicts.get(q.id); return !solved.has(q.id) || !v || !v.candidateCorrect || !v.matchesBook; });
if (failed.length) {
  for (let b = 0; b < failed.length; b += 8) {
    const batch = failed.slice(b, b + 8);
    const out = await stage(`resolve-${b / 8 + 1}`, () => structured("solutions", `${SOLVE}\nA previous attempt was rejected by an examiner; the reason is given. Solve again carefully.`,
      JSON.stringify(batch.map(({ id, question, bookAnswer, drawing }) => ({ id, question, bookAnswer, drawing, rejectedBecause: verdicts.get(id)?.problem || "missing" }))), solveSchema));
    for (const s of out.items) solved.set(s.id, s);
  }
  const again = await check(failed, "recheck");
  for (const [id, v] of again) verdicts.set(id, v);
}

// ── 3. Book MCQs: make sure every one has its key and a reason for each option ───────────────────────────
const bookMcqs = sections.flatMap((s, si) => s.inv.mcqs.map((q, k) => ({ ...q, id: `${si}:${k}` })));
const MCQ = `For each multiple-choice item from the textbook, decide the correct option yourself (0-based correctIndex), and write a one-sentence reason for EVERY option (why it is right or wrong).`;
const mcqOut = new Map();
for (let b = 0; b < bookMcqs.length; b += 15) {
  const batch = bookMcqs.slice(b, b + 15);
  const out = await stage(`mcq-${b / 15 + 1}`, () => structured("book_mcqs", MCQ,
    JSON.stringify(batch.map(({ id, stem, options }) => ({ id, stem, options }))), obj({ items: arr(obj({ id: str, correctIndex: int, rationales: strs })) }), 20000));
  for (const m of out.items) mcqOut.set(m.id, m);
}

// ── 4. Assemble per section, and report coverage ──────────────────────────────────────────────────────────
const report = { unit: UNIT, title: outline.unitTitle, sections: [], solutionOnlyBookAnswer: [], mcqDisagreements: [] };
const result = { unit: UNIT, sections: [] };
for (const [si, s] of sections.entries()) {
  const solutions = [];
  s.inv.examples.forEach((e) => solutions.push({ kind: "EXAMPLE", source: e.label, question: e.question, steps: e.solution, answer: e.answer, figure: "", accept: [], checked: true }));
  s.inv.exercise.forEach((q, k) => {
    const id = `${si}:${k}`, sol = solved.get(id), v = verdicts.get(id);
    const ok = sol && v && v.candidateCorrect && v.matchesBook;
    const accept = ok ? sol.accept.map((g) => g.filter((p) => p.trim())).filter((g) => g.length) : [];
    const usable = accept.length && matchesAny(sol.finalAnswer, accept) && !matchesAny("", accept) ? accept : [];
    if (ok) solutions.push({ kind: "EXERCISE", source: q.source, question: q.question, steps: sol.steps, answer: sol.finalAnswer, figure: sol.figure, accept: usable, checked: true });
    else {
      // Never show an unchecked solution: the question and the book's own answer only.
      solutions.push({ kind: "EXERCISE", source: q.source, question: q.question, steps: [], answer: q.bookAnswer, figure: "", accept: [], checked: false });
      report.solutionOnlyBookAnswer.push(`${q.source}: ${v?.problem || "no checked solution"}`);
    }
  });
  const mcqs = s.inv.mcqs.map((q, k) => {
    const m = mcqOut.get(`${si}:${k}`);
    const key = q.answerIndex >= 0 ? q.answerIndex : m?.correctIndex;
    if (m && q.answerIndex >= 0 && m.correctIndex !== q.answerIndex) report.mcqDisagreements.push(`${q.source}: book ${q.answerIndex}, solver ${m.correctIndex} (book kept)`);
    return { source: q.source, stem: q.stem, options: q.options, correctIndex: key ?? 0, rationales: m?.rationales ?? [] };
  }).filter((q) => q.rationales.length === q.options.length);
  result.sections.push({ number: s.sec.number, solutions, mcqs });
  report.sections.push({ number: s.sec.number, title: s.sec.title,
    examples: `${s.inv.examples.length}/${s.expected.examples}`,
    exercise: `${s.inv.exercise.length}/${s.expected.exerciseItems}`,
    solved: solutions.filter((x) => x.kind === "EXERCISE" && x.checked).length,
    mcqs: mcqs.length });
}
writeFileSync(join(ROOT, "units", `u-${UNIT}-solutions.json`), JSON.stringify(result, null, 1));
writeFileSync(join(ROOT, "units", `u-${UNIT}-coverage.json`), JSON.stringify(report, null, 1));
const ex = result.sections.flatMap((s) => s.solutions.filter((x) => x.kind === "EXERCISE"));
console.log(`Unit ${UNIT} coverage: ${result.sections.reduce((n, s) => n + s.solutions.filter((x) => x.kind === "EXAMPLE").length, 0)} examples, ${ex.length} exercise items (${ex.filter((x) => x.checked).length} with checked solutions), ${result.sections.reduce((n, s) => n + s.mcqs.length, 0)} book MCQs. Spend $${costSoFar().toFixed(3)}`);
