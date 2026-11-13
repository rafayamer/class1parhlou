# Parhlou database (Supabase)

- `migrations/` — schema, row-level security, server functions (`start_quiz`, `answer_quiz_item`, `validate_blueprint`).
- `seed.sql` — curriculum skeleton only, all `DRAFT` and unverified. No textbook content.
- `tests/database/rls.test.sql` — 34 pgTAP checks: tenant isolation, guardian verification/revocation, hidden answer keys and drafts, no client-written marks or entitlements, append-only evidence, blueprint mark totals, server-side quiz scoring.

## Run the tests

With Docker and the Supabase CLI:

```bash
supabase start && supabase test db
```

Without Docker (PostgreSQL 16 + pgTAP, uses `tests/local/auth_stub.sql` in place of Supabase Auth):

```bash
./scripts/test-db-local.sh
```

Status: verified with the local runner only. Not yet run against a hosted Supabase project.

## AI gateway (`functions/ai`)

Calls GPT-6 Luna for the tutor and Check Master. The OpenAI key lives only here, as a server secret.

- `core.ts`: prompts, JSON schemas, validation, retry. `handler.ts`: looks up questions and keys on the server. `index.ts`: HTTP and database wiring, app-key check, per-install limit and project-wide daily cap.
- `content/sample.json` is generated from the app's sample content: `cd android && ./gradlew :app:testDebugUnitTest --tests '*ContentExportTest' -PcontentExport=../supabase/functions/ai/content/sample.json`
- Offline tests: `node --experimental-strip-types --test supabase/functions/ai/core.test.ts`
- Live checks (needs `OPENAI_API_KEY`): `node --experimental-strip-types supabase/functions/ai/live.ts`. Results are in `docs/ai-test-log.md`.

### Live project

- Project ref `hulfrloizndafqchcwhz` (`https://hulfrloizndafqchcwhz.supabase.co`), installed on 2026-11-13 with `scripts/deploy-supabase.sh`.
- 29 tables, row-level security on all. Checked with the publishable key: answer keys, book solutions, AI usage and entitlements return nothing.
- Function `ai` deployed (verify_jwt off; it checks the app key itself). Secrets: `PARHLOU_APP_KEY` is set; **`OPENAI_API_KEY` must be added by the owner** in the dashboard. Optional: `PARHLOU_DAILY_AI_CALLS` (default 300 per install), `PARHLOU_DAILY_TOTAL_AI_CALLS` (default 2000 for the project).
- Re-deploy: `SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/deploy-supabase.sh hulfrloizndafqchcwhz function`

### Changing content or settings live

- Content: edit the content, then `SUPABASE_ACCESS_TOKEN=sbp_... ./scripts/publish-content.sh hulfrloizndafqchcwhz`. It publishes a new version; phones download it the next time the app is opened or brought to the front, and the AI function uses it within 30 seconds.
- Settings (table `app_config`): `ai_enabled` (true/false), `daily_calls_per_install`, `daily_calls_total`. Change them in the Supabase table editor; the function follows within 30 seconds.

### Going live (new project)

1. Create a project at supabase.com (name `parhlou`, region Mumbai or Singapore).
2. `supabase link --project-ref <ref>` then `supabase db push`.
3. In the dashboard: Edge Functions → Secrets → add `OPENAI_API_KEY`.
4. `supabase functions deploy ai`
5. Build the app with `-PparhlouAiUrl=https://<ref>.supabase.co/functions/v1/ai -PsupabaseAnonKey=<anon public key>`.

Never commit the OpenAI key, the service_role key or the database password.
