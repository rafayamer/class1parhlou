# Parhlou (پڑھ لو) — Developer-Ready Product, Business and Functional Specification

> Saved verbatim from the founder's message of 12 November 2026 as the planning baseline. Formatting was converted to Markdown; wording is unchanged. Later founder decisions are recorded in [`decisions.md`](decisions.md) and supersede this document where they conflict.

**Document type:** Product Requirements Document (PRD) + Functional Specification + Initial Technical Handoff
**Status:** PLANNING BASELINE — NOT AN IMPLEMENTED OR TESTED PRODUCTION APPLICATION
**Date:** 12 November 2026
**Foundational references:** Parhlou_Operational_Master_Plan_Editable.md (A-to-Z Master Guide, operational source of truth) and Parhlou_Easy_Guided_App.zip (approved visual/UI prototype).

**Rule for developers:** The two references take precedence over assumptions in this implementation brief. New explicitly approved founder decisions supersede earlier assumptions and must be added to the decision log. Do not treat prototype illustrations, unofficial PDFs or proposed thresholds as approved academic policy.

## 1. Executive overview and exact product scope

Parhlou is a mobile-first AI-powered, examination-focused home learning academy for Pakistani students. It should replace much of the academic support traditionally obtained through expensive private tuition by combining approved teacher instruction, exact curriculum-linked reading, adaptive exercises, written practice, realistic timed assessments and explainable paper marking. Parhlou cannot replace school admission, officially administered board exams or a student's own independent effort; it must not guarantee grades.

Build one shared platform with two connected core products:

1. **Parhlou Student App:** a genuine, Play Store-distributed Android application providing enrollment, a personalized learning path, teacher video lessons, MCQs, textbook reading, written work, AI tutoring, timed examinations, camera scanning, corrections, progress, revision, guardian consent and account/subscription management.
2. **Parhlou Business / Owner Master Panel:** a secure, browser-based no-code academic and operational console for the founder and appropriately assigned staff. It controls academic source intake, curriculum creation, teacher video mapping, question and rubric review, publication, tenants, purchases, guardian permissions, grading appeals, model settings, budgets, staff and partnerships.

Both consume the same authenticated backend, tenant policy, academic content registry, versioned question/rubric resources and assessment records. Guardian, assigned-teacher, reviewer, creator and institution views are role-restricted extensions, not necessarily separate downloadable apps.

**First paid release:** Punjab Board, Classes 9 and 10, only founder-selected and academically complete verified subjects, one PKR 3,999/month subscription. Within that one product, students choose Complete Academy or Test Series as educational pathways; these are not two automatically different prices. Build all board/edition data as versioned curriculum packs so Punjab 11–12, FBISE, KPK and entrance examinations such as NUST can be added later without cloning the Android app or mixing assessment rules.

**Current reality:** the supplied ZIP contains an attractive interactive HTML/CSS/JavaScript UI prototype, visual preview images and a small local Python development server. Its existing flow and assets are the approved design baseline; it is not a secure production Android binary, completed multi-tenant platform, legally cleared academic repository or validated 25-page board examiner. No developer may mark planned features as completed solely because a prototype screen exists.

## 2. Success criteria and non-negotiable principles

Parhlou should enable a student to register, select a valid curriculum, receive a realistic daily plan, learn a module, perform teacher-approved objective checks, study identifiable textbook pages, write an answer, complete a real chapter assessment, scan and submit handwritten pages, obtain justified criterion-level marks, correct errors and be reassessed on weak topics.

Non-negotiables:

- **Academic correctness:** curriculum identity and exact textbook edition enforced by backend; no Federal mark scheme for a Punjab assessment; no fabricated page, exam trend, 'board examiner secret' or past-paper occurrence.
- **Evidence-based progress:** viewing, practising, completing and mastering are different states. Video watch/reading checkmarks do not equal an assessed achievement.
- **Human-reviewed publishing:** AI may draft source-linked exercises, but no unreviewed question keys, exams or rubrics become publicly scored content.
- **Private student data and safeguarding:** strict tenant isolation, verified guardian scopes and minimal child data; no public marks/identity exposure by default.
- **Transparent AI:** sourced teaching; explainable grading; ambiguous handwriting and valid alternative methods receive visual recheck or qualified human escalation.
- **Financial sustainability:** model, upload, support, academic and refund costs counted against actual subscription receipts. Accuracy is never traded away to satisfy a quota.
- **Low-friction Android UX:** one main action per screen, readable explanations, offline drafts, reliable camera flow and support for entry-level devices.

## 3. Users, roles and permissions

| Role | Primary capabilities | Access restrictions |
|---|---|---|
| Learner | Enroll, study, practise, sit tests, scan papers, view own marks, ask tutor, manage privacy | Only their own data and their allowed curriculum |
| Verified guardian | Approved weekly progress reports, relevant scores, constructive recommendations, consent management | Only linked child and explicitly authorized report scopes; cannot edit marks |
| Assigned teacher | Approved assigned learners' permitted progress, feedback and academic support | Cannot browse unrelated learners/tenants or change official rubrics |
| Academic editor/reviewer | Source verification, question/rubric review, publish approvals as granted | Cannot change billing/permissions; cannot self-approve restricted content if policy requires second person |
| Content creator / referral partner | Authorized lecture/contract records and eligible aggregate referral earnings | No personal student records or paper scans |
| School/family tenant administrator | Manage membership, subject access and permitted organizational summaries | Cannot assume guardian identity; no access to other tenants |
| Founder/owner | Global business settings, academic workflow, tenant controls, finance, compliance, supervised moderation | Strong authentication and audit; privileged actions require role checks and appropriate approval |
| Backend worker | Narrow approved grading, delivery, billing and maintenance tasks | Service account scoped by purpose; no user-facing unrestricted credentials |

Use Supabase Auth for account identity, PostgreSQL membership tables and Row Level Security (RLS) to enforce authorization, plus independent checks in backend functions. A student can belong to an individual/family tenant and later an organization by explicit membership. Teachers, guardians and partners receive delegated permissions; a tenant role is not equivalent to consent or ownership of a child.

## 4. Student journey — first launch and onboarding

The first launch must mirror the approved easy-guided visual flow: welcome/brand, a short four-step setup, simple large choices, icon+text labels, one prominent Continue control and minimal overload.

**Step 1 — Account, age/consent and first-use explanation.** Introduce the learning academy in plain English/appropriate supported language. Create an account with permitted sign-in methods, collect only necessary information, request guardian authorization when required, explain how data and scans are used. Students must not gain paid AI access merely from a local flag.

