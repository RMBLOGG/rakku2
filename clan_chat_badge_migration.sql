-- =====================================================================
-- CLAN CHAT BADGE MIGRATION
-- Nambahin RPC buat ambil Tag Clan beberapa user sekaligus (dipakai buat
-- nampilin badge tag clan di sebelah username di Obrolan Global).
-- Dipisah dari migration Clan lain biar bisa dijalankan belakangan.
--
-- CARA PAKAI: Supabase Dashboard -> SQL Editor -> New Query -> paste
-- semua isi file ini -> Run. Jalankan SETELAH clan_tag_migration.sql.
-- =====================================================================

create or replace function public.get_user_clan_tags(p_user_ids uuid[])
returns table (
  user_id uuid,
  tag text
)
language sql
security definer
set search_path = public
as $$
  select m.user_id, c.tag
  from public.clan_members m
  join public.clans c on c.id = m.clan_id
  where m.user_id = any(p_user_ids) and c.tag is not null;
$$;

grant execute on function public.get_user_clan_tags(uuid[]) to authenticated;

-- =====================================================================
-- SELESAI. Setelah ini dijalankan, badge tag clan otomatis muncul di
-- sebelah nama pengirim di Obrolan Global (kalau pengirimnya gabung clan
-- yang punya tag).
-- =====================================================================
