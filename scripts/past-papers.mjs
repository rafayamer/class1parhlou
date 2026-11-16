// Builds practice questions from real Punjab board past papers.
//   node --use-env-proxy scripts/past-papers.mjs <dir with pdf9/ and pdf10/> [model-paper.pdf]
// 1. read:     every page of every paper, read by Luna vision into questions (English text only), cached per page.
// 2. classify: each question → the new book's unit and section (or "not in the new book"), and the textbook exercise it
//              repeats, if any. Only questions on topics in the new book and the 2027 smart syllabus are kept.
// 3. solve:    MCQs get a key that two independent solves agree on, plus a reason for every option; written
//              questions get a board-style model answer (from the checked book solution when they repeat a book
//              question) and a marking scheme whose patterns are checked against the model answer.
// Repeats across boards and years are merged, keeping every "came in" tag.
// Writes content/class9-maths/pastpapers.json; assemble-course.mjs builds practice and papers from it.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync, unlinkSync } from "node:fs";
import { join, basename } from "node:path";
import { tmpdir } from "node:os";
import { luna, costSoFar } from "./luna-budget.mjs";
import { matchesAll, matchesAny, SYNTAX } from "./textmatch.mjs";
import { isExcluded } from "./syllabus.mjs";

const ROOT = "content/class9-maths";
const WORK = join(ROOT, "work", "pp");
mkdirSync(WORK, { recursive: true });
const [srcDir, modelPaper] = process.argv.slice(2);
const bundle = JSON.parse(readFileSync(join(ROOT, "bundle.json"), "utf8"));
const syllabus = JSON.parse(readFileSync(join(ROOT, "syllabus-2027.json"), "utf8"));

