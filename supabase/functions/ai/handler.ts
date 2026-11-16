// Maps an app request to a Luna task. The app sends ids and the student's own text only;
// questions, answer keys and rubrics are looked up here, on the server.
import { runExaminer, type PaperReportTask, type PickPaperTask, type ReadScriptTask } from "./examiner.ts";
import { SAFETY_REPLIES, runTask, type BookRef, type Fetch, type Task, type Turn, type UstaadReply, type Usage, type Video } from "./core.ts";

export interface Content { course: any; subjectId: string; notes: any[]; written: any[]; mcqs: any[] }
export interface Limits { maxImages: number; maxImageChars: number; maxTextChars: number }
export const DEFAULT_LIMITS: Limits = { maxImages: 5, maxImageChars: 2_800_000, maxTextChars: 6_000 };

export type Outcome = { status: number; body: unknown; usage?: Usage };

const STYLES = ["NORMAL", "SIMPLER", "ANOTHER_EXAMPLE", "STEPS", "QUIZ_ME"];

export function toTask(req: any, content: Content, limits: Limits = DEFAULT_LIMITS): Task | ReadScriptTask | PickPaperTask | PaperReportTask | { error: string; status: number } {
  const bad = (error: string, status = 400) => ({ error, status });
  if (!req || typeof req !== "object") return bad("BAD_REQUEST");
  if (req.task === "tutor") {
    const question = String(req.question ?? "").slice(0, limits.maxTextChars);
    const style = STYLES.includes(req.style) ? req.style : "NORMAL";
    const subjects: string[] = Array.isArray(req.subjectIds) ? req.subjectIds : [];
    // Only notes of the learner's own subjects are ever shown to the model, and only the relevant ones.
    const all = subjects.includes(content.subjectId) ? content.notes : [];
    const last = all.find((n) => n.id === req.lastNoteId);
    const notes = relevantNotes(all, question, { lastNoteId: req.lastNoteId, moduleId: req.moduleId });
    const followUp = question.trim() === "" && last ? `Continue with the topic "${last.title}".` : question;
    if (followUp.trim() === "") return bad("EMPTY_QUESTION");
    return {
      task: "tutor", course: content.course, question: followUp, style, notes,
      examActive: req.examActive === true,
      pendingCheck: req.awaitingCheck === true && last ? last.checkQuestion : null,
    };
  }
  if (req.task === "mcq_feedback") {
    const mcq = allMcqs(content).find((m) => m.id === req.questionId);
    if (!mcq) return bad("SOURCE_NOT_APPROVED", 404);
    const chosen = Number(req.chosen);
    if (!Number.isInteger(chosen) || chosen < 0 || chosen >= mcq.options.length) return bad("BAD_REQUEST");
    return { task: "mcq_feedback", course: content.course, mcq, chosen };
  }
  if (req.task === "mark_written") {
    const question = content.written.find((w) => w.id === req.questionId);
    if (!question) return bad("RUBRIC_NOT_APPROVED", 404);
    const images: string[] = Array.isArray(req.images) ? req.images : [];
    if (images.length > limits.maxImages) return bad("TOO_MANY_IMAGES", 413);
    if (images.some((i) => typeof i !== "string" || !/^data:image\/(png|jpeg);base64,/.test(i) || i.length > limits.maxImageChars)) {
      return bad("IMAGE_RESCAN_REQUIRED", 413);
    }
    return { task: "mark_written", course: content.course, question, answer: String(req.answer ?? "").slice(0, limits.maxTextChars), images,
      handwritten: req.handwritten === true && images.length > 0 };
  }
  if (req.task === "read_script") {
    // The phone sends the paper's question ids and printed labels; question texts come from the server's own content.
    const image = req.image;
    if (typeof image !== "string" || !/^data:image\/(png|jpeg);base64,/.test(image) || image.length > limits.maxImageChars) return bad("IMAGE_RESCAN_REQUIRED", 413);
    const asked: any[] = Array.isArray(req.questions) ? req.questions.slice(0, 80) : [];
    const questions = asked.map((x) => {
      const w = content.written.find((q) => q.id === x?.id);
      return w && typeof x.label === "string" ? { id: w.id, label: x.label.slice(0, 20), text: String(w.text) } : null;
    }).filter((x): x is { id: string; label: string; text: string } => x !== null);
    if (!questions.length) return bad("RUBRIC_NOT_APPROVED", 404);
    const mcqCount = Math.min(Math.max(Number(req.mcqCount) || 0, 0), 30);
    const page = Math.min(Math.max(Number(req.page) || 1, 1), 60);
    return { task: "read_script", course: content.course, image, questions, mcqCount, page } as ReadScriptTask;
  }
  if (req.task === "paper_report") {
    // The facts are the app's own sums of the student's marks; they carry no answer keys and are capped in size.
    const facts = typeof req.facts === "string" ? req.facts.slice(0, 12_000) : "";
    if (!facts.trim()) return bad("BAD_REQUEST");
    const topics = (Array.isArray(req.topics) ? req.topics : []).filter((x: unknown) => typeof x === "string").slice(0, 60).map((x: string) => x.slice(0, 120));
    const language = ["English", "Urdu", "English with Roman Urdu explanations"].includes(req.language) ? req.language : "English";
    return { task: "paper_report", course: content.course, facts, topics, profile: String(req.profile ?? "").slice(0, 1200), language } as PaperReportTask;
  }
  if (req.task === "pick_paper") {
    const slots: any[] = Array.isArray(req.slots) ? req.slots.slice(0, 40) : [];
    const mcqs = allMcqs(content);
    const out: PickPaperTask["slots"] = [];
    for (const s of slots) {
      const count = Number(s?.count);
      if (typeof s?.slot !== "string" || !Number.isInteger(count) || count < 1 || count > 20) return bad("BAD_REQUEST");
      const candidates = (Array.isArray(s.candidates) ? s.candidates.slice(0, 40) : []).map((c: any) => {
        const w = content.written.find((q) => q.id === c?.id) ?? mcqs.find((m) => m.id === c?.id);
        if (!w) return null;
        return { id: w.id, text: String(w.text ?? w.stem ?? ""), marks: Number(w.marks ?? 1), topic: String(c.topic ?? "").slice(0, 80),
          pastPapers: Array.isArray(w.tags) ? w.tags.map(String) : [], seen: c.seen === true };
      }).filter((c: any) => c !== null);
      if (candidates.length < count) return bad("NOT_ENOUGH_QUESTIONS");
      out.push({ slot: s.slot.slice(0, 40), count, candidates });
    }
    if (!out.length) return bad("BAD_REQUEST");
    return { task: "pick_paper", course: content.course, profile: String(req.profile ?? "").slice(0, 1200), slots: out } as PickPaperTask;
  }
  if (req.task === "ustaad") {
    const message = String(req.message ?? "").slice(0, limits.maxTextChars);
    const image = typeof req.image === "string" ? req.image : null;
    if (image && (!/^data:image\/(png|jpeg);base64,/.test(image) || image.length > limits.maxImageChars)) return bad("IMAGE_RESCAN_REQUIRED", 413);
    if (!message.trim() && !image) return bad("EMPTY_QUESTION");
    const subjects: string[] = Array.isArray(req.subjectIds) ? req.subjectIds : [];
    const mine = subjects.includes(content.subjectId);
    const history: Turn[] = (Array.isArray(req.history) ? req.history : []).slice(-12)
      .filter((h: any) => h && (h.role === "student" || h.role === "ustaad") && typeof h.text === "string")
      .map((h: any) => ({ role: h.role, text: h.text.slice(0, 1500) }));
    const pick = (v: unknown, allowed: string[], fallback: string) => (typeof v === "string" && allowed.includes(v) ? v : fallback);
    return {
      task: "ustaad", course: content.course, message, image, questionAsRead: null, history,
      profile: String(req.profile ?? "").slice(0, 1200),
      language: pick(req.language, ["English", "Urdu", "English with Roman Urdu explanations"], "English"),
      style: pick(req.style, ["Step by step, simple words", "Short and exam-style"], "Step by step, simple words"),
      notes: mine ? relevantNotes(content.notes, message, { moduleId: req.moduleId }, 4) : [],
      sections: mine ? sectionsOf(content) : [], book: null, examActive: req.examActive === true,
    };
  }
  return bad("UNKNOWN_TASK");
}

