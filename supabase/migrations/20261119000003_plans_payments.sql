-- Free and Plus plans, paid by hand through JazzCash or Easypaisa (no registered business needed: the student sends money to the
-- founder's own wallet, types the transaction ID, and the founder approves it in the owner panel). Google Play Billing comes later.
--   Free: a smaller monthly AI allowance (free_monthly_usd, default $0.50) and child-safe ads outside lessons and papers.
--   Plus: the full allowance (monthly_usd_per_student, default $3) and no ads, until paid_until.
insert into public.app_config (key, value) values
  ('free_monthly_usd', '0.5'::jsonb),
  ('plus_price_pkr', '499'::jsonb),
  ('ads_enabled', 'true'::jsonb),
  ('payment_accounts', '{}'::jsonb)
on conflict (key) do nothing;

create table public.subscriptions (
  user_id uuid primary key references auth.users (id) on delete cascade,
  plan text not null default 'plus' check (plan in ('plus')),
  paid_until date not null,
  updated_at timestamptz not null default now()
);
alter table public.subscriptions enable row level security;

create table public.payment_requests (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  method text not null check (method in ('jazzcash', 'easypaisa', 'bank')),
  months int not null check (months between 1 and 12),
  amount_pkr int not null check (amount_pkr between 1 and 1000000),
  txn_ref text not null check (length(txn_ref) between 4 and 40),
  payer text not null default '' check (length(payer) <= 60),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  note text not null default '' check (length(note) <= 300),
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  unique (method, txn_ref)
);
create index payment_requests_status on public.payment_requests (status, created_at);
alter table public.payment_requests enable row level security;
-- No policies: students and the owner go through the functions below.

-- What the student's plan is, the price, where to send money, and any request still waiting.
create or replace function public.my_plan() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := auth.uid(); s public.subscriptions; p public.payment_requests;
  cfg jsonb := (select jsonb_object_agg(key, value) from public.app_config
                where key in ('plus_price_pkr', 'payment_accounts', 'ads_enabled', 'free_monthly_usd', 'monthly_usd_per_student'));
begin
  if v is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  select * into s from public.subscriptions where user_id = v;
  select * into p from public.payment_requests where user_id = v order by created_at desc limit 1;
  return jsonb_build_object(
    'plan', case when s.paid_until >= current_date then 'plus' else 'free' end,
    'paidUntil', s.paid_until,
    'pricePkr', coalesce((cfg ->> 'plus_price_pkr')::int, 499),
    'accounts', coalesce(cfg -> 'payment_accounts', '{}'::jsonb),
    'adsEnabled', coalesce((cfg ->> 'ads_enabled')::boolean, true),
    'freeUsd', coalesce((cfg ->> 'free_monthly_usd')::numeric, 0.5),
    'plusUsd', coalesce((cfg ->> 'monthly_usd_per_student')::numeric, 3),
    'lastRequest', case when p.id is null then null else jsonb_build_object(
      'status', p.status, 'months', p.months, 'amount', p.amount_pkr, 'txn', p.txn_ref, 'note', p.note, 'at', p.created_at) end);
end $$;

-- "I've paid": records the transaction ID for the owner to check. One waiting request at a time.
create or replace function public.payment_request(p_method text, p_months int, p_txn text, p_payer text default '') returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := auth.uid(); price int; txn text := upper(regexp_replace(coalesce(p_txn, ''), '[^A-Za-z0-9]', '', 'g'));
begin
  if v is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  if p_method not in ('jazzcash', 'easypaisa', 'bank') then raise exception 'BAD_METHOD' using errcode = '22023'; end if;
  if p_months not in (1, 3, 12) then raise exception 'BAD_MONTHS' using errcode = '22023'; end if;
  if length(txn) < 4 or length(txn) > 40 then raise exception 'BAD_TRANSACTION_ID' using errcode = '22023'; end if;
  if exists (select 1 from public.payment_requests where user_id = v and status = 'pending') then
    raise exception 'ALREADY_WAITING' using errcode = '22023'; end if;
  if exists (select 1 from public.payment_requests where method = p_method and txn_ref = txn) then
    raise exception 'TRANSACTION_USED' using errcode = '23505'; end if;
  select coalesce((value #>> '{}')::int, 499) into price from public.app_config where key = 'plus_price_pkr';
  insert into public.payment_requests (user_id, method, months, amount_pkr, txn_ref, payer)
  values (v, p_method, p_months, coalesce(price, 499) * p_months, txn, left(coalesce(p_payer, ''), 60));
  return public.my_plan();
end $$;

-- Owner: the payments to check (waiting first), with the student's email so the founder can match their wallet's SMS.
create or replace function public.owner_payments(p_status text default 'pending') returns table (
  id bigint, email text, method text, months int, amount_pkr int, txn_ref text, payer text, status text, note text,
  created_at timestamptz, paid_until date)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query
  select r.id, u.email::text, r.method, r.months, r.amount_pkr, r.txn_ref, r.payer, r.status, r.note, r.created_at, s.paid_until
  from public.payment_requests r join auth.users u on u.id = r.user_id
  left join public.subscriptions s on s.user_id = r.user_id
  where p_status = 'all' or r.status = p_status
  order by r.created_at desc limit 300;
end $$;

-- Owner: approve (adds the months to Plus, from today or from the current end date) or reject with a note.
create or replace function public.owner_payment_decide(p_id bigint, p_approve boolean, p_note text default '') returns void
language plpgsql volatile security definer set search_path = '' as $$
declare r public.payment_requests;
begin
  perform private.require_owner();
  select * into r from public.payment_requests where id = p_id for update;
  if not found then raise exception 'NOT_FOUND' using errcode = 'P0002'; end if;
  if r.status <> 'pending' then raise exception 'ALREADY_DECIDED' using errcode = '22023'; end if;
  update public.payment_requests set status = case when p_approve then 'approved' else 'rejected' end,
    note = left(coalesce(p_note, ''), 300), decided_at = now() where id = p_id;
  if p_approve then
    insert into public.subscriptions (user_id, paid_until) values (r.user_id, current_date + make_interval(months => r.months))
    on conflict (user_id) do update set
      paid_until = greatest(public.subscriptions.paid_until, current_date) + make_interval(months => r.months), updated_at = now();
  end if;
  insert into public.audit_logs (actor_id, action, target, detail)
  values (auth.uid(), case when p_approve then 'payment.approved' else 'payment.rejected' end, r.id::text,
          jsonb_build_object('months', r.months, 'amount', r.amount_pkr));
end $$;

-- Settings the owner can now change: the free allowance, the price, ads on/off, and the wallets students pay into.
create or replace function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key in ('ai_enabled', 'ads_enabled') and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total', 'daily_photo_questions', 'monthly_credits', 'credit_months')
        and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 1000000 then null;
  elsif p_key in ('usd_per_million_input', 'usd_per_million_output', 'pkr_per_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  elsif p_key in ('monthly_usd_per_student', 'free_monthly_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100 then null;
  elsif p_key = 'plus_price_pkr' and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  elsif p_key in ('credit_prices', 'payment_accounts') and jsonb_typeof(p_value) = 'object' and length(p_value::text) < 2000 then null;
  else
    raise exception 'SETTING_NOT_ALLOWED: %', p_key using errcode = '22023';
  end if;
  insert into public.app_config (key, value) values (p_key, p_value)
  on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'config.changed', p_key, jsonb_build_object('value', p_value));
end $$;

revoke all on function public.my_plan(), public.payment_request(text, int, text, text),
  public.owner_payments(text), public.owner_payment_decide(bigint, boolean, text) from public, anon;
grant execute on function public.my_plan(), public.payment_request(text, int, text, text),
  public.owner_payments(text), public.owner_payment_decide(bigint, boolean, text) to authenticated;
