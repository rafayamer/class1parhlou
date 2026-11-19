-- Friends, weekly leagues and class groups (Duolingo-style), safe for under-18 students:
--   * no free-text messages between students: the only contact is a 👏 nudge;
--   * other students see only a first name, an emoji avatar, XP and streak;
--   * friends are added by sharing a code (both sides then see each other); class groups by a group code.
-- Tables are reached only through the functions below (row level security, no direct policies).

create table public.social_profiles (
  user_id      uuid primary key references auth.users (id) on delete cascade,
  friend_code  text not null unique,
  first_name   text not null default 'Learner',
  avatar       text not null default '🦉',
  league       smallint not null default 0,          -- 0 Bronze … 6 Diamond
  created_at   timestamptz not null default now()
);

create table public.weekly_xp (
  user_id     uuid not null references auth.users (id) on delete cascade,
  week        date not null,                          -- Monday of the week (UTC)
  league      smallint not null,
  group_no    int not null,                           -- the student's group of up to 30 in that league this week
  xp          int not null default 0,
  streak      int not null default 0,
  updated_at  timestamptz not null default now(),
  primary key (user_id, week)
);
create index on public.weekly_xp (week, league, group_no);

create table public.friendships (
  user_id    uuid not null references auth.users (id) on delete cascade,
  friend_id  uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, friend_id),
  check (user_id <> friend_id)
);

create table public.nudges (
  id         bigint generated always as identity primary key,
  from_user  uuid not null references auth.users (id) on delete cascade,
  to_user    uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index on public.nudges (to_user, created_at);

create table public.class_groups (
  id         uuid primary key default gen_random_uuid(),
  code       text not null unique,
  name       text not null,
  created_by uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
create table public.class_members (
  group_id   uuid not null references public.class_groups (id) on delete cascade,
  user_id    uuid not null references auth.users (id) on delete cascade,
  joined_at  timestamptz not null default now(),
  primary key (group_id, user_id)
);

alter table public.social_profiles enable row level security;
alter table public.weekly_xp enable row level security;
alter table public.friendships enable row level security;
alter table public.nudges enable row level security;
alter table public.class_groups enable row level security;
alter table public.class_members enable row level security;

-- ── Helpers ────────────────────────────────────────────────────────────────────────────────────────────
create function private.me() returns uuid language plpgsql stable set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  return auth.uid();
end $$;

-- 6 letters/digits without look-alikes (no 0/O, 1/I).
create function private.new_code() returns text language sql volatile set search_path = '' as $$
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '') from generate_series(1, 6);
$$;

create function private.week_start(p_at timestamptz default now()) returns date language sql immutable set search_path = '' as $$
  select (date_trunc('week', p_at at time zone 'UTC'))::date;
$$;

-- The student's profile, made on first use: a friend code and the first word of their name.
create function public.social_me() returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); p public.social_profiles; v_name text;
begin
  select * into p from public.social_profiles where user_id = v;
  if not found then
    select split_part(btrim(coalesce(display_name, 'Learner')), ' ', 1) into v_name from public.student_profiles where user_id = v;
    loop
      begin
        insert into public.social_profiles (user_id, friend_code, first_name) values (v, private.new_code(), left(coalesce(nullif(v_name, ''), 'Learner'), 20))
        returning * into p;
        exit;
      exception when unique_violation then null; -- code taken: try another
      end;
    end loop;
  end if;
  return jsonb_build_object('friendCode', p.friend_code, 'firstName', p.first_name, 'avatar', p.avatar, 'league', p.league);
end $$;

