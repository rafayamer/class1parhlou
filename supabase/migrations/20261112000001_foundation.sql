-- Parhlou Milestone 1 foundation schema.
-- Identity/tenancy, versioned curriculum, sources, question items, blueprints,
-- learning evidence, entitlements and audit. RLS is deny-by-default on every table.
-- Status: UNVERIFIED in a hosted Supabase project; verified locally by supabase/tests.

create extension if not exists pgcrypto;

create schema if not exists private;
revoke all on schema private from public;

------------------------------------------------------------------------------
-- Enums
------------------------------------------------------------------------------
create type public.member_role as enum
  ('learner', 'guardian', 'teacher', 'editor', 'creator', 'tenant_admin', 'owner');

create type public.publication_status as enum
  ('DRAFT', 'ACADEMIC_REVIEW', 'APPROVED', 'PUBLISHED', 'SUSPENDED', 'RETIRED');

create type public.guardian_link_status as enum ('invited', 'verified', 'revoked');

create type public.pathway as enum ('complete_academy', 'test_series');

create type public.item_type as enum ('mcq', 'short', 'long');

create type public.paper_scope as enum ('chapter', 'quarter', 'half', 'full', 'readiness');

create type public.blueprint_authority as enum ('official', 'teacher_reviewed_practice');

------------------------------------------------------------------------------
-- Identity and tenancy
------------------------------------------------------------------------------
create table public.tenants (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  kind        text not null check (kind in ('platform', 'family', 'school', 'academy')),
  created_at  timestamptz not null default now()
);

create table public.memberships (
  id          uuid primary key default gen_random_uuid(),
  tenant_id   uuid not null references public.tenants(id) on delete cascade,
  user_id     uuid not null references auth.users(id) on delete cascade,
  role        public.member_role not null,
  active      boolean not null default true,
  created_at  timestamptz not null default now(),
  unique (tenant_id, user_id, role)
);
create index on public.memberships (user_id);

create table public.student_profiles (
  user_id       uuid primary key references auth.users(id) on delete cascade,
  tenant_id     uuid not null references public.tenants(id),
  display_name  text not null check (length(display_name) between 1 and 80),
  birth_year    int check (birth_year between 1990 and 2030),
  created_at    timestamptz not null default now()
);

create table public.guardian_links (
  id                uuid primary key default gen_random_uuid(),
  tenant_id         uuid not null references public.tenants(id),
  learner_id        uuid not null references auth.users(id) on delete cascade,
  guardian_user_id  uuid references auth.users(id) on delete cascade,
  relationship      text not null,
  status            public.guardian_link_status not null default 'invited',
  scopes            text[] not null default '{}',
  verified_at       timestamptz,
  revoked_at        timestamptz,
  created_at        timestamptz not null default now(),
  check (status <> 'verified' or (guardian_user_id is not null and verified_at is not null)),
  check (scopes <@ array['progress', 'marks', 'weekly_report']::text[])
);

create table public.teacher_assignments (
  id               uuid primary key default gen_random_uuid(),
  tenant_id        uuid not null references public.tenants(id),
  teacher_user_id  uuid not null references auth.users(id) on delete cascade,
  learner_id       uuid not null references auth.users(id) on delete cascade,
  active           boolean not null default true,
  created_at       timestamptz not null default now()
);

create table public.consent_events (
  id          uuid primary key default gen_random_uuid(),
  learner_id  uuid not null references auth.users(id) on delete cascade,
  actor_id    uuid not null references auth.users(id),
  kind        text not null,
  granted     boolean not null,
  created_at  timestamptz not null default now()
);

------------------------------------------------------------------------------
-- Curriculum (versioned packs)
------------------------------------------------------------------------------
create table public.boards (
  id      uuid primary key default gen_random_uuid(),
  code    text not null unique,
  name    text not null,
  status  public.publication_status not null default 'DRAFT'
);

create table public.examination_targets (
  id             uuid primary key default gen_random_uuid(),
  board_id       uuid not null references public.boards(id),
  grade          int not null check (grade between 1 and 14),
  session_label  text not null,
  status         public.publication_status not null default 'DRAFT',
  unique (board_id, grade, session_label)
);

create table public.subjects (
  id        uuid primary key default gen_random_uuid(),
  board_id  uuid not null references public.boards(id),
  grade     int not null check (grade between 1 and 14),
  code      text not null,
  name      text not null,
  status    public.publication_status not null default 'DRAFT',
  unique (board_id, grade, code),
  unique (id, board_id, grade)
);

