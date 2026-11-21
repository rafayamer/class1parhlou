-- Premium credits (1,000 a month = $3.50 of AI time, counted from the day the student paid) and promo codes.
begin;
create extension if not exists pgtap;
select plan(22);

select public.set_owner_email('head@example.test');
insert into auth.users (id, email) values
  ('d4000000-0000-0000-0000-0000000000ee', 'head@example.test'),
  ('d4000000-0000-0000-0000-00000000000a', 'sana@example.test'),
  ('d4000000-0000-0000-0000-00000000000b', 'bilal@example.test');
create temp table made (code text) on commit drop;
grant all on made to authenticated;

select is((select value from public.app_config where key = 'monthly_credits'), '1000'::jsonb, 'Premium gives 1,000 credits a month');
select is((select value from public.app_config where key = 'monthly_usd_per_student'), '3.5'::jsonb, '1,000 credits are worth $3.50 of AI time');
select is((select value from public.app_config where key = 'usd_per_million_input'), '0.10'::jsonb, 'GPT-6 Luna input price is set, so AI calls are not counted as free');
select is((select value from public.app_config where key = 'usd_per_million_output'), '0.50'::jsonb, 'and its output price');
select has_column('public', 'ai_usage', 'error', 'the AI log keeps why a call failed');
select is(private.premium_month_start(current_date + 10), (current_date + 10 - interval '1 month')::date, 'the Premium month starts on the day the student paid');
select is(private.premium_month_start('2027-03-31'::date) <= current_date, true, 'and never in the future');
select is((select count(*)::int from public.promo_codes where note = 'Owner test code' and uses_left = 5 and days = 90), 1, 'the owner''s test code is ready, stored only as a hash');

-- Students can't make codes or see them.
set local role authenticated;
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-00000000000a', true);
select throws_ok($$select public.owner_promo_create(30, 1)$$, '42501', null, 'students cannot make codes');
select is((select count(*)::int from public.promo_codes), 0, 'codes are not readable directly');
select throws_ok($$select public.redeem_promo('PH-0000-0000-0000')$$, '22023', 'CODE_NOT_VALID', 'a wrong code is refused');

-- The owner makes a 30-day code for two students.
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-0000000000ee', true);
insert into made select public.owner_promo_create(30, 2, 'Teachers');
select matches((select code from made), '^PH-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$', 'a code looks like PH-XXXX-XXXX-XXXX');
select is((select uses_left from public.owner_promos() where note = 'Teachers'), 2, 'the owner sees the code without the code itself');

-- A student redeems it (typed in small letters with spaces): Premium for 30 days.
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-00000000000a', true);
select is(public.redeem_promo(lower(replace((select code from made), '-', ' '))) ->> 'plan', 'plus', 'the code gives Premium, however it is typed');
select is((public.my_plan() ->> 'paidUntil')::date, current_date + 30, 'for the code''s 30 days');
select is((public.my_plan() ->> 'monthlyCredits')::int, 1000, 'with 1,000 credits a month');
select throws_ok(format('select public.redeem_promo(%L)', (select code from made)), '22023', 'CODE_ALREADY_USED', 'each code once per account');

-- Ten wrong codes in an hour, and the student must wait.
reset role;
insert into public.promo_attempts (user_id) select 'd4000000-0000-0000-0000-00000000000b' from generate_series(1, 10);
set local role authenticated;
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-00000000000b', true);
select throws_ok(format('select public.redeem_promo(%L)', (select code from made)), '54000', 'TOO_MANY_TRIES', 'too many wrong codes: wait an hour');

-- A disabled code stops working.
reset role;
delete from public.promo_attempts;
set local role authenticated;
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-0000000000ee', true);
select lives_ok($$select public.owner_promo_disable((select id from public.owner_promos() where note = 'Teachers'))$$, 'the owner switches a code off');
select set_config('request.jwt.claim.sub', 'd4000000-0000-0000-0000-00000000000b', true);
select throws_ok(format('select public.redeem_promo(%L)', (select code from made)), '22023', 'CODE_NOT_VALID', 'a switched-off code is refused');

-- Estimates: only the gateway can read the average cost of each kind of AI call.
select throws_ok($$select * from public.ai_task_costs(now() - interval '30 days')$$, '42501', null, 'students cannot read AI costs');
reset role;
insert into public.ai_usage (caller, task, model, prompt_version, ok, usd) values
  ('x', 'read_script', 'm', 'v', true, 0.02), ('x', 'read_script', 'm', 'v', true, 0.04), ('x', 'read_script', 'm', 'v', false, 0.9);
set local role service_role;
select is((select avg_usd from public.ai_task_costs(now() - interval '1 day') where task = 'read_script'), 0.03::numeric,
  'the gateway gets the average real cost of reading a page (failed calls left out)');

select * from finish();
rollback;
