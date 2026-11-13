begin;
create extension if not exists pgtap;
select plan(7);

select is(public.publish_content('test-subject', '{"n":1}'), 1, 'first publish is version 1');
select is(public.publish_content('test-subject', '{"n":2}'), 2, 'second publish is version 2');
select is((select count(*)::int from public.app_content where subject_id = 'test-subject' and status = 'PUBLISHED'), 1,
  'only one bundle is published at a time');
insert into public.app_content (subject_id, version, bundle, status) values ('test-subject', 9, '{"draft":true}', 'DRAFT');

set local role anon;
select is((select (bundle->>'n')::int from public.app_content where subject_id = 'test-subject'), 2,
  'the app (public key) sees only the latest published bundle');
select is((select count(*)::int from public.app_config), 0, 'the app cannot read server settings');
select throws_ok($$select public.publish_content('test-subject', '{"n":3}')$$, '42501', null, 'the app cannot publish content');
select throws_ok($$insert into public.app_content (subject_id, version, bundle, status) values ('x', 1, '{}', 'PUBLISHED')$$,
  '42501', null, 'the app cannot insert content');
reset role;

select * from finish();
rollback;
