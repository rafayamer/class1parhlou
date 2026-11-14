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

const coverage = [];
for (const u of units) {
  // Full book coverage (build-solutions.mjs): every example and exercise question, and every book MCQ.
  const solFile = `${ROOT}/units/u-${u.chapter.number}-solutions.json`;
  const sol = existsSync(solFile) ? JSON.parse(readFileSync(solFile, "utf8")) : null;
  const sectionOf = (m, i) => sol?.sections.length === u.chapter.modules.length ? sol.sections[i]
    : sol?.sections.find((s) => m.title.startsWith(s.number + " ") || (s.number.endsWith(".R") && /review/i.test(m.title)));
  u.chapter.modules.forEach((m, i) => {
    const s = sectionOf(m, i);
    m.solutions = s?.solutions ?? [];
    for (const q of s?.mcqs ?? []) {
      u.quiz.push({ id: `b-${u.chapter.number}-${i + 1}-${u.quiz.length + 1}`, stem: q.stem, options: q.options, correctIndex: q.correctIndex,
        rationales: q.rationales, status: "APPROVED", noteId: u.notes.find((n) => n.moduleId === m.id)?.id ?? null, moduleId: m.id });
    }
  });
  if (sol) {
    const ex = u.chapter.modules.flatMap((m) => m.solutions.filter((x) => x.kind === "EXERCISE"));
    coverage.push(`Unit ${u.chapter.number}: ${u.chapter.modules.reduce((n, m) => n + m.solutions.filter((x) => x.kind === "EXAMPLE").length, 0)} examples, ${ex.length} exercise questions (${ex.filter((x) => x.checked).length} with checked step-by-step solutions)`);
  } else coverage.push(`Unit ${u.chapter.number}: full solutions not built yet`);

  // MCQs: no repeated stems within a unit; the book's own come first.
  const seen = new Set();
  const ordered = [...u.quiz.filter((q) => q.id.startsWith("b-")), ...u.quiz.filter((q) => !q.id.startsWith("b-"))];
  const unitQuiz = ordered.filter((q) => !seen.has(norm(q.stem)) && seen.add(norm(q.stem)));
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

const problems = bundleProblems(bundle, SUBJECT);
writeFileSync(OUT, JSON.stringify(bundle));
const kb = Math.round(JSON.stringify(bundle).length / 1024);
coverage.forEach((c) => console.log(c));
console.log(`Course: ${units.length} units, ${units.reduce((s, u) => s + u.chapter.modules.length, 0)} modules, ${notes.length} notes, ${quiz.length} MCQs, ${written.length} written, ${blueprints.length} unit tests, ${kb} KB → ${OUT}`);
if (problems.length) {
  console.log(`${problems.length} publishing problems:`);
  for (const p of problems.slice(0, 40)) console.log(`  ${p.where}: ${p.message}`);
  process.exit(1);
}
console.log("All publishing checks pass.");
