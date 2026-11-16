import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";

interface Feedback { id: number; user_id: string | null; text: string; screen: string | null; app_version: string | null; status: "open" | "done"; created_at: string }
interface Flag { id: number; user_id: string | null; category: string; excerpt: string; status: "open" | "handled"; note: string | null; created_at: string }
interface Usage { holder: string; user_id: string | null; display_name: string | null; task: string; spent: number; uses: number }

/** One credit is about $0.0035 of AI time (1,000 credits = $3.50 a month). */
const USD_PER_CREDIT = 0.0035;
const CATEGORY: Record<string, string> = { UNSAFE_SEXUAL: "Sexual", UNSAFE_ILLEGAL: "Illegal or harmful", SELF_HARM: "Self-harm (care)" };

/** Student care: feedback from the app, safety flags from Ask Ustaad, and how much AI time each student uses. */
export function Care({ onView }: { onView: (userId: string) => void }) {
  const [tab, setTab] = useState<"flags" | "feedback" | "credits">("flags");
  return (
    <>
      <h1>Student care</h1>
      <div className="tabs">
        <button className={tab === "flags" ? "on" : ""} onClick={() => setTab("flags")}>Safety flags</button>
        <button className={tab === "feedback" ? "on" : ""} onClick={() => setTab("feedback")}>Feedback</button>
        <button className={tab === "credits" ? "on" : ""} onClick={() => setTab("credits")}>AI use</button>
      </div>
      {tab === "flags" && <Flags onView={onView} />}
      {tab === "feedback" && <FeedbackInbox onView={onView} />}
      {tab === "credits" && <Credits onView={onView} />}
    </>
  );
}

function useRpc<T>(name: string, version: number): [T[] | null, string] {
  const [rows, setRows] = useState<T[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { db.rpc(name).then(({ data, error }) => (error ? setError(explain(error)) : setRows(data as T[]))); }, [name, version]);
  return [rows, error];
}

function Who({ id, onView }: { id: string | null; onView: (id: string) => void }) {
  return id ? <button onClick={() => onView(id)}>View student</button> : <span className="muted small">Not signed in</span>;
}

function Flags({ onView }: { onView: (id: string) => void }) {
  const [v, setV] = useState(0);
  const [rows, error] = useRpc<Flag>("owner_safety_flags", v);
  const [notes, setNotes] = useState<Record<number, string>>({});
  if (error) return <p className="error">{error}</p>;
  if (!rows) return <p className="muted">Loading…</p>;
  const save = async (f: Flag, status: Flag["status"]) => {
    const { error } = await db.rpc("owner_set_flag_status", { p_id: f.id, p_status: status, p_note: notes[f.id] ?? f.note ?? "" });
    if (error) alert(explain(error)); else setV(v + 1);
  };
  return (
    <>
      <p className="muted">Messages Ustaad refused. The student was told it was recorded. Nothing is sent to parents automatically: contact them from the student's page if needed.</p>
      {!rows.length && <div className="card muted">No safety flags.</div>}
      {rows.map((f) => (
        <div className="card" key={f.id}>
          <div className="row spread">
            <strong>{CATEGORY[f.category] ?? f.category}</strong>
            <span className={`pill ${f.status === "handled" ? "ok" : ""}`}>{f.status === "handled" ? "Handled" : "Open"}</span>
          </div>
          <p className="small muted">{when(f.created_at)}</p>
          <p>“{f.excerpt}”</p>
          <textarea placeholder="Note (what you did)" value={notes[f.id] ?? f.note ?? ""} onChange={(e) => setNotes({ ...notes, [f.id]: e.target.value })} />
          <div className="row" style={{ marginTop: 8 }}>
            <Who id={f.user_id} onView={onView} />
            {f.status === "open"
              ? <button className="primary" onClick={() => save(f, "handled")}>Mark handled</button>
              : <button onClick={() => save(f, "open")}>Reopen</button>}
          </div>
        </div>
      ))}
    </>
  );
}

function FeedbackInbox({ onView }: { onView: (id: string) => void }) {
  const [v, setV] = useState(0);
  const [rows, error] = useRpc<Feedback>("owner_feedback", v);
  if (error) return <p className="error">{error}</p>;
  if (!rows) return <p className="muted">Loading…</p>;
  const set = async (f: Feedback, status: Feedback["status"]) => {
    const { error } = await db.rpc("owner_set_feedback_status", { p_id: f.id, p_status: status });
    if (error) alert(explain(error)); else setV(v + 1);
  };
  return (
    <>
      <p className="muted">{rows.filter((r) => r.status === "open").length} open. Sent from Profile → Send feedback.</p>
      {!rows.length && <div className="card muted">No feedback yet.</div>}
      {rows.map((f) => (
        <div className="card" key={f.id}>
          <div className="row spread">
            <span className="small muted">{when(f.created_at)}{f.screen ? ` · ${f.screen}` : ""}{f.app_version ? ` · ${f.app_version}` : ""}</span>
            <span className={`pill ${f.status === "done" ? "ok" : ""}`}>{f.status === "done" ? "Done" : "Open"}</span>
          </div>
          <p style={{ whiteSpace: "pre-wrap" }}>{f.text}</p>
          <div className="row">
            <Who id={f.user_id} onView={onView} />
            {f.status === "open" ? <button className="primary" onClick={() => set(f, "done")}>Mark done</button> : <button onClick={() => set(f, "open")}>Reopen</button>}
          </div>
        </div>
      ))}
    </>
  );
}

function Credits({ onView }: { onView: (id: string) => void }) {
  const [rows, error] = useRpc<Usage>("owner_credit_usage", 0);
  if (error) return <p className="error">{error}</p>;
  if (!rows) return <p className="muted">Loading…</p>;
  const total = rows.reduce((s, r) => s + Number(r.spent), 0);
  return (
    <>
      <p className="muted">Last 30 days. Each student gets 1,000 credits a month (unused credits last 4 months). Prices per task are in AI settings.
        Total: {total.toLocaleString()} credits ≈ ${(total * USD_PER_CREDIT).toFixed(2)}.</p>
      <div className="card scroll">
        <table>
          <thead><tr><th>Student</th><th>Task</th><th>Uses</th><th>Credits</th><th>≈ Cost</th><th /></tr></thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i}>
                <td>{r.display_name || <span className="muted">{r.user_id ? "No name" : "Not signed in"}</span>}</td>
                <td>{r.task}</td><td>{r.uses}</td><td>{Number(r.spent).toLocaleString()}</td><td>${(Number(r.spent) * USD_PER_CREDIT).toFixed(2)}</td>
                <td>{r.user_id && <button onClick={() => onView(r.user_id!)}>View</button>}</td>
              </tr>
            ))}
            {!rows.length && <tr><td colSpan={6} className="muted">No AI use yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
