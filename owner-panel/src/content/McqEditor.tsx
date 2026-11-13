import { useState } from "react";
import type { Bundle, Mcq } from "../types";
import type { Edit } from "../pages/Content";
import { Area, Field } from "../ui";
import { ListDetail, newId } from "./ListDetail";

const LISTS = [["mcqs", "Question bank"], ["quiz", "Module quiz"], ["readiness", "Readiness quiz"]] as const;
type ListKey = (typeof LISTS)[number][0];

export function McqEditor({ bundle, edit }: { bundle: Bundle; edit: Edit }) {
  const [list, setList] = useState<ListKey>("mcqs");
  const items = bundle[list];
  const allIds = [...bundle.mcqs, ...bundle.quiz, ...bundle.readiness].map((q) => q.id);
  return (
    <>
      <div className="tabs">
        {LISTS.map(([k, label]) => <button key={k} className={k === list ? "on" : ""} onClick={() => setList(k)}>{label} ({bundle[k].length})</button>)}
      </div>
      <ListDetail key={list} items={items} label={(q, i) => `${i + 1}. ${q.stem}`} addLabel="Add MCQ"
        onAdd={() => edit((b) => { b[list].push({ id: newId("q", allIds), stem: "", options: ["", "", "", ""], correctIndex: 0, rationales: ["", "", "", ""], status: "DRAFT", noteId: null }); })}
        onDelete={(i) => edit((b) => { b[list].splice(i, 1); })}>
        {(q, i) => <McqForm q={q} notes={bundle.notes.map((n) => [n.id, n.title])} set={(change) => edit((b) => change(b[list][i]))} />}
      </ListDetail>
    </>
  );
}

export function McqForm({ q, notes, set }: { q: Mcq; notes: string[][]; set: (change: (x: Mcq) => void) => void }) {
  return (
    <>
      <p className="muted small">Id: {q.id}</p>
      <Area label="Question" value={q.stem} onChange={(v) => set((x) => { x.stem = v; })} />
      <Field label="Options — tick the correct one, and say why each option is right or wrong">
        <div>
          {q.options.map((o, oi) => (
            <div className="option" key={oi}>
              <input type="radio" name={`correct-${q.id}`} aria-label={`Option ${oi + 1} is correct`} checked={q.correctIndex === oi}
                onChange={() => set((x) => { x.correctIndex = oi; })} />
              <input aria-label={`Option ${oi + 1}`} value={o} onChange={(e) => set((x) => { x.options[oi] = e.target.value; })} />
              <input className="why" aria-label={`Why option ${oi + 1}`} placeholder="Why" value={q.rationales[oi] ?? ""}
                onChange={(e) => set((x) => { x.rationales[oi] = e.target.value; })} />
              <button aria-label={`Remove option ${oi + 1}`} disabled={q.options.length <= 2}
                onClick={() => set((x) => { x.options.splice(oi, 1); x.rationales.splice(oi, 1); if (x.correctIndex >= x.options.length) x.correctIndex = 0; })}>×</button>
            </div>
          ))}
          <button onClick={() => set((x) => { x.options.push(""); x.rationales.push(""); })}>+ Option</button>
        </div>
      </Field>
      <Field label="Tutor note that explains it">
        <select value={q.noteId ?? ""} onChange={(e) => set((x) => { x.noteId = e.target.value || null; })}>
          <option value="">None</option>
          {notes.map(([id, title]) => <option key={id} value={id}>{title}</option>)}
        </select>
      </Field>
    </>
  );
}
