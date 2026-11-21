-- App customisation: the owner saves it, every phone can read the public part, nothing private leaks.
begin;
create extension if not exists pgtap;
select plan(11);

select public.set_owner_email('chief@example.test');
insert into auth.users (id, email) values
  ('c3000000-0000-0000-0000-0000000000ee', 'chief@example.test'),
  ('c3000000-0000-0000-0000-00000000000a', 'kid@example.test');

set local role anon;
select is(public.public_app_settings() ->> 'adsEnabled', 'true', 'phones without an account can read the app settings');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'c3000000-0000-0000-0000-00000000000a', true);
select throws_ok($$select public.owner_set_config('app_customization', '{"banner":{"title":"Hacked"}}')$$, '42501', null, 'students cannot change the app');

select set_config('request.jwt.claim.sub', 'c3000000-0000-0000-0000-0000000000ee', true);
select lives_ok($$select public.owner_set_config('app_customization', '{"banner":{"title":"Exams in 30 days"},"features":{"league":{"on":true,"rollout":50}}}')$$,
  'the owner saves the app customisation');
select is(jsonb_array_length(coalesce((select jsonb_agg(h.value) from public.owner_customization_history() h), '[]'::jsonb)), 1, 'each save is kept for restoring');

set local role anon;
select is(public.public_app_settings() -> 'banner' ->> 'title', 'Exams in 30 days', 'the banner reaches phones');
select ok(public.public_app_settings() ->> 'payment_accounts' is null and public.public_app_settings() ->> 'owner_email' is null,
  'nothing private is in the public settings');

-- "View as student" shows this month's AI use against the student's plan allowance.
reset role;
insert into public.ai_usage (caller, task, model, prompt_version, ok, usd) values
  ('c3000000-0000-0000-0000-00000000000a', 'tutor', 'm', 'v', true, 0.12),
  ('c3000000-0000-0000-0000-00000000000a', 'mark', 'm', 'v', true, 0.08),
  ('c3000000-0000-0000-0000-00000000000a', 'mark', 'm', 'v', true, 5.00);
update public.ai_usage set created_at = now() - interval '62 days' where usd = 5.00;
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c3000000-0000-0000-0000-0000000000ee', true);
select is((public.owner_student('c3000000-0000-0000-0000-00000000000a') -> 'ai_month' ->> 'spent_usd')::numeric, 0.20::numeric, 'the owner sees this month''s AI spending only');
select is(public.owner_student('c3000000-0000-0000-0000-00000000000a') -> 'ai_month' ->> 'plan', 'free', 'and the student''s plan');
select is((public.owner_student('c3000000-0000-0000-0000-00000000000a') -> 'ai_month' ->> 'allowance_usd')::numeric, 0.05::numeric, 'and the free plan''s allowance');

-- Premium: Rs 3,999 a month, and a small free AI allowance behind the app's three free tries.
select is((select value from public.app_config where key = 'plus_price_pkr'), '3999'::jsonb, 'Premium costs Rs 3,999 a month');
select is((select value from public.app_config where key = 'free_monthly_usd'), '0.05'::jsonb, 'free students keep only a small AI allowance');

select * from finish();
rollback;
