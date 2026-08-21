-- =====================================================================
-- CHAT COOLDOWN MIGRATION
-- Fitur: cooldown antar pesan di Obrolan Global, DITEGAKKAN DI SERVER
-- (trigger di tabel), bukan cuma di app - jadi gak bisa dilewatin
-- biarpun orangnya kirim pesan langsung ke REST API Supabase (skip app
-- Kotlin & web sepenuhnya).
--
-- CARA PAKAI: Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run.
-- =====================================================================

-- Trigger function: dicek SETIAP KALI ada INSERT baru ke
-- global_chat_messages. Kalau pesan TERAKHIR dari user yang sama masih
-- kurang dari COOLDOWN_SECONDS detik yang lalu, insert-nya ditolak
-- (exception) - trigger jalan SEBELUM baris baru masuk, jadi otomatis
-- berlaku buat SEMUA jalur insert (REST langsung dari app Kotlin, dari
-- web pakai supabase-js, atau siapapun yang hit API-nya langsung).
create or replace function public.enforce_chat_cooldown()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_last timestamptz;
  -- Ubah angka ini kalau mau cooldown-nya lebih lama/singkat.
  v_cooldown_seconds constant integer := 3;
begin
  select created_at into v_last
  from public.global_chat_messages
  where user_id = new.user_id
  order by created_at desc
  limit 1;

  if v_last is not null and (now() - v_last) < make_interval(secs => v_cooldown_seconds) then
    raise exception 'chat_cooldown_active'
      using detail = extract(epoch from (make_interval(secs => v_cooldown_seconds) - (now() - v_last)))::text;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_enforce_chat_cooldown on public.global_chat_messages;

create trigger trg_enforce_chat_cooldown
before insert on public.global_chat_messages
for each row
execute function public.enforce_chat_cooldown();

-- =====================================================================
-- SELESAI. Setelah ini dijalankan, siapapun yang kirim pesan Obrolan
-- Global kurang dari 3 detik sejak pesan terakhirnya bakal ditolak
-- server dengan error 'chat_cooldown_active' - berlaku di app Kotlin,
-- web, maupun panggilan API langsung.
-- =====================================================================