**Step 2 — Academic identity.** Choose Punjab Board, Class 9 or 10, approved examination session/cohort, school subject group, preferred language/medium and subjects available in that edition. Board options not yet released may appear as disabled 'Coming later', never as selectable fake courses. App must not guess a new textbook edition from a user-entered grade alone.

**Step 3 — Educational pathway.** Explain Complete Academy (structured full learning) versus Test Series (assessment-focused), what differs operationally, and that the launch price is a single monthly product. A learner may change pathways through a documented rule; changing does not manufacture completion of skipped lessons.

**Step 4 — Availability and plan.** Ask realistic study days/time, anticipated exam date if verified, and optional personal goal. Calculate workload and show an honest feasibility message; a late starter should receive an accelerated diagnostic-focused route rather than an unrealistic promise to cover everything.

**Completion:** Save profile and enrollment to backend, issue curriculum and entitlement IDs, prepare personalized planner and route to home. Persist partial onboarding, allow non-destructive back navigation, and support timeouts/poor network. If an option lacks approved content, show unavailability without falling back to another board.

## 5. Home screen and navigation (approved visual baseline)

The home screen must answer 'What should I do now?' within a few seconds. Keep the approved purple/lavender soft-glass branding, large labeled icons, readable opaque study sheets and friendly rocket visualization; avoid excessive translucent layers over text. It should contain:

- One primary Continue card/button that opens the highest-priority available and permitted task (module, revision, unfinished written answer or next assessment).
- Three prominent shortcuts: subjects/courses; practice/tests; journey/progress (exact labels may be finalized in UX review).
- Today's plan: time estimate, top priorities, due reviews, optional additional tasks and an understandable explanation of recommendation.
- Subject snapshot: individual progress by subject, chapter currently active, weak areas and next relevant exam.
- Journey/rocket: current milestone, verified achieved milestones, approach toward the next incomplete milestone.
- Account and subscription: remaining permitted assessments/tutor allowance, expiry/renewal state and settings.
- Guardian setup entry: visible without exposing protected marks before verification.

**Navigation:** Welcome -> Onboarding -> Home -> Subjects -> Subject -> Chapter -> Module/Assessment -> Result/Repair -> Home. Secondary routes: Tutor, Planner, Journey, Test Series, Scan/Marked Papers, Guardian, Subscription, Profile/Privacy. Android back and system interruptions must preserve drafts. An inaccessible or paid-locked action explains why rather than failing silently.

## 6. Course hierarchy and curriculum release model

The academic system is not a set of hard-coded screens. Its canonical hierarchy is:

Board -> Grade -> examination session/cohort -> textbook edition -> language/medium -> subject -> chapter -> module -> concept/learning objective -> exact textbook section/page/exercise.

Maintain a separately versioned set of question items, paper blueprints and marking rubrics tied to these academic IDs. Global shared concepts (e.g., basic algebra) may be reused as general explanations, but a specific Punjab textbook question and its examination rubric stay bound to that Punjab pack.

Subject screen: show subject identity, edition, chapter list, next chapter and verified progress. Chapter screen: expand the current chapter by default; older/future chapters may be browsed deliberately; display clear module order, five-step completion indicators, revision status and eligibility for the chapter paper. Modules vary in number and effort with the actual textbook; the prototype's illustrative ten modules per chapter must not be interpreted as mandatory.

Content statuses: DRAFT -> ACADEMIC_REVIEW -> APPROVED -> PUBLISHED; additionally SUSPENDED/RETIRED and immutable historical versions. For every published chapter maintain a coverage matrix mapping all examinable sections, examples, objectives and textbook exercises to approved teaching, practice and assessment evidence. Do not advertise a 'complete course' until this audit passes.

## 7. Complete Academy module flow — exact student behavior

Each module is an instructional unit, not simply a video. Show a five-stage rail matching the approved prototype. Student can reopen earlier stages; recorded learning evidence remains accurate. Suggested stages:

### Stage A — WATCH / LEARN

Show a teacher-approved, objective-matched YouTube lecture in an official permitted embed, with teacher attribution, duration, description, and clearly identified topic/lesson segment. If rights and creator policies allow, provide timestamps and legitimate authorized captions. Never automatically download/scrape a public video's captions merely because the video is embeddable, and never charge for or block ordinary freely available YouTube playback. When embeds fail, offer legitimate open-in-YouTube playback and a separately approved textbook/notes study alternative. Record that the student opened or confirmed studying a video, but never call viewing alone 'mastered'.

### Stage B — UNDERSTAND (10–15 approved MCQs)

After instruction, give normally 10–15 MCQs, selected from a reviewed reusable question pool aligned to the lecture's authorized transcript or independently reviewed matched lesson notes. Provide one defensible correct answer, plausible distractors, objective tags, explanatory feedback for all choices and a clear retry route. The question bank is generated offline/in review, not via a fresh expensive model call for every student's every tap. At quiz start, server selects an approved set and stores the item revisions; check answers on server, log first-attempt performance and later corrections separately, detect misconception tags and suggest exactly relevant video sections. Low MCQ accuracy triggers remedial action according to configurable academic rules rather than a fabricated chapter score.

### Stage C — READ / RECALL THE BOOK

Display the exact approved textbook title, edition, chapter, printed page range, section and exercise number for the module. Give two or three specific tasks: read section, recall a formula/definition without looking, reconstruct an explanation or solve a named example. Provide book pages in-app/offline only with documented distribution rights; otherwise deep-link lawfully to the source or use licensed editorial material. Reading confirmation is a study checkpoint, not assessed mastery. No fake page number if the source extraction lacks verification.

### Stage D — PRACTISE WRITING

Ask one or two short written prompts that require active recall and application. Students can type on a phone initially; subject-appropriate work (maths proofs, formula solutions, diagrams) can also use a structured entry or the camera workflow. Draft answers autosave. The rubric scores meaningful evidence (method, key concept, unit, reasoning, terminology) and can accept accurate alternative language/solutions. Show the student's response and specific correction rather than only 'Correct/Incorrect'. Uncertain handwriting requires review, not an unearned zero. Separate quick formative marks from a formal paper grade.

### Stage E — IMPROVE / RETRY

Summarize what the student demonstrated, list specific misunderstanding(s), recommend one short targeted explanation and one fresh equivalent practice question, and show readiness for the next step. Update the student learning model only from verified evidence. A new answer must be submitted to resolve weakness. Persist the module as learning/practising/ready/assessed/mastered/review-due according to policy; no tap-based rocket advancement.

