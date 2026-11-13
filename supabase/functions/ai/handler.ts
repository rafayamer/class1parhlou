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
    // Only notes of the learner's own subjects are ever shown to the model.
    const notes = subjects.includes(content.subjectId) ? content.notes : [];
    const last = notes.find((n) => n.id === req.lastNoteId);
    const followUp = question.trim() === "" && last ? `Continue with the topic "${last.title}".` : question;
    if (followUp.trim() === "") return bad("EMPTY_QUESTION");
    return {
      task: "tutor", course: content.course, question: followUp, style, notes,
      examActive: req.examActive === true,
      pendingCheck: req.awaitingCheck === true && last ? last.checkQuestion : null,
    };
  }
  if (req.task === "mcq_feedback") {
    const mcq = content.mcqs.find((m) => m.id === req.questionId);
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
