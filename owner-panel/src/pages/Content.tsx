import { useEffect, useMemo, useRef, useState } from "react";
import { db, explain } from "../supabase";
import type { Bundle, ContentVersion } from "../types";
import { bundleProblems } from "../validate";
import { when } from "../ui";
import { UnitsEditor } from "../content/UnitsEditor";
import { NotesEditor } from "../content/NotesEditor";
import { McqEditor } from "../content/McqEditor";
import { WrittenEditor } from "../content/WrittenEditor";
import { BlueprintEditor } from "../content/BlueprintEditor";

const TABS = [
  ["units", "Units & modules"],
  ["notes", "Tutor notes"],
  ["mcqs", "MCQs"],
  ["written", "Written questions"],
  ["papers", "Paper patterns"],
  ["json", "Full JSON"],
  ["history", "History"],
] as const;
type Tab = (typeof TABS)[number][0];

/** Applies an edit to a copy of the bundle. */
export type Edit = (change: (b: Bundle) => void) => void;

export function Content() {
  const [versions, setVersions] = useState<ContentVersion[]>([]);
  const [drafts, setDrafts] = useState<{ subject_id: string; updated_at: string }[]>([]);
  const [subject, setSubject] = useState("");
  const [bundle, setBundle] = useState<Bundle | null>(null);
  const [baseline, setBaseline] = useState("");
  const [source, setSource] = useState("");
  const [tab, setTab] = useState<Tab>("units");
  const [status, setStatus] = useState("");
  const [busy, setBusy] = useState(false);

  const loadLists = async () => {
    const [v, d] = await Promise.all([
      db.from("app_content").select("subject_id,version,status,created_at").order("version", { ascending: false }),
      db.from("content_drafts").select("subject_id,updated_at"),
    ]);
    if (v.error) return setStatus(explain(v.error));
    setVersions(v.data as ContentVersion[]);
    setDrafts(d.data ?? []);
    const subjects = [...new Set([...(v.data ?? []), ...(d.data ?? [])].map((r) => r.subject_id))];
    if (!subject && subjects.length) setSubject(subjects[0]);
  };
  useEffect(() => { loadLists(); }, []);

  // Open the saved draft if there is one, else the published version. Switching subjects quickly starts several loads;
  // only the one for the subject chosen last may fill the editor (a slower, older load is ignored).
  const opening = useRef("");
  // The subject chosen right now: a load (or a reload after publishing) for any other subject is dropped.
  const chosen = useRef("");
  chosen.current = subject;
  const open = async (s: string) => {
    if (s !== chosen.current) return;
    opening.current = s;
    setStatus(""); setBundle(null); setSource("");
    const draft = await db.from("content_drafts").select("bundle,updated_at").eq("subject_id", s).maybeSingle();
    if (opening.current !== s || chosen.current !== s) return;
    if (draft.data) {
      setSource(`Draft saved ${when(draft.data.updated_at)}`);
      setBundle(draft.data.bundle as Bundle); setBaseline(JSON.stringify(draft.data.bundle));
      return;
    }
    const pub = await db.from("app_content").select("bundle,version").eq("subject_id", s).eq("status", "PUBLISHED").maybeSingle();
    if (opening.current !== s || chosen.current !== s) return;
    if (pub.error || !pub.data) return setStatus(pub.error ? explain(pub.error) : "No published version for this subject.");
    setSource(`Published version ${pub.data.version}`);
    setBundle(pub.data.bundle as Bundle); setBaseline(JSON.stringify(pub.data.bundle));
  };
  useEffect(() => { if (subject) open(subject); }, [subject]);
  // If a stale load of another subject ever lands, load the chosen subject again.
  useEffect(() => { if (bundle && subject && (bundle as { subjectId?: string }).subjectId !== subject) open(subject); }, [bundle, subject]);

  const edit: Edit = (change) => setBundle((b) => {
    if (!b) return b;
    const next = structuredClone(b);
    change(next);
    return next;
  });
  // A bundle of another subject is never shown or published (it can only be a stale load): treat it as still loading.
  const shown = bundle && (bundle as { subjectId?: string }).subjectId === subject ? bundle : null;
  const problems = useMemo(() => (shown ? bundleProblems(shown, subject) : []), [shown, subject]);
  const dirty = bundle != null && JSON.stringify(bundle) !== baseline;
  // A new subject has only a draft until its first publish, so drafts are listed too.
  const subjects = [...new Set([...versions, ...drafts].map((v) => v.subject_id))];
  const history = versions.filter((v) => v.subject_id === subject);

  const saveDraft = async () => {
    if (!shown) return;
    setBusy(true);
    const { error } = await db.rpc("owner_save_draft", { p_subject: subject, p_bundle: bundle });
    setBusy(false);
    if (error) return setStatus(explain(error));
    setBaseline(JSON.stringify(bundle)); setSource("Draft saved just now"); setStatus("Draft saved. Students don't see it until you publish.");
    loadLists();
  };
  const publish = async () => {
    if (!shown || problems.length) return;
    if (!confirm("Publish this content to every student? Phones pick it up the next time the app opens.")) return;
    setBusy(true);
    const { data, error } = await db.rpc("owner_publish_content", { p_subject: subject, p_bundle: bundle });
    setBusy(false);
    if (error) return setStatus(explain(error));
    setStatus(`Published as version ${data}.`);
    await loadLists(); open(subject);
  };
  const rollback = async (version: number) => {
    if (!confirm(`Publish version ${version} again as a new version? Your unsaved edits here are discarded.`)) return;
    setBusy(true);
    const { data, error } = await db.rpc("owner_rollback_content", { p_subject: subject, p_version: version });
    setBusy(false);
    if (error) return setStatus(explain(error));
    setStatus(`Version ${version} is live again as version ${data}.`);
    await loadLists(); open(subject);
  };

  return (
    <>
      <div className="row spread">
        <div>
          <h1>Content</h1>
          <p className="muted">{shown ? source : "Loading…"}{dirty ? " · unsaved changes" : ""}</p>
        </div>
        <div className="row">
          <label className="field" style={{ minWidth: 200, margin: 0 }}>
            <span>Subject</span>
            <select value={subject} onChange={(e) => (!dirty || confirm("Discard unsaved changes?")) && setSubject(e.target.value)}>
              {subjects.map((s) => <option key={s} value={s}>{s}{drafts.some((d) => d.subject_id === s) ? " (draft)" : ""}</option>)}
            </select>
          </label>
        </div>
      </div>

      {shown && (
        <div className="card">
          <div className="row spread">
            <div>
              {problems.length === 0
                ? <strong className="ok">Ready to publish: every check passes.</strong>
                : <strong className="error">{problems.length} {problems.length === 1 ? "problem" : "problems"} to fix before publishing</strong>}
            </div>
            <div className="row">
              <button disabled={busy || !dirty} onClick={() => (confirm("Discard unsaved changes?")) && open(subject)}>Discard changes</button>
              <button disabled={busy || !dirty} onClick={saveDraft}>Save draft</button>
              <button className="primary" disabled={busy || problems.length > 0} onClick={publish}>Publish</button>
            </div>
          </div>
          {problems.length > 0 && (
            <ul className="problems small">{problems.slice(0, 50).map((p, i) => <li key={i}><strong>{p.where}:</strong> {p.message}</li>)}</ul>
          )}
        </div>
      )}
      {status && <p role="status">{status}</p>}

      <div className="tabs" role="tablist">
        {TABS.map(([id, label]) => (
          <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "on" : ""} onClick={() => setTab(id)}>{label}</button>
        ))}
      </div>

      {shown && tab === "units" && <UnitsEditor bundle={shown} edit={edit} />}
      {shown && tab === "notes" && <NotesEditor bundle={shown} edit={edit} />}
      {shown && tab === "mcqs" && <McqEditor bundle={shown} edit={edit} />}
      {shown && tab === "written" && <WrittenEditor bundle={shown} edit={edit} />}
      {shown && tab === "papers" && <BlueprintEditor bundle={shown} edit={edit} />}
      {shown && tab === "json" && <JsonEditor bundle={shown} onApply={(b) => setBundle(b)} />}
      {tab === "history" && (
        <div className="card scroll">
          <table>
            <thead><tr><th>Version</th><th>Status</th><th>Created</th><th /></tr></thead>
            <tbody>
              {history.map((v) => (
                <tr key={v.version}>
                  <td>{v.version}</td>
                  <td>{v.status === "PUBLISHED" ? <span className="pill ok">Live</span> : <span className="pill">{v.status.toLowerCase()}</span>}</td>
                  <td>{when(v.created_at)}</td>
                  <td>{v.status !== "PUBLISHED" && <button disabled={busy} onClick={() => rollback(v.version)}>Roll back to this</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function JsonEditor({ bundle, onApply }: { bundle: Bundle; onApply: (b: Bundle) => void }) {
  const [text, setText] = useState(() => JSON.stringify(bundle, null, 2));
  const [error, setError] = useState("");
  useEffect(() => setText(JSON.stringify(bundle, null, 2)), [bundle]);
  return (
    <div className="card">
      <p className="muted small">Everything in one place, including paper questions and module practice. Apply, then check the problems list above.</p>
      <textarea className="code" spellCheck={false} value={text} onChange={(e) => setText(e.target.value)} aria-label="Content as JSON" />
      <div className="row" style={{ marginTop: 8 }}>
        <button onClick={() => { try { onApply(JSON.parse(text)); setError(""); } catch (e) { setError(`Not valid JSON: ${(e as Error).message}`); } }}>Apply</button>
        {error && <span className="error">{error}</span>}
      </div>
    </div>
  );
}
