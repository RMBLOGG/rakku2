-- =====================================================================
-- USERNAME LENGTH LIMIT MIGRATION
-- Batasin panjang username maksimal 20 karakter DI SERVER (bukan cuma di
-- app) - trigger ini otomatis motong (bukan nolak) username yang
-- kepanjangan, jadi gak bakal bikin proses signup/update gagal, cuma
-- username-nya aja yang otomatis dipendekin.
--
-- Ini jaga-jaga penting: kalau cuma dibatasin di app Kotlin doang, orang
-- masih bisa kirim username panjang lewat cara lain (hit REST API
-- langsung, request Postman/curl, dsb) dan bikin tampilan berantakan
-- kayak yang kejadian sebelumnya (username super panjang bikin layar
-- profil penuh sebaris karakter berulang).
--
-- CARA PAKAI: Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run.
-- =====================================================================

create or replace function public.enforce_username_length()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_max_length constant integer := 20;
begin
  if new.username is not null and length(new.username) > v_max_length then
    new.username := substring(new.username from 1 for v_max_length);
  end if;
  return new;
end;
$$;

drop trigger if exists trg_enforce_username_length on public.profiles;

create trigger trg_enforce_username_length
before insert or update on public.profiles
for each row
execute function public.enforce_username_length();

-- ---------------------------------------------------------------------
-- Sekalian beresin data yang UDAH KELANJUR kepanjangan di database
-- (kayak kasus di screenshot) - dipotong ke 20 karakter juga. Aman
-- dijalankan berkali-kali (idempotent).
-- ---------------------------------------------------------------------
update public.profiles
set username = substring(username from 1 for 20)
where username is not null and length(username) > 20;

-- =====================================================================
-- SELESAI. Username baru maupun lama sekarang dibatasin maksimal 20
-- karakter, gak peduli lewat jalur mana masuknya.
-- =====================================================================
