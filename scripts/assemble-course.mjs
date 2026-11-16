// Joins the built units into one content bundle (format 1) for the app and the AI gateway, adds a chapter test
// per unit and the readiness quiz, and runs the owner panel's publishing checks on it.
//   node --experimental-strip-types scripts/assemble-course.mjs [out.json]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { bundleProblems } from "../owner-panel/src/validate.ts";
import { isExcluded } from "./syllabus.mjs";
import { createHash } from "node:crypto";
const whyKey = (unit, s) => createHash("sha1").update(`${unit}|${s.kind}|${s.source}|${s.steps.join("\n")}`).digest("hex").slice(0, 16);

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
// The exam students sit next: the smart syllabus says what it leaves out (kept as practice, never in tests or papers).
const syllabus = JSON.parse(readFileSync(`${ROOT}/syllabus-2027.json`, "utf8"));
const EXAM = "2027 exam";
const pools = new Map(); // unit number → examinable { mcqs, shorts, fours }
const audit = [];
const notes = [], quiz = [], written = [], blueprints = [], papers = [], readiness = [];

/**
 * The book's worked examples, taken straight from the transcribed section text (no AI): "EXAMPLE n:", the question,
 * "SOLUTION:", then the book's working up to the next example, exercise, heading or box.
 */
