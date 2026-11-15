# Upgrade map: the 15 November master brief against the app as built

This is the first task the 15 November brief ("upgrade the existing app, do not rebuild") asks for. It says what already exists, what is partly there, what is demo-only and what is missing, with the file that proves it, and then lists the next phases.

**Status words used below:**
- **REAL**: works end to end with the live server or on the phone today.
- **PARTIAL**: built, with a known gap.
- **DEMO**: works only with built-in sample content or the offline demo brain.
- **MISSING**: not built.

**Files the brief refers to.** These five were **not received**: they are not in the uploads, the repo or Google Drive:
- `Parhlou_Easy_Guided_App.zip`
- `Parhlou_Operational_Master_Plan_Editable.md`
- `…Auto_YouTube_Discovery_Addendum_2026-11-15.md`
- `Parhlou_Premium_Exam_Readiness_Strategy_2026.pdf`
- `Parhlou_Master_Prompt_Upgrade_Existing_App_2026-11-15.md`

Anything that depends only on them is marked BLOCKED. The map below follows the brief's text as given in the conversation.

## 1. What exists today

### Student app (Android, `android/app/src/main/java/pk/parhlou/student`)

| Feature in the brief | Status | Where |
|---|---|---|
| Onboarding (board, class, subjects, pathway, study days, minutes per day) | REAL | `ui/screens/OnboardingScreens.kt`, `data/LocalStore.kt` |
| Email sign-in, guest mode, progress sync, export and delete account | REAL | `data/AuthClient.kt`, `data/SyncClient.kt`, `ui/screens/AccountScreens.kt`, `supabase/migrations/20261114000001_accounts_sync_owner.sql` |
| Live course content from the server, no app update needed | REAL | `data/RemoteContent.kt`, `data/ContentBundle.kt`, table `app_content` |
| Book → unit → section ("module") with 5 stages: Learn, Quiz, Solutions, Write, Improve | REAL | `ui/screens/ModuleScreens.kt` |
| Tutor-style lesson (recap, explain, worked example with "why", your turn with hints, exam tips) | REAL | `ui/screens/LessonScreens.kt` |
| Step-by-step textbook solutions ("Chegg" experience), browse and search, ask the tutor about a step | REAL (app) / PARTIAL (live) | `ui/screens/SolutionScreens.kt`. The full course is a draft and is not yet published (see §3). |
| "Book answer" from the textbook's key on every question that has one | PARTIAL | `data/Models.kt` `BookSolution.bookAnswer`; the data comes from `scripts/align-answers.mjs` (in progress) |
| AI tutor chat (Luna through the server; offline demo brain without it) | REAL | `ai/LunaGateway.kt`, `ai/DemoBrain.kt`, `ui/screens/TutorScreens.kt`, `supabase/functions/ai` |
| Written-answer marking with rubric (typed or scanned pages) | REAL | `domain/CheckMaster.kt`, `ui/screens/CheckMasterScreens.kt` |
| Answer-sheet scanner (camera with page edges, crop, gallery, reorder, up to 40 pages) | REAL | `android/scanner`, `ui/screens/TestScreens.kt` (`ExamScreen`) |
| Mock papers: quarter, half and full book, timed, with invigilator report | REAL | `domain/PaperAssembler.kt`, `domain/ExamTimer.kt`, `domain/Invigilator.kt` |
| Marked paper as PDF (share or save) | REAL | `domain/MarkedPdf.kt`, `ui/Share.kt` |
| Daily study plan: fix weak topics, spaced review, new lesson, paper | REAL | `domain/StudyPlanner.kt`, `ui/screens/PlannerScreens.kt` |
| Mistake notebook / weak topics | REAL (named "Weak topics") | `ui/screens/PlannerScreens.kt` (`WeakTopicsScreen`) |
| Spaced revision | REAL | `StudyPlanner.REVIEW_DAYS` |
| Continue button (picks up where the student left off) | REAL | `ui/screens/HomeScreens.kt` (`nextTask`) |
| Interactive week calendar with day summary | REAL | `HomeScreens.kt` (`WeekStrip`, `DaySummary`) |
| Journey (progress rocket that moves only on verified marks) | REAL | `HomeScreens.kt` (`JourneyScreen`), `domain/ProgressCalculator.kt` |
| Practice history | REAL | `HistoryScreen` |
| Teacher video per section (YouTube link) | PARTIAL | `Module.video` is shown in the Learn stage, but no videos are filled in for the course yet |
| App self-update from GitHub releases | REAL | `update/AppUpdater.kt` |
| Exam countdown | MISSING | — |
| Ten-minute "busy day" plan | MISSING | The planner already fits tasks to minutes per day; it needs a one-tap short plan |
| Retention check after Improve (re-test a few days later) | PARTIAL | Spaced review exists; there is no separate retention quiz |
| Notifications and reminders | MISSING | The bell icon on Home is decorative |
| Guardian (parent) view and reports | MISSING | "Add a parent" shows "Coming later" (`HomeScreens.kt`) |
| Billing, subscription and referrals | MISSING | — (PRD plan: PKR 3,999/month, D-009) |
| Appeals (student disputes a mark) | MISSING | The tutor can explain a mark; there is no formal appeal |
| Urdu interface | MISSING | English only |

### Owner panel (`owner-panel/`, GitHub Pages)

