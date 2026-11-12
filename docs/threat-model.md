# Threat model (Milestone 1 draft)

Scope: Android student app, Supabase (Auth + Postgres/RLS), future R2 scan storage, AI workers, owner panel. Status: **DRAFT, UNVERIFIED** beyond the database tests listed in the last column.

## Assets

Children's identity and contact data; marks, scans and progress; guardian links and consent records; hidden answer keys and unreleased papers; entitlements and purchase records; commission ledger; academic source files under licence; AI provider and service-role keys.

## Actors

Learner (minor), verified guardian, unverified/adversarial "guardian", assigned teacher, tenant admin of another school, academic editor, creator/referral partner, owner, backend worker, external attacker with a valid student account, compromised client app.

## Trust boundaries

1. Android client → Supabase API: **client is untrusted.** All authorization in RLS + server functions.
2. Server functions → AI providers: model output is untrusted input; schema-validated.
3. Server → R2: only short-lived, per-object, per-owner signed URLs.
4. Owner panel → database: role-checked, MFA, audited; no raw SQL console.

## Threats and controls

| # | Threat | Control | Verified by |
|---|---|---|---|
| T1 | Learner reads another tenant's marks/progress (PRD §29.2) | RLS deny-by-default; membership-scoped policies | `supabase/tests/database/rls.test.sql` |
| T2 | Unverified or revoked guardian reads a child's data (§29.13) | Guardian policies require `status = 'verified'` and the scope | `supabase/tests/database/rls.test.sql` |
| T3 | Learner reads answer keys or DRAFT content | Keys in a separate table with no learner policy; curriculum policies require `PUBLISHED` | `supabase/tests/database/rls.test.sql` |
| T4 | Learner writes their own marks/entitlements | No insert/update policies for authenticated users; only service role / security-definer RPCs | `supabase/tests/database/rls.test.sql` |
| T5 | Evidence history rewritten | Append-only triggers on `learning_events`, `audit_logs`, `consent_events` | `supabase/tests/database/rls.test.sql` |
| T6 | Paper with wrong mark totals published | `validate_blueprint` + publish check | `supabase/tests/database/rls.test.sql` |
| T7 | Client fakes a correct quiz answer | `answer_quiz_item` scores server-side; first attempt preserved | `supabase/tests/database/rls.test.sql` |
| T8 | Service-role/AI keys shipped in app | Keys never in Android code or assets; CI secret scan (todo) | Manual review (todo) |
| T9 | Public scan URLs | Private bucket, short-lived signed URLs | Gate 2b (todo) |
| T10 | Duplicate billing on 25-page retries | Idempotency keys on uploads/jobs | Gate 2b/5 (todo) |
| T11 | Cross-board retrieval in tutor | Retrieval filtered by edition before vector search | Gate 4 (todo) |
| T12 | Shared-phone leakage on a family device | App lock option, sign-out clears local drafts | Gate 2 (todo) |
