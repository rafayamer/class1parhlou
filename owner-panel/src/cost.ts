/** Estimated AI spend from token counts and the owner's rates (USD per million tokens, PKR per USD). */
export interface Rates { usdPerMillionInput: number; usdPerMillionOutput: number; pkrPerUsd: number }

export function estimateCost(tokensIn: number, tokensOut: number, r: Rates): { usd: number; pkr: number } {
  const usd = (tokensIn * r.usdPerMillionInput + tokensOut * r.usdPerMillionOutput) / 1_000_000;
  return { usd, pkr: usd * r.pkrPerUsd };
}

export const money = (n: number, currency: "USD" | "PKR") =>
  new Intl.NumberFormat("en-PK", { style: "currency", currency, maximumFractionDigits: currency === "PKR" ? 0 : 2 }).format(n);
