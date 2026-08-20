package com.rakku.app.ui.profile

import java.util.Calendar
import java.util.TimeZone

// Kecil-kecilan aja: ngitung "sudah berapa hari sejak akun dibuat" dari
// kolom profiles.created_at (format ISO 8601 dari Postgres/Supabase, mis.
// "2024-01-15T10:23:45.123456+00:00"). Sengaja TIDAK pakai java.time
// (Instant/LocalDate dkk) karena minSdk project ini 24 dan belum ada
// coreLibraryDesugaring di build.gradle.kts - java.time baru kejamin
// available mulai API 26 tanpa desugaring.
object ProfileDateUtils {

    /**
     * Balikin jumlah hari sejak [createdAtIso] sampai hari ini (dibulatkan
     * ke bawah, dihitung dari tanggal kalender - bukan 24 jam penuh, jadi
     * user yang daftar "kemarin" langsung kebaca 1 hari walau baru lewat
     * beberapa jam). Balikin null kalau string-nya gak bisa di-parse.
     */
    fun daysSince(createdAtIso: String?): Int? {
        if (createdAtIso.isNullOrBlank() || createdAtIso.length < 10) return null
        return try {
            // Cukup ambil bagian "yyyy-MM-dd" di depan - presisi jam/menit
            // gak dibutuhkan buat hitung "sudah berapa hari".
            val year = createdAtIso.substring(0, 4).toInt()
            val month = createdAtIso.substring(5, 7).toInt()
            val day = createdAtIso.substring(8, 10).toInt()

            val utc = TimeZone.getTimeZone("UTC")
            val created = Calendar.getInstance(utc).apply {
                clear()
                set(year, month - 1, day)
            }
            val today = Calendar.getInstance(utc).apply {
                val now = Calendar.getInstance(utc)
                clear()
                set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
            }

            val diffMillis = today.timeInMillis - created.timeInMillis
            val days = (diffMillis / (24L * 60 * 60 * 1000)).toInt()
            if (days < 0) 0 else days
        } catch (e: Exception) {
            null
        }
    }

    /** Format menit jadi teks singkat, mis. 125 menit -> "2j 5m", 40 -> "40m". */
    fun formatMinutes(totalMinutes: Int?): String {
        val minutes = totalMinutes ?: 0
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (remainder == 0) "${hours}j" else "${hours}j ${remainder}m"
    }
}
