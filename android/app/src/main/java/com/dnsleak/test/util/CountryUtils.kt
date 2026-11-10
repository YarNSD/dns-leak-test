package com.dnsleak.test.util

import java.util.Locale

object CountryUtils {

    private val nameToCodeMap: Map<String, String> by lazy {
        val map = HashMap<String, String>(600)

        // 1. Prepopulate from system Locale ISO countries
        for (iso in Locale.getISOCountries()) {
            val upperIso = iso.uppercase(Locale.ROOT)
            val l = Locale("", upperIso)

            map[upperIso.lowercase(Locale.ROOT)] = upperIso

            val enName = l.getDisplayCountry(Locale.ENGLISH).lowercase(Locale.ROOT).trim()
            if (enName.isNotBlank()) map[enName] = upperIso

            val ruName = l.getDisplayCountry(Locale("ru")).lowercase(Locale.ROOT).trim()
            if (ruName.isNotBlank()) map[ruName] = upperIso

            val defName = l.displayCountry.lowercase(Locale.ROOT).trim()
            if (defName.isNotBlank()) map[defName] = upperIso
        }

        // 2. Add common aliases & variations
        val aliases = mapOf(
            "russia" to "RU",
            "россия" to "RU",
            "russian federation" to "RU",
            "российская федерация" to "RU",
            "sweden" to "SE",
            "швеция" to "SE",
            "germany" to "DE",
            "германия" to "DE",
            "deutschland" to "DE",
            "netherlands" to "NL",
            "нидерланды" to "NL",
            "holland" to "NL",
            "голландия" to "NL",
            "united states" to "US",
            "united states of america" to "US",
            "usa" to "US",
            "сша" to "US",
            "finland" to "FI",
            "финляндия" to "FI",
            "france" to "FR",
            "франция" to "FR",
            "united kingdom" to "GB",
            "великобритания" to "GB",
            "great britain" to "GB",
            "uk" to "GB",
            "switzerland" to "CH",
            "швейцария" to "CH",
            "austria" to "AT",
            "австрия" to "AT",
            "poland" to "PL",
            "польша" to "PL",
            "estonia" to "EE",
            "эстония" to "EE",
            "latvia" to "LV",
            "латвия" to "LV",
            "lithuania" to "LT",
            "литва" to "LT",
            "canada" to "CA",
            "канада" to "CA",
            "japan" to "JP",
            "япония" to "JP",
            "singapore" to "SG",
            "сингапур" to "SG",
            "turkey" to "TR",
            "турция" to "TR",
            "kazakhstan" to "KZ",
            "казахстан" to "KZ",
            "czech republic" to "CZ",
            "czechia" to "CZ",
            "чехия" to "CZ",
            "norway" to "NO",
            "норвегия" to "NO",
            "denmark" to "DK",
            "дания" to "DK",
            "ireland" to "IE",
            "ирландия" to "IE",
            "spain" to "ES",
            "испания" to "ES",
            "italy" to "IT",
            "италия" to "IT",
            "belgium" to "BE",
            "бельгия" to "BE",
            "ukraine" to "UA",
            "украина" to "UA",
            "belarus" to "BY",
            "беларусь" to "BY",
            "белоруссия" to "BY",
            "georgia" to "GE",
            "грузия" to "GE",
            "armenia" to "AM",
            "армения" to "AM",
            "azerbaijan" to "AZ",
            "азербайджан" to "AZ",
            "moldova" to "MD",
            "молдова" to "MD",
            "romania" to "RO",
            "румыния" to "RO",
            "bulgaria" to "BG",
            "болгария" to "BG",
            "serbia" to "RS",
            "сербия" to "RS",
            "cyprus" to "CY",
            "кипр" to "CY",
            "greece" to "GR",
            "греция" to "GR",
            "israel" to "IL",
            "израиль" to "IL",
            "uae" to "AE",
            "united arab emirates" to "AE",
            "оаэ" to "AE",
            "south korea" to "KR",
            "korea" to "KR",
            "южная корея" to "KR",
            "hong kong" to "HK",
            "гонконг" to "HK",
            "taiwan" to "TW",
            "тайвань" to "TW",
            "india" to "IN",
            "индия" to "IN",
            "brazil" to "BR",
            "бразилия" to "BR",
            "australia" to "AU",
            "австралия" to "AU"
        )
        map.putAll(aliases)
        map
    }

