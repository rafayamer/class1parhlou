// Joins the built units into one content bundle (format 1) for the app and the AI gateway, adds a chapter test
// per unit and the readiness quiz, and runs the owner panel's publishing checks on it.
//   node --experimental-strip-types scripts/assemble-course.mjs [out.json]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { bundleProblems } from "../owner-panel/src/validate.ts";

const ROOT = "content/class9-maths";
const OUT = process.argv[2] ?? `${ROOT}/bundle.json`;
const SUBJECT = "pb-9-math";

const units = [];
for (let u = 1; u <= 13; u++) {
  const f = `${ROOT}/units/u-${u}.json`;
  if (existsSync(f)) units.push(JSON.parse(readFileSync(f, "utf8")));
}
if (!units.length) throw new Error("no built units");

const norm = (s) => s.toLowerCase().replace(/\s+/g, " ").trim();
const notes = [], quiz = [], written = [], blueprints = [], papers = [], readiness = [];

/**
 * The book's worked examples, taken straight from the transcribed section text (no AI): "EXAMPLE n:", the question,
 * "SOLUTION:", then the book's working up to the next example, exercise, heading or box.
 */
function examplesFrom(text) {
  const lines = text.split("\n").map((l) => l.trim()).filter((l) => l && !/^PAGE\s+\d+$/i.test(l));
  const out = [];
  let cur = null, inSolution = false;
  const end = (l) => /^EXAMPLE\s+\d+/i.test(l) || /^#{0,4}\s*(REVIEW\s+)?EXERCISE\b/i.test(l) || /^#{1,4}\s/.test(l) || /^(REMEMBER|NOTE|KEY FACT)\s*:/i.test(l);
  for (const l of lines) {
    if (cur && end(l)) { out.push(cur); cur = null; inSolution = false; }
    const head = /^EXAMPLE\s+(\d+)\s*:?\s*(.*)$/i.exec(l);
    if (head) { cur = { label: `Example ${head[1]}`, question: head[2] ? [head[2]] : [], steps: [] }; continue; }
    if (!cur) continue;
    const sol = /^\**SOLUTION\**\s*:?\**\s*(.*)$/i.exec(l);
    if (sol) { inSolution = true; if (sol[1]) cur.steps.push(sol[1]); continue; }
    (inSolution ? cur.steps : cur.question).push(l.replace(/\s+$/, ""));
  }
  if (cur) out.push(cur);
  return out.filter((e) => e.question.length && e.steps.length).map((e) => ({
    kind: "EXAMPLE", source: e.label, question: e.question.join("\n"), steps: e.steps, answer: "",
    figure: "", accept: [], checked: true, note: "",
  }));
}

/** Turns typesetting left over from the scan (e.g. "m\overline{BC}", "\frac{a}{b}") into plain maths symbols. */
function cleanMaths(s) {
  let t = s;
  for (let i = 0; i < 3; i++) t = t.replace(/\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}/g, (_, a, b) => `${/^\w+$/.test(a) ? a : `(${a})`}/${/^\w+$/.test(b) ? b : `(${b})`}`);
  t = t.replace(/\\overline\s*\{([^{}]*)\}/g, (_, x) => [...x].map((c) => c + "̅").join(""));
  t = t.replace(/\\overrightarrow\s*\{([^{}]*)\}/g, (_, x) => `${x}⃗`);
  t = t.replace(/\\sqrt\s*\{([^{}]*)\}/g, (_, x) => (/^\w+$/.test(x) ? `√${x}` : `√(${x})`));
  t = t.replace(/\\operatorname\s*\{([^{}]*)\}/g, "$1").replace(/\\text\s*\{([^{}]*)\}/g, "$1");
  const sym = { times: "×", cdot: "·", div: "÷", le: "≤", leq: "≤", ge: "≥", geq: "≥", neq: "≠", ne: "≠", pm: "±", circ: "°", pi: "π", theta: "θ",
    alpha: "α", beta: "β", angle: "∠", triangle: "Δ", infty: "∞", in: "∈", cup: "∪", cap: "∩", subset: "⊂", subseteq: "⊆", emptyset: "∅",
    Rightarrow: "⇒", rightarrow: "→", Leftrightarrow: "⇔", log: "log", sin: "sin", cos: "cos", tan: "tan", quad: "  ", qquad: "  " };
  t = t.replace(/\\(left|right)\s*/g, "").replace(/\\([A-Za-z]+)/g, (m, w) => sym[w] ?? m);
  return t.replace(/\\([{}])/g, "$1");
}
const cleanAll = (v) => typeof v === "string" ? cleanMaths(v) : Array.isArray(v) ? v.map(cleanAll)
  : v && typeof v === "object" ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, /^(id|noteId|moduleId|chapterId|blueprintId|subjectId)$/.test(k) ? x : cleanAll(x)])) : v;

