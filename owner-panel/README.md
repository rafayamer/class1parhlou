# Parhlou owner panel

A small web page for the owner: dashboard, content editor (draft, checks, publish, history, roll back), AI settings,
learners and the AI log. It talks to Supabase with the public publishable key; every read and action is checked by the
database (row-level security and owner-only functions), so the page grants nothing by itself.

The owner is whoever signs in with the email stored server-side in `app_config.owner_email`
(set with `public.set_owner_email`, never in this repository).

```bash
npm ci
npm test        # content checks and cost maths
npm run dev     # http://localhost:5173
npm run build   # dist/, published by .github/workflows/owner-panel.yml
```

Sign-in links go to the published address (`https://<user>.github.io/<repo>/`), which is on the Supabase redirect
allow-list. For local use, add your local address to the allow-list or use the code from the email if it shows one.