const str = { type: "string" }, int = { type: "integer" }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
async function cached(name, run) {
  const f = join(WORK, `${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const r = await run();
  writeFileSync(f, JSON.stringify(r));
  return r;
}
async function structured(name, item, instructions, input, schema, effort = "low", maxTokens = 30000) {
  const text = await luna({ max_output_tokens: maxTokens, reasoning: { effort }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `pp-${name}`, item);
  return JSON.parse(text);
}
async function pool(items, n, fn) {
  let next = 0;
  await Promise.all(Array.from({ length: n }, async () => { while (next < items.length) { const i = next++; await fn(items[i], i); } }));
}

// ── 1. Read every page ───────────────────────────────────────────────────────────────────────────────────
const BOARDS = { BWP: "Bahawalpur", DGK: "DG Khan", FBD: "Faisalabad", GUJ: "Gujranwala", GRW: "Gujranwala", LHR: "Lahore", MTN: "Multan", RWP: "Rawalpindi", SGD: "Sargodha", SWL: "Sahiwal" };
const READ = `You read one page of a Punjab board (Pakistan) Mathematics exam paper. Pages are bilingual: copy the ENGLISH text only.
List every question on the page. label: its number as printed, e.g. "Q1(iv)", "Q2(iii)", "Q5(a)". kind: MCQ (four options),
SHORT (a short question in Q2–Q4, 2 marks) or LONG (a part of Q5–Q9, usually 4 marks). text: the question exactly, in plain Unicode
maths (√, ², ×, ÷, π, θ, matrices as [[a, b], [c, d]], fractions as a/b with brackets); describe any figure in one bracketed line
[Figure: …]. options: the four MCQ options in order (empty for others). marks: as printed (MCQ 1).
Also read the header: board (city), year, class (9 or 10), group (I or II, "" if unknown), part (OBJECTIVE or SUBJECTIVE).
If the page is not part of an exam paper (cover, advert), return no questions. Ignore website watermarks.`;
const readSchema = obj({ board: str, year: int, klass: int, group: str, part: str,
  questions: arr(obj({ label: str, kind: { type: "string", enum: ["MCQ", "SHORT", "LONG"] }, text: str, options: arr(str), marks: int })) });

function render(pdf, page) {
  const base = join(tmpdir(), `pp-${process.pid}-${Math.random().toString(36).slice(2)}`);
  execFileSync("pdftoppm", ["-f", String(page), "-l", String(page), "-r", "120", "-jpeg", "-jpegopt", "quality=80", "-singlefile", pdf, base]);
  const b64 = readFileSync(`${base}.jpg`).toString("base64");
  unlinkSync(`${base}.jpg`);
  return `data:image/jpeg;base64,${b64}`;
}

const papers = [];
for (const sub of ["pdf9", "pdf10"]) {
  const dir = join(srcDir, sub);
  if (!existsSync(dir)) continue;
  for (const f of readdirSync(dir).filter((x) => x.endsWith(".pdf")).sort()) {
    const code = /(BWP|DGK|FBD|GUJ|GRW|LHR|MTN|RWP|SGD|SWL)/.exec(f)?.[1];
    papers.push({ file: join(dir, f), key: basename(f, ".pdf"), klass: sub === "pdf9" ? 9 : 10, year: Number(f.slice(0, 4)), board: BOARDS[code] ?? "" });
  }
}
if (modelPaper && existsSync(modelPaper)) papers.push({ file: modelPaper, key: "model-2027", klass: 9, year: 2027, board: "Model paper", model: true });

const pages = papers.flatMap((p) => {
  const n = Number(/Pages:\s+(\d+)/.exec(execFileSync("pdfinfo", [p.file]).toString())[1]);
  return Array.from({ length: n }, (_, i) => ({ paper: p, page: i + 1 }));
}).filter((x) => !(x.paper.model && x.page < 5)); // the model paper's first pages are the syllabus, not the paper
console.log(`${papers.length} papers, ${pages.length} pages`);

const raw = [];
await pool(pages, 6, async ({ paper, page }) => {
  try {
    const r = await cached(`read-${paper.key}-p${page}`, () => structured("read", `${paper.key} p${page}`, READ, [{ role: "user", content: [
      { type: "input_text", text: `Exam paper page. File name: ${paper.key}.` }, { type: "input_image", image_url: render(paper.file, page), detail: "high" }] }], readSchema));
    for (const q of r.questions) {
      if (!q.text.trim()) continue;
      raw.push({ ...q, klass: paper.klass, year: paper.year || r.year, board: paper.board || r.board, group: r.group, model: !!paper.model,
        tag: paper.model ? "Official model paper 2027" : `${paper.board || r.board} ${paper.year || r.year}${r.group ? ` (Group ${r.group})` : ""}${paper.klass === 10 ? " · Class 10 paper" : ""}` });
    }
  } catch (e) { console.log(`${paper.key} p${page}: ${e.message}`); }
});
console.log(`read ${raw.length} questions. $${costSoFar().toFixed(3)}`);

// ── 2. Classify against the new book and the 2027 syllabus ───────────────────────────────────────────────────
const sections = bundle.subject.chapters.flatMap((c) => c.modules.map((m) => ({ id: m.id, unit: c.number, label: `Unit ${c.number} · ${m.title}` })));
const exampleSources = new Map();
for (const c of bundle.subject.chapters) for (const m of c.modules) m.solutions.forEach((s, i) => exampleSources.set(`${c.number}|${s.source}`, { m, i, s }));
const CLASSIFY = `You map old Punjab board exam questions onto the NEW Class 9 Mathematics textbook (National Curriculum 2023).
For each question: section = the new book's section that teaches it, exactly as written in the list, or "" if the new book does not
teach it (e.g. matrices, complex numbers, congruent triangles proofs, Pythagoras proofs, theorems on areas, ratio and proportion
variation, partial fractions — anything not in the list). bookSource = the textbook exercise or example it repeats or very closely
resembles, exactly as in that section's list (e.g. "Ex 2.3 Q4(v)"), or "".
Sections and their book questions:
${sections.map((s) => s.label).join("\n")}`;
const classifySchema = obj({ items: arr(obj({ id: str, section: str, bookSource: str })) });

const uniq = new Map(); // merge identical questions across papers
const norm = (t) => t.toLowerCase().replace(/\[figure:[^\]]*\]/g, "").replace(/[^a-z0-9√π²³+\-×÷=<>]/g, "");
for (const q of raw) {
  const k = `${q.kind}|${norm(q.text)}|${q.options.map(norm).join("|")}`;
  const e = uniq.get(k);
  if (e) { if (!e.tags.includes(q.tag)) e.tags.push(q.tag); e.count++; }
  else uniq.set(k, { ...q, id: `q${uniq.size}`, tags: [q.tag], count: 1 });
}
const all = [...uniq.values()];
console.log(`${all.length} distinct questions`);
const bookList = (label) => {
  const s = sections.find((x) => x.label === label);
  const c = bundle.subject.chapters.find((ch) => ch.number === s?.unit);
  return c?.modules.find((m) => m.id === s.id)?.solutions.map((x) => x.source) ?? [];
};
for (let b = 0; b < all.length; b += 30) {
  const batch = all.slice(b, b + 30);
  const r = await cached(`classify-${b / 30}`, () => structured("classify", `classify ${b / 30}`, CLASSIFY,
    JSON.stringify(batch.map((q) => ({ id: q.id, kind: q.kind, text: q.text, options: q.options }))), classifySchema, "medium"));
  for (const it of r.items) {
    const q = all.find((x) => x.id === it.id);
    if (!q) continue;
    const s = sections.find((x) => x.label === it.section);
    q.moduleId = s?.id ?? null; q.unit = s?.unit ?? 0; q.section = s?.label ?? "";
    q.bookSource = s && bookList(s.label).includes(it.bookSource) ? it.bookSource : "";
  }
}
const inSyllabus = (q) => {
  if (!q.moduleId) return false;
  if (syllabus.excludedUnits.includes(q.unit) || syllabus.excludedModules.includes(q.moduleId)) return false;
  return !(q.bookSource && isExcluded(syllabus, q.unit, q.bookSource));
};
const kept = all.filter(inSyllabus);
console.log(`${kept.length} questions are on topics in the new book and the 2027 syllabus. $${costSoFar().toFixed(3)}`);

// ── 3. Solve and write marking schemes ───────────────────────────────────────────────────────────────────────
const MCQ = `For each multiple-choice question from a Punjab board Class 9/10 Mathematics paper: correctIndex = the right option (0-based)
after solving it yourself; rationales = one short sentence for EVERY option saying why it is right or wrong. If no option is correct
or the question is broken, set correctIndex to -1.`;
const mcqSchema = obj({ items: arr(obj({ id: str, correctIndex: int, rationales: arr(str) })) });
const WRITE = `You write board-exam model answers and marking schemes for Punjab board Class 9 Mathematics questions.
For each item: modelAnswer = the full answer a top student writes, one line per step (when a checked textbook solution is given,
follow its method and final answer); marks = 2 for a short question, 4 for a long-question part; rubric = 2–4 marking points whose marks
add up to the question's marks, each with description, why, how and accept patterns (key results the model answer contains; never
patterns a blank answer would match); followUp = one similar practice question. For a drawing (graph, construction) the model answer
lists the table of values or construction steps with every measurement.
${SYNTAX}`;
const writeSchema = obj({ items: arr(obj({ id: str, modelAnswer: arr(str), rubric: arr(obj({ description: str, marks: int, why: str, how: str, accept: arr(arr(str)) })), followUp: str })) });

const rubricOk = (w) => {
  const answer = w.modelAnswer.join("\n");
  return w.rubric.reduce((s, c) => s + c.marks, 0) === w.marks && w.rubric.every((c) => c.accept.some((g) => g.length) &&
    matchesAny(answer, c.accept) && !c.accept.some((g) => g.length && matchesAll("", g)));
};

const mcqs = kept.filter((q) => q.kind === "MCQ" && q.options.length >= 2);
const outMcqs = [];
for (let b = 0; b < mcqs.length; b += 15) {
  const batch = mcqs.slice(b, b + 15).map((q) => ({ id: q.id, stem: q.text, options: q.options }));
  const a = await cached(`mcq-a-${b / 15}`, () => structured("mcq", `mcq a ${b / 15}`, MCQ, JSON.stringify(batch), mcqSchema, "medium"));
  const c = await cached(`mcq-b-${b / 15}`, () => structured("mcq", `mcq b ${b / 15}`, `${MCQ}\nWork independently and carefully; another examiner will compare.`, JSON.stringify(batch), mcqSchema, "high"));
  for (const x of a.items) {
    const y = c.items.find((z) => z.id === x.id);
    const q = mcqs.find((z) => z.id === x.id);
    if (!q || !y || x.correctIndex < 0 || x.correctIndex !== y.correctIndex || x.rationales.length !== q.options.length) continue;
    outMcqs.push({ id: `pp-${q.id}`, stem: q.text, options: q.options, correctIndex: x.correctIndex, rationales: x.rationales,
      moduleId: q.moduleId, tags: q.tags, count: q.count, year: q.year, klass: q.klass });
  }
}
const written = kept.filter((q) => q.kind !== "MCQ");
const outWritten = [];
for (let b = 0; b < written.length; b += 8) {
  const batch = written.slice(b, b + 8).map((q) => {
    const book = q.bookSource ? exampleSources.get(`${q.unit}|${q.bookSource}`)?.s : null;
    return { id: q.id, marks: q.kind === "SHORT" ? 2 : 4, question: q.text, checkedTextbookSolution: book && book.checked ? { steps: book.steps, answer: book.answer } : null };
  });
  const r = await cached(`write-${b / 8}`, () => structured("write", `write ${b / 8}`, WRITE, JSON.stringify(batch), writeSchema, "medium", 40000));
  let items = r.items.map((w) => ({ ...w, marks: batch.find((x) => x.id === w.id)?.marks ?? 0 }));
  const bad = items.filter((w) => !rubricOk(w));
  if (bad.length) {
    const fix = await cached(`write-fix-${b / 8}`, () => structured("write", `write fix ${b / 8}`,
      `${WRITE}\nThe previous marking schemes below failed checks (marks must add up; every point's patterns must match the model answer and not a blank answer). Fix them.`,
      JSON.stringify(bad.map((w) => ({ ...batch.find((x) => x.id === w.id), previous: w }))), writeSchema, "medium", 40000));
    for (const w of fix.items) { const k = items.findIndex((x) => x.id === w.id); if (k >= 0) items[k] = { ...w, marks: items[k].marks }; }
  }
  for (const w of items.filter(rubricOk)) {
    const q = written.find((z) => z.id === w.id);
    outWritten.push({ id: `pp-${q.id}`, text: q.text, marks: w.marks, modelAnswer: w.modelAnswer, followUp: w.followUp,
      rubric: w.rubric.map((c, k) => ({ id: String.fromCharCode(97 + k), ...c })), moduleId: q.moduleId, tags: q.tags,
      count: q.count, kind: q.kind, bookSource: q.bookSource, year: q.year, klass: q.klass });
  }
}
const out = { mcqs: outMcqs, written: outWritten, stats: { pages: pages.length, read: raw.length, distinct: all.length, kept: kept.length } };
writeFileSync(join(ROOT, "pastpapers.json"), JSON.stringify(out, null, 1));
console.log(`past-paper bank: ${outMcqs.length} MCQs, ${outWritten.length} written (${outWritten.filter((w) => w.marks === 2).length} short, ${outWritten.filter((w) => w.marks === 4).length} long parts). $${costSoFar().toFixed(3)}`);
