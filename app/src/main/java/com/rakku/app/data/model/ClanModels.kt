package com.rakku.app.data.model

import com.squareup.moshi.JsonClass

// Ringkasan clan, dipakai buat Leaderboard Clan & hasil pencarian clan
// (RPC search_clans - juga dipakai buat leaderboard dengan query kosong).
@JsonClass(generateAdapter = true)
data class ClanSummary(
    val id: String = "",
    val name: String = "",
    val tag: String? = null,
    val avatar_url: String? = null,
    val level: Int = 1,
    val total_donated: Long = 0,
    val member_count: Long = 0,
    val capacity: Int = 50
)

// Detail lengkap 1 clan (RPC get_clan_detail), termasuk info kapasitas,
// hadiah Daily Claim saat ini, dan syarat donasi buat naik ke level
// berikutnya (buat progress bar di UI).
@JsonClass(generateAdapter = true)
data class ClanDetail(
    val id: String = "",
    val name: String = "",
    val tag: String? = null,
    val description: String? = null,
    val avatar_url: String? = null,
    val leader_id: String? = null,
    val leader_username: String? = null,
    val level: Int = 1,
    val total_donated: Long = 0,
    val member_count: Long = 0,
    val capacity: Int = 50,
    val daily_reward: Int = 50,
    val next_level_donation: Long = 0,
    val created_at: String? = null
)

// Anggota clan (RPC get_clan_members) - dipakai buat daftar anggota di
// layar detail clan.
@JsonClass(generateAdapter = true)
data class ClanMemberInfo(
    val user_id: String = "",
    val username: String? = null,
    val avatar_url: String? = null,
    val active_border_url: String? = null,
    val level: Int? = 1,
    val role: String = "member",
    val total_donated: Long = 0,
    val joined_at: String? = null
)

// Baris keanggotaan clan milik user yang lagi login sendiri, diambil
// langsung dari tabel clan_members (RLS-nya select-all, jadi baca baris
// sendiri aman tanpa perlu RPC). Dipakai buat tau "aku lagi di clan mana"
// + status Daily Claim hari ini.
@JsonClass(generateAdapter = true)
data class MyClanMembership(
    val clan_id: String = "",
    val user_id: String = "",
    val role: String = "member",
    val total_donated: Long = 0,
    val last_daily_claim_date: String? = null,
    val joined_at: String? = null
)
