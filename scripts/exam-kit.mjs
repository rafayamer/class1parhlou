// Exam kit for each unit, built for the Annual Exam 2027 (content/class9-maths/syllabus-2027.json):
//   - definitions and statements to learn word for word (kept only if found verbatim in the book's text),
//   - formula sheet, the most important questions (only references that exist in the unit's solutions and are examinable),
//   - how to write answers for full marks, and the mistakes that lose marks.
// For units whose questions are mostly graphs and constructions (5, 10, 11) it also writes board-style written questions
// from the book's examinable exercise questions, with marking rubrics checked against the app's matcher.
//   node --use-env-proxy scripts/exam-kit.mjs [unit ...]
// Writes content/class9-maths/units/u-N-kit.json (applied by assemble-course.mjs). Stages cached in work/kit-*.json.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { matchesAll, matchesAny, SYNTAX } from "./textmatch.mjs";
import { isExcluded } from "./syllabus.mjs";

const ROOT = "content/class9-maths";
const UNITS = process.argv.slice(2).length ? process.argv.slice(2).map(Number) : Array.from({ length: 13 }, (_, i) => i + 1);
const WRITTEN_UNITS = new Set([5, 10, 11]);
const syllabus = JSON.parse(readFileSync(join(ROOT, "syllabus-2027.json"), "utf8"));

