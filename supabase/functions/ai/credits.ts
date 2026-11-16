// Credits: each student gets a monthly grant; a grant expires a set number of months after it was given; spending always
// uses the oldest grant that is still valid. Pure functions, so the rules are tested without a database.

export interface LedgerRow { kind: "grant" | "spend"; amount: number; created_at: string; expires_at: string | null }
export interface Prices { [task: string]: number }

export const DEFAULT_PRICES: Prices = { chat: 1, photo: 3, mark_typed: 1, mark_handwritten: 2, page_read: 2, analysis: 1, pick_paper: 2, videos: 0 };

/** What's left now, and when the next grant arrives (the 1st of next month, UTC). */
export function creditBalance(rows: LedgerRow[], now: Date): { balance: number; resetsOn: string } {
  const grants = rows.filter((r) => r.kind === "grant").map((r) => ({ at: Date.parse(r.created_at), exp: Date.parse(r.expires_at!), left: r.amount }))
    .sort((a, b) => a.at - b.at);
  const spends = rows.filter((r) => r.kind === "spend").map((r) => ({ at: Date.parse(r.created_at), amount: r.amount })).sort((a, b) => a.at - b.at);
  for (const s of spends) {
    let need = s.amount;
    for (const g of grants) {
      if (need <= 0) break;
      if (g.at > s.at || g.exp <= s.at || g.left <= 0) continue; // only grants that existed and were valid at the time
      const take = Math.min(g.left, need);
      g.left -= take; need -= take;
    }
  }
  const t = now.getTime();
  const balance = grants.filter((g) => g.exp > t && g.at <= t).reduce((s, g) => s + g.left, 0);
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
  return { balance, resetsOn: next.toISOString().slice(0, 10) };
}

/** True when this month's grant hasn't been given yet. */
export function needsMonthlyGrant(rows: LedgerRow[], now: Date): boolean {
  const start = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1);
  return !rows.some((r) => r.kind === "grant" && Date.parse(r.created_at) >= start);
}

export function grantExpiry(now: Date, months: number): string {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + months, now.getUTCDate(), now.getUTCHours())).toISOString();
}

/** The price of one request, by what it does. Unknown tasks cost nothing. */
export function priceOf(body: any, prices: Prices): { task: string; credits: number } {
  const p = (k: string) => ({ task: k, credits: Math.max(0, Math.round(Number(prices[k] ?? DEFAULT_PRICES[k] ?? 0))) });
  switch (body?.task) {
    case "ustaad": return body.image ? p("photo") : p("chat");
    case "tutor": case "mcq_feedback": return p("chat");
    case "mark_written": return body.handwritten ? p("mark_handwritten") : p("mark_typed");
    case "read_script": return p("page_read");
    case "paper_report": return p("analysis");
    case "pick_paper": return p("pick_paper");
    case "videos": return p("videos");
    default: return { task: String(body?.task ?? ""), credits: 0 };
  }
}

/** "About 420 questions to Ustaad, 6 papers marked …" from a balance: how many of each thing are left this month. */
export function whatsLeft(balance: number, prices: Prices): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [k, v] of Object.entries({ ...DEFAULT_PRICES, ...prices })) if (v > 0) out[k] = Math.floor(balance / v);
  return out;
}
