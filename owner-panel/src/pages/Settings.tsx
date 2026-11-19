import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { Check, Num } from "../ui";

const KEYS = ["ai_enabled", "daily_calls_per_install", "daily_calls_total", "daily_photo_questions", "monthly_usd_per_student", "usd_per_million_input", "usd_per_million_output", "pkr_per_usd"] as const;
type Key = (typeof KEYS)[number];
type Values = Record<Key, number | boolean>;

export function Settings() {
  const [saved, setSaved] = useState<Values | null>(null);
  const [v, setV] = useState<Values | null>(null);
  const [status, setStatus] = useState("");

  const load = () => db.from("app_config").select("key,value").then(({ data, error }) => {
    if (error) return setStatus(explain(error));
    const get = (k: Key, d: number | boolean) => (data ?? []).find((r) => r.key === k)?.value ?? d;
    const next: Values = {
      ai_enabled: get("ai_enabled", true) === true,
      daily_calls_per_install: Number(get("daily_calls_per_install", 300)),
      daily_calls_total: Number(get("daily_calls_total", 2000)),
      daily_photo_questions: Number(get("daily_photo_questions", 20)),
      monthly_usd_per_student: Number(get("monthly_usd_per_student", 3)),
      usd_per_million_input: Number(get("usd_per_million_input", 0)),
      usd_per_million_output: Number(get("usd_per_million_output", 0)),
      pkr_per_usd: Number(get("pkr_per_usd", 0)),
    };
    setSaved(next); setV(next);
  });
  useEffect(() => { load(); }, []);

  if (!v || !saved) return <p className="muted">{status || "Loading…"}</p>;
  const changed = KEYS.filter((k) => v[k] !== saved[k]);
  const save = async () => {
    setStatus("Saving…");
    for (const k of changed) {
      const { error } = await db.rpc("owner_set_config", { p_key: k, p_value: v[k] });
      if (error) return setStatus(explain(error));
    }
    setStatus("Saved. Phones and the AI gateway use the new settings within about a minute.");
    load();
  };
  const set = (k: Key) => (x: number | boolean) => setV({ ...v, [k]: x });
  return (
    <>
      <h1>AI settings</h1>
      <div className="card">
        <h2 style={{ marginTop: 0 }}>AI on or off</h2>
        <Check label="AI tutor and AI marking are on" value={v.ai_enabled as boolean} onChange={set("ai_enabled")} />
        <p className="muted small">When off, the app keeps working with on-phone checking only. Use this if costs run high or something goes wrong.</p>
      </div>
      <div className="card">
        <h2 style={{ marginTop: 0 }}>Daily limits</h2>
        <Num label="AI calls per student per day" value={v.daily_calls_per_install as number} onChange={set("daily_calls_per_install")} />
        <Num label="AI calls in total per day (all students)" value={v.daily_calls_total as number} onChange={set("daily_calls_total")} />
        <Num label="Ask Ustaad photo questions per student per day" value={v.daily_photo_questions as number} onChange={set("daily_photo_questions")} />
        <Num label="AI allowance per student per month (US $, 0 = no limit)" value={v.monthly_usd_per_student as number} onChange={set("monthly_usd_per_student")} />
      </div>
      <div className="card">
        <h2 style={{ marginTop: 0 }}>Prices for the cost estimate</h2>
        <Num label="USD per million input tokens" step={0.01} value={v.usd_per_million_input as number} onChange={set("usd_per_million_input")} />
        <Num label="USD per million output tokens" step={0.01} value={v.usd_per_million_output as number} onChange={set("usd_per_million_output")} />
        <Num label="PKR per USD" step={0.5} value={v.pkr_per_usd as number} onChange={set("pkr_per_usd")} />
      </div>
      <div className="row">
        <button className="primary" disabled={!changed.length} onClick={save}>Save {changed.length ? `(${changed.length})` : ""}</button>
        <button disabled={!changed.length} onClick={() => setV(saved)}>Undo changes</button>
      </div>
      {status && <p role="status">{status}</p>}
    </>
  );
}
