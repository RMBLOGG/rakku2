package com.rakku.app.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String = "",
    val username: String? = null,
    val role: String? = "user",
    val level: Int? = 1,
    val exp: Int? = 0,
    val is_banned: Boolean? = false,
    val banned_reason: String? = null,
    val banned_until: String? = null,
    val has_unlimited: Boolean? = false,
    val avatar_url: String? = null,
    val rakku_coin: Int? = 0,
    // URL gambar border/frame yang lagi dipasang user di foto profilnya.
    // Didenormalisasi langsung ke kolom profiles biar gampang ditampilkan
    // (gak perlu join manual ke tabel borders tiap render avatar). Diisi
    // lewat RPC equip_border(), bukan diedit langsung dari client.
    val active_border_url: String? = null,
    val created_at: String? = null,
    // Nomor urut pendaftaran akun (1, 2, 3, ...) - diisi otomatis oleh
    // database lewat kolom identity, BUKAN oleh client. Dipakai buat
    // ditampilkan di UI sebagai pengganti UUID yang panjang/acak.
    val user_number: Long? = null,
    // Total menit nonton anime, diakumulasi server-side lewat RPC
    // increment_watch_minutes() - lihat profile_stats_migration.sql &
    // AnimeViewModel.startWatchMinutesTimer().
    val total_watch_minutes: Int? = 0
)

// Profil publik user LAIN (dilihat dari klik nama/avatar di Obrolan
// Global) beserta statistiknya. Diisi lewat RPC get_public_profile_stats()
// (SECURITY DEFINER) karena RLS tabel profiles normalnya cuma izinin baca
// profil sendiri - lihat profile_stats_migration.sql.
@JsonClass(generateAdapter = true)
data class PublicProfileStats(
    val id: String = "",
    val username: String? = null,
    val role: String? = "user",
    val level: Int? = 1,
    val avatar_url: String? = null,
    val active_border_url: String? = null,
    val user_number: Long? = null,
    val created_at: String? = null,
    val total_watch_minutes: Int? = 0,
    val total_comments: Long? = 0
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String = "",
    val email: String? = null,
    val user_metadata: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long? = null,
    val refresh_token: String? = null,
    val user: AuthUser? = null,
    // Supabase /auth/v1/signup punya 2 bentuk response beda tergantung setting
    // "Confirm email" di project:
    // - OFF (langsung dikasih sesi): {"access_token":..., "user": {...}}
    // - ON (perlu klik link email dulu, kayak Rakku): objek user-nya balik
    //   LANGSUNG di top-level, TANPA dibungkus "user" - {"id":..., "email":...}.
    // Tanpa field id/email di sini, kasus kedua bikin authRes.user selalu null
    // walau akun-nya sukses kebuat, jadi ke-detect "gagal" padahal berhasil.
    val id: String? = null,
    val email: String? = null,
    val error: String? = null,
    val error_description: String? = null,
    // Supabase Auth (GoTrue) versi terbaru balikin error pakai field ini, BUKAN
    // "error"/"error_description" di atas (itu format lama/OAuth-style). Tanpa
    // field ini, pesan error asli dari server (mis. "User already registered",
    // "Password should be at least 6 characters") gak pernah kebaca, selalu
    // jatuh ke pesan generik.
    val msg: String? = null,
    val message: String? = null,
    val error_code: String? = null
) {
    val friendlyError: String?
        get() = error_description ?: msg ?: message ?: error
}

@JsonClass(generateAdapter = true)
data class BookmarkItem(
    val id: Long? = null,
    val user_id: String = "",
    val content_type: String = "", // 'anime' or 'manga'
    val ref_id: String = "",
    val title: String = "",
    val thumb: String? = null,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class HistoryItem(
    val id: Long? = null,
    val user_id: String = "",
    val content_type: String = "", // 'anime' or 'manga'
    val ref_id: String = "",
    val title: String = "",
    val thumb: String? = null,
    val progress_id: String? = null,
    val progress_name: String? = null,
    val updated_at: String? = null
)

@JsonClass(generateAdapter = true)
data class GlobalChatMessage(
    val id: Long? = null,
    val user_id: String = "",
    val message: String = "",
    val created_at: String? = null,
    var username: String? = null,
    var avatar_url: String? = null,
    var role: String? = null,
    // Kolom ini SUDAH ADA dari awal di tabel global_chat_messages (diisi
    // otomatis pas insert, sama kayak username/role - lihat website chat.js).
    // Cuma belum pernah dibaca di app Android.
    var is_unlimited: Boolean? = false,
    // Border yang lagi dipasang si pengirim pesan ini. Kolom ini gak ada di
    // tabel global_chat_messages, diisi belakangan dari get_public_profiles
    // - lihat getGlobalChatMessages() di SupabaseRepository.
    var active_border_url: String? = null,
    // Nomor ID publik pengirim (sama kayak "ID: #x" di ProfileScreen). Juga
    // gak ada di tabel global_chat_messages, diisi dari get_public_profiles.
    var user_number: Long? = null
)

@JsonClass(generateAdapter = true)
data class TopupRequest(
    val id: Long? = null,
    val user_id: String = "",
    val amount_coin: Int = 0,
    val price: String = "",
    val status: String = "pending", // 'pending' | 'approved' | 'rejected'
    val proof_note: String? = null,
    val proof_image_url: String? = null,
    val created_at: String? = null,
    val approved_by: String? = null,
    val approved_at: String? = null,
    var username: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeComment(
    val id: String? = null,
    val anime_slug: String = "",
    val episode_slug: String = "",
    val user_id: String = "",
    val message: String = "",
    val created_at: String? = null,
    var username: String? = null,
    var avatar_url: String? = null,
    var role: String? = null
)

@JsonClass(generateAdapter = true)
data class CommentReport(
    val id: String? = null,
    val comment_id: String = "",
    val reporter_id: String = "",
    val category: String = "", // 'spam' | 'promosi' | '18+' | 'lainnya'
    val description: String? = null,
    val status: String? = "pending",
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackReport(
    // Sama kayak Announcement.id - ini UUID (teks) di database, bukan angka.
    val id: String? = null,
    val user_id: String = "",
    val type: String = "saran", // 'saran' | 'laporan'
    val message: String = "",
    val status: String? = "open", // 'open' | 'in_progress' | 'closed'
    val created_at: String? = null,
    var username: String? = null
)

// PROFILE BORDER SHOP
@JsonClass(generateAdapter = true)
data class ProfileBorder(
    val id: Long? = null,
    val name: String = "",
    val image_url: String = "",
    val price_coin: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null
)

@JsonClass(generateAdapter = true)
data class UserBorder(
    val id: Long? = null,
    val user_id: String = "",
    val border_id: Long = 0,
    val purchased_at: String? = null
)

@JsonClass(generateAdapter = true)
data class Announcement(
    // PENTING: id pengumuman di database itu tipe UUID (teks), BUKAN angka.
    // Kalau dipaksa jadi Long, parsing JSON-nya bakal error "Expected a long
    // but was <uuid>" begitu ada data pengumuman yang kebaca.
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val is_active: Boolean = true,
    val created_by: String? = null,
    val created_at: String? = null
)
