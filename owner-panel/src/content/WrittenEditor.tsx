import type { Bundle, WrittenQuestion } from "../types";
import type { Edit } from "../pages/Content";
import { Area, Field, Lines, Num, Patterns, Text } from "../ui";
import { ListDetail, newId } from "./ListDetail";

/** Written questions in the bank, each with a model answer and marking points. */
export function WrittenEditor({ bundle, edit }: { bundle: Bundle; edit: Edit }) {
  return (
    <ListDetail items={bundle.written} label={(q) => `${q.id} · ${q.text}`} addLabel="Add written question"
      onAdd={() => edit((b) => { b.written.push({ id: newId("w", b.written.map((w) => w.id)), text: "", marks: 2, modelAnswer: [], followUp: "", noteId: null, rubric: [] }); })}
      onDelete={(i) => edit((b) => { b.written.splice(i, 1); })}>
      {(q, i) => <WrittenForm q={q} notes={bundle.notes.map((n) => [n.id, n.title])} set={(change) => edit((b) => change(b.written[i]))} />}
    </ListDetail>
  );
}

export function WrittenForm({ q, notes, set }: { q: WrittenQuestion; notes: string[][]; set: (change: (x: WrittenQuestion) => void) => void }) {
  const sum = q.rubric.reduce((s, c) => s + (Number(c.marks) || 0), 0);
  return (
    <>
      <p className="muted small">Id: {q.id}</p>
      <Area label="Question" value={q.text} onChange={(v) => set((x) => { x.text = v; })} />
      <div className="grid">
        <Num label="Marks" step={0.5} value={q.marks} onChange={(v) => set((x) => { x.marks = v; })} />
        <Field label="Tutor note">
          <select value={q.noteId ?? ""} onChange={(e) => set((x) => { x.noteId = e.target.value || null; })}>
            <option value="">None</option>
            {notes.map(([id, title]) => <option key={id} value={id}>{title}</option>)}
          </select>
        </Field>
      </div>
      <Lines label="Model answer (book method)" value={q.modelAnswer} onChange={(v) => set((x) => { x.modelAnswer = v; })} />
      <Text label="Follow-up practice question" value={q.followUp} onChange={(v) => set((x) => { x.followUp = v; })} />
      <h3>Marking points <span className={sum === q.marks ? "ok" : "error"}>({sum} of {q.marks} marks)</span></h3>
      {q.rubric.map((c, ci) => (
        <div key={ci} className="card">
          <div className="grid">
            <Text label="Point id" value={c.id} onChange={(v) => set((x) => { x.rubric[ci].id = v; })} />
            <Num label="Marks" step={0.5} value={c.marks} onChange={(v) => set((x) => { x.rubric[ci].marks = v; })} />
          </div>
          <Text label="What earns the mark" value={c.description} onChange={(v) => set((x) => { x.rubric[ci].description = v; })} />
          <Text label="Why it matters (shown when missed)" value={c.why} onChange={(v) => set((x) => { x.rubric[ci].why = v; })} />
          <Text label="How to fix it (shown when missed)" value={c.how} onChange={(v) => set((x) => { x.rubric[ci].how = v; })} />
          <Patterns label="Accepted answers" value={c.accept} onChange={(v) => set((x) => { x.rubric[ci].accept = v; })} />
          <button className="danger" onClick={() => set((x) => { x.rubric.splice(ci, 1); })}>Remove point</button>
        </div>
      ))}
      <button onClick={() => set((x) => { x.rubric.push({ id: String.fromCharCode(97 + x.rubric.length), description: "", marks: 1, why: "", how: "", accept: [[""]] }); })}>+ Marking point</button>
    </>
  );
}
