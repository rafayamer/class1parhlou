# Founder decision log

Newest decisions first. A newer decision supersedes the PRD ([`PRD.md`](PRD.md)) and any older entry where they conflict. Each entry should also be copied into the A-to-Z Master Guide (not yet in this repo).

| ID | Date | Decision | Supersedes / notes |
|---|---|---|---|
| D-009 | 2026-11-12 | **Launch scope follows the PRD as written:** Punjab Board, Classes 9 and 10, founder-selected academically verified subjects, one PKR 3,999/month plan with Complete Academy and Test Series pathways. | Replaces D-004 ("Class 9 Maths only"). Which subjects are first is still open (PRD §31.1). |
| D-008 | 2026-11-12 | **Milestone 1 builds backend core and Android foundation in parallel**, alongside Gate 0 documents. | Deviates from strict PRD gate order. Backend RLS tests must pass before any real student data or AI calls. |
| D-007 | 2026-11-12 | **PRD saved in the repo** as the planning baseline (`docs/PRD.md`). | — |
| D-006 | 2026-11-12 | **Parhlou does not record its own lecture videos.** Lessons link existing teacher videos/playlists on YouTube via the official player or "open in YouTube", with attribution (PRD §7 Stage A). Videos are optional for Test Series and never count as mastery. | — |
| D-005 | 2026-11-12 | **Textbook pages are references only** (title, edition, unit, printed page, section, exercise). Book pages are not shown in-app unless written distribution rights are obtained (PRD §7 Stage C). Parhlou writes its own step-by-step solutions. | Showing full question text from the book also needs rights; open item O-3. |
| D-004 | 2026-11-11 | ~~Launch Class 9 Mathematics only.~~ | **Superseded by D-009.** |
| D-003 | 2026-11-12 | **The 2018 eLearn.Punjab Class 9 Maths PDF (17 chapters) is treated as superseded.** Research indicates Punjab uses a single new Class 9 Mathematics textbook (National Curriculum 2023, from session 2025-26) with 13 units. | Must be confirmed against the official textbook board edition before any content work. See [`sources-and-rights.md`](sources-and-rights.md). |
| D-002 | 2026-11-11 | **Product emphasis:** the book as the learning base, Chegg-style step-by-step solutions, quizzes, full-length papers, mock tests and the Test Series. | Consistent with the PRD; no new pricing tier. |
| D-001 | 2026-11-11 | **Android app is native** (Kotlin + Jetpack Compose) as proposed in PRD §23. | Formal sign-off still listed in PRD §31.7. |

## Open items (from PRD §31 and the conversation)

| ID | Open item | Owner |
|---|---|---|
| O-1 | First complete subject(s) for Class 9 and Class 10, exam cohort/session and exact textbook editions. | Founder |
| O-2 | Official pairing scheme / paper blueprint per subject (Maths: 75 marks = 15 MCQ + 60 subjective reported; short/long split unconfirmed). | Founder + academic reviewer |
| O-3 | Rights for textbook question text, past papers and any teacher-video transcripts. | Founder |
| O-4 | Marking source hierarchy, partial-credit and appeal rules, human calibration benchmark. | Academic reviewer |
| O-5 | Whether pathway can change mid-term; shared usage counters; per-task usage limits from real cost data. | Founder |
| O-6 | Guardian verification process; social features deferred until after pilot? | Founder |
| O-7 | Offline retention, deletion/export and backup policy sign-off. | Founder + tech lead |
| O-8 | Referral discount/commission rules and payout timing. | Founder |
| O-9 | Approved UI reference (Easy Guided ZIP / images) and A-to-Z Master Guide to be added to the repo. | Founder (promised next) |