const str = { type: "string" }, strs = { type: "array", items: str }, int = { type: "integer" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
async function cached(name, run) {
  const f = join(ROOT, "work", `kit-${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const result = await run();
  writeFileSync(f, JSON.stringify(result, null, 1));
  return result;
}
async function structured(name, item, instructions, input, schema, effort = "medium") {
  const text = await luna({ max_output_tokens: 60000, reasoning: { effort }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `kit-${name}`, item);
  return JSON.parse(text);
}
const norm = (s) => s.toLowerCase().replace(/[*_`#>]/g, "").replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/\s+/g, " ").trim();

const KIT = `You are an experienced Punjab board Class 9 Mathematics teacher whose students score 75/75. Build the exam kit for ONE unit
of the 2025 Punjab textbook (National Curriculum 2023), for the Annual Exam 2027. You get the unit's full text, its examinable exercise
questions (references), the 2027 paper pattern, and what the 2027 smart syllabus excludes from this unit.
- definitions: every definition, theorem/law statement and rule a student must reproduce word for word, copied EXACTLY as printed in the
  unit text (same words and symbols; a full sentence or two). term = the name. Only examinable content.
- formulas: every formula/identity/law of the unit in plain Unicode (√, ², ×, ÷, π, θ, logₐ), each with a few words saying when to use it.
- important: 8–14 examinable exercise questions most likely to come in the board paper (short and long), as references exactly as given
  in the list, each with a one-line reason (e.g. "repeated in board papers", "standard 4-mark long question").
- fullMarks: 5–8 concrete instructions for writing answers in THIS unit so an examiner gives full marks (what to write first, which
  steps examiners look for, units, final statement, diagram labelling, number line, etc.).
- mistakes: 4–7 specific mistakes Class 9 students make in this unit that lose marks, each with the fix.
- shortTheory: 4–8 likely 2-mark theory short questions (e.g. "Define …", "State …") with the exact book answer.
Write simply for a 14–15 year old. Never mention any book, website, teacher or publisher by name.`;
const kitSchema = obj({
  definitions: arr(obj({ term: str, text: str })),
  formulas: arr(obj({ formula: str, use: str })),
  important: arr(obj({ ref: str, why: str })),
  fullMarks: strs, mistakes: strs,
  shortTheory: arr(obj({ question: str, answer: str })),
});

const WRITE = `You turn Class 9 textbook exercise questions (Punjab, 2025 book) into board-exam written questions with marking schemes.
For each item you get the question exactly as printed, its checked step-by-step solution and final answer, and whether it is a drawing.
Return one written question per item:
- marks: 2 for a short question (one or two steps, or a simple sketch), 4 for a longer one (a long-question part).
- text: the question exactly as printed (one part, stating what to do). For a graph or construction add at the end:
  " Draw it on paper and photograph your work, or type your table of values / measurements."
- modelAnswer: the full answer the way a top student writes it in the board exam, one line per step. For a drawing: the table of values
  or construction steps with every measurement, then what the finished figure shows.
- rubric: 2–4 marking points whose marks add up to the question's marks; each with description, why (why it matters), how (how to earn
  it) and accept patterns that the model answer satisfies (key values the student must write; for drawings: the table values,
  intercepts, measurements or vertices). The last point is the correct final answer or completed figure.
- followUp: one similar practice question.
${SYNTAX}`;
const writeSchema = obj({ items: arr(obj({ id: str, marks: int, text: str, modelAnswer: strs, followUp: str,
  rubric: arr(obj({ description: str, marks: int, why: str, how: str, accept: arr(strs) })) })) });
const DRAWING = /\b(draw|construct|plot|sketch|graph|shade|number line|real line)\b/i;

function rubricProblems(w) {
  const answer = w.modelAnswer.join("\n");
  const p = [];
  if (w.rubric.reduce((s, c) => s + c.marks, 0) !== w.marks) p.push("rubric marks do not add up");
  for (const c of w.rubric) {
    if (!c.accept.length || !c.accept.some((g) => g.length)) p.push(`point "${c.description}" has no accept pattern`);
    else if (!matchesAny(answer, c.accept)) p.push(`model answer does not match point "${c.description}"`);
    if (c.accept.some((g) => g.length && matchesAll("", g))) p.push(`point "${c.description}" matches a blank answer`);
  }
  return p;
}

async function buildUnit(n) {
  const outline = JSON.parse(readFileSync(join(ROOT, "work", `u${n}-outline.json`), "utf8"));
  const sol = JSON.parse(readFileSync(join(ROOT, "units", `u-${n}-solutions.json`), "utf8"));
  const unitText = outline.sections.map((s) => `## ${s.number} ${s.title}\n${s.text}`).join("\n\n");
  const exercises = sol.sections.flatMap((s) => s.solutions.map((x) => ({ ...x, section: s.number }))).filter((x) => x.kind === "EXERCISE");
  const examinable = exercises.filter((x) => !isExcluded(syllabus, n, x.source));
  const excludedUnit = syllabus.excludedUnits.includes(n);

  const kit = await cached(`u${n}`, () => structured("exam_kit", `unit ${n}`, KIT, JSON.stringify({
    unit: n, title: outline.unitTitle, paperPattern: syllabus.paper, excludedFromThisUnit: syllabus.excluded[String(n)] ?? [],
    examinableQuestions: examinable.map((x) => `${x.source}: ${x.question.slice(0, 140)}`),
    unitText,
  }), kitSchema));

  // Definitions must be the book's own words; anything not found verbatim in the unit text is dropped.
  const textNorm = norm(unitText);
  const definitions = kit.definitions.filter((d) => textNorm.includes(norm(d.text)));
  const sources = new Map(examinable.map((x) => [x.source.replace(/\s+/g, "").toLowerCase(), x.source]));
  const important = kit.important.map((i) => ({ ...i, ref: sources.get(i.ref.split(":")[0].replace(/\s+/g, "").toLowerCase()) }))
    .filter((i, k, all) => i.ref && all.findIndex((j) => j.ref === i.ref) === k);

  let written = [];
  if (WRITTEN_UNITS.has(n)) {
    const items = examinable.filter((x) => x.checked).map((x, i) => ({ id: String(i), source: x.source, section: x.section, question: x.question,
      steps: x.steps, finalAnswer: x.answer, figure: x.figure, drawing: DRAWING.test(x.question) }));
    const out = [];
    for (let b = 0; b < items.length; b += 8) {
      const batch = items.slice(b, b + 8);
      const res = await cached(`u${n}-written-${b / 8 + 1}`, () => structured("written", `unit ${n} written ${b / 8 + 1}`, WRITE,
        JSON.stringify(batch.map(({ section, ...rest }) => rest)), writeSchema));
      out.push(...res.items);
    }
    // One repair pass for items whose rubric does not fit the model answer.
    const bad = out.filter((w) => rubricProblems(w).length);
    if (bad.length) {
      for (let b = 0; b < bad.length; b += 8) {
        const batch = bad.slice(b, b + 8);
        const res = await cached(`u${n}-written-fix-${b / 8 + 1}`, () => structured("written", `unit ${n} written fix ${b / 8 + 1}`,
          `${WRITE}\nA previous version failed these checks (listed per item); fix the accept patterns (or the model answer) so every point matches the model answer and none matches a blank answer.`,
          JSON.stringify(batch.map((w) => ({ ...items[Number(w.id)], previous: w, problems: rubricProblems(w) }))), writeSchema));
        for (const w of res.items) { const k = out.findIndex((o) => o.id === w.id); if (k >= 0) out[k] = w; }
      }
    }
    const moduleOf = new Map();
    outline.sections.forEach((s, i) => moduleOf.set(s.number, i + 1));
    written = out.filter((w) => !rubricProblems(w).length && (w.marks === 2 || w.marks === 4)).map((w) => {
      const src = items[Number(w.id)];
      const mod = moduleOf.get(src.section) ?? 1;
      return {
        id: `w-${n}-${mod}-k${w.id}`, text: w.text, marks: w.marks, modelAnswer: w.modelAnswer, followUp: w.followUp,
        noteId: `n-${n}-${mod}`, section: 0, number: 0, matching: "method", source: src.source,
        rubric: w.rubric.map((c, k) => ({ id: String.fromCharCode(97 + k), ...c })),
      };
    });
  }

  // Theory short questions ("Define …", "State …") become marked 2-mark written questions, as the board sets them.
  const THEORY = `For each Class 9 maths theory question with its exact textbook answer, write a 2-mark marking scheme: two points of 1 mark
each (usually: the key term or condition, then the complete statement). modelAnswer = the textbook answer as one or two lines.
Each point needs why, how and accept patterns (key words or symbols from the answer that a correct answer must contain).
${SYNTAX}`;
  const theorySchema = obj({ items: arr(obj({ id: str, modelAnswer: strs, rubric: arr(obj({ description: str, marks: int, why: str, how: str, accept: arr(strs) })) })) });
  let theory = [];
  if (!excludedUnit && kit.shortTheory.length) {
    const res = await cached(`u${n}-theory`, () => structured("theory", `unit ${n} theory`, THEORY,
      JSON.stringify(kit.shortTheory.map((q, i) => ({ id: String(i), question: q.question, answer: q.answer }))), theorySchema));
    theory = res.items.map((t) => ({ ...t, marks: 2, text: kit.shortTheory[Number(t.id)]?.question ?? "" }))
      .filter((t) => t.text && rubricProblems(t).length === 0)
      .map((t) => ({
        id: `w-${n}-1-t${t.id}`, text: t.text, marks: 2, modelAnswer: t.modelAnswer, followUp: t.text,
        noteId: `n-${n}-1`, section: 0, number: 0, matching: "method", source: "theory",
        rubric: t.rubric.map((c, k) => ({ id: String.fromCharCode(97 + k), ...c })),
      }));
  }
  written = [...written, ...theory];

  const result = {
    unit: n, examinable: !excludedUnit,
    kit: {
      inExam: !excludedUnit,
      excluded: syllabus.excluded[String(n)] ?? [],
      definitions, formulas: kit.formulas, important, fullMarks: kit.fullMarks, mistakes: kit.mistakes, shortTheory: kit.shortTheory,
    },
    written,
    report: { definitionsDropped: kit.definitions.length - definitions.length, importantDropped: kit.important.length - important.length,
      writtenBuilt: written.length },
  };
  writeFileSync(join(ROOT, "units", `u-${n}-kit.json`), JSON.stringify(result, null, 1));
  console.log(`unit ${n}: ${definitions.length} definitions (${result.report.definitionsDropped} dropped), ${kit.formulas.length} formulas, ${important.length} important, ${written.length} written. $${costSoFar().toFixed(3)}`);
}

let next = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (next < UNITS.length) { const n = UNITS[next++]; await buildUnit(n).catch((e) => console.log(`unit ${n}: FAILED ${e.message}`)); }
}));
