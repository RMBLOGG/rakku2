-- =====================================================================
-- BORDERS MIGRATION
-- Fitur: Toko Border (frame foto profil) yang dibeli pakai Rakku Coin,
-- dipasang di foto profil, dan diupload manual oleh admin lewat panel admin.
--
-- CATATAN PENTING: FILE GAMBAR border di-hosting di Cloudinary, BUKAN di
-- Supabase Storage. Jadi kamu TIDAK PERLU bikin storage bucket apapun di
-- Supabase buat fitur ini. Yang disimpan di Supabase cuma data border-nya
-- (nama, harga, status aktif, dan siapa saja yang sudah beli) - itu sebabnya
-- tetap butuh tabel & RLS seperti di bawah.
--
-- CARA PAKAI:
-- 1. Buka Supabase Dashboard -> SQL Editor -> New Query.
-- 2. Copy-paste seluruh isi file ini, lalu klik "Run".
-- 3. Bikin akun Cloudinary (gratis) di cloudinary.com kalau belum punya, lalu:
--    a. Catat "Cloud Name" dari Dashboard (pojok kiri atas).
--    b. Settings -> Upload -> Upload presets -> Add upload preset ->
--       Signing Mode: pilih "Unsigned" -> Save -> catat nama presetnya.
--    c. Isi CLOUDINARY_CLOUD_NAME & CLOUDINARY_UPLOAD_PRESET di
--       SupabaseRepository.kt (companion object di bagian paling atas file)
--       dengan nilai dari langkah a & b.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. KOLOM BARU DI TABEL PROFILES
--    Menyimpan URL gambar border yang lagi dipasang user (didenormalisasi
--    biar gampang ditampilkan tanpa join tiap render avatar).
-- ---------------------------------------------------------------------
alter table public.profiles
  add column if not exists active_border_url text;