**Module completion:** A backend rules engine evaluates required approved learning evidence; authorized educator-configurable thresholds govern formative step completion. A student may revisit any prior activity, but original scores and evidence timestamps are never overwritten. All steps show clear 'save/resume/try again' states.

## 8. Specialist treatment by subject

One universal text comparer is unacceptable. Expose subject-specific activity and rubric types:

- **Mathematics:** equations, numerical calculations, proofs and geometric construction; verify logical working, algebra, signs, formula correctness and valid alternative methods.
- **Physics:** concept explanations, formulas, calculation steps, SI units, significant values where academically required, graphs and experimental reasoning; don't deduct for a unit unless its rubric criterion applies.
- **Chemistry:** symbols, balanced equations, valency, reactions, state symbols when specified, stoichiometry and correct chemical notation.
- **Biology:** diagrams and labels, process ordering, correct functions, distinctions, definitions and appropriate accepted synonyms.
- **English/Urdu/other approved languages:** comprehension, grammar, organized answer construction and content-specific expression; don't impose a rigid phrase match as the whole scoring method.

The academic editor selects supported formats and rubric versions per assessment. If the specialist scorer is not validated yet, label the question as practice-only or hold it for human review.

## 9. Progress engine — learning, practising, assessed and mastered

Treat progress as an append-only event history, not one editable percentage. Example events: module_opened, video_study_confirmed, quiz_answered, quiz_completed, reading_checked, written_submitted, written_feedback_accepted, exam_started, exam_marked, appeal_decided, remediation_passed, review_due. Every event references learner, tenant, publication version, objective IDs and idempotency key.

Derived learning states include NOT_STARTED, LEARNING, PRACTISING, READY_FOR_ASSESSMENT, ASSESSED, MASTERED, REPAIR_REQUIRED, REVIEW_DUE and PENDING_REVIEW. Academic progress and interface completion are different measures; a student can have watched every video yet still show incomplete mastery. When the examiner revises a mark, append a correction event and rebuild affected progress and reports, preserving the initial result history.

**Milestones and rocket:** construct balanced effort-weighted bundles of assessed learning across all enrolled subjects, not a simple fixed number of chapters. The student with Mathematics 16 chapters and another subject with 9 chapters must not have unfair progress weighting. Each bundle has required objective/assessment thresholds. Rocket position may advance visibly toward the next unfinished bundle as partial verified evidence accumulates; the destination cannot be labeled achieved until all required evidence qualifies. Past achieved bundles persist during timetable changes.

## 10. Smart daily planner and revision scheduling

Inputs: actual selected subjects, verified chapter/objective coverage, module effort estimates (video + quiz + reading + written work), pending examiner feedback, due spaced reviews, student study availability and official exam dates when confirmed. Outputs: one primary next task, a realistic short queue, study duration, due review and rationale for ordering.

Schedule weak concepts and imminent reviews ahead of endless new material where justified; alternate demanding and lighter subjects, avoid multiple full-length examinations on a typical school night, and replan after missed sessions. Target teaching completion 10–12 weeks before the actual board examinations when feasible, leaving revision/mock time; if the remaining content makes this impossible, show an honest compressed strategy. Mark provisional dates as provisional. The planner cannot award marks or study evidence simply because a calendar block passed.

Spaced revision should use fresh unseen retrieval questions, revisit persistent misconception tags and adapt intervals based on assessed outcomes. Notify discreetly; no manipulative or punitive streak rules.

## 11. Test Series pathway — assessment without compulsory lectures

Students choosing Test Series receive the same correct board/grade/edition validation and paid entitlement checks, but their next action points to approved paper scopes, not an obligatory lesson video. Before a major assessment, give 5–10 short, pre-approved readiness MCQs targeted to the assessment's objective coverage. Explain the pass criterion in advance. Passing unlocks the assessment; failure offers a brief targeted review and equivalent retry without consuming a full paper grading credit. A passed diagnostic demonstrates assessment readiness, not completion of missing video/book/written learning steps.

Test Series learners may optionally watch free teacher videos, use the textbook and request tutoring according to their entitlements. They receive the same objective-based weakness analysis after a real scored paper. Switching track preserves genuine completed assessment records, with any uncompleted Academy modules clearly distinct.

## 12. Chapter assessment (90 minutes) — creation and sitting

After a chapter's readiness and required published coverage are satisfied, offer a configurable 90-minute chapter examination. The example of approximately ten short answers and one long answer is illustrative: actual item counts, total marks, short/long ratios and specialist tasks must come from the exact approved board/subject/examination-cohort paper blueprint or a clearly labeled teacher-reviewed practice blueprint. Do not hard-code one board layout across all subjects.

Assessment creation: backend verifies active learner/consent, published blueprint, permitted board/grade/edition, subscription, quota and any readiness gate; reserves the credit atomically, then snapshots question/item/rubric/marks version. Before Start show duration, total marks, material rules, expected scanning process and accessibility accommodations. Once started, timer is based on a trusted server start timestamp with bounded offline handling. Paper is a set of numbered sections and items, with question-specific prompts, optional diagram spaces and marking maximums. Autosave typed or locally photographed work. On timeout/submit, finalize the attempt under clear rules, without secretly editing or replacing the active paper blueprint.

Provide recovery states for app closure, phone calls, weak internet, postponed uploading and lost drafts. Prohibited attempts and duplicate submissions should not incur repeated billing charges.

## 13. Quarter-book, half-book and full-book examination engine

A curriculum editor defines cumulative scope using verified chapters/objectives and approved published paper blueprints. 'Quarter', 'half' and 'full' refer to curriculum coverage and objective weights, not necessarily the first 25%, 50% and 100% of PDF pages. Select questions from reviewed approved banks respecting subject-specific content distribution, difficulty, total marks, option rules, diagrams and practical elements. Paper variants must be independently checked for duplicate questions, incorrect answer keys and over-represented objectives. Version and freeze the paper upon attempt creation.

If students request an early mock before finishing the syllabus, label it clearly as an early diagnostic; the score does not certify unattempted chapters as learned. Display marked history and ongoing growth instead of overriding old paper scores.

## 14. Real Android handwriting scanner — 25-page workflow

The Android app must use native CameraX for acquisition. Adapt approved UI appearance, but implement full native camera permission and file-handling behavior. Evaluate a locally integrated MIT jscanify crop/perspective workflow only where practical, with a vetted native/OpenCV or manual-corner-adjust fallback and licence/dependency checks.

Student flow:

