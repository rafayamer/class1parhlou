// Builds one textbook unit into Parhlou content: modules with tutor lessons, tutor notes, MCQs and marked written
// questions, using the book's own examples, exercise questions and answer key (decision D-022).
//   node --use-env-proxy scripts/build-unit.mjs <unit number>
// Reads content/class9-maths/pages/*.md (book transcription), writes content/class9-maths/units/u-<n>.json.
// Stages are cached in content/class9-maths/work/, so a rerun only redoes what is missing. Spend is capped.
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { matchesAny, SYNTAX } from "./textmatch.mjs";

const ROOT = "content/class9-maths";
const UNIT = Number(process.argv[2]);
if (!UNIT) throw new Error("usage: build-unit.mjs <unit number>");
mkdirSync(join(ROOT, "work"), { recursive: true });
mkdirSync(join(ROOT, "units"), { recursive: true });

const cache = (name) => join(ROOT, "work", `u${UNIT}-${name}.json`);
async function stage(name, run) {
  const f = cache(name);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const result = await run();
  writeFileSync(f, JSON.stringify(result, null, 1));
  return result;
}

async function structured(name, instructions, input, schema, maxTokens = 32000) {
  const text = await luna({
    max_output_tokens: maxTokens,
    reasoning: { effort: "medium" },
    instructions,
    input,
    text: { format: { type: "json_schema", name, strict: true, schema } },
  }, name, `unit ${UNIT}`);
  return JSON.parse(text);
}

const str = { type: "string" };
const strs = { type: "array", items: str };
const obj = (props) => ({ type: "object", additionalProperties: false, required: Object.keys(props), properties: props });
const arr = (items) => ({ type: "array", items });