-- ---------------------------------------------------------------------
-- 2. TABEL BORDERS
--    Katalog border yang dijual di toko. Diisi manual oleh admin lewat
--    panel admin (upload gambar + nama + harga).
-- ---------------------------------------------------------------------
create table if not exists public.borders (
  id bigint generated always as identity primary key,
  name text not null,
  image_url text not null,
  price_coin integer not null default 0 check (price_coin >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

alter table public.borders enable row level security;

-- Semua user yang login boleh lihat daftar border (buat ditampilkan di toko
-- & panel admin). Insert/update/delete cuma lewat RPC admin_* di bawah.
drop policy if exists "borders_select_all" on public.borders;
create policy "borders_select_all"
  on public.borders for select
  to authenticated
  using (true);

-- ---------------------------------------------------------------------
-- 3. TABEL USER_BORDERS
--    Catatan kepemilikan: border apa saja yang sudah dibeli tiap user.
-- ---------------------------------------------------------------------
create table if not exists public.user_borders (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  border_id bigint not null references public.borders(id) on delete cascade,
  purchased_at timestamptz not null default now(),
  unique (user_id, border_id)
);

alter table public.user_borders enable row level security;

-- User cuma boleh lihat border miliknya sendiri. Insert/delete cuma lewat
-- RPC buy_border() (SECURITY DEFINER) supaya saldo koin gak bisa dicurangi
-- dari client.
drop policy if exists "user_borders_select_own" on public.user_borders;
create policy "user_borders_select_own"
  on public.user_borders for select
  to authenticated
  using (auth.uid() = user_id);

-- ---------------------------------------------------------------------
-- 4. RPC: buy_border(p_border_id)
--    Beli border pakai Rakku Coin. Mengecek saldo, status aktif, dan
--    kepemilikan di sisi server (SECURITY DEFINER) supaya gak bisa
--    dimanipulasi dari client.
-- ---------------------------------------------------------------------
create or replace function public.buy_border(p_border_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_price integer;
  v_active boolean;
  v_balance integer;
  v_already_owned boolean;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select price_coin, is_active into v_price, v_active
  from public.borders
  where id = p_border_id;

  if not found then
    raise exception 'border_not_found';
  end if;

  if not v_active then
    raise exception 'border_not_found';
  end if;

  select exists(
    select 1 from public.user_borders
    where user_id = v_user_id and border_id = p_border_id
  ) into v_already_owned;

  if v_already_owned then
    raise exception 'already_owned';
  end if;

  select rakku_coin into v_balance
  from public.profiles
  where id = v_user_id
  for update;

  if v_balance is null or v_balance < v_price then
    raise exception 'insufficient_coin';
  end if;

  -- PENTING: tabel profiles punya trigger proteksi (trg_protect_profile_columns)
  -- yang otomatis membatalkan perubahan ke kolom sensitif (termasuk rakku_coin)
  -- kecuali session variable ini di-set 'true' dulu. Tanpa baris ini, UPDATE di
  -- bawah akan "berhasil" (gak error) tapi nilainya otomatis dibalikin lagi ke
  -- nilai lama oleh trigger - itu sebabnya koin sempat gak kepotong walau
  -- pembelian border sendiri tercatat. set_config dengan is_local=true di sini
  -- artinya cuma berlaku buat durasi transaksi function ini, otomatis kereset
  -- setelahnya - jadi gak membuka celah keamanan yang bocor ke query lain.
  perform set_config('app.allow_admin_update', 'true', true);

  update public.profiles
  set rakku_coin = rakku_coin - v_price
  where id = v_user_id;

  insert into public.user_borders (user_id, border_id)
  values (v_user_id, p_border_id);

  return true;
end;
$$;

grant execute on function public.buy_border(bigint) to authenticated;

-- ---------------------------------------------------------------------
-- 5. RPC: equip_border(p_border_id)
--    Pasang border (kirim id-nya) atau lepas border (kirim NULL) di foto
--    profil. Memvalidasi kepemilikan sebelum ngeset active_border_url.
-- ---------------------------------------------------------------------
create or replace function public.equip_border(p_border_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_image_url text;
  v_owned boolean;
begin
  if v_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if p_border_id is null then
    update public.profiles set active_border_url = null where id = v_user_id;
    return true;
  end if;

  select exists(
    select 1 from public.user_borders
    where user_id = v_user_id and border_id = p_border_id
  ) into v_owned;

  if not v_owned then
    raise exception 'not_owned';
  end if;

  select image_url into v_image_url from public.borders where id = p_border_id;

  if v_image_url is null then
    raise exception 'border_not_found';
  end if;

  update public.profiles set active_border_url = v_image_url where id = v_user_id;
  return true;
end;
$$;

grant execute on function public.equip_border(bigint) to authenticated;

-- ---------------------------------------------------------------------
-- 6. RPC ADMIN: admin_create_border / admin_set_border_active / admin_delete_border
--    Sama pola pengecekan role admin/moderator seperti admin_ban_user,
--    admin_add_coin, dkk yang sudah ada.
-- ---------------------------------------------------------------------
create or replace function public.admin_create_border(p_name text, p_image_url text, p_price_coin integer)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
  v_id bigint;
begin
  select role into v_role from public.profiles where id = auth.uid();
  if v_role is null or v_role not in ('admin', 'moderator') then
    raise exception 'not_authorized';
  end if;

  insert into public.borders (name, image_url, price_coin, is_active)
  values (p_name, p_image_url, greatest(p_price_coin, 0), true)
  returning id into v_id;

  return v_id;
end;
$$;

grant execute on function public.admin_create_border(text, text, integer) to authenticated;

create or replace function public.admin_set_border_active(p_border_id bigint, p_active boolean)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  select role into v_role from public.profiles where id = auth.uid();
  if v_role is null or v_role not in ('admin', 'moderator') then
    raise exception 'not_authorized';
  end if;

  update public.borders set is_active = p_active where id = p_border_id;
  return true;
end;
$$;

grant execute on function public.admin_set_border_active(bigint, boolean) to authenticated;

create or replace function public.admin_delete_border(p_border_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
  v_owned_count integer;
begin
  select role into v_role from public.profiles where id = auth.uid();
  if v_role is null or v_role not in ('admin', 'moderator') then
    raise exception 'not_authorized';
  end if;

  -- Border yang sudah dibeli minimal 1 user TIDAK BOLEH dihapus permanen -
  -- itu barang yang udah dibayar pakai Rakku Coin. Kalau dihapus, baris
  -- kepemilikannya bakal ikut kehapus (foreign key cascade) dan user yang
  -- udah bayar jadi kehilangan border yang mereka beli. Solusinya: admin
  -- nonaktifkan aja (admin_set_border_active) supaya berhenti dijual ke
  -- user baru, tapi user lama yang udah beli tetap bisa pakai.
  select count(*) into v_owned_count from public.user_borders where border_id = p_border_id;
  if v_owned_count > 0 then
    raise exception 'border_has_owners';
  end if;

  delete from public.borders where id = p_border_id;
  return true;
end;
$$;

grant execute on function public.admin_delete_border(bigint) to authenticated;

-- =====================================================================
-- SELESAI. Gambar border akan otomatis ke-hosting di Cloudinary begitu
-- admin upload lewat panel admin di app (asal CLOUDINARY_CLOUD_NAME &
-- CLOUDINARY_UPLOAD_PRESET sudah diisi di SupabaseRepository.kt).
-- Tidak ada storage bucket Supabase yang perlu dibuat untuk fitur ini.
-- =====================================================================
