-- =====================================================================
-- PROFILE STATS MIGRATION
-- Fitur: Info tambahan di halaman Profil + lihat profil user lain dari
-- Obrolan Global.
--
-- Yang ditambahkan:
-- 1. Jumlah hari sejak akun dibuat       -> dihitung di client dari kolom
--    profiles.created_at yang SUDAH ADA, jadi TIDAK butuh migrasi apapun.
-- 2. Total komentar                      -> dihitung dari tabel
--    episode_comments yang SUDAH ADA (komentar episode memang publik/
--    terbaca semua orang), jadi TIDAK butuh migrasi tabel, cuma query
--    count biasa dari client.
-- 3. Total menit menonton                -> kolom BARU di profiles,
--    diisi lewat RPC increment_watch_minutes() yang dipanggil app tiap
--    1 menit selagi user nonton anime (lihat startWatchMinutesTimer di
--    AnimeViewModel.kt).
-- 4. Lihat profil user lain dari Obrolan Global (klik nama/avatar) ->
--    RPC get_public_profile_stats() & get_public_user_history() baru,
--    keduanya SECURITY DEFINER supaya bisa baca data profil & riwayat
--    tontonan user LAIN walau RLS tabel profiles/history normalnya cuma
--    izinin baca punya sendiri.
--
-- CARA PAKAI: Buka Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. KOLOM BARU: total_watch_minutes
-- ---------------------------------------------------------------------
alter table public.profiles
  add column if not exists total_watch_minutes integer not null default 0;

-- ---------------------------------------------------------------------
-- 2. RPC: increment_watch_minutes(p_minutes)
--    Dipanggil app tiap 1 menit selagi layar player anime kebuka (lihat
--    AnimeViewModel.startWatchMinutesTimer). Nambahin ke akun user yang
--    lagi login sendiri (auth.uid()), gak bisa dipakai buat nambahin
--    punya user lain.
-- ---------------------------------------------------------------------
create or replace function public.increment_watch_minutes(p_minutes integer default 1)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_minutes is null or p_minutes <= 0 then
    return false;
  end if;

  -- Sama seperti buy_border/equip_border: tabel profiles punya trigger
  -- proteksi (trg_protect_profile_columns) yang otomatis membatalkan
  -- perubahan ke kolom sensitif kecuali session variable ini di-set
  -- 'true' dulu. is_local=true artinya cuma berlaku buat durasi transaksi
  -- function ini.
  perform set_config('app.allow_admin_update', 'true', true);

  update public.profiles
  set total_watch_minutes = total_watch_minutes + p_minutes
  where id = v_user_id;

  return true;
end;
$$;

grant execute on function public.increment_watch_minutes(integer) to authenticated;

-- ---------------------------------------------------------------------
-- 3. RPC: get_public_profile_stats(p_user_id)
--    Dipanggil pas user klik nama/avatar orang lain di Obrolan Global.
--    Balikin data profil publik (id, username, avatar, role, level, ID
--    urut, border aktif, tanggal daftar, total menit nonton) DITAMBAH
--    total komentar user itu - dihitung langsung di query ini supaya
--    cuma 1 kali panggilan API.
--    SECURITY DEFINER supaya bisa baca profil user LAIN walau RLS tabel
--    profiles normalnya cuma izinin baca profil sendiri.
-- ---------------------------------------------------------------------
create or replace function public.get_public_profile_stats(p_user_id uuid)
returns table (
  id uuid,
  username text,
  role text,
  level integer,
  avatar_url text,
  active_border_url text,
  user_number bigint,
  created_at timestamptz,
  total_watch_minutes integer,
  total_comments bigint
)
language sql
security definer
set search_path = public
as $$
  select
    p.id,
    p.username,
    p.role,
    p.level,
    p.avatar_url,
    p.active_border_url,
    p.user_number,
    p.created_at,
    p.total_watch_minutes,
    (select count(*) from public.episode_comments c where c.user_id = p.id) as total_comments
  from public.profiles p
  where p.id = p_user_id;
$$;

grant execute on function public.get_public_profile_stats(uuid) to authenticated;

-- ---------------------------------------------------------------------
-- 4. RPC: get_public_user_history(p_user_id, p_limit)
--    Riwayat tontonan/bacaan (anime & manga) milik user LAIN, buat
--    ditampilkan di halaman profil publik yang dibuka dari Obrolan
--    Global. Dibatasi p_limit item terbaru (default 30) biar responnya
--    gak kebesaran.
-- ---------------------------------------------------------------------
create or replace function public.get_public_user_history(p_user_id uuid, p_limit integer default 30)
returns table (
  id bigint,
  user_id uuid,
  content_type text,
  ref_id text,
  title text,
  thumb text,
  progress_id text,
  progress_name text,
  updated_at timestamptz
)
language sql
security definer
set search_path = public
as $$
  select
    h.id,
    h.user_id,
    h.content_type,
    h.ref_id,
    h.title,
    h.thumb,
    h.progress_id,
    h.progress_name,
    h.updated_at
  from public.history h
  where h.user_id = p_user_id
  order by h.updated_at desc
  limit greatest(coalesce(p_limit, 30), 1);
$$;

grant execute on function public.get_public_user_history(uuid, integer) to authenticated;

-- =====================================================================
-- SELESAI. Setelah ini dijalankan, fitur baru di Profil (hari bergabung,
-- total komentar, total menit nonton) dan "Lihat Profil" dari Obrolan
-- Global langsung bisa jalan tanpa perlu perubahan lain di database.
-- =====================================================================