| Feature | Status | Where |
|---|---|---|
| Owner sign-in (owner email checked by the database) | REAL | `src/pages/SignIn.tsx`, `app_config.owner_email` |
| Dashboard (learners, activity, AI spend) | REAL | `src/pages/Dashboard.tsx` |
| Content: drafts, checks, publish, version history, rollback | REAL | `src/pages/Content.tsx`, `src/validate.ts` |
| Learners list | REAL | `src/pages/Learners.tsx` |
| AI log and cost monitoring | REAL | `src/pages/AiLog.tsx`, `src/cost.ts` |
| Settings: AI on/off, limits | REAL | `src/pages/Settings.tsx` |
| Audit-log view of owner actions | MISSING | Actions are checked server-side, but there is no screen listing them |
| Appeals queue, guardian and billing admin | MISSING | Depends on the student-side features above |

### Server (Supabase project `hulfrloizndafqchcwhz`)

| Feature | Status | Where |
|---|---|---|
| Row-level security, 80 database tests | REAL | `supabase/migrations`, `scripts/test-db-local.sh` |
| AI gateway: per-user limits, cost log, mark validation | REAL | `supabase/functions/ai` (14 tests) |
| Content publishing with versions and rollback | REAL | `publish_content`, `owner_publish_content` |

### Content (Class 9 Mathematics, Punjab 2025 book)

| Item | Status | Where |
|---|---|---|
| All 13 units: lessons, MCQs, written questions with rubrics, unit tests | REAL (draft) | `content/class9-maths/bundle.json` |
| Every example (195) and exercise question (1,011) with step-by-step solutions | REAL (draft) | `content/class9-maths/units/u-N-solutions.json`, `docs/coverage-class9-maths.md` |
| Every answer aligned with the textbook key and an independent teacher solution | IN PROGRESS | `scripts/align-answers.mjs` → `units/u-N-align.json` |
| Official 2026 paper pattern (pairing scheme) | PARTIAL | Uses the reported 75-mark pattern; the official scheme is being read |
| Exam kit per unit (definitions, formulas, important questions, how to write for full marks) | MISSING | Planned next |
| Unit tests for Units 5, 10 and 11 (graphs and constructions) | MISSING | Planned with self-checked drawing steps |
| Other subjects and Class 10 | MISSING | Same pipeline (`scripts/build-unit.mjs`, `build-solutions.mjs`) once each book is supplied |

## 2. Phases (incremental, nothing rebuilt)

| Phase | Goal | Work |
|---|---|---|
| 1. Preserve and stabilise | Keep everything that works | Keep the existing backend, design and tests. Run every suite on every change (app 115 tests, gateway 14, database 80, panel). Done each round. |
| 2. Guided experience | One obvious next step | Continue button, daily plan and week calendar are done. Next: exam countdown (date in Profile; shown on Home and in the plan), one-tap 10-minute busy-day plan, reminders. |
| 3. One subject complete and verified | Class 9 Maths | Publish the full course. Align every answer with the key. Official paper pattern, exam kit per unit, Units 5/10/11 tests. **In progress this round.** |
| 4. Exam readiness | Score 100/100 | Retention checks after Improve; full-book mocks on the official pattern; "most likely questions" from past papers. |
| 5. Trust and support | Parents and fairness | Guardian view (read-only progress and weekly report), appeals (student asks for a re-mark; AI re-marks with a second rubric pass; owner sees the queue), owner audit log. |
| 6. Business | Payment | Subscription and billing (provider to be chosen by the owner), referrals, trial. |
| 7. Expansion | More subjects and Class 10 | Repeat phase 3 per book; YouTube lesson finder (needs an API key). |

## 3. BLOCKED

- **The five referenced files** listed at the top were not received. Upload them to the chat or to Google Drive to unblock anything specific to them, such as the guided-app design and the YouTube discovery rules.
- **Publishing the full course.** The automated safety check would not let me publish to production on my own. The owner presses **Publish** in the owner panel (Content → pb-9-math), or gives explicit permission for me to run the publish. Live today: version 7, the old one-unit sample.
- **YouTube lesson finder.** Needs a YouTube Data API key, added through the secure credential form, never in chat.
- **Billing.** Needs a payment provider and merchant account chosen by the owner.

## 4. RISKS

- Students see nothing new until the course is published.
- Answers automatically checked against the key can still contain rare errors. Each question shows the book's answer and a note where the key itself is wrong, and anything unresolved shows only the book's answer.
- The 2026 pairing scheme comes from education websites, not the board itself. It is re-checked when the board publishes model papers.
- Self-checked drawing questions (Units 5, 10, 11) cannot be verified by the app. They stay labelled as practice and never count as verified marks (D-013).

## 5. OWNER DECISIONS needed

1. **Colour identity.** The brief asks for purple/lavender. The approved design is Calm Green (D-010). The app stays green until you choose.
2. **Human review.** The brief mentions teacher-approved content and human review. D-015 says fully automated, with no human anywhere. It stays automated until you choose; the owner panel already lets you inspect and roll back any version.
3. **Guardian, billing and referrals.** Confirm the scope and the order (phases 5 and 6), and the payment provider.
4. **YouTube.** Approve using the YouTube Data API (and supply a key) or keep hand-picked links.
5. **Publishing rights.** D-022 records your statement that Parhlou may use the textbook's content without naming its source. Please keep the written permission on file.
