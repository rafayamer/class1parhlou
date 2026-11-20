-- Full audit (20 November 2026): defence in depth for the database.

-- 1. Internal functions must never be callable by users. The private schema isn't exposed through the API today, but if it
--    ever were, grant_owner(my_id, 'owner@email') would hand anyone the owner role. Trigger functions need no grant to run.
revoke all on function private.grant_owner(uuid, text), private.on_auth_user_created() from public, anon, authenticated;

-- 2. Students may change only their own name and birth year, never which organisation (tenant) their profile belongs to.
revoke update on public.student_profiles from anon, authenticated;
grant update (display_name, birth_year) on public.student_profiles to authenticated;

-- 3. The sync log is append-only and per student; cap how fast one account can write to it (storage abuse).
create index if not exists learner_events_user_created on public.learner_events (user_id, created_at);
create or replace function private.learner_events_rate() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.learner_events where user_id = new.user_id and created_at > now() - interval '1 day') >= 20000 then
    raise exception 'TOO_MANY_EVENTS' using errcode = '54000';
  end if;
  return new;
end $$;
drop trigger if exists learner_events_rate on public.learner_events;
create trigger learner_events_rate before insert on public.learner_events
  for each row execute function private.learner_events_rate();
revoke all on function private.learner_events_rate() from public, anon, authenticated;

-- 4. Leagues: what other children see must be clean, and the board must be hard to cheat.
--    Avatars come from the app's fixed list only.
create or replace function private.avatar_ok(p text) returns boolean language sql immutable set search_path = '' as $$
  select p = any (array['🦉','🦁','🐯','🦊','🐼','🐬','🦅','🐢','🚀','⭐','📚','🧠']);
$$;
create or replace function public.social_set_avatar(p_avatar text) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform public.social_me();
  if not private.avatar_ok(btrim(coalesce(p_avatar, ''))) then raise exception 'AVATAR_NOT_ALLOWED' using errcode = '22023'; end if;
  update public.social_profiles set avatar = btrim(p_avatar) where user_id = private.me();
end $$;

--    The first name other students see is filtered; anything unsuitable shows as "Learner".
create or replace function public.social_me() returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v uuid := private.me(); p public.social_profiles; v_name text;
begin
  select * into p from public.social_profiles where user_id = v;
  if not found then
    select left(split_part(btrim(coalesce(display_name, '')), ' ', 1), 20) into v_name from public.student_profiles where user_id = v;
    if v_name is null or v_name = '' or not private.name_ok(v_name) then v_name := 'Learner'; end if;
    loop
      begin
        insert into public.social_profiles (user_id, friend_code, first_name) values (v, private.new_code(), v_name) returning * into p;
        exit;
      exception when unique_violation then null; -- code taken: try another
      end;
    end loop;
  end if;
  return jsonb_build_object('friendCode', p.friend_code, 'firstName', p.first_name, 'avatar', p.avatar, 'league', p.league);
end $$;

--    Weekly XP is reported by the phone, so it is capped at what real study can earn: 1,500 XP for each day of the league week
--    so far (the daily goal tops out at 480). A streak can't be longer than the account is old.
create or replace function public.social_post_week(p_xp int, p_streak int) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare
  v uuid := private.me(); v_week date := private.week_start(); v_league smallint; v_group int; v_rank int; v_size int; last record;
  v_max_xp int := 1500 * ((current_date - v_week) + 1);
  v_max_streak int;
begin
  perform public.social_me();
  select (current_date - created_at::date) + 1 into v_max_streak from auth.users where id = v;
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
  update public.weekly_xp
  set xp = greatest(0, least(coalesce(p_xp, 0), v_max_xp)),
      streak = greatest(0, least(coalesce(p_streak, 0), coalesce(v_max_streak, 1), 3650)),
      updated_at = now()
  where user_id = v and week = v_week;
  return public.league_board();
end $$;

--    Clean what's already there.
update public.social_profiles set first_name = 'Learner' where not private.name_ok(first_name);
update public.social_profiles set avatar = '🦉' where not private.avatar_ok(avatar);
update public.weekly_xp set xp = least(xp, 1500 * 7), streak = least(streak, 3650);

revoke all on function private.avatar_ok(text) from public, anon;
do $$
declare f text;
begin
  foreach f in array array['social_me()', 'social_set_avatar(text)', 'social_post_week(int, int)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

-- 5. Sums of AI spending for the gateway, done in the database (the REST API returns at most 1,000 rows, so adding up rows
--    in the gateway undercounted heavy users and the daily ceiling). Server only.
create index if not exists ai_usage_created on public.ai_usage (created_at);
create or replace function public.ai_spend(p_caller text, p_since timestamptz) returns numeric
language sql stable security definer set search_path = '' as $$
  select coalesce(sum(usd), 0) from public.ai_usage
  where created_at >= p_since and (p_caller is null or caller = p_caller);
$$;
revoke all on function public.ai_spend(text, timestamptz) from public, anon, authenticated;
grant execute on function public.ai_spend(text, timestamptz) to service_role;