/** "Unit 2 · 2.3 Common Logarithm" for every section of the course. */
export function sectionsOf(content: Content): string[] {
  const chapters: any[] = (content as any).subject?.chapters ?? [];
  return chapters.flatMap((c) => (c.modules ?? []).map((m: any) => `Unit ${c.number} · ${m.title}`));
}

const tokens = (t: string) => (t.toLowerCase().normalize("NFKC").match(/[a-z]+|\d+(?:\.\d+)?|[√πθ²³]/g) ?? []).filter((w) => w.trim() && !STOP.has(w));

/**
 * Finds the textbook question a student's question is, among the course's checked solutions: most of the student's
 * words must appear in it and every number must match. Returns null for anything less than a clear match.
 */
export function bookMatch(content: Content, text: string): (BookRef & { moduleId: string; index: number; score: number }) | null {
  const q = tokens(text);
  if (q.length < 3) return null;
  const qNums = q.filter((w) => /\d/.test(w));
  let best: any = null;
  for (const c of (content as any).subject?.chapters ?? []) for (const m of c.modules ?? []) {
    (m.solutions ?? []).forEach((s: any, index: number) => {
      if (!s.checked || !Array.isArray(s.steps) || !s.steps.length) return;
      const cand = new Set(tokens(s.question));
      if (qNums.some((n) => !cand.has(n))) return;
      const hit = q.filter((w) => cand.has(w)).length / q.length;
      const back = [...cand].filter((w) => q.includes(w)).length / Math.max(cand.size, 1);
      const score = 0.7 * hit + 0.3 * back;
      if (!best || score > best.score) best = { score, moduleId: m.id, index, source: s.source, section: `Unit ${c.number} · ${m.title}`,
        question: s.question, steps: s.steps, answer: s.answer ?? "", bookAnswer: s.bookAnswer ?? "" };
    });
  }
  return best && best.score >= 0.72 ? best : null;
}

