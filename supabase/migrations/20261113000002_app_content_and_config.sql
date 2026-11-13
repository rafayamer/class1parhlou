-- Live content and settings: the app downloads the published content bundle, and the AI function reads its
-- questions, keys and limits from here, so content edits reach phones without a new app build.
-- PROTOTYPE NOTE: the bundle currently includes answer keys and rubrics (exactly as the APK already does).
-- Moving paper keys to a server-only table is tracked as open item O-10.

create table public.app_content (
  id          uuid primary key default gen_random_uuid(),
  subject_id  text not null,
  version     int not null check (version > 0),
  bundle      jsonb not null,
  status      public.publication_status not null default 'DRAFT',
  created_at  timestamptz not null default now(),
  unique (subject_id, version)
);
-- At most one published bundle per subject.
create unique index app_content_one_published on public.app_content (subject_id) where status = 'PUBLISHED';

create table public.app_config (
  key         text primary key,
  value       jsonb not null,
  updated_at  timestamptz not null default now()
);

alter table public.app_content enable row level security;
alter table public.app_config enable row level security;

grant select on public.app_content to anon, authenticated;
create policy app_content_published on public.app_content for select to anon, authenticated
  using (status = 'PUBLISHED');
-- app_config has no policies: only the server (service role) reads or writes it.

insert into public.app_config (key, value) values
  ('ai_enabled', 'true'),
  ('daily_calls_per_install', '300'),
  ('daily_calls_total', '2000')
on conflict (key) do nothing;

-- Publishes a new bundle version and retires the previous one in a single step.
create function public.publish_content(p_subject text, p_bundle jsonb) returns int
language plpgsql security definer set search_path = '' as $$
declare v int;
begin
  select coalesce(max(version), 0) + 1 into v from public.app_content where subject_id = p_subject;
  update public.app_content set status = 'RETIRED' where subject_id = p_subject and status = 'PUBLISHED';
  insert into public.app_content (subject_id, version, bundle, status) values (p_subject, v, p_bundle, 'PUBLISHED');
  return v;
end $$;
revoke all on function public.publish_content(text, jsonb) from public, anon, authenticated;
