import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { when } from "../ui";

/** Everything owner_student returns: read-only, and every look is written to the audit log by the database. */
interface Portfolio {
  profile: { display_name?: string | null; guardian_email?: string | null; [k: string]: unknown } | null;
  settings: Record<string, unknown> | null;
  events: { at: string; key: string; code: string }[];
  marks: { created_at: string; question_id?: string; awarded?: number; max_marks?: number; [k: string]: unknown }[];
  memory: { summary: string; updated_at: string } | null;
  chats: { role: "student" | "ustaad"; text: string; at: string }[];
  credits: { kind: string; amount: number; task: string | null; at: string; expires: string | null }[];
  flags: { id: number; category: string; excerpt: string; status: string; created_at: string }[];
  feedback: { id: number; text: string; status: string; created_at: string }[];
  /** This month's AI use against the plan's allowance (the app shows it as the Ustaad energy bar). Needs migration 20261121000001. */
  ai_month?: { plan: "free" | "plus"; paid_until: string | null; spent_usd: number; calls: number; allowance_usd: number };
}

const WHAT: Record<string, string> = { O: "Opened a lesson", V: "Watched a video", L: "Lesson finished", R: "Book reading", Q: "Quiz", W: "Written practice",
  P: "Paper handed in", E: "Paper marked", D: "Paper practised", M: "Topic missed", C: "Topic right", S: "Study session", H: "Rated an answer" };
/** "Quiz · 80%" from a code like "Q:80". */
const what = (code: string) => {
  const [k, n] = (code ?? "").split(":");
  const label = WHAT[k] ?? code;
  return n == null ? label : k === "S" ? `${label} · ${n} XP` : k === "H" ? label : `${label} · ${n}%`;
};

/** "View as student": the student's portfolio, read-only. */
export function Student({ userId, onBack }: { userId: string; onBack: () => void }) {
  const [p, setP] = useState<Portfolio | null>(null);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"overview" | "activity" | "chats" | "credits">("overview");
  useEffect(() => { db.rpc("owner_student", { p_user: userId }).then(({ data, error }) => (error ? setError(explain(error)) : setP(data as Portfolio))); }, [userId]);
  if (error) return <><button onClick={onBack}>← Back</button><p className="error">{error}</p></>;
  if (!p) return <p className="muted">Loading…</p>;
  const name = p.profile?.display_name || "This student";
  // The parent's email comes from what the student typed: use it only if it is a plain email address (no extra mail headers).
  const typed = String((p.profile?.guardian_email as string | undefined) || (p.settings?.["guardianEmail"] as string | undefined) || "").trim();
  const parent = /^[^\s@?&#<>"']+@[^\s@?&#<>"']+\.[A-Za-z]{2,}$/.test(typed) ? typed : "";
  const ai = p.ai_month;
  const used = ai && ai.allowance_usd > 0 ? Math.min(100, Math.round((Number(ai.spent_usd) / Number(ai.allowance_usd)) * 100)) : null;
  const days = new Set(p.events.map((e) => e.at.slice(0, 10))).size;
  return (
    <>
      <button onClick={onBack}>← Back</button>
      <h1 style={{ marginTop: 12 }}>{name}</h1>
      <p className="small warn">Read-only. This view is logged. Students and parents are told the owner can review their data.</p>
      <div className="grid">
        <div className="card stat"><div className="n">{days}</div><div className="l">days studied</div></div>
        <div className="card stat"><div className="n">{p.marks.length}</div><div className="l">answers marked</div></div>
        <div className="card stat">
          <div className="n">{used == null ? "—" : `${used}%`}</div>
          <div className="l">{ai ? <>AI used this month · {ai.plan === "plus" ? "Premium" : "Free"} plan (${Number(ai.spent_usd).toFixed(2)} of ${Number(ai.allowance_usd).toFixed(2)}, {ai.calls} calls)</> : "AI used this month (needs the App design database update)"}</div>
          {used != null && <div className="meter-bar" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={used} aria-label="AI allowance used"><span style={{ width: `${used}%` }} className={used >= 90 ? "hot" : ""} /></div>}
        </div>
        <div className="card stat"><div className="n">{p.flags.filter((f) => f.status === "open").length}</div><div className="l">open safety flags</div></div>
      </div>
      <div className="card">
        <h3>Contact the parent</h3>
        {parent
          ? <a href={`mailto:${parent}?subject=${encodeURIComponent(`About ${name}'s studies on Parhlou`)}`}><button className="primary">Email {parent}</button></a>
          : <p className="muted small">No parent email on file.</p>}
      </div>
      <div className="tabs">
        {(["overview", "activity", "chats", ...(p.credits.length ? ["credits" as const] : [])] as const).map((t) => (
          <button key={t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>{{ overview: "Overview", activity: "Activity", chats: "Chats (30 days)", credits: "Old credits" }[t]}</button>
        ))}
      </div>
      {tab === "overview" && <>
        <div className="card"><h3>What Ustaad remembers</h3>
          {p.memory?.summary ? <><p style={{ whiteSpace: "pre-wrap" }}>{p.memory.summary}</p><p className="small muted">Updated {when(p.memory.updated_at)}</p></> : <p className="muted">Nothing yet.</p>}
        </div>
        <div className="card"><h3>Safety flags</h3>
          {p.flags.length ? p.flags.map((f) => <p key={f.id}><span className="pill">{f.status}</span> {when(f.created_at)}: “{f.excerpt}”</p>) : <p className="muted">None.</p>}
        </div>
        <div className="card"><h3>Feedback</h3>
          {p.feedback.length ? p.feedback.map((f) => <p key={f.id}>{when(f.created_at)}: {f.text}</p>) : <p className="muted">None.</p>}
        </div>
      </>}
      {tab === "activity" && <div className="card scroll"><table>
        <thead><tr><th>When</th><th>What</th><th>Where</th></tr></thead>
        <tbody>{p.events.map((e, i) => <tr key={i}><td>{when(e.at)}</td><td>{what(e.code)}</td><td className="small">{e.key}</td></tr>)}
          {!p.events.length && <tr><td colSpan={3} className="muted">No activity synced yet.</td></tr>}</tbody>
      </table></div>}
      {tab === "chats" && <div className="card">
        {p.chats.map((c, i) => <p key={i} style={{ whiteSpace: "pre-wrap" }}><strong>{c.role === "student" ? name : "Ustaad"}</strong> <span className="small muted">{when(c.at)}</span><br />{c.text}</p>)}
        {!p.chats.length && <p className="muted">No chats in the last 30 days.</p>}
      </div>}
      {tab === "credits" && <div className="card scroll"><table>
        <thead><tr><th>When</th><th>Kind</th><th>Task</th><th>Credits</th><th>Expires</th></tr></thead>
        <tbody>{p.credits.map((c, i) => <tr key={i}><td>{when(c.at)}</td><td>{c.kind}</td><td>{c.task ?? "—"}</td><td>{c.kind === "spend" ? -c.amount : `+${c.amount}`}</td><td>{when(c.expires)}</td></tr>)}
          {!p.credits.length && <tr><td colSpan={5} className="muted">No credit history.</td></tr>}</tbody>
      </table></div>}
    </>
  );
}
