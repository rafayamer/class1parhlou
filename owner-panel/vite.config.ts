import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const SUPABASE = (process.env.VITE_SUPABASE_URL ?? "https://hulfrloizndafqchcwhz.supabase.co").replace(/\/$/, "");

/**
 * A strict Content Security Policy in the built page: scripts only from this site, network only to Supabase. Even if some
 * text from the database ever slipped into the page as HTML, it could not run code or send the owner's session anywhere.
 * (Only in the build: the dev server needs inline scripts for hot reload.)
 */
const csp = (): Plugin => ({
  name: "parhlou-csp",
  apply: "build",
  transformIndexHtml: (html) => html.replace("<head>", `<head>
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' ${SUPABASE} ${SUPABASE.replace("https://", "wss://")}; object-src 'none'; base-uri 'none'; form-action 'none'" />
    <meta name="referrer" content="no-referrer" />`),
});

// Served from GitHub Pages at https://<owner>.github.io/<repo>/ (the workflow sets PANEL_BASE).
export default defineConfig({
  base: process.env.PANEL_BASE ?? "/",
  plugins: [react(), csp()],
});
