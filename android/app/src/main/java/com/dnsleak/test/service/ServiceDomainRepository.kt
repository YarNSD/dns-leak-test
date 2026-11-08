package com.dnsleak.test.service

import android.content.Context
import com.dnsleak.test.model.ServiceDomainItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

data class ServiceSuggestion(
    val displayText: String,
    val service: ServiceDomainItem,
    val matchedDomain: String? = null
) {
    override fun toString(): String = displayText
}

class ServiceDomainRepository(private val context: Context) {

    private val gson = Gson()
    private var cachedServices: List<ServiceDomainItem>? = null

    fun getAllServices(): List<ServiceDomainItem> {
        if (cachedServices != null) return cachedServices!!

        cachedServices = try {
            val jsonString = context.assets.open("services_db.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<ServiceDomainItem>>() {}.type
            gson.fromJson<List<ServiceDomainItem>>(jsonString, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return cachedServices!!
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
