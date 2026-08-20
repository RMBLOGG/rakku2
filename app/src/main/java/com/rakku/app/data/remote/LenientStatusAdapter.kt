package com.rakku.app.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * Beberapa endpoint upstream (animasu/komiku scraper) kadang mengembalikan
 * field "status" sebagai boolean (true/false), kadang sebagai string
 * ("success"/"Ongoing"/dll), tergantung endpoint-nya. Moshi default akan
 * CRASH (JsonDataException) kalau tipe yang diharapkan tidak sama persis
 * dengan tipe di JSON asli.
 *
 * PENTING: adapter ini SENGAJA tidak lagi pakai @JsonQualifier custom
 * (dulu @LenientStatus). KSP2 (versi KSP yang dipakai project ini) punya
 * bug yang bikin kspDebugKotlin crash ("Error preparing X: jdk.proxy...$Proxy.value")
 * setiap kali ada @JsonClass(generateAdapter = true) yang fieldnya ditandai
 * custom @JsonQualifier. Ini bug upstream, belum ada fix dari Moshi/Google:
 * https://github.com/square/moshi/issues/1874
 * https://github.com/google/ksp/issues/2019
 *
 * Solusinya: adapter ini didaftarkan TANPA qualifier, jadi otomatis dipakai
 * Moshi untuk SEMUA field bertipe String?/String (menggantikan adapter
 * bawaan Moshi). Ini aman karena perilakunya cuma superset dari behaviour
 * default (string tetap diparse sebagai string, cuma jadi toleran juga ke
 * boolean/number/null, bukan crash).
 */
class LenientStatusAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: String?) {
        writer.value(value)
    }
}

/**
 * Beberapa endpoint mengembalikan daftar genre sebagai array of string
 * (["Action", "Comedy"]) tapi ada juga yang berupa array of object
 * ([{"name": "Action"}, {"name": "Comedy"}]). Adapter ini menyeragamkan
 * keduanya jadi List<String> di sisi Kotlin, meniru cara web (`g?.name || g`).
 *
 * Sama seperti LenientStatusAdapter di atas: SENGAJA tanpa @JsonQualifier
 * custom supaya tidak memicu bug crash KSP2 (lihat penjelasan di atas).
 * Didaftarkan tanpa qualifier, otomatis dipakai untuk semua field
 * List<String>?/List<String> di seluruh app.
 */
class LenientNameListAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): List<String>? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }
        val result = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonReader.Token.STRING -> result.add(reader.nextString())
                JsonReader.Token.BEGIN_OBJECT -> {
                    var name: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key == "name" && reader.peek() == JsonReader.Token.STRING) {
                            name = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (name != null) result.add(name)
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return result
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: List<String>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        value.forEach { writer.value(it) }
        writer.endArray()
    }
}
