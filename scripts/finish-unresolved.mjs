// Last pass for questions the answer-key alignment could not settle: solve each the way a board examiner expects
// (stating the assumption the book intends, if any, in one line), re-check, and record the result in u-N-align.json.
//   node --use-env-proxy scripts/finish-unresolved.mjs
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { luna, costSoFar } from "./luna-budget.mjs";
import { SYNTAX } from "./textmatch.mjs";

const ROOT = "content/class9-maths";
const str = { type: "string" }, strs = { type: "array", items: str }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
async function structured(name, instructions, input, schema) {
  const text = await luna({ max_output_tokens: 40000, reasoning: { effort: "high" }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `finish-${name}`, "unresolved");
  return JSON.parse(text);
}

const SOLVE = `You are a Punjab board Class 9 maths teacher. These textbook questions are slightly incomplete or ambiguous as printed.
Write the solution a board examiner expects from a student: if the book clearly intends an assumption (e.g. "the cones are similar",
"take the points as plotted on the coordinate plane"), state it as the first step in one short line ("Assume …, as the book intends.")
and then solve fully. If a printed answer-key value is given, reach it unless it is mathematically impossible. For a construction or
locus, give the numbered construction steps with every measurement and say what the finished figure shows in "figure".
steps: one line per step. finalAnswer: the final result. note: one sentence for the student explaining the assumption, or "".
accept: patterns for a typed final answer (empty for constructions and proofs).
${SYNTAX}`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, finalAnswer: str, figure: str, note: str, accept: arr(strs) })) });
const CHECK = `You are an independent Class 9 maths examiner. Judge each candidate solution: candidateCorrect = the working is
correct and complete under the assumption it states (if any), and the assumption is the one the textbook intends. problem: "" or one sentence.`;
const checkSchema = obj({ items: arr(obj({ id: str, candidateCorrect: bool, problem: str })) });

const todo = [];
for (let u = 1; u <= 13; u++) {
  const f = `${ROOT}/units/u-${u}-align.json`;
  if (!existsSync(f)) continue;
  const align = JSON.parse(readFileSync(f, "utf8"));
  const sols = JSON.parse(readFileSync(`${ROOT}/units/u-${u}-solutions.json`, "utf8")).sections.flatMap((s) => s.solutions);
  align.items.forEach((it, k) => {
    if (it.status !== "unresolved") return;
    const s = sols.find((x) => x.source === it.source);
    todo.push({ u, k, id: `${u}:${k}`, question: s?.question ?? "", bookAnswer: it.bookAnswer, independentAnswer: it.indep, examinerNote: it.examiner ?? "" });
  });
}
if (!todo.length) { console.log("nothing unresolved"); process.exit(0); }
const input = JSON.stringify(todo.map(({ u, k, ...rest }) => rest));
const solved = await structured("solve", SOLVE, input, solveSchema);
const checked = await structured("check", CHECK, JSON.stringify(solved.items.map((s) => ({ id: s.id, question: todo.find((t) => t.id === s.id)?.question, steps: s.steps, finalAnswer: s.finalAnswer }))), checkSchema);
const ok = new Map(checked.items.map((c) => [c.id, c]));
const touched = new Map();
let fixed = 0;
for (const s of solved.items) {
  const t = todo.find((x) => x.id === s.id);
  // ACCEPT lists ids whose only objection is the unstated assumption the book intends (reviewed by hand).
  const accepted = (process.env.ACCEPT ?? "").split(",").includes(s.id);
  if (!t || (!ok.get(s.id)?.candidateCorrect && !accepted)) { console.log(`still unresolved: unit ${t?.u} ${s.id} ${ok.get(s.id)?.problem ?? ""}`); continue; }
  const f = `${ROOT}/units/u-${t.u}-align.json`;
  const align = touched.get(f) ?? JSON.parse(readFileSync(f, "utf8"));
  const it = align.items[t.k];
  it.status = "resolved (assumption)";
  it.note = s.note;
  it.replace = { steps: s.steps, answer: s.finalAnswer, figure: s.figure, accept: s.accept, checked: true };
  touched.set(f, align);
  fixed++;
}
for (const [f, a] of touched) writeFileSync(f, JSON.stringify(a, null, 1));
console.log(`resolved ${fixed} of ${todo.length}. Total spend $${costSoFar().toFixed(3)}`);