const coverage = [];
for (const u of units) {
  const outlineFile = `${ROOT}/work/u${u.chapter.number}-outline.json`;
  const outline = existsSync(outlineFile) ? JSON.parse(readFileSync(outlineFile, "utf8")) : null;
  // Full book coverage (build-solutions.mjs): every example and exercise question, and every book MCQ.
  const solFile = `${ROOT}/units/u-${u.chapter.number}-solutions.json`;
  const sol = existsSync(solFile) ? JSON.parse(readFileSync(solFile, "utf8")) : null;
  const sectionOf = (m, i) => sol?.sections.length === u.chapter.modules.length ? sol.sections[i]
    : sol?.sections.find((s) => m.title.startsWith(s.number + " ") || (s.number.endsWith(".R") && /review/i.test(m.title)));
  u.chapter.modules.forEach((m, i) => {
    const s = sectionOf(m, i);
    m.solutions = s?.solutions ?? [];
    // Worked examples: the book's own, verbatim, when the coverage step didn't list them.
    const secText = outline?.sections.length === u.chapter.modules.length ? outline.sections[i].text : null;
    if (secText) {
      const parsed = examplesFrom(secText);
      const labels = new Set(parsed.map((e) => e.source.toLowerCase()));
      // The book's own text wins; AI-listed examples are kept only when the text doesn't have them.
      const other = m.solutions.filter((x) => x.kind !== "EXAMPLE" || !labels.has((/^example\s+\d+/i.exec(x.source)?.[0] ?? "").toLowerCase()));
      m.solutions = [...parsed, ...other];
    }
    for (const q of s?.mcqs ?? []) {
      u.quiz.push({ id: `b-${u.chapter.number}-${i + 1}-${u.quiz.length + 1}`, stem: q.stem, options: q.options, correctIndex: q.correctIndex,
        rationales: q.rationales, status: "APPROVED", noteId: u.notes.find((n) => n.moduleId === m.id)?.id ?? null, moduleId: m.id });
    }
  });
  // A question given to several sub-lessons (Logic's single exercise) is kept once, in the last of them.
  const kept = new Set();
  for (const m of [...u.chapter.modules].reverse()) {
    m.solutions = m.solutions.filter((x) => { const k = `${x.kind}|${norm(x.question)}`; if (kept.has(k)) return false; kept.add(k); return true; });
  }
  if (sol) {
    const ex = u.chapter.modules.flatMap((m) => m.solutions.filter((x) => x.kind === "EXERCISE"));
    coverage.push(`Unit ${u.chapter.number}: ${u.chapter.modules.reduce((n, m) => n + m.solutions.filter((x) => x.kind === "EXAMPLE").length, 0)} examples, ${ex.length} exercise questions (${ex.filter((x) => x.checked).length} with checked step-by-step solutions)`);
  } else coverage.push(`Unit ${u.chapter.number}: full solutions not built yet`);

  // MCQs: no repeated stems within a unit; the book's own come first.
  const seen = new Set();
  const ordered = [...u.quiz.filter((q) => q.id.startsWith("b-")), ...u.quiz.filter((q) => !q.id.startsWith("b-"))];
  const unitQuiz = ordered.filter((q) => !seen.has(norm(q.stem)) && seen.add(norm(q.stem)));
  // A module left with fewer than 5 questions after removing repeats keeps its own copies (Logic's sub-lessons share MCQs).
  for (const m of u.chapter.modules) {
    const have = unitQuiz.filter((q) => q.moduleId === m.id).length;
    if (have >= 5) continue;
    const own = ordered.filter((q) => q.moduleId === m.id && !unitQuiz.includes(q));
    const already = new Set(unitQuiz.filter((q) => q.moduleId === m.id).map((q) => norm(q.stem)));
    unitQuiz.push(...own.filter((q) => !already.has(norm(q.stem)) && already.add(norm(q.stem))).map((q) => ({ ...q, id: `${q.id}-${m.id}` })));
  }
  notes.push(...u.notes);
  quiz.push(...unitQuiz);
  const unitWritten = u.written.map(({ source, ...w }) => w);
  written.push(...unitWritten);

  // Recap: two quick questions from the previous module, asked at the start of each lesson.
  u.chapter.modules.forEach((m, i) => {
    const prev = i > 0 ? u.notes.find((n) => n.moduleId === u.chapter.modules[i - 1].id) : null;
    const prevQuiz = i > 0 ? unitQuiz.filter((q) => q.moduleId === u.chapter.modules[i - 1].id) : [];
    const recap = [];
    if (prev?.checkQuestion) recap.push({ question: prev.checkQuestion, answer: prev.checkAnswer });
    if (prevQuiz[0]) recap.push({ question: prevQuiz[0].stem, answer: prevQuiz[0].options[prevQuiz[0].correctIndex] });
    if (m.lesson) m.lesson.recap = recap;
  });

  // Chapter test (practice pattern): MCQs, short (2 marks) and long (8 marks = two 4-mark parts).
  const n = u.chapter.number;
  const mcqs = unitQuiz.filter((q) => q.status === "APPROVED").concat(unitQuiz.filter((q) => q.status !== "APPROVED")).slice(0, 12);
  const shorts = unitWritten.filter((w) => w.marks === 2).slice(0, 9);
  const fours = unitWritten.filter((w) => w.marks === 4);
  const longs = [];
  for (let i = 0; i + 1 < fours.length && longs.length < 3; i += 2) {
    const a = fours[i], b = fours[i + 1];
    longs.push({
      id: `L-${n}-${longs.length + 1}`,
      text: `(a) ${a.text}\n(b) ${b.text}`,
      marks: 8,
      modelAnswer: [...a.modelAnswer.map((l, k) => (k === 0 ? `(a) ${l}` : l)), ...b.modelAnswer.map((l, k) => (k === 0 ? `(b) ${l}` : l))],
      followUp: a.followUp, noteId: a.noteId, matching: "method",
      rubric: [...a.rubric.map((c) => ({ ...c, id: `a${c.id}`, description: `(a) ${c.description}` })),
               ...b.rubric.map((c) => ({ ...c, id: `b${c.id}`, description: `(b) ${c.description}` }))],
    });
  }
  written.push(...longs.map((l) => ({ ...l, section: 0, number: 0 })));
  if (mcqs.length >= 5 && shorts.length >= 3) {
    const sections = [{ type: "MCQ", count: mcqs.length, attempt: mcqs.length, marksEach: 1, minutes: null }];
    const shortAttempt = Math.max(1, Math.round(shorts.length * 2 / 3));
    sections.push({ type: "Short questions", count: shorts.length, attempt: shortAttempt, marksEach: 2, minutes: null });
    if (longs.length) sections.push({ type: "Long questions", count: longs.length, attempt: longs.length > 1 ? longs.length - 1 : 1, marksEach: 8, minutes: null });
    const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
    const id = `ch-${n}-test`;
    blueprints.push({ id, title: `Unit ${n} test: ${u.chapter.title}`, durationMinutes: total >= 35 ? 90 : 60, totalMarks: total, official: false, chapterId: u.chapter.id, sections });
    papers.push({
      blueprintId: id, mcqs,
      written: [...shorts.map((w, i) => ({ ...w, section: 1, number: i + 1 })), ...longs.map((w, i) => ({ ...w, section: 2, number: i + 1 }))],
    });
  }
  // Readiness quiz: one book MCQ from each unit.
  const r = unitQuiz.find((q) => q.status === "APPROVED") ?? unitQuiz[0];
  if (r) readiness.push(r);
}

