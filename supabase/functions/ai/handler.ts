// Maps an app request to a Luna task. The app sends ids and the student's own text only;
// questions, answer keys and rubrics are looked up here, on the server.
import { runTask, type Fetch, type Task, type Usage } from "./core.ts";

export interface Content { course: any; subjectId: string; notes: any[]; written: any[]; mcqs: any[] }
export interface Limits { maxImages: number; maxImageChars: number; maxTextChars: number }
export const DEFAULT_LIMITS: Limits = { maxImages: 5, maxImageChars: 2_800_000, maxTextChars: 6_000 };

export type Outcome = { status: number; body: unknown; usage?: Usage };

const STYLES = ["NORMAL", "SIMPLER", "ANOTHER_EXAMPLE", "STEPS", "QUIZ_ME"];

export function toTask(req: any, content: Content, limits: Limits = DEFAULT_LIMITS): Task | { error: string; status: number } {
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
    return { task: "mark_written", course: content.course, question, answer: String(req.answer ?? "").slice(0, limits.maxTextChars), images };
  }
  return bad("UNKNOWN_TASK");
}

export async function handle(req: any, content: Content, fetchFn: Fetch, apiKey: string, quotaLeft: number): Promise<Outcome> {
  const task = toTask(req, content);
  if ("error" in task) return { status: task.status, body: { error: task.error } };
  if (quotaLeft <= 0) return { status: 429, body: { error: "QUOTA_LIMIT" } };
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