create table public.textbook_editions (
  id             uuid primary key default gen_random_uuid(),
  subject_id     uuid not null,
  board_id       uuid not null,
  grade          int not null,
  title          text not null,
  edition_label  text not null,
  publisher      text,
  medium         text not null default 'English',
  verified       boolean not null default false,
  notes          text,
  status         public.publication_status not null default 'DRAFT',
  -- An edition can only belong to a subject of the same board and grade.
  foreign key (subject_id, board_id, grade) references public.subjects (id, board_id, grade)
);

create table public.chapters (
  id          uuid primary key default gen_random_uuid(),
  edition_id  uuid not null references public.textbook_editions(id),
  number      int not null check (number > 0),
  title       text not null,
  status      public.publication_status not null default 'DRAFT',
  unique (edition_id, number)
);

create table public.modules (
  id          uuid primary key default gen_random_uuid(),
  chapter_id  uuid not null references public.chapters(id),
  position    int not null check (position > 0),
  title       text not null,
  book_ref    text,           -- printed page/section/exercise reference, verified only
  status      public.publication_status not null default 'DRAFT',
  unique (chapter_id, position)
);

create table public.learning_objectives (
  id          uuid primary key default gen_random_uuid(),
  chapter_id  uuid not null references public.chapters(id),
  code        text not null,
  statement   text not null,
  status      public.publication_status not null default 'DRAFT',
  unique (chapter_id, code)
);

-- A learner's enrollment ties them to exactly one edition per subject.
create table public.enrollments (
  id                     uuid primary key default gen_random_uuid(),
  learner_id             uuid not null references auth.users(id) on delete cascade,
  tenant_id              uuid not null references public.tenants(id),
  examination_target_id  uuid not null references public.examination_targets(id),
  edition_id             uuid not null references public.textbook_editions(id),
  pathway                public.pathway not null,
  active                 boolean not null default true,
  created_at             timestamptz not null default now(),
  unique (learner_id, edition_id)
);

------------------------------------------------------------------------------
-- Sources and rights
------------------------------------------------------------------------------
create table public.publications (
  id          uuid primary key default gen_random_uuid(),
  edition_id  uuid references public.textbook_editions(id),
  title       text not null,
  origin_url  text,
  status      public.publication_status not null default 'DRAFT'
);

create table public.source_rights (
  id              uuid primary key default gen_random_uuid(),
  publication_id  uuid not null references public.publications(id),
  licence         text not null,
  allowed_uses    text[] not null default '{}',
  expires_on      date,
  evidence_note   text not null,
  approved_by     uuid references auth.users(id),
  approved_at     timestamptz
);

create table public.source_files (
  id              uuid primary key default gen_random_uuid(),
  publication_id  uuid not null references public.publications(id),
  sha256          text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  storage_path    text not null,
  kind            text not null check (kind in ('text_pdf', 'scanned_pdf', 'image', 'other')),
  created_at      timestamptz not null default now()
);

------------------------------------------------------------------------------
-- Questions and blueprints
------------------------------------------------------------------------------
create table public.question_items (
  id            uuid primary key default gen_random_uuid(),
  chapter_id    uuid not null references public.chapters(id),
  module_id     uuid references public.modules(id),
  objective_id  uuid references public.learning_objectives(id),
  item_type     public.item_type not null,
  created_at    timestamptz not null default now()
);

create table public.item_revisions (
  id          uuid primary key default gen_random_uuid(),
  item_id     uuid not null references public.question_items(id),
  revision    int not null check (revision > 0),
  stem        text not null,
  options     jsonb,          -- MCQ: JSON array of 4 strings
  rationales  jsonb,          -- MCQ: JSON array of 4 explanations
  marks       numeric(5,2) not null check (marks > 0),
  source_ref  text,
  status      public.publication_status not null default 'DRAFT',
  created_at  timestamptz not null default now(),
  unique (item_id, revision),
  check (options is null or (jsonb_typeof(options) = 'array' and jsonb_array_length(options) between 2 and 6))
);

-- Answer keys live apart from revisions so no learner-facing policy can expose them.
create table public.item_answer_keys (
  revision_id     uuid primary key references public.item_revisions(id),
  correct_option  int,
  model_answer    text,
  check (correct_option is not null or model_answer is not null)
);

