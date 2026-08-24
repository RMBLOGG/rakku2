-- =====================================================================
-- CLAN TAG MIGRATION
-- Nambahin "Tag Clan" (singkatan pendek, mis. [RKKU]) yang ditampilin di
-- samping nama clan - dipisah dari clan_migration.sql biar bisa
-- dijalankan belakangan tanpa perlu re-run migration Clan yang lama.
--
-- CARA PAKAI: Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run. Jalankan SETELAH clan_migration.sql.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. KOLOM BARU: tag
--    2-5 karakter, huruf/angka aja, disimpan HURUF BESAR semua, dan unik
--    (gak boleh ada 2 clan pakai tag yang sama). Boleh kosong (NULL) buat
--    clan yang udah kebuat sebelum migration ini jalan.
-- ---------------------------------------------------------------------
alter table public.clans
  add column if not exists tag text;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'clans_tag_unique'
  ) then
    alter table public.clans add constraint clans_tag_unique unique (tag);
  end if;
end $$;

-- ---------------------------------------------------------------------
-- 2. Ganti RPC create_clan supaya nerima p_tag. Function lama (3
--    parameter: name, description, avatar_url) di-drop dulu supaya gak
--    nyangkut jadi 2 function berbeda (overload) yang bisa bikin
--    PostgREST bingung manggil yang mana.
-- ---------------------------------------------------------------------
drop function if exists public.create_clan(text, text, text);

create or replace function public.create_clan(
  p_name text,
  p_description text default null,
  p_tag text default null,
  p_avatar_url text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_coin integer;
  v_clan_id uuid;
  v_cost constant integer := 5500;
  v_name text := trim(coalesce(p_name, ''));
  v_tag text := nullif(upper(trim(coalesce(p_tag, ''))), '');
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  if length(v_name) < 3 or length(v_name) > 30 then
    raise exception 'invalid_name';
  end if;

  if v_tag is not null and (length(v_tag) < 2 or length(v_tag) > 5 or v_tag !~ '^[A-Z0-9]+$') then
    raise exception 'invalid_tag';
  end if;

  if exists (select 1 from public.clan_members where user_id = v_user) then
    raise exception 'already_in_clan';
  end if;

  select rakku_coin into v_coin from public.profiles where id = v_user for update;
  if v_coin is null or v_coin < v_cost then
    raise exception 'insufficient_coin';
  end if;

  perform set_config('app.allow_admin_update', 'true', true);

  update public.profiles set rakku_coin = rakku_coin - v_cost where id = v_user;

  insert into public.clans (name, description, tag, avatar_url, leader_id, level, total_donated)
  values (v_name, nullif(trim(coalesce(p_description, '')), ''), v_tag, p_avatar_url, v_user, 1, 0)
  returning id into v_clan_id;

  insert into public.clan_members (clan_id, user_id, role, total_donated)
  values (v_clan_id, v_user, 'leader', 0);

  return v_clan_id;
exception
  when unique_violation then
    -- Bisa dari constraint nama ATAU tag - cek tag dulu (lebih spesifik).
    if exists (select 1 from public.clans where tag = v_tag) then
      raise exception 'tag_taken';
    else
      raise exception 'name_taken';
    end if;
end;
$$;

grant execute on function public.create_clan(text, text, text, text) to authenticated;

-- ---------------------------------------------------------------------
-- 3. Update search_clans & get_clan_detail supaya ikut balikin tag.
-- ---------------------------------------------------------------------
drop function if exists public.search_clans(text, integer);

create or replace function public.search_clans(p_query text default null, p_limit integer default 30)
returns table (
  id uuid,
  name text,
  tag text,
  avatar_url text,
  level integer,
  total_donated bigint,
  member_count bigint,
  capacity integer
)
language sql
security definer
set search_path = public
as $$
  select
    c.id,
    c.name,
    c.tag,
    c.avatar_url,
    c.level,
    c.total_donated,
    (select count(*) from public.clan_members m where m.clan_id = c.id) as member_count,
    public.clan_capacity_for_level(c.level) as capacity
  from public.clans c
  where p_query is null or trim(p_query) = ''
     or c.name ilike '%' || trim(p_query) || '%'
     or c.tag ilike '%' || trim(p_query) || '%'
  order by c.level desc, c.total_donated desc
  limit greatest(coalesce(p_limit, 30), 1);
$$;

grant execute on function public.search_clans(text, integer) to authenticated;

drop function if exists public.get_clan_detail(uuid);

create or replace function public.get_clan_detail(p_clan_id uuid)
returns table (
  id uuid,
  name text,
  tag text,
  description text,
  avatar_url text,
  leader_id uuid,
  leader_username text,
  level integer,
  total_donated bigint,
  member_count bigint,
  capacity integer,
  daily_reward integer,
  next_level_donation bigint,
  created_at timestamptz
)
language sql
security definer
set search_path = public
as $$
  select
    c.id,
    c.name,
    c.tag,
    c.description,
    c.avatar_url,
    c.leader_id,
    lp.username,
    c.level,
    c.total_donated,
    (select count(*) from public.clan_members m where m.clan_id = c.id),
    public.clan_capacity_for_level(c.level),
    public.clan_daily_reward_for_level(c.level),
    public.clan_donation_required_for_level(least(c.level + 1, 100)),
    c.created_at
  from public.clans c
  left join public.profiles lp on lp.id = c.leader_id
  where c.id = p_clan_id;
$$;

grant execute on function public.get_clan_detail(uuid) to authenticated;

-- =====================================================================
-- SELESAI. Setelah ini dijalankan, waktu bikin clan baru bisa isi Tag
-- Clan (2-5 karakter, opsional), dan tag itu ikut muncul di Leaderboard
-- & detail clan.
-- =====================================================================
