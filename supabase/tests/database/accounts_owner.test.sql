-- Accounts, sync, AI marks, deletion and owner functions.
begin;
create extension if not exists pgtap;
select plan(30);

-- Platform tenant comes from seed.sql. The owner email is configured before the owner signs up.
select public.set_owner_email('Owner@Example.test');
insert into auth.users (id, email) values
  ('c1000000-0000-0000-0000-00000000000a', 'student-a@example.test'),
  ('c1000000-0000-0000-0000-00000000000b', 'student-b@example.test'),
  ('c1000000-0000-0000-0000-0000000000ee', 'owner@example.test');

select ok(exists (select 1 from public.memberships where user_id = 'c1000000-0000-0000-0000-0000000000ee' and role = 'owner'),
  'the configured owner email gets the owner role on sign-up');
select ok(not exists (select 1 from public.memberships where user_id = 'c1000000-0000-0000-0000-00000000000a' and role = 'owner'),
  'other accounts do not');

-- Student A
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-00000000000a', true);
select isnt(public.register_learner('Ayesha'), null, 'student registers');
select is(public.register_learner('Ayesha K'), (select tenant_id from public.student_profiles), 'registering again keeps the same family space');
select is((select display_name from public.student_profiles), 'Ayesha K', 'and updates the name');
select lives_ok($$insert into public.learner_settings (settings) values ('{"grade":9}')$$, 'student saves settings');
select lives_ok($$update public.learner_settings set settings = '{"grade":10}'$$, 'and updates them');
select lives_ok($$insert into public.learner_events (client_id, at, key, code) values ('a-0000001', now(), 'm-1-1', 'Q:80')$$, 'student syncs an event');
select throws_ok($$insert into public.learner_events (client_id, at, key, code) values ('a-0000001', now(), 'm-1-1', 'Q:80')$$,
  '23505', null, 'the same event cannot be stored twice');
select throws_ok($$insert into public.learner_events (client_id, at, key, code) values ('a-0000002', now(), 'paper:x', 'E:95')$$,
  '23514', null, 'the phone cannot write a verified exam mark');
select throws_ok($$insert into public.learner_events (user_id, client_id, at, key, code) values ('c1000000-0000-0000-0000-00000000000b', 'a-0000003', now(), 'k', 'O')$$,
  '42501', null, 'student cannot write events for someone else');
update public.learner_events set code = 'Q:100';
select is((select code from public.learner_events), 'Q:80', 'student cannot change a synced event');
select throws_ok($$insert into public.ai_marks (user_id, question_id, awarded, max_marks, status, model, prompt_version)
                   values ('c1000000-0000-0000-0000-00000000000a', 's1', 3, 3, 'CHECKED', 'x', 'x')$$,
  '42501', null, 'student cannot write AI marks');
select throws_ok($$select public.owner_dashboard()$$, '42501', 'OWNER_ONLY', 'student cannot open the owner dashboard');
select throws_ok($$select public.owner_publish_content('pb-9-math', '{}')$$, '42501', 'OWNER_ONLY', 'student cannot publish content');
select throws_ok($$select public.owner_set_config('ai_enabled', 'false')$$, '42501', 'OWNER_ONLY', 'student cannot change settings');
select throws_ok($$select * from public.owner_learners()$$, '42501', 'OWNER_ONLY', 'student cannot list learners');
select is((select count(*)::int from public.app_config), 0, 'student cannot read server settings');
reset role;

select throws_ok($$update public.learner_events set code = 'Q:100'$$, 'P0001', null, 'events are append-only, even for the server');

-- Server writes a mark for A; student B sees nothing of A's.
insert into public.ai_marks (user_id, paper_id, question_id, awarded, max_marks, status, model, prompt_version)
values ('c1000000-0000-0000-0000-00000000000a', 'sample-chapter-test', 's1', 2, 3, 'CHECKED', 'gpt-6-luna', 't');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-00000000000b', true);
select is((select count(*)::int from public.learner_events) + (select count(*)::int from public.learner_settings)
          + (select count(*)::int from public.ai_marks), 0, 'student B sees none of student A''s data');
reset role;

-- Owner
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-0000000000ee', true);
select is((public.owner_dashboard() ->> 'learners_total')::int, 1, 'owner dashboard counts learners');
select is((select papers from public.owner_learners() limit 1), 1, 'owner sees papers marked per learner');
select throws_ok($$select public.owner_publish_content('pb-9-math', '{"format":1,"subjectId":"other"}')$$,
  '22023', null, 'a bundle for another subject is refused');
select is(public.owner_publish_content('pb-9-math', '{"format":1,"subjectId":"pb-9-math","subject":{},"written":[],"mcqs":[],"notes":[]}'), 1,
  'owner publishes version 1');
select is(public.owner_publish_content('pb-9-math', '{"format":1,"subjectId":"pb-9-math","subject":{"v":2},"written":[],"mcqs":[],"notes":[]}'), 2,
  'and version 2');
select is(public.owner_rollback_content('pb-9-math', 1), 3, 'rolling back republishes version 1 as version 3');
select is((select bundle->'subject' from public.app_content where status = 'PUBLISHED' and subject_id = 'pb-9-math'), '{}'::jsonb,
  'the rolled-back content is live');
select throws_ok($$select public.owner_set_config('owner_email', '"x@y.z"')$$, '22023', null, 'the owner email cannot be changed from the panel');
select lives_ok($$select public.owner_set_config('ai_enabled', 'false')$$, 'owner switches the AI off');
reset role;

-- Account deletion
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1000000-0000-0000-0000-00000000000a', true);
select public.delete_my_account();
reset role;
select is((select count(*)::int from auth.users where id = 'c1000000-0000-0000-0000-00000000000a')
          + (select count(*)::int from public.learner_events where user_id = 'c1000000-0000-0000-0000-00000000000a')
          + (select count(*)::int from public.ai_marks where user_id = 'c1000000-0000-0000-0000-00000000000a')
          + (select count(*)::int from public.student_profiles where user_id = 'c1000000-0000-0000-0000-00000000000a')
          + (select count(*)::int from public.tenants where name like 'Family of Ayesha%'),
  0, 'deleting the account removes the user, events, marks, profile and family space');

select * from finish();
rollback;
