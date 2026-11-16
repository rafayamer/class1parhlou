// Cuts every figure out of the textbook pages, so questions and solutions can show the real diagram.
// For each page whose transcription has [FIGURE: …] markers, Luna's vision returns a box for each figure (in order);
// the box is cropped from the page at 220 dpi and saved as WebP into the app's assets.
//   node --use-env-proxy scripts/book-figures.mjs <book.pdf>
// Writes android/app/src/main/assets/figures/<id>.webp and content/class9-maths/figures.json
// ({ id, page, index, description }). Pages already done are skipped (work/fig-pNNN.json).
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { luna, costSoFar } from "./luna-budget.mjs";

const ROOT = "content/class9-maths";
const OUT = "android/app/src/main/assets/figures";
const [pdf] = process.argv.slice(2);
mkdirSync(OUT, { recursive: true });
const DPI = 220;

const num = { type: "number" }, str = { type: "string" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const schema = obj({ figures: { type: "array", items: obj({ index: { type: "integer" }, found: { type: "boolean" }, x0: num, y0: num, x1: num, y1: num }) } });
const PROMPT = `You locate figures (diagrams, graphs, Venn diagrams, number lines, triangles, constructions, tables drawn as pictures) on one
page of a Class 9 maths textbook. You get the page image and a numbered list of the figures on it, in reading order, each with a
short description. For each, return its bounding box as fractions of the page width and height (x0,y0 = top-left, x1,y1 =
bottom-right, 0..1), tight around the drawing including its labels but excluding body text and question numbers.
found = false if you cannot see it on the page.`;

const pages = [];
for (let p = 1; p <= 288; p++) {
  const f = join(ROOT, "pages", `p${String(p).padStart(3, "0")}.md`);
  if (!existsSync(f)) continue;
  const figs = [...readFileSync(f, "utf8").matchAll(/\[FIGURE:\s*([^\]]*)\]/g)].map((m) => m[1].trim());
  if (figs.length) pages.push({ p, figs });
}

function render(page, dpi) {
  const base = join(tmpdir(), `parhlou-fig-${process.pid}-${page}`);
  execFileSync("pdftoppm", ["-f", String(page), "-l", String(page), "-r", String(dpi), "-png", "-singlefile", pdf, base]);
  return `${base}.png`;
}
function size(png) {
  const b = readFileSync(png);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

const all = [];
let next = 0;
await Promise.all(Array.from({ length: 5 }, async () => {
  while (next < pages.length) {
    const { p, figs } = pages[next++];
    const cache = join(ROOT, "work", `fig-p${String(p).padStart(3, "0")}.json`);
    let boxes;
    const png = render(p, DPI);
    if (existsSync(cache)) boxes = JSON.parse(readFileSync(cache, "utf8"));
    else {
      try {
        const small = render(p, 110);
        const b64 = readFileSync(small).toString("base64");
        const text = await luna({ max_output_tokens: 8000, reasoning: { effort: "low" }, instructions: PROMPT,
          input: [{ role: "user", content: [
            { type: "input_text", text: figs.map((d, i) => `${i + 1}. ${d}`).join("\n") },
            { type: "input_image", image_url: `data:image/png;base64,${b64}`, detail: "high" }] }],
          text: { format: { type: "json_schema", name: "figures", strict: true, schema } } }, "figures", `page ${p}`);
        boxes = JSON.parse(text).figures;
        writeFileSync(cache, JSON.stringify(boxes));
      } catch (e) { console.log(`page ${p}: ${e.message}`); unlinkSync(png); continue; }
    }
    const { w, h } = size(png);
    for (const b of boxes) {
      const k = b.index - 1;
      if (!b.found || k < 0 || k >= figs.length) continue;
      const pad = 0.006; // boxes are verified to include every label (verify-figures.mjs)
      const x = Math.max(0, Math.floor((Math.min(b.x0, b.x1) - pad) * w)), y = Math.max(0, Math.floor((Math.min(b.y0, b.y1) - pad) * h));
      const x2 = Math.min(w, Math.ceil((Math.max(b.x0, b.x1) + pad) * w)), y2 = Math.min(h, Math.ceil((Math.max(b.y0, b.y1) + pad) * h));
      if (x2 - x < 30 || y2 - y < 30) continue;
      const id = `p${String(p).padStart(3, "0")}-${k + 1}`;
      execFileSync("cwebp", ["-quiet", "-q", "80", "-crop", String(x), String(y), String(x2 - x), String(y2 - y), png, "-o", join(OUT, `${id}.webp`)]);
      all.push({ id, page: p, index: k + 1, description: figs[k] });
    }
    unlinkSync(png);
  }
}));
all.sort((a, b) => a.id.localeCompare(b.id));
writeFileSync(join(ROOT, "figures.json"), JSON.stringify(all, null, 1));
console.log(`${all.length} figures from ${pages.length} pages (${pages.reduce((s, x) => s + x.figs.length, 0)} markers). $${costSoFar().toFixed(3)}`);