create table public.paper_blueprints (
  id                uuid primary key default gen_random_uuid(),
  edition_id        uuid not null references public.textbook_editions(id),
  scope             public.paper_scope not null,
  title             text not null,
  duration_minutes  int not null check (duration_minutes between 5 and 240),
  total_marks       numeric(6,2) not null check (total_marks > 0),
  sections          jsonb not null,
  authority         public.blueprint_authority not null,
  status            public.publication_status not null default 'DRAFT',
  created_at        timestamptz not null default now()
);

------------------------------------------------------------------------------
-- Learning evidence
------------------------------------------------------------------------------
create table public.quiz_attempts (
  id                 uuid primary key default gen_random_uuid(),
  learner_id         uuid not null references auth.users(id) on delete cascade,
  tenant_id          uuid not null references public.tenants(id),
  module_id          uuid not null references public.modules(id),
  item_revision_ids  uuid[] not null,
  started_at         timestamptz not null default now()
);

create table public.quiz_answers (
  id             uuid primary key default gen_random_uuid(),
  attempt_id     uuid not null references public.quiz_attempts(id) on delete cascade,
  revision_id    uuid not null references public.item_revisions(id),
  chosen_option  int not null,
  is_correct     boolean not null,
  attempt_no     int not null check (attempt_no > 0),
  answered_at    timestamptz not null default now(),
  unique (attempt_id, revision_id, attempt_no)
);

create table public.learning_events (
  id               uuid primary key default gen_random_uuid(),
  learner_id       uuid not null references auth.users(id) on delete cascade,
  tenant_id        uuid not null references public.tenants(id),
  kind             text not null,
  ref              jsonb not null default '{}',
  idempotency_key  text not null,
  created_at       timestamptz not null default now(),
  unique (learner_id, idempotency_key)
);

------------------------------------------------------------------------------
-- Commerce and operations
------------------------------------------------------------------------------
create table public.entitlements (
  id           uuid primary key default gen_random_uuid(),
  learner_id   uuid not null references auth.users(id) on delete cascade,
  tenant_id    uuid not null references public.tenants(id),
  product      text not null,
  status       text not null check (status in ('active', 'pending', 'grace', 'paused', 'canceled', 'expired', 'refunded')),
  valid_until  timestamptz,
  source       text not null,
  created_at   timestamptz not null default now()
);

create table public.audit_logs (
  id          uuid primary key default gen_random_uuid(),
  actor_id    uuid,
  action      text not null,
  target      text,
  detail      jsonb not null default '{}',
  created_at  timestamptz not null default now()
);

------------------------------------------------------------------------------
-- Append-only enforcement (evidence is corrected by new rows, never rewritten)
------------------------------------------------------------------------------
create function private.forbid_change() returns trigger
language plpgsql as $$
begin
  raise exception 'table %.% is append-only', tg_table_schema, tg_table_name
    using errcode = 'P0001';
end $$;

create trigger learning_events_append_only before update or delete on public.learning_events
  for each row execute function private.forbid_change();
create trigger quiz_answers_append_only before update or delete on public.quiz_answers
  for each row execute function private.forbid_change();
create trigger consent_events_append_only before update or delete on public.consent_events
  for each row execute function private.forbid_change();
create trigger audit_logs_append_only before update or delete on public.audit_logs
  for each row execute function private.forbid_change();

------------------------------------------------------------------------------
-- Blueprint validation: marks must reconcile before publication
------------------------------------------------------------------------------
-- sections: [{"type":"mcq","count":15,"attempt":15,"marks_each":1}, ...]
create function public.validate_blueprint(p_total numeric, p_sections jsonb)
returns text[]
language plpgsql immutable as $$
declare
  problems text[] := '{}';
  s jsonb;
  computed numeric := 0;
  i int := 0;
begin
  if p_sections is null or jsonb_typeof(p_sections) <> 'array' or jsonb_array_length(p_sections) = 0 then
    return array['sections must be a non-empty array'];
  end if;
  for s in select * from jsonb_array_elements(p_sections) loop
    i := i + 1;
    if coalesce((s->>'count')::int, 0) <= 0 then
      problems := problems || format('section %s: count must be positive', i);
    end if;
    if coalesce((s->>'attempt')::int, 0) <= 0 then
      problems := problems || format('section %s: attempt must be positive', i);
    elsif (s->>'attempt')::int > coalesce((s->>'count')::int, 0) then
      problems := problems || format('section %s: attempt exceeds count', i);
    end if;
    if coalesce((s->>'marks_each')::numeric, 0) <= 0 then
      problems := problems || format('section %s: marks_each must be positive', i);
    end if;
    computed := computed + coalesce((s->>'attempt')::numeric, 0) * coalesce((s->>'marks_each')::numeric, 0);
  end loop;
  if computed <> p_total then
    problems := problems || format('sections add up to %s marks but total_marks is %s', computed, p_total);
  end if;
  return problems;
