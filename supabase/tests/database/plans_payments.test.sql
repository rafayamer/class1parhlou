-- Free and Plus plans with hand-checked JazzCash/Easypaisa payments.
begin;
create extension if not exists pgtap;
select plan(12);

select public.set_owner_email('boss@example.test');
insert into auth.users (id, email) values
  ('f1000000-0000-0000-0000-00000000000a', 'hira@example.test'),
  ('f1000000-0000-0000-0000-0000000000ee', 'boss@example.test');
create temp table req (id bigint) on commit drop;
grant all on req to authenticated;

set local role authenticated;
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-00000000000a', true);
select is(public.my_plan() ->> 'plan', 'free', 'a new student is on the free plan');
select throws_ok($$select public.payment_request('jazzcash', 2, 'ABC12345')$$, '22023', null, 'only 1, 3 or 12 months');
select throws_ok($$select public.payment_request('jazzcash', 1, '12')$$, '22023', null, 'a transaction ID is needed');
select is((public.payment_request('jazzcash', 3, ' 0123-4567 89 ') -> 'lastRequest' ->> 'status'), 'pending', 'a payment waits for the owner');
select is((public.my_plan() -> 'lastRequest' ->> 'amount')::int, 1497, 'the amount is the price times the months');
select throws_ok($$select public.payment_request('easypaisa', 1, 'XYZ98765')$$, '22023', null, 'one waiting request at a time');
select throws_ok($$select public.owner_payments()$$, '42501', null, 'students cannot see payments');
select is((select count(*)::int from public.payment_requests), 0, 'payment rows are not readable directly');

-- The owner approves it.
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-0000000000ee', true);
insert into req select id from public.owner_payments() where txn_ref = '0123456789';
select is((select count(*)::int from req), 1, 'the owner sees the waiting payment with the cleaned transaction ID');
select lives_ok($$select public.owner_payment_decide((select id from req), true)$$, 'the owner approves it');
select throws_ok($$select public.owner_payment_decide((select id from req), false)$$, '22023', null, 'a decided payment cannot be decided again');

select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-00000000000a', true);
select is(public.my_plan() ->> 'plan', 'plus', 'the student is on Plus after approval');

select * from finish();
rollback;