1. Open own started assessment -> Scan handwritten answers.
2. Camera preview gives readable lighting/edge feedback and takes page 1; store raw original in app-private local storage immediately.
3. Attempt boundary detection; show adjustable corner handles, rotation, crop preview and retake/keep choice. Never silently cut off written marginal steps.
4. Add page after page (at least 25), viewing thumbnails with sequence numbers; allow reorder, delete, replace, rename/assign question labels, split sections and return later.
5. Detect obvious blur, glare or missing corners locally; warn rather than permanently reject a still-readable page.
6. Generate local original PDF and corrected-preview PDF (where appropriate), keep ordered image hashes and restore state after process death/app restart.
7. When online, request private signed per-page upload targets and upload missing pages with resumable/idempotent progress. Do not upload a batch twice just because page 17 failed.
8. Finalize the manifest only after user confirmation of order/completeness; server checks ownership, content type, count, size, quota and received hashes. Submit asynchronous marking job.
9. Show processing/pending, then marks and visually positioned notes; assemble an annotated marked PDF on the learner's phone, retaining the readable originals. Provide export/share under user control and explain local storage/backup implications.

Use short-lived private Cloudflare R2 copies of scan images for processing. No public scan links. Apply published retention and scheduled purge, including receipts and recovery-window communication; server retains compact grade/evidence references and audit data as lawfully required. Student deletion and changing-device behavior must be specified.

## 15. AI examiner — precise, defensible marking workflow

The grader is a pipeline, not 'send 25 photos to one chat prompt and trust the score':

1. **Preflight:** verify identity, tenant, assessment state, exact pinned paper/rubric, consent, entitlement and available cost reservation.
2. **Image QA:** validate order/rotation/quality; identify question labels and spans using vision. Keep original crop/source regions available.
3. **Visual extraction:** transcribe handwriting, numerical working, formulas, diagrams, labels, tables and crossed-out text with page+region references. Return explicit unknown or unreadable flags; do not guess digits or units.
4. **Question matching:** associate answer regions with the correct issued question, approved answer key and rubric version. Avoid assessing a wrong question against a different subject.
5. **Criterion-level marking:** for each criterion, record maximum marks, awarded marks, relevant evidence, accepted alternatives and rationale. Distinguish correct method/incorrect final arithmetic; award fair partial credit according to rubric.
6. **Verification:** independently recompute math, equation balance, mark totals and exact diagram/negative-sign regions where needed. Reopen original image if extraction contradicts likely evidence.
7. **Confidence gate:** ambiguous student work, conflicting graders or uncertain source region -> PENDING_REVIEW, selective stronger-model recheck or academic human escalation. Never impose an automatic penalty for unreadable scanning.
8. **Results:** return every question's marks, what was correct, WHAT was wrong, WHY marks may be deducted under the identified rubric, HOW to fix it, corrected worked answer, weakness IDs and retest recommendation. Label clearly as AI-assessed practice marks / teacher-reviewed practice rubric unless a particular official criterion is verifiably sourced.
9. **Recheck and appeals:** user disputes a criterion with reason/rescan; backend routes original region to review, records append-only corrections, recalculates totals/learning plan and notifies permitted guardians if a prior report is superseded.

Benchmark with independently human-marked real-world sample scripts, multiple handwriting styles, diagrams, valid alternative solutions and ambiguous signs; do not launch graded exam marketing without an academic sign-off. The 20% extra recheck/retry allowance is cost planning only, not permission for 20% incorrect scoring.

## 16. Parhlou AI Tutor — contextual teaching experience

The student can open a floating or dedicated Tutor from a lesson, a textbook page, an MCQ explanation, a practice answer or a marked exam mistake. Each entrypoint provides context metadata from the trusted backend: learner's published board/grade/edition/cohort, subject, chapter, module/objective, legal source scope and explicit question. The AI must not be able to change its own retrieval/permissions based on student prompt text.

For every question, backend first performs curriculum/entitlement/quota checks, searches allowed academic content, then produces an age-appropriate answer. Response structure: direct answer -> intuitive explanation -> worked steps if relevant -> real approved textbook page/exercise/source citations -> optional 1 fresh practice check. Maths formulas and units are independently checked where possible. Provide short explanation by default, with 'Explain more simply', 'Another example', 'Show each step' and 'Ask me a question'. Support accessible language/medium configuration without fabricating source translations.

If no verified source supports a requested exact textbook citation, disclose that no approved page match was found instead of inventing one. Universal mathematical facts may use a reviewed shared concept, while board examination rubric and question-specific claims remain board-specific. Track misconceptions only from actual objective-based practice or marked work, not from speculative sentiment analysis of ordinary chat. Avoid generic unsourced 'examiners always deduct' claims.

## 17. Academic library and retrieval-augmented generation (RAG)

One approved, versioned source registry must identify: publisher/creator, URL, rights/licence/allowed uses/expiry, board, grade, exam session/cohort, textbook edition, medium, subject, chapter, module, objective, exact printed page and exercise, source file hash/checksum, reviewer, parser/OCR revision and approval state. Store images/renderings as appropriate; handle scanned and text-native books differently.

Ingestion: owner uploads source -> records rights -> selectable PDF text extraction first -> selective Docling/OCR for scanned textbooks -> page/layout reconciliation -> manual mathematical formula/diagram/page validation -> objective mappings -> approved sections -> full-text/vector indexing -> controlled release. Do not use bulk handwriting OCR as a substitute for vision exam marking.

Retrieval order: authenticate and resolve curricular scope -> filter for tenant/rights/approved publication/board/grade/edition/cohort -> exact identifier lookup (chapter/page/exercise) and PostgreSQL full-text -> pgvector semantic retrieval only within allowed set -> score/rerank -> return whitelisted citation IDs and direct approved source metadata. Shared concept text may be intentionally selected for general tutoring; Punjab grading never imports Federal exam rules. Entrance tests may use a specifically approved cross-board library.

The Google Drive books and syllabi previously located are only candidate assets, many older or off-board. Their existence in Drive does not grant commercial PDF rehosting, model training, caption reuse or suitability for 2026+ Punjab exams. All must pass the academic and rights pipeline.

## 18. AI workers and deterministic orchestration

Use an authenticated controller to choose narrow tasks and pass limited tools to each worker. Recommended roles:

