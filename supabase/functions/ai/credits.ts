// Credits: Premium students get a monthly allowance of AI time, shown as credits (default 1,000 credits = US$3.50). Every AI
// call is charged at what it really cost, so credits never drift from the OpenAI bill. Free students get no AI.
// Pure functions, so the rules are tested without a database.

/** What each kind of AI work typically sends and receives (tokens), used for estimates until real averages exist. */
export const TYPICAL_TOKENS: Record<string, { input: number; output: number }> = {
  chat: { input: 4000, output: 1200 },           // a question to Ask Ustaad
  photo: { input: 6000, output: 2500 },          // a photo of a question: read it, then answer it
  mark: { input: 3000, output: 3000 },           // one written answer marked by the AI examiner
  mark_photo: { input: 6000, output: 4000 },     // one handwritten answer marked from its photos
  page: { input: 3500, output: 2500 },           // one scanned answer page read
  report: { input: 4000, output: 2500 },         // the examiner's analysis of a paper
  pick: { input: 6000, output: 1500 },           // choosing a paper's questions for the student
};
/** Which logged AI task (ai_usage.task) each estimate learns from. */
export const ESTIMATE_TASKS: Record<string, string> = {
  chat: "ustaad", photo: "ustaad_photo", mark: "mark_written", page: "read_script", report: "paper_report", pick: "pick_paper",
};
/** Real averages are used once a task has this many recorded calls. */
export const MIN_CALLS_FOR_AVERAGE = 10;

/** Credits for an amount of money, given that [monthlyCredits] are worth [monthlyUsd]. */
export function creditsFor(usd: number, monthlyUsd: number, monthlyCredits: number): number {
  return monthlyUsd > 0 ? (usd / monthlyUsd) * monthlyCredits : 0;
}

/** Whole credits left this month (never below 0). */
export function creditsLeft(usedUsd: number, monthlyUsd: number, monthlyCredits: number): number {
  return Math.max(0, Math.floor(monthlyCredits - creditsFor(usedUsd, monthlyUsd, monthlyCredits) + 1e-9));
}

/**
 * The Premium month a date falls in. Months run from the day the student paid, not the calendar: Premium paid on the 21st
 * renews credits on the 21st of each month, and stops on [paidUntil]. Returns the month's first day and the day credits
 * renew (or Premium ends, whichever is first), as YYYY-MM-DD.
 */
export function premiumMonth(paidUntil: string, now: Date): { start: string; resetsOn: string } {
  const [y, m, d] = paidUntil.split("-").map(Number);
  const today = now.toISOString().slice(0, 10);
  const back = (n: number) => {
    const last = new Date(Date.UTC(y, m - 1 - n + 1, 0)).getUTCDate(); // days in the target month
    return new Date(Date.UTC(y, m - 1 - n, Math.min(d, last))).toISOString().slice(0, 10);
  };
  for (let n = 1; n <= 240; n++) {
    const start = back(n);
    if (start <= today) return { start, resetsOn: back(n - 1) };
  }
  return { start: today, resetsOn: paidUntil };
}

/**
 * Credits each kind of AI work is likely to cost, to one decimal: the real average of recent calls when there are enough of
 * them, otherwise the typical tokens at today's prices. [averages] maps a logged task to its average cost and call count.
 */
export function estimates(averages: Record<string, { usd: number; calls: number }>, usdIn: number, usdOut: number,
                          monthlyUsd: number, monthlyCredits: number): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [key, t] of Object.entries(TYPICAL_TOKENS)) {
    const real = averages[ESTIMATE_TASKS[key] ?? ""];
    const typical = usdOf(t.input, t.output, usdIn, usdOut);
    // Handwritten marking is learned from typed marking's average, scaled by how much bigger it typically is.
    const usd = real && real.calls >= MIN_CALLS_FOR_AVERAGE ? real.usd
      : key === "mark_photo" && (averages.mark_written?.calls ?? 0) >= MIN_CALLS_FOR_AVERAGE
        ? averages.mark_written.usd * (typical / usdOf(TYPICAL_TOKENS.mark.input, TYPICAL_TOKENS.mark.output, usdIn, usdOut))
        : typical;
    out[key] = Math.max(0.1, Math.ceil(creditsFor(usd, monthlyUsd, monthlyCredits) * 10) / 10);
  }
  return out;
}

/**
 * Who may use AI, and how much: Premium gets the full monthly allowance; the free plan gets [freeUsd] (0 = no AI at all).
 * `plus` is null when plans are not set up yet: then the full allowance applies, as before plans.
 */
export function allowanceFor(plus: boolean | null, plusUsd: number, freeUsd: number): { allowed: boolean; capUsd: number } {
  if (plus === false) return { allowed: freeUsd > 0, capUsd: freeUsd };
  return { allowed: true, capUsd: plusUsd };
}

// ── Money: each student's monthly AI allowance in US dollars ─────────────────────────────────────────────────────
/** The cost of one AI call from its tokens and the price per million tokens. */
export function usdOf(inputTokens: number, outputTokens: number, usdPerMillionIn: number, usdPerMillionOut: number): number {
  return Math.round(((inputTokens || 0) * usdPerMillionIn + (outputTokens || 0) * usdPerMillionOut) / 1e6 * 1e6) / 1e6;
}
/** The first moment of this calendar month (UTC), as an ISO string. */
export function monthStart(now: Date): string {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString();
}
/** The day the allowance starts again: the first of next month (UTC), as YYYY-MM-DD. */
export function nextMonthStart(now: Date): string {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1)).toISOString().slice(0, 10);
}
/** True when the month's spend has reached the cap (a cap of 0 or less means no cap). */
export function overCap(spentUsd: number, capUsd: number): boolean {
  return capUsd > 0 && spentUsd >= capUsd;
}
