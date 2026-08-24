package com.rakku.app.data.remote

import android.content.Context
import android.net.Uri
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.Announcement
import com.rakku.app.data.model.AuthResponse
import com.rakku.app.data.model.BookmarkItem
import com.rakku.app.data.model.ClanDetail
import com.rakku.app.data.model.ClanMemberInfo
import com.rakku.app.data.model.ClanSummary
import com.rakku.app.data.model.CommentReport
import com.rakku.app.data.model.EpisodeComment
import com.rakku.app.data.model.FeedbackReport
import com.rakku.app.data.model.GlobalChatMessage
import com.rakku.app.data.model.HistoryItem
import com.rakku.app.data.model.MyClanMembership
import com.rakku.app.data.model.ProfileBorder
import com.rakku.app.data.model.PublicProfileStats
import com.rakku.app.data.model.TopupRequest
import com.rakku.app.data.model.UserBorder
import com.rakku.app.data.model.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream

class SupabaseRepository(
    private val sessionManager: SessionManager
) {
    companion object {
        const val SUPABASE_URL = "https://lqixsabpmyflguisblrb.supabase.co"
        const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxxaXhzYWJwbXlmbGd1aXNibHJiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMyNjI4NDcsImV4cCI6MjA5ODgzODg0N30.QVdxWkMguIbJ0T5uqomBKwN7PBAYeb_xNjRfh67W1-E"

        // ================= CLOUDINARY (upload gambar border) =================
        // GANTI dua nilai di bawah ini dengan punya kamu sendiri:
        // 1. CLOUD_NAME: buka Cloudinary Dashboard -> tercantum di pojok kiri atas
        //    (contoh: "dxyz1234a").
        // 2. UPLOAD_PRESET: Cloudinary Dashboard -> Settings (ikon gerigi) -> tab
        //    "Upload" -> scroll ke "Upload presets" -> "Add upload preset" ->
        //    Signing Mode WAJIB diset ke "Unsigned" -> Save -> copy nama presetnya.
        //    WAJIB unsigned karena upload dilakukan langsung dari app Android,
        //    dan API Secret Cloudinary TIDAK BOLEH ditaruh di client (bisa dicuri
        //    dari APK). Unsigned preset aman dipakai di client karena Cloudinary
        //    sendiri yang membatasi apa yang boleh di-upload lewatnya (bisa diatur
        //    folder tujuan, ukuran max, format yang diizinkan, dsb di halaman
        //    setting preset itu).
        const val CLOUDINARY_CLOUD_NAME = "qbtrsrkv"
        const val CLOUDINARY_UPLOAD_PRESET = "rakku-border"
    }

    private val client = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getAuthToken(): String {
        return sessionManager.getAccessToken() ?: SUPABASE_ANON_KEY
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val token = getAuthToken()
        return Request.Builder()
            .url(url)
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Prefer", "return=representation")
    }

    // EXP: manggil RPC "award_exp_once" (sudah ada di database, sama seperti yang
    // dipakai website di anime.js). eventKey harus unik per kejadian (mis.
    // "anime_open:{slug}:{episodeSlug}") supaya EXP gak dobel kalau dipanggil
    // berkali-kali untuk kejadian yang sama - RPC ini sendiri yang jaga idempotensi
    // di sisi server, return true kalau baru pertama kali (EXP ditambahkan),
    // false kalau sudah pernah (EXP tidak ditambahkan lagi).
    suspend fun awardExp(eventKey: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val bodyJson = moshi.adapter(Map::class.java).toJson(
                mapOf("p_event_key" to eventKey, "p_amount" to amount)
            )
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/award_exp_once")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.trim() == "true"
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // STATS: nambahin akumulasi total menit nonton anime punya user yang lagi
    // login (RPC increment_watch_minutes, SECURITY DEFINER) - dipanggil tiap
    // 1 menit selagi layar player anime kebuka, lihat
    // AnimeViewModel.startWatchMinutesTimer(). Dipisah dari awardExp/EXP timer
    // karena statistik ini TIDAK dibatasi cap 10 menit/sesi kayak EXP.
    suspend fun incrementWatchMinutes(minutes: Int = 1): Boolean = withContext(Dispatchers.IO) {
        try {
            val bodyJson = moshi.adapter(Map::class.java).toJson(mapOf("p_minutes" to minutes))
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/increment_watch_minutes")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // AUTH
    suspend fun signUp(email: String, password: String, username: String): AuthResponse = withContext(Dispatchers.IO) {
        val bodyJson = moshi.adapter(Map::class.java).toJson(
            mapOf(
                "email" to email,
                "password" to password,
                "data" to mapOf("username" to username)
            )
        )
        val request = Request.Builder()
            // redirect_to = deep link app, biar pas user klik link konfirmasi di
            // email, dia balik ke app Rakku langsung (bukan ke website). URL ini
            // WAJIB juga didaftarin di Supabase Dashboard -> Authentication ->
            // URL Configuration -> Redirect URLs, kalau enggak, Supabase bakal
            // nolak/abaikan redirect_to ini dan tetap fallback ke Site URL.
            .url("$SUPABASE_URL/auth/v1/signup?redirect_to=rakku://login-callback")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        val authRes = moshi.adapter(AuthResponse::class.java).fromJson(responseBody) ?: AuthResponse(error = "Unknown error")
        
        if (response.isSuccessful && authRes.access_token != null && authRes.user != null) {
            sessionManager.saveSession(authRes.access_token, authRes.user.id)
            // ensure profile entry exists if needed
        }
        authRes
    }

    suspend fun signIn(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val bodyJson = moshi.adapter(Map::class.java).toJson(
            mapOf("email" to email, "password" to password)
        )
        val request = Request.Builder()
            .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        val authRes = moshi.adapter(AuthResponse::class.java).fromJson(responseBody) ?: AuthResponse(error = "Gagal login")

        if (response.isSuccessful && authRes.access_token != null && authRes.user != null) {
            sessionManager.saveSession(authRes.access_token, authRes.user.id)
        }
        authRes
    }

    suspend fun fetchUserProfile(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, UserProfile::class.java)
            val list = moshi.adapter<List<UserProfile>>(type).fromJson(responseBody)
            val profile = list?.firstOrNull()
            sessionManager.updateProfile(profile)
            profile
        } else null
    }

    suspend fun updateUserProfile(userId: String, username: String?, avatarUrl: String?): Boolean = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Any>()
        if (username != null) map["username"] = username
        if (avatarUrl != null) map["avatar_url"] = avatarUrl

        val bodyJson = moshi.adapter(Map::class.java).toJson(map)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/profiles?id=eq.$userId")
            .patch(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        response.isSuccessful
    }

    // BAN LOGIC RPC
    suspend fun clearExpiredBan(userId: String): Boolean = withContext(Dispatchers.IO) {
        val bodyJson = moshi.adapter(Map::class.java).toJson(mapOf("target_id" to userId))
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/clear_expired_ban")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        response.isSuccessful
    }

    // UPLOAD AVATAR TO BUCKET 'avatars'
    suspend fun uploadAvatar(context: Context, userId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            val path = "avatars/$userId/$fileName"
            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$path"

            val imageMediaType = "image/jpeg".toMediaType()
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody(imageMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                "$SUPABASE_URL/storage/v1/object/public/$path"
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ================= PROFILE BORDER SHOP =================

    sealed class BuyBorderResult {
        object Success : BuyBorderResult()
        object InsufficientCoin : BuyBorderResult()
        object AlreadyOwned : BuyBorderResult()
        object NotFound : BuyBorderResult()
        data class Error(val message: String) : BuyBorderResult()
    }

    // Daftar border yang aktif dijual, buat ditampilkan di Toko Border.
    suspend fun getActiveBorders(): List<ProfileBorder> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/borders?is_active=eq.true&order=price_coin.asc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ProfileBorder::class.java)
            moshi.adapter<List<ProfileBorder>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    // Border-border yang sudah dibeli/dimiliki user yang lagi login.
    suspend fun getMyBorderIds(): List<Long> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext emptyList()
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/user_borders?user_id=eq.$userId&select=id,user_id,border_id,purchased_at")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, UserBorder::class.java)
            val list = moshi.adapter<List<UserBorder>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            list.map { it.border_id }
        } else emptyList()
    }

    // Beli border pakai Rakku Coin. Logic pengecekan saldo & kepemilikan
    // dilakukan di RPC "buy_border" (SECURITY DEFINER) di sisi database supaya
    // gak bisa dicurangi dari client (mis. beli walau koin kurang). Lihat
    // borders_migration.sql.
    suspend fun buyBorder(borderId: Long): BuyBorderResult = withContext(Dispatchers.IO) {
        val map = mapOf("p_border_id" to borderId)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/buy_border")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (response.isSuccessful) {
            sessionManager.getUserId()?.let { fetchUserProfile(it) }
            BuyBorderResult.Success
        } else {
            android.util.Log.e("RakkuBorder", "buyBorder gagal (HTTP ${response.code}): $bodyStr")
            when {
                bodyStr.contains("insufficient_coin") -> BuyBorderResult.InsufficientCoin
                bodyStr.contains("already_owned") -> BuyBorderResult.AlreadyOwned
                bodyStr.contains("border_not_found") -> BuyBorderResult.NotFound
                else -> BuyBorderResult.Error(bodyStr)
            }
        }
    }

    // Pasang border (kirim borderId) atau lepas border (kirim null) di foto
    // profil. RPC "equip_border" memvalidasi kepemilikan sebelum ngeset
    // profiles.active_border_url.
    //
    // PENTING: pakai .serializeNulls() di sini. Moshi secara default MEMBUANG
    // field yang nilainya null dari JSON output (mis. {"p_border_id":null}
    // akan jadi cuma "{}"). Kalau ini kepasang buat kirim border_id=null
    // (proses "lepas" border), badan request yang nyampe ke server jadi
    // kosong -> parameter wajib "p_border_id" gak kekirim -> RPC error ->
    // proses lepas border gagal diam-diam. serializeNulls() memastikan
    // {"p_border_id":null} beneran terkirim apa adanya.
    suspend fun equipBorder(borderId: Long?): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("p_border_id" to borderId)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/equip_border")
            .post(moshi.adapter(Map::class.java).serializeNulls().toJson(map).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            sessionManager.getUserId()?.let { fetchUserProfile(it) }
            true
        } else {
            response.body?.string()?.let { android.util.Log.e("RakkuBorder", "equipBorder gagal: $it") }
            false
        }
    }

    // ADMIN: kelola border (upload gambar manual dari panel admin)
    suspend fun getAllBordersAdmin(): List<ProfileBorder> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/borders?order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ProfileBorder::class.java)
            moshi.adapter<List<ProfileBorder>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    // Upload file gambar border ke Cloudinary (bukan Supabase Storage) lewat
    // unsigned upload preset, jadi gambar border gak ikut numpuk kuota storage
    // Supabase. Data border (nama/harga/status) tetap disimpan di tabel
    // "borders" Supabase seperti biasa - cuma FILE gambarnya yang dipindah
    // hosting-nya ke Cloudinary CDN.
    suspend fun uploadBorderImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            if (CLOUDINARY_CLOUD_NAME.startsWith("GANTI_") || CLOUDINARY_UPLOAD_PRESET.startsWith("GANTI_")) {
                // Konstanta belum diisi - lihat komentar di companion object di atas.
                return@withContext null
            }

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            val fileName = "border_${System.currentTimeMillis()}.png"
            val imageMediaType = "image/*".toMediaType()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, bytes.toRequestBody(imageMediaType))
                .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                .addFormDataPart("folder", "rakku_borders")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                if (json.has("secure_url")) json.getString("secure_url") else null
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun adminCreateBorder(name: String, imageUrl: String, priceCoin: Int): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("p_name" to name, "p_image_url" to imageUrl, "p_price_coin" to priceCoin)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_create_border")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminSetBorderActive(borderId: Long, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("p_border_id" to borderId, "p_active" to active)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_set_border_active")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    // Return: null kalau berhasil dihapus, atau pesan error kalau gagal
    // (mis. "border_has_owners" karena udah ada user yang beli - lihat
    // admin_delete_border di borders_migration.sql).
    suspend fun adminDeleteBorder(borderId: Long): String? = withContext(Dispatchers.IO) {
        val map = mapOf("p_border_id" to borderId)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_delete_border")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            null
        } else {
            val bodyStr = response.body?.string() ?: ""
            if (bodyStr.contains("border_has_owners")) "border_has_owners" else "error"
        }
    }

    // ANNOUNCEMENTS
    suspend fun getActiveAnnouncements(): List<Announcement> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/announcements?is_active=eq.true&order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Announcement::class.java)
            moshi.adapter<List<Announcement>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    suspend fun getAllAnnouncements(): List<Announcement> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/announcements?order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Announcement::class.java)
            moshi.adapter<List<Announcement>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    suspend fun createAnnouncement(title: String, content: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf(
            "title" to title,
            "content" to content,
            "is_active" to active,
            "created_by" to sessionManager.getUserId()
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/announcements")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun toggleAnnouncement(id: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("is_active" to active)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/announcements?id=eq.$id")
            .patch(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun deleteAnnouncement(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/announcements?id=eq.$id")
            .delete()
            .build()
        client.newCall(request).execute().isSuccessful
    }

    // BOOKMARKS & HISTORY
    suspend fun getBookmarks(userId: String): List<BookmarkItem> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/bookmarks?user_id=eq.$userId&order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, BookmarkItem::class.java)
            moshi.adapter<List<BookmarkItem>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    suspend fun addBookmark(userId: String, contentType: String, refId: String, title: String, thumb: String?): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf(
            "user_id" to userId,
            "content_type" to contentType,
            "ref_id" to refId,
            "title" to title,
            "thumb" to thumb
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/bookmarks")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun removeBookmark(id: Long): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/bookmarks?id=eq.$id")
            .delete().build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun clearAllBookmarks(userId: String): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/bookmarks?user_id=eq.$userId")
            .delete().build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun getWatchHistory(userId: String): List<HistoryItem> = withContext(Dispatchers.IO) {
        // Gak difilter content_type lagi - riwayat anime & manga digabung jadi satu
        // daftar "Riwayat Tontonan", sama kayak Bookmark yang juga nyampur dua-duanya.
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/history?user_id=eq.$userId&order=updated_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, HistoryItem::class.java)
            moshi.adapter<List<HistoryItem>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    suspend fun deleteHistoryItem(id: Long): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/history?id=eq.$id")
            .delete().build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun clearAllHistory(userId: String, contentType: String): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/history?user_id=eq.$userId&content_type=eq.$contentType")
            .delete().build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun saveWatchHistory(userId: String, refId: String, title: String, thumb: String?, progressId: String, progressName: String): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf(
            "user_id" to userId,
            "content_type" to "anime",
            "ref_id" to refId,
            "title" to title,
            "thumb" to thumb,
            "progress_id" to progressId,
            "progress_name" to progressName
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/history")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    // Sama persis kayak saveWatchHistory di atas, cuma buat manga. Dipisah jadi
    // function sendiri (bukan nambahin parameter content_type) biar konsisten
    // sama pola nama fungsi anime yang udah ada, dan biar pemanggilnya jelas.
    suspend fun saveMangaHistory(userId: String, refId: String, title: String, thumb: String?, progressId: String, progressName: String): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf(
            "user_id" to userId,
            "content_type" to "manga",
            "ref_id" to refId,
            "title" to title,
            "thumb" to thumb,
            "progress_id" to progressId,
            "progress_name" to progressName
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/history")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    // EPISODE COMMENTS & COMMENT REPORTS
    suspend fun getEpisodeComments(animeSlug: String, episodeSlug: String): List<EpisodeComment> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/episode_comments?anime_slug=eq.$animeSlug&episode_slug=eq.$episodeSlug&order=created_at.asc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, EpisodeComment::class.java)
            val comments = moshi.adapter<List<EpisodeComment>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            // Attach user profiles
            attachProfilesToComments(comments)
        } else emptyList()
    }

    suspend fun getUserComments(userId: String): List<EpisodeComment> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/episode_comments?user_id=eq.$userId&order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, EpisodeComment::class.java)
            moshi.adapter<List<EpisodeComment>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    private suspend fun attachProfilesToComments(comments: List<EpisodeComment>): List<EpisodeComment> {
        val userIds = comments.map { it.user_id }.distinct()
        if (userIds.isEmpty()) return comments
        val profilesMap = fetchProfilesMap(userIds)
        comments.forEach { c ->
            val p = profilesMap[c.user_id]
            c.username = p?.username ?: "User"
            c.avatar_url = p?.avatar_url
            c.role = p?.role ?: "user"
        }
        return comments
    }

    suspend fun postEpisodeComment(animeSlug: String, episodeSlug: String, message: String): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        val map = mapOf(
            "anime_slug" to animeSlug,
            "episode_slug" to episodeSlug,
            "user_id" to userId,
            "message" to message
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/episode_comments")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun deleteEpisodeComment(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/episode_comments?id=eq.$id")
            .delete().build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun reportComment(commentId: String, category: String, description: String?): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        val map = mapOf(
            "comment_id" to commentId,
            "reporter_id" to userId,
            "category" to category,
            "description" to description,
            "status" to "pending"
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/comment_reports")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun getCommentReports(): List<CommentReport> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/comment_reports?order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, CommentReport::class.java)
            moshi.adapter<List<CommentReport>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    // GLOBAL CHAT
    suspend fun getGlobalChatMessages(): List<GlobalChatMessage> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/global_chat_messages?order=created_at.desc&limit=50")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, GlobalChatMessage::class.java)
            val list = moshi.adapter<List<GlobalChatMessage>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            // PENTING: tabel global_chat_messages SUDAH punya kolom username/role
            // sendiri (diisi otomatis oleh trigger DB pas insert, sama seperti di website -
            // lihat chat.js yang cuma .select("*") tanpa join). Jangan ditimpa lagi dengan
            // hasil lookup manual ke tabel profiles, karena kalau lookup itu gagal/kosong
            // (mis. RLS profiles membatasi baca profil user lain), semua username malah
            // ke-reset jadi fallback "User" walau data aslinya sudah benar dari response ini.
            val reversed = list.reversed().onEach { msg ->
                if (msg.username.isNullOrBlank()) msg.username = "User"
            }
            // avatar_url TIDAK didenormalisasi di tabel ini (website chat.js sendiri
            // gak pernah pakai/nampilin avatar di chat), jadi kolomnya nyaris pasti
            // selalu kosong. Supaya foto profil tetap muncul di chat, ambil live dari
            // get_public_profiles RPC (yang sama dipakai buat komentar episode) buat
            // pengirim yang avatar_url-nya belum keisi dari response awal.
            //
            // active_border_url juga diambil dari sini buat SEMUA pengirim (bukan cuma
            // yang avatar-nya kosong), karena kolom itu emang gak pernah ada sama sekali
            // di tabel global_chat_messages - satu-satunya sumbernya ya get_public_profiles.
            val allUserIds = reversed.map { it.user_id }.distinct()
            if (allUserIds.isNotEmpty()) {
                val profiles = fetchProfilesMap(allUserIds)
                reversed.forEach { msg ->
                    if (msg.avatar_url.isNullOrBlank()) {
                        msg.avatar_url = profiles[msg.user_id]?.avatar_url
                    }
                    msg.active_border_url = profiles[msg.user_id]?.active_border_url
                    msg.user_number = profiles[msg.user_id]?.user_number
                }
            }
            reversed
        } else emptyList()
    }

    sealed class SendChatResult {
        object Success : SendChatResult()
        // remainingSeconds null kalau server gak ngasih detail sisa waktunya
        // (tetap ditolak, cuma buat fallback UI kalau parsing gagal).
        data class Cooldown(val remainingSeconds: Double?) : SendChatResult()
        data class Error(val message: String) : SendChatResult()
    }

    // Kirim pesan Obrolan Global. Validasi cooldown SEBENARNYA ditegakkan di
    // server lewat trigger trg_enforce_chat_cooldown (lihat
    // chat_cooldown_migration.sql) - jadi walau ada bug/dilewatin di sisi app,
    // server tetap nolak. Return value di sini cuma buat nampilin pesan yang
    // pas ke user (termasuk sisa detik cooldown-nya kalau server kasih tau).
    suspend fun sendGlobalChatMessage(message: String): SendChatResult = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext SendChatResult.Error("Kamu belum login")
        try {
            val map = mapOf(
                "user_id" to userId,
                "message" to message
            )
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/global_chat_messages")
                .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                SendChatResult.Success
            } else {
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.contains("chat_cooldown_active")) {
                    val remaining = try {
                        org.json.JSONObject(bodyStr).optString("details").toDoubleOrNull()
                    } catch (e: Exception) {
                        null
                    }
                    SendChatResult.Cooldown(remaining)
                } else {
                    android.util.Log.e("RakkuChat", "sendGlobalChatMessage gagal (HTTP ${response.code}): $bodyStr")
                    SendChatResult.Error("Gagal mengirim pesan")
                }
            }
        } catch (e: Exception) {
            SendChatResult.Error(e.message ?: "Gagal mengirim pesan")
        }
    }

    // TOPUP REQUESTS

    sealed class TopupProofResult {
        data class Success(val proofUrl: String) : TopupProofResult()
        data class Error(val stage: String, val detail: String) : TopupProofResult()
    }

    // UPLOAD BUKTI TF TOPUP KE BUCKET 'topup-proofs'
    suspend fun uploadTopupProof(context: Context, userId: String, uri: Uri): TopupProofResult = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
                ?: return@withContext TopupProofResult.Error("upload", "Gagal baca file gambar")
            val fileName = "proof_${System.currentTimeMillis()}.jpg"
            val path = "topup-proofs/$userId/$fileName"
            val uploadUrl = "$SUPABASE_URL/storage/v1/object/$path"

            val imageMediaType = "image/jpeg".toMediaType()
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody(imageMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                TopupProofResult.Success("$SUPABASE_URL/storage/v1/object/public/$path")
            } else {
                val body = response.body?.string()?.take(200) ?: ""
                TopupProofResult.Error("upload", "HTTP ${response.code}: $body")
            }
        } catch (e: Exception) {
            TopupProofResult.Error("upload", e.message ?: e.toString())
        }
    }

    // Dipanggil setelah user transfer via SocialBuzz lalu kirim bukti. amountCoin/price
    // sengaja gak diisi user (gak ada pilihan paket lagi) - admin yang nentuin jumlah
    // koin pas approve, berdasarkan nominal yang kelihatan di bukti transfer.
    suspend fun createTopupRequest(proofImageUrl: String): TopupProofResult = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId()
            ?: return@withContext TopupProofResult.Error("insert", "User belum login")
        val map = mapOf(
            "user_id" to userId,
            "amount_coin" to 0,
            "price" to "-",
            "status" to "pending",
            "proof_image_url" to proofImageUrl
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/topup_requests")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            TopupProofResult.Success(proofImageUrl)
        } else {
            val body = response.body?.string()?.take(200) ?: ""
            TopupProofResult.Error("insert", "HTTP ${response.code}: $body")
        }
    }

    // FEEDBACK & REPORTS
    suspend fun submitFeedback(type: String, message: String): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        val map = mapOf(
            "user_id" to userId,
            "type" to type,
            "message" to message,
            "status" to "open"
        )
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/feedback_reports")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun getFeedbackReports(): List<FeedbackReport> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/feedback_reports?order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, FeedbackReport::class.java)
            val list = moshi.adapter<List<FeedbackReport>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            val userIds = list.map { it.user_id }.distinct()
            val profiles = fetchProfilesMap(userIds)
            list.forEach { f ->
                f.username = profiles[f.user_id]?.username ?: "User"
            }
            list
        } else emptyList()
    }

    suspend fun updateFeedbackStatus(id: String, newStatus: String): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("status" to newStatus)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/feedback_reports?id=eq.$id")
            .patch(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    // ADMIN RPC CALLS
    suspend fun adminBanUser(targetId: String, reason: String?, durationHours: Int?): Boolean = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Any?>("target_id" to targetId, "reason" to reason)
        if (durationHours != null) map["duration_hours"] = durationHours
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_ban_user")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminUnbanUser(targetId: String): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("target_id" to targetId)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_unban_user")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminAddCoin(targetId: String, amount: Int, topupId: Long?): Boolean = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Any?>("target_id" to targetId, "amount" to amount)
        if (topupId != null) map["topup_id"] = topupId
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_add_coin")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminSetRole(targetId: String, newRole: String): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("target_id" to targetId, "new_role" to newRole)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_set_role")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminSetLevel(targetId: String, newLevel: Int): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("target_id" to targetId, "new_level" to newLevel)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_set_level")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminAddExp(targetId: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("target_id" to targetId, "amount" to amount)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_add_exp")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun adminSetUnlimited(targetId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val map = mapOf("target_id" to targetId, "enabled" to enabled)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/admin_set_unlimited")
            .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().isSuccessful
    }

    suspend fun getAllProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/profiles?order=created_at.desc")
            .get().build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, UserProfile::class.java)
            moshi.adapter<List<UserProfile>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
        } else emptyList()
    }

    // ================= PROFIL PUBLIK (klik user di Obrolan Global) =================

    // Data profil + statistik (total komentar, total menit nonton, dsb) milik
    // user LAIN. Pakai RPC get_public_profile_stats (SECURITY DEFINER) karena
    // RLS tabel profiles normalnya cuma izinin baca profil sendiri - lihat
    // profile_stats_migration.sql. RPC ini juga dipakai buat profil sendiri
    // (aman, karena cuma select data publik + count, gak ada data sensitif).
    suspend fun getPublicProfileStats(userId: String): PublicProfileStats? = withContext(Dispatchers.IO) {
        try {
            val bodyJson = moshi.adapter(Map::class.java).toJson(mapOf("p_user_id" to userId))
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/get_public_profile_stats")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, PublicProfileStats::class.java)
                moshi.adapter<List<PublicProfileStats>>(type).fromJson(response.body?.string() ?: "")?.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // Riwayat tontonan/bacaan milik user LAIN, buat ditampilkan di halaman
    // profil publik. Pakai RPC get_public_user_history (SECURITY DEFINER)
    // karena tabel history normalnya cuma bisa dibaca sama pemiliknya sendiri.
    suspend fun getPublicUserHistory(userId: String, limit: Int = 30): List<HistoryItem> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = moshi.adapter(Map::class.java).toJson(mapOf("p_user_id" to userId, "p_limit" to limit))
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/get_public_user_history")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, HistoryItem::class.java)
                moshi.adapter<List<HistoryItem>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ================= CLAN =================
    // Semua aksi yang mengubah data (bikin/gabung/keluar/donasi/klaim) WAJIB
    // lewat RPC SECURITY DEFINER di clan_migration.sql - validasi saldo,
    // kapasitas, "1 user 1 clan", dan cooldown Daily Claim semuanya dicek di
    // sisi database supaya gak bisa dicurangi dari client.

    sealed class ClanActionResult {
        object Success : ClanActionResult()
        object InsufficientCoin : ClanActionResult()
        object AlreadyInClan : ClanActionResult()
        object NotInClan : ClanActionResult()
        object ClanFull : ClanActionResult()
        object ClanNotFound : ClanActionResult()
        object NameTaken : ClanActionResult()
        object InvalidName : ClanActionResult()
        object TagTaken : ClanActionResult()
        object InvalidTag : ClanActionResult()
        object AlreadyClaimedToday : ClanActionResult()
        data class Error(val message: String) : ClanActionResult()
    }

    private fun parseClanError(bodyStr: String): ClanActionResult = when {
        bodyStr.contains("insufficient_coin") -> ClanActionResult.InsufficientCoin
        bodyStr.contains("already_in_clan") -> ClanActionResult.AlreadyInClan
        bodyStr.contains("not_in_clan") -> ClanActionResult.NotInClan
        bodyStr.contains("clan_full") -> ClanActionResult.ClanFull
        bodyStr.contains("clan_not_found") -> ClanActionResult.ClanNotFound
        bodyStr.contains("tag_taken") -> ClanActionResult.TagTaken
        bodyStr.contains("invalid_tag") -> ClanActionResult.InvalidTag
        bodyStr.contains("name_taken") -> ClanActionResult.NameTaken
        bodyStr.contains("invalid_name") -> ClanActionResult.InvalidName
        bodyStr.contains("already_claimed_today") -> ClanActionResult.AlreadyClaimedToday
        else -> ClanActionResult.Error(bodyStr)
    }

    // Bikin clan baru (potong 5.500 RC, pembuat otomatis jadi leader). Balikin
    // id clan yang baru dibuat kalau sukses (null kalau gagal - cek result
    // buat alasannya). tag = singkatan clan (opsional, 2-5 karakter, unik).
    suspend fun createClan(name: String, description: String?, tag: String?, avatarUrl: String?): Pair<ClanActionResult, String?> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_name" to name, "p_description" to description, "p_tag" to tag, "p_avatar_url" to avatarUrl)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/create_clan")
                .post(moshi.adapter(Map::class.java).serializeNulls().toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                sessionManager.getUserId()?.let { fetchUserProfile(it) }
                // RPC create_clan returns a bare uuid string (JSON-encoded, e.g. "\"abc-123\"")
                val clanId = bodyStr.trim().trim('"')
                ClanActionResult.Success to clanId
            } else {
                android.util.Log.e("RakkuClan", "createClan gagal (HTTP ${response.code}): $bodyStr")
                parseClanError(bodyStr) to null
            }
        } catch (e: Exception) {
            ClanActionResult.Error(e.message ?: "unknown_error") to null
        }
    }

    suspend fun joinClan(clanId: String): ClanActionResult = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_clan_id" to clanId)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/join_clan")
                .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                ClanActionResult.Success
            } else {
                val bodyStr = response.body?.string() ?: ""
                android.util.Log.e("RakkuClan", "joinClan gagal (HTTP ${response.code}): $bodyStr")
                parseClanError(bodyStr)
            }
        } catch (e: Exception) {
            ClanActionResult.Error(e.message ?: "unknown_error")
        }
    }

    suspend fun leaveClan(): ClanActionResult = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/leave_clan")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                ClanActionResult.Success
            } else {
                val bodyStr = response.body?.string() ?: ""
                android.util.Log.e("RakkuClan", "leaveClan gagal (HTTP ${response.code}): $bodyStr")
                parseClanError(bodyStr)
            }
        } catch (e: Exception) {
            ClanActionResult.Error(e.message ?: "unknown_error")
        }
    }

    // Donasi RC ke clan sendiri buat naikin Level Clan. Balikin level clan
    // TERBARU (buat langsung update UI tanpa perlu fetch ulang) kalau sukses.
    suspend fun donateToClan(amount: Int): Pair<ClanActionResult, Int?> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_amount" to amount)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/donate_to_clan")
                .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                sessionManager.getUserId()?.let { fetchUserProfile(it) }
                val newLevel = try {
                    org.json.JSONArray(bodyStr).optJSONObject(0)?.optInt("new_level")
                } catch (e: Exception) {
                    null
                }
                ClanActionResult.Success to newLevel
            } else {
                android.util.Log.e("RakkuClan", "donateToClan gagal (HTTP ${response.code}): $bodyStr")
                parseClanError(bodyStr) to null
            }
        } catch (e: Exception) {
            ClanActionResult.Error(e.message ?: "unknown_error") to null
        }
    }

    // Klaim hadiah harian dari clan (1x/hari per user). Balikin jumlah RC yang
    // didapat kalau sukses.
    suspend fun claimDailyClanReward(): Pair<ClanActionResult, Int?> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/claim_daily_clan_reward")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                sessionManager.getUserId()?.let { fetchUserProfile(it) }
                val reward = bodyStr.trim().toIntOrNull()
                ClanActionResult.Success to reward
            } else {
                android.util.Log.e("RakkuClan", "claimDailyClanReward gagal (HTTP ${response.code}): $bodyStr")
                parseClanError(bodyStr) to null
            }
        } catch (e: Exception) {
            ClanActionResult.Error(e.message ?: "unknown_error") to null
        }
    }

    // Leaderboard Clan (nama, level, total donasi, jumlah anggota). Panggil
    // dengan query kosong buat ranking semua clan.
    suspend fun searchClans(query: String? = null, limit: Int = 30): List<ClanSummary> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_query" to query, "p_limit" to limit)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/search_clans")
                .post(moshi.adapter(Map::class.java).serializeNulls().toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ClanSummary::class.java)
                moshi.adapter<List<ClanSummary>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getClanDetail(clanId: String): ClanDetail? = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_clan_id" to clanId)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/get_clan_detail")
                .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ClanDetail::class.java)
                moshi.adapter<List<ClanDetail>>(type).fromJson(response.body?.string() ?: "")?.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getClanMembers(clanId: String): List<ClanMemberInfo> = withContext(Dispatchers.IO) {
        try {
            val map = mapOf("p_clan_id" to clanId)
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/get_clan_members")
                .post(moshi.adapter(Map::class.java).toJson(map).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ClanMemberInfo::class.java)
                moshi.adapter<List<ClanMemberInfo>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Baris keanggotaan clan milik user yang lagi login. Dibaca langsung dari
    // tabel (bukan RPC) karena policy clan_members_select_all sudah izinin
    // baca baris siapa saja, termasuk baris sendiri.
    suspend fun getMyClanMembership(userId: String): MyClanMembership? = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("$SUPABASE_URL/rest/v1/clan_members?user_id=eq.$userId&select=*")
                .get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, MyClanMembership::class.java)
                moshi.adapter<List<MyClanMembership>>(type).fromJson(response.body?.string() ?: "")?.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchProfilesMap(userIds: List<String>): Map<String, UserProfile> = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext emptyMap()
        // Panggil RPC get_public_profiles (SECURITY DEFINER) - lihat
        // get_public_profiles_migration.sql. Ini WAJIB dijalankan dulu di Supabase,
        // karena query langsung ke tabel "profiles" buat user lain kemungkinan
        // diblokir RLS (profiles biasanya cuma izinin baca profil sendiri).
        val body = moshi.adapter(Map::class.java)
            .toJson(mapOf("ids" to userIds))
            .toRequestBody(jsonMediaType)
        val request = newRequestBuilder("$SUPABASE_URL/rest/v1/rpc/get_public_profiles")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, UserProfile::class.java)
            val list = moshi.adapter<List<UserProfile>>(type).fromJson(response.body?.string() ?: "") ?: emptyList()
            list.associateBy { it.id }
        } else emptyMap()
    }
}
