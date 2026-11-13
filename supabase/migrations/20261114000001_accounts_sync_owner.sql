-- Accounts, server sync, AI marks, account deletion and the owner panel's functions.

------------------------------------------------------------------------------
-- Erasure: append-only tables block updates and deletes, except during a deliberate account erasure,
-- which sets a transaction-local flag (see public.delete_my_account).
------------------------------------------------------------------------------
create or replace function private.forbid_change() returns trigger
language plpgsql as $$
begin
  if tg_op = 'DELETE' and current_setting('parhlou.erasing', true) = 'on' then
    return old;
  end if;
  raise exception 'table %.% is append-only', tg_table_schema, tg_table_name
    using errcode = 'P0001';
end $$;

------------------------------------------------------------------------------
-- Learner registration and settings
------------------------------------------------------------------------------
-- Creates (or renames) the signed-in student's family space, learner role and profile. Safe to call again.
create function public.register_learner(p_display_name text) returns uuid
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_tenant uuid;
  v_name text := left(btrim(coalesce(p_display_name, '')), 80);
begin
  if v_user is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  if v_name = '' then v_name := 'Learner'; end if;

  select tenant_id into v_tenant from public.student_profiles where user_id = v_user;
  if v_tenant is not null then
    update public.student_profiles set display_name = v_name where user_id = v_user;
    return v_tenant;
  end if;

  insert into public.tenants (name, kind) values ('Family of ' || v_name, 'family') returning id into v_tenant;
  insert into public.memberships (tenant_id, user_id, role) values (v_tenant, v_user, 'learner');
  insert into public.student_profiles (user_id, tenant_id, display_name) values (v_user, v_tenant, v_name);
  return v_tenant;
end $$;

create table public.learner_settings (
  user_id     uuid primary key references auth.users(id) on delete cascade default auth.uid(),
  settings    jsonb not null check (jsonb_typeof(settings) = 'object' and length(settings::text) < 8000),
  updated_at  timestamptz not null default now()
);

------------------------------------------------------------------------------
-- Evidence log mirrored from the phone (append-only, duplicate-proof)
------------------------------------------------------------------------------
create table public.learner_events (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade default auth.uid(),
  client_id   text not null check (length(client_id) between 8 and 80),
  at          timestamptz not null,
  key         text not null check (length(key) between 1 and 120),
  code        text not null check (length(code) between 1 and 200),
  created_at  timestamptz not null default now(),
  unique (user_id, client_id),
  -- 'E:' is a verified exam mark. Only the server writes those (see ai_marks); the phone never may.
  check (code !~ '^E:')
);
create index on public.learner_events (user_id, at);
create trigger learner_events_append_only before update or delete on public.learner_events
  for each row execute function private.forbid_change();

------------------------------------------------------------------------------
-- Marks written by the AI server only
------------------------------------------------------------------------------
create table public.ai_marks (
  id              bigint generated always as identity primary key,
  user_id         uuid not null references auth.users(id) on delete cascade,
  paper_id        text,
  question_id     text not null,
  awarded         numeric(6,2) not null check (awarded >= 0),
  max_marks       numeric(6,2) not null check (max_marks > 0 and awarded <= max_marks),
  status          text not null,
  model           text not null,
  prompt_version  text not null,
  created_at      timestamptz not null default now()
);
create index on public.ai_marks (user_id, created_at);
create trigger ai_marks_append_only before update or delete on public.ai_marks
  for each row execute function private.forbid_change();

alter table public.learner_settings enable row level security;
alter table public.learner_events enable row level security;
alter table public.ai_marks enable row level security;

grant select, insert, update on public.learner_settings to authenticated;
grant select, insert on public.learner_events to authenticated;
grant select on public.ai_marks to authenticated;

create policy learner_settings_own on public.learner_settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy learner_events_read_own on public.learner_events for select to authenticated
  using (user_id = auth.uid());
create policy learner_events_insert_own on public.learner_events for insert to authenticated
  with check (user_id = auth.uid());
create policy ai_marks_read_own on public.ai_marks for select to authenticated
  using (user_id = auth.uid() or private.has_role(array['owner']::public.member_role[]));

------------------------------------------------------------------------------
-- Export and deletion of the signed-in student's own data
------------------------------------------------------------------------------
create function public.export_my_data() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'exported_at', now(),
    'user_id', auth.uid(),
    'email', (select email from auth.users where id = auth.uid()),
    'profile', (select to_jsonb(p) - 'tenant_id' from public.student_profiles p where p.user_id = auth.uid()),
    'settings', (select settings from public.learner_settings where user_id = auth.uid()),
    'events', coalesce((select jsonb_agg(jsonb_build_object('at', e.at, 'key', e.key, 'code', e.code) order by e.at)
                        from public.learner_events e where e.user_id = auth.uid()), '[]'::jsonb),
    'ai_marks', coalesce((select jsonb_agg(jsonb_build_object('at', m.created_at, 'paper', m.paper_id, 'question', m.question_id,
                                                              'awarded', m.awarded, 'max', m.max_marks, 'status', m.status) order by m.created_at)
                          from public.ai_marks m where m.user_id = auth.uid()), '[]'::jsonb)
  )
  where auth.uid() is not null;
