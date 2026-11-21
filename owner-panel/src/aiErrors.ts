// Why an AI call failed (ai_usage.error, written by the AI gateway), in plain words for the owner.
/** A failure reason in plain words, with what to do about it. */
export function whyFailed(code: string | null | undefined): string {
  if (!code) return "";
  if (code.includes("insufficient_quota")) return "OpenAI credit is used up. Add credit at platform.openai.com → Settings → Billing.";
  if (code.startsWith("openai_401")) return "OpenAI didn't accept the key. Check the OPENAI_API_KEY secret in Supabase.";
  if (code.includes("model_not_found") || code.startsWith("openai_404")) return "This OpenAI account can't use the model yet.";
  if (code.startsWith("openai_429")) return "Too many requests at once. It clears by itself.";
  if (code.startsWith("openai_5")) return "OpenAI had a problem. It usually passes in a few minutes.";
  if (code.startsWith("openai_400")) return "OpenAI refused the request.";
  if (code === "invalid_reply") return "The AI's answer didn't pass Parhlou's checks, so it wasn't shown.";
  return code;
}
