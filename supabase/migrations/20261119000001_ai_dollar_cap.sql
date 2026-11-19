-- Each student's AI use in real money: every AI call records its cost in US dollars, and the gateway stops AI for a student who
-- has used their monthly allowance (default $3, set in the owner panel as monthly_usd_per_student; 0 = no cap).
alter table public.ai_usage add column if not exists usd numeric(12, 6) not null default 0;
insert into public.app_config (key, value) values ('monthly_usd_per_student', '3'::jsonb) on conflict (key) do nothing;

-- What a student has spent this calendar month (UTC), for the owner panel's Learners page.
create or replace function public.owner_ai_spend(p_since timestamptz default date_trunc('month', now()))
returns table (caller text, usd numeric, calls bigint)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query
  select u.caller, sum(u.usd)::numeric(12, 4), count(*) from public.ai_usage u
  where u.created_at >= p_since
  group by u.caller order by 2 desc limit 500;
end $$;
revoke all on function public.owner_ai_spend(timestamptz) from public, anon;
grant execute on function public.owner_ai_spend(timestamptz) to authenticated;

-- The owner can change the monthly allowance in the panel.
create or replace function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key = 'ai_enabled' and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total', 'daily_photo_questions', 'monthly_credits', 'credit_months')
        and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 1000000 then null;
  elsif p_key in ('usd_per_million_input', 'usd_per_million_output', 'pkr_per_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  elsif p_key = 'monthly_usd_per_student' and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 100 then null;
  elsif p_key = 'credit_prices' and jsonb_typeof(p_value) = 'object' and length(p_value::text) < 2000 then null;
  else
    raise exception 'SETTING_NOT_ALLOWED: %', p_key using errcode = '22023';
  end if;
  insert into public.app_config (key, value) values (p_key, p_value)
  on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'config.changed', p_key, jsonb_build_object('value', p_value));
end $$;
