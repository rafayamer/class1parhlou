-- Premium credits and promo codes (2026-11-21).
--   Premium (Rs 3,999 a month): 1,000 credits a month for everything the AI does, worth US$3.50 of AI time. Every AI call is
--   charged at what it really cost. The Premium month runs from the day the student paid and stops when Premium ends.
--   Free: the first quarter of each book, and no AI (free_monthly_usd = 0).
--   Promo codes: the owner makes codes that give Premium for a number of days. Only a SHA-256 hash of each code is stored.
insert into public.app_config (key, value) values
  ('monthly_credits', '1000'::jsonb),
  ('monthly_usd_per_student', '3.5'::jsonb),
  ('free_monthly_usd', '0'::jsonb)
on conflict (key) do update set value = excluded.value, updated_at = now();
-- GPT-6 Luna's published prices (US$ per million tokens). They were 0, which counted every AI call as free, so no allowance
-- or daily limit ever filled up. Prices the owner has already set are kept.
update public.app_config set value = '0.10'::jsonb, updated_at = now() where key = 'usd_per_million_input' and (value #>> '{}')::numeric = 0;
update public.app_config set value = '0.50'::jsonb, updated_at = now() where key = 'usd_per_million_output' and (value #>> '{}')::numeric = 0;
insert into public.app_config (key, value) values ('usd_per_million_input', '0.10'::jsonb), ('usd_per_million_output', '0.50'::jsonb)
on conflict (key) do nothing;

-- Why an AI call failed ("openai_429_insufficient_quota", "invalid_reply"…), shown in the owner panel's AI log.
alter table public.ai_usage add column if not exists error text check (error is null or length(error) <= 120);

-- The first day of the Premium month that today falls in: paid_until minus whole months (31 March - 1 month = 28 February).
create or replace function private.premium_month_start(p_until date) returns date
language sql stable set search_path = '' as $$
  select coalesce(max(d), current_date) from (
    select (p_until - make_interval(months => n))::date as d from generate_series(1, 240) as n) x
  where d <= current_date
$$;

-- What the student's plan is, the price, the credits Premium gives, where to send money, and any request still waiting.
create or replace function public.my_plan() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := auth.uid(); s public.subscriptions; p public.payment_requests;
  cfg jsonb := (select jsonb_object_agg(key, value) from public.app_config
                where key in ('plus_price_pkr', 'payment_accounts', 'ads_enabled', 'free_monthly_usd', 'monthly_usd_per_student', 'monthly_credits'));
begin
  if v is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  select * into s from public.subscriptions where user_id = v;
  select * into p from public.payment_requests where user_id = v order by created_at desc limit 1;
  return jsonb_build_object(
    'plan', case when s.paid_until >= current_date then 'plus' else 'free' end,
    'paidUntil', s.paid_until,
    'pricePkr', coalesce((cfg ->> 'plus_price_pkr')::int, 3999),
    'accounts', coalesce(cfg -> 'payment_accounts', '{}'::jsonb),
    'adsEnabled', coalesce((cfg ->> 'ads_enabled')::boolean, true),
    'freeUsd', coalesce((cfg ->> 'free_monthly_usd')::numeric, 0),
    'plusUsd', coalesce((cfg ->> 'monthly_usd_per_student')::numeric, 3.5),
    'monthlyCredits', coalesce((cfg ->> 'monthly_credits')::int, 1000),
    'lastRequest', case when p.id is null then null else jsonb_build_object(
      'status', p.status, 'months', p.months, 'amount', p.amount_pkr, 'txn', p.txn_ref, 'note', p.note, 'at', p.created_at) end);
end $$;

-- ── Promo codes ─────────────────────────────────────────────────────────────────────────────────────────────────────
create table public.promo_codes (
  id bigint generated always as identity primary key,
  code_hash text not null unique check (code_hash ~ '^[0-9a-f]{64}$'),
  hint text not null default '' check (length(hint) <= 8),
  days int not null check (days between 1 and 366),
  uses_left int not null check (uses_left >= 0),
  note text not null default '' check (length(note) <= 120),
  expires_at timestamptz,
  disabled boolean not null default false,
  created_at timestamptz not null default now()
);
create table public.promo_redemptions (
  code_id bigint not null references public.promo_codes (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (code_id, user_id)
);
create table public.promo_attempts (
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index promo_attempts_user on public.promo_attempts (user_id, created_at);
alter table public.promo_codes enable row level security;
alter table public.promo_redemptions enable row level security;
alter table public.promo_attempts enable row level security;
-- No policies: students and the owner go through the functions below.

-- A code as typed ("ph-1a2b 3c4d…") becomes its hash: capitals, without spaces or dashes.
create or replace function private.promo_hash(p_code text) returns text
language sql immutable set search_path = '' as $$
  select encode(pg_catalog.sha256(convert_to(upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g')), 'UTF8')), 'hex')
$$;

-- The owner's test code (the code itself was given to the owner privately; only its hash is here). 90 days of Premium, 5 accounts.
insert into public.promo_codes (code_hash, hint, days, uses_left, note, expires_at)
values ('019df52ea80de2bb1e8c71e154d14f2087ed1339932dbe0214f89ef8feee7040', 'DC67', 90, 5, 'Owner test code', '2027-06-30T00:00:00Z');

-- A student redeems a code: Premium for its days, added after any Premium they already have. Each code once per account,
-- and at most 10 wrong codes an hour.
create or replace function public.redeem_promo(p_code text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := auth.uid(); c public.promo_codes;
begin
  if v is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  if (select count(*) from public.promo_attempts a where a.user_id = v and a.created_at > now() - interval '1 hour') >= 10 then
    raise exception 'TOO_MANY_TRIES' using errcode = '54000';
  end if;
  select * into c from public.promo_codes where code_hash = private.promo_hash(p_code) for update;
  if not found or c.disabled or c.uses_left <= 0 or (c.expires_at is not null and c.expires_at <= now()) then
    insert into public.promo_attempts (user_id) values (v);
    raise exception 'CODE_NOT_VALID' using errcode = '22023';
  end if;
  if exists (select 1 from public.promo_redemptions r where r.code_id = c.id and r.user_id = v) then
    raise exception 'CODE_ALREADY_USED' using errcode = '22023';
  end if;
  insert into public.promo_redemptions (code_id, user_id) values (c.id, v);
  update public.promo_codes set uses_left = uses_left - 1 where id = c.id;
  insert into public.subscriptions (user_id, paid_until) values (v, current_date + c.days)
  on conflict (user_id) do update set
    paid_until = greatest(public.subscriptions.paid_until, current_date) + c.days, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail)
  values (v, 'promo.redeemed', c.id::text, jsonb_build_object('days', c.days));
  return public.my_plan();
end $$;

-- Owner: make a code. The code is shown once and never stored, only its hash.
create or replace function public.owner_promo_create(p_days int, p_uses int, p_note text default '', p_expires timestamptz default null)
returns text
language plpgsql volatile security definer set search_path = '' as $$
declare v_code text;
begin
  perform private.require_owner();
  if p_days is null or p_uses is null or p_days not between 1 and 366 or p_uses not between 1 and 10000 then
    raise exception 'BAD_PROMO' using errcode = '22023';
  end if;
  -- The first 12 hex digits of a random UUID are all random (the version digit comes later): 48 bits.
  v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
  v_code :='PH-' || substr(v_code, 1, 4) || '-' || substr(v_code, 5, 4) || '-' || substr(v_code, 9, 4);
  insert into public.promo_codes (code_hash, hint, days, uses_left, note, expires_at)
  values (private.promo_hash(v_code), right(v_code, 4), p_days, p_uses, left(coalesce(p_note, ''), 120), p_expires);
  insert into public.audit_logs (actor_id, action, target, detail)
  values (auth.uid(), 'promo.created', right(v_code, 4), jsonb_build_object('days', p_days, 'uses', p_uses));
  return v_code;
end $$;

create or replace function public.owner_promos() returns table (
  id bigint, hint text, days int, uses_left int, redeemed bigint, note text, expires_at timestamptz, disabled boolean, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query select c.id, c.hint, c.days, c.uses_left, (select count(*) from public.promo_redemptions r where r.code_id = c.id),
    c.note, c.expires_at, c.disabled, c.created_at from public.promo_codes c order by c.created_at desc limit 200;
end $$;

create or replace function public.owner_promo_disable(p_id bigint) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  update public.promo_codes set disabled = true where id = p_id;
  if not found then raise exception 'NOT_FOUND' using errcode = 'P0002'; end if;
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'promo.disabled', p_id::text, '{}'::jsonb);
end $$;

-- ── Cost estimates ──────────────────────────────────────────────────────────────────────────────────────────────────
-- The average real cost of each kind of AI call, so the app can say "about 40 credits" before a paper. Gateway only.
create or replace function public.ai_task_costs(p_since timestamptz) returns table (task text, avg_usd numeric, calls bigint)
language sql stable security definer set search_path = '' as $$
  select u.task, avg(u.usd)::numeric(12, 6), count(*) from public.ai_usage u
  where u.created_at >= p_since and u.ok and u.usd > 0 group by u.task
$$;

-- "View as student": the AI use of the student's Premium month (from the day they paid), and the credits it is worth.
create or replace function public.owner_student(p_user uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r jsonb; v_plus boolean; v_until date; v_from timestamptz;
begin
  perform private.require_owner();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'student.viewed', p_user::text, '{}'::jsonb);
  select s.paid_until into v_until from public.subscriptions s where s.user_id = p_user;
  v_plus := coalesce(v_until >= current_date, false);
  v_from := case when v_plus then private.premium_month_start(v_until)::timestamptz else date_trunc('month', now()) end;
  select jsonb_build_object(
    'profile', (select to_jsonb(p) from public.student_profiles p where p.user_id = p_user),
    'settings', (select s.settings from public.learner_settings s where s.user_id = p_user),
    'events', coalesce((select jsonb_agg(jsonb_build_object('at', e.at, 'key', e.key, 'code', e.code) order by e.at desc)
                        from (select * from public.learner_events where user_id = p_user order by at desc limit 1000) e), '[]'::jsonb),
    'marks', coalesce((select jsonb_agg(to_jsonb(m) order by m.created_at desc)
                       from (select * from public.ai_marks where user_id = p_user order by created_at desc limit 500) m), '[]'::jsonb),
    'memory', (select jsonb_build_object('summary', m.summary, 'updated_at', m.updated_at) from public.student_memory m where m.user_id = p_user),
    'chats', coalesce((select jsonb_agg(jsonb_build_object('role', c.role, 'text', c.text, 'at', c.created_at) order by c.created_at)
                       from (select * from public.chat_messages where user_id = p_user and created_at > now() - interval '30 days'
                             order by created_at desc limit 200) c), '[]'::jsonb),
    'credits', coalesce((select jsonb_agg(jsonb_build_object('kind', l.kind, 'amount', l.amount, 'task', l.task, 'at', l.created_at, 'expires', l.expires_at) order by l.created_at desc)
                         from (select * from public.credit_ledger where user_id = p_user order by created_at desc limit 300) l), '[]'::jsonb),
    'flags', coalesce((select jsonb_agg(to_jsonb(f) order by f.created_at desc) from public.safety_flags f where f.user_id = p_user), '[]'::jsonb),
    'feedback', coalesce((select jsonb_agg(to_jsonb(f) order by f.created_at desc) from public.feedback f where f.user_id = p_user), '[]'::jsonb),
    'ai_month', jsonb_build_object(
      'plan', case when v_plus then 'plus' else 'free' end,
      'paid_until', v_until,
      'month_start', v_from::date,
      'monthly_credits', coalesce((select (c.value #>> '{}')::int from public.app_config c where c.key = 'monthly_credits'), 1000),
      'spent_usd', (select coalesce(sum(u.usd), 0) from public.ai_usage u where u.caller = p_user::text and u.created_at >= v_from),
      'calls', (select count(*) from public.ai_usage u where u.caller = p_user::text and u.created_at >= v_from),
      'allowance_usd', coalesce((select (c.value #>> '{}')::numeric from public.app_config c
                                 where c.key = case when v_plus then 'monthly_usd_per_student' else 'free_monthly_usd' end),
                                case when v_plus then 3.5 else 0 end))
  ) into r;
  return r;
end $$;

revoke all on function public.redeem_promo(text), public.owner_promo_create(int, int, text, timestamptz), public.owner_promos(),
  public.owner_promo_disable(bigint), public.ai_task_costs(timestamptz) from public, anon, authenticated;
grant execute on function public.redeem_promo(text), public.owner_promo_create(int, int, text, timestamptz), public.owner_promos(),
  public.owner_promo_disable(bigint) to authenticated;
grant execute on function public.ai_task_costs(timestamptz) to service_role;
revoke all on function private.promo_hash(text), private.premium_month_start(date) from public;
