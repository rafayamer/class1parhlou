// The content bundle (format 1) read by the app and the AI gateway. Mirrors android ContentBundle.kt.
export interface RubricCriterion { id: string; description: string; marks: number; why: string; how: string; accept: string[][] }
export interface WrittenQuestion {
  id: string; text: string; marks: number; modelAnswer: string[]; followUp: string; noteId?: string | null;
  section?: number; number?: number; matching?: string; rubric: RubricCriterion[];
}
export interface Mcq { id: string; stem: string; options: string[]; correctIndex: number; rationales: string[]; status?: string; noteId?: string | null }
export interface Note {
  id: string; subjectId: string; moduleId: string; title: string; keywords: string[]; answer: string; explanation: string;
  simple: string; examples: string[]; steps: string[]; checkQuestion: string; checkAnswer: string; checkAccept: string[][];
}
export interface Module {
  id: string; position: number; title: string; status: string; video?: { url: string; title?: string } | null;
  bookReference?: { text: string; verified: boolean } | null; readingTasks: string[]; writtenQuestions: WrittenQuestion[];
}
export interface Chapter { id: string; number: number; title: string; status: string; modules: Module[] }
export interface Section { type: string; count: number; attempt: number; marksEach: number; minutes?: number | null }
export interface Blueprint { id: string; title: string; durationMinutes: number; totalMarks: number; official: boolean; chapterId?: string | null; sections: Section[] }
export interface Paper { blueprintId: string; mcqs: Mcq[]; written: WrittenQuestion[] }
export interface Bundle {
  format: 1;
  subjectId: string;
  course?: Record<string, unknown>;
  subject: { id: string; name: string; grade: number; editionLabel: string; editionVerified: boolean; chapters: Chapter[] };
  notes: Note[];
  quiz: Mcq[];
  readiness: Mcq[];
  blueprints: Blueprint[];
  papers: Paper[];
  written: WrittenQuestion[];
  mcqs: Mcq[];
}
export interface ContentVersion { subject_id: string; version: number; status: string; created_at: string; bundle?: Bundle }