create function public.social_set_avatar(p_avatar text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform public.social_me();
  update public.social_profiles set avatar = left(coalesce(nullif(btrim(p_avatar), ''), '🦉'), 4) where user_id = private.me();
end $$;

-- This week's XP and streak (the app works them out from the study log). The first post of a week settles last week's
-- league: top 7 of a group move up, bottom 5 move down; then the student joins a group of up to 30 in their league.
create function public.social_post_week(p_xp int, p_streak int) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
  v uuid := private.me(); v_week date := private.week_start(); v_league smallint; v_group int; v_rank int; v_size int; last record;
begin
  perform public.social_me();
  select league into v_league from public.social_profiles where user_id = v;
  if not exists (select 1 from public.weekly_xp where user_id = v and week = v_week) then
    select * into last from public.weekly_xp where user_id = v and week = v_week - 7;
    if found then
      select r, n into v_rank, v_size from (
        select user_id, rank() over (order by xp desc) r, count(*) over () n from public.weekly_xp
        where week = last.week and league = last.league and group_no = last.group_no) t where t.user_id = v;
      if last.xp > 0 and v_rank <= 7 and v_size >= 5 then v_league := least(last.league + 1, 6);
      elsif v_size >= 10 and v_rank > v_size - 5 then v_league := greatest(last.league - 1, 0);
      else v_league := last.league; end if;
      update public.social_profiles set league = v_league where user_id = v;
    end if;
    select g into v_group from (
      select group_no g, count(*) n from public.weekly_xp where week = v_week and league = v_league group by group_no) t
      where n < 30 order by g limit 1;
    if v_group is null then
      select coalesce(max(group_no), 0) + 1 into v_group from public.weekly_xp where week = v_week and league = v_league;
    end if;
    insert into public.weekly_xp (user_id, week, league, group_no, xp, streak) values (v, v_week, v_league, v_group, 0, 0);
  end if;
  update public.weekly_xp set xp = greatest(0, least(p_xp, 100000)), streak = greatest(0, least(p_streak, 10000)), updated_at = now()
  where user_id = v and week = v_week;
  return public.league_board();
end $$;

-- My league group this week: rank, first name, avatar, XP; and days left.
create function public.league_board() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.me(); w public.weekly_xp;
begin
  select * into w from public.weekly_xp where user_id = v and week = private.week_start();
  if not found then return jsonb_build_object('league', 0, 'rows', '[]'::jsonb, 'daysLeft', 7 - extract(isodow from now() at time zone 'UTC')::int + 1); end if;
  return jsonb_build_object(
    'league', w.league,
    'daysLeft', 7 - extract(isodow from now() at time zone 'UTC')::int + 1,
    'rows', coalesce((select jsonb_agg(jsonb_build_object('rank', r, 'name', first_name, 'avatar', avatar, 'xp', xp, 'me', user_id = v) order by r)
      from (select x.user_id, p.first_name, p.avatar, x.xp, rank() over (order by x.xp desc, x.updated_at) r
            from public.weekly_xp x join public.social_profiles p using (user_id)
            where x.week = w.week and x.league = w.league and x.group_no = w.group_no) t), '[]'::jsonb));
end $$;

-- Friends: adding by code makes both students friends (sharing a code is the consent).
create function public.friend_add(p_code text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); f uuid;
begin
  perform public.social_me();
  select user_id into f from public.social_profiles where friend_code = upper(btrim(p_code));
  if f is null then raise exception 'CODE_NOT_FOUND' using errcode = 'P0002'; end if;
  if f = v then raise exception 'THAT_IS_YOU' using errcode = '22023'; end if;
  if (select count(*) from public.friendships where user_id = v) >= 100 then raise exception 'TOO_MANY_FRIENDS' using errcode = '22023'; end if;
  insert into public.friendships (user_id, friend_id) values (v, f), (f, v) on conflict do nothing;
  return public.friends_list();
end $$;

create function public.friend_remove(p_code text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); f uuid;
begin
  select user_id into f from public.social_profiles where friend_code = upper(btrim(p_code));
  delete from public.friendships where (user_id = v and friend_id = f) or (user_id = f and friend_id = v);
end $$;

create function public.friends_list() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.me();
begin
  return coalesce((select jsonb_agg(jsonb_build_object('code', p.friend_code, 'name', p.first_name, 'avatar', p.avatar,
      'xp', coalesce(x.xp, 0), 'streak', coalesce(x.streak, 0)) order by coalesce(x.xp, 0) desc)
    from public.friendships f join public.social_profiles p on p.user_id = f.friend_id
    left join public.weekly_xp x on x.user_id = f.friend_id and x.week = private.week_start()
    where f.user_id = v), '[]'::jsonb);
end $$;

-- A 👏 nudge to a friend: at most one per friend per day; no text.
create function public.nudge(p_code text) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); f uuid;
begin
  select user_id into f from public.social_profiles where friend_code = upper(btrim(p_code));
  if f is null or not exists (select 1 from public.friendships where user_id = v and friend_id = f) then
    raise exception 'NOT_A_FRIEND' using errcode = '42501';
  end if;
  if exists (select 1 from public.nudges where from_user = v and to_user = f and created_at > now() - interval '1 day') then return; end if;
  insert into public.nudges (from_user, to_user) values (v, f);
end $$;

create function public.my_nudges() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.me();
begin
  return coalesce((select jsonb_agg(jsonb_build_object('name', p.first_name, 'avatar', p.avatar, 'at', n.created_at) order by n.created_at desc)
    from public.nudges n join public.social_profiles p on p.user_id = n.from_user
    where n.to_user = v and n.created_at > now() - interval '3 days'), '[]'::jsonb);
end $$;

-- Class groups: a code a teacher or student shares with the class; the group has its own weekly board.
create function public.group_create(p_name text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); g public.class_groups; v_name text := left(btrim(coalesce(p_name, '')), 40);
begin
  perform public.social_me();
  if v_name = '' then raise exception 'NAME_REQUIRED' using errcode = '22023'; end if;
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

create function public.group_join(p_code text) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); g public.class_groups;
begin
  perform public.social_me();
  select * into g from public.class_groups where code = upper(btrim(p_code));
  if not found then raise exception 'CODE_NOT_FOUND' using errcode = 'P0002'; end if;
  if (select count(*) from public.class_members where group_id = g.id) >= 200 then raise exception 'GROUP_FULL' using errcode = '22023'; end if;
  insert into public.class_members (group_id, user_id) values (g.id, v) on conflict do nothing;
  return jsonb_build_object('code', g.code, 'name', g.name);
end $$;

create function public.group_leave(p_code text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  delete from public.class_members m using public.class_groups g
  where g.id = m.group_id and g.code = upper(btrim(p_code)) and m.user_id = private.me();
end $$;

-- My groups, each with this week's board (members only).
create function public.my_groups() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.me();
begin
  return coalesce((select jsonb_agg(jsonb_build_object('code', g.code, 'name', g.name, 'rows',
      (select coalesce(jsonb_agg(jsonb_build_object('name', p.first_name, 'avatar', p.avatar, 'xp', coalesce(x.xp, 0), 'me', m2.user_id = v)
         order by coalesce(x.xp, 0) desc), '[]'::jsonb)
       from public.class_members m2 join public.social_profiles p on p.user_id = m2.user_id
       left join public.weekly_xp x on x.user_id = m2.user_id and x.week = private.week_start()
       where m2.group_id = g.id)) order by g.created_at)
    from public.class_groups g join public.class_members m on m.group_id = g.id and m.user_id = v), '[]'::jsonb);
end $$;

do $$
declare f text;
begin
  foreach f in array array['social_me()', 'social_set_avatar(text)', 'social_post_week(int, int)', 'league_board()', 'friend_add(text)',
    'friend_remove(text)', 'friends_list()', 'nudge(text)', 'my_nudges()', 'group_create(text)', 'group_join(text)', 'group_leave(text)', 'my_groups()'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
