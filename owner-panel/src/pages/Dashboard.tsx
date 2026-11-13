import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { Stat, when } from "../ui";
import { estimateCost, money } from "../cost";

interface Figures {
  learners_total: number; learners_active_today: number; learners_active_7d: number;
  ai_calls_today: number; ai_calls_7d: number; ai_failed_7d: number;
  tokens_in_7d: number; tokens_out_7d: number; tokens_in_30d: number; tokens_out_30d: number;
  answers_marked_7d: number; papers_marked_7d: number;
  content: { subject: string; version: number; published_at: string }[];
}

export function Dashboard() {
  const [f, setF] = useState<Figures | null>(null);
  const [rates, setRates] = useState({ usdPerMillionInput: 0, usdPerMillionOutput: 0, pkrPerUsd: 0 });
  const [error, setError] = useState("");

  useEffect(() => {
    db.rpc("owner_dashboard").then(({ data, error }) => (error ? setError(explain(error)) : setF(data as Figures)));
    db.from("app_config").select("key,value").then(({ data }) => {
      const v = (k: string) => Number((data ?? []).find((r) => r.key === k)?.value ?? 0);
      setRates({ usdPerMillionInput: v("usd_per_million_input"), usdPerMillionOutput: v("usd_per_million_output"), pkrPerUsd: v("pkr_per_usd") });
    });
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!f) return <p className="muted">Loading…</p>;
  const c7 = estimateCost(f.tokens_in_7d, f.tokens_out_7d, rates);
  const c30 = estimateCost(f.tokens_in_30d, f.tokens_out_30d, rates);
  const priced = rates.usdPerMillionInput > 0 || rates.usdPerMillionOutput > 0;
  const n = (x: number) => x.toLocaleString("en-PK");
  return (
    <>
      <h1>Dashboard</h1>
      <p className="muted">Live figures from the server.</p>
      <h2>Learners</h2>
      <div className="grid">
        <Stat n={n(f.learners_total)} label="Signed-up learners" />
        <Stat n={n(f.learners_active_today)} label="Active today" />
        <Stat n={n(f.learners_active_7d)} label="Active in 7 days" />
      </div>
      <h2>AI marking and tutor</h2>
      <div className="grid">
        <Stat n={n(f.ai_calls_today)} label="AI calls today" />
        <Stat n={n(f.ai_calls_7d)} label="AI calls in 7 days" />
        <Stat n={<span className={f.ai_failed_7d ? "warn" : ""}>{n(f.ai_failed_7d)}</span>} label="Failed AI calls in 7 days" />
        <Stat n={n(f.papers_marked_7d)} label="Papers marked in 7 days" />
        <Stat n={n(f.answers_marked_7d)} label="Answers marked in 7 days" />
      </div>
      <h2>Estimated AI cost</h2>
      {priced ? (
        <div className="grid">
          <Stat n={money(c7.pkr, "PKR")} label={`7 days (${money(c7.usd, "USD")})`} />
          <Stat n={money(c30.pkr, "PKR")} label={`30 days (${money(c30.usd, "USD")})`} />
          <Stat n={`${n(f.tokens_in_30d + f.tokens_out_30d)}`} label="Tokens in 30 days" />
        </div>
      ) : (
        <p className="muted">Set your model's prices in AI settings to see the cost. Tokens in 30 days: {n(f.tokens_in_30d + f.tokens_out_30d)}.</p>
      )}
      <h2>Published content</h2>
      <div className="card scroll">
        <table>
          <thead><tr><th>Subject</th><th>Version</th><th>Published</th></tr></thead>
          <tbody>
            {f.content.map((c) => <tr key={c.subject}><td>{c.subject}</td><td>{c.version}</td><td>{when(c.published_at)}</td></tr>)}
            {!f.content.length && <tr><td colSpan={3} className="muted">Nothing published yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