end $$;

create function private.check_blueprint_on_publish() returns trigger
language plpgsql as $$
declare
  problems text[];
begin
  if new.status in ('APPROVED', 'PUBLISHED') then
    problems := public.validate_blueprint(new.total_marks, new.sections);
    if array_length(problems, 1) > 0 then
      raise exception 'blueprint cannot be %: %', new.status, array_to_string(problems, '; ')
        using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

create trigger paper_blueprints_validate before insert or update on public.paper_blueprints
  for each row execute function private.check_blueprint_on_publish();

------------------------------------------------------------------------------
-- Authorization helpers (security definer; read memberships without RLS recursion)
------------------------------------------------------------------------------
create function private.has_role(p_roles public.member_role[], p_tenant uuid default null)
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.memberships m
    where m.user_id = auth.uid()
      and m.active
      and m.role = any (p_roles)
      and (p_tenant is null or m.tenant_id = p_tenant)
  );
$$;

create function private.is_staff() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.memberships m join public.tenants t on t.id = m.tenant_id
    where m.user_id = auth.uid() and m.active and t.kind = 'platform'
      and m.role in ('owner', 'editor')
  );
$$;

create function private.guardian_can(p_learner uuid, p_scope text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.guardian_links g
    where g.learner_id = p_learner
      and g.guardian_user_id = auth.uid()
      and g.status = 'verified'
      and p_scope = any (g.scopes)
  );
$$;

create function private.teacher_can(p_learner uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.teacher_assignments a
    where a.learner_id = p_learner and a.teacher_user_id = auth.uid() and a.active
      and exists (select 1 from public.memberships m
                  where m.user_id = auth.uid() and m.tenant_id = a.tenant_id
                    and m.role = 'teacher' and m.active)
  );
$$;

create function private.enrolled_in_edition(p_edition uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.enrollments e
    where e.learner_id = auth.uid() and e.edition_id = p_edition and e.active
  );
$$;

create function private.has_active_entitlement(p_learner uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.entitlements e
    where e.learner_id = p_learner and e.status in ('active', 'grace')
      and (e.valid_until is null or e.valid_until > now())
  );
$$;

grant usage on schema private to authenticated, service_role;
grant execute on all functions in schema private to authenticated, service_role;

------------------------------------------------------------------------------
-- Row level security
------------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array[
    'tenants','memberships','student_profiles','guardian_links','teacher_assignments','consent_events',
    'boards','examination_targets','subjects','textbook_editions','chapters','modules','learning_objectives',
    'enrollments','publications','source_rights','source_files','question_items','item_revisions',
    'item_answer_keys','paper_blueprints','quiz_attempts','quiz_answers','learning_events',
    'entitlements','audit_logs']
  loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

-- Tenants and memberships: see your own; staff see all.
create policy tenants_read on public.tenants for select to authenticated
  using (private.is_staff() or exists (
    select 1 from public.memberships m
    where m.tenant_id = tenants.id and m.user_id = auth.uid() and m.active));
create policy memberships_read on public.memberships for select to authenticated
  using (user_id = auth.uid() or private.is_staff()
         or private.has_role(array['tenant_admin']::public.member_role[], tenant_id));

-- Student profile: self, verified guardian with progress scope, assigned teacher, staff.
create policy student_profiles_read on public.student_profiles for select to authenticated
  using (user_id = auth.uid() or private.is_staff()
         or private.guardian_can(user_id, 'progress') or private.teacher_can(user_id));
create policy student_profiles_insert_self on public.student_profiles for insert to authenticated
  with check (user_id = auth.uid()
              and private.has_role(array['learner']::public.member_role[], tenant_id));
create policy student_profiles_update_self on public.student_profiles for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Guardian links: the learner and the linked guardian can see the link; staff too.
create policy guardian_links_read on public.guardian_links for select to authenticated
  using (learner_id = auth.uid() or guardian_user_id = auth.uid() or private.is_staff());

create policy teacher_assignments_read on public.teacher_assignments for select to authenticated
  using (teacher_user_id = auth.uid() or learner_id = auth.uid() or private.is_staff());

create policy consent_events_read on public.consent_events for select to authenticated
  using (learner_id = auth.uid() or actor_id = auth.uid() or private.is_staff());