function examplesFrom(text) {
  const lines = text.split("\n").map((l) => l.trim().replace(/\*\*/g, "")).filter((l) => l && !/^PAGE\s+\d+$/i.test(l));
  const out = [];
  let cur = null, inSolution = false, inBox = false;
  const box = (l) => /^(REMEMBER|NOTE|KEY FACT|DO YOU KNOW|KEEP IN MIND)\b\s*:?/i.test(l);
  const end = (l) => /^EXAMPLE\s+\d+/i.test(l) || /^#{0,4}\s*(REVIEW\s+)?EXERCISE\b/i.test(l) || /^#{1,4}\s/.test(l);
  const solutionLine = /^\**(SOLUTION|PROOF)\**\s*:?\**\s*(.*)$/i;
  // A box printed in the middle of an example (a note, "remember", "keep in mind") is skipped, up to the solution or
  // the next line that clearly continues the working.
  const resumes = (l) => solutionLine.test(l) || /^[⇒=∴(]/.test(l) || /^(thus|therefore|hence|now|but|from|let|so|since|then)\b/i.test(l);
  for (const l of lines) {
    if (cur && end(l)) { out.push(cur); cur = null; inSolution = false; inBox = false; }
    const head = /^EXAMPLE\s+(\d+)\s*:?\s*(.*)$/i.exec(l);
    if (head) { cur = { label: `Example ${head[1]}`, question: head[2] ? [head[2]] : [], steps: [] }; inBox = false; continue; }
    if (!cur) continue;
    if (box(l)) {
      if (!inSolution && !cur.question.length) continue;
      inBox = true; continue;
    }
    if (inBox) { if (!resumes(l)) continue; inBox = false; }
    const sol = solutionLine.exec(l)?.slice(1);
    if (sol) sol[0] = sol[1];
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

/** An 8-mark long question from two 4-mark parts, as the board sets them: (a) and (b). */
function longOf(id, a, b) {
  return {
    id, text: `(a) ${a.text}\n(b) ${b.text}`, marks: 8,
    modelAnswer: [...a.modelAnswer.map((l, k) => (k === 0 ? `(a) ${l}` : l)), ...b.modelAnswer.map((l, k) => (k === 0 ? `(b) ${l}` : l))],
    followUp: a.followUp, noteId: a.noteId, matching: "method",
    rubric: [...a.rubric.map((c) => ({ ...c, id: `a${c.id}`, description: `(a) ${c.description}` })),
             ...b.rubric.map((c) => ({ ...c, id: `b${c.id}`, description: `(b) ${c.description}` }))],
  };
}

const bookPages = Array.from({ length: 261 }, (_, i) => { const f = `${ROOT}/pages/p${String(i + 1).padStart(3, "0")}.md`; return existsSync(f) ? readFileSync(f, "utf8") : ""; });
// Places where the book's own numbering skips (checked on the printed page), so the audit doesn't report them as gaps.
const BOOK_NUMBERING_QUIRKS = ["ex12.2 q13(iv)", "ex12.2 q13(v)"]; // printed as (i), (ii), (iii), (vi)
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
  // Answers aligned with the textbook's key (align-answers.mjs), and what the exam leaves out.
  const n0 = u.chapter.number;
  const alignFile = `${ROOT}/units/u-${n0}-align.json`;
  const aligned = existsSync(alignFile) ? JSON.parse(readFileSync(alignFile, "utf8")) : null;
  const alignBy = new Map((aligned?.items ?? []).map((a) => [a.source, a]));
  const unitOut = syllabus.excludedUnits.includes(n0);
  for (const m of u.chapter.modules) {
    m.inExam = !unitOut && !syllabus.excludedModules.includes(m.id);
    m.solutions = m.solutions.map((x) => {
      let y = { ...x, inExam: !isExcluded(syllabus, n0, x.source) };
      const a = x.kind === "EXERCISE" ? alignBy.get(x.source) : null;
      if (a) {
        y.bookAnswer = a.bookAnswer ?? "";
        y.note = a.note ?? y.note;
        // Still unsettled: a solution that passed its own check keeps its working, with the examiner's note.
        if (a.status === "unresolved" && x.checked) y.note = a.examiner ? `Note: ${a.examiner}` : y.note;
        else if (a.replace) y = { ...y, ...a.replace };
      }
      return y;
    });
  }
  // A question the independent set has and we lack entirely (part labels differ between solution sets, so compare questions).
  const ourKeys = u.chapter.modules.flatMap((m) => m.solutions.map((x) => x.source.toLowerCase().replace(/review\s*exercise/g, "reviewex").replace(/exercise/g, "ex").replace(/[^a-z0-9.()]/g, "")));
  const missingQuestions = [...new Set((aligned?.inIndependentNotOurs ?? []).map((k) => /^(.*?q\d+)/.exec(k)?.[1] ?? k))]
    .filter((q) => !ourKeys.some((r) => r === q || (r.startsWith(q) && !/\d/.test(r[q.length] ?? ""))));
  // Only a question the book itself prints counts as missing (part labels and headings differ between solution sets);
  // question 1 of a review exercise (and of Logic's single exercise) is multiple choice and lives in the quiz.
  const printed = (q) => {
    const m = /^(reviewex(\d+)|ex(\d+(?:\.\d+)?))q(\d+)$/.exec(q);
    if (!m) return true;
    if (Number(m[4]) === 1 && (m[2] || !m[3].includes("."))) return false;
    const heading = m[2] ? new RegExp(`REVIEW EXERCISE\\s*${m[2]}\\b`, "i") : new RegExp(`EXERCISE\\s*${m[3].replace(".", "\\.")}(?![\\d.])`, "i");
    const text = bookPages.join("\n");
    const at = text.search(heading);
    if (at < 0) return true;
    const rest = text.slice(at + 10);
    const stop = rest.search(/\n\s*#*\s*(REVIEW )?EXERCISE\s*\d|\n#{1,2} \d+\.\d/);
    return new RegExp(`(^|\\n)\\s*(Q\\s*)?${m[4]}\\.`).test(stop < 0 ? rest : rest.slice(0, stop));
  };
  if (aligned) audit.push({ unit: n0, counts: aligned.counts, gaps: aligned.numberingGaps.filter((g) => !BOOK_NUMBERING_QUIRKS.includes(g) && printed(g.replace(/\s/g, ""))).filter((g) => !ourKeys.some((r) => r.startsWith(g.replace(/\s/g, "")) && !/\d/.test(r[g.replace(/\s/g, "").length] ?? ""))), missing: missingQuestions.filter(printed),
    flagged: aligned.items.filter((i) => i.status !== "matches key" && i.status !== "confirmed" && i.status !== "no key") });
  // Worked examples must run 1…N without gaps.
  const exNums = u.chapter.modules.flatMap((m) => m.solutions.filter((x) => x.kind === "EXAMPLE").map((x) => Number(/\d+/.exec(x.source)?.[0] ?? 0)));
  const exMissing = exNums.length ? Array.from({ length: Math.max(...exNums) }, (_, i) => i + 1).filter((k) => !exNums.includes(k)) : [];
  if (exMissing.length) audit.push({ unit: n0, examplesMissing: exMissing });

  // Exam kit and, for graph/construction units, board-style written questions (exam-kit.mjs).
  const kitFile = `${ROOT}/units/u-${n0}-kit.json`;
  if (existsSync(kitFile)) {
    const kit = JSON.parse(readFileSync(kitFile, "utf8"));
    u.chapter.kit = { ...kit.kit, examLabel: EXAM };
    const have = new Set(u.written.map((w) => w.id));
    u.written.push(...kit.written.filter((w) => !have.has(w.id)));
    for (const m of u.chapter.modules) {
      if (m.writtenQuestions.length >= 3) continue;
      const pos = m.id.split("-")[2];
      const extra = kit.written.filter((w) => w.id.startsWith(`w-${n0}-${pos}-k`)).slice(0, 3 - m.writtenQuestions.length).map(({ source, ...w }) => w);
      m.writtenQuestions = [...m.writtenQuestions, ...extra];
    }
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
  const examinableModule = (id) => { const m = u.chapter.modules.find((x) => x.id === id); return m ? m.inExam : true; };
  const moduleOfWritten = (w) => { const k = /^w-(\d+)-(\d+)/.exec(w.id); return k ? `m-${k[1]}-${k[2]}` : null; };
  const inExamWritten = new Set(u.written.filter((w) => !unitOut && examinableModule(moduleOfWritten(w)) && !(w.source && isExcluded(syllabus, n0, w.source))).map((w) => w.id));
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
  // Only what the exam examines (a unit the exam leaves out keeps its whole test, as practice).
  const examQuiz = unitOut ? unitQuiz : unitQuiz.filter((q) => examinableModule(q.moduleId));
  const examWritten = unitOut ? unitWritten : unitWritten.filter((w) => inExamWritten.has(w.id));
  const mcqs = examQuiz.filter((q) => q.status === "APPROVED").concat(examQuiz.filter((q) => q.status !== "APPROVED")).slice(0, 12);
  const shorts = examWritten.filter((w) => w.marks === 2).slice(0, 9);
  const fours = examWritten.filter((w) => w.marks === 4);
  if (!unitOut) pools.set(n, {
    mcqs: examQuiz.filter((q) => q.status === "APPROVED").concat(examQuiz.filter((q) => q.status !== "APPROVED")),
    shorts: examWritten.filter((w) => w.marks === 2), fours,
  });
  const longs = [];
  for (let i = 0; i + 1 < fours.length && longs.length < 3; i += 2) longs.push(longOf(`L-${n}-${longs.length + 1}`, fours[i], fours[i + 1]));
  written.push(...longs.map((l) => ({ ...l, section: 0, number: 0 })));
  if (mcqs.length >= 5 && shorts.length >= 3) {
    const sections = [{ type: "MCQ", count: mcqs.length, attempt: mcqs.length, marksEach: 1, minutes: null }];
    const shortAttempt = Math.max(1, Math.round(shorts.length * 2 / 3));
    sections.push({ type: "Short questions", count: shorts.length, attempt: shortAttempt, marksEach: 2, minutes: null });
    if (longs.length) sections.push({ type: "Long questions", count: longs.length, attempt: longs.length > 1 ? longs.length - 1 : 1, marksEach: 8, minutes: null });
    const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
    const id = `ch-${n}-test`;
    blueprints.push({ id, title: `Unit ${n} test: ${u.chapter.title}${unitOut ? ` (not in the ${EXAM})` : ""}`, durationMinutes: total >= 35 ? 90 : 60, totalMarks: total, official: false, chapterId: u.chapter.id, sections });
    papers.push({
      blueprintId: id, mcqs,
      written: [...shorts.map((w, i) => ({ ...w, section: 1, number: i + 1 })), ...longs.map((w, i) => ({ ...w, section: 2, number: i + 1 }))],
    });
  }
  // Readiness quiz: one book MCQ from each unit.
  const r = unitQuiz.find((q) => q.status === "APPROVED") ?? unitQuiz[0];
  if (r) readiness.push(r);
}

// ── Board papers: the full 75-mark paper exactly as the 2027 instructions lay it out ───────────────────────
function boardPapers() {
  const P = syllabus.paper;
  const made = [];
  for (const [k, set] of ["A", "B", "C"].entries()) {
    const pick = (list, count, salt) => {
      if (list.length < count) return null;
      const start = (k * count + salt) % list.length;
      return Array.from({ length: count }, (_, j) => list[(start + j) % list.length]);
    };
    const problems = [];
    const mcqs = [];
    for (const [unit, count] of Object.entries(P.objective.mcqsPerUnit)) {
      const got = pick(pools.get(Number(unit))?.mcqs ?? [], count, 0);
      if (got) mcqs.push(...got); else problems.push(`Unit ${unit}: not enough MCQs`);
    }
    const sections = [{ type: "MCQ", count: mcqs.length, attempt: mcqs.length, marksEach: 1, minutes: P.objective.minutes }];
    const writtenOut = [];
    for (const sq of P.short) {
      const qs = [];
      for (const [unit, count] of Object.entries(sq.perUnit)) {
        const got = pick(pools.get(Number(unit))?.shorts ?? [], count, 0);
        if (got) qs.push(...got); else problems.push(`Q${sq.q}: Unit ${unit} has too few 2-mark questions`);
      }
      const units = Object.keys(sq.perUnit).join(", ");
      sections.push({ type: `Q${sq.q} Short questions (Units ${units})`, count: qs.length, attempt: sq.attempt, marksEach: 2, minutes: null });
      writtenOut.push(qs.map((w) => ({ ...w })));
    }
    for (const part of P.long) {
      const qs = [];
      for (const q of part.questions) {
        const fourFrom = (units, salt) => {
          const opts = units.filter((u) => (pools.get(u)?.fours.length ?? 0) > 0);
          if (!opts.length) return null;
          const u = opts[(k + salt) % opts.length];
          const list = pools.get(u).fours;
          return list[(k + salt) % list.length];
        };
        const a = fourFrom(q.a, 0), b = fourFrom(q.b, 1);
        if (!a || !b) { problems.push(`Q${q.q}: no 4-mark question for part ${a ? "b" : "a"}`); continue; }
        qs.push(longOf(`BP-${set}-Q${q.q}`, a, b));
      }
      sections.push({ type: `Part ${part.part}: long questions (Q${part.questions.map((q) => q.q).join(", Q")})`, count: qs.length,
        attempt: part.attempt, marksEach: 8, minutes: null });
      writtenOut.push(qs);
    }
    const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
    if (problems.length || total !== P.totalMarks) { console.log(`Board paper ${set} not made: ${problems.join("; ") || `total ${total}`}`); continue; }
    const id = `board-2027-${set.toLowerCase()}`;
    blueprints.push({ id, title: `Board paper ${set} (2027 pattern, 75 marks)`, durationMinutes: P.minutes, totalMarks: P.totalMarks, official: true, chapterId: null, sections });
    for (const l of writtenOut.slice(P.short.length).flat()) written.push({ ...l, section: 0, number: 0 });
    papers.push({ blueprintId: id, mcqs, written: writtenOut.flatMap((qs, i) => qs.map((w, j) => ({ ...w, section: i + 1, number: j + 1 }))) });
    made.push(set);
  }
  return made;
}
const boardMade = boardPapers();

const bundle = {
  format: 1,
  subjectId: SUBJECT,
  course: { board: "Punjab", grade: 9, subject: "Mathematics", edition: "Mathematics 9, National Curriculum 2023, English medium (your textbook)" },
  subject: {
    id: SUBJECT, name: "Mathematics", grade: 9,
    editionLabel: "Mathematics 9 · National Curriculum 2023 · English medium",
    editionVerified: true,
    examDate: syllabus.examDate, examName: syllabus.examName,
    chapters: units.map((u) => u.chapter),
  },
  notes, quiz, readiness: readiness.slice(0, 12), blueprints, papers, written, mcqs: [],
};

const cleaned = cleanAll(bundle);
// "Why this step?" reasons (step-why.mjs), attached only where they were written for exactly these steps.
const whyFile = `${ROOT}/why.json`;
if (existsSync(whyFile)) {
  const whys = JSON.parse(readFileSync(whyFile, "utf8"));
  for (const c of cleaned.subject.chapters) for (const m of c.modules) for (const s of m.solutions) {
    const w = whys[whyKey(c.number, s)];
    if (w && w.length === s.steps.length) s.why = w;
  }
}
// Book figures (book-figures.mjs + figure-links.mjs): the diagrams a question needs and the book's answer figures.
const linksFile = `${ROOT}/figure-links.json`;
if (existsSync(linksFile)) {
  const links = JSON.parse(readFileSync(linksFile, "utf8"));
  const unmark = (t, keep) => t.replace(/\[FIGURE:\s*([^\]]*)\]/g, (_, d) => (keep ? "" : `(Figure: ${d.trim()})`)).replace(/\n{3,}/g, "\n\n").trim();
  let withFig = 0;
  for (const c of cleaned.subject.chapters) for (const m of c.modules) for (const s of m.solutions) {
    const n = c.number;
    let qf = [], af = [];
    const ex = /^example\s*(\d+)/i.exec(s.source);
    if (ex) qf = links.example[`${n}|${ex[1]}`] ?? [];
    else {
      const r = /^(?:review\s*ex(?:ercise)?\.?\s*(\d+)|ex(?:ercise)?\.?\s*(\d+(?:\.\d+)?))\s*q\s*(\d+)\s*(?:\(([ivx]+|[a-h])\))?/i.exec(s.source);
      if (r) {
        const key = `${n}|${r[1] ? `r${r[1]}` : r[2]}|${r[3]}`;
        const part = r[4]?.toLowerCase() ?? "";
        qf = [...(links.question[`${key}|${part}`] ?? []), ...(part ? links.question[`${key}|`] ?? [] : [])];
        af = [...(links.answer[`${key}|${part}`] ?? []), ...(part ? links.answer[`${key}|`] ?? [] : [])];
      }
    }
    if (qf.length) { s.figures = [...new Set(qf)]; withFig++; }
    if (af.length) s.answerFigures = [...new Set(af)];
    s.question = unmark(s.question, qf.length > 0);
    s.steps = s.steps.map((t) => unmark(t, false));
  }
  console.log(`Figures: ${withFig} questions show a book figure`);
}
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
console.log(`Board papers (2027 pattern): ${boardMade.join(", ") || "none"}`);
writeFileSync(`${ROOT}/work/audit.json`, JSON.stringify(audit, null, 1));
for (const a of audit) if (a.examplesMissing || a.gaps?.length || a.missing?.length) console.log(`Audit unit ${a.unit}: ${JSON.stringify({ examplesMissing: a.examplesMissing, gaps: a.gaps, missing: a.missing })}`);
console.log("All publishing checks pass.");
