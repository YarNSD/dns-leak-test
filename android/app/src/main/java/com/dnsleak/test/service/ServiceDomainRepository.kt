package com.dnsleak.test.service

import android.content.Context
import com.dnsleak.test.model.ServiceDomainItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ServiceSuggestion(
    val displayText: String,
    val service: ServiceDomainItem,
    val matchedDomain: String? = null
) {
    override fun toString(): String = displayText
}

class ServiceDomainRepository(private val context: Context) {

    companion object {
        private const val REMOTE_DB_URL =
            "https://raw.githubusercontent.com/YarNSD/dns-leak-test/main/android/app/src/main/assets/services_db.json"
        private const val CACHED_FILE_NAME = "services_db_cached.json"
    }

    private val gson = Gson()
    private var cachedServices: List<ServiceDomainItem>? = null

    fun getAllServices(): List<ServiceDomainItem> {
        if (cachedServices != null) return cachedServices!!

        val type = object : TypeToken<List<ServiceDomainItem>>() {}.type
        val localFile = File(context.filesDir, CACHED_FILE_NAME)

        cachedServices = try {
            val fromCache = if (localFile.exists() && localFile.length() > 100) {
                gson.fromJson<List<ServiceDomainItem>>(localFile.readText(), type)
            } else null

            if (!fromCache.isNullOrEmpty()) {
                fromCache
            } else {
                val jsonString = context.assets.open("services_db.json").bufferedReader().use { it.readText() }
                gson.fromJson<List<ServiceDomainItem>>(jsonString, type) ?: emptyList()
            }
        } catch (_: Exception) {
            try {
                val jsonString = context.assets.open("services_db.json").bufferedReader().use { it.readText() }
                gson.fromJson<List<ServiceDomainItem>>(jsonString, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        return cachedServices!!
    }

    /**
     * Downloads the latest services_db.json from GitHub, validates it, and caches locally.
     */
    suspend fun updateFromGitHub(): Result<Int> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(REMOTE_DB_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(RuntimeException("HTTP $code"))
            }

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<ServiceDomainItem>>() {}.type
            val parsed = gson.fromJson<List<ServiceDomainItem>>(jsonText, type)

            if (parsed.isNullOrEmpty()) {
                return@withContext Result.failure(RuntimeException("Пустая или повреждённая база"))
            }

            val targetFile = File(context.filesDir, CACHED_FILE_NAME)
            val tmpFile = File(context.filesDir, "$CACHED_FILE_NAME.tmp")
            tmpFile.writeText(jsonText)
            tmpFile.renameTo(targetFile)

            cachedServices = parsed
            Result.success(parsed.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    fun getSuggestions(query: String, limit: Int = 15): List<ServiceSuggestion> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) {
            return getAllServices().take(limit).map {
                ServiceSuggestion(it.name, it)
            }
        }

        val results = mutableListOf<ServiceSuggestion>()
        val seenServices = mutableSetOf<String>()
        val all = getAllServices()

        // 1. Direct name match (starts with first)
        for (item in all) {
            val nameLower = item.name.lowercase(Locale.ROOT)
            val idLower = item.id.lowercase(Locale.ROOT)
            if (nameLower.startsWith(q) || idLower.startsWith(q)) {
                if (seenServices.add(item.id)) {
                    results.add(ServiceSuggestion(item.name, item))
                }
            }
        }

        // 2. Alias match (e.g. "gemini", "джемини", "ютуб", "тг", "вк")
        for (item in all) {
            if (seenServices.contains(item.id)) continue
            val matchedAlias = item.aliases.firstOrNull { it.lowercase(Locale.ROOT).contains(q) }
            if (matchedAlias != null) {
                seenServices.add(item.id)
                results.add(ServiceSuggestion("${item.name} ($matchedAlias)", item))
            }
        }

        // 3. Name contains
        for (item in all) {
            if (seenServices.contains(item.id)) continue
            if (item.name.lowercase(Locale.ROOT).contains(q)) {
                seenServices.add(item.id)
                results.add(ServiceSuggestion(item.name, item))
            }
        }

        // 4. Primary domain or subdomains match (e.g. searching "gemini.google.com" or "t.me")
        for (item in all) {
            if (seenServices.contains(item.id)) continue
            val matchedDomain = item.domains.firstOrNull { it.lowercase(Locale.ROOT).contains(q) }
                ?: if (item.primaryDomain.lowercase(Locale.ROOT).contains(q)) item.primaryDomain else null
            if (matchedDomain != null) {
                seenServices.add(item.id)
                results.add(ServiceSuggestion("${item.name} ($matchedDomain)", item, matchedDomain))
            }
        }

        return results.take(limit)
    }

    fun findBestMatch(query: String): ServiceDomainItem? {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return null

        val top = getSuggestions(q, 1).firstOrNull()
        if (top != null) return top.service

        return getAllServices().firstOrNull { item ->
            item.primaryDomain.equals(q, ignoreCase = true) ||
            item.domains.any { it.equals(q, ignoreCase = true) }
        }
    }

    fun searchServices(query: String): List<ServiceDomainItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return getAllServices()

        return getAllServices().filter { item ->
            item.name.lowercase(Locale.ROOT).contains(q) ||
            item.id.lowercase(Locale.ROOT).contains(q) ||
            item.aliases.any { it.lowercase(Locale.ROOT).contains(q) } ||
            item.primaryDomain.lowercase(Locale.ROOT).contains(q) ||
            item.domains.any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }

    fun getById(id: String): ServiceDomainItem? {
        val lowerId = id.trim().lowercase(Locale.ROOT)
        return getAllServices().firstOrNull { it.id.equals(lowerId, ignoreCase = true) }
    }

    fun findServiceByCdnName(cdnName: String): ServiceDomainItem? {
        val lower = cdnName.lowercase(Locale.ROOT)
        return when {
            lower.contains("cloudflare") -> getById("cloudflare")
            lower.contains("akamai") -> getById("akamai")
            lower.contains("fastly") -> getById("fastly")
            lower.contains("gcore") || lower.contains("g-core") -> getById("gcore")
            lower.contains("cloudfront") || lower.contains("amazon") -> getById("amazon")
            lower.contains("google") -> getById("google")
            lower.contains("vk") -> getById("vk")
            lower.contains("yandex") -> getById("yandex")
            lower.contains("ddos-guard") -> getById("ddosguard")
            lower.contains("qrator") -> getById("qrator")
            lower.contains("cdn77") -> getById("cdn77")
            lower.contains("bunny") -> getById("bunnycdn")
            lower.contains("selectel") -> getById("selectel")
            lower.contains("adguard") -> getById("adguard")
            else -> null
        }
    }
}
