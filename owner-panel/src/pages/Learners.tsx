import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";

interface Learner { user_id: string; display_name: string | null; joined: string; last_active: string | null; papers: number; answers_marked: number; average_percent: number | null }

/** Learners at a glance. No bulk export: children's data stays in the database. */
export function Learners({ onView }: { onView: (userId: string) => void }) {
  const [rows, setRows] = useState<Learner[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { db.rpc("owner_learners").then(({ data, error }) => (error ? setError(explain(error)) : setRows(data as Learner[]))); }, []);
  if (error) return <p className="error">{error}</p>;
  if (!rows) return <p className="muted">Loading…</p>;
  return (
    <>
      <h1>Learners</h1>
      <p className="muted">{rows.length} signed-up learners. Average mark is from AI-marked answers only.</p>
      <div className="card scroll">
        <table>
          <thead><tr><th>Name</th><th>Joined</th><th>Last active</th><th>Papers</th><th>Answers marked</th><th>Average</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.user_id}>
                <td>{r.display_name || <span className="muted">No name</span>}</td>
                <td>{when(r.joined)}</td><td>{when(r.last_active)}</td><td>{r.papers}</td><td>{r.answers_marked}</td>
                <td>{r.average_percent == null ? "—" : `${r.average_percent}%`}</td>
                <td><button onClick={() => onView(r.user_id)}>View</button></td>
              </tr>
            ))}
            {!rows.length && <tr><td colSpan={7} className="muted">No learners yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