-- Curriculum: published rows readable for onboarding; deeper content only for enrolled editions.
create policy boards_read on public.boards for select to authenticated
  using (status = 'PUBLISHED' or private.is_staff());
create policy targets_read on public.examination_targets for select to authenticated
  using (status = 'PUBLISHED' or private.is_staff());
create policy subjects_read on public.subjects for select to authenticated
  using (status = 'PUBLISHED' or private.is_staff());
create policy editions_read on public.textbook_editions for select to authenticated
  using (status = 'PUBLISHED' or private.is_staff());
create policy chapters_read on public.chapters for select to authenticated
  using (private.is_staff()
         or (status = 'PUBLISHED' and private.enrolled_in_edition(edition_id)));
create policy modules_read on public.modules for select to authenticated
  using (private.is_staff() or (status = 'PUBLISHED' and exists (
    select 1 from public.chapters c
    where c.id = chapter_id and c.status = 'PUBLISHED' and private.enrolled_in_edition(c.edition_id))));
create policy objectives_read on public.learning_objectives for select to authenticated
  using (private.is_staff() or (status = 'PUBLISHED' and exists (
    select 1 from public.chapters c
    where c.id = chapter_id and c.status = 'PUBLISHED' and private.enrolled_in_edition(c.edition_id))));

-- Enrollments: own rows only; created through onboarding as the learner themself.
create policy enrollments_read on public.enrollments for select to authenticated
  using (learner_id = auth.uid() or private.is_staff()
         or private.guardian_can(learner_id, 'progress') or private.teacher_can(learner_id));
create policy enrollments_insert_self on public.enrollments for insert to authenticated
  with check (learner_id = auth.uid()
              and private.has_role(array['learner']::public.member_role[], tenant_id)
              and exists (select 1 from public.textbook_editions e
                          where e.id = edition_id and e.status = 'PUBLISHED')
              and exists (select 1 from public.examination_targets t
                          where t.id = examination_target_id and t.status = 'PUBLISHED'));

-- Sources, questions, keys and blueprints: staff only. Learners get items via RPCs.
create policy publications_staff on public.publications for select to authenticated using (private.is_staff());
create policy source_rights_staff on public.source_rights for select to authenticated using (private.is_staff());
create policy source_files_staff on public.source_files for select to authenticated using (private.is_staff());
create policy question_items_staff on public.question_items for select to authenticated using (private.is_staff());
create policy item_revisions_staff on public.item_revisions for select to authenticated using (private.is_staff());
create policy answer_keys_staff on public.item_answer_keys for select to authenticated using (private.is_staff());
create policy blueprints_read on public.paper_blueprints for select to authenticated
  using (private.is_staff() or (status = 'PUBLISHED' and private.enrolled_in_edition(edition_id)));

-- Learning evidence: read own (and verified guardian / assigned teacher); writes via RPC only,
-- except self-reported study checkpoints which never count as assessment.
create policy quiz_attempts_read on public.quiz_attempts for select to authenticated
  using (learner_id = auth.uid() or private.is_staff()
         or private.guardian_can(learner_id, 'marks') or private.teacher_can(learner_id));
create policy quiz_answers_read on public.quiz_answers for select to authenticated
  using (exists (select 1 from public.quiz_attempts a where a.id = attempt_id and (
    a.learner_id = auth.uid() or private.is_staff()
    or private.guardian_can(a.learner_id, 'marks') or private.teacher_can(a.learner_id))));
create policy learning_events_read on public.learning_events for select to authenticated
  using (learner_id = auth.uid() or private.is_staff()
         or private.guardian_can(learner_id, 'progress') or private.teacher_can(learner_id));
create policy learning_events_insert_self on public.learning_events for insert to authenticated
  with check (learner_id = auth.uid()
              and kind in ('module_opened', 'video_study_confirmed', 'reading_checked', 'written_draft_saved')
              and private.has_role(array['learner']::public.member_role[], tenant_id));

-- Entitlements: read own; written only by the billing backend (service role).
create policy entitlements_read on public.entitlements for select to authenticated
  using (learner_id = auth.uid() or private.is_staff());

create policy audit_logs_owner on public.audit_logs for select to authenticated
  using (private.has_role(array['owner']::public.member_role[]));

------------------------------------------------------------------------------
-- Quiz RPCs (server-side selection and scoring; keys never leave the server)
------------------------------------------------------------------------------
create function public.start_quiz(p_module_id uuid, p_count int default 10)
returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_learner uuid := auth.uid();
  v_edition uuid;
  v_tenant uuid;
  v_ids uuid[];
  v_attempt uuid;
