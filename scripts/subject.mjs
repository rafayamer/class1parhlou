// Which course the content scripts build: SUBJECT=maths (default) or SUBJECT=physics.
// Everything a script needs to know about a subject lives here, so the pipeline itself stays the same for every subject.

const SUBJECTS = {
  maths: {
    key: "maths",
    id: "pb-9-math",
    root: "content/class9-maths",
    name: "Mathematics",
    short: "maths",
    book: "Mathematics 9, National Curriculum 2023, English medium",
    idPrefix: "",
    unitWord: "Unit",
    unitHeading: (u) => new RegExp(`^#\\s*Unit\\s*${u}\\b`, "mi"),
    // The unit's closing exercise (its own module).
    reviewHeading: /^#{0,4}\s*REVIEW EXERCISE/i,
    reviewTitle: (u) => `Unit ${u} review`,
    answerKeyAtBack: true,
    backMatter: null,
    writtenMarks: [2, 4],
    sourceExample: (u) => `"Ex ${u}.1 Q2(iii)"`,
    examiner: "Class 9 maths examiner",
    sectionBrief: "This is one main section of the unit, with its sub-sections, examples and exercise.",
    reviewBrief: "This is the unit's REVIEW EXERCISE. mcqs: include ALL its multiple-choice items verbatim with the book's answers (fromBook true), up to 15. written: its other questions (verbatim). explain: 4–6 cards summarising the whole unit. example: one review question solved step by step. yourTurn: two review questions.",
    checkRules: `Precision: answers found with four-figure log/antilog tables, or rounded as the question asks, are equivalent when they agree to
the precision those methods give (e.g. log 0.0876 = −1.0575 from tables vs −1.0580 exactly: equivalent; 0.02675 vs 0.020675: NOT
equivalent). Decimals that agree to 3 significant figures are equivalent unless the question demands more.`,
    // How a transcribed page marks its parts (book-ocr.mjs).
    ocr: `You transcribe one page of a Pakistani Class 9 Mathematics textbook (Punjab, English medium) exactly.
Rules:
- Copy every word, number and symbol exactly as printed, in reading order. Do not summarise, correct, solve or add anything.
- Write maths in plain Unicode: √, ∛, ², ³, ×, ÷, ±, ≠, ≤, ≥, ∈, ∉, ⊆, ⊂, ∪, ∩, ∅, →, ⇒, ⇔, ∧, ∨, ~, θ, π, ∞, |x|, log x, log_b x.
  Fractions as a/b, with brackets when a or b has more than one term: (x + 1)/(x − 2). Powers that are not simple digits as a^(n+1).
  Matrices as [[a, b], [c, d]]. Sets with braces.
- Use Markdown: '#' for unit titles, '##' for numbered sections (e.g. '## 1.1.2 Decimal Representation'), '###' for sub-headings.
- Mark these blocks on their own line exactly: 'EXAMPLE <n>:' , 'SOLUTION:' , 'EXERCISE <x.y>' , 'Q<n>.' for exercise questions with '(i)', '(ii)' parts,
  'MCQ <n>.' with options '(a) ... (b) ... (c) ... (d) ...', 'ANSWERS' for answer sections, 'REMEMBER:' / 'NOTE:' / 'KEY FACT:' for boxes.
- Tables as Markdown tables. For a picture or diagram write [FIGURE: one-line description of what it shows].
- Ignore page headers ('Mathematics - 9', 'Unit – N: ...'), the 'NOT FOR SALE' footer and any website watermark; but put the printed page number on the first line as 'PAGE <n>'.`,
  },
  physics: {
    key: "physics",
    id: "pb-9-phy",
    root: "content/class9-physics",
    name: "Physics",
    short: "physics",
    book: "Physics 9, National Curriculum 2023, English medium",
    idPrefix: "phy-",
    unitWord: "Chapter",
    unitHeading: (u) => new RegExp(`^#\\s*Chapter\\s*${u}\\b`, "mi"),
    // The chapter's end-of-chapter exercise (sections A–E) is its own module.
    reviewHeading: /^#{0,4}\s*SECTION A\b/i,
    reviewTitle: (u) => `Chapter ${u} exercise`,
    answerKeyAtBack: false,
    // Pages after the last chapter.
    backMatter: /^#{0,4}\s*(BIBLIOGRAPHY|GLOSSARY|INDEX)\s*$/im,
    writtenMarks: [2, 4, 5],
    sourceExample: (u) => `"Numerical ${u}.3", "Short ${u}.5", "CRQ ${u}.2", "Comprehensive ${u}.1" or "Example ${u}.2" (the book's own labels)`,
    examiner: "Class 9 physics examiner (Punjab board)",
    sectionBrief: `This is one numbered section of the chapter, with its worked examples and activities. The chapter's exercise is given after it.
mcqs: NEW board-style MCQs on this section only (fromBook false); do not copy the book's exercise MCQs here.
written: this section's worked examples (source "Example n.m") and the chapter exercise items that test THIS section
(short answer, constructed response, comprehensive and numerical; source like "Numerical n.m"), verbatim.`,
    reviewBrief: `This is the chapter's END-OF-CHAPTER EXERCISE (Sections A–E). mcqs: include ALL of Section A verbatim with the correct answer (fromBook true).
written: every item of Sections B–E verbatim, one part per item, with source labels "Short n.m", "CRQ n.m", "Comprehensive n.m", "Numerical n.m"
(add the part letter, e.g. "Numerical 1.3(a)"). For numericals, finalAnswer must agree with the answer printed after the problem ("ANSWER:")
in value AND unit. explain: 4–6 cards summarising the whole chapter. example: one numerical solved step by step (given, formula, substitution
with units, answer with unit). yourTurn: two numericals.`,
    checkRules: `Physics answers: a numerical answer is equivalent only when the value agrees to 3 significant figures (or the precision the
question asks) AND the unit is the same quantity in an equivalent unit (5 m s⁻¹ = 5 m/s; 0.5 km = 500 m; 2.4 × 10³ kg m s⁻² = 2400 N).
A missing or wrong unit is NOT equivalent. Scientific notation and decimal forms of the same value are equivalent. For theory questions,
equivalent means the same physics points as the textbook answer, in any wording.`,
    ocr: `You transcribe one page of a Pakistani Class 9 Physics textbook (Punjab, English medium, National Curriculum 2023) exactly.
Rules:
- Copy every word, number, symbol and unit exactly as printed, in reading order. Do not summarise, correct, solve or add anything.
- Write maths and units in plain Unicode: ², ³, ⁻¹, ⁻², ×, ÷, ±, ≈, ≠, ≤, ≥, √, θ, π, Δ, Σ, μ, Ω, °, °C, λ, ρ, η, →. Powers of ten as 3.0 × 10⁸ m s⁻¹.
  Fractions as a/b, with brackets when a or b has more than one term: (v − u)/t. Subscripts as v_f, F_net. Vectors with an arrow word: "vector F".
- Use Markdown: '# Chapter <n> <title>' for chapter titles, '##' for numbered sections (e.g. '## 1.2 Physical Quantities'), '###' for sub-headings.
- Mark these blocks on their own line exactly:
  'EXAMPLE <n.m>:' for worked examples (copy the book's number), then 'SOLUTION:' before its working;
  'ACTIVITY <n.m>' for activities; 'REMEMBER:' / 'NOTE:' / 'DO YOU KNOW?' / 'FOR YOUR INFORMATION:' / 'SAFETY:' for boxes;
  'STUDENT LEARNING OUTCOMES' and 'SUMMARY' for those parts;
  the end-of-chapter exercise sections exactly as 'SECTION A: MULTIPLE CHOICE QUESTIONS', 'SECTION B: SHORT ANSWER QUESTIONS',
  'SECTION C: CONSTRUCTED RESPONSE QUESTIONS', 'SECTION D: COMPREHENSIVE QUESTIONS', 'SECTION E: NUMERICAL PROBLEMS';
  each exercise item as 'Q<n.m>.' with the book's own number (e.g. 'Q1.4.'), parts as '(a)', '(b)' or '(i)', '(ii)';
  MCQ options as '(a) ... (b) ... (c) ... (d) ...'; an answer printed after a numerical problem (usually in brackets) on its own line as 'ANSWER: <exactly as printed>'.
- Tables as Markdown tables (observation tables keep their blank cells). For a picture, diagram, circuit, ray diagram or graph write
  [FIGURE <label if printed, e.g. Fig. 1.5>: one-line description of exactly what it shows, with every label and value on it].
- Ignore running page headers, the 'NOT FOR SALE' footer and any website watermark ('PCTB Textbook', 'Web version'); but put the printed page number on the first line as 'PAGE <n>'.`,
  },
};

const key = (process.env.SUBJECT ?? "maths").toLowerCase();
export const S = SUBJECTS[key];
if (!S) throw new Error(`Unknown SUBJECT "${key}" (choose: ${Object.keys(SUBJECTS).join(", ")})`);