// ── Pages of this unit ────────────────────────────────────────────────────────────────────────────────────
const pageFiles = readdirSync(join(ROOT, "pages")).filter((f) => /^p\d+\.md$/.test(f)).sort();
const pages = pageFiles.map((f) => ({ n: Number(f.slice(1, 4)), text: readFileSync(join(ROOT, "pages", f), "utf8") }));
const unitStart = (u) => pages.find((p) => new RegExp(`^#\\s*Unit\\s*${u}\\b`, "mi").test(p.text))?.n;
// The answer key at the back starts at the first page headed ANSWERS after the last unit begins.
const answersStart = pages.find((p) => p.n > (unitStart(13) ?? 0) && /^\s*#*\s*ANSWERS\s*$/m.test(p.text))?.n ?? 9999;
const start = unitStart(UNIT);
if (!start) throw new Error(`Unit ${UNIT} heading not found in the transcription`);
const nextStart = Math.min(unitStart(UNIT + 1) ?? answersStart, answersStart);
const unitPages = pages.filter((p) => p.n >= start && p.n < nextStart);
const printed = (n) => /PAGE\s+(\d+)/.exec(pages.find((p) => p.n === n)?.text ?? "")?.[1] ?? String(n);

// ── A. Split the unit into its main sections, in code (the book's own wording, nothing lost) ─────────────
// Main sections are two-level headings (e.g. "## 1.2 Radical Expressions"); the review exercise is its own module.
const mainHeading = new RegExp(`^#{1,4}\\s*(${UNIT}\\.\\d+)\\s+(.+)$`);
const sections = [];
let unitTitle = "";
let titleNext = false;
for (const p of unitPages) {
  for (const line of p.text.split("\n")) {
    const trimmed = line.trim();
    const t = /^#\s*Unit\s*\d+\s*[:\-–]?\s*(.*)$/i.exec(trimmed);
    if (t && !unitTitle && !titleNext) { unitTitle = t[1].trim(); titleNext = !unitTitle; continue; }
    // Some units print their title on the line after "Unit N".
    if (titleNext && trimmed && !/^PAGE\s+\d+/i.test(trimmed)) { unitTitle = trimmed.replace(/^#+\s*/, ""); titleNext = false; continue; }
    const main = mainHeading.exec(trimmed);
    if (main) sections.push({ number: main[1], title: main[2].replace(/[.\s]+$/, ""), pages: [p.n], text: [] });
    else if (/^#{0,4}\s*REVIEW EXERCISE/i.test(trimmed)) sections.push({ number: `${UNIT}.R`, title: "Unit review", pages: [p.n], text: [], review: true });
    if (!sections.length) sections.push({ number: `${UNIT}.0`, title: "Introduction", pages: [p.n], text: [] });
    const cur = sections[sections.length - 1];
    if (!cur.pages.includes(p.n)) cur.pages.push(p.n);
    cur.text.push(line);
  }
}
// An "Introduction" before the first numbered section is folded into it.
if (sections.length > 1 && sections[0].number.endsWith(".0")) {
  const intro = sections.shift();
  sections[0].text = [...intro.text, ...sections[0].text];
  sections[0].pages = [...new Set([...intro.pages, ...sections[0].pages])];
}
// A unit with only one main section (e.g. Logic: 8.1 with 8.1.1–8.1.5) is split at its sub-sections, so each
// becomes a lesson of its own; the section's exercise is given to every part.
const mains = sections.filter((s) => !s.review);
if (mains.length === 1) {
  const only = mains[0];
  const subHeading = new RegExp(`^#{1,4}\\s*(${UNIT}\\.\\d+\\.\\d+)\\s+(.+)$`);
  const parts = [];
  for (const line of only.text) {
    const sub = subHeading.exec(line.trim());
    if (sub || !parts.length) parts.push({ number: sub ? sub[1] : only.number, title: sub ? sub[2].replace(/[.\s]+$/, "") : only.title, pages: only.pages, text: [] });
    parts[parts.length - 1].text.push(line);
  }
  const exerciseAt = only.text.findIndex((l) => /^#{0,4}\s*EXERCISE\b/i.test(l.trim()));
  const exerciseText = exerciseAt >= 0 ? only.text.slice(exerciseAt) : [];
  if (parts.length > 1) {
    for (const part of parts) if (!part.text.some((l) => /EXERCISE\b/i.test(l))) part.text.push("", ...exerciseText);
    sections.splice(sections.indexOf(only), 1, ...parts);
  }
}

// Answer key for this unit: blocks headed "EXERCISE <unit>.<n>" or "REVIEW EXERCISE <unit>" at the back of the book.
const answers = {};
let key = null;
for (const p of pages.filter((pg) => pg.n >= answersStart)) {
  for (const line of p.text.split("\n")) {
    const trimmed = line.trim();
    const ex = /^#{0,4}\s*(REVIEW\s+)?EXERCISE\s+(\d+)(?:\.(\d+))?/i.exec(trimmed);
    if (ex) {
      key = Number(ex[2]) === UNIT ? (ex[1] ? "review" : (ex[3] ? `${ex[2]}.${ex[3]}` : `${ex[2]}`)) : null;
      if (key) answers[key] = answers[key] ?? [];
      continue;
    }
    if (/^#\s/.test(trimmed)) key = null;
    if (key) answers[key].push(line);
  }
}

const outline = {
  unitTitle: unitTitle || `Unit ${UNIT}`,
  sections: sections.map((s) => {
    const text = s.text.join("\n");
    const exercises = [...new Set([...text.matchAll(/(?<!REVIEW\s)EXERCISE\s+(\d+(?:\.\d+)?)/gi)].map((m) => m[1]))];
    const keyLines = s.review ? (answers.review ?? []) : exercises.flatMap((e) => answers[e] ?? []);
    const nums = s.pages.map(printed);
    return {
      number: s.number, title: s.title, review: !!s.review,
      bookPages: nums.length > 1 ? `${nums[0]}–${nums[nums.length - 1]}` : nums[0],
      exercise: exercises.map((e) => `Exercise ${e}`).join(", ") || (s.review ? `Review Exercise ${UNIT}` : ""),
      text, bookAnswers: keyLines.join("\n"),
    };
  }),
};
writeFileSync(cache("outline"), JSON.stringify(outline, null, 1));
console.log(`Unit ${UNIT} "${outline.unitTitle}" (pdf pages ${start}–${nextStart - 1}):`);
for (const s of outline.sections) console.log(`  ${s.number} ${s.title} [${s.exercise || "no exercise"}; answer key ${s.bookAnswers ? "found" : "none"}; ${s.text.length} chars]`);
if (process.env.OUTLINE_ONLY) process.exit(0);

// ── B. Teach each section like a home tutor ───────────────────────────────────────────────────────────────
const TEACH = `You are Parhlou, a patient home tutor preparing a Pakistani Class 9 student (Punjab board, English medium) for HIGH marks in the board exam.
You receive one textbook section: its text (definitions, worked examples, exercise) and the book's answer key for that exercise.
Write teaching material that follows the BOOK'S method and notation exactly (students are marked against the book's way of working). Never mention any publisher, website or source; say "your textbook".
- explain: 3–6 short cards, one idea each, in simple English a 14-year-old understands; use the book's definitions word for word inside them.
- example: take the book's own worked example that best shows the method (question and steps verbatim, split into one-line steps); add for each step a one-sentence "why".
- yourTurn: 2 exercise questions from this section (verbatim) for the student to try, each with the final answer, 2 hints (small, then bigger) and the full step-by-step solution in the book's method. "accept" patterns must match a correct typed final answer and must not match an empty answer.
- examTips: how this topic is asked and marked in the board paper (which kind of question, what earns the marks). mistakes: common mistakes that lose marks.
- note: a tutor note for this section (title, 4–8 keywords students might type, a direct answer, an explanation, a simpler explanation, 1–2 examples, steps, a check question with its answer and accept patterns).
- mcqs: 6 to 10 MCQs on this section: the book's own MCQs if the text has any (verbatim, with the book's answer, fromBook true), otherwise new board-style MCQs (fromBook false); 4 options, exactly one correct, and a one-sentence reason for EVERY option (why it is right or wrong).
- written: up to 14 of this section's exercise questions (verbatim, one part per item, stating what to do, e.g. "Rationalise the denominator: 3/(√5 − √2)"; include "source" like "Ex ${UNIT}.1 Q2(iii)"). Skip questions that need a drawing or a number line. Give marks 2 for a short question (one or two steps) and 4 for a longer one. modelAnswer: the solution as the book would accept it, one line per step, ending with the final answer; finalAnswer must equal the book's answer key when it gives one. rubric: 2–4 marking points whose marks add up to the question's marks, each with "why" (why it matters) and "how" (how to earn it) and accept patterns that the model answer satisfies; the last point is the correct final answer.
${SYNTAX}`;

const teachSchema = obj({
  note: obj({ title: str, keywords: strs, answer: str, explanation: str, simple: str, examples: strs, steps: strs, checkQuestion: str, checkAnswer: str, checkAccept: arr(strs) }),
  lesson: obj({
    explain: arr(obj({ title: str, body: str })),
    example: obj({ question: str, steps: arr(obj({ text: str, why: str })), answer: str }),
    yourTurn: arr(obj({ question: str, answer: str, accept: arr(strs), hints: strs, solution: strs })),
    examTips: strs, mistakes: strs,
  }),
  mcqs: arr(obj({ stem: str, options: strs, correctIndex: { type: "integer" }, rationales: strs, fromBook: { type: "boolean" } })),
  written: arr(obj({ text: str, source: str, marks: { type: "integer" }, modelAnswer: strs, finalAnswer: str,
    rubric: arr(obj({ description: str, marks: { type: "number" }, why: str, how: str, accept: arr(strs) })) })),
});

const modules = [];
for (const [i, sec] of outline.sections.entries()) {
  const brief = sec.review
    ? "This is the unit's REVIEW EXERCISE. mcqs: include ALL its multiple-choice items verbatim with the book's answers (fromBook true), up to 15. written: its other questions (verbatim). explain: 4–6 cards summarising the whole unit. example: one review question solved step by step. yourTurn: two review questions."
    : "This is one main section of the unit, with its sub-sections, examples and exercise.";
  const input = `${brief}\n\nUNIT: ${UNIT} ${outline.unitTitle}\nSECTION: ${sec.number} ${sec.title} (printed pages ${sec.bookPages})\n\nSECTION TEXT (verbatim from the book):\n${sec.text}\n\nBOOK ANSWER KEY for this section's exercises (finalAnswer must agree with it):\n${sec.bookAnswers || "(none printed)"}`;
  const taught = await stage(`teach-${i + 1}`, () => structured("section_lesson", TEACH, input, teachSchema, 40000));
  modules.push({ sec, taught });
  console.log(`  taught ${sec.number}: ${taught.mcqs.length} MCQs, ${taught.written.length} written. $${costSoFar().toFixed(3)}`);
}

// ── C. Independent check of every answer ──────────────────────────────────────────────────────────────────
const CHECK = `You are an independent Class 9 maths examiner. Solve each item yourself, carefully, WITHOUT trusting any given key.
For MCQs return the index (0-based) of the correct option. For written items return your final answer, then say whether the
candidate final answer is mathematically equivalent to yours (same value/set/expression/statement; ignore formatting).
Precision: answers found with four-figure log/antilog tables, or rounded as the question asks, are equivalent when they agree to
the precision those methods give (e.g. log 0.0876 = −1.0575 from tables vs −1.0580 exactly: equivalent; 0.02675 vs 0.020675: NOT
equivalent). Decimals that agree to 3 significant figures are equivalent unless the question demands more.`;
const checkSchema = obj({
  mcqs: arr(obj({ id: str, correctIndex: { type: "integer" } })),
  written: arr(obj({ id: str, myAnswer: str, candidateEquivalent: { type: "boolean" } })),
});
for (const [i, m] of modules.entries()) {
  const items = {
    mcqs: m.taught.mcqs.map((q, k) => ({ id: `q${k}`, stem: q.stem, options: q.options })),
    written: m.taught.written.map((w, k) => ({ id: `w${k}`, question: w.text, candidateFinalAnswer: w.finalAnswer })),
  };
  m.check = await stage(`check-${i + 1}`, () => structured("independent_check", CHECK, JSON.stringify(items), checkSchema, 24000));
}

// ── C2. Repair marking points that don't recognise their own model answer (one call per unit) ─────────────
const patternOk = (w) => {
  const model = w.modelAnswer.join("\n");
  return w.rubric.every((c) => {
    const groups = c.accept.map((g) => g.filter((p) => p.trim())).filter((g) => g.length);
    return groups.length && matchesAny(model, groups) && !matchesAny("", groups);
  });
};
const toRepair = [];
for (const [i, m] of modules.entries()) {
  const ok = new Set(m.check.written.filter((c) => c.candidateEquivalent).map((c) => Number(c.id.slice(1))));
  m.taught.written.forEach((w, k) => { if (ok.has(k) && !patternOk(w)) toRepair.push({ id: `${i}:${k}`, question: w.text, modelAnswer: w.modelAnswer,
    rubric: w.rubric.map((c) => ({ description: c.description, accept: c.accept })) }); });
}
if (toRepair.length) {
  const fixed = await stage("repair", () => structured("repair_patterns", `Fix the accepted-answer patterns of each marking point so that the model answer (the student's correct typed answer) matches every point and an empty answer matches none. Keep the same number and order of marking points. Prefer short key results (e.g. "10x=4.4~", "4/9", "irrational", "π"); use "~" after a decimal that may continue; give 1–3 alternative groups when students may write it differently.
${SYNTAX}`, JSON.stringify(toRepair), obj({ items: arr(obj({ id: str, rubric: arr(obj({ accept: arr(strs) })) })) }), 24000));
  for (const f of fixed.items) {
    const [i, k] = f.id.split(":").map(Number);
    const w = modules[i]?.taught.written[k];
    if (w && f.rubric.length === w.rubric.length) w.rubric = w.rubric.map((c, j) => ({ ...c, accept: f.rubric[j].accept }));
  }
}

// ── D. Keep only what passed, and assemble the unit ───────────────────────────────────────────────────────
const report = { unit: UNIT, title: outline.unitTitle, dropped: [] };
const near = (a, b) => Math.abs(a - b) < 1e-9;
const chapter = { id: `u-${UNIT}`, number: UNIT, title: outline.unitTitle, status: "SAMPLE_UNREVIEWED", modules: [] };
const notes = [], quiz = [], written = [];
for (const [i, m] of modules.entries()) {
  const mid = `m-${UNIT}-${i + 1}`, nid = `n-${UNIT}-${i + 1}`;
  const t = m.taught;
  const agreed = new Map(m.check.mcqs.map((c) => [Number(c.id.slice(1)), c.correctIndex]));
  t.mcqs.forEach((q, k) => {
    const valid = q.options.length >= 2 && q.rationales.length === q.options.length && q.correctIndex >= 0 && q.correctIndex < q.options.length;
    if (!valid || agreed.get(k) !== q.correctIndex) { report.dropped.push(`MCQ ${mid}#${k}: ${valid ? "independent check disagreed" : "malformed"}`); return; }
    quiz.push({ id: `q-${UNIT}-${i + 1}-${k + 1}`, stem: q.stem, options: q.options, correctIndex: q.correctIndex, rationales: q.rationales,
      status: q.fromBook ? "APPROVED" : "SAMPLE_UNREVIEWED", noteId: nid, moduleId: mid });
  });
  const okWritten = new Set(m.check.written.filter((c) => c.candidateEquivalent).map((c) => Number(c.id.slice(1))));
  const moduleWritten = [];
  t.written.forEach((w, k) => {
    const rubric = w.rubric.map((c, j) => ({ id: String.fromCharCode(97 + j), description: c.description, marks: c.marks, why: c.why, how: c.how,
      accept: c.accept.map((g) => g.filter((p) => p.trim())).filter((g) => g.length) }));
    const sum = rubric.reduce((s, c) => s + c.marks, 0);
    const model = w.modelAnswer.join("\n");
    const problems = [];
    if (!okWritten.has(k)) problems.push("independent check disagreed");
    if (![2, 4].includes(w.marks) || !near(sum, w.marks)) problems.push(`marks ${sum}/${w.marks}`);
    if (rubric.some((c) => !c.accept.length || !matchesAny(model, c.accept))) problems.push("model answer misses a marking point");
    if (rubric.some((c) => c.accept.length && matchesAny("", c.accept))) problems.push("blank answer would score");
    if (problems.length) { report.dropped.push(`Written ${mid}#${k} (${w.source}): ${problems.join(", ")}`); return; }
    moduleWritten.push({ id: `w-${UNIT}-${i + 1}-${k + 1}`, text: w.text, marks: w.marks, modelAnswer: w.modelAnswer,
      followUp: t.lesson.yourTurn[0]?.question ?? "", noteId: nid, section: 0, number: 0, matching: "method", rubric, source: w.source });
  });
  written.push(...moduleWritten);
  notes.push({ id: nid, subjectId: "pb-9-math", moduleId: mid, title: t.note.title, keywords: t.note.keywords, answer: t.note.answer,
    explanation: t.note.explanation, simple: t.note.simple, examples: t.note.examples, steps: t.note.steps,
    checkQuestion: t.note.checkQuestion, checkAnswer: t.note.checkAnswer, checkAccept: t.note.checkAccept });
  const yourTurn = t.lesson.yourTurn.filter((p) => p.accept.length && matchesAny(p.answer, p.accept) && !matchesAny("", p.accept));
  if (yourTurn.length < t.lesson.yourTurn.length) report.dropped.push(`Your-turn ${mid}: ${t.lesson.yourTurn.length - yourTurn.length} with unusable answer patterns`);
  chapter.modules.push({
    id: mid, position: i + 1, title: m.sec.review ? `Unit ${UNIT} review` : `${m.sec.number} ${m.sec.title}`, status: "SAMPLE_UNREVIEWED", video: null,
    bookReference: { text: `Your textbook, Unit ${UNIT}${m.sec.review ? ", review exercise" : `, section ${m.sec.number}`}, pages ${m.sec.bookPages}${m.sec.exercise ? `; ${m.sec.exercise}` : ""}`, verified: true },
    readingTasks: [m.sec.review ? `Revise Unit ${UNIT} and attempt Review Exercise ${UNIT} (pages ${m.sec.bookPages}).` : `Read section ${m.sec.number} (pages ${m.sec.bookPages}) in your textbook.`,
      ...(m.sec.exercise && !m.sec.review ? [`Solve ${m.sec.exercise} in your notebook.`] : [])],
    writtenQuestions: moduleWritten.slice(0, 3),
    lesson: { ...t.lesson, yourTurn },
  });
}
writeFileSync(join(ROOT, "units", `u-${UNIT}.json`), JSON.stringify({ chapter, notes, quiz, written, report }, null, 1));
console.log(`Unit ${UNIT} built: ${chapter.modules.length} modules, ${quiz.length} MCQs, ${written.length} written (${report.dropped.length} items dropped). Total spend $${costSoFar().toFixed(3)}`);