1. **Curriculum Router:** resolve active enrollment, examination type, edition and allowed resource scope.
2. **Retriever:** return approved passages/page/exercise IDs using filtered hybrid search; no private cross-tenant read.
3. **Tutor:** explain concepts step by step, give optional fresh exercises and source citations.
4. **Item/Paper Author:** draft schema-constrained reviewed question candidates and paper assemblies.
5. **Independent Critic:** check ambiguity, duplicate items, wrong-answer keys, unsupported sources, marks totals and numerical validity.
6. **Vision/Image Reader:** extract scanned answer evidence and uncertainty regions.
7. **Rubric Examiner:** award criterion-level points against a frozen, approved marking specification.
8. **Visual Verifier:** review original symbols, diagrams, units, disputed deductions and alternate approaches.
9. **Weakness Planner:** schedule targeted repair and fresh retesting from actual evidence.
10. **Guardian Reporter:** summarize only authorized released results and approved next steps.
11. **Quota Governor:** atomically authorize model spend, prevent double billing and apply safe fallback.

Sequence is Auth -> Consent -> Tenant RLS/role -> Curriculum and rights -> Subscription/entitlement -> Quota/cost reserve -> Retrieval/tool invocation -> Validation -> Store result/audit -> Release quota/settlement. All AI prompts, models, provider routing policies, rubrics and academic bundles carry version IDs. Prefer cheap validated models for routine tutoring and deterministic MCQ scoring; use more capable vision/verification only on tasks that require it. No agent receives unrestricted database credentials, arbitrary student scans or permission to email an address supplied by untrusted text.

## 19. Weakness detection, personalized repair and fairness

Each graded item maps to explicit objectives and misconception classes (e.g., incorrect sign manipulation; missing Physics unit; incorrect Biology function). A weakness score uses how often, how recently, how important and how confidently a student missed the objective. Separate evidence from quiz first tries, formal exam marks and accepted appeal corrections. The planner offers a small contextual explanation and a fresh question, rechecks later, and updates status only upon verified new work. Prevent a single scanning mistake from contaminating the learner's long-term profile.

Provide recommendations in supportive instructional language, without ranking a minor publicly by their marks. Show explanation of why a topic was suggested ('You missed two questions on balancing equations in your last practice'). Allow reasonable accessible accommodations and alternative response formats.

## 20. Student paper library, reports and guardian experience

**Paper Library:** For each student, show attempted exams by subject/scope/date, original/updated marks, completion/pending-review state, objective breakdown, corrections, retest action and local marked PDF availability. If the PDF has been exported or removed by Android storage cleanup, clearly distinguish it from server-retained score summary. Do not promise indefinite cloud recovery after temporary scans have been purged.

**Guardian linking:** Start with pictorial setup from the prototype: select relationship, enter contact, explain permissions, obtain any required student/guardian consent and email verification. Proving control of an email mailbox is not alone proof that the recipient is an authorized guardian; apply age-appropriate checks and explicit scope confirmation. Invitation emails must not contain protected grade details. Allow revoke, dispute and access changes.

**Weekly authorized report:** progress in approved subjects, meaningful completed assessments, original/corrected marks with status, current weak objectives, effort trends expressed accurately, next-week constructive plan and a link to authorized secure portal. No routine emailing of raw handwriting photos, public ranking or unrelated students' details. When an appeal alters a score, label prior reports superseded and send corrections as appropriate. Teacher access is separate from guardian authorization.

## 21. Safe friend/community functions — optional, not first-launch dependency

A future opt-in feature may allow mutually approved friends to share non-sensitive milestones, study encouragement and limited achievement badges. Minors must not be discoverable through a public directory by default. Do not add open direct messaging, unrestricted chat, phone numbers, geographic location exposure or exact-score public leaderboards to the MVP. Provide reporting/blocking/moderation, guardian/child-safe controls and current policy review before releasing any social surfaces. This feature must never be necessary to complete the Academy or Test Series learning cycle.

## 22. Business/Owner Master Panel — main navigation

Create an accessible web console with MFA for privileged roles and audit history. Suggested navigation:

Dashboard; Academic Library; Boards & Editions; Subjects/Chapters/Modules; Teacher Videos & Creator Rights; MCQ Bank; Written Question Bank; Paper Blueprints; Rubrics & Examiner Calibration; AI Tutor / Safe Prompt Settings; Publishing & Rollback; Students & Tenants; Guardians & Teachers; Subscriptions/Play Billing; Referrals/Commissions; Paper Jobs & Appeals; Reports/Analytics; Cost & Quota Control; Compliance/Security; Staff/Roles; Settings.

Do not show every category in a single cluttered view. Founder and editors have separate permissions for sensitive operations. Every saved change is an authenticated revision with actor, timestamp, diff and approval status; no content edit should require changing Android application code.

### 22.1 Owner dashboard

Show active paid learners, subscription state and refunds; subjects released versus missing; rights about to expire; content waiting for academic review; AI job queue and failed scan uploads; papers awaiting human review; appeals; suspicious access; daily/monthly model cost and projected margin. Use action-oriented alerts and distinguish measured data from forecast estimates.

### 22.2 Add or replace textbooks

Admin selects board, grade, edition, cohort and medium; uploads source PDF; adds official origin, copyright/licence fields and allowed uses; starts extraction; compares parsed text to original rendered pages; corrects formulas/printed page numbers/diagrams; links exercises to objective records; assigns independent academic review; publishes approved immutable edition. Replacement keeps old references and exam results accessible to authorized historic records, while future sessions switch only through documented migration.

### 22.3 Subject and module builder

Admin creates/edits subject chapters and individual modules, assigns objectives, difficulty, duration, book pages, exercise references, prerequisites, subject-specialist activity type, suggested order and fallback notes. Wizard validates full-chapter coverage and warns about missing source, unverified formula, lesson gap or unsigned rights. Changes stay in draft until reviewed/published. Preview the exact student screen and its five-stage behavior before approval.

### 22.4 Teacher lecture/creator management

Store teacher/creator identity and contractual rights separately from student data. Paste a YouTube URL, preserve original player/attribution, record embed permission/availability, optional timestamp sections, licensed creator-supplied transcripts or separate original notes, rights expiry and replacement video. Review video accuracy for current edition. If video vanishes, show module fallback and alert editor. Teacher referrals/commissions are governed by separate verified commercial agreements and cannot silently grant access to personal student records.

### 22.5 Question factory and academic approval

Editor picks module objectives, authorized source spans, permitted difficulty distribution, forbidden content and question types. AI drafts a larger reusable candidate pool, including answer key, rationale for each option, citations and misconception tags. Independent critic flags ambiguity, unsupported answers, duplicate stems, incorrect arithmetic and risky translations. Human editor edits, accepts/rejects, assigns reviewer and publishes a verified pool. Revision invalidates affected test items if necessary and creates clear regrade alerts. Never create final exam questions live at each learner tap from an unreviewed model output.