$$;

-- Permanently deletes the signed-in student and everything that belongs to them, including their family space.
create function public.delete_my_account() returns void
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_family uuid[];
begin
  if v_user is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  perform set_config('parhlou.erasing', 'on', true);

  -- Family spaces where this student is the only member go too.
  select coalesce(array_agg(t.id), '{}') into v_family
  from public.tenants t
  where t.kind = 'family'
    and exists (select 1 from public.memberships m where m.tenant_id = t.id and m.user_id = v_user)
    and not exists (select 1 from public.memberships m where m.tenant_id = t.id and m.user_id <> v_user);

  delete from public.consent_events where learner_id = v_user or actor_id = v_user;
  delete from public.ai_usage where caller = v_user::text;
  delete from auth.users where id = v_user;          -- cascades to profiles, memberships, events, marks, settings
  delete from public.entitlements where tenant_id = any (v_family);
  delete from public.tenants where id = any (v_family);
end $$;

------------------------------------------------------------------------------
-- Owner account: the configured owner email gets the owner role on its first sign-in
------------------------------------------------------------------------------
create function private.grant_owner(p_user uuid, p_email text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare v_owner text; v_platform uuid;
begin
  select lower(value #>> '{}') into v_owner from public.app_config where key = 'owner_email';
  if v_owner is null or v_owner = '' or lower(coalesce(p_email, '')) <> v_owner then return; end if;
  select id into v_platform from public.tenants where kind = 'platform' order by id limit 1;
  if v_platform is null then
    insert into public.tenants (name, kind) values ('Parhlou platform', 'platform') returning id into v_platform;
  end if;
  insert into public.memberships (tenant_id, user_id, role) values (v_platform, p_user, 'owner')
  on conflict (tenant_id, user_id, role) do nothing;
end $$;

create function private.on_auth_user_created() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform private.grant_owner(new.id, new.email);
  return new;
end $$;

create trigger parhlou_grant_owner after insert on auth.users
  for each row execute function private.on_auth_user_created();

-- Sets the owner email (server-side only) and grants the role at once if that account already exists.
create function public.set_owner_email(p_email text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare u record;
begin
  insert into public.app_config (key, value) values ('owner_email', to_jsonb(lower(btrim(p_email))))
  on conflict (key) do update set value = excluded.value, updated_at = now();
  for u in select id, email from auth.users where lower(email) = lower(btrim(p_email)) loop
    perform private.grant_owner(u.id, u.email);
  end loop;
end $$;
revoke all on function public.set_owner_email(text) from public, anon, authenticated;

------------------------------------------------------------------------------
-- Owner panel
------------------------------------------------------------------------------
create function private.require_owner() returns void
language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.has_role(array['owner']::public.member_role[]) then
    raise exception 'OWNER_ONLY' using errcode = '42501';
  end if;
end $$;

create table public.content_drafts (
  subject_id  text primary key,
  bundle      jsonb not null,
  updated_at  timestamptz not null default now(),
  updated_by  uuid
);
alter table public.content_drafts enable row level security;
grant select on public.content_drafts to authenticated;
create policy content_drafts_owner on public.content_drafts for select to authenticated
  using (private.has_role(array['owner']::public.member_role[]));

create policy app_content_owner_all on public.app_content for select to authenticated
  using (private.has_role(array['owner']::public.member_role[]));
grant select on public.app_config to authenticated;
create policy app_config_owner on public.app_config for select to authenticated
  using (private.has_role(array['owner']::public.member_role[]));
grant select on public.ai_usage to authenticated;

create function private.check_bundle(p_subject text, p_bundle jsonb) returns void
language plpgsql immutable as $$
begin
  if p_bundle is null or jsonb_typeof(p_bundle) <> 'object' or (p_bundle->>'format')::int is distinct from 1 then
    raise exception 'BUNDLE_INVALID: format must be 1' using errcode = '22023';
  end if;
  if p_bundle->>'subjectId' is distinct from p_subject then
    raise exception 'BUNDLE_INVALID: subjectId does not match' using errcode = '22023';
  end if;
  if jsonb_typeof(p_bundle->'subject') <> 'object' or jsonb_typeof(p_bundle->'written') <> 'array'
     or jsonb_typeof(p_bundle->'mcqs') <> 'array' or jsonb_typeof(p_bundle->'notes') <> 'array' then
    raise exception 'BUNDLE_INVALID: missing subject, written, mcqs or notes' using errcode = '22023';
  end if;
end $$;

create function public.owner_save_draft(p_subject text, p_bundle jsonb) returns timestamptz
language plpgsql volatile security definer set search_path = '' as $$
declare v_at timestamptz := now();
begin
  perform private.require_owner();
  perform private.check_bundle(p_subject, p_bundle);
  insert into public.content_drafts (subject_id, bundle, updated_at, updated_by) values (p_subject, p_bundle, v_at, auth.uid())
  on conflict (subject_id) do update set bundle = excluded.bundle, updated_at = v_at, updated_by = auth.uid();
  insert into public.audit_logs (actor_id, action, target) values (auth.uid(), 'content.draft_saved', p_subject);
  return v_at;
end $$;

create function public.owner_publish_content(p_subject text, p_bundle jsonb) returns int
language plpgsql volatile security definer set search_path = '' as $$
declare v int;
begin
  perform private.require_owner();
  perform private.check_bundle(p_subject, p_bundle);
  v := public.publish_content(p_subject, p_bundle);
  delete from public.content_drafts where subject_id = p_subject;
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'content.published', p_subject, jsonb_build_object('version', v));
  return v;
end $$;

-- Republishes an earlier version as a new version, so history is never rewritten.
create function public.owner_rollback_content(p_subject text, p_version int) returns int
language plpgsql volatile security definer set search_path = '' as $$
declare v_bundle jsonb; v int;
begin
  perform private.require_owner();
  select bundle into v_bundle from public.app_content where subject_id = p_subject and version = p_version;
  if v_bundle is null then raise exception 'VERSION_NOT_FOUND' using errcode = 'P0002'; end if;
  v := public.publish_content(p_subject, v_bundle);
  insert into public.audit_logs (actor_id, action, target, detail)
  values (auth.uid(), 'content.rolled_back', p_subject, jsonb_build_object('from_version', p_version, 'new_version', v));
  return v;
end $$;

create function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key = 'ai_enabled' and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total') and jsonb_typeof(p_value) = 'number'
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

create function public.owner_dashboard() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare r jsonb;
begin
  perform private.require_owner();
  select jsonb_build_object(
    'learners_total', (select count(*) from public.student_profiles),
    'learners_active_today', (select count(distinct user_id) from public.learner_events where created_at > now() - interval '1 day'),
    'learners_active_7d', (select count(distinct user_id) from public.learner_events where created_at > now() - interval '7 days'),
    'ai_calls_today', (select count(*) from public.ai_usage where created_at > now() - interval '1 day'),
    'ai_calls_7d', (select count(*) from public.ai_usage where created_at > now() - interval '7 days'),
    'ai_failed_7d', (select count(*) from public.ai_usage where created_at > now() - interval '7 days' and not ok),
    'tokens_in_7d', (select coalesce(sum(input_tokens), 0) from public.ai_usage where created_at > now() - interval '7 days'),
    'tokens_out_7d', (select coalesce(sum(output_tokens), 0) from public.ai_usage where created_at > now() - interval '7 days'),
    'tokens_in_30d', (select coalesce(sum(input_tokens), 0) from public.ai_usage where created_at > now() - interval '30 days'),
    'tokens_out_30d', (select coalesce(sum(output_tokens), 0) from public.ai_usage where created_at > now() - interval '30 days'),
    'answers_marked_7d', (select count(*) from public.ai_marks where created_at > now() - interval '7 days'),
    'papers_marked_7d', (select count(distinct (user_id, paper_id)) from public.ai_marks where paper_id is not null and created_at > now() - interval '7 days'),
    'content', coalesce((select jsonb_agg(jsonb_build_object('subject', subject_id, 'version', version, 'published_at', created_at))
                         from public.app_content where status = 'PUBLISHED'), '[]'::jsonb)
  ) into r;
  return r;
end $$;

create function public.owner_learners() returns table (
  user_id uuid, display_name text, joined timestamptz, last_active timestamptz, papers int, answers_marked int, average_percent int
)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query
  select p.user_id, p.display_name, p.created_at,
         greatest((select max(e.created_at) from public.learner_events e where e.user_id = p.user_id),
                  (select max(m.created_at) from public.ai_marks m where m.user_id = p.user_id)),
         (select count(distinct m.paper_id)::int from public.ai_marks m where m.user_id = p.user_id and m.paper_id is not null),
         (select count(*)::int from public.ai_marks m where m.user_id = p.user_id),
         (select (100 * sum(m.awarded) / nullif(sum(m.max_marks), 0))::int from public.ai_marks m where m.user_id = p.user_id)
  from public.student_profiles p
  order by 4 desc nulls last, p.created_at desc;
end $$;

revoke all on function public.export_my_data() from public, anon;
revoke all on function public.delete_my_account() from public, anon;
revoke all on function public.register_learner(text) from public, anon;
grant execute on function public.export_my_data() to authenticated;
grant execute on function public.delete_my_account() to authenticated;
grant execute on function public.register_learner(text) to authenticated;
do $$
declare f text;
begin
  foreach f in array array['owner_save_draft(text, jsonb)', 'owner_publish_content(text, jsonb)', 'owner_rollback_content(text, int)',
                           'owner_set_config(text, jsonb)', 'owner_dashboard()', 'owner_learners()'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

insert into public.app_config (key, value) values
  ('usd_per_million_input', '0'),
  ('usd_per_million_output', '0'),
  ('pkr_per_usd', '280')
on conflict (key) do nothing;