export interface Extras {
  videos?: (query: string) => Promise<Video[]>; photoQuotaLeft?: number;
  /** Records a safety flag for the owner (category, the student's message). */
  flag?: (category: string, message: string) => Promise<void>;
}

export async function handle(req: any, content: Content, fetchFn: Fetch, apiKey: string, quotaLeft: number, extras: Extras = {}): Promise<Outcome> {
  const task = toTask(req, content);
  if ("error" in task) return { status: task.status, body: { error: task.error } };
  if (quotaLeft <= 0) return { status: 429, body: { error: "QUOTA_LIMIT" } };
  if (task.task === "ustaad") return ustaad(task, content, fetchFn, apiKey, extras);
  if (task.task === "read_script" || task.task === "pick_paper" || task.task === "paper_report") {
    const { result, usage } = await runExaminer(task, fetchFn, apiKey);
    return { status: 200, body: { result, model: usage.model, promptVersion: usage.promptVersion }, usage };
  }
  const { result, usage } = await runTask(task, fetchFn, apiKey);
  return { status: 200, body: { result, model: usage.model, promptVersion: usage.promptVersion }, usage };
}

/** The row the server records for a marked answer of a signed-in student, or null if nothing should be recorded. */
export function markRecord(req: any, result: any, userId: string | null, usage: Usage | undefined): Record<string, unknown> | null {
  if (!userId || !usage?.ok || req?.task !== "mark_written" || !result || !Array.isArray(result.criteria)) return null;
  if (result.status === "NOT_ATTEMPTED" || result.status === "COULD_NOT_CHECK") return null;
  const max = result.criteria.reduce((s: number, c: any) => s + Number(c.max ?? 0), 0);
  const awarded = result.criteria.reduce((s: number, c: any) => s + Number(c.awarded ?? 0), 0);
  if (!(max > 0) || awarded < 0 || awarded > max) return null;
  const paperId = typeof req.paperId === "string" && /^[A-Za-z0-9._-]{1,80}$/.test(req.paperId) ? req.paperId : null;
  return {
    user_id: userId, paper_id: paperId, question_id: String(result.questionId), awarded, max_marks: max,
    status: String(result.status), model: usage.model, prompt_version: usage.promptVersion,
  };
}

