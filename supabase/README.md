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
