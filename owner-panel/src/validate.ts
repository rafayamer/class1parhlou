import type { Blueprint, Bundle, Mcq, Paper, WrittenQuestion } from "./types";

/** A problem that blocks publishing, with where it is. */
export interface Problem { where: string; message: string }

const near = (a: number, b: number) => Math.abs(a - b) < 1e-9;
const fmt = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2));

export function mcqProblems(q: Mcq, where: string): Problem[] {
  const out: Problem[] = [];
  if (!q.id?.trim()) out.push({ where, message: "needs an id" });
  if (!q.stem?.trim()) out.push({ where, message: "question text is empty" });
  if (!Array.isArray(q.options) || q.options.length < 2) out.push({ where, message: "needs at least 2 options" });
  else {
    if (q.options.some((o) => !o?.trim())) out.push({ where, message: "an option is empty" });
    if (!Number.isInteger(q.correctIndex) || q.correctIndex < 0 || q.correctIndex >= q.options.length)
      out.push({ where, message: "exactly one option must be marked correct" });
    if (!Array.isArray(q.rationales) || q.rationales.length !== q.options.length)
      out.push({ where, message: "every option needs a reason (why it is right or wrong)" });
  }
  return out;
}

export function writtenProblems(q: WrittenQuestion, where: string): Problem[] {
  const out: Problem[] = [];
  if (!q.id?.trim()) out.push({ where, message: "needs an id" });
  if (!q.text?.trim()) out.push({ where, message: "question text is empty" });
  if (!(q.marks > 0)) out.push({ where, message: "marks must be more than 0" });
  if (!q.modelAnswer?.length || q.modelAnswer.every((l) => !l.trim())) out.push({ where, message: "needs a model answer" });
  if (!q.rubric?.length) out.push({ where, message: "needs at least one marking point" });
  else {
    const sum = q.rubric.reduce((s, c) => s + (Number(c.marks) || 0), 0);
    if (!near(sum, q.marks)) out.push({ where, message: `marking points add up to ${fmt(sum)} but the question is worth ${fmt(q.marks)}` });
    const ids = new Set<string>();
    q.rubric.forEach((c, i) => {
      const at = `${where}, point ${i + 1}`;
      if (!c.id?.trim() || ids.has(c.id)) out.push({ where: at, message: "needs its own id" });
      ids.add(c.id);
      if (!c.description?.trim()) out.push({ where: at, message: "description is empty" });
      if (!(c.marks > 0)) out.push({ where: at, message: "marks must be more than 0" });
      if (!c.accept?.length || c.accept.some((g) => !g.length || g.some((p) => !p.trim())))
        out.push({ where: at, message: "needs at least one accepted answer pattern, with no empty patterns" });
      if (!c.why?.trim() || !c.how?.trim()) out.push({ where: at, message: "needs a 'why' and a 'how' for the student" });
    });
  }
  return out;
}

/** Mirrors BlueprintValidator.kt and public.validate_blueprint: a paper's marks must reconcile. */
export function blueprintProblems(b: Blueprint, where: string): Problem[] {
  const out: Problem[] = [];
  if (!b.sections?.length) return [{ where, message: "needs at least one section" }];
  let computed = 0;
  b.sections.forEach((s, i) => {
    const at = `${where}, section ${i + 1}`;
    if (!(s.count > 0)) out.push({ where: at, message: "count must be positive" });
    if (!(s.attempt > 0)) out.push({ where: at, message: "attempt must be positive" });
    else if (s.attempt > s.count) out.push({ where: at, message: "attempt exceeds count" });
    if (!(s.marksEach > 0)) out.push({ where: at, message: "marks each must be positive" });
    computed += s.attempt * s.marksEach;
  });
  if (!near(computed, b.totalMarks)) out.push({ where, message: `sections add up to ${fmt(computed)} marks but total is ${fmt(b.totalMarks)}` });
  if (!(b.durationMinutes > 0)) out.push({ where, message: "duration must be positive" });
  return out;
}

/** A paper must have exactly the questions its blueprint asks for, each worth the section's marks. */
export function paperProblems(p: Paper, b: Blueprint | undefined): Problem[] {
  const where = `Paper ${p.blueprintId}`;
  if (!b) return [{ where, message: "has no blueprint with this id" }];
  const out: Problem[] = [];
  const mcqSection = b.sections[0];
  if (mcqSection && p.mcqs.length !== mcqSection.count)
    out.push({ where, message: `has ${p.mcqs.length} MCQs but section 1 needs ${mcqSection.count}` });
  b.sections.slice(1).forEach((s, i) => {
    const qs = p.written.filter((w) => w.section === i + 1);
    if (qs.length !== s.count) out.push({ where, message: `section ${i + 2} has ${qs.length} questions but needs ${s.count}` });
    qs.filter((q) => !near(q.marks, s.marksEach)).forEach((q) =>
      out.push({ where: `${where}, ${q.id}`, message: `is worth ${fmt(q.marks)} but section ${i + 2} questions are worth ${fmt(s.marksEach)}` }));
  });
  p.mcqs.forEach((q, i) => out.push(...mcqProblems(q, `${where}, MCQ ${i + 1}`)));
  p.written.forEach((q) => out.push(...writtenProblems(q, `${where}, ${q.id}`)));
  return out;
}