/**
 * Checks a paper's MCQ choices against the server's own answer key (no AI call). Returns the result for the app
 * and the rows to record for a signed-in student. Unknown question ids are ignored, never trusted.
 */
export function markMcqs(req: any, content: Content, userId: string | null): { status: number; body: unknown; rows: Record<string, unknown>[] } {
  const paperId = typeof req?.paperId === "string" && /^[A-Za-z0-9._-]{1,80}$/.test(req.paperId) ? req.paperId : null;
  const answers = req?.answers && typeof req.answers === "object" ? req.answers : null;
  if (!paperId || !answers) return { status: 400, body: { error: "BAD_REQUEST" }, rows: [] };
  const results: { questionId: string; correct: boolean }[] = [];
  for (const [id, chosen] of Object.entries(answers).slice(0, 100)) {
    const mcq = content.mcqs.find((m: any) => m.id === id);
    if (!mcq || !Number.isInteger(chosen)) continue;
    results.push({ questionId: id, correct: chosen === mcq.correctIndex });
  }
  const rows = userId ? results.map((r) => ({
    user_id: userId, paper_id: paperId, question_id: r.questionId, awarded: r.correct ? 1 : 0, max_marks: 1,
    status: "CHECKED", model: "answer-key", prompt_version: "-",
  })) : [];
  return { status: 200, body: { result: { results } }, rows };
}

/** Every MCQ in a bundle: the bank, module quizzes, the readiness quiz and papers. */
export function allMcqs(content: Content): any[] {
  const c = content as any;
  return [...(c.mcqs ?? []), ...(c.quiz ?? []), ...(c.readiness ?? []), ...((c.papers ?? []).flatMap((p: any) => p.mcqs ?? []))];
}

const STOP = new Set("the a an of to in is and or for what how why do does i me my it this that with on by from be are can you please explain".split(" "));
const words = (t: string) => (t.toLowerCase().match(/[a-z0-9√²³]+/g) ?? []).filter((w) => w.length > 1 && !STOP.has(w));

/**
 * The notes a tutor question needs: the topic being continued and the current module's notes first, then the best
 * keyword matches, at most [limit]. Small bundles are sent whole.
 */
export function relevantNotes(notes: any[], question: string, ctx: { lastNoteId?: string; moduleId?: string } = {}, limit = 6): any[] {
  if (notes.length <= limit) return notes;
  const q = new Set(words(question));
  const score = (n: any) => {
    let s = 0;
    for (const k of n.keywords ?? []) { const kw = words(k); if (kw.length && kw.every((w) => q.has(w))) s += 3; }
    for (const w of words(n.title)) if (q.has(w)) s += 2;
    if (n.id === ctx.lastNoteId) s += 10;
    if (ctx.moduleId && (n as any).moduleId === ctx.moduleId) s += 4;
    return s;
  };
  return notes.map((n) => [score(n), n] as const).filter(([s]) => s > 0).sort((a, b) => b[0] - a[0]).slice(0, limit).map(([, n]) => n);
}

