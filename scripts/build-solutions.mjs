// Full book coverage for one unit: every worked example, every exercise question (each part) and every book MCQ,
// each with a step-by-step solution in the book's method, checked against the book's answer key and by an
// independent re-solve. Run after build-unit.mjs (it reads that unit's section split).
//   node --use-env-proxy scripts/build-solutions.mjs <unit number>
// Writes content/class9-maths/units/u-<n>-solutions.json and a coverage report. Stages are cached in work/.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { matchesAny, SYNTAX } from "./textmatch.mjs";
import { S } from "./subject.mjs";
const PHY = !!S.science; // Physics, Chemistry: chapters with sections A–E

const ROOT = S.root;
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
// Physics: exercise items are "Q1.4." in Sections B–E (Section A is the MCQs), parts are (a), (b) …; examples "EXAMPLE 1.2:".
function physicsCounts(text) {
  let section = "", items = 0, parts = 0, inItem = 0, examples = 0;
  const close = () => { if (inItem !== null) parts += Math.max(1, inItem); };
  let open = false;
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (/^EXAMPLE\s+\d+(\.\d+)?\s*:?/i.test(line)) examples++;
    const sec = /^#{0,4}\s*SECTION\s+([A-E])\b/i.exec(line);
    if (sec) { if (open) close(); open = false; section = sec[1].toUpperCase(); continue; }
    if (!"BCDE".includes(section) || !section) continue;
    if (/^Q?\d{1,2}\.\d{1,2}\.\s/.test(line)) { if (open) close(); open = true; items++; inItem = 0; continue; }
    if (open) inItem += (line.match(/(^|\s)\([a-h]\)\s/g) ?? []).length;
  }
  if (open) close();
  return { examples, exerciseItems: parts };
}
function bookCounts(text) {
  if (PHY) return physicsCounts(text);
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
const INVENTORY = PHY ? `You list EVERYTHING in one section of a Pakistani Class 9 ${S.name} textbook (Punjab, National Curriculum 2023), copying the book's words exactly.
- examples: EVERY worked example (each block starting "EXAMPLE n.m:"), label "Example n.m", with its question, the BOOK'S OWN solution copied as one
  line per step (given data, formula, substitution, result with unit) and its final answer with unit. Worked examples are NEVER exercise items.
- exercise: ONLY items of the end-of-chapter exercise Sections B–E (if this text contains them): EVERY item and EVERY part separately
  ((a), (b), … each its own item). "question" must be complete on its own (the item's instruction plus the part).
  "source": ${Object.entries(S.sectionKinds ?? {}).map(([k, v]) => `"${v} n.m" (Section ${k})`).join(", ")}, adding the part
  letter when there are parts, e.g. "${S.sectionKinds?.E} 1.3(a)".
  bookAnswer: for numericals, the answer printed after the problem ("ANSWER:") for that part, exactly; else "".
  drawing: true if the answer needs a diagram, graph, circuit or table drawn.
- mcqs: EVERY item of Section A, stem and options verbatim (without the option letters), source "MCQ n.m", answerIndex -1 (the book prints no key).
If this text has no end-of-chapter exercise, exercise and mcqs are empty. Do not skip anything and do not merge parts. Do not solve anything.` : `You list EVERYTHING in one section of a Pakistani Class 9 Mathematics textbook, copying the book's words exactly.
- examples: EVERY worked example (each block starting "EXAMPLE n:", including all its parts), with its question, the BOOK'S OWN solution
  copied as one line per step, and its final answer. Worked examples are NEVER exercise items.
- exercise: ONLY questions printed after an "EXERCISE" heading (or in the review exercise): EVERY question and EVERY part separately ((i), (ii), ... each its own item). "question" must be complete on its own:
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
  const expected = bookCounts(sec.text.split("\n\nCHAPTER EXERCISE (pick only")[0]);
  // A Physics section's text ends with the chapter exercise (for teaching); its items are listed once, in the exercise module.
  const own = sec.text.split("\n\nCHAPTER EXERCISE (pick only")[0];
  const input = `${S.unitWord.toUpperCase()} ${UNIT} ${outline.unitTitle}, SECTION ${sec.number} ${sec.title}\n\nSECTION TEXT:\n${own}\n\nANSWER KEY:\n${PHY ? "(numerical answers are printed in the text after each problem as ANSWER:)" : sec.bookAnswers || "(none printed)"}`;
  let inv = await stage(`inv-${i + 1}`, () => structured("section_inventory", INVENTORY, input, invSchema));
  // Too few items against the page count: ask once more, saying how many the pages show.
  if (expected.exerciseItems && inv.exercise.length < expected.exerciseItems * 0.9) {
    inv = await stage(`inv-${i + 1}-retry`, () => structured("section_inventory", INVENTORY,
      `${input}\n\nNOTE: the exercise has about ${expected.exerciseItems} question parts and ${expected.examples} examples. Your previous list had ${inv.exercise.length}. List every one.`, invSchema));
  }
  // Anything labelled "Example" belongs with the examples (it keeps its checked solution below).
  inv = { ...inv, exercise: inv.exercise.filter((q) => !/^example/i.test(q.source)), strayExamples: inv.exercise.filter((q) => /^example/i.test(q.source)) };
  // Physics: only the exercise module may hold exercise items and MCQs (sections teach; the chapter exercise is listed once).
  if (PHY && !sec.review) inv = { ...inv, exercise: [], mcqs: [], strayExamples: inv.strayExamples };
  sections.push({ sec, inv, expected });
  console.log(`  ${sec.number}: ${inv.examples.length}/${expected.examples} examples, ${inv.exercise.length}/${expected.exerciseItems} exercise parts, ${inv.mcqs.length} MCQs. $${costSoFar().toFixed(3)}`);
}

// ── 2. Solve every exercise item in the book's method ─────────────────────────────────────────────────────
const SOLVE = PHY ? `You are a Class 9 ${S.short} teacher in Punjab writing model answers exactly the way the textbook teaches, so a student copying
your answer earns full marks in the board exam.
For a NUMERICAL: steps = "Given: …" (each quantity with its unit, converted to SI where needed), "Required: …", the formula, the substitution
with units, the working, and the result; finalAnswer = the value WITH its unit, in the form the question asks (scientific notation, prefix,
significant figures), and it must agree with bookAnswer when given.
For a THEORY item (short answer, constructed response, comprehensive): steps = the answer as the textbook would accept it, one point per line,
using the book's definitions and wording (include a formula or an example where the book does); finalAnswer = a one-line summary of the answer.
For a drawing item, describe the diagram/graph step by step and put what the finished figure shows in "figure", else "".
accept = patterns that recognise a correct typed final answer for numericals (value and unit); an empty list for theory and drawings.
${SYNTAX}` : `You are a Class 9 maths teacher in Punjab writing model solutions exactly the way the textbook teaches (its method, notation and
layout), so a student copying your solution earns full marks in the board exam.
For each item: steps = the full working, one line per step (no skipped steps); finalAnswer = the final result (must agree with bookAnswer when given);
for a drawing item, describe each construction/plotting step and put what the finished figure shows in "figure", else "".
For a proof, the steps are the proof and finalAnswer is the statement proved.
accept = patterns that recognise a correct typed final answer (empty list for drawings and proofs).
${SYNTAX}`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, finalAnswer: str, figure: str, accept: arr(strs) })) });
const CHECK = PHY ? `You are an independent Class 9 ${S.short} examiner (Punjab board). For each item, answer it yourself without trusting the candidate, then judge:
- candidateCorrect: the candidate's working and final answer are correct and complete ${S.short} (numericals: right formula, right substitution,
  right value AND unit; theory: every point is scientifically correct and the answer covers what the question asks, as the textbook teaches it).
