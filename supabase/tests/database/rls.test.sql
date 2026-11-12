-- Security and integrity tests for the Milestone 1 schema (PRD §29.2, §29.13 and threat model T1–T7).
-- Runs with `supabase test db` or scripts/test-db-local.sh. Everything is rolled back at the end.
begin;
create extension if not exists pgtap;

select plan(34);

------------------------------------------------------------------------------
-- Fixtures (as the database owner)
------------------------------------------------------------------------------
insert into auth.users (id, email) values
  ('a0000000-0000-0000-0000-00000000000a', 'learner-a@example.test'),
  ('b0000000-0000-0000-0000-00000000000b', 'learner-b@example.test'),
  ('90000000-0000-0000-0000-000000000001', 'guardian-verified@example.test'),
  ('90000000-0000-0000-0000-000000000002', 'guardian-invited@example.test'),
  ('90000000-0000-0000-0000-000000000003', 'guardian-revoked@example.test'),
  ('70000000-0000-0000-0000-000000000001', 'teacher-b@example.test'),
  ('e0000000-0000-0000-0000-000000000001', 'editor@example.test');

insert into public.tenants (id, name, kind) values
  ('00000000-0000-0000-0000-0000000000aa', 'Family A', 'family'),
  ('00000000-0000-0000-0000-0000000000bb', 'School B', 'school'),
  ('00000000-0000-0000-0000-0000000000ff', 'Platform (test)', 'platform');

insert into public.memberships (tenant_id, user_id, role) values
  ('00000000-0000-0000-0000-0000000000aa', 'a0000000-0000-0000-0000-00000000000a', 'learner'),
  ('00000000-0000-0000-0000-0000000000bb', 'b0000000-0000-0000-0000-00000000000b', 'learner'),
  ('00000000-0000-0000-0000-0000000000aa', '90000000-0000-0000-0000-000000000001', 'guardian'),
  ('00000000-0000-0000-0000-0000000000bb', '70000000-0000-0000-0000-000000000001', 'teacher'),
  ('00000000-0000-0000-0000-0000000000ff', 'e0000000-0000-0000-0000-000000000001', 'editor');

insert into public.student_profiles (user_id, tenant_id, display_name) values
  ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', 'Learner A'),
  ('b0000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-0000000000bb', 'Learner B');

insert into public.guardian_links (tenant_id, learner_id, guardian_user_id, relationship, status, scopes, verified_at, revoked_at) values
  ('00000000-0000-0000-0000-0000000000aa', 'a0000000-0000-0000-0000-00000000000a', '90000000-0000-0000-0000-000000000001', 'mother',  'verified', '{progress}', now(), null),
  ('00000000-0000-0000-0000-0000000000aa', 'a0000000-0000-0000-0000-00000000000a', '90000000-0000-0000-0000-000000000002', 'father',  'invited',  '{progress,marks}', null, null),
  ('00000000-0000-0000-0000-0000000000aa', 'a0000000-0000-0000-0000-00000000000a', '90000000-0000-0000-0000-000000000003', 'uncle',   'revoked',  '{progress,marks}', now(), now());

insert into public.teacher_assignments (tenant_id, teacher_user_id, learner_id) values
  ('00000000-0000-0000-0000-0000000000bb', '70000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-00000000000b');

-- Published test curriculum (synthetic, not textbook content)
insert into public.boards (id, code, name, status) values
  ('11000000-0000-0000-0000-000000000001', 'TEST', 'Test board', 'PUBLISHED');
