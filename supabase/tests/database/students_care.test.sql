-- Credits, memory, chats, safety flags, feedback and the owner's "view as student".
begin;
create extension if not exists pgtap;
select plan(16);

select public.set_owner_email('Owner@Example.test');
insert into auth.users (id, email) values
  ('d1000000-0000-0000-0000-00000000000a', 'student-a@example.test'),
  ('d1000000-0000-0000-0000-00000000000b', 'student-b@example.test'),
  ('d1000000-0000-0000-0000-0000000000ee', 'owner@example.test');

-- The gateway (service role, here the table owner) writes rows for two students.
insert into public.credit_ledger (holder, user_id, kind, amount, expires_at) values
  ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'grant', 1000, now() + interval '4 months'),
  ('d1000000-0000-0000-0000-00000000000b', 'd1000000-0000-0000-0000-00000000000b', 'grant', 1000, now() + interval '4 months');
insert into public.credit_ledger (holder, user_id, kind, amount, task) values
  ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'spend', 3, 'photo');
insert into public.chat_messages (holder, user_id, role, text) values
  ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'student', 'help with logs'),
  ('d1000000-0000-0000-0000-00000000000b', 'd1000000-0000-0000-0000-00000000000b', 'student', 'b''s question');
insert into public.student_memory (holder, user_id, summary) values ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'Likes worked examples.');
insert into public.safety_flags (holder, user_id, category, excerpt) values ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'UNSAFE_ILLEGAL', 'x');
insert into public.feedback (holder, user_id, text, screen) values ('d1000000-0000-0000-0000-00000000000a', 'd1000000-0000-0000-0000-00000000000a', 'The calendar is great', 'home');

select throws_ok($$insert into public.credit_ledger (holder, kind, amount) values ('d1000000-0000-0000-0000-00000000000a', 'grant', 10)$$,
  '23514', null, 'a grant must have an expiry date');
select throws_ok($$insert into public.student_memory (holder, summary) values ('h-0000000000000001', repeat('x', 1501))$$,
  '23514', null, 'the memory summary is capped at 1,500 characters');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'd1000000-0000-0000-0000-00000000000a', true);
select is((select count(*)::int from public.chat_messages), 1, 'a student reads only their own chats');
select is((select count(*)::int from public.credit_ledger), 2, 'and their own credits');
select is((select summary from public.student_memory), 'Likes worked examples.', 'and what Ustaad remembers about them');
select is((select count(*)::int from public.safety_flags), 0, 'but never the safety flags');
select is((select count(*)::int from public.feedback), 1, 'and their own feedback');
select throws_ok($$insert into public.chat_messages (holder, role, text) values ('d1000000-0000-0000-0000-00000000000a', 'student', 'x')$$,
  '42501', null, 'the app cannot write chats directly');
select throws_ok($$insert into public.credit_ledger (holder, kind, amount, expires_at) values ('d1000000-0000-0000-0000-00000000000a', 'grant', 9999, now() + interval '1 year')$$,
  '42501', null, 'and certainly cannot give itself credits');
select throws_ok($$select public.owner_student('d1000000-0000-0000-0000-00000000000b')$$, '42501', 'OWNER_ONLY', 'a student cannot view another student');
select throws_ok($$select * from public.owner_safety_flags()$$, '42501', 'OWNER_ONLY', 'or the safety flags');

-- The owner.
select set_config('request.jwt.claim.sub', 'd1000000-0000-0000-0000-0000000000ee', true);
select is((public.owner_student('d1000000-0000-0000-0000-00000000000a') -> 'memory' ->> 'summary'), 'Likes worked examples.', 'the owner sees the student''s portfolio');
select is(jsonb_array_length(public.owner_student('d1000000-0000-0000-0000-00000000000a') -> 'chats'), 1, 'including their chats');
select is((select count(*)::int from public.owner_safety_flags()), 1, 'the owner sees the safety flags');
select lives_ok($$select public.owner_set_config('credit_prices', '{"chat":2}')$$, 'the owner can change the credit prices');
reset role;
select is((select count(*)::int from public.audit_logs where action = 'student.viewed'), 2, 'every look at a student is logged');

select * from finish();
rollback;
