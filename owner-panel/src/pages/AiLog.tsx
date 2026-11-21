import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";
import { whyFailed } from "../aiErrors";

interface Call { id: string; task: string; model: string; prompt_version: string; input_tokens: number; output_tokens: number; attempts: number; ok: boolean; created_at: string;
  error?: string | null }


const TASKS: Record<string, string> = { tutor: "Tutor", mcq_feedback: "Explain an MCQ", mark_written: "Mark written answer", ustaad: "Ask Ustaad",
  ustaad_photo: "Ask Ustaad (photo)", read_script: "Read an answer page", paper_report: "Examiner's report", pick_paper: "Make a paper" };
const COLUMNS = "id,task,model,prompt_version,input_tokens,output_tokens,attempts,ok,created_at";

export function AiLog() {
  const [rows, setRows] = useState<Call[] | null>(null);
  const [onlyFailed, setOnlyFailed] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const load = (columns: string) => {
      let q = db.from("ai_usage").select(columns).order("created_at", { ascending: false }).limit(200);
      if (onlyFailed) q = q.eq("ok", false);
      return q;
    };
    // The failure reason needs the Premium credits database update; without it the log shows as before.
    load(COLUMNS + ",error").then(({ data, error }) => {
      if (!error) return setRows(data as unknown as Call[]);
      load(COLUMNS).then(({ data: d, error: e }) => (e ? setError(explain(e)) : setRows(d as unknown as Call[])));
    });
  }, [onlyFailed]);
  const latestFailure = rows?.find((r) => !r.ok && r.error);
  if (error) return <p className="error">{error}</p>;
  return (
    <>
      <h1>AI log</h1>
      <div className="row spread">
        <p className="muted">The latest 200 AI calls. Student answers are not stored here.</p>
        <label className="check"><input type="checkbox" checked={onlyFailed} onChange={(e) => setOnlyFailed(e.target.checked)} />Failed only</label>
      </div>
      {latestFailure && rows?.[0] && !rows[0].ok && (
        <p className="warn" role="status">Latest AI calls are failing: {whyFailed(latestFailure.error)}</p>
      )}
      {!rows ? <p className="muted">Loading…</p> : (
        <div className="card scroll">
          <table>
            <thead><tr><th>When</th><th>Task</th><th>Result</th><th>Tokens in / out</th><th>Tries</th><th>Model · prompt</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{when(r.created_at)}</td><td>{TASKS[r.task] ?? r.task}</td>
                  <td>{r.ok ? <span className="pill ok">OK</span> : <><span className="pill error">Failed</span>{r.error && <div className="small muted">{whyFailed(r.error)}</div>}</>}</td>
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
