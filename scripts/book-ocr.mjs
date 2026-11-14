// Reads textbook pages with GPT-6 Luna's vision and writes a faithful Markdown transcription per page.
// Usage: node --use-env-proxy scripts/book-ocr.mjs <book.pdf> <firstPage> <lastPage> [outDir]
// Pages already transcribed are skipped. Every call's cost is logged and the run stops at the budget cap.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { costSoFar, luna } from "./luna-budget.mjs";

const [pdf, first, last, outDir = "content/class9-maths/pages"] = process.argv.slice(2);
mkdirSync(outDir, { recursive: true });

const INSTRUCTIONS = `You transcribe one page of a Pakistani Class 9 Mathematics textbook (Punjab, English medium) exactly.
Rules:
- Copy every word, number and symbol exactly as printed, in reading order. Do not summarise, correct, solve or add anything.
- Write maths in plain Unicode: √, ∛, ², ³, ×, ÷, ±, ≠, ≤, ≥, ∈, ∉, ⊆, ⊂, ∪, ∩, ∅, →, ⇒, ⇔, ∧, ∨, ~, θ, π, ∞, |x|, log x, log_b x.
  Fractions as a/b, with brackets when a or b has more than one term: (x + 1)/(x − 2). Powers that are not simple digits as a^(n+1).
  Matrices as [[a, b], [c, d]]. Sets with braces.
- Use Markdown: '#' for unit titles, '##' for numbered sections (e.g. '## 1.1.2 Decimal Representation'), '###' for sub-headings.
- Mark these blocks on their own line exactly: 'EXAMPLE <n>:' , 'SOLUTION:' , 'EXERCISE <x.y>' , 'Q<n>.' for exercise questions with '(i)', '(ii)' parts,
  'MCQ <n>.' with options '(a) ... (b) ... (c) ... (d) ...', 'ANSWERS' for answer sections, 'REMEMBER:' / 'NOTE:' / 'KEY FACT:' for boxes.
- Tables as Markdown tables. For a picture or diagram write [FIGURE: one-line description of what it shows].
- Ignore page headers ('Mathematics - 9', 'Unit – N: ...'), the 'NOT FOR SALE' footer and any website watermark; but put the printed page number on the first line as 'PAGE <n>'.`;

async function readPage(n) {
  const out = join(outDir, `p${String(n).padStart(3, "0")}.md`);
  if (existsSync(out)) return "skip";
  const base = join(tmpdir(), `parhlou-ocr-${process.pid}-${n}`);
  execFileSync("pdftoppm", ["-f", String(n), "-l", String(n), "-r", "130", "-jpeg", "-jpegopt", "quality=82", "-singlefile", pdf, base]);
  const img = `${base}.jpg`;
  const b64 = readFileSync(img).toString("base64");
  unlinkSync(img);
  const text = await luna({
    max_output_tokens: 6000,
    instructions: INSTRUCTIONS,
    input: [{ role: "user", content: [
      { type: "input_text", text: `Transcribe this page (PDF page ${n}).` },
      { type: "input_image", image_url: `data:image/jpeg;base64,${b64}`, detail: "high" },
    ] }],
  }, "ocr", `page ${n}`, { background: false });
  writeFileSync(out, text);
  return text.length;
}

const pages = [];
for (let n = Number(first); n <= Number(last); n++) pages.push(n);
let next = 0;
await Promise.all(Array.from({ length: 6 }, async () => {
  while (next < pages.length) {
    const n = pages[next++];
    try {
      const res = await readPage(n);
      if (res !== "skip") console.log(`page ${n}: ${res} chars, total $${costSoFar().toFixed(4)}`);
    } catch (e) {
      console.log(`page ${n}: FAILED ${e.message}`);
      if (String(e.message).includes("Budget cap")) process.exit(2);
    }
  }
}));
