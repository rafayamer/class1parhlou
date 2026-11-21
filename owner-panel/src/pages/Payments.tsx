import { useEffect, useState } from "react";
import { db, explain } from "../supabase";
import { Check, Num, Text } from "../ui";

type Row = { id: number; email: string; method: string; months: number; amount_pkr: number; txn_ref: string; payer: string;
  status: string; note: string; created_at: string; paid_until: string | null };
type Wallet = { number: string; name: string };
type Plan = { free_monthly_usd: number; monthly_usd_per_student: number; monthly_credits: number; plus_price_pkr: number; ads_enabled: boolean;
  payment_accounts: Record<string, Wallet> };
type Promo = { id: number; hint: string; days: number; uses_left: number; redeemed: number; note: string; expires_at: string | null;
  disabled: boolean; created_at: string };
const METHODS: [string, string][] = [["jazzcash", "JazzCash"], ["easypaisa", "Easypaisa"], ["bank", "Bank transfer"]];
const methodName = (m: string) => METHODS.find(([id]) => id === m)?.[1] ?? m;

/**
 * Premium (stored as "plus"), paid by hand. A student sends money to your JazzCash/Easypaisa wallet and types the transaction ID (TID) in the app.
 * Match the TID and amount with the SMS on your phone, then approve: Premium switches on for the months paid.
 */