const bundle = {
  format: 1,
  subjectId: SUBJECT,
  course: { board: "Punjab", grade: 9, subject: "Mathematics", edition: "Mathematics 9, National Curriculum 2023, English medium (your textbook)" },
  subject: {
    id: SUBJECT, name: "Mathematics", grade: 9,
    editionLabel: "Mathematics 9 · National Curriculum 2023 · English medium",
    editionVerified: true,
    chapters: units.map((u) => u.chapter),
  },
  notes, quiz, readiness: readiness.slice(0, 12), blueprints, papers, written, mcqs: [],
};

const cleaned = cleanAll(bundle);
const problems = bundleProblems(cleaned, SUBJECT);
writeFileSync(OUT, JSON.stringify(cleaned));
const kb = Math.round(JSON.stringify(cleaned).length / 1024);
coverage.forEach((c) => console.log(c));
console.log(`Course: ${units.length} units, ${units.reduce((s, u) => s + u.chapter.modules.length, 0)} modules, ${notes.length} notes, ${quiz.length} MCQs, ${written.length} written, ${blueprints.length} unit tests, ${kb} KB → ${OUT}`);
if (problems.length) {
  console.log(`${problems.length} publishing problems:`);
  for (const p of problems.slice(0, 40)) console.log(`  ${p.where}: ${p.message}`);
  process.exit(1);
}
console.log("All publishing checks pass.");
