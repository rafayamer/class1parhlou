-- Credits, Ustaad's memory, saved chats, safety flags, feedback, and the owner's read-only "view as student".
-- Only the AI gateway (service role) writes these tables. Students read their own rows; the owner reads through the
-- owner_* functions below, and every look at a student's portfolio is written to audit_logs.

------------------------------------------------------------------------------
-- Credits: 1,000 a month per student; each month's credits expire 4 months after they're given; oldest are spent first.
-- holder = the student's user id, or the install id for a phone that isn't signed in.
------------------------------------------------------------------------------
create table if not exists public.credit_ledger (
  id          bigint generated always as identity primary key,
  holder      text not null check (length(holder) between 16 and 64),
  user_id     uuid references auth.users(id) on delete cascade,
  kind        text not null check (kind in ('grant', 'spend')),
  amount      int not null check (amount > 0 and amount <= 100000),
  task        text check (task is null or length(task) <= 40),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz,
  check ((kind = 'grant') = (expires_at is not null))
);
create index if not exists credit_ledger_holder on public.credit_ledger (holder, created_at);

insert into public.app_config (key, value) values
  ('monthly_credits', '1000'::jsonb),
  ('credit_months', '4'::jsonb),
  ('credit_prices', '{"chat":1,"photo":3,"mark_typed":1,"mark_handwritten":2,"page_read":2,"analysis":1,"pick_paper":2,"videos":0}'::jsonb)
on conflict (key) do nothing;

------------------------------------------------------------------------------
-- Ustaad's memory of each student (a short profile he keeps up to date) and the chat itself (30 days, 200 messages).
------------------------------------------------------------------------------
create table if not exists public.student_memory (
  holder          text primary key check (length(holder) between 16 and 64),
  user_id         uuid references auth.users(id) on delete cascade,
  summary         text not null default '' check (length(summary) <= 1500),
  messages_since  int not null default 0,
  updated_at      timestamptz not null default now()
);
create table if not exists public.chat_messages (
  id          bigint generated always as identity primary key,
  holder      text not null check (length(holder) between 16 and 64),
  user_id     uuid references auth.users(id) on delete cascade,
  role        text not null check (role in ('student', 'ustaad')),
  text        text not null check (length(text) <= 4000),
  created_at  timestamptz not null default now()
);
create index if not exists chat_messages_holder on public.chat_messages (holder, created_at desc);

------------------------------------------------------------------------------
-- Safety: messages about sex or illegal things (and self-harm, for care) are refused and flagged for the owner.
------------------------------------------------------------------------------
create table if not exists public.safety_flags (
  id          bigint generated always as identity primary key,
  holder      text not null check (length(holder) between 16 and 64),
  user_id     uuid references auth.users(id) on delete cascade,
  category    text not null check (category in ('UNSAFE_SEXUAL', 'UNSAFE_ILLEGAL', 'SELF_HARM')),
  excerpt     text not null check (length(excerpt) <= 500),
  status      text not null default 'open' check (status in ('open', 'handled')),
  note        text check (note is null or length(note) <= 1000),
  created_at  timestamptz not null default now()
);
create index if not exists safety_flags_status on public.safety_flags (status, created_at desc);

------------------------------------------------------------------------------
-- Feedback from the app (Profile → Send feedback).
------------------------------------------------------------------------------
create table if not exists public.feedback (
  id           bigint generated always as identity primary key,
  holder       text not null check (length(holder) between 16 and 64),
  user_id      uuid references auth.users(id) on delete cascade,
  text         text not null check (length(text) between 1 and 3000),
  screen       text check (screen is null or length(screen) <= 80),
  app_version  text check (app_version is null or length(app_version) <= 40),
  status       text not null default 'open' check (status in ('open', 'done')),
  created_at   timestamptz not null default now()
);