export function Payments() {
  const [filter, setFilter] = useState("pending");
  const [rows, setRows] = useState<Row[] | null>(null);
  const [status, setStatus] = useState("");
  const [notes, setNotes] = useState<Record<number, string>>({});

  const load = () => db.rpc("owner_payments", { p_status: filter }).then(({ data, error }) => {
    if (error) { setStatus(explain(error)); setRows([]); } else setRows((data ?? []) as Row[]);
  });
  useEffect(() => { setRows(null); load(); }, [filter]);

  const decide = async (r: Row, approve: boolean) => {
    if (!approve && !(notes[r.id] ?? "").trim()) return setStatus("Write a short reason first: the student sees it.");
    setStatus(approve ? "Approving…" : "Rejecting…");
    const { error } = await db.rpc("owner_payment_decide", { p_id: r.id, p_approve: approve, p_note: notes[r.id] ?? "" });
    setStatus(error ? explain(error) : approve ? `Approved: ${r.email} has Premium for ${r.months} more month(s).` : "Rejected.");
    load();
  };

  return (
    <>
      <h1>Payments</h1>
      <PlanSettings />
      <PromoCodes />
      <div className="card">
        <div className="row">
          <h2 style={{ margin: 0, flex: 1 }}>Payments to check</h2>
          <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Show">
            <option value="pending">Waiting</option><option value="approved">Approved</option>
            <option value="rejected">Rejected</option><option value="all">All</option>
          </select>
        </div>
        <p className="muted small">Check each transaction ID and amount against the SMS in your wallet app before approving.</p>
        {!rows ? <p className="muted">Loading…</p> : !rows.length ? <p className="muted">Nothing here.</p> : (
          <div style={{ overflowX: "auto" }}>
            <table>
              <thead><tr><th>When</th><th>Student</th><th>Paid with</th><th>Amount</th><th>TID</th><th>From</th><th>Premium until</th><th></th></tr></thead>
              <tbody>{rows.map((r) => (
                <tr key={r.id}>
                  <td>{new Date(r.created_at).toLocaleString()}</td>
                  <td>{r.email}</td>
                  <td>{methodName(r.method)}</td>
                  <td>Rs {r.amount_pkr.toLocaleString()} ({r.months} mo)</td>
                  <td><code>{r.txn_ref}</code></td>
                  <td>{r.payer || "—"}</td>
                  <td>{r.paid_until ?? "—"}</td>
                  <td>{r.status === "pending" ? (
                    <div className="row">
                      <button className="primary" onClick={() => decide(r, true)}>Approve</button>
                      <input placeholder="Reason if rejecting" value={notes[r.id] ?? ""} onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })} />
                      <button onClick={() => decide(r, false)}>Reject</button>
                    </div>
                  ) : <span className="muted">{r.status}{r.note ? `: ${r.note}` : ""}</span>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </div>
      {status && <p role="status">{status}</p>}
    </>
  );
}

function PlanSettings() {
  const [saved, setSaved] = useState<Plan | null>(null);
  const [v, setV] = useState<Plan | null>(null);
  const [status, setStatus] = useState("");
  const load = () => db.from("app_config").select("key,value").then(({ data, error }) => {
    if (error) return setStatus(explain(error));
    const get = (k: string, d: unknown) => (data ?? []).find((r) => r.key === k)?.value ?? d;
    const next: Plan = {
      free_monthly_usd: Number(get("free_monthly_usd", 0)),
      monthly_usd_per_student: Number(get("monthly_usd_per_student", 3.5)),
      monthly_credits: Number(get("monthly_credits", 1000)),
      plus_price_pkr: Number(get("plus_price_pkr", 3999)),
      ads_enabled: get("ads_enabled", true) === true,
      payment_accounts: (get("payment_accounts", {}) ?? {}) as Record<string, Wallet>,
    };
    setSaved(next); setV(next);
  });
  useEffect(() => { load(); }, []);
  if (!v || !saved) return <p className="muted">{status || "Loading…"}</p>;
  const keys = Object.keys(v) as (keyof Plan)[];
  const changed = keys.filter((k) => JSON.stringify(v[k]) !== JSON.stringify(saved[k]));
  const save = async () => {
    setStatus("Saving…");
    for (const k of changed) {
      const value = k === "payment_accounts"
        ? Object.fromEntries(Object.entries(v.payment_accounts).filter(([, w]) => w.number.trim()))
        : v[k];
      const { error } = await db.rpc("owner_set_config", { p_key: k, p_value: value });
      if (error) return setStatus(explain(error));
    }
    setStatus("Saved.");
    load();
  };
  const wallet = (m: string) => v.payment_accounts[m] ?? { number: "", name: "" };
  const setWallet = (m: string, w: Wallet) => setV({ ...v, payment_accounts: { ...v.payment_accounts, [m]: w } });
  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>Plans</h2>
      <Num label="Premium price per month (Rs)" value={v.plus_price_pkr} onChange={(x) => setV({ ...v, plus_price_pkr: x })} />
      <Num label="Premium: credits per month" step={100} value={v.monthly_credits} onChange={(x) => setV({ ...v, monthly_credits: x })} />
      <Num label="Premium: what those credits are worth in AI time (US $ a month)" step={0.5} value={v.monthly_usd_per_student} onChange={(x) => setV({ ...v, monthly_usd_per_student: x })} />
      <p className="muted small">
        {v.monthly_credits.toLocaleString()} credits = ${v.monthly_usd_per_student.toFixed(2)}, so 1 credit = ${v.monthly_credits > 0 ? (v.monthly_usd_per_student / v.monthly_credits).toFixed(4) : "—"} of AI time.
        Every AI call uses what it really cost. Credits renew each month from the day the student paid, and stop when Premium ends.
      </p>
      <Num label="Free: AI allowance per month (US $, 0 = no AI for free students)" step={0.1} value={v.free_monthly_usd} onChange={(x) => setV({ ...v, free_monthly_usd: x })} />
      <Check label="Show ads to free-plan students (after some quizzes only)" value={v.ads_enabled} onChange={(x) => setV({ ...v, ads_enabled: x })} />
      <h3>Where students send money</h3>
      <p className="muted small">Your own wallet numbers. Leave a number empty to hide that option in the app.</p>
      {METHODS.map(([m, label]) => (
        <div key={m} className="row">
          <Text label={`${label} number`} value={wallet(m).number} onChange={(x) => setWallet(m, { ...wallet(m), number: x })} />
          <Text label="Account name" value={wallet(m).name} onChange={(x) => setWallet(m, { ...wallet(m), name: x })} />
        </div>
      ))}
      <div className="row">
        <button className="primary" disabled={!changed.length} onClick={save}>Save {changed.length ? `(${changed.length})` : ""}</button>
        <button disabled={!changed.length} onClick={() => setV(saved)}>Undo changes</button>
      </div>
      {status && <p role="status">{status}</p>}
    </div>
  );
}

/** Promo codes: Premium for some days, for testing or for teachers. A code is shown once; only its hash is kept. */
function PromoCodes() {
  const [rows, setRows] = useState<Promo[] | null>(null);
  const [days, setDays] = useState(30);
  const [uses, setUses] = useState(1);
  const [note, setNote] = useState("");
  const [made, setMade] = useState<string | null>(null);
  const [status, setStatus] = useState("");
  const load = () => db.rpc("owner_promos").then(({ data, error }) => {
    if (error) { setStatus(explain(error)); setRows([]); } else setRows((data ?? []) as Promo[]);
  });
  useEffect(() => { load(); }, []);
  const create = async () => {
    setStatus("Making a code…"); setMade(null);
    const { data, error } = await db.rpc("owner_promo_create", { p_days: days, p_uses: uses, p_note: note.trim() });
    if (error) return setStatus(explain(error));
    setMade(String(data)); setStatus(""); setNote(""); load();
  };
  const disable = async (r: Promo) => {
    const { error } = await db.rpc("owner_promo_disable", { p_id: r.id });
    setStatus(error ? explain(error) : `Code …${r.hint} switched off.`);
    load();
  };
  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>Promo codes</h2>
      <p className="muted small">A student types the code on the Premium screen (direct-download app only) and gets Premium for its days, with credits.
        Each account can use a code once. Codes are shown only when made: copy it then.</p>
      <div className="row">
        <Num label="Days of Premium" value={days} onChange={(x) => setDays(Math.max(1, Math.min(366, Math.round(x))))} />
        <Num label="How many students can use it" value={uses} onChange={(x) => setUses(Math.max(1, Math.min(10000, Math.round(x))))} />
        <Text label="Note (who it's for)" value={note} onChange={setNote} />
      </div>
      <button className="primary" onClick={create}>Make a code</button>
      {made && <p role="status">New code: <code style={{ fontSize: "1.2em" }}>{made}</code> — copy it now, it won't be shown again.</p>}
      {!rows ? <p className="muted">Loading…</p> : !rows.length ? <p className="muted">No codes yet.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead><tr><th>Code</th><th>Note</th><th>Days</th><th>Used</th><th>Left</th><th>Code expires</th><th></th></tr></thead>
            <tbody>{rows.map((r) => (
              <tr key={r.id}>
                <td><code>…{r.hint}</code></td><td>{r.note || "—"}</td><td>{r.days}</td><td>{r.redeemed}</td><td>{r.uses_left}</td>
                <td>{r.expires_at ? new Date(r.expires_at).toLocaleDateString() : "never"}</td>
                <td>{r.disabled ? <span className="muted">off</span> : <button onClick={() => disable(r)}>Switch off</button>}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {status && <p role="status">{status}</p>}
    </div>
  );
}