### 22.6 Exam blueprints and rubric editor

Admin selects board/subject/cohort and chapter/quarter/half/full scope; defines time window, section format, choice rules, short/long count, total marks, objective weights, diagram/numerical requirements and per-criterion partial-credit rules. Flag whether pattern is officially evidenced or teacher-reviewed practice. Validator ensures marks add up, correct source and edition apply and no objective is outside scope. Preview sample paper beside its exact model solution and full rubric, obtain independent academic approval and freeze each published release.

### 22.7 AI settings and safe prompts

Owner may choose approved explanation depth, difficulty bands, supported languages, cost caps, routing priorities, help style, valid model options and tests through versioned configuration. Cannot disable server-side identity/consent/RLS/rights checks, source citation validation, examiner scoring invariants, child safety or quotas by editing a plain prompt. Expose staging examples, regression results, rollback and traceable prompt versions.

### 22.8 Student, guardian and tenant management

Create/support institutions and families; enroll students in validated scopes; map assigned teachers; inspect only authorized support data; verify/revoke guardians; investigate access reports, account deletion and educational appeals. Provide school-level aggregated summaries only where permitted and with privacy protections. No default global download of minors' scans for staff convenience.

### 22.9 Operations, finance and creators

See verified Play purchase/renewal/refund records, entitlements, allowed paper and tutor usage, teacher referral attribution, eligible commission (not on refunded sales), private payout statements, detailed costs by task and per-learner/project spending alerts. Admin can pause a dangerous model route or a defective item without deleting historical audit entries. Export limited owner-level accounting data under privilege checks.

### 22.10 Publishing SOP

Draft -> Automated QA -> Academic review -> Rights/permission approval -> Staging preview -> Signed approval -> Immutable production release -> Monitor -> Suspend/rollback if required. Owner may initiate but may not bypass required academic sign-off. Publishing snapshots dependencies: board, grade, edition, source rights, videos, question bank, rubric, prompt config and effective cohort. When a scoring fix affects previous attempts, queue targeted regrade/review and notify affected users honestly.

## 23. Security architecture and multi-tenancy

Recommended initial architecture:

Android app (Kotlin/Jetpack Compose proposed for native functionality and accessible UI implementation; final framework subject to engineering choice) -> authenticated HTTPS API/Supabase Auth -> PostgreSQL with RLS and academic version registry -> PostgreSQL full-text + pgvector for approved retrieval -> private Cloudflare R2 temporary image store -> durable background worker queue for image grading/reports/billing webhooks -> approved model providers -> encrypted backups, metrics and audit service.

Implement tenant and user ownership checks on every read/write, including signed object creation and retrieval. RLS policies deny by default; use explicit verified membership for guardian, teacher, owner and assigned-staff contexts. Never ship Supabase service-role or AI provider keys in the app. Apply encrypted transport/storage, short-lived tokens, rate limits and targeted moderation controls. Separate development/staging/production; use synthetic/de-identified school content in testing. Admin login should be MFA-protected; sensitive role changes and guardian revocations auditable. Secure deletion, export, backups, incident notifications and retention rules require release verification.

For low-end Android: maintain offline text drafts and camera originals locally in app-private storage, synchronize idempotently; review OS background process limits, app lock / shared-phone leakage, media permission minimization, crash recovery and secure file export. Reconcile R2 object purge even after failed jobs. Where student data is deleted, apply retention exceptions transparently and legally.

## 24. Technical objects / relational data model (planning contract)

Use typed, versioned migrations, not just this conceptual list:

- **Identity:** users, student_profiles, tenants, memberships, teacher_assignments, guardian_links, consent_events, account_deletion_requests.
- **Curriculum:** boards, examination_targets, curriculum_versions, textbook_editions, subjects, chapters, modules, concepts, learning_objectives, curriculum_objectives, prerequisite_edges.
- **Sources:** publications, source_rights, source_files, source_pages, source_sections, source_embeddings, source_approval_events, content_release_manifests.
- **Content/questions:** teacher_videos, transcript_rights, video_segments, question_items, item_revisions, distractors, rubrics, rubric_criteria, paper_blueprints, papers, paper_snapshots.
- **Learning:** learning_events, learner_objective_states, quiz_attempts, quiz_answers, written_submissions, learner_journeys, milestone_bundles, revision_tasks, daily_plan_revisions.
- **Exam pipeline:** exam_attempts, exam_pages, scan_upload_manifests, scoring_jobs, scoring_criteria_results, grade_corrections, appeals, processing_artifacts, purge_receipts.
- **Commerce/ops:** subscriptions, purchase_events, entitlements, ai_usage, quota_reservations, referrals, commission_ledger, guardian_report_jobs, audit_logs, incidents.

Enforce foreign keys across subject/board/edition where applicable. Record content checksum and immutable publication/rubric/prompt version in every scored response. Model output is untrusted input requiring validation. Students must never directly write awarded marks or entitlements. Use stored events rather than replacing old academic evidence.

## 25. Proposed authenticated API surface

Names are illustrative; engineering should formalize OpenAPI schemas and idempotency semantics before coding. All operations validate caller, tenant, curriculum publication and entitlement on the server.

| Route | What it does | Critical guards |
|---|---|---|
| POST /v1/onboarding | Save board/grade/subjects/session/pathway and study time | Approved active edition, guardian state, valid subjects |
| GET /v1/today | One primary task and optional revision queue | Learner ownership, entitlement, task reason |
| GET /v1/subjects | Published enrolled subject tree and progress | Exact board/edition filter |
| GET /v1/modules/:id | Published video, quiz, reading, written tasks | No draft leaks or hidden keys |
| POST /v1/quiz/start | Select approved versioned MCQs | Valid module, rate limit, no key exposure |
| POST /v1/quiz/answer | Mark selected answer and return feedback | Server answer key, original attempt preserved |
| POST /v1/written/submit | Evaluate a typed/approved written practice answer | Correct rubric, idempotency |
| POST /v1/tutor | Retrieve allowed textbook evidence and answer | Consent, cost cap, citation whitelist |
| GET /v1/journey | Weighted milestone progress and evidence | No client-side arbitrary completion |
| GET /v1/assessments | List eligible exam scopes | Active published blueprint |
| POST /v1/assessments/create | Reserve quota and snapshot paper | Billing verified, ready/eligible, no double reserve |
| POST /v1/scans/sign | Issue signed upload targets for own page hashes | Own attempt, private R2 key, time/size cap |
| POST /v1/scans/finalize | Validate ordered pages and queue marking | Every submitted page received, idempotency |
| GET /v1/assessments/:id/result | Question/criterion marks and overlay annotations | Learner/verified guardian scope as applicable |
| POST /v1/grades/:id/appeal | Request scoped visual/human recheck | Owner, dispute window, original evidence available |
| POST /v1/guardians/invite | Begin verified guardian link | No marks in initial invitation |
| POST /v1/guardians/verify | Verify and approve permitted reporting scopes | Authorization and revocation checks |
| POST /v1/billing/play/verify | Verify purchase with Google backend | Server token verification, replay defense |
| POST /v1/admin/publish | Approve/release immutable content manifest | Academic approval, rights, QA, RBAC, audit |

