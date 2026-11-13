import type { Bundle } from "../types";
import type { Edit } from "../pages/Content";
import { Check, Field, Num, Text } from "../ui";
import { ListDetail, newId } from "./ListDetail";

/** Paper patterns: sections, counts and marks. The questions of each paper are edited in Full JSON. */
export function BlueprintEditor({ bundle, edit }: { bundle: Bundle; edit: Edit }) {
  return (
    <ListDetail items={bundle.blueprints} label={(b) => b.title || b.id} addLabel="Add paper pattern"
      onAdd={() => edit((b) => {
        b.blueprints.push({ id: newId("paper-", b.blueprints.map((x) => x.id)), title: "New paper", durationMinutes: 90, totalMarks: 10, official: false, chapterId: null,
          sections: [{ type: "MCQ", count: 10, attempt: 10, marksEach: 1, minutes: null }] });
      })}
      onDelete={(i) => edit((b) => { b.blueprints.splice(i, 1); })}>
      {(bp, i) => {
        const set = (change: (x: typeof bp) => void) => edit((b) => change(b.blueprints[i]));
        const computed = bp.sections.reduce((s, x) => s + x.attempt * x.marksEach, 0);
        const paper = bundle.papers.find((p) => p.blueprintId === bp.id);
        return (
          <>
            <p className="muted small">Id: {bp.id}</p>
            <Text label="Title" value={bp.title} onChange={(v) => set((x) => { x.title = v; })} />
            <div className="grid">
              <Num label="Minutes" value={bp.durationMinutes} onChange={(v) => set((x) => { x.durationMinutes = v; })} />
              <Num label="Total marks" value={bp.totalMarks} onChange={(v) => set((x) => { x.totalMarks = v; })} />
              <Field label="Unit (empty = several units)">
                <select value={bp.chapterId ?? ""} onChange={(e) => set((x) => { x.chapterId = e.target.value || null; })}>
                  <option value="">Several units</option>
                  {bundle.subject.chapters.map((c) => <option key={c.id} value={c.id}>Unit {c.number} · {c.title}</option>)}
                </select>
              </Field>
            </div>
            <Check label="Official board pattern (only when confirmed from the board's own scheme)" value={bp.official} onChange={(v) => set((x) => { x.official = v; })} />
            <h3>Sections <span className={computed === bp.totalMarks ? "ok" : "error"}>(add up to {computed} of {bp.totalMarks})</span></h3>
            <div className="scroll">
              <table>
                <thead><tr><th>Type</th><th>Questions</th><th>Attempt</th><th>Marks each</th><th /></tr></thead>
                <tbody>
                  {bp.sections.map((s, si) => (
                    <tr key={si}>
                      <td><input aria-label="Section type" value={s.type} onChange={(e) => set((x) => { x.sections[si].type = e.target.value; })} /></td>
                      <td><input aria-label="Questions" type="number" value={s.count} onChange={(e) => set((x) => { x.sections[si].count = Number(e.target.value); })} /></td>
                      <td><input aria-label="Attempt" type="number" value={s.attempt} onChange={(e) => set((x) => { x.sections[si].attempt = Number(e.target.value); })} /></td>
                      <td><input aria-label="Marks each" type="number" step={0.5} value={s.marksEach} onChange={(e) => set((x) => { x.sections[si].marksEach = Number(e.target.value); })} /></td>
                      <td><button aria-label="Remove section" disabled={bp.sections.length <= 1} onClick={() => set((x) => { x.sections.splice(si, 1); })}>×</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button onClick={() => set((x) => { x.sections.push({ type: "Short questions", count: 1, attempt: 1, marksEach: 2, minutes: null }); })}>+ Section</button>
            <p className="muted small">
              {paper ? `This paper has ${paper.mcqs.length} MCQs and ${paper.written.length} written questions (edit them in Full JSON).`
                : "No questions are published for this pattern yet; students see it as not available."}
            </p>
          </>
        );
      }}
    </ListDetail>
  );
}
