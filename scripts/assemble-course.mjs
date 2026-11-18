// Joins the built units into one content bundle (format 1) for the app and the AI gateway, adds a chapter test
// per unit and the readiness quiz, and runs the owner panel's publishing checks on it.
//   node --experimental-strip-types scripts/assemble-course.mjs [out.json]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { bundleProblems } from "../owner-panel/src/validate.ts";
import { isExcluded } from "./syllabus.mjs";
import { createHash } from "node:crypto";
import { readdirSync } from "node:fs";
import { S } from "./subject.mjs";
const PHY = !!S.science; // Physics, Chemistry: chapters with sections A–E
const IDP = S.idPrefix;
// A long question is two parts: Mathematics (a) 4 + (b) 4 = 8 marks; Physics (a) 4 + (b) 5 = 9; Chemistry (a) 5 + (b) 4 = 9.
const LONG = S.long ?? (PHY ? { a: 4, b: 5, total: 9 } : { a: 4, b: 4, total: 8 });
const poolOf = (marks) => (marks === 4 ? "fours" : marks === 8 ? "eights" : "fives");
/** A long question that is one whole question (Computer Science: one detailed 8-mark question). */
const singleLong = (id, w) => ({ ...w, id, text: w.text, marks: w.marks });
const whyKey = (unit, s) => createHash("sha1").update(`${unit}|${s.kind}|${s.source}|${s.steps.join("\n")}`).digest("hex").slice(0, 16);

const ROOT = S.root;
const OUT = process.argv[2] ?? `${ROOT}/bundle.json`;
const SUBJECT = S.id;

