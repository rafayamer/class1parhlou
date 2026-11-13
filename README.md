# Parhlou (پڑھ لو)

Exam-focused learning academy for Pakistani students: Android student app + owner panel on a shared Supabase backend.

**Status: Milestone 1 foundation — not a production application.** See [`docs/PRD.md`](docs/PRD.md) for the specification and [`docs/decisions.md`](docs/decisions.md) for founder decisions that supersede it.

| Folder | What it is |
|---|---|
| `docs/` | PRD, decision log, source/rights register, coverage-map template, threat model |
| `supabase/` | Database migrations, row-level security, server functions and pgTAP tests |
| `android/` | Kotlin + Jetpack Compose student app (sample data only); `android/scanner` is the answer-page scanner |
| `owner-panel/` | Owner web panel (React + Supabase), published to GitHub Pages |
| `scripts/` | Database tests, Supabase deploy and content publishing |

## Checks

```bash
cd android && ./gradlew :app:testDebugUnitTest :scanner:testDebugUnitTest
```

```bash
./scripts/test-db-local.sh
```

```bash
node --experimental-strip-types --test supabase/functions/ai/core.test.ts
```

```bash
cd owner-panel && npm ci && npm test && npm run build
```

Live end-to-end check against the real project (creates and removes throwaway accounts; needs a Supabase access token):

```bash
cd owner-panel && npx vite build && SUPABASE_ACCESS_TOKEN=... node --use-env-proxy e2e/live.mjs <project-ref>
```

Third-party code and licences: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
