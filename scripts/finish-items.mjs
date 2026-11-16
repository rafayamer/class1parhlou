// Last pass for exercise items that still have no checked solution (build-solutions.mjs gave up on them): each is solved again
// carefully, with a figure the app draws when the question asks for a drawing (vectors, forces, circuits, graphs), and an
// independent examiner checks the working and the figure. Only items that pass are filled in; the rest are listed for a teacher.
//   SUBJECT=physics node --experimental-strip-types --use-env-proxy scripts/finish-items.mjs
// Updates <root>/units/u-N-solutions.json in place and writes <root>/work/finish-report.json.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { S } from "./subject.mjs";
import { SYNTAX } from "./textmatch.mjs";
import { FIGURE_SCHEMA } from "../supabase/functions/ai/core.ts";
import { toFigure } from "../supabase/functions/ai/figure.ts";

const ROOT = S.root;
const str = { type: "string" }, strs = { type: "array", items: str }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
async function cached(name, run) {
  const f = join(ROOT, "work", `finish-${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const r = await run();
  writeFileSync(f, JSON.stringify(r, null, 1));
  return r;
}
async function structured(name, instructions, input, schema) {
  const text = await luna({ max_output_tokens: 50000, reasoning: { effort: "high" }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `finish-${name}`, "items");
  return JSON.parse(text);
}

const SOLVE = `You are a Punjab board Class 9 ${S.name} teacher writing the answer a board examiner gives full marks to, the way the
textbook teaches it. Some items ask for a drawing (vectors, forces, a circuit, a graph): then the steps say how to draw it (the scale
chosen, e.g. "1 cm = 100 m s⁻¹", each line's length and angle, labels) and "figure" gives the finished drawing as data the app draws:
kind "forces" for vectors/forces (each arrow: label with its value, angle in degrees from the +x axis, size), "circuit" for circuits,
"graph" for a graph; otherwise figure kind "none" with empty fields. Numericals: given data in SI units, formula, substitution with units,
answer with unit. Theory: the textbook's points, one per line. If a printed answer is given, reach it unless it is impossible.
steps: one line per step. finalAnswer: the final result (for a drawing, what the finished figure shows). accept: patterns for a typed
numerical answer (empty for theory and drawings).
${SYNTAX}`;
const solveSchema = obj({ items: arr(obj({ id: str, steps: strs, finalAnswer: str, accept: arr(strs), figure: FIGURE_SCHEMA })) });
const CHECK = `You are an independent Class 9 ${S.name} examiner (Punjab board). For each candidate answer decide ok: the working is correct
and complete, the final answer (value and unit) is right, and when a figure is given it shows exactly what the question asks (right
directions, angles and labels; lengths in proportion to the values). problem: "" or one sentence.`;
const checkSchema = obj({ items: arr(obj({ id: str, ok: bool, problem: str })) });

const todo = [];
const files = new Map();
for (let u = 1; u <= 30; u++) {
  const f = join(ROOT, "units", `u-${u}-solutions.json`);
  if (!existsSync(f)) continue;
  const data = JSON.parse(readFileSync(f, "utf8"));
  files.set(f, data);
  data.sections.forEach((sec, si) => sec.solutions.forEach((x, k) => {
    if (x.kind === "EXERCISE" && !x.checked) todo.push({ f, si, k, id: `${u}:${si}:${k}`, unit: u, source: x.source, question: x.question, bookAnswer: x.answer ?? "" });
  }));
}
console.log(`${todo.length} items without a checked solution`);
if (!todo.length) process.exit(0);

const input = (list) => JSON.stringify(list.map(({ id, source, question, bookAnswer }) => ({ id, source, question, printedAnswer: bookAnswer })));
const solved = await cached("solve", () => structured("solve", SOLVE, input(todo), solveSchema));
const forCheck = (sol) => JSON.stringify(sol.items.map((s) => ({ id: s.id, question: todo.find((t) => t.id === s.id)?.question,
  steps: s.steps, finalAnswer: s.finalAnswer, figure: s.figure.kind === "none" ? null : s.figure })));
let checked = await cached("check", () => structured("check", CHECK, forCheck(solved), checkSchema));

// One more attempt for anything the examiner rejected, with the reason.
const rejected = solved.items.filter((s) => !checked.items.find((c) => c.id === s.id)?.ok);
let finalItems = solved.items;
if (rejected.length) {
  const again = await cached("solve-2", () => structured("solve", `${SOLVE}\nA previous answer was rejected by an examiner for the reason given; fix it.`,
    JSON.stringify(rejected.map((s) => ({ ...JSON.parse(input([todo.find((t) => t.id === s.id)]))[0], rejectedBecause: checked.items.find((c) => c.id === s.id)?.problem ?? "" }))), solveSchema));
  const recheck = await cached("check-2", () => structured("check", CHECK, forCheck(again), checkSchema));
  finalItems = [...solved.items.filter((s) => !rejected.includes(s)), ...again.items];
  checked = { items: [...checked.items.filter((c) => !rejected.some((r) => r.id === c.id)), ...recheck.items] };
}

const report = { filled: [], forTeacher: [] };
for (const s of finalItems) {
  const t = todo.find((x) => x.id === s.id);
  const v = checked.items.find((c) => c.id === s.id);
  if (!t) continue;
  const drawn = s.figure.kind === "none" ? null : toFigure(s.figure);
  if (!v?.ok || (s.figure.kind !== "none" && !drawn)) { report.forTeacher.push({ unit: t.unit, source: t.source, why: v?.problem || "the figure could not be drawn" }); continue; }
  const x = files.get(t.f).sections[t.si].solutions[t.k];
  Object.assign(x, { steps: s.steps, answer: s.finalAnswer, checked: true, accept: s.accept.map((g) => g.filter((p) => p.trim())).filter((g) => g.length) });
  if (drawn) x.drawn = drawn;
  report.filled.push({ unit: t.unit, source: t.source, drawn: !!drawn });
}
for (const [f, data] of files) writeFileSync(f, JSON.stringify(data, null, 1));
writeFileSync(join(ROOT, "work", "finish-report.json"), JSON.stringify(report, null, 1));
console.log(`Filled ${report.filled.length} (${report.filled.filter((r) => r.drawn).length} with a drawn figure); ${report.forTeacher.length} left for a teacher. $${costSoFar().toFixed(3)}`);