Paper jobs should expose QUEUED, PROCESSING, PENDING_REVIEW, RESULT_READY, FAILED_RETRYABLE, FAILED_FINAL statuses; app polls or receives authenticated notifications without presenting partial unvalidated marks as final. Have standardized error responses for BOARD_UNAVAILABLE, SOURCE_NOT_APPROVED, ENTITLEMENT_REQUIRED, QUOTA_LIMIT, GUARDIAN_AUTH_REQUIRED, SOURCE_RIGHTS_EXPIRED, IMAGE_RESCAN_REQUIRED, RUBRIC_NOT_APPROVED and PAPER_PENDING_REVIEW.

## 26. Subscription, referrals and economics

**Subscription:** one nominal PKR 3,999/month paid product. Confirm current Google Play digital-goods/subscription policies and applicable Pakistan distribution/tax treatment prior to release; implement Android purchase UI through required Play Billing mechanisms with backend verification and real-time lifecycle events. Handle active, pending, grace, paused, canceled, expired and refunded states, restoration and support without double-charging or granting access on an unverified client token. Public permitted YouTube playback is not itself a subscriber-exclusive entitlement.

**Referral proposal, pending contract/founder approval:** eligible partner referral may give a learner 10% off and teacher 10% commission under defined payout/refund rules; do not automatically pay any teacher who appears in a video. Store referrals, verified sales and refunds in a private ledger without exposing child information.

**Unit economics warning:** discounted receipt 3,999 x 0.90 = PKR 3,599.10. Under an illustrative, unverified 15% Play fee assumption, fee = 539.865; at teacher commission of 10% of discounted collected price, commission = 359.91; balance = PKR 2,699.325 before AI, hosting, support, academic review, refunds/tax and profit. Reserving PKR 1,000 for AI, PKR 500 for hosting and PKR 1,500 fixed profit simultaneously would cost PKR 300.675 more than the balance even before other charges. Target 40% margin on actual receipts only if real blended costs permit; mark these figures as planning assumptions, not confirmed transaction fees.

Stress-test a heavy-use learner: 10 handwritten papers x 25 pages = 250 pages, 20 quizzes, 1,000 tutor calls per month, plus 20% retry/recheck cost allowance. If compressed scans average 0.5 MB, that example is ~125 MB temporary uploaded monthly per learner, excluding metadata and retries. Capture actual token/vision/infrastructure invoices; quota governor should protect from abuse, cache approved knowledge/questions and reduce nonessential calls, not fake marks or discard uncertain evidence. Display fair quota/remaining entitlement and use human review for necessary complex answers.

## 27. Child safety, privacy, accessibility and public claims

Before launch, independently verify current Google Play Families/target-audience/permissions/data-safety/account-deletion/billing policies, YouTube embed/API rules, and applicable Pakistani privacy/child/consumer obligations against official sources and legal advice. Do not assume this planning document is a compliance certification. Require minimum data, secure privacy notice, account and student-data deletion workflow, guardian approval/revocation, signed release consent, abuse reporting and moderation support. Do not allow student-to-student exposure of exam scripts, home addresses, phone numbers or public academic rankings by default.

For accessibility: readable contrast, descriptive labels on all icons, one primary action per screen, responsive sizes for small phones/tablets, larger touch targets, no required animated motion, reduced-motion mode, clear Urdu/English language support where academically validated, graceful internet failures and low-data fallback. Treat text-book images and licensed content distribution according to their own source rights.

Public app claims may say 'AI-assessed practice exams' and 'board-aligned after academic verification'; do not claim actual official exam marks are guaranteed, every reference comes from official sources without evidence, or a course is complete before the editorial coverage certificate exists.

## 28. Error handling, offline and observability

Persist student drafts, ordered images, scan crop settings, quiz answer queue and last plan state on-device under a private profile; refresh entitlements and immutable content references on reconnection. An offline student may continue previously licensed downloaded notes, unsent written responses or local camera capture but cannot obtain fabricated server AI feedback. If a payment is pending, keep it pending until confirmed. If an AI provider is down, show queued status and a genuine retry path. If a teacher removes a video, show verified notes or an alternate approved lesson. If a rubric is quarantined mid-test, preserve the attempt snapshot and hold publishing until corrected/approved.

Track minimum trustworthy metrics: authentication success, next-task readiness, chapter coverage, source citation validity, cross-board retrieval incidents, MCQ correction rates, human examiner agreement, scan reorder/upload/resume success, guardian verification and revocation, Play verification errors, job queue depth, p95 response/marking delays, per-feature AI spend, refund rate, user-reported ambiguous marks and data deletion fulfillment. Separate synthetic demo counts from real users and all predicted values from confirmed numbers.

## 29. Minimum end-to-end testing matrix / acceptance gates

A release cannot be described as 'done' without measured checks. At minimum:

