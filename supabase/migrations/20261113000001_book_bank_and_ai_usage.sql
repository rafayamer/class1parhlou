-- Book bank (book-exact questions with solutions as marking keys) and AI usage log.
-- Decision D-016: content mirrors the textbook; solutions are never readable by learners.

create type public.book_item_kind as enum ('definition', 'example', 'exercise', 'theorem');
create type public.answer_matching as enum ('exact', 'method');

create table public.book_items (
  id            uuid primary key default gen_random_uuid(),
  chapter_id    uuid not null references public.chapters(id),
  kind          public.book_item_kind not null,
  exercise      text,                 -- e.g. '1.2'; null for in-text definitions and examples
  number        text not null,        -- question or example number as printed, e.g. '3(ii)'
  printed_page  int check (printed_page > 0),
  text          text not null,        -- exactly as printed in the verified edition
  marks         numeric(5,2) not null check (marks > 0),
  matching      public.answer_matching not null default 'method',
  status        public.publication_status not null default 'DRAFT',
  created_at    timestamptz not null default now(),
  unique (chapter_id, kind, exercise, number)
);

-- Solutions and rubrics live apart from the items so no learner-facing policy can expose them.
create table public.book_solutions (
  item_id           uuid primary key references public.book_items(id) on delete cascade,
  model_answer      jsonb not null,   -- array of lines
  rubric            jsonb not null,   -- [{id, description, marks, why, how}]
  book_final_answer text,             -- the answer printed in the book's answer key, when there is one
  origin            text not null check (origin in ('book', 'ai_drafted')),
  -- Automated double check (D-015): a second independent AI solve must agree, and the final answer must
  -- match the book's own answer key, before the item can be published.
  second_check_ok   boolean not null default false,
  matches_book_key  boolean not null default false,
  check (jsonb_typeof(model_answer) = 'array' and jsonb_typeof(rubric) = 'array')
);

create function private.book_item_publish_guard() returns trigger
language plpgsql as $$
declare s public.book_solutions;
declare rubric_total numeric;
begin
  if new.status in ('APPROVED', 'PUBLISHED') then
    select * into s from public.book_solutions where item_id = new.id;
    if s.item_id is null then
      raise exception 'book item cannot be %: it has no solution', new.status using errcode = 'P0001';
    end if;
    select coalesce(sum((c->>'marks')::numeric), 0) into rubric_total from jsonb_array_elements(s.rubric) c;
    if rubric_total <> new.marks then
      raise exception 'book item cannot be %: rubric adds up to % but the question is worth %', new.status, rubric_total, new.marks using errcode = 'P0001';
    end if;
    if s.origin = 'ai_drafted' and not s.second_check_ok then
      raise exception 'book item cannot be %: the AI-drafted solution has not passed its second check', new.status using errcode = 'P0001';
    end if;
    if s.book_final_answer is not null and not s.matches_book_key then
      raise exception 'book item cannot be %: the solution does not match the book answer key', new.status using errcode = 'P0001';
    end if;
  end if;
  return new;
end $$;

create trigger book_items_publish_guard before insert or update on public.book_items
  for each row execute function private.book_item_publish_guard();

create table public.ai_usage (
  id              uuid primary key default gen_random_uuid(),
  caller          text not null,
  task            text not null,
  model           text not null,
  prompt_version  text not null,
  input_tokens    int not null default 0,
  output_tokens   int not null default 0,
  attempts        int not null default 1,
  ok              boolean not null,
  created_at      timestamptz not null default now()
);
create index on public.ai_usage (caller, created_at);

alter table public.book_items enable row level security;
alter table public.book_solutions enable row level security;
alter table public.ai_usage enable row level security;

create policy book_items_read on public.book_items for select to authenticated
  using (private.is_staff() or (status = 'PUBLISHED' and exists (
    select 1 from public.chapters c
    where c.id = chapter_id and c.status = 'PUBLISHED' and private.enrolled_in_edition(c.edition_id))));
create policy book_solutions_staff on public.book_solutions for select to authenticated using (private.is_staff());
create policy ai_usage_owner on public.ai_usage for select to authenticated
  using (private.has_role(array['owner']::public.member_role[]));