const YOUTUBE = /^https:\/\/(www\.)?(youtube\.com\/(watch\?v=|playlist\?list=|embed\/)|youtu\.be\/)[\w-]+/;

/** Everything that must be right before a bundle is published. Empty = ready. */
export function bundleProblems(b: Bundle, subjectId: string): Problem[] {
  const out: Problem[] = [];
  if (b.format !== 1) out.push({ where: "Bundle", message: "format must be 1" });
  if (b.subjectId !== subjectId) out.push({ where: "Bundle", message: `subjectId must be ${subjectId}` });
  if (!b.subject || !Array.isArray(b.subject.chapters)) return [...out, { where: "Bundle", message: "missing subject and units" }];
  for (const key of ["notes", "quiz", "readiness", "blueprints", "papers", "written", "mcqs"] as const) {
    if (!Array.isArray(b[key])) out.push({ where: "Bundle", message: `missing ${key}` });
  }
  if (out.length) return out;

  const noteIds = new Set(b.notes.map((n) => n.id));
  const moduleIds = new Set<string>();
  const seen = (ids: Set<string>, id: string, where: string) => {
    if (ids.has(id)) out.push({ where, message: `id "${id}" is used twice` });
    ids.add(id);
  };
  const unitIds = new Set<string>();
  b.subject.chapters.forEach((c) => {
    const at = `Unit ${c.number}`;
    seen(unitIds, c.id, at);
    if (!c.title?.trim()) out.push({ where: at, message: "title is empty" });
    c.modules.forEach((m, i) => {
      const mat = `${at}, module ${i + 1}`;
      seen(moduleIds, m.id, mat);
      if (!m.title?.trim()) out.push({ where: mat, message: "title is empty" });
      if (m.video?.url && !YOUTUBE.test(m.video.url)) out.push({ where: mat, message: "video link must be a YouTube https link" });
      m.writtenQuestions.forEach((q) => out.push(...writtenProblems(q, `${mat}, ${q.id}`)));
    });
  });
  const noteSeen = new Set<string>();
  b.notes.forEach((n) => {
    seen(noteSeen, n.id, `Note ${n.title || n.id}`);
    if (!moduleIds.has(n.moduleId)) out.push({ where: `Note ${n.id}`, message: `belongs to module "${n.moduleId}", which doesn't exist` });
    if (!n.title?.trim() || !n.answer?.trim()) out.push({ where: `Note ${n.id}`, message: "needs a title and an answer" });
  });
  const questionIds = new Set<string>();
  const checkNote = (id: string | null | undefined, where: string) => {
    if (id && !noteIds.has(id)) out.push({ where, message: `refers to tutor note "${id}", which doesn't exist` });
  };
  ([["MCQ bank", b.mcqs], ["Module quiz", b.quiz], ["Readiness quiz", b.readiness]] as const).forEach(([name, list]) =>
    list.forEach((q, i) => {
      const at = `${name} ${i + 1}`;
      out.push(...mcqProblems(q, at));
      checkNote(q.noteId, at);
    }));
  b.mcqs.forEach((q) => seen(questionIds, q.id, `MCQ ${q.id}`));
  b.written.forEach((q) => {
    seen(questionIds, q.id, `Written ${q.id}`);
    out.push(...writtenProblems(q, `Written ${q.id}`));
    checkNote(q.noteId, `Written ${q.id}`);
  });
  const bpIds = new Set<string>();
  b.blueprints.forEach((bp) => {
    seen(bpIds, bp.id, `Blueprint ${bp.title || bp.id}`);
    out.push(...blueprintProblems(bp, `Blueprint ${bp.title || bp.id}`));
    if (bp.chapterId && !unitIds.has(bp.chapterId)) out.push({ where: `Blueprint ${bp.id}`, message: `unit "${bp.chapterId}" doesn't exist` });
  });
  b.papers.forEach((p) => out.push(...paperProblems(p, b.blueprints.find((bp) => bp.id === p.blueprintId))));
  return out;
}