insert into public.examination_targets (id, board_id, grade, session_label, status) values
  ('12000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 9, 'Test 2027', 'PUBLISHED');
insert into public.subjects (id, board_id, grade, code, name, status) values
  ('13000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 9, 'MATH', 'Maths', 'PUBLISHED');
insert into public.textbook_editions (id, subject_id, board_id, grade, title, edition_label, status) values
  ('14000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 9, 'Test Maths', 'v1', 'PUBLISHED'),
  ('14000000-0000-0000-0000-000000000002', '13000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 9, 'Draft Maths', 'v2', 'DRAFT');
insert into public.chapters (id, edition_id, number, title, status) values
  ('15000000-0000-0000-0000-000000000001', '14000000-0000-0000-0000-000000000001', 1, 'Published chapter', 'PUBLISHED'),
  ('15000000-0000-0000-0000-000000000002', '14000000-0000-0000-0000-000000000001', 2, 'Draft chapter', 'DRAFT');
insert into public.modules (id, chapter_id, position, title, status) values
  ('16000000-0000-0000-0000-000000000001', '15000000-0000-0000-0000-000000000001', 1, 'Module 1', 'PUBLISHED');

-- Six published MCQs (correct option = 2) and one draft MCQ in module 1
insert into public.question_items (id, chapter_id, module_id, item_type)
select ('17000000-0000-0000-0000-00000000000' || g)::uuid, '15000000-0000-0000-0000-000000000001', '16000000-0000-0000-0000-000000000001', 'mcq'
from generate_series(1, 7) g;
insert into public.item_revisions (id, item_id, revision, stem, options, rationales, marks, status)
select ('18000000-0000-0000-0000-00000000000' || g)::uuid, ('17000000-0000-0000-0000-00000000000' || g)::uuid, 1,
       'Synthetic question ' || g, '["w","x","y","z"]', '["no","no","yes","no"]', 1,
       case when g = 7 then 'DRAFT' else 'PUBLISHED' end::public.publication_status
from generate_series(1, 7) g;
insert into public.item_answer_keys (revision_id, correct_option)
select ('18000000-0000-0000-0000-00000000000' || g)::uuid, 2 from generate_series(1, 7) g;

insert into public.enrollments (learner_id, tenant_id, examination_target_id, edition_id, pathway) values
  ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', '12000000-0000-0000-0000-000000000001', '14000000-0000-0000-0000-000000000001', 'complete_academy'),
  ('b0000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-0000000000bb', '12000000-0000-0000-0000-000000000001', '14000000-0000-0000-0000-000000000001', 'test_series');

insert into public.entitlements (learner_id, tenant_id, product, status, valid_until, source) values
  ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', 'monthly_3999', 'active', now() + interval '30 days', 'test');

insert into public.learning_events (learner_id, tenant_id, kind, idempotency_key) values
  ('b0000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-0000000000bb', 'module_opened', 'b-1');

------------------------------------------------------------------------------
-- Learner A
------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', true);

select is((select count(*)::int from public.student_profiles), 1, 'A sees only their own profile');
select is((select count(*)::int from public.learning_events where learner_id = 'b0000000-0000-0000-0000-00000000000b'), 0,
          'T1: A cannot read B''s learning events');
select is((select count(*)::int from public.tenants where kind = 'school'), 0, 'T1: A cannot see School B tenant');
select is((select count(*)::int from public.item_answer_keys), 0, 'T3: A cannot read answer keys');
select is((select count(*)::int from public.item_revisions), 0, 'T3: A cannot read raw question revisions');
select is((select count(*)::int from public.chapters where status = 'DRAFT'), 0, 'T3: A cannot see DRAFT chapters');
select is((select count(*)::int from public.chapters where status = 'PUBLISHED'), 1, 'A sees the published chapter of their edition');

select throws_ok(
  $$insert into public.entitlements (learner_id, tenant_id, product, status, source)
    values ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', 'x', 'active', 'client')$$,
  '42501', null, 'T4: learner cannot grant themself an entitlement');

select throws_ok(
  $$insert into public.learning_events (learner_id, tenant_id, kind, idempotency_key)
    values ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', 'exam_marked', 'fake-1')$$,
  '42501', null, 'T4: learner cannot write an assessed event');

select lives_ok(
  $$insert into public.learning_events (learner_id, tenant_id, kind, idempotency_key)
    values ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa', 'module_opened', 'a-open-1')$$,
  'learner can record a study checkpoint');

select throws_ok(
  $$insert into public.learning_events (learner_id, tenant_id, kind, idempotency_key)
    values ('b0000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-0000000000bb', 'module_opened', 'spoof')$$,
  '42501', null, 'T1: learner cannot write events for another learner');

select throws_ok(
  $$insert into public.enrollments (learner_id, tenant_id, examination_target_id, edition_id, pathway)
    values ('a0000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-0000000000aa',
            '12000000-0000-0000-0000-000000000001', '14000000-0000-0000-0000-000000000002', 'test_series')$$,
  '42501', null, 'learner cannot enroll in a DRAFT edition');

-- Quiz flow
create temp table q on commit drop as select public.start_quiz('16000000-0000-0000-0000-000000000001', 5) as j;
select is((select jsonb_array_length(j->'items') from q), 5, 'start_quiz returns 5 items');
select ok((select position('correct' in (j)::text) = 0 from q), 'T3: start_quiz response contains no answer key');
select ok((select not ((j)::text like '%18000000-0000-0000-0000-000000000007%') from q), 'start_quiz never serves DRAFT revisions');

create temp table first_item on commit drop as
  select (j->>'attempt_id')::uuid as attempt_id, (j->'items'->0->>'revision_id')::uuid as revision_id from q;

select is((select (public.answer_quiz_item(attempt_id, revision_id, 1))->>'is_correct' from first_item), 'false',
          'T7: wrong option is scored false on the server');
select is((select (public.answer_quiz_item(attempt_id, revision_id, 2))->>'first_attempt' from first_item), 'false',
          'retry is recorded as a later attempt');
select is((select count(*)::int from public.quiz_answers), 2, 'both attempts are kept (first attempt preserved)');
select is((select count(*)::int from public.learning_events where kind = 'quiz_answered'), 2,
          'server recorded quiz_answered evidence');

select throws_ok(
  $$insert into public.quiz_answers (attempt_id, revision_id, chosen_option, is_correct, attempt_no)
    select attempt_id, revision_id, 1, true, 9 from first_item$$,
  '42501', null, 'T7: learner cannot insert a quiz answer directly');

reset role;

------------------------------------------------------------------------------
-- Learner B (no entitlement; other tenant)
------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', true);

select throws_ok($$select public.start_quiz('16000000-0000-0000-0000-000000000001', 5)$$,
  '42501', 'ENTITLEMENT_REQUIRED', 'learner without an active plan cannot start a scored quiz');
select throws_ok($$select public.answer_quiz_item(attempt_id, revision_id, 2) from first_item$$,
  'P0002', 'NOT_FOUND', 'T1: B cannot answer inside A''s attempt');
select is((select count(*)::int from public.quiz_attempts), 0, 'T1: B cannot see A''s quiz attempts');
reset role;

------------------------------------------------------------------------------
-- Teacher assigned to B
------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claim.sub', '70000000-0000-0000-0000-000000000001', true);
select is((select count(*)::int from public.student_profiles where user_id = 'b0000000-0000-0000-0000-00000000000b'), 1,
          'assigned teacher sees their learner');
select is((select count(*)::int from public.student_profiles where user_id = 'a0000000-0000-0000-0000-00000000000a'), 0,
          'T1: teacher cannot see an unrelated learner');
reset role;

------------------------------------------------------------------------------
-- Guardians of A
------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claim.sub', '90000000-0000-0000-0000-000000000001', true);
select is((select count(*)::int from public.student_profiles), 1, 'verified guardian sees the linked child');
select is((select count(*)::int from public.quiz_attempts), 0, 'guardian without the marks scope sees no marks');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '90000000-0000-0000-0000-000000000002', true);
select is((select count(*)::int from public.student_profiles), 0, 'T2: invited (unverified) guardian sees nothing');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '90000000-0000-0000-0000-000000000003', true);
select is((select count(*)::int from public.student_profiles), 0, 'T2: revoked guardian sees nothing');
select is((select count(*)::int from public.quiz_attempts), 0, 'T2: revoked guardian sees no marks');
reset role;

------------------------------------------------------------------------------
-- Staff editor
------------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-000000000001', true);
select is((select count(*)::int from public.item_answer_keys), 7, 'platform editor can review answer keys');
reset role;

------------------------------------------------------------------------------
-- Integrity (as database owner)
------------------------------------------------------------------------------
select throws_ok($$update public.learning_events set kind = 'exam_marked'$$,
  'P0001', null, 'T5: learning events are append-only');

select is(public.validate_blueprint(75, '[{"type":"mcq","count":15,"attempt":15,"marks_each":1},
                                         {"type":"short","count":27,"attempt":18,"marks_each":2},
                                         {"type":"long","count":5,"attempt":3,"marks_each":8}]'),
          '{}'::text[], 'T6: a 75-mark blueprint that reconciles has no problems');

select throws_ok(
  $$insert into public.paper_blueprints (edition_id, scope, title, duration_minutes, total_marks, sections, authority, status)
    values ('14000000-0000-0000-0000-000000000001', 'full', 'Bad paper', 150, 75,
            '[{"type":"mcq","count":15,"attempt":15,"marks_each":1}]', 'teacher_reviewed_practice', 'PUBLISHED')$$,
  'P0001', null, 'T6: a blueprint whose marks do not add up cannot be published');

select * from finish();
rollback;
