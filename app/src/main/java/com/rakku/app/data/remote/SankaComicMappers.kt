package com.rakku.app.data.remote

import com.rakku.app.data.model.BacakomikChapterResponse
import com.rakku.app.data.model.BacakomikDetailResponse
import com.rakku.app.data.model.BacakomikListItem
import com.rakku.app.data.model.MangaChapterItem
import com.rakku.app.data.model.MangaDetailResponse
import com.rakku.app.data.model.MangaDownloadResponse
import com.rakku.app.data.model.MangaItem

/**
 * Endpoint bacakomik udah nyediain "slug" polos langsung (bukan link kayak
 * "/manga/naruto/" di endpoint lama), jadi gak perlu ekstraksi apa-apa lagi -
 * fungsi ini dipertahankan (dijadiin no-op passthrough) supaya kalau ada
 * pemanggil lama yang masih manggil extractComicSlug() gak perlu diubah.
 */
fun extractComicSlug(link: String?): String = link?.trim('/')?.substringAfterLast('/') ?: ""

fun BacakomikListItem.toMangaItem(): MangaItem = MangaItem(
    title = title,
    url = slug,
    thumb = cover,
    chapter = chapter,
    rating = rating,
    type = type
)

fun BacakomikDetailResponse.toMangaDetailResponse(): MangaDetailResponse {
    val d = detail
    return MangaDetailResponse(
        title = d?.title,
        thumb = d?.cover,
        synopsis = d?.synopsis,
        author = d?.author,
        type = d?.type,
        rating = d?.rating,
        genres = d?.genres?.map { it.title },
        // "title" chapter dari API ini SELALU KOSONG - label ditulis ulang
        // dari nomor chapter yang diekstrak dari slug (lihat extractChapterLabel).
        chapters = d?.chapters?.map {
            MangaChapterItem(title = extractChapterLabel(it.slug), url = it.slug, date = it.date)
        },
        totalChapters = d?.chapters?.size,
        status = d?.status
    )
}

// "nano-machine-chapter-325" -> "Chapter 325" ; "one-piece-chapter-1050-5" ->
// "Chapter 1050.5" (buat chapter selingan kayak "1050.5"). Fallback ke slug
// apa adanya kalau pola "-chapter-<angka>" gak ketemu.
fun extractChapterLabel(slug: String): String {
    val match = Regex("-chapter-([0-9]+(?:-[0-9]+)?)$").find(slug) ?: return slug
    val num = match.groupValues[1].replaceFirst('-', '.')
    return "Chapter $num"
}

fun BacakomikChapterResponse.toMangaDownloadResponse(): MangaDownloadResponse = MangaDownloadResponse(
    status = if (success == true) "success" else "error",
    title = title,
    images = images,
    nextUrl = navigation?.next,
    prevUrl = navigation?.prev
)
