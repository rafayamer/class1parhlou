# Founder decision log

Newest decisions first. A newer decision supersedes the PRD ([`PRD.md`](PRD.md)) and any older entry where they conflict. Each entry should also be copied into the A-to-Z Master Guide (not yet in this repo).

| ID | Date | Decision | Supersedes / notes |
|---|---|---|---|
| D-016 | 2026-11-13 | **Content follows the book exactly.** Board exams reward reproducing the book's definitions, worked examples and end-of-chapter problems, so questions and marking keys come from the book itself (the "book bank"), not invented questions. Worked examples use the book's solution; exercise solutions are AI-drafted, re-solved by a second independent AI pass and must match the book's answer key before publishing. | **Replaces D-005 ("references only").** Showing book-exact questions in a paid app needs the textbook board's permission before selling. Blocked until the current Class 9 Maths book PDF is supplied. |
| D-015 | 2026-11-12 | **Parhlou is fully automated: no human teacher, tutor, reviewer or marker anywhere.** Human academic review, human escalation and human appeals in the PRD and the Luna instructions are replaced by automated steps: a second independent AI check, recomputed maths and totals, and student-triggered re-checks. | Accepted risk: with nobody qualified checking, a wrong key or mark can reach students. Marks stay described as practice marks. |
| D-014 | 2026-11-12 | **Invigilator scope for the prototype: report only.** It records time used and each time the student leaves the paper, and shows a report afterwards. No warnings, blocking or camera. The report stays on the phone. | Camera invigilation would need consent for minors; not built. |
| D-013 | 2026-11-12 | **Demo-checked or self-checked marks never count as verified progress.** They show as "Demo-checked"; the Journey rocket moves only on verified marks. | Follows PRD §9. |
| D-012 | 2026-11-12 | **Target AI model: GPT-6 Luna (`gpt-6-luna`) through the OpenAI Responses API**, per [`AI-INSTRUCTIONS.md`](AI-INSTRUCTIONS.md). The OpenAI key stays on a server, never on the phone. **This round runs on an on-phone demo brain only**; Luna is connected later behind the same `ParhlouAi` interface. | Model existence checked on OpenAI's model page on 2026-11-12. Not yet called or tested. |
| D-011 | 2026-11-12 | **iOS-style glass bars and app-wide animation.** Glass comes from the vendored open-source library PrismalAGSL (`android/prismal`, see its `UPSTREAM.md`). Animations respect Android's "Remove animations" setting. **Minimum Android raised from 7.0 to 7.1 (API 25)** because the library requires it. | Library licence: repo file MIT, source headers Apache-2.0; both permissive, both notices kept. |
| D-010 | 2026-11-12 | **Visual design: "Calm Green"** from the founder's reference image 1 (sage background, lime accents, dark pill navigation), in **light and dark** themes, with layout ideas from reference 2. See [`design/README.md`](design/README.md). | Replaces the purple/lavender baseline in PRD §5. "Pro" upsell, XP and streaks from the references are not used. |
| D-009 | 2026-11-12 | **Launch scope follows the PRD as written:** Punjab Board, Classes 9 and 10, founder-selected academically verified subjects, one PKR 3,999/month plan with Complete Academy and Test Series pathways. | Replaces D-004 ("Class 9 Maths only"). Which subjects are first is still open (PRD §31.1). |
| D-008 | 2026-11-12 | **Milestone 1 builds backend core and Android foundation in parallel**, alongside Gate 0 documents. | Deviates from strict PRD gate order. Backend RLS tests must pass before any real student data or AI calls. |
| D-007 | 2026-11-12 | **PRD saved in the repo** as the planning baseline (`docs/PRD.md`). | — |
| D-006 | 2026-11-12 | **Parhlou does not record its own lecture videos.** Lessons link existing teacher videos/playlists on YouTube via the official player or "open in YouTube", with attribution (PRD §7 Stage A). Videos are optional for Test Series and never count as mastery. | — |
| D-005 | 2026-11-12 | ~~**Textbook pages are references only** (title, edition, unit, printed page, section, exercise). Book pages are not shown in-app unless written distribution rights are obtained (PRD §7 Stage C). Parhlou writes its own step-by-step solutions. | Showing full question text from the book also needs rights; open item O-3. |
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
| O-9 | A-to-Z Master Guide to be added to the repo (UI reference images received, see D-010). | Founder |