alter table public.credit_ledger enable row level security;
alter table public.student_memory enable row level security;
alter table public.chat_messages enable row level security;
alter table public.safety_flags enable row level security;
alter table public.feedback enable row level security;
-- A signed-in student can read their own credits, memory, chats and feedback (never their safety flags). No one writes
-- from the app: the gateway uses the service role.
drop policy if exists credit_ledger_own on public.credit_ledger;
create policy credit_ledger_own on public.credit_ledger for select to authenticated using (user_id = auth.uid());
drop policy if exists student_memory_own on public.student_memory;
create policy student_memory_own on public.student_memory for select to authenticated using (user_id = auth.uid());
drop policy if exists chat_messages_own on public.chat_messages;
create policy chat_messages_own on public.chat_messages for select to authenticated using (user_id = auth.uid());
drop policy if exists feedback_own on public.feedback;
create policy feedback_own on public.feedback for select to authenticated using (user_id = auth.uid());

------------------------------------------------------------------------------
-- Owner functions
------------------------------------------------------------------------------
create or replace function public.owner_feedback() returns setof public.feedback
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query select * from public.feedback order by (status = 'open') desc, created_at desc limit 500;
end $$;

create or replace function public.owner_set_feedback_status(p_id bigint, p_status text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  update public.feedback set status = p_status where id = p_id;
end $$;

create or replace function public.owner_safety_flags() returns setof public.safety_flags
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query select * from public.safety_flags order by (status = 'open') desc, created_at desc limit 500;
end $$;

create or replace function public.owner_set_flag_status(p_id bigint, p_status text, p_note text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  update public.safety_flags set status = p_status, note = left(p_note, 1000) where id = p_id;
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'safety.flag_updated', p_id::text, jsonb_build_object('status', p_status));
end $$;

-- Credits used per student and task over the last 30 days, for tuning the price list.
create or replace function public.owner_credit_usage() returns table (holder text, user_id uuid, display_name text, task text, spent bigint, uses bigint)
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_owner();
  return query
  select l.holder, l.user_id, p.display_name, l.task, sum(l.amount)::bigint, count(*)::bigint
  from public.credit_ledger l left join public.student_profiles p on p.user_id = l.user_id
  where l.kind = 'spend' and l.created_at > now() - interval '30 days'
  group by l.holder, l.user_id, p.display_name, l.task
  order by 5 desc;
end $$;

-- "View as student": everything about one student, read-only. Every call is logged.
create or replace function public.owner_student(p_user uuid) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r jsonb;
begin
  perform private.require_owner();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'student.viewed', p_user::text, '{}'::jsonb);
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
    'feedback', coalesce((select jsonb_agg(to_jsonb(f) order by f.created_at desc) from public.feedback f where f.user_id = p_user), '[]'::jsonb)
  ) into r;
  return r;
end $$;

-- The owner can change the credit settings, as well as the earlier settings.
create or replace function public.owner_set_config(p_key text, p_value jsonb) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_owner();
  if p_key = 'ai_enabled' and jsonb_typeof(p_value) = 'boolean' then null;
  elsif p_key in ('daily_calls_per_install', 'daily_calls_total', 'daily_photo_questions', 'monthly_credits', 'credit_months')
        and jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 1000000 then null;
  elsif p_key in ('usd_per_million_input', 'usd_per_million_output', 'pkr_per_usd') and jsonb_typeof(p_value) = 'number'
        and (p_value #>> '{}')::numeric between 0 and 100000 then null;
  elsif p_key = 'credit_prices' and jsonb_typeof(p_value) = 'object' and length(p_value::text) < 2000 then null;
  else
    raise exception 'SETTING_NOT_ALLOWED: %', p_key using errcode = '22023';
  end if;
  insert into public.app_config (key, value) values (p_key, p_value)
  on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target, detail) values (auth.uid(), 'config.changed', p_key, jsonb_build_object('value', p_value));
end $$;

do $$
declare f text;
begin
  foreach f in array array['owner_feedback()', 'owner_set_feedback_status(bigint, text)', 'owner_safety_flags()',
                           'owner_set_flag_status(bigint, text, text)', 'owner_credit_usage()', 'owner_student(uuid)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
