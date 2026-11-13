import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Served from GitHub Pages at https://<owner>.github.io/<repo>/ (the workflow sets PANEL_BASE).
export default defineConfig({
  base: process.env.PANEL_BASE ?? "/",
  plugins: [react()],
});
