-- =====================================================================
-- CLAN MIGRATION
-- Fitur: Clan (buat clan, gabung, donasi buat naikin Level Clan, Daily
-- Claim RC per anggota, Leaderboard Clan).
--
-- CARA PAKAI: Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run. Jalankan SETELAH profile_stats_migration.sql
-- (butuh tabel profiles yang sudah ada kolom rakku_coin, dan trigger
-- trg_protect_profile_columns + session var 'app.allow_admin_update' yang
-- sama polanya dengan borders_migration.sql).
--
-- RINGKASAN ATURAN (sesuai spek fitur):
-- - Biaya bikin Clan: 5.500 RC.
-- - Clan mulai dari Lv.1, kapasitas awal 50 anggota, maksimal Lv.100
--   dengan kapasitas 1.000 anggota.
-- - Kapasitas anggota = MAX(50, Level * 10) -> cocok sama semua contoh
--   di spek (Lv.1->50, Lv.10->100, Lv.20->200, ... Lv.50->500, Lv.100->1000).
-- - Level Clan naik berdasarkan TOTAL RC yang didonasikan anggota
--   (akumulatif, gak pernah berkurang), pakai kurva akar kuadrat biar
--   Lv.100 sengaja SUSAH dicapai (butuh donasi total ~9,8 juta RC).
-- - Hadiah Daily Claim per anggota naik linear dari 50 RC (Lv.1) sampai
--   2.000 RC (Lv.100).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. TABEL clans & clan_members
-- ---------------------------------------------------------------------
create table if not exists public.clans (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  description text,
  avatar_url text,
  leader_id uuid not null references public.profiles(id) on delete cascade,
  level integer not null default 1,
  total_donated bigint not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.clan_members (
  clan_id uuid not null references public.clans(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  role text not null default 'member' check (role in ('leader', 'member')),
  total_donated bigint not null default 0,
  -- Tanggal terakhir user ini klaim Daily Claim RC clan-nya. Dicek di RPC
  -- claim_daily_clan_reward supaya cuma bisa klaim 1x per hari (per user,
  -- BUKAN 1x per clan - jadi tiap anggota klaim jatahnya masing-masing).
  last_daily_claim_date date,
  joined_at timestamptz not null default now(),
  primary key (clan_id, user_id),
  -- 1 user cuma boleh gabung 1 clan dalam satu waktu.
  unique (user_id)
);

create index if not exists idx_clan_members_clan_id on public.clan_members(clan_id);
create index if not exists idx_clans_leaderboard on public.clans(level desc, total_donated desc);

alter table public.clans enable row level security;
alter table public.clan_members enable row level security;

-- Baca boleh siapa saja yang login (buat leaderboard & lihat detail clan
-- orang lain). Insert/update/delete SENGAJA gak dikasih policy sama
-- sekali - artinya diblokir total lewat REST API biasa, dan HARUS lewat
-- RPC SECURITY DEFINER di bawah supaya semua validasi (saldo, kapasitas,
-- 1 user 1 clan, dst) gak bisa dilewatin dari client.
drop policy if exists clans_select_all on public.clans;
create policy clans_select_all on public.clans for select using (true);

drop policy if exists clan_members_select_all on public.clan_members;
create policy clan_members_select_all on public.clan_members for select using (true);

-- ---------------------------------------------------------------------
-- 2. FUNGSI RUMUS (level, kapasitas, hadiah harian, syarat level)
--    Dipisah jadi fungsi kecil biar gampang dipanggil ulang dari
--    beberapa RPC dan gampang di-tweak belakangan tanpa bongkar semua
--    RPC.
-- ---------------------------------------------------------------------

-- Kapasitas anggota dari Level Clan: MAX(50, level*10), dibatasi 1000.
create or replace function public.clan_capacity_for_level(p_level integer)
returns integer
language sql
immutable
as $$
  select least(1000, greatest(50, greatest(p_level, 1) * 10));
$$;

-- Level Clan dari total RC yang sudah didonasikan (akumulatif). Pakai
-- kurva akar kuadrat: level = 1 + floor(sqrt(total/1000)), dibatasi
-- 1..100. Ini yang bikin Lv.100 sengaja susah (butuh total donasi
-- sekitar 9.801.000 RC dari SELURUH anggota clan, bukan cuma 1 orang).
create or replace function public.clan_level_for_donation(p_total bigint)
returns integer
language sql
immutable
as $$
  select least(100, greatest(1, 1 + floor(sqrt(greatest(p_total, 0)::float8 / 1000.0))::integer));
$$;

-- Kebalikan dari fungsi di atas: total RC MINIMAL yang harus sudah
-- didonasikan buat mencapai suatu level. Dipakai buat nampilin progress
-- bar "menuju level berikutnya" di UI.
create or replace function public.clan_donation_required_for_level(p_level integer)
returns bigint
language sql
immutable
as $$
  select (power(greatest(p_level, 1) - 1, 2) * 1000)::bigint;
$$;

-- Hadiah Daily Claim per anggota: naik linear dari 50 RC (Lv.1) sampai
-- 2.000 RC (Lv.100).
create or replace function public.clan_daily_reward_for_level(p_level integer)
returns integer
language sql
immutable
as $$
  select least(2000, greatest(50, 50 + floor((greatest(p_level, 1) - 1) * 1950.0 / 99.0)::integer));
$$;

-- ---------------------------------------------------------------------
-- 3. RPC: create_clan
--    Biaya 5.500 RC, langsung dipotong dari rakku_coin. User yang bikin
--    otomatis jadi leader clan barunya.
-- ---------------------------------------------------------------------
create or replace function public.create_clan(p_name text, p_description text default null, p_avatar_url text default null)
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
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  if length(v_name) < 3 or length(v_name) > 30 then
    raise exception 'invalid_name';
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

  insert into public.clans (name, description, avatar_url, leader_id, level, total_donated)
  values (v_name, nullif(trim(coalesce(p_description, '')), ''), p_avatar_url, v_user, 1, 0)
  returning id into v_clan_id;

  insert into public.clan_members (clan_id, user_id, role, total_donated)
  values (v_clan_id, v_user, 'leader', 0);

  return v_clan_id;
exception
  when unique_violation then
    raise exception 'name_taken';
end;
$$;

grant execute on function public.create_clan(text, text, text) to authenticated;

-- ---------------------------------------------------------------------
-- 4. RPC: join_clan / leave_clan
-- ---------------------------------------------------------------------
create or replace function public.join_clan(p_clan_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_level integer;
  v_capacity integer;
  v_count integer;
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  if exists (select 1 from public.clan_members where user_id = v_user) then
    raise exception 'already_in_clan';
  end if;

  select level into v_level from public.clans where id = p_clan_id for update;
  if v_level is null then
    raise exception 'clan_not_found';
  end if;

  v_capacity := public.clan_capacity_for_level(v_level);
  select count(*) into v_count from public.clan_members where clan_id = p_clan_id;

  if v_count >= v_capacity then
    raise exception 'clan_full';
  end if;

  insert into public.clan_members (clan_id, user_id, role, total_donated)
  values (p_clan_id, v_user, 'member', 0);

  return true;
end;
$$;

grant execute on function public.join_clan(uuid) to authenticated;

-- Keluar dari clan. Kalau yang keluar adalah leader dan masih ada anggota
-- lain, kepemimpinan otomatis pindah ke anggota yang paling lama gabung.
-- Kalau leader keluar dan clan jadi kosong, clan-nya otomatis bubar
-- (dihapus).
create or replace function public.leave_clan()
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_clan_id uuid;
  v_role text;
  v_next_leader uuid;
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  select clan_id, role into v_clan_id, v_role from public.clan_members where user_id = v_user;
  if v_clan_id is null then
    raise exception 'not_in_clan';
  end if;

  delete from public.clan_members where user_id = v_user;

  if v_role = 'leader' then
    select user_id into v_next_leader
    from public.clan_members
    where clan_id = v_clan_id
    order by joined_at asc
    limit 1;

    if v_next_leader is null then
      delete from public.clans where id = v_clan_id;
    else
      update public.clan_members set role = 'leader' where clan_id = v_clan_id and user_id = v_next_leader;
      update public.clans set leader_id = v_next_leader where id = v_clan_id;
    end if;
  end if;

  return true;
end;
$$;

grant execute on function public.leave_clan() to authenticated;

-- ---------------------------------------------------------------------
-- 5. RPC: donate_to_clan
--    Potong rakku_coin milik user, tambahin ke total_donated clan &
--    total_donated pribadi user di clan itu, lalu hitung ulang Level
--    Clan berdasarkan total donasi terbaru.
-- ---------------------------------------------------------------------
create or replace function public.donate_to_clan(p_amount integer)
returns table (new_level integer, new_total_donated bigint)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_clan_id uuid;
  v_coin integer;
  v_level integer;
  v_total bigint;
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  if p_amount is null or p_amount <= 0 then
    raise exception 'invalid_amount';
  end if;

  select clan_id into v_clan_id from public.clan_members where user_id = v_user;
  if v_clan_id is null then
    raise exception 'not_in_clan';
  end if;

  select rakku_coin into v_coin from public.profiles where id = v_user for update;
  if v_coin is null or v_coin < p_amount then
    raise exception 'insufficient_coin';
  end if;

  perform set_config('app.allow_admin_update', 'true', true);

  update public.profiles set rakku_coin = rakku_coin - p_amount where id = v_user;

  update public.clan_members
  set total_donated = total_donated + p_amount
  where clan_id = v_clan_id and user_id = v_user;

  update public.clans
  set total_donated = total_donated + p_amount
  where id = v_clan_id
  returning total_donated into v_total;

  v_level := public.clan_level_for_donation(v_total);

  update public.clans set level = v_level where id = v_clan_id;

  return query select v_level, v_total;
end;
$$;

grant execute on function public.donate_to_clan(integer) to authenticated;

-- ---------------------------------------------------------------------
-- 6. RPC: claim_daily_clan_reward
--    1x per hari PER USER (bukan per clan) - jadi tiap anggota clan
--    boleh klaim jatah RC hariannya masing-masing selama masih jadi
--    anggota. Besarannya ikut Level Clan saat ini.
-- ---------------------------------------------------------------------
create or replace function public.claim_daily_clan_reward()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_clan_id uuid;
  v_last_claim date;
  v_level integer;
  v_reward integer;
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  select clan_id, last_daily_claim_date into v_clan_id, v_last_claim
  from public.clan_members where user_id = v_user;

  if v_clan_id is null then
    raise exception 'not_in_clan';
  end if;

  if v_last_claim is not null and v_last_claim = current_date then
    raise exception 'already_claimed_today';
  end if;

  select level into v_level from public.clans where id = v_clan_id;
  v_reward := public.clan_daily_reward_for_level(v_level);

  perform set_config('app.allow_admin_update', 'true', true);

  update public.profiles set rakku_coin = rakku_coin + v_reward where id = v_user;

  update public.clan_members set last_daily_claim_date = current_date
  where clan_id = v_clan_id and user_id = v_user;

  return v_reward;
end;
$$;

grant execute on function public.claim_daily_clan_reward() to authenticated;

-- ---------------------------------------------------------------------
-- 7. RPC: search_clans (dipakai juga buat Leaderboard Clan - panggil
--    dengan p_query null/kosong buat ranking semua clan)
-- ---------------------------------------------------------------------
create or replace function public.search_clans(p_query text default null, p_limit integer default 30)
returns table (
  id uuid,
  name text,
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
    c.avatar_url,
    c.level,
    c.total_donated,
    (select count(*) from public.clan_members m where m.clan_id = c.id) as member_count,
    public.clan_capacity_for_level(c.level) as capacity
  from public.clans c
  where p_query is null or trim(p_query) = '' or c.name ilike '%' || trim(p_query) || '%'
  order by c.level desc, c.total_donated desc
  limit greatest(coalesce(p_limit, 30), 1);
$$;

grant execute on function public.search_clans(text, integer) to authenticated;

-- ---------------------------------------------------------------------
-- 8. RPC: get_clan_detail & get_clan_members
--    SECURITY DEFINER supaya bisa nampilin username/avatar anggota clan
--    (termasuk anggota yang BUKAN diri sendiri) walau RLS tabel profiles
--    normalnya cuma izinin baca profil sendiri.
-- ---------------------------------------------------------------------
create or replace function public.get_clan_detail(p_clan_id uuid)
returns table (
  id uuid,
  name text,
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

create or replace function public.get_clan_members(p_clan_id uuid)
returns table (
  user_id uuid,
  username text,
  avatar_url text,
  active_border_url text,
  level integer,
  role text,
  total_donated bigint,
  joined_at timestamptz
)
language sql
security definer
set search_path = public
as $$
  select
    m.user_id,
    p.username,
    p.avatar_url,
    p.active_border_url,
    p.level,
    m.role,
    m.total_donated,
    m.joined_at
  from public.clan_members m
  join public.profiles p on p.id = m.user_id
  where m.clan_id = p_clan_id
  order by (m.role = 'leader') desc, m.total_donated desc;
$$;

grant execute on function public.get_clan_members(uuid) to authenticated;

-- =====================================================================
-- SELESAI. Setelah ini dijalankan, fitur Clan (bikin, gabung, donasi,
-- daily claim, leaderboard) langsung bisa dipakai dari app tanpa perlu
-- perubahan database lain.
-- =====================================================================
