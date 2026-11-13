import type { Bundle } from "../types";
import type { Edit } from "../pages/Content";
import { Check, Lines, Text } from "../ui";
import { ListDetail, newId } from "./ListDetail";

const STATUSES = ["SAMPLE_UNREVIEWED", "DRAFT", "ACADEMIC_REVIEW", "APPROVED", "PUBLISHED"];

export function UnitsEditor({ bundle, edit }: { bundle: Bundle; edit: Edit }) {
  const s = bundle.subject;
  return (
    <>
      <div className="card">
        <div className="grid">
          <Text label="Subject name" value={s.name} onChange={(v) => edit((b) => { b.subject.name = v; })} />
          <Text label="Book edition" value={s.editionLabel} onChange={(v) => edit((b) => { b.subject.editionLabel = v; })} />
        </div>
        <Check label="Edition checked against the official book" value={s.editionVerified} onChange={(v) => edit((b) => { b.subject.editionVerified = v; })} />
      </div>
      <ListDetail items={s.chapters} label={(c) => `Unit ${c.number} · ${c.title}`}>
        {(c, ci) => (
          <>
            <Text label="Unit title" value={c.title} onChange={(v) => edit((b) => { b.subject.chapters[ci].title = v; })} />
            <label className="field"><span>Status</span>
              <select value={c.status} onChange={(e) => edit((b) => { b.subject.chapters[ci].status = e.target.value; })}>
                {STATUSES.map((x) => <option key={x}>{x}</option>)}
              </select>
            </label>
            <h3>Modules ({c.modules.length})</h3>
            {c.modules.map((m, mi) => (
              <div key={m.id} className="card">
                <div className="row spread"><strong>Module {mi + 1}</strong><span className="muted small">{m.id}</span></div>
                <Text label="Title" value={m.title} onChange={(v) => edit((b) => { b.subject.chapters[ci].modules[mi].title = v; })} />
                <Text label="YouTube link (a teacher's video or playlist)" value={m.video?.url} placeholder="https://www.youtube.com/watch?v=…"
                  onChange={(v) => edit((b) => { const mod = b.subject.chapters[ci].modules[mi]; mod.video = v.trim() ? { ...(mod.video ?? {}), url: v.trim() } : null; })} />
                <Text label="Book reference (pages, exercise)" value={m.bookReference?.text}
                  onChange={(v) => edit((b) => { const mod = b.subject.chapters[ci].modules[mi]; mod.bookReference = { text: v, verified: mod.bookReference?.verified ?? false }; })} />
                <Check label="Book reference checked against the official edition" value={m.bookReference?.verified}
                  onChange={(v) => edit((b) => { const mod = b.subject.chapters[ci].modules[mi]; mod.bookReference = { text: mod.bookReference?.text ?? "", verified: v }; })} />
                <Lines label="Reading tasks" value={m.readingTasks} onChange={(v) => edit((b) => { b.subject.chapters[ci].modules[mi].readingTasks = v; })} />
                <p className="muted small">{m.writtenQuestions.length} written practice questions (edit in Full JSON).</p>
                <button className="danger" onClick={() => confirm(`Delete module "${m.title}"?`) && edit((b) => { b.subject.chapters[ci].modules.splice(mi, 1); })}>Delete module</button>
              </div>
            ))}
            <button onClick={() => edit((b) => {
              const ch = b.subject.chapters[ci];
              const all = b.subject.chapters.flatMap((x) => x.modules.map((m) => m.id));
              ch.modules.push({ id: newId(`m-${ch.number}-`, all), position: ch.modules.length + 1, title: "New module", status: "DRAFT",
                video: null, bookReference: null, readingTasks: [], writtenQuestions: [] });
            })}>+ Add module</button>
          </>
        )}
      </ListDetail>
    </>
  );
}