begin
  if v_learner is null then
    raise exception 'AUTH_REQUIRED' using errcode = '28000';
  end if;
  if p_count not between 5 and 15 then
    raise exception 'QUIZ_SIZE_INVALID' using errcode = '22023';
  end if;

  select c.edition_id into v_edition
  from public.modules m join public.chapters c on c.id = m.chapter_id
  where m.id = p_module_id and m.status = 'PUBLISHED' and c.status = 'PUBLISHED';
  if v_edition is null then
    raise exception 'SOURCE_NOT_APPROVED' using errcode = 'P0002';
  end if;

  select e.tenant_id into v_tenant from public.enrollments e
  where e.learner_id = v_learner and e.edition_id = v_edition and e.active;
  if v_tenant is null then
    raise exception 'BOARD_UNAVAILABLE' using errcode = '42501';
  end if;
  if not private.has_active_entitlement(v_learner) then
    raise exception 'ENTITLEMENT_REQUIRED' using errcode = '42501';
  end if;

  -- Latest published MCQ revision per item in this module, random order.
  select array_agg(rid) into v_ids from (
    select r.id as rid
    from public.question_items q
    join lateral (
      select r2.id from public.item_revisions r2
      where r2.item_id = q.id and r2.status = 'PUBLISHED'
      order by r2.revision desc limit 1) r on true
    where q.module_id = p_module_id and q.item_type = 'mcq'
    order by random()
    limit p_count) s;

  if v_ids is null then
    raise exception 'NO_PUBLISHED_ITEMS' using errcode = 'P0002';
  end if;

  insert into public.quiz_attempts (learner_id, tenant_id, module_id, item_revision_ids)
  values (v_learner, v_tenant, p_module_id, v_ids)
  returning id into v_attempt;

  return jsonb_build_object(
    'attempt_id', v_attempt,
    'items', (select jsonb_agg(jsonb_build_object(
                       'revision_id', r.id, 'stem', r.stem, 'options', r.options)
                     order by array_position(v_ids, r.id))
              from public.item_revisions r where r.id = any (v_ids)));
end $$;

create function public.answer_quiz_item(p_attempt_id uuid, p_revision_id uuid, p_option int)
returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_attempt public.quiz_attempts;
  v_key int;
  v_rationales jsonb;
  v_n int;
  v_correct boolean;
begin
  select * into v_attempt from public.quiz_attempts where id = p_attempt_id;
  if v_attempt.id is null or v_attempt.learner_id is distinct from auth.uid() then
    raise exception 'NOT_FOUND' using errcode = 'P0002';
  end if;
  if not (p_revision_id = any (v_attempt.item_revision_ids)) then
    raise exception 'ITEM_NOT_IN_ATTEMPT' using errcode = '22023';
  end if;

  select k.correct_option, r.rationales into v_key, v_rationales
  from public.item_answer_keys k join public.item_revisions r on r.id = k.revision_id
  where k.revision_id = p_revision_id;
  if v_key is null then
    raise exception 'RUBRIC_NOT_APPROVED' using errcode = 'P0002';
  end if;

  select coalesce(max(attempt_no), 0) + 1 into v_n
  from public.quiz_answers where attempt_id = p_attempt_id and revision_id = p_revision_id;
  v_correct := (p_option = v_key);

  insert into public.quiz_answers (attempt_id, revision_id, chosen_option, is_correct, attempt_no)
  values (p_attempt_id, p_revision_id, p_option, v_correct, v_n);

  insert into public.learning_events (learner_id, tenant_id, kind, ref, idempotency_key)
  values (v_attempt.learner_id, v_attempt.tenant_id, 'quiz_answered',
          jsonb_build_object('attempt_id', p_attempt_id, 'revision_id', p_revision_id,
                             'attempt_no', v_n, 'is_correct', v_correct),
          format('quiz:%s:%s:%s', p_attempt_id, p_revision_id, v_n));

  return jsonb_build_object(
    'is_correct', v_correct,
    'first_attempt', v_n = 1,
    'correct_option', v_key,
    'rationales', v_rationales);
end $$;

revoke all on function public.start_quiz(uuid, int) from public, anon;
revoke all on function public.answer_quiz_item(uuid, uuid, int) from public, anon;
grant execute on function public.start_quiz(uuid, int) to authenticated;
grant execute on function public.answer_quiz_item(uuid, uuid, int) to authenticated;
grant execute on function public.validate_blueprint(numeric, jsonb) to authenticated;
