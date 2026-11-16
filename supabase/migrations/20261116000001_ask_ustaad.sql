-- Ask Ustaad: a cache of YouTube lesson searches (one search per topic) and the daily photo-question limit.
create table if not exists public.video_cache (
  query      text primary key check (length(query) between 1 and 120),
  results    jsonb not null,
  created_at timestamptz not null default now()
);
alter table public.video_cache enable row level security;
-- No policies: only the AI gateway (service role) reads and writes it.

insert into public.app_config (key, value) values ('daily_photo_questions', '20'::jsonb)
on conflict (key) do nothing;

-- The owner can change the photo-question limit from the owner panel.
create or replace function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key = 'ai_enabled' and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total', 'daily_photo_questions') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 1000000 then null;
  elsif p_key in ('usd_per_million_input', 'usd_per_million_output', 'pkr_per_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  else
    raise exception 'SETTING_NOT_ALLOWED: %', p_key using errcode = '22023';
  end if;
  insert into public.app_config (key, value) values (p_key, p_value)
  on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'config.changed', p_key, jsonb_build_object('value', p_value));
end $$;
