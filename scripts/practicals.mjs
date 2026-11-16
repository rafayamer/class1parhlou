// Practicals for a science subject, built from the textbook's own activities (the National Curriculum 2023 book's hands-on work).
// For each activity: aim, apparatus, a labelled diagram description, the procedure (the book's steps), an observation table,
// the calculation, the result, precautions and viva questions with answers. A second, independent examiner checks every
// practical for correct physics; anything it rejects is shown with the book's own text only.
//   SUBJECT=physics node --use-env-proxy scripts/practicals.mjs
// Writes <root>/units/practicals.json (applied by assemble-course.mjs). Stages cached in work/prac-*.json.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { luna, costSoFar } from "./luna-budget.mjs";
import { S } from "./subject.mjs";
import { isExcluded } from "./syllabus.mjs";

const ROOT = S.root;
const syllabus = JSON.parse(readFileSync(join(ROOT, "syllabus-2027.json"), "utf8"));
const str = { type: "string" }, strs = { type: "array", items: str }, bool = { type: "boolean" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const arr = (i) => ({ type: "array", items: i });
async function cached(name, run) {
  const f = join(ROOT, "work", `prac-${name}.json`);
  if (existsSync(f)) return JSON.parse(readFileSync(f, "utf8"));
  const r = await run();
  writeFileSync(f, JSON.stringify(r, null, 1));
  return r;
}
async function structured(name, item, instructions, input, schema, effort = "medium") {
  const text = await luna({ max_output_tokens: 40000, reasoning: { effort }, instructions, input,
    text: { format: { type: "json_schema", name, strict: true, schema } } }, `prac-${name}`, item);
  return JSON.parse(text);
}

// ── The book's activities, straight from the transcription (no AI) ─────────────────────────────────────────
const activities = [];
for (let u = 1; u <= 30; u++) {
  const f = join(ROOT, "work", `u${u}-outline.json`);
  if (!existsSync(f)) continue;
  const o = JSON.parse(readFileSync(f, "utf8"));
  for (const sec of o.sections) {
    if (sec.review) continue;
    const lines = sec.text.split("\n\nCHAPTER EXERCISE (pick only")[0].split("\n");
    let cur = null;
    const close = () => { if (cur) activities.push(cur); cur = null; };
    for (const raw of lines) {
      const l = raw.trim();
      const head = /^\**\s*#*\s*ACTIVITY\s*([0-9.]+)\s*\**\s*:?\s*(.*)$/i.exec(l);
      if (head) {
        close();
        // Chapters that number activities 1, 2, 3 … are labelled with the chapter: "Activity 8.1".
        const n = head[1].includes(".") ? head[1] : `${u}.${head[1]}`;
        cur = { unit: u, section: `${sec.number} ${sec.title}`, label: `Activity ${n}`, bookLabel: `Activity ${head[1]}`, text: head[2] ? [head[2]] : [] };
        continue;
      }
      if (!cur) continue;
      if (/^#{1,4}\s/.test(l) || /^EXAMPLE\s+\d/i.test(l) || /^SECTION\s+[A-E]\b/i.test(l) || cur.text.length > 40) { close(); continue; }
      if (l && !/^PAGE\s+\d+$/i.test(l)) cur.text.push(l);
    }
    close();
  }
}
console.log(`${activities.length} activities in the book`);

const BUILD = `You are a Punjab board Class 9 ${S.name} teacher preparing students for the practical exam. You get ONE activity from the
textbook (National Curriculum 2023), copied exactly, with the section it belongs to. Write it up as a practical, as students write it
in their practical notebook:
- title: a short name for the practical (e.g. "Thickness of a coin using a metre rule").
- aim: one sentence.
- apparatus: the items needed (from the book's text; add only obvious basics like a pencil or a table if needed).
- diagram: one sentence describing the labelled diagram to draw (what is drawn and every label), or "" if none is needed.
- procedure: the steps, one per line, following the BOOK'S instructions in order and in its words where it gives them.
- table: the observation table: columns (with units) and 2–5 rows of sample readings that a careful student could really get
  (realistic values; say nothing about where they came from); empty when the activity has no readings.
- calculation: the working from the sample readings to the result, one line per step, with units ("" lines removed when none).
- result: the conclusion in one or two sentences, with the value and unit when there is one.
- precautions: 3–5 precautions for accurate, safe work.
- viva: 4–6 short viva questions an examiner asks about this practical, each with a correct short answer.
Use correct physics and SI units throughout. Never mention any book, website or publisher by name.`;
const buildSchema = obj({
  title: str, aim: str, apparatus: strs, diagram: str, procedure: strs,
  table: obj({ columns: strs, rows: arr(strs) }), calculation: strs, result: str, precautions: strs,
  viva: arr(obj({ question: str, answer: str })),
});
const CHECK = `You are an independent Class 9 ${S.name} examiner. Check this practical write-up against the textbook activity it is based on.
ok = true only if: every statement and viva answer is scientifically correct; the procedure follows the activity; the sample readings are
realistic and the calculation from them is correct with correct units; and the result follows from the readings.
problems: each thing that is wrong, one sentence each (empty when ok).`;
const checkSchema = obj({ ok: bool, problems: strs });

const out = [];
let next = 0;
await Promise.all(Array.from({ length: 4 }, async () => {
  while (next < activities.length) {
    const a = activities[next++];
    const key = a.label.replace(/\s+/g, "-").toLowerCase();
    const input = JSON.stringify({ chapter: a.unit, section: a.section, activity: a.bookLabel, bookText: a.text.join("\n") });
    let p = await cached(`${key}`, () => structured("practical", a.label, BUILD, input, buildSchema));
    let v = await cached(`${key}-check`, () => structured("practical_check", a.label, CHECK, JSON.stringify({ activity: a.text.join("\n"), practical: p }), checkSchema, "high"));
    if (!v.ok) {
      // One rewrite with the examiner's reasons, then a fresh check.
      p = await cached(`${key}-fix`, () => structured("practical", a.label, `${BUILD}\nA previous write-up was rejected by an examiner for these reasons; fix them:\n- ${v.problems.join("\n- ")}`, input, buildSchema));
      v = await cached(`${key}-recheck`, () => structured("practical_check", a.label, CHECK, JSON.stringify({ activity: a.text.join("\n"), practical: p }), checkSchema, "high"));
    }
    out.push({ id: `${S.idPrefix}prac-${a.label.replace(/^Activity\s+/i, "").replace(/\./g, "-")}`, unit: a.unit, section: a.section, label: a.label,
      bookText: a.text.join("\n"), checked: v.ok, inExam: !isExcluded(syllabus, a.unit, a.bookLabel) && !isExcluded(syllabus, a.unit, a.label),
      ...(v.ok ? p : { title: p.title, aim: p.aim, apparatus: [], diagram: "", procedure: [], table: { columns: [], rows: [] }, calculation: [], result: "", precautions: [], viva: [] }) });
    console.log(`  ${a.label}: ${v.ok ? "checked" : `book text only (${v.problems.length} problems)`}. $${costSoFar().toFixed(3)}`);
  }
}));
out.sort((x, y) => x.unit - y.unit || x.label.localeCompare(y.label, undefined, { numeric: true }));
writeFileSync(join(ROOT, "units", "practicals.json"), JSON.stringify({ practicals: out }, null, 1));
console.log(`Practicals: ${out.length} (${out.filter((p) => p.checked).length} checked). $${costSoFar().toFixed(3)}`);
