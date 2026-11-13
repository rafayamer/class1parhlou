-- Book bank: learners see published questions but never solutions; unchecked items cannot be published.
begin;
create extension if not exists pgtap;
select plan(9);

insert into auth.users (id, email) values
  ('a1000000-0000-0000-0000-00000000000a', 'learner@example.test'),
  ('e1000000-0000-0000-0000-000000000001', 'editor@example.test');
insert into public.tenants (id, name, kind) values
  ('01000000-0000-0000-0000-0000000000aa', 'Family', 'family'),
  ('01000000-0000-0000-0000-0000000000ff', 'Platform', 'platform');
insert into public.memberships (tenant_id, user_id, role) values
  ('01000000-0000-0000-0000-0000000000aa', 'a1000000-0000-0000-0000-00000000000a', 'learner'),
  ('01000000-0000-0000-0000-0000000000ff', 'e1000000-0000-0000-0000-000000000001', 'editor');
insert into public.boards (id, code, name, status) values ('21000000-0000-0000-0000-000000000001', 'TB', 'Test', 'PUBLISHED');
insert into public.examination_targets (id, board_id, grade, session_label, status) values
  ('22000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', 9, 'T', 'PUBLISHED');
insert into public.subjects (id, board_id, grade, code, name, status) values
  ('23000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', 9, 'M', 'Maths', 'PUBLISHED');
insert into public.textbook_editions (id, subject_id, board_id, grade, title, edition_label, status) values
  ('24000000-0000-0000-0000-000000000001', '23000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', 9, 'B', 'v1', 'PUBLISHED');
insert into public.chapters (id, edition_id, number, title, status) values
  ('25000000-0000-0000-0000-000000000001', '24000000-0000-0000-0000-000000000001', 1, 'C', 'PUBLISHED');
insert into public.enrollments (learner_id, tenant_id, examination_target_id, edition_id, pathway) values
  ('a1000000-0000-0000-0000-00000000000a', '01000000-0000-0000-0000-0000000000aa', '22000000-0000-0000-0000-000000000001', '24000000-0000-0000-0000-000000000001', 'test_series');

insert into public.book_items (id, chapter_id, kind, exercise, number, text, marks) values
  ('26000000-0000-0000-0000-000000000001', '25000000-0000-0000-0000-000000000001', 'exercise', '1.1', '1', 'Synthetic question one', 3),
  ('26000000-0000-0000-0000-000000000002', '25000000-0000-0000-0000-000000000001', 'exercise', '1.1', '2', 'Synthetic question two', 3),
  ('26000000-0000-0000-0000-000000000003', '25000000-0000-0000-0000-000000000001', 'exercise', '1.1', '3', 'Synthetic question three', 3),
  ('26000000-0000-0000-0000-000000000004', '25000000-0000-0000-0000-000000000001', 'exercise', '1.1', '4', 'Synthetic question four', 3);
insert into public.book_solutions (item_id, model_answer, rubric, book_final_answer, origin, second_check_ok, matches_book_key) values
  ('26000000-0000-0000-0000-000000000001', '["line"]', '[{"id":"a","marks":2},{"id":"b","marks":1}]', '5', 'ai_drafted', true, true),
  ('26000000-0000-0000-0000-000000000002', '["line"]', '[{"id":"a","marks":3}]', '5', 'ai_drafted', false, true),
  ('26000000-0000-0000-0000-000000000003', '["line"]', '[{"id":"a","marks":3}]', '5', 'ai_drafted', true, false),
  ('26000000-0000-0000-0000-000000000004', '["line"]', '[{"id":"a","marks":2}]', null, 'book', false, false);

select lives_ok($$update public.book_items set status = 'PUBLISHED' where number = '1'$$,
  'double-checked item that matches the book key can be published');
select throws_ok($$update public.book_items set status = 'PUBLISHED' where number = '2'$$,
  'P0001', null, 'AI-drafted solution without a second check cannot be published');
select throws_ok($$update public.book_items set status = 'PUBLISHED' where number = '3'$$,
  'P0001', null, 'solution that does not match the book answer key cannot be published');
select throws_ok($$update public.book_items set status = 'PUBLISHED' where number = '4'$$,
  'P0001', null, 'rubric that does not add up to the question marks cannot be published');
select throws_ok(
  $$insert into public.book_items (chapter_id, kind, exercise, number, text, marks, status)
    values ('25000000-0000-0000-0000-000000000001', 'exercise', '1.1', '9', 'No solution', 3, 'PUBLISHED')$$,
  'P0001', null, 'item with no solution cannot be published');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-00000000000a', true);
select is((select count(*)::int from public.book_items), 1, 'learner sees only the published book question');
select is((select count(*)::int from public.book_solutions), 0, 'learner can never read book solutions');
select is((select count(*)::int from public.ai_usage), 0, 'learner cannot read AI usage');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', 'e1000000-0000-0000-0000-000000000001', true);
select is((select count(*)::int from public.book_solutions), 4, 'platform editor can read solutions');
reset role;

select * from finish();
rollback;