- matchesBook: the candidate's final answer agrees with the book's printed answer in value and unit (true when no book answer is given).
${S.checkRules}
problem: one sentence on what is wrong, or "".` : `You are an independent Class 9 maths examiner. For each item, solve it yourself without trusting the candidate, then judge:
- candidateCorrect: the candidate's working and final answer are mathematically correct and complete (for proofs: the proof is valid; for drawings: the steps would produce a correct figure).
- matchesBook: the candidate's final answer agrees with the book's answer (true when no book answer is given).
Answers from four-figure log tables or rounded as asked are equivalent within that precision; decimals agreeing to 3 significant figures are equivalent.
problem: one sentence on what is wrong, or "".`;
const checkSchema = obj({ items: arr(obj({ id: str, candidateCorrect: bool, matchesBook: bool, problem: str })) });

const all = sections.flatMap((s, si) => [...s.inv.exercise, ...s.inv.strayExamples].map((q, k) => ({ ...q, id: `${si}:${k}` })));
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

// Physics prints no MCQ key: a second, independent pass answers every MCQ, and the two must agree (else the examiner rules below).
if (PHY) {
  for (let b = 0; b < bookMcqs.length; b += 15) {
    const batch = bookMcqs.slice(b, b + 15);
    const out = await stage(`mcq2-${b / 15 + 1}`, () => structured("mcq_answers", `You are an independent Class 9 ${S.short} examiner. For each multiple-choice item, work out the correct option yourself (0-based correctIndex).`,
      JSON.stringify(batch.map(({ id, stem, options }) => ({ id, stem, options }))), obj({ items: arr(obj({ id: str, correctIndex: int })) }), 16000));
    for (const m of out.items) { const q = bookMcqs.find((x) => x.id === m.id); if (q) q.answerIndex = m.correctIndex; }
  }
}
// MCQs where the solver disagrees with the book's key (or, in Physics, with the second pass): a separate examiner decides.
const disputed = bookMcqs.filter((q) => q.answerIndex >= 0 && mcqOut.get(q.id) && mcqOut.get(q.id).correctIndex !== q.answerIndex);
const ruling = new Map();
if (disputed.length) {
  const out = await stage("mcq-dispute", () => structured("mcq_ruling",
    `Two answers are proposed for each multiple-choice item (A and B). Work it out carefully yourself and return the index of the correct option (0-based), or -1 if neither is right.`,
    JSON.stringify(disputed.map((q) => ({ id: q.id, stem: q.stem, options: q.options, A: q.answerIndex, B: mcqOut.get(q.id).correctIndex }))),
    obj({ items: arr(obj({ id: str, correctIndex: int })) }), 16000));
  for (const r of out.items) ruling.set(r.id, r.correctIndex);
}

// ── 4. Assemble per section, and report coverage ──────────────────────────────────────────────────────────
const report = { unit: UNIT, title: outline.unitTitle, sections: [], solutionOnlyBookAnswer: [], answerKeyConflicts: [], mcqDisagreements: [] };
const result = { unit: UNIT, sections: [] };
for (const [si, s] of sections.entries()) {
  const solutions = [];
  s.inv.examples.forEach((e) => solutions.push({ kind: "EXAMPLE", source: e.label, question: e.question, steps: e.solution, answer: e.answer, figure: "", accept: [], checked: true, note: "" }));
  [...s.inv.exercise, ...s.inv.strayExamples].forEach((q, k) => {
    const id = `${si}:${k}`, sol = solved.get(id), v = verdicts.get(id);
    const kind = /^example/i.test(q.source) ? "EXAMPLE" : "EXERCISE";
    // Correct working confirmed by the solver and the independent examiner. If the textbook's key disagrees
    // (a misprint, or a misread scan), the checked working is shown with a note rather than a wrong answer.
    const ok = sol && v && v.candidateCorrect;
    const note = ok && !v.matchesBook && q.bookAnswer ? `Your textbook's answer key shows "${q.bookAnswer}"; the checked working above gives this answer.` : "";
    if (note) report.answerKeyConflicts.push(`${q.source}: key "${q.bookAnswer}", checked answer "${sol.finalAnswer}"`);
    const accept = ok ? sol.accept.map((g) => g.filter((p) => p.trim())).filter((g) => g.length) : [];
    const usable = accept.length && matchesAny(sol.finalAnswer, accept) && !matchesAny("", accept) ? accept : [];
    if (ok) solutions.push({ kind, source: q.source, question: q.question, steps: sol.steps, answer: sol.finalAnswer, figure: sol.figure, accept: usable, checked: true, note });
    else {
      // Never show an unchecked solution: the question and the book's own answer only.
      solutions.push({ kind, source: q.source, question: q.question, steps: [], answer: q.bookAnswer, figure: "", accept: [], checked: false, note: "" });
      report.solutionOnlyBookAnswer.push(`${q.source}: ${v?.problem || "no checked solution"}`);
    }
  });
  const mcqs = s.inv.mcqs.map((q, k) => {
    const id = `${si}:${k}`, m = mcqOut.get(id);
    let key = q.answerIndex >= 0 ? q.answerIndex : m?.correctIndex;
    if (ruling.has(id)) {
      const r = ruling.get(id);
      // Two independent passes agree against the key: use theirs. A three-way split: leave the item out.
      if (r === m.correctIndex) key = r; else if (r !== q.answerIndex) key = -1;
      report.mcqDisagreements.push(`${q.source}: book ${q.answerIndex}, solver ${m.correctIndex}, examiner ${r} → ${key < 0 ? "left out" : key === q.answerIndex ? "book kept" : "corrected"}`);
    }
    // Reasons must agree with the key used; regenerate-free rule: reasons came from the solver, so drop them if the key is the book's and differs.
    const rationales = m && key === m.correctIndex ? m.rationales : [];
    return { source: q.source, stem: q.stem, options: q.options, correctIndex: key ?? -1, rationales };
  }).filter((q) => q.correctIndex >= 0 && q.rationales.length === q.options.length);
  result.sections.push({ number: s.sec.number, solutions, mcqs });
  report.sections.push({ number: s.sec.number, title: s.sec.title,
    examples: `${solutions.filter((x) => x.kind === "EXAMPLE").length}/${s.expected.examples}`,
    exercise: `${s.inv.exercise.length}/${s.expected.exerciseItems}`,
    solved: solutions.filter((x) => x.kind === "EXERCISE" && x.checked).length,
    mcqs: mcqs.length });
}
writeFileSync(join(ROOT, "units", `u-${UNIT}-solutions.json`), JSON.stringify(result, null, 1));
writeFileSync(join(ROOT, "units", `u-${UNIT}-coverage.json`), JSON.stringify(report, null, 1));
const ex = result.sections.flatMap((s) => s.solutions.filter((x) => x.kind === "EXERCISE"));
console.log(`Unit ${UNIT} coverage: ${result.sections.reduce((n, s) => n + s.solutions.filter((x) => x.kind === "EXAMPLE").length, 0)} examples, ${ex.length} exercise items (${ex.filter((x) => x.checked).length} with checked solutions), ${result.sections.reduce((n, s) => n + s.mcqs.length, 0)} book MCQs. Spend $${costSoFar().toFixed(3)}`);
