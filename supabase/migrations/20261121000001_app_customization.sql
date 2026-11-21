-- App customisation from the owner panel: branding, a home announcement, feature switches with gradual rollout, game
-- tuning, texts, maintenance mode and a minimum app version, all in one setting (app_customization). Phones read it through
-- public_app_settings(), which returns only these public values (never AI costs, wallet numbers or the owner email).
insert into public.app_config (key, value) values ('app_customization', '{}'::jsonb) on conflict (key) do nothing;

create or replace function public.public_app_settings() returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce((select value from public.app_config where key = 'app_customization'), '{}'::jsonb)
    || jsonb_build_object('adsEnabled', coalesce((select (value #>> '{}')::boolean from public.app_config where key = 'ads_enabled'), true));
$$;
revoke all on function public.public_app_settings() from public;
grant execute on function public.public_app_settings() to anon, authenticated;

-- The owner can now save app_customization (an object, under 20 kB).
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
  elsif p_key = 'app_customization' and jsonb_typeof(p_value) = 'object' and length(p_value::text) < 20000 then null;
  else
    raise exception 'SETTING_NOT_ALLOWED: %', p_key using errcode = '22023';
  end if;
  insert into public.app_config (key, value) values (p_key, p_value)
  on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'config.changed', p_key, jsonb_build_object('value', p_value));
end $$;

-- Owner: the last saved versions of the app customisation, newest first, for "restore an earlier version".
create or replace function public.owner_customization_history() returns table (saved_at timestamptz, value jsonb)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query select a.created_at, a.detail -> 'value' from public.audit_logs a
    where a.action = 'config.changed' and a.target = 'app_customization' order by a.created_at desc limit 20;
end $$;
revoke all on function public.owner_customization_history() from public, anon;
grant execute on function public.owner_customization_history() to authenticated;

-- "View as student" shows the student's AI use this month against their allowance and plan (the app shows the same as the
-- Ustaad energy bar), instead of the old credits. Everything else is as before; every look is still logged.
create or replace function public.owner_student(p_user uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r jsonb; v_plus boolean; v_until date;
begin
  perform private.require_owner();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'student.viewed', p_user::text, '{}'::jsonb);
  select s.paid_until into v_until from public.subscriptions s where s.user_id = p_user;
  v_plus := coalesce(v_until >= current_date, false);
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
      'spent_usd', (select coalesce(sum(u.usd), 0) from public.ai_usage u where u.caller = p_user::text and u.created_at >= date_trunc('month', now())),
      'calls', (select count(*) from public.ai_usage u where u.caller = p_user::text and u.created_at >= date_trunc('month', now())),
      'allowance_usd', coalesce((select (c.value #>> '{}')::numeric from public.app_config c
                                 where c.key = case when v_plus then 'monthly_usd_per_student' else 'free_monthly_usd' end),
                                case when v_plus then 3 else 0.5 end))
  ) into r;
  return r;
end $$;
revoke all on function public.owner_student(uuid) from public, anon;
grant execute on function public.owner_student(uuid) to authenticated;
