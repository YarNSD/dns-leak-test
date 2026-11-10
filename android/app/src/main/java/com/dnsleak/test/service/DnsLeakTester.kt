package com.dnsleak.test.service

import android.content.Context
import com.dnsleak.test.model.DnsLeakResult
import com.dnsleak.test.model.DnsProvider
import com.dnsleak.test.model.DnsServerItem
import com.dnsleak.test.model.LocalDnsInfo
import com.dnsleak.test.util.CountryUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID

class DnsLeakTester(private val context: Context) {

    private val gson = Gson()
    private val localInspector = LocalDnsInspector(context)

    companion object {
        private val RU_ISP_KEYWORDS = listOf(
            "rostelecom", "ростелеком", "rtcomm", "mts", "мтс", "mobile telesystems",
            "megafon", "мегафон", "netbynet", "net by net", "vimpelcom", "вымпелком", "beeline", "билайн",
            "er-telecom", "эр-телеком", "dom.ru", "дом.ru", "transtelecom", "транстелеком", "ttk",
            "ufanet", "уфанет", "akado", "акадо", "comcor", "tattelecom", "таттелеком",
            "mgts", "мгтс", "yota", "йота", "skartel", "скартел", "tele2", "теле2",
            "msk-ix", "internet exchange", "yandex", "яндекс"
        )

        private val SECURE_DNS_KEYWORDS = listOf(
            "cloudflare", "google", "quad9", "adguard", "mullvad", "opendns", "cisco",
            "nextdns", "control d", "cleanbrowsing", "dns.sb"
        )
    }

    suspend fun runTest(provider: DnsProvider): DnsLeakResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val localDns = localInspector.inspect()

        return@withContext try {
            when (provider.id) {
                                "edns_ip_api" -> testEdnsIpApi(provider, localDns, startTime)
                "ipleak_net" -> testIpLeakNet(provider, localDns, startTime)
                "mullvad_net" -> testMullvad(provider, localDns, startTime)
                "whoer_net" -> testWhoer(provider, localDns, startTime)
                "adguard_dns" -> testAdguard(provider, localDns, startTime)
                else -> testGenericFallback(provider, localDns, startTime)
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            DnsLeakResult(
                clientIp = "Ошибка подключения",
                clientIsp = e.localizedMessage ?: "Сетевая ошибка",
                clientCountry = "Не определено",
                servers = emptyList(),
                isOverallLeak = false,
                providerName = provider.name,
                testDurationMs = duration,
                localDnsInfo = localDns,
                rawReport = "Ошибка запроса к ${provider.url}: ${e.message}"
            )
        }
    }

