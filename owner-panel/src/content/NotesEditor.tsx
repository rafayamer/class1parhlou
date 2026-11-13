import type { Bundle } from "../types";
import type { Edit } from "../pages/Content";
import { Area, Field, Lines, Patterns, Text } from "../ui";
import { ListDetail, newId } from "./ListDetail";

/** The tutor answers only from these notes. */
export function NotesEditor({ bundle, edit }: { bundle: Bundle; edit: Edit }) {
  const modules = bundle.subject.chapters.flatMap((c) => c.modules.map((m) => ({ id: m.id, label: `Unit ${c.number} · ${m.title}` })));
  return (
    <ListDetail items={bundle.notes} label={(n) => n.title || n.id}
      addLabel="Add note"
      onAdd={() => edit((b) => {
        b.notes.push({ id: newId("n-", b.notes.map((n) => n.id)), subjectId: b.subjectId, moduleId: modules[0]?.id ?? "", title: "New note",
          keywords: [], answer: "", explanation: "", simple: "", examples: [], steps: [], checkQuestion: "", checkAnswer: "", checkAccept: [] });
      })}
      onDelete={(i) => edit((b) => { b.notes.splice(i, 1); })}>
      {(n, i) => {
        const set = (change: (x: typeof n) => void) => edit((b) => change(b.notes[i]));
        return (
          <>
            <p className="muted small">Id: {n.id}</p>
            <Text label="Title" value={n.title} onChange={(v) => set((x) => { x.title = v; })} />
            <Field label="Module">
              <select value={n.moduleId} onChange={(e) => set((x) => { x.moduleId = e.target.value; })}>
                {modules.map((m) => <option key={m.id} value={m.id}>{m.label}</option>)}
              </select>
            </Field>
            <Lines label="Keywords the student might use" value={n.keywords} onChange={(v) => set((x) => { x.keywords = v; })} />
            <Area label="Short answer" value={n.answer} onChange={(v) => set((x) => { x.answer = v; })} />
            <Area label="Explanation" value={n.explanation} onChange={(v) => set((x) => { x.explanation = v; })} />
            <Area label="Simpler explanation" value={n.simple} onChange={(v) => set((x) => { x.simple = v; })} />
            <Lines label="Examples" value={n.examples} onChange={(v) => set((x) => { x.examples = v; })} />
            <Lines label="Steps" value={n.steps} onChange={(v) => set((x) => { x.steps = v; })} />
            <Text label="Check question" value={n.checkQuestion} onChange={(v) => set((x) => { x.checkQuestion = v; })} />
            <Text label="Check answer" value={n.checkAnswer} onChange={(v) => set((x) => { x.checkAnswer = v; })} />
            <Patterns label="Accepted check answers" value={n.checkAccept} onChange={(v) => set((x) => { x.checkAccept = v; })} />
          </>
        );
      }}
    </ListDetail>
  );
}
