-- Friends, leagues, nudges and class groups.
begin;
create extension if not exists pgtap;
select plan(18);

insert into auth.users (id, email) values
  ('e1000000-0000-0000-0000-00000000000a', 'ayesha@example.test'),
  ('e1000000-0000-0000-0000-00000000000b', 'bilal@example.test'),
  ('e1000000-0000-0000-0000-00000000000c', 'sara@example.test');

set local role authenticated;
-- Ayesha
select set_config('request.jwt.claim.sub', 'e1000000-0000-0000-0000-00000000000a', true);
select ok(length(public.social_me() ->> 'friendCode') = 6, 'a student gets a 6-character friend code');
create temp table codes (who text, code text) on commit drop;
grant all on codes to authenticated;
insert into codes values ('a', public.social_me() ->> 'friendCode');
select is((public.social_post_week(120, 4) -> 'rows' -> 0 ->> 'xp')::int, 120, 'posting this week''s XP puts the student on a league board');
select is((select count(*)::int from public.weekly_xp), 0, 'league tables show nothing when read directly');

-- Bilal
select set_config('request.jwt.claim.sub', 'e1000000-0000-0000-0000-00000000000b', true);
insert into codes values ('b', public.social_me() ->> 'friendCode');
select public.social_post_week(80, 2);
select is(jsonb_array_length(public.league_board() -> 'rows'), 2, 'both students share a league group');
select is((public.league_board() -> 'rows' -> 0 ->> 'name'), 'Learner', 'others see only a first name');
select is(public.friend_add('ZZZZZZ') ->> 'error', 'CODE_NOT_FOUND', 'an unknown friend code is refused');
select throws_ok($$select public.friend_add((select code from codes where who = 'b'))$$, '22023', null, 'you cannot add yourself');
select is(jsonb_array_length(public.friend_add((select code from codes where who = 'a'))), 1, 'adding by code makes a friend');
select lives_ok($$select public.nudge((select code from codes where who = 'a'))$$, 'a friend can be nudged');
select lives_ok($$select public.nudge((select code from codes where who = 'a'))$$, 'a second nudge the same day is ignored, not an error');

-- Ayesha sees Bilal as a friend, and one nudge.
select set_config('request.jwt.claim.sub', 'e1000000-0000-0000-0000-00000000000a', true);
select is((public.friends_list() -> 0 ->> 'xp')::int, 80, 'friendship works both ways, with this week''s XP');
select is(jsonb_array_length(public.my_nudges()), 1, 'only one nudge arrives');

-- Sara is not a friend: she can't nudge; she joins Ayesha's class group by code.
create temp table grp (code text) on commit drop;
grant all on grp to authenticated;
insert into grp select public.group_create('9th Blue') ->> 'code';
select set_config('request.jwt.claim.sub', 'e1000000-0000-0000-0000-00000000000c', true);
select throws_ok($$select public.nudge((select code from codes where who = 'a'))$$, '42501', null, 'only friends can nudge');
select public.group_join((select code from grp));
select is(jsonb_array_length(public.my_groups() -> 0 -> 'rows'), 2, 'a class group has its own board');
-- Guessing codes: 20 wrong tries a day, then locked.
select public.friend_add('WRONG' || i) from generate_series(1, 20) i;
select throws_ok($$select public.friend_add('ANYONE')$$, '54000', null, 'after 20 wrong codes in a day, no more tries');
select throws_ok($$select public.group_join('ANYONE')$$, '54000', null, 'the same limit covers group codes');
select throws_ok($$select public.group_create('9th Chutiya class')$$, '22023', 'NAME_NOT_ALLOWED', 'abusive group names are refused');
select throws_ok($$select public.group_create('<script>x</script>')$$, '22023', 'NAME_NOT_ALLOWED', 'markup in names is refused');

select * from finish();
rollback;