/**
 * Ask Ustaad. A photo is read first (a separate, cheap call), then the question is looked up among the textbook's
 * checked solutions, so a book question is taught from its checked working. Videos are attached when available.
 */
async function ustaad(task: Extract<Task, { task: "ustaad" }>, content: Content, fetchFn: Fetch, apiKey: string, extras: Extras): Promise<Outcome> {
  if (task.image && (extras.photoQuotaLeft ?? 1) <= 0) return { status: 429, body: { error: "PHOTO_QUOTA_LIMIT" } };
  let usage: Usage | undefined;
  const add = (u: Usage) => {
    usage = usage ? { ...u, task: usage.task, inputTokens: usage.inputTokens + u.inputTokens, outputTokens: usage.outputTokens + u.outputTokens, attempts: usage.attempts + u.attempts } : u;
  };
  if (task.examActive) {
    const { result, usage: u } = await runTask(task, fetchFn, apiKey);
    return { status: 200, body: { result, model: u.model, promptVersion: u.promptVersion }, usage: u };
  }
  if (task.image) {
    const read = await runTask({ task: "read_question", course: task.course, image: task.image }, fetchFn, apiKey);
    add({ ...read.usage, task: "ustaad_photo" });
    const r = read.result as { readable: boolean; question: string };
    if (!r.readable) {
      const result: UstaadReply = { kind: "UNREADABLE", questionAsRead: r.question, topic: "", steps: [], finalAnswer: "", concept: "", examTip: "",
        checkQuestion: "", suggestions: ["I'll retake the photo", "Let me type the question"], videoQuery: "", book: null, videos: [],
        reply: `I couldn't read this photo clearly${r.question ? ` (${r.question})` : ""}. Could you retake it in good light, holding the phone straight over the question?` };
      return { status: 200, body: { result, model: read.usage.model, promptVersion: read.usage.promptVersion }, usage };
    }
    task.questionAsRead = r.question;
  }
  const subjectOk = task.sections.length > 0;
  const match = subjectOk ? bookMatch(content, task.questionAsRead ?? task.message) : null;
  if (match) task.book = match;
  if (!task.notes.length && subjectOk && task.questionAsRead) task.notes = relevantNotes(content.notes, task.questionAsRead, {}, 4);
  const run = await runTask(task, fetchFn, apiKey);
  add({ ...run.usage, task: usage?.task ?? "ustaad" });
  const result = run.result as UstaadReply;
  // Unsafe messages: a fixed reply written by the server (never the model's), no videos, figures or PDFs, and a flag.
  if (result.safety && result.safety !== "STUDY" && result.safety !== "OFF_TOPIC") {
    Object.assign(result, { kind: "OFF_TOPIC", reply: SAFETY_REPLIES[result.safety], steps: [], finalAnswer: "", concept: "", examTip: "", checkQuestion: "",
      suggestions: result.safety === "SELF_HARM" ? ["I'll talk to someone", "Help me with my studies"] : ["Help me with my studies"],
      videoQuery: "", figure: null, action: null, questionAsRead: "" });
    await extras.flag?.(result.safety, task.questionAsRead ?? task.message);
    return { status: 200, body: { result, model: run.usage.model, promptVersion: run.usage.promptVersion }, usage: { ...usage!, ok: run.usage.ok } };
  }
  if (match && (result.kind === "SOLUTION" || result.kind === "CHAT")) {
    result.book = { source: match.source, section: match.section, moduleId: match.moduleId, index: match.index } as any;
    if (!result.topic) result.topic = match.section;
  }
  if (extras.videos && result.videoQuery && result.kind !== "OFF_TOPIC" && result.kind !== "LOCKED") {
    result.videos = await extras.videos(result.videoQuery).catch(() => []);
  }
  return { status: 200, body: { result, model: run.usage.model, promptVersion: run.usage.promptVersion }, usage: { ...usage!, ok: run.usage.ok } };
}