    private fun testEdnsIpApi(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        val response = httpGet("http://edns.ip-api.com/json")
        val obj = gson.fromJson(response, JsonObject::class.java)

        val clientObj = obj.getAsJsonObject("client")
        var clientIp = clientObj?.get("ip")?.asString ?: ""
        var clientIsp = clientObj?.get("isp")?.asString ?: ""
        var clientCountry = clientObj?.get("country")?.asString ?: ""

        if (clientIp.isBlank() || clientIp == "Неизвестно") {
            try {
                val ipInfoResp = httpGet("http://ip-api.com/json/?fields=status,country,countryCode,isp,query")
                val ipObj = gson.fromJson(ipInfoResp, JsonObject::class.java)
                if (ipObj.get("status")?.asString == "success") {
                    clientIp = ipObj.get("query")?.asString ?: "Неизвестно"
                    clientIsp = ipObj.get("isp")?.asString ?: "Неизвестно"
                    clientCountry = ipObj.get("country")?.asString ?: "Неизвестно"
                }
            } catch (_: Exception) { }
        }
        if (clientIp.isBlank()) clientIp = "Неизвестно"
        if (clientIsp.isBlank()) clientIsp = "Неизвестно"
        if (clientCountry.isBlank()) clientCountry = "Неизвестно"

        val dnsObj = obj.getAsJsonObject("dns")
        val dnsItems = mutableListOf<DnsServerItem>()

        if (dnsObj != null) {
            val ip = dnsObj.get("ip")?.asString ?: ""
            val rawGeo = dnsObj.get("geo")?.asString ?: ""
            var country = dnsObj.get("country")?.asString ?: ""
            var isp = dnsObj.get("isp")?.asString ?: ""

            if (rawGeo.isNotBlank()) {
                val parsed = CountryUtils.parseGeoString(rawGeo)
                if (country.isBlank()) country = parsed.first
                if (isp.isBlank()) isp = parsed.second.ifBlank { rawGeo }
            }
            if (isp.isBlank()) isp = rawGeo.ifBlank { "DNS Resolver" }

            val countryCode = CountryUtils.getCountryCode(country)
            val source = determineDnsSource(ip, localDns)
            val analysis = evaluateDnsServer(isp, country, clientIsp, clientCountry, localDns)

            dnsItems.add(
                DnsServerItem(
                    ip = ip,
                    isp = isp,
                    country = country,
                    countryCode = countryCode,
                    sourceAttribution = source,
                    isIspDns = analysis.isIspDns,
                    isLeak = analysis.isLeak,
                    isWarning = analysis.isWarning,
                    statusText = analysis.statusText
                )
            )
        }

        val duration = System.currentTimeMillis() - startTime
        return DnsLeakResult(
            clientIp = clientIp,
            clientIsp = clientIsp,
            clientCountry = clientCountry,
            servers = dnsItems,
            isOverallLeak = dnsItems.any { it.isLeak },
            providerName = provider.name,
            testDurationMs = duration,
            localDnsInfo = localDns,
            rawReport = response
        )
    }