    /**
     * Converts a 2-letter ISO country code (e.g. "RU", "SE", "US") to Unicode flag emoji
     */
    fun countryCodeToEmoji(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return ""
        val trimmed = countryCode.trim().uppercase(Locale.ROOT)
        if (trimmed.length != 2) return ""
        val c1 = trimmed[0]
        val c2 = trimmed[1]
        if (c1 !in 'A'..'Z' || c2 !in 'A'..'Z') return ""

        val firstChar = Character.toChars(0x1F1E6 + (c1 - 'A'))
        val secondChar = Character.toChars(0x1F1E6 + (c2 - 'A'))
        return String(firstChar) + String(secondChar)
    }

    /**
     * Resolves 2-letter ISO country code from country name or returns the code itself if already 2 letters
     */
    fun getCountryCode(countryOrCode: String?): String {
        if (countryOrCode.isNullOrBlank()) return ""
        val trimmed = countryOrCode.trim()
        if (trimmed.length == 2 && trimmed[0].uppercaseChar() in 'A'..'Z' && trimmed[1].uppercaseChar() in 'A'..'Z') {
            return trimmed.uppercase(Locale.ROOT)
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        nameToCodeMap[lower]?.let { return it }

        // Partial match
        for ((name, code) in nameToCodeMap) {
            if (lower.contains(name) || name.contains(lower)) {
                return code
            }
        }
        return ""
    }

    /**
     * Returns country flag emoji from code or country name
     */
    fun getCountryFlag(codeOrName: String?): String {
        if (codeOrName.isNullOrBlank()) return ""
        val code = getCountryCode(codeOrName)
        return if (code.isNotBlank()) countryCodeToEmoji(code) else ""
    }

    /**
     * Parses geo string like "Sweden - Cloudflare, Inc." into Country and ISP
     */
    fun parseGeoString(rawGeo: String?): Pair<String, String> {
        if (rawGeo.isNullOrBlank()) return Pair("", "")
        val trimmed = rawGeo.trim()
        if (trimmed.contains(" - ")) {
            val parts = trimmed.split(" - ", limit = 2)
            return Pair(parts[0].trim(), parts[1].trim())
        }
        return Pair(trimmed, "")
    }

    /**
     * Checks if country, country code or ISP indicates a Russian DNS server / provider
     */
    fun isRussian(countryOrCode: String?, isp: String? = null): Boolean {
        val code = getCountryCode(countryOrCode)
        if (code == "RU") return true

        val lowerCountry = (countryOrCode ?: "").lowercase(Locale.ROOT)
        if (lowerCountry.contains("russia") || lowerCountry.contains("россия") || lowerCountry.contains("russian")) {
            return true
        }

        val lowerIsp = (isp ?: "").lowercase(Locale.ROOT)
        val ruMarkers = listOf(
            "msk-ix", "internet exchange", "rostelecom", "ростелеком", "rtcomm",
            "mts", "мтс", "mobile telesystems", "megafon", "мегафон", "netbynet",
            "net by net", "vimpelcom", "вымпелком", "beeline", "билайн",
            "er-telecom", "эр-телеком", "dom.ru", "дом.ru", "transtelecom",
            "транстелеком", "ttk", "ufanet", "уфанет", "akado", "акадо",
            "comcor", "tattelecom", "таттелеком", "mgts", "мгтс", "yota",
            "йота", "skartel", "скартел", "tele2", "теле2", "yandex", "яндекс"
        )
        return ruMarkers.any { lowerIsp.contains(it) }
    }
}