const units = [];
for (let u = 1; u <= 30; u++) {
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
// Past-paper question bank (past-papers.mjs).
const pastPapers = existsSync(`${ROOT}/pastpapers.json`) ? JSON.parse(readFileSync(`${ROOT}/pastpapers.json`, "utf8")) : null;
// A past-paper MCQ with a blank option can't be shown, and a question is listed once (the first copy kept).
if (pastPapers) {
  const once = (list) => { const seen = new Set(); return list.filter((q) => !seen.has(q.id) && seen.add(q.id)); };
  pastPapers.mcqs = once((pastPapers.mcqs ?? []).filter((q) => q.options?.every((o) => String(o).trim())));
  pastPapers.written = once(pastPapers.written ?? []);
}
const notes = [], quiz = [], written = [], blueprints = [], papers = [], readiness = [];

/**
 * The book's worked examples, taken straight from the transcribed section text (no AI): "EXAMPLE n:", the question,
 * "SOLUTION:", then the book's working up to the next example, exercise, heading or box.
 */
function examplesFrom(text) {
  const lines = text.split("\n").map((l) => l.trim().replace(/\*\*/g, "")).filter((l) => l && !/^PAGE\s+\d+$/i.test(l));
  const out = [];
  let cur = null, inSolution = false, inBox = false;
  const box = (l) => /^(REMEMBER|NOTE|KEY FACT|DO YOU KNOW|KEEP IN MIND|FOR YOUR INFORMATION|SAFETY)\b\s*:?/i.test(l);
  const end = (l) => /^EXAMPLE\s+\d+/i.test(l) || /^#{0,4}\s*(REVIEW\s+)?EXERCISE\b/i.test(l) || /^#{1,4}\s/.test(l)
    || (PHY && /^(#{0,4}\s*SECTION\s+[A-E]\b|ACTIVITY\s+\d|SUMMARY\b)/i.test(l));
  const solutionLine = /^\**(SOLUTION|PROOF)\**\s*:?\**\s*(.*)$/i;
  // A box printed in the middle of an example (a note, "remember", "keep in mind") is skipped, up to the solution or
  // the next line that clearly continues the working.
  const resumes = (l) => solutionLine.test(l) || /^[⇒=∴(]/.test(l) || /^(thus|therefore|hence|now|but|from|let|so|since|then)\b/i.test(l);
  for (const l of lines) {
    if (cur && end(l)) { out.push(cur); cur = null; inSolution = false; inBox = false; }
    const head = /^EXAMPLE\s+(\d+(?:\.\d+)?)\s*:?\s*(.*)$/i.exec(l);
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
    id, text: `(a) ${a.text}\n(b) ${b.text}`, marks: a.marks + b.marks,
    modelAnswer: [...a.modelAnswer.map((l, k) => (k === 0 ? `(a) ${l}` : l)), ...b.modelAnswer.map((l, k) => (k === 0 ? `(b) ${l}` : l))],
    followUp: a.followUp, noteId: a.noteId, matching: "method",
    rubric: [...a.rubric.map((c) => ({ ...c, id: `a${c.id}`, description: `(a) ${c.description}` })),
             ...b.rubric.map((c) => ({ ...c, id: `b${c.id}`, description: `(b) ${c.description}` }))],
  };
}

const bookPages = Array.from({ length: readdirSync(`${ROOT}/pages`).length + 5 }, (_, i) => { const f = `${ROOT}/pages/p${String(i + 1).padStart(3, "0")}.md`; return existsSync(f) ? readFileSync(f, "utf8") : ""; });
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
      const other = m.solutions.filter((x) => x.kind !== "EXAMPLE" || !labels.has((/^example\s+\d+(?:\.\d+)?/i.exec(x.source)?.[0] ?? "").toLowerCase()));
      m.solutions = [...parsed, ...other];
    }
    for (const q of s?.mcqs ?? []) {
      u.quiz.push({ id: `${IDP}b-${u.chapter.number}-${i + 1}-${u.quiz.length + 1}`, stem: q.stem, options: q.options, correctIndex: q.correctIndex,
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
    // A whole numbered section the smart syllabus deletes ("1.2 States of Matter") is out of the exam too (not partial deletions).
    const secNo = /^(\d+\.\d+)\s/.exec(m.title)?.[1];
    const topicOut = !!secNo && (syllabus.excludedTopics?.[String(n0)] ?? []).some((t) => !t.partial && (t.section ?? /^(\d+\.\d+)\s/.exec(t.title)?.[1]) === secNo);
    m.inExam = !unitOut && !syllabus.excludedModules.includes(m.id) && !topicOut;
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
  // ("Example 12" in Mathematics; "Example 2.3" in Physics, numbered within the chapter.)
  const exNums = u.chapter.modules.flatMap((m) => m.solutions.filter((x) => x.kind === "EXAMPLE").map((x) => Number((PHY ? /\.(\d+)/.exec(x.source)?.[1] : /\d+/.exec(x.source)?.[0]) ?? 0)));
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
      const pos = m.id.split("-").at(-1);
      const extra = kit.written.filter((w) => w.id.startsWith(`${IDP}w-${n0}-${pos}-k`)).slice(0, 3 - m.writtenQuestions.length).map(({ source, ...w }) => w);
      m.writtenQuestions = [...m.writtenQuestions, ...extra];
    }
  }

  if (sol) {
    const ex = u.chapter.modules.flatMap((m) => m.solutions.filter((x) => x.kind === "EXERCISE"));
    coverage.push(`Unit ${u.chapter.number}: ${u.chapter.modules.reduce((n, m) => n + m.solutions.filter((x) => x.kind === "EXAMPLE").length, 0)} examples, ${ex.length} exercise questions (${ex.filter((x) => x.checked).length} with checked step-by-step solutions)`);
  } else coverage.push(`Unit ${u.chapter.number}: full solutions not built yet`);

  // Practice comes from past papers (D-027): board MCQs first (most repeated first), then the book's own review MCQs;
  // MCQs written for the course are used only where a section still has fewer than 5.
  const noteOf = (moduleId) => u.notes.find((x) => x.moduleId === moduleId)?.id ?? null;
  const ppMcqs = (pastPapers?.mcqs ?? []).filter((q) => q.moduleId?.startsWith(`${IDP}m-${n0}-`))
    .sort((a, b) => b.count - a.count || b.year - a.year)
    .map((q) => ({ id: q.id, stem: q.stem, options: q.options, correctIndex: q.correctIndex, rationales: q.rationales,
      status: "APPROVED", noteId: noteOf(q.moduleId), moduleId: q.moduleId, tags: q.tags }));
  const seen = new Set();
  const realFirst = [...ppMcqs, ...u.quiz.filter((q) => q.id.startsWith(`${IDP}b-`))];
  const made = u.quiz.filter((q) => !q.id.startsWith(`${IDP}b-`));
  const fill = made.filter((q) => realFirst.filter((x) => x.moduleId === q.moduleId).length < 5);
  const ordered = [...realFirst, ...fill];
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
  // Written practice: past-paper questions first (most repeated first), then the book's exercise questions.
  const ppWritten = (pastPapers?.written ?? []).filter((w) => w.moduleId?.startsWith(`${IDP}m-${n0}-`))
    .sort((a, b) => b.count - a.count || b.year - a.year)
    .map((w) => ({ id: w.id, text: w.text, marks: w.marks, modelAnswer: w.modelAnswer, followUp: w.followUp, noteId: noteOf(w.moduleId),
      section: 0, number: 0, matching: "method", rubric: w.rubric, tags: w.tags, source: w.bookSource || "", ppModule: w.moduleId }));
  u.written = [...ppWritten, ...u.written];
  // A marking point worth nothing ("Gives a direct answer", 0 marks) is not a marking point.
  for (const w of u.written) if (w.rubric?.some((c) => !(c.marks > 0))) w.rubric = w.rubric.filter((c) => c.marks > 0);
  const examinableModule = (id) => { const m = u.chapter.modules.find((x) => x.id === id); return m ? m.inExam : true; };
  const moduleOfWritten = (w) => { if (w.ppModule) return w.ppModule; const k = new RegExp(`^${IDP}w-(\\d+)-(\\d+)`).exec(w.id); return k ? `${IDP}m-${k[1]}-${k[2]}` : null; };
  const inExamWritten = new Set(u.written.filter((w) => !unitOut && examinableModule(moduleOfWritten(w)) && !(w.source && isExcluded(syllabus, n0, w.source))).map((w) => w.id));
  const unitWritten = u.written.map(({ source, ppModule, ...w }) => w);
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
  const fives = examWritten.filter((w) => w.marks === 5);
  if (!unitOut) pools.set(n, {
    mcqs: examQuiz.filter((q) => q.status === "APPROVED").concat(examQuiz.filter((q) => q.status !== "APPROVED")),
    shorts: examWritten.filter((w) => w.marks === 2), fours, fives, eights: examWritten.filter((w) => w.marks === 8),
  });
  const longs = [];
  if (LONG.single) examWritten.filter((w) => w.marks === LONG.total).slice(0, 3).forEach((w) => longs.push(singleLong(`${IDP}L-${n}-${longs.length + 1}`, w)));
  else if (LONG.a === 4 && LONG.b === 4) for (let i = 0; i + 1 < fours.length && longs.length < 3; i += 2) longs.push(longOf(`${IDP}L-${n}-${longs.length + 1}`, fours[i], fours[i + 1]));
  else {
    const [pa, pb] = LONG.a === 4 ? [fours, fives] : [fives, fours];
    for (let i = 0; i < Math.min(pa.length, pb.length) && longs.length < 3; i++) longs.push(longOf(`${IDP}L-${n}-${longs.length + 1}`, pa[i], pb[i]));
  }
  written.push(...longs.map((l) => ({ ...l, section: 0, number: 0 })));
  if (mcqs.length >= 5 && shorts.length >= 3) {
    const sections = [{ type: "MCQ", count: mcqs.length, attempt: mcqs.length, marksEach: 1, minutes: null }];
    const shortAttempt = Math.max(1, Math.round(shorts.length * 2 / 3));
    sections.push({ type: "Short questions", count: shorts.length, attempt: shortAttempt, marksEach: 2, minutes: null });
    if (longs.length) sections.push({ type: "Long questions", count: longs.length, attempt: longs.length > 1 ? longs.length - 1 : 1, marksEach: LONG.total, minutes: null });
    const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
    const id = `${IDP}ch-${n}-test`;
    blueprints.push({ id, title: `${S.unitWord} ${n} test: ${u.chapter.title}${unitOut ? ` (not in the ${EXAM})` : ""}`, durationMinutes: total >= 35 ? 90 : 60, totalMarks: total, official: false, chapterId: u.chapter.id, sections });
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
const P_TOTAL = syllabus.paper.totalMarks;
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
      sections.push({ type: `Q${sq.q} Short questions (${S.unitWord}s ${units})`, count: qs.length, attempt: sq.attempt, marksEach: 2, minutes: null });
      writtenOut.push(qs.map((w) => ({ ...w })));
    }
    for (const part of P.long) {
      const qs = [];
      for (const q of part.questions) {
        // Part (a) and part (b): from the units the instructions name (Physics: one group of chapters, (b) from a different chapter).
        const partFrom = (units, key, salt, notUnit) => {
          const opts = units.filter((u) => u !== notUnit && (pools.get(u)?.[key].length ?? 0) > 0);
          if (!opts.length) return null;
          const u = opts[(k + salt) % opts.length];
          const list = pools.get(u)[key];
          return { w: list[(k + salt) % list.length], u };
        };
        if (LONG.single) {
          const one = partFrom(q.units ?? q.a, poolOf(LONG.total), 0);
          if (!one) { problems.push(`Q${q.q}: no ${LONG.total}-mark question`); continue; }
          qs.push(singleLong(`${IDP}BP-${set}-Q${q.q}`, one.w));
          continue;
        }
        const a = partFrom(q.a ?? q.units, poolOf(LONG.a), 0);
        const b = partFrom(q.b ?? q.units, poolOf(LONG.b), 1, q.units ? a?.u : undefined);
        if (!a || !b) { problems.push(`Q${q.q}: no ${a ? LONG.b : LONG.a}-mark question for part ${a ? "b" : "a"}`); continue; }
        qs.push(longOf(`${IDP}BP-${set}-Q${q.q}`, a.w, b.w));
      }
      sections.push({ type: `Part ${part.part}: long questions (Q${part.questions.map((q) => q.q).join(", Q")})`, count: qs.length,
        attempt: part.attempt, marksEach: LONG.total, minutes: null });
      writtenOut.push(qs);
    }
    const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
    if (problems.length || total !== P.totalMarks) { console.log(`Board paper ${set} not made: ${problems.join("; ") || `total ${total}`}`); continue; }
    const id = `${IDP}board-2027-${set.toLowerCase()}`;
    blueprints.push({ id, title: `Board paper ${set} (2027 pattern, ${P_TOTAL} marks)`, durationMinutes: P.minutes, totalMarks: P.totalMarks, official: true, chapterId: null, sections });
    for (const l of writtenOut.slice(P.short.length).flat()) written.push({ ...l, section: 0, number: 0 });
    papers.push({ blueprintId: id, mcqs, written: writtenOut.flatMap((qs, i) => qs.map((w, j) => ({ ...w, section: i + 1, number: j + 1 }))) });
    made.push(set);
  }
  return made;
}
const boardMade = boardPapers();

// ── The official 2027 model paper, question for question (D-027) ─────────────────────────────────────────────
// Built only when every one of its questions came through the past-paper pipeline; otherwise it is left out and logged.
function modelPaper() {
  if (!pastPapers) return "no past-paper bank";
  const P = syllabus.paper;
  const ROMAN = ["i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv", "xv"];
  const place = (label) => { const m = /(\d+)\s*\(?\s*([a-z]+)\s*\)?/i.exec(label ?? ""); return m ? `${Number(m[1])}|${m[2].toLowerCase()}` : null; };
  const slots = new Map();
  for (const it of [...pastPapers.mcqs, ...pastPapers.written]) for (const l of it.labels ?? []) if (l.model && place(l.label)) slots.set(place(l.label), it);
  const quizBy = new Map(quiz.map((q) => [q.id, q])), quizByStem = new Map(quiz.map((q) => [norm(q.stem), q]));
  const writtenBy = new Map(written.map((w) => [w.id, w]));
  const missing = [];
  const mcqs = ROMAN.slice(0, P.objective.marks ?? 15).map((r) => {
    const it = slots.get(`1|${r}`);
    const q = it && (quizBy.get(it.id) ?? quizByStem.get(norm(it.stem)));
    if (!q) missing.push(`Q1(${r})`);
    return q;
  });
  const sections = [{ type: "MCQ", count: mcqs.length, attempt: mcqs.length, marksEach: 1, minutes: P.objective.minutes }];
  const writtenOut = [];
  for (const sq of P.short) {
    const n = Object.values(sq.perUnit).reduce((a, b) => a + b, 0);
    const qs = ROMAN.slice(0, n).map((r) => { const w = writtenBy.get(slots.get(`${sq.q}|${r}`)?.id); if (!w) missing.push(`Q${sq.q}(${r})`); return w; });
    sections.push({ type: `Q${sq.q} Short questions`, count: qs.length, attempt: sq.attempt, marksEach: 2, minutes: null });
    writtenOut.push(qs);
  }
  for (const part of P.long) {
    const qs = part.questions.map((q) => {
      if (LONG.single) { const w = writtenBy.get(slots.get(`${q.q}|a`)?.id) ?? writtenBy.get(slots.get(`${q.q}|`)?.id); if (!w) missing.push(`Q${q.q}`); return w ? singleLong(`${IDP}MP-2027-Q${q.q}`, w) : null; }
      const [a, b] = ["a", "b"].map((x) => { const w = writtenBy.get(slots.get(`${q.q}|${x}`)?.id); if (!w) missing.push(`Q${q.q}(${x})`); return w; });
      return a && b ? longOf(`${IDP}MP-2027-Q${q.q}`, a, b) : null;
    });
    sections.push({ type: `Part ${part.part}: long questions (Q${part.questions.map((q) => q.q).join(", Q")})`, count: qs.length, attempt: part.attempt, marksEach: LONG.total, minutes: null });
    writtenOut.push(qs);
  }
  if (missing.length) return `missing ${missing.join(", ")}`;
  const total = sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
  if (total !== P.totalMarks) return `total ${total}`;
  const id = `${IDP}model-2027`;
  blueprints.push({ id, title: PHY ? `Official model paper (${P.totalMarks} marks)` : "Official model paper 2027 (75 marks)", durationMinutes: P.minutes, totalMarks: P.totalMarks, official: true, chapterId: null, sections });
  for (const l of writtenOut.slice(P.short.length).flat()) written.push({ ...l, section: 0, number: 0 });
  papers.push({ blueprintId: id, mcqs, written: writtenOut.flatMap((qs, i) => qs.map((w, j) => ({ ...w, section: i + 1, number: j + 1 }))) });
  return "made";
}
const modelMade = modelPaper();

// Some book MCQs carry their own letters ("(a) 12(x + 3)"); the app and the printed paper add A–D themselves.
const unletter = (q) => q.options.every((o, i) => new RegExp(`^\\(?${"abcd"[i]}[).]\\s+`, "i").test(o))
  ? { ...q, options: q.options.map((o) => o.replace(/^\(?[a-d][).]\s+/i, "")) } : q;
quiz.splice(0, quiz.length, ...quiz.map(unletter));
readiness.splice(0, readiness.length, ...readiness.map(unletter));
for (const p of papers) p.mcqs = p.mcqs.map(unletter);

const bundle = {
  format: 1,
  subjectId: SUBJECT,
  course: { board: "Punjab", grade: 9, subject: S.name, edition: `${S.book} (your textbook)` },
  subject: {
    id: SUBJECT, name: S.name, grade: 9,
    editionLabel: S.book.replace(/, /g, " · "),
    editionVerified: true,
    examDate: syllabus.examDate, examName: syllabus.examName,
    chapters: units.map((u) => u.chapter),
  },
  notes, quiz, readiness: readiness.slice(0, 12), blueprints, papers, written, mcqs: [],
};

const cleaned = cleanAll(bundle);
// Titles printed in capitals ("THE CELL") read as shouting in the app: they become title case ("The Cell").
const SMALL = new Set(["a", "an", "and", "as", "at", "by", "for", "in", "of", "on", "or", "the", "to", "with"]);
const titleCase = (t) => t.toLowerCase().replace(/[a-z][a-z'’]*/g, (w, i) => (i > 0 && SMALL.has(w) ? w : w[0].toUpperCase() + w.slice(1)));
for (const c of cleaned.subject.chapters) {
  if (/[A-Z]{3}/.test(c.title) && c.title === c.title.toUpperCase()) c.title = titleCase(c.title);
  for (const m of c.modules) if (/[A-Z]{3}/.test(m.title) && m.title === m.title.toUpperCase()) m.title = titleCase(m.title);
}

// ── Content audit (audit-content.mjs): corrections proof-read against the printed pages, and re-worked solutions ──
const auditFile = `${ROOT}/audit-fixes.json`;
if (existsSync(auditFile)) {
  const fx = JSON.parse(readFileSync(auditFile, "utf8"));
  // Decisions made by hand on the audit's findings (audit-manual.json): removals that were wrong, added notes and MCQs.
  const manualFile = `${ROOT}/audit-manual.json`;
  const man = existsSync(manualFile) ? JSON.parse(readFileSync(manualFile, "utf8")) : {};
  const removals = (fx.remove ?? []).filter((r) => !(r in (man.keep ?? {})));
  let applied = 0;
  for (const c of cleaned.subject.chapters) for (const m of c.modules) {
    const sec = /^(\d+\.\d+)/.exec(m.title)?.[1] ?? String(c.number);
    // (Mathematics examples are labelled with their section; Physics labels are already unique: "Example 2.3".)
    m.solutions = m.solutions.filter((s) => !removals.includes(s.kind === "EXAMPLE" && !PHY ? `${sec} ${s.source}` : s.source));
    for (const s of m.solutions) {
      const k = s.kind === "EXAMPLE" && !PHY ? `${sec} ${s.source}` : s.source;
      // Question corrections; some readers filed a question's wording under "stem".
      const q = fx.questions?.[k] ?? fx.mcq?.[k]?.stem;
      if (q) { s.question = q; applied++; }
      const pre = man.prefix?.[k];
      if (pre && !s.question.startsWith(pre)) { s.question = `${pre}\n${s.question}`; applied++; }
      if (man.notes?.[k]) s.note = man.notes[k];
      if (fx.steps?.[k]?.length) { s.steps = fx.steps[k]; delete s.why; applied++; }
      if (fx.answers?.[k] && fx.answers[k] !== s.answer) {
        s.answer = fx.answers[k]; s.checked = true;
        // What the app accepts as the student's final answer follows the re-worked answer.
        s.accept = [[fx.answers[k].replace(/^[A-Za-z]\s*=\s*/, "").trim()]];
        // A note about the book's key disagreeing no longer applies once the answer agrees with it.
        const bare = (t) => String(t ?? "").replace(/^[A-Za-z]\s*=\s*/, "").replace(/\s+/g, "");
        if (s.bookAnswer && bare(s.bookAnswer) === bare(s.answer)) s.note = "";
      }
    }
  }
  for (const q of [...cleaned.quiz, ...cleaned.papers.flatMap((p) => p.mcqs)]) {
    const f = fx.mcq?.[q.id.replace(/-m-\d+-\d+$/, "")];
    if (f?.stem) { q.stem = f.stem; applied++; }
    if (f?.options?.length === q.options.length) { q.options = f.options; applied++; }
  }
  // Items the audit found missing are added to their section; they're solved by audit-solve-missing.mjs before release.
  for (const a of fx.add ?? []) {
    const c = cleaned.subject.chapters.find((x) => x.number === a.unit); if (!c) continue;
    const m = c.modules.find((x) => x.title.startsWith(`${String(a.exercise).replace(/^R/, "")} `)) ?? c.modules.at(-1);
    const source = a.kind === "EXAMPLE" ? a.label.replace(/^\d+\.\d+\s+/, "") : a.label;
    // Examples are numbered through the whole unit, and the app groups some book sections into one module, so an
    // item counts as present if any module of the unit has it.
    if (a.kind === "MCQ" || a.label in (man.skipAdd ?? {}) || c.modules.some((x) => x.solutions.some((s) => s.source === source))) continue;
    const solved = fx.solved?.[a.label];
    m.solutions.push({ kind: a.kind, source, question: a.text, steps: solved?.steps ?? a.steps ?? [], answer: solved?.answer ?? "", accept: solved?.accept ?? [],
      checked: !!solved || (a.kind === "EXAMPLE" && a.steps?.length > 0), inExam: true, figures: [] });
    applied++;
  }
  // Review MCQs the course was missing, with the answer checked against the book's key (see audit-manual.json).
  for (const q of man.mcqs ?? []) {
    const c = cleaned.subject.chapters.find((x) => x.number === q.unit); if (!c) continue;
    const m = c.modules.at(-1);
    const id = `${IDP}b-${q.unit}-r-${q.label.replace(/^.*Q1\(([ivx]+)\)$/, "$1")}`;
    if (cleaned.quiz.some((x) => x.id === id)) continue;
    const note = cleaned.notes.find((n) => n.moduleId === m.id)?.id ?? null;
    cleaned.quiz.push({ id, stem: q.stem, options: q.options, correctIndex: q.correctIndex, rationales: q.rationales, status: "APPROVED", noteId: note, moduleId: m.id });
    applied++;
  }
  console.log(`Content audit: ${applied} corrections applied`);
}

// ── No developer notes in anything a student reads ─────────────────────────────────────────────────────────────
const devClean = (t) => typeof t !== "string" ? t : t
  .replace(/^\s*Q\d{1,2}\.\d{1,2}\.\s*/, "")                                          // the book's "Q1.4." numbering (Physics)
  .replace(/\s*\((?:Ex|Example|Exercise)\s+[\d.]+[^()]*(?:\([^()]*\))?[^()]*\)/g, "")   // "(Ex 6.1 Q1(i))" references
  .replace(/\s*Draw it on paper and photograph your work[^.]*\./g, "")
  .replace(/\s{2,}/g, " ").trim();
for (const c of cleaned.subject.chapters) {
  c.status = "PUBLISHED";
  for (const m of c.modules) {
    m.status = "PUBLISHED";
    for (const s of m.solutions) s.question = devClean(s.question);
    for (const w of m.writtenQuestions ?? []) { w.text = devClean(w.text); w.followUp = devClean(w.followUp); }
  }
}
for (const w of cleaned.written) { w.text = devClean(w.text); w.followUp = devClean(w.followUp); }
for (const p of cleaned.papers) for (const w of p.written) { w.text = devClean(w.text); w.followUp = devClean(w.followUp); }
for (const q of [...cleaned.quiz, ...cleaned.papers.flatMap((p) => p.mcqs)]) q.stem = devClean(q.stem);
if (cleaned.subject) cleaned.subject.editionVerified = true;
// Physics labels: one name per kind of exercise item ("Constructed Response 1.2" → "CRQ 1.2").
for (const c of cleaned.subject.chapters) for (const m of c.modules) for (const x of m.solutions)
  x.source = x.source.replace(/^Constructed\s+Response(\s+Questions?)?\s+/i, "CRQ ").replace(/^Short\s+Answer(\s+Questions?)?\s+/i, "Short ");
// Practicals, from the book's activities (practicals.mjs).
const pracFile = `${ROOT}/units/practicals.json`;
if (existsSync(pracFile)) {
  cleaned.subject.practicals = JSON.parse(readFileSync(pracFile, "utf8")).practicals.map((p) => ({ ...p, bookText: devClean(p.bookText) }));
  console.log(`Practicals: ${cleaned.subject.practicals.length} (${cleaned.subject.practicals.filter((p) => p.checked).length} written up and checked)`);
}
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
const STOP = new Set(["with", "that", "this", "from", "shows", "showing", "shown", "labelled", "label", "labels", "which", "their", "there", "into", "each", "when", "they", "have", "also", "used", "figure"]);
if (existsSync(linksFile)) {
  const links = JSON.parse(readFileSync(linksFile, "utf8"));
  const unmark = (t, keep) => t.replace(/\[FIGURE[^:\]]*:\s*([^\]]*)\]/g, (_, d) => (keep ? "" : `(Figure: ${d.trim()})`)).replace(/\n{3,}/g, "\n\n").trim();
  let withFig = 0;
  for (const c of cleaned.subject.chapters) for (const m of c.modules) for (const s of m.solutions) {
    const n = c.number;
    let qf = [], af = [];
    const ex = /^example\s*(\d+(?:\.\d+)?)/i.exec(s.source);
    if (ex) qf = links.example[`${n}|${ex[1]}`] ?? [];
    else if (PHY) {
      // "Numerical 1.3(a)": figures of the part, and of the whole item.
      const m = /^(.*?)\s*(?:\(([a-h])\))?$/.exec(s.source);
      qf = [...(links.question[`${n}|${m[1]}|${m[2] ?? ""}`] ?? []), ...(m[2] ? links.question[`${n}|${m[1]}|`] ?? [] : [])];
    } else {
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
  for (const p of cleaned.subject.practicals ?? []) {
    const f = links.activity?.[p.label] ?? [];
    if (f.length) p.figures = f;
    p.bookText = unmark(p.bookText, f.length > 0);
  }
  console.log(`Figures: ${withFig} questions show a book figure`);
  // Lessons show the book's teaching diagrams: the figures printed in a section that belong to no question, example or
  // activity, each on the idea card it explains best (most shared words with the figure's description).
  if (S.lessonFigures) {
    const all = JSON.parse(readFileSync(`${ROOT}/figures.json`, "utf8"));
    const figs = Array.isArray(all) ? all : all.figures ?? Object.values(all);
    const used = new Set(Object.values(links).flatMap((g) => Object.values(g).flat()));
    const words = (t) => new Set((t.toLowerCase().match(/[a-z]{4,}/g) ?? []).filter((w) => !STOP.has(w)));
    let placed = 0;
    for (const c of cleaned.subject.chapters) {
      const of = `${ROOT}/work/u${c.number}-outline.json`;
      if (!existsSync(of)) continue;
      const sections = JSON.parse(readFileSync(of, "utf8")).sections.filter((x) => !x.review);
      for (const m of c.modules) {
        const sec = sections.find((x) => m.title.startsWith(`${x.number} `));
        const cards = m.lesson?.explain;
        if (!sec || !cards?.length) continue;
        const pages = new Set([...sec.text.split("\n\nCHAPTER EXERCISE (pick only")[0].matchAll(/PAGE\s+(\d+)/g)].map((x) => +x[1]));
        for (const f of figs.filter((f) => pages.has(f.page) && !used.has(f.id))) {
          const fw = words(f.description);
          let best = 0, score = -1;
          cards.forEach((card, i) => { const cw = words(`${card.title} ${card.body}`); const n = [...fw].filter((w) => cw.has(w)).length; if (n > score) { score = n; best = i; } });
          (cards[best].figures ??= []).push(f.id);
          used.add(f.id); placed++;
        }
      }
    }
    console.log(`Lessons: ${placed} teaching diagrams placed on idea cards`);
  }
}
// A marking point worth nothing ("Gives a direct answer", 0 marks) is not a marking point, wherever it is.
(function dropZeroPoints(o) {
  if (Array.isArray(o)) return o.forEach(dropZeroPoints);
  if (!o || typeof o !== "object") return;
  if (Array.isArray(o.rubric) && o.rubric.some((c) => !(c.marks > 0))) o.rubric = o.rubric.filter((c) => c.marks > 0);
  Object.values(o).forEach(dropZeroPoints);
})(cleaned);
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
console.log(`Official model paper 2027: ${modelMade}`);
writeFileSync(`${ROOT}/work/audit.json`, JSON.stringify(audit, null, 1));
for (const a of audit) if (a.examplesMissing || a.gaps?.length || a.missing?.length) console.log(`Audit unit ${a.unit}: ${JSON.stringify({ examplesMissing: a.examplesMissing, gaps: a.gaps, missing: a.missing })}`);
console.log("All publishing checks pass.");