    private fun testIpLeakNet(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        val response = httpGet("https://ipleak.net/json/")
        val obj = gson.fromJson(response, JsonObject::class.java)

        val clientIp = obj.get("ip")?.asString ?: "Неизвестно"
        val clientIsp = obj.get("isp_name")?.asString ?: "Неизвестно"
        val clientCountry = obj.get("country_name")?.asString ?: "Неизвестно"

        val dnsItems = mutableListOf<DnsServerItem>()
        val dnsObj = obj.getAsJsonObject("dns_resolvers")
        if (dnsObj != null) {
            for (key in dnsObj.keySet()) {
                val item = dnsObj.getAsJsonObject(key)
                val ip = item.get("ip")?.asString ?: key
                val isp = item.get("isp_name")?.asString ?: ""
                val country = item.get("country_name")?.asString ?: ""
                val source = determineDnsSource(ip, localDns)
                val analysis = evaluateDnsServer(isp, country, clientIsp, clientCountry, localDns)

                dnsItems.add(
                    DnsServerItem(
                        ip = ip,
                        isp = isp,
                        country = country,
                        countryCode = item.get("country_code")?.asString ?: "",
                        sourceAttribution = source,
                        isIspDns = analysis.isIspDns,
                        isLeak = analysis.isLeak,
                        isWarning = analysis.isWarning,
                        statusText = analysis.statusText
                    )
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        return DnsLeakResult(
            clientIp = clientIp,
            clientIsp = clientIsp,
            clientCountry = clientCountry,
            servers = dnsItems,
            isOverallLeak = dnsItems.any { it.isLeak },
            providerName = provider.name,
            testDurationMs = duration,
            localDnsInfo = localDns,
            rawReport = response
        )
    }

    private fun testMullvad(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        val response = httpGet("https://am.i.mullvad.net/json")
        val obj = gson.fromJson(response, JsonObject::class.java)

        val clientIp = obj.get("ip")?.asString ?: "Неизвестно"
        val clientIsp = obj.get("organization")?.asString ?: "Неизвестно"
        val clientCountry = obj.get("country")?.asString ?: "Неизвестно"

        val dnsItems = mutableListOf<DnsServerItem>()
        try {
            val dnsResponse = httpGet("https://am.i.mullvad.net/dnsleak")
            val dnsObj = gson.fromJson(dnsResponse, JsonArray::class.java)
            for (item in dnsObj) {
                val sObj = item.asJsonObject
                val ip = sObj.get("ip")?.asString ?: ""
                val isp = sObj.get("organization")?.asString ?: ""
                val country = sObj.get("country")?.asString ?: ""
                val source = determineDnsSource(ip, localDns)
                val analysis = evaluateDnsServer(isp, country, clientIsp, clientCountry, localDns)

                val countryCode = CountryUtils.getCountryCode(country)
                dnsItems.add(
                    DnsServerItem(
                        ip = ip,
                        isp = isp,
                        country = country,
                        countryCode = countryCode,
                        sourceAttribution = source,
                        isIspDns = analysis.isIspDns,
                        isLeak = analysis.isLeak,
                        isWarning = analysis.isWarning,
                        statusText = analysis.statusText
                    )
                )
            }
        } catch (_: Exception) { }

        val duration = System.currentTimeMillis() - startTime
        return DnsLeakResult(
            clientIp = clientIp,
            clientIsp = clientIsp,
            clientCountry = clientCountry,
            servers = dnsItems,
            isOverallLeak = dnsItems.any { it.isLeak },
            providerName = provider.name,
            testDurationMs = duration,
            localDnsInfo = localDns,
            rawReport = response
        )
    }

    private fun testWhoer(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        return testEdnsIpApi(provider, localDns, startTime)
    }

    private fun testAdguard(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        val response = httpGet("https://api-adguard-dns.org/v1/status")
        val obj = gson.fromJson(response, JsonObject::class.java)

        val clientIp = obj.get("ip")?.asString ?: "Неизвестно"
        val clientIsp = obj.get("isp")?.asString ?: "AdGuard Diagnostic Node"
        val clientCountry = obj.get("country")?.asString ?: "Неизвестно"

        val dnsItems = mutableListOf<DnsServerItem>()
        val isFiltered = obj.get("dns_filter")?.asBoolean ?: false
        val source = if (!localDns.privateDnsHostname.isNullOrEmpty()) {
            "Источник: Настройки Android (Частный DNS: ${localDns.privateDnsHostname})"
        } else {
            determineDnsSource(clientIp, localDns)
        }

        val analysis = evaluateDnsServer(clientIsp, clientCountry, clientIsp, clientCountry, localDns)

        val adgCountryCode = CountryUtils.getCountryCode(clientCountry)
        dnsItems.add(
            DnsServerItem(
                ip = clientIp,
                isp = if (isFiltered) "AdGuard Protected Resolver" else clientIsp,
                country = clientCountry,
                countryCode = adgCountryCode,
                sourceAttribution = source,
                isIspDns = analysis.isIspDns,
                isLeak = analysis.isLeak,
                isWarning = if (isFiltered) false else analysis.isWarning,
                statusText = if (isFiltered) "БЕЗОПАСНО: DoH/DoT защита AdGuard" else analysis.statusText
            )
        )

        val duration = System.currentTimeMillis() - startTime
        return DnsLeakResult(
            clientIp = clientIp,
            clientIsp = clientIsp,
            clientCountry = clientCountry,
            servers = dnsItems,
            isOverallLeak = dnsItems.any { it.isLeak },
            providerName = provider.name,
            testDurationMs = duration,
            localDnsInfo = localDns,
            rawReport = response
        )
    }

    private fun testGenericFallback(
        provider: DnsProvider,
        localDns: LocalDnsInfo,
        startTime: Long
    ): DnsLeakResult {
        return testEdnsIpApi(provider, localDns, startTime)
    }

    private fun determineDnsSource(resolverIp: String, localDns: LocalDnsInfo): String {
        if (!localDns.privateDnsHostname.isNullOrBlank()) {
            return "Источник: Настройки Android (Частный DNS: ${localDns.privateDnsHostname})"
        }

        if (localDns.gatewayIp != null && localDns.dnsServers.contains(localDns.gatewayIp)) {
            return "Источник: Задан на роутере (${localDns.gatewayIp} / DHCP)"
        }

        for (localIp in localDns.dnsServers) {
            if (localIp == resolverIp) {
                return "Источник: Настроен в параметрах адаптера ($localIp)"
            }
            if (localIp.startsWith("192.168.") || localIp.startsWith("10.") || localIp.startsWith("172.16.")) {
                return "Источник: Задан на локальном шлюзе/роутере ($localIp)"
            }
        }

        if (localDns.isVpnActive) {
            return "Источник: VPN-туннель (${localDns.interfaceName ?: "туннель"})"
        }

        return "Источник: Внешний транзитный резолвер"
    }

    private data class DnsEvaluation(
        val isIspDns: Boolean,
        val isLeak: Boolean,
        val isWarning: Boolean,
        val statusText: String
    )

    private fun evaluateDnsServer(
        dnsIsp: String,
        dnsCountry: String,
        clientIsp: String,
        clientCountry: String,
        localDns: LocalDnsInfo
    ): DnsEvaluation {
        val lowerDnsIsp = dnsIsp.lowercase()
        val lowerClientIsp = clientIsp.lowercase()

        val isSecureDns = SECURE_DNS_KEYWORDS.any { lowerDnsIsp.contains(it) }
        val isRussian = CountryUtils.isRussian(dnsCountry, dnsIsp)
        val isKnownRuIsp = RU_ISP_KEYWORDS.any { lowerDnsIsp.contains(it) } || isRussian
        val matchesClientIsp = lowerClientIsp.isNotEmpty() &&
                lowerClientIsp != "неизвестно" &&
                (lowerDnsIsp.contains(lowerClientIsp) || lowerClientIsp.contains(lowerDnsIsp))

        val isIspDns = (isKnownRuIsp || matchesClientIsp) && !isSecureDns
        val hasPrivateDns = localDns.hasActivePrivateDns() || localDns.privateDnsMode == "opportunistic"

        val isLeak: Boolean
        val isWarning: Boolean

        when {
            // Case 1: VPN is active
            localDns.isVpnActive -> {
                isLeak = isIspDns || matchesClientIsp || (isRussian && !clientCountry.equals("Russia", ignoreCase = true))
                isWarning = false
            }
            // Case 2: Private DNS is active
            hasPrivateDns -> {
                isLeak = isIspDns && !isSecureDns
                isWarning = false
            }
            // Case 3: Wi-Fi network (queries go through router)
            localDns.isWifi -> {
                isLeak = false
                isWarning = isRussian
            }
            // Case 4: Mobile network (cellular)
            localDns.isCellular -> {
                isLeak = isIspDns || isRussian
                isWarning = false
            }
            // Case 5: Other
            else -> {
                isLeak = isIspDns || isRussian
                isWarning = !isLeak && isRussian
            }
        }

        val statusText = when {
            isLeak && localDns.isVpnActive -> "УТЕЧКА: DNS уходит мимо VPN ($dnsIsp)"
            isLeak -> "ВНИМАНИЕ: Используется DNS интернет-провайдера ($dnsIsp)"
            isWarning -> "ПРЕДУПРЕЖДЕНИЕ: Российский DNS на роутере ($dnsIsp)"
            localDns.isWifi && !isRussian -> "БЕЗОПАСНО: Внешний DNS на роутере ($dnsIsp)"
            isSecureDns -> "БЕЗОПАСНО: DNS провайдера НЕ используется ($dnsIsp)"
            else -> "БЕЗОПАСНО: Внешний независимый резолвер ($dnsIsp)"
        }

        return DnsEvaluation(
            isIspDns = isIspDns,
            isLeak = isLeak,
            isWarning = isWarning,
            statusText = statusText
        )
    }

    private fun httpGet(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 9000
        conn.readTimeout = 9000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; DNS Leak Test 1.0)")
        conn.setRequestProperty("Accept", "application/json")

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw RuntimeException("HTTP $responseCode от $urlStr")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }
}
