// Checks every cropped book figure against its page and fixes bad crops: a crop must contain the whole drawing with all
// its labels, and no body text or neighbouring figure. Luna sees the crop and the page (with the current box drawn) and
// either confirms it or returns a corrected box, which is cropped again.
//   node --use-env-proxy scripts/verify-figures.mjs <book.pdf>
// Updates the boxes cached in work/fig-pNNN.json; re-run book-figures.mjs afterwards to re-crop.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { luna, costSoFar } from "./luna-budget.mjs";

const ROOT = "content/class9-maths";
const [pdf] = process.argv.slice(2);
const num = { type: "number" };
const obj = (p) => ({ type: "object", additionalProperties: false, required: Object.keys(p), properties: p });
const schema = obj({ ok: { type: "boolean" }, problem: { type: "string" }, x0: num, y0: num, x1: num, y1: num });
const PROMPT = `You check a figure cut out of a Class 9 maths textbook page. Image 1 is the whole page with the current crop box drawn in red;
image 2 is the crop. The crop must contain the WHOLE figure described (every line, point label, angle mark, measurement and caption
such as "Fig. 3") and must NOT contain body text, question text, headings or another figure.
ok = true if the crop is right. Otherwise give the corrected box for the described figure as fractions of the page (0..1, x0,y0 =
top-left, x1,y1 = bottom-right) and say in "problem" what was wrong. When ok, repeat the current box.`;

const figures = JSON.parse(readFileSync(join(ROOT, "figures.json"), "utf8"));
const byPage = new Map();
for (const f of figures) (byPage.get(f.page) ?? byPage.set(f.page, []).get(f.page)).push(f);

let fixed = 0, checked = 0;
const pages = [...byPage.keys()];
let next = 0;
await Promise.all(Array.from({ length: 5 }, async () => {
  while (next < pages.length) {
    const p = pages[next++];
    const cacheFile = join(ROOT, "work", `fig-p${String(p).padStart(3, "0")}.json`);
    const verifiedFile = join(ROOT, "work", `figv-p${String(p).padStart(3, "0")}.json`);
    if (existsSync(verifiedFile) || !existsSync(cacheFile)) continue;
    const boxes = JSON.parse(readFileSync(cacheFile, "utf8"));
    const base = join(tmpdir(), `parhlou-figv-${process.pid}-${p}`);
    execFileSync("pdftoppm", ["-f", String(p), "-l", String(p), "-r", "110", "-png", "-singlefile", pdf, base]);
    const page = `${base}.png`;
    for (const f of byPage.get(p)) {
      const b = boxes.find((x) => x.index === f.index);
      if (!b || !b.found) continue;
      // Page with the box drawn, and the crop itself (PIL does the drawing and cropping).
      const marked = `${base}-m${f.index}.png`, crop = `${base}-c${f.index}.png`;
      execFileSync("python3", ["-I", "-c", `
import sys
from PIL import Image, ImageDraw
im = Image.open(sys.argv[1]).convert("RGB"); w, h = im.size
x0, y0, x1, y1 = [float(v) for v in sys.argv[4:8]]
box = (int(min(x0,x1)*w), int(min(y0,y1)*h), int(max(x0,x1)*w), int(max(y0,y1)*h))
im.crop(box).save(sys.argv[3])
d = ImageDraw.Draw(im); d.rectangle(box, outline=(255,0,0), width=3); im.save(sys.argv[2])
`, page, marked, crop, String(b.x0), String(b.y0), String(b.x1), String(b.y1)]);
      const img = (f) => ({ type: "input_image", image_url: `data:image/png;base64,${readFileSync(f).toString("base64")}`, detail: "high" });
      try {
        const v = JSON.parse(await luna({ max_output_tokens: 6000, reasoning: { effort: "low" }, instructions: PROMPT,
          input: [{ role: "user", content: [{ type: "input_text", text: `Figure: ${f.description}` }, img(marked), img(crop)] }],
          text: { format: { type: "json_schema", name: "figure_check", strict: true, schema } } }, "figures-verify", f.id));
        checked++;
        if (!v.ok && v.x1 > v.x0 && v.y1 > v.y0) { Object.assign(b, { x0: v.x0, y0: v.y0, x1: v.x1, y1: v.y1 }); fixed++; console.log(`${f.id}: ${v.problem}`); }
      } catch (e) { console.log(`${f.id}: ${e.message}`); }
      unlinkSync(marked); unlinkSync(crop);
    }
    unlinkSync(page);
    writeFileSync(cacheFile, JSON.stringify(boxes));
    writeFileSync(verifiedFile, "{}");
  }
}));
console.log(`checked ${checked}, corrected ${fixed}. $${costSoFar().toFixed(3)}`);