1. **Board filtering:** Same exercise number exists in Punjab and Federal books; Punjab learner/tutor/marker reads only approved Punjab material and citation.
2. **Tenants:** School A teacher and unrelated guardian cannot guess or fetch School B marks, scans, progress, signed URLs, hidden answer keys or emails.
3. **Onboarding:** Unsupported board or wrong edition is blocked; selected 9/10 active subjects persist.
4. **Learning:** Student completes 12 approved MCQs, leaves mid-written answer and resumes without losing text; no rocket milestone is awarded for video alone.
5. **Academic source:** Editor validates real printed page/diagram/exercise in approved PDF; missing citation produces explicit no-source state.
6. **Video rights:** No-transcript-rights video cannot produce a purported caption-derived commercial question bank.
7. **Question integrity:** Academic review catches ambiguous MCQs, wrong keys, mismatched citations and duplicate paper items before publication.
8. **Chapter exam:** 90-minute approved paper opens with snapshot and marks total; background/clock/camera interruptions do not corrupt attempt.
9. **25-page scan:** Low-end device can capture, retake, reorder, offline resume, upload, verify and locally archive all 25 pages.
10. **Vision reliability:** '6 N' must not silently become '8 N'; doubtful signs/diagrams trigger original-image review rather than unjust deductions.
11. **Valid alternatives:** Theorem or mathematics alternate correct approach receives fair approved partial/full credit, or a documented human decision.
12. **Grade appeal:** Corrected score cascades to weakness planner, subject progress, milestone status and guardian report supersession without destroying initial audit.
13. **Guardian security:** Unverified/adversarial email never receives marks; immediately revoked guardian cannot access a protected report.
14. **Billing:** Purchase, duplicate webhook, pending, renewal, cancellation and refund accurately update server entitlements and commission ledger.
15. **Quotas:** Heavy-use student stress test measures true AI+vision costs while preserving grading quality and preventing duplicate 25-page job bills.
16. **Publication/rollback:** Wrong Chemistry key can be suspended, fixed with approval, released and rolled back without changing old paper snapshots silently.
17. **Disaster recovery:** Restore encrypted backup to staging with RLS intact; demonstrate timely purge of temporary R2 images.
18. **App quality:** Android device tests on low-end phones and tablets; camera permission denied, no connection, Android background kill, offline draft recovery and screen-reader/reduced-motion checks.

Quantitative target thresholds (e.g., acceptable human/AI score divergence, p95 latency, crash-free sessions, successful page capture) must be established from real pilot benchmarks and signed off by founder, academic reviewer and technical lead before production; do not invent precise pass thresholds for an untested model.

## 30. Delivery phases and definition of done

**Gate 0 — Requirements, rights, academic authority.** Freeze feature scope, approved first complete subject, correct/current Punjab book and exam cohort, licensed content sources, marking benchmarks, consent/compliance plan, founder decisions and initial budget. Deliver content coverage map, signed rights register, threat model and PRD.

**Gate 1 — Backend, tenant/RLS, billing-ready identity.** Supabase schema, RLS/role tests, environments, auth/consent, plan entitlement model, basic owner roles, private object permissions and audit. Do not start expensive AI calls before authorization controls exist.

**Gate 2 — Android foundation + CameraX.** Rebuild approved easy-guided visual experience with real app navigation; implement secure local storage, 25-page scanner, manual crop, draft recovery and resumable uploads.

**Gate 3 — One complete verified academic subject.** Publisher/edition intake, book page review, one entire subject coverage matrix, teacher rights, module stages, approved MCQ pool and editable no-code content publishing. Validate one real end-to-end chapter before broad subject creation.

**Gate 4 — Curriculum tutor and paper factory.** Permission-scoped retrieval, validated tutor, test-series readiness, question generation/critic/approval and exact approved exam blueprints.

**Gate 5 — Grader, local marked paper and calibration.** Durable marking pipeline, selective vision verification, criterion-level rubrics, appeals, local marked PDF, independent human mark comparison and financial instrumentation.

**Gate 6 — Guardian reporting, Play Billing and first pilot.** Verification/revocation, weekly report, purchase/refund lifecycle, account deletion, child-safety and official policy verification; run consented 1–10-learner pilot with real (lawful) approved materials.

**Gate 7 — Scale and expansion.** Correct pilot faults, test 50 and 100 paid learners before adding approved subjects, other boards and university entrance packs. Each new board passes full source and rubrics review; do not copy Punjab scoring rules to another board.

**Definition of done for every feature:** tested in staging against at least normal/empty/error/unauthorized/offline cases, documented API/data schema and UI states, academic sign-off for scored content, automated tests where feasible, security audit evidence, cost telemetry and a release/rollback path. Mark VERIFIED / UNVERIFIED without embellishment.

## 31. Founder decisions needed before implementation

1. Confirm the first complete Punjab 9/10 subject and exact academic cohort/edition and launch subject set; do not assume existing older Google Drive PDFs are current.
2. Confirm exact board session and who provides legally reusable textbook/exercise/past-paper rights and teacher-video transcript rights.
3. Approve academic marking source hierarchy, partial-credit expectations, appeal rules and initial human calibration benchmark.
4. Confirm whether Test Series and Complete Academy can be changed during a paid term and which usage counters are shared.
5. Define permitted assessment/tutor usage, fairness and refusal/retry policy from real per-task cost measurements.
6. Set the guardian verification and child-safety review process; decide if any social feature is delayed entirely until after the pilot.
7. Sign off the native Android approach, owner panel minimum screens, offline data retention, user deletion/export and backup policy.
8. Confirm referral eligibility, discounts, teacher commissions and payout timing against actual collected/refunded receipts.

## 32. Final developer instruction

Build Parhlou as an academically governed, secure, cost-measured student learning platform, not a generic chatbot wrapper or a visual mockup with random AI-generated questions. The exact student journey is:

Onboard verified Punjab Class 9/10 curriculum -> personalized daily plan -> approved teacher lecture or legal study fallback -> 10–15 approved MCQs -> exact textbook reading and active recall -> 1–2 written practice responses with correction -> chapter revision -> configurable 90-minute chapter assessment -> native scan of up to at least 25 handwritten pages -> vision-based rubric marking and independent verification -> detailed corrections and human recheck where necessary -> weakness repair and spaced retest -> balanced multi-subject milestones -> quarter/half/full syllabus mocks -> authorized guardian progress reports.

For Test Series, bypass compulsory videos and module completion but retain 5–10 question readiness checks, published paper eligibility, versioned correct board rubrics and the same high-quality feedback loop.

The owner controls academic content and commercial operations through a no-code, audited publication workflow. The backend enforces identity, tenant membership, curricular scope, source permissions, purchase entitlement and AI spending before every protected operation. The Play Store Android app preserves the approved easy-guided UI and provides reliable offline drafts, native scanning and local marked PDFs. Do not claim any feature is live until an actual test and academic/legal sign-off prove it.

**STATUS (as of this handoff):**

- **DONE:** detailed plan and reviewed UI baseline; developer-ready functional handoff prepared.
- **NEXT:** founder freezes launch subject/book versions, rights and exact MVP gates, then engineering converts this document into tickets, wireframes, typed database migrations and OpenAPI schemas.
- **BLOCKED:** verified up-to-date academic corpus, source/media licences, board examination blueprints, external policy sign-off, tested marking calibration and live cost measurements.
- **RISKS:** wrong-edition content, vision marking errors, child data leakage, YouTube caption misuse, heavy-use unit economics, Play billing/policy non-compliance.
- **OWNER DECISIONS:** section 31; record every accepted change back into the A-to-Z Master Guide and decision log.
