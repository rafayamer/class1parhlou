-- Audit fixes (20 November 2026).
begin;
create extension if not exists pgtap;
select plan(12);

insert into auth.users (id, email) values
  ('a2000000-0000-0000-0000-00000000000a', 'zara@example.test'),
  ('a2000000-0000-0000-0000-00000000000b', 'rude@example.test');
insert into public.ai_usage (caller, task, model, prompt_version, input_tokens, output_tokens, attempts, ok, usd)
select 'caller-1234567890abcd', 'tutor', 'm', 'v', 1, 1, 1, true, 0.001 from generate_series(1, 1500);

select ok(not has_function_privilege('authenticated', 'private.grant_owner(uuid, text)', 'execute'), 'users cannot call grant_owner');
select ok(not has_function_privilege('authenticated', 'public.ai_spend(text, timestamptz)', 'execute'), 'users cannot read AI spending');
select is(public.ai_spend('caller-1234567890abcd', now() - interval '1 day'), 1.500::numeric, 'spending sums every row, past the API''s 1,000-row page');
select is(public.ai_spend(null, now() - interval '1 day'), 1.500::numeric, 'and the whole app''s spending');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'a2000000-0000-0000-0000-00000000000a', true);
select public.register_learner('Zara Khan');
select lives_ok($$update public.student_profiles set display_name = 'Zara K' where user_id = auth.uid()$$, 'a student can rename themself');
select throws_ok($$update public.student_profiles set tenant_id = gen_random_uuid() where user_id = auth.uid()$$, '42501', null,
  'but cannot move their profile to another organisation');
select is(public.social_me() ->> 'firstName', 'Zara', 'the league shows the first name');
select throws_ok($$select public.social_set_avatar('SHIT')$$, '22023', 'AVATAR_NOT_ALLOWED', 'avatars come from the fixed list only');
select lives_ok($$select public.social_set_avatar('🦁')$$, 'a listed avatar is fine');
select ok((public.social_post_week(999999, 9999) -> 'rows' -> 0 ->> 'xp')::int <= 1500 * 7, 'weekly XP is capped at what real study can earn');

select set_config('request.jwt.claim.sub', 'a2000000-0000-0000-0000-00000000000b', true);
select public.register_learner('Chutiya Boy');
select is(public.social_me() ->> 'firstName', 'Learner', 'an unsuitable name shows as Learner to other students');
reset role;
select is((select streak from public.weekly_xp where user_id = 'a2000000-0000-0000-0000-00000000000a'), 1, 'a streak cannot be longer than the account is old');

select * from finish();
rollback;
