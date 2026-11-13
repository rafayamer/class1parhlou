// Public values only. The publishable key gives no access by itself: every table and function checks the signed-in
// user in the database (row-level security and owner-only functions). Never put a service key here.
export const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL ?? "https://hulfrloizndafqchcwhz.supabase.co";
export const SUPABASE_KEY = import.meta.env.VITE_SUPABASE_KEY ?? "sb_publishable_1B5nCScqVoPf4YyzKQ1caQ_-Gi2WaEU";
