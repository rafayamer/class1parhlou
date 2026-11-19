-- Security hardening (2026-11-19 review).
-- 1. Friend and group codes can't be guessed: 20 wrong codes per student per day, then locked until tomorrow. Wrong codes are
--    answered with {"error": ...} instead of an exception, so the try is recorded (an exception would undo it).
-- 3. Class group names other children see: letters, digits and simple punctuation only, and no abusive words.
-- 4. Payment records are kept (without the account) after a student deletes their account: needed for tax records.
-- 2. A project-wide daily AI spending ceiling the gateway enforces (default $1 a day), settable in the owner panel.
insert into public.app_config (key, value) values ('daily_usd_total', '1'::jsonb) on conflict (key) do nothing;

create table if not exists public.social_code_tries (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  at timestamptz not null default now()
);
create index if not exists social_code_tries_user on public.social_code_tries (user_id, at);
alter table public.social_code_tries enable row level security;

create or replace function private.code_tries_left() returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  if (select count(*) from public.social_code_tries where user_id = auth.uid() and at > now() - interval '1 day') >= 20 then
    raise exception 'TOO_MANY_TRIES' using errcode = '54000';
  end if;
end $$;

create or replace function public.friend_add(p_code text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); f uuid;
begin
  perform public.social_me();
  perform private.code_tries_left();
  select user_id into f from public.social_profiles where friend_code = upper(btrim(p_code));
  if f is null then
    insert into public.social_code_tries (user_id) values (v);
    return jsonb_build_object('error', 'CODE_NOT_FOUND');
  end if;
  if f = v then raise exception 'THAT_IS_YOU' using errcode = '22023'; end if;
  if (select count(*) from public.friendships where user_id = v) >= 100 then raise exception 'TOO_MANY_FRIENDS' using errcode = '22023'; end if;
  insert into public.friendships (user_id, friend_id) values (v, f), (f, v) on conflict do nothing;
  return public.friends_list();
end $$;

create or replace function public.group_join(p_code text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); g public.class_groups;
begin
  perform public.social_me();
  perform private.code_tries_left();
  select * into g from public.class_groups where code = upper(btrim(p_code));
  if not found then
    insert into public.social_code_tries (user_id) values (v);
    return jsonb_build_object('error', 'CODE_NOT_FOUND');
  end if;
  if (select count(*) from public.class_members where group_id = g.id) >= 200 then raise exception 'GROUP_FULL' using errcode = '22023'; end if;
  insert into public.class_members (group_id, user_id) values (g.id, v) on conflict do nothing;
  return jsonb_build_object('code', g.code, 'name', g.name);
end $$;

-- Words not allowed in names other students see (English and Roman Urdu; matched inside words, case-insensitive).
create or replace function private.name_ok(p text) returns boolean language sql immutable set search_path = '' as $$
  select p ~ '^[[:alnum:] .,''()&#+-]+$'
     and lower(regexp_replace(p, '[^[:alnum:]]', '', 'g')) !~ '(fuck|shit|bitch|bastard|dick|pussy|cunt|slut|whore|porn|sex|nude|rape|kill|terror|bomb|nigg|chutiya|chootiya|bhenchod|behenchod|benchod|madarchod|maderchod|gandu|harami|kutta|kutti|randi|lund|bhosd|chod)';
$$;

create or replace function public.group_create(p_name text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); g public.class_groups; v_name text := left(btrim(coalesce(p_name, '')), 40);
begin
  perform public.social_me();
  if v_name = '' then raise exception 'NAME_REQUIRED' using errcode = '22023'; end if;
  if not private.name_ok(v_name) then raise exception 'NAME_NOT_ALLOWED' using errcode = '22023'; end if;
  if (select count(*) from public.class_groups where created_by = v) >= 10 then raise exception 'TOO_MANY_GROUPS' using errcode = '22023'; end if;
  loop
    begin
      insert into public.class_groups (code, name, created_by) values (private.new_code(), v_name, v) returning * into g;
      exit;
    exception when unique_violation then null;
    end;
  end loop;
  insert into public.class_members (group_id, user_id) values (g.id, v);
  return jsonb_build_object('code', g.code, 'name', g.name);
end $$;

-- Owner: remove a group with an unsuitable name (from a student's report).
create or replace function public.owner_group_remove(p_code text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  delete from public.class_groups where code = upper(btrim(p_code));
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'group.removed', upper(btrim(p_code)), '{}'::jsonb);
end $$;

-- Payment records outlive the account (tax records); the link to the student is cleared.
alter table public.payment_requests alter column user_id drop not null;
alter table public.payment_requests drop constraint if exists payment_requests_user_id_fkey;
alter table public.payment_requests add constraint payment_requests_user_id_fkey
  foreign key (user_id) references auth.users (id) on delete set null;

-- Deleted accounts' payments still show (for tax records).
create or replace function public.owner_payments(p_status text default 'pending') returns table (
  id bigint, email text, method text, months int, amount_pkr int, txn_ref text, payer text, status text, note text,
  created_at timestamptz, paid_until date)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query
  select r.id, coalesce(u.email::text, '(deleted account)'), r.method, r.months, r.amount_pkr, r.txn_ref, r.payer, r.status, r.note, r.created_at, s.paid_until
  from public.payment_requests r left join auth.users u on u.id = r.user_id
  left join public.subscriptions s on s.user_id = r.user_id
  where p_status = 'all' or r.status = p_status
  order by r.created_at desc limit 300;
end $$;

revoke all on function private.code_tries_left(), private.name_ok(text) from public, anon;
revoke all on function public.owner_group_remove(text) from public, anon;
grant execute on function public.owner_group_remove(text) to authenticated;

-- Settings the owner can change, now with the project-wide daily AI spending ceiling (daily_usd_total, US $; 0 = none).
create or replace function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key in ('ai_enabled', 'ads_enabled') and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total', 'daily_photo_questions', 'monthly_credits', 'credit_months')
        and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 1000000 then null;
  elsif p_key in ('usd_per_million_input', 'usd_per_million_output', 'pkr_per_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  elsif p_key in ('monthly_usd_per_student', 'free_monthly_usd', 'daily_usd_total') and jsonb_typeof(p_value) = 'number'
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

