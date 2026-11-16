import { createClient } from "@supabase/supabase-js";
import { SUPABASE_KEY, SUPABASE_URL } from "./config";

export const db = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: { persistSession: true, detectSessionInUrl: true, flowType: "implicit" },
});

/** Turns a database error into a sentence for the owner. */
export function explain(error: { message?: string; code?: string } | null | undefined): string {
  const m = error?.message ?? "Something went wrong.";
  if (m.includes("OWNER_ONLY")) return "This account is not the owner account.";
  if (m.includes("BUNDLE_INVALID")) return "The content was refused by the server: " + m.replace(/^.*BUNDLE_INVALID:\s*/, "");
  if (m.includes("SETTING_NOT_ALLOWED")) return "That setting can't be changed here.";
  if (m.includes("VERSION_NOT_FOUND")) return "That version no longer exists.";
  // owner_feedback, owner_student, … arrive with the students_care migration.
  if (error?.code === "PGRST202" || /could not find the function public\.owner_(feedback|safety_flags|credit_usage|student)/i.test(m))
    return "This page needs the database update 20261117000001_students_care.sql. Run it in the Supabase SQL editor (see docs/owner-setup.md).";
  if (m.toLowerCase().includes("jwt") || m.toLowerCase().includes("expired")) return "Your sign-in has expired. Sign in again.";
  return m;
}
