import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";

interface Call { id: string; task: string; model: string; prompt_version: string; input_tokens: number; output_tokens: number; attempts: number; ok: boolean; created_at: string }

const TASKS: Record<string, string> = { tutor: "Tutor", mcq_feedback: "Explain an MCQ", mark_written: "Mark written answer" };

export function AiLog() {
  const [rows, setRows] = useState<Call[] | null>(null);
  const [onlyFailed, setOnlyFailed] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    let q = db.from("ai_usage").select("id,task,model,prompt_version,input_tokens,output_tokens,attempts,ok,created_at")
      .order("created_at", { ascending: false }).limit(200);
    if (onlyFailed) q = q.eq("ok", false);
    q.then(({ data, error }) => (error ? setError(explain(error)) : setRows(data as Call[])));
  }, [onlyFailed]);
  if (error) return <p className="error">{error}</p>;
  return (
    <>
      <h1>AI log</h1>
      <div className="row spread">
        <p className="muted">The latest 200 AI calls. Student answers are not stored here.</p>
        <label className="check"><input type="checkbox" checked={onlyFailed} onChange={(e) => setOnlyFailed(e.target.checked)} />Failed only</label>
      </div>
      {!rows ? <p className="muted">Loading…</p> : (
        <div className="card scroll">
          <table>
            <thead><tr><th>When</th><th>Task</th><th>Result</th><th>Tokens in / out</th><th>Tries</th><th>Model · prompt</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{when(r.created_at)}</td><td>{TASKS[r.task] ?? r.task}</td>
                  <td>{r.ok ? <span className="pill ok">OK</span> : <span className="pill error">Failed</span>}</td>
                  <td>{r.input_tokens.toLocaleString()} / {r.output_tokens.toLocaleString()}</td><td>{r.attempts}</td>
                  <td className="small muted">{r.model} · {r.prompt_version}</td>
                </tr>
              ))}
              {!rows.length && <tr><td colSpan={6} className="muted">No calls.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
