package com.dnsleak.test.service

import com.dnsleak.test.model.CdnCheckResult
import com.dnsleak.test.model.CdnEdgeIp
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
import java.util.Locale

class CdnInspector {

    private val gson = Gson()

    companion object {
        private const val TIMEOUT_MS = 3500

        private val CDN_DEFINITIONS = listOf(
            CdnDef(
                name = "Cloudflare",
                cnameKeywords = listOf("cloudflare.net", "cloudflare.com", "cdn.cloudflare.net"),
                asnKeywords = listOf("13335", "cloudflare"),
                orgKeywords = listOf("cloudflare"),
                headerSignatures = listOf("cf-ray", "cf-cache-status"),
                serverKeyword = "cloudflare"
            ),
            CdnDef(
                name = "Amazon CloudFront",
                cnameKeywords = listOf("cloudfront.net"),
                asnKeywords = listOf("16509", "amazon"),
                orgKeywords = listOf("cloudfront", "amazon.com"),
                headerSignatures = listOf("x-amz-cf-id", "x-amz-cf-pop"),
                serverKeyword = "cloudfront"
            ),
            CdnDef(
                name = "Akamai Technologies",
                cnameKeywords = listOf("akamaiedge.net", "akamai.net", "edgekey.net", "edgesuite.net", "srip.net", "akamaitechnologies.com", "akadns.net"),
                asnKeywords = listOf("20940", "16625", "akamai"),
                orgKeywords = listOf("akamai"),
                headerSignatures = listOf("x-akamai-transformed", "x-akamai-request-id"),
                serverKeyword = "akamaighost"
            ),
            CdnDef(
                name = "Fastly",
                cnameKeywords = listOf("fastly.net", "fastlylb.net", "map.fastly.net"),
                asnKeywords = listOf("54113", "fastly"),
                orgKeywords = listOf("fastly"),
                headerSignatures = listOf("x-served-by", "fastly-debug-digest"),
                serverKeyword = "fastly"
            ),
            CdnDef(
                name = "Gcore (G-Core Labs)",
                cnameKeywords = listOf("gcdn.co", "gcore.com", "gcorelabs.net"),
                asnKeywords = listOf("202425", "gcore"),
                orgKeywords = listOf("gcore", "g-core"),
                headerSignatures = listOf("x-gcore-request-id", "g-cdn"),
                serverKeyword = "gcore"
            ),
            CdnDef(
                name = "VK Cloud CDN",
                cnameKeywords = listOf("vkcdn.ru", "mycdn.me", "vk.com"),
                asnKeywords = listOf("197695", "vk-llc"),
                orgKeywords = listOf("vk company", "mail.ru"),
                headerSignatures = listOf("x-mr-trace", "x-vk-trace"),
                serverKeyword = "vk"
            ),
            CdnDef(
                name = "Yandex Cloud CDN",
                cnameKeywords = listOf("yandexcloud.net", "yc.ru", "cdn.yandex.net"),
                asnKeywords = listOf("200350", "yandex-cloud"),
                orgKeywords = listOf("yandex"),
                headerSignatures = listOf("x-yc-trace"),
                serverKeyword = "yandex"
            ),
            CdnDef(
                name = "DDoS-Guard",
                cnameKeywords = listOf("ddos-guard.net"),
                asnKeywords = listOf("57724", "ddos-guard"),
                orgKeywords = listOf("ddos-guard"),
                headerSignatures = listOf("x-ddg-cache"),
                serverKeyword = "ddos-guard"
            ),
            CdnDef(
                name = "Qrator Labs (Anti-DDoS / CDN)",
                cnameKeywords = listOf("qrator.net"),
                asnKeywords = listOf("197068", "qrator"),
                orgKeywords = listOf("qrator"),
                headerSignatures = listOf("x-qrator"),
                serverKeyword = "qrator"
            ),
            CdnDef(
                name = "CDN77 / DataCamp",
                cnameKeywords = listOf("cdn77.org", "cdn77.net", "r.worldssl.net"),
                asnKeywords = listOf("60068", "cdn77", "datacamp"),
                orgKeywords = listOf("cdn77", "datacamp"),
                headerSignatures = listOf("x-77-cache", "x-cdn77"),
                serverKeyword = "cdn77"
            ),
            CdnDef(
                name = "BunnyCDN",
                cnameKeywords = listOf("b-cdn.net", "bunnycdn.com"),
                asnKeywords = listOf("200325", "bunny"),
                orgKeywords = listOf("bunnycdn", "bunny"),
                headerSignatures = listOf("b-cache", "b-cdn"),
                serverKeyword = "bunnycdn"
            ),
            CdnDef(
                name = "Microsoft Azure Front Door",
                cnameKeywords = listOf("azureedge.net", "azurefd.net", "msecnd.net", "trafficmanager.net"),
                asnKeywords = listOf("8075", "microsoft"),
                orgKeywords = listOf("microsoft"),
                headerSignatures = listOf("x-azure-ref", "x-azure-fdid"),
                serverKeyword = "azure"
            ),
            CdnDef(
                name = "Google Cloud CDN",
                cnameKeywords = listOf("googlehosted.com", "1e100.net"),
                asnKeywords = listOf("15169", "google"),
                orgKeywords = listOf("google"),
                headerSignatures = listOf("x-goog-generation"),
                serverKeyword = "gfe"
            ),
            CdnDef(
                name = "Imperva / Incapsula",
                cnameKeywords = listOf("incapdns.net", "imperva.com"),
                asnKeywords = listOf("19551", "incapsula"),
                orgKeywords = listOf("incapsula", "imperva"),
                headerSignatures = listOf("x-iinfo", "x-cdn"),
                serverKeyword = "incapsula"
            ),
            CdnDef(
                name = "Selectel CDN",
                cnameKeywords = listOf("selcdn.ru", "selectel.ru", "cdn.selectel.ru"),
                asnKeywords = listOf("49544", "selectel"),
                orgKeywords = listOf("selectel"),
                headerSignatures = listOf("x-sel-trace"),
                serverKeyword = "selectel"
            )
        )
    }

    suspend fun inspect(rawInput: String): CdnCheckResult = withContext(Dispatchers.IO) {
        val domain = cleanDomain(rawInput)
        if (domain.isBlank() || !domain.contains(".")) {
            return@withContext CdnCheckResult(
                domain = rawInput,
                cdnServiceName = "Некорректный домен",
                isCdnDetected = false,
                detectionMethod = "Ошибка валидации",
                errorMessage = "Укажите корректное доменное имя (например, habr.com или apple.com)"
            )
        }

        val discoveredCnames = mutableListOf<String>()
        val edgeIps = mutableListOf<CdnEdgeIp>()
        val evidenceHeaders = mutableListOf<Pair<String, String>>()
        val matchedMethods = mutableListOf<String>()
        var detectedCdnName: String? = null

        // 1. DoH CNAME & A lookup
        try {
            val dohAnswers = queryDoh(domain)
            for (ans in dohAnswers) {
                if (ans.type == 5) { // CNAME
                    val cleanCname = ans.data.trimEnd('.')
                    if (!discoveredCnames.contains(cleanCname)) {
                        discoveredCnames.add(cleanCname)
                    }
                }
            }
            // If apex domain has no CNAME, check www.domain
            if (discoveredCnames.isEmpty() && !domain.startsWith("www.")) {
                val wwwAnswers = queryDoh("www.$domain")
                for (ans in wwwAnswers) {
                    if (ans.type == 5) {
                        val cleanCname = ans.data.trimEnd('.')
                        if (!discoveredCnames.contains(cleanCname)) {
                            discoveredCnames.add(cleanCname)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // DoH might fail if filtered, continue to IP and HTTP probes
        }

        // Check CNAME matches
        for (cname in discoveredCnames) {
            val lowerCname = cname.lowercase(Locale.ROOT)
            for (def in CDN_DEFINITIONS) {
                if (def.cnameKeywords.any { lowerCname.contains(it) }) {
                    detectedCdnName = def.name
                    matchedMethods.add("CNAME (${cname})")
                    break
                }
            }
            if (detectedCdnName != null) break
        }

        // 2. DNS Resolution for IP addresses
        val resolvedIps = mutableListOf<String>()
        try {
            val addresses = InetAddress.getAllByName(domain)
            for (addr in addresses) {
                val ipStr = addr.hostAddress ?: continue
                if (!resolvedIps.contains(ipStr)) {
                    resolvedIps.add(ipStr)
                }
            }
        } catch (e: Exception) {
            if (discoveredCnames.isEmpty()) {
                return@withContext CdnCheckResult(
                    domain = domain,
                    cdnServiceName = "Ошибка разрешения DNS",
                    isCdnDetected = false,
                    detectionMethod = "DNS Lookup",
                    errorMessage = "Не удалось разрешить домен в IP-адрес: ${e.localizedMessage ?: "Хост не найден"}"
                )
            }
        }

        // 3. IP ASN & Whois Lookup for Edge IPs
        for (ip in resolvedIps.take(3)) {
            try {
                val edgeIp = fetchIpInfo(ip)
                if (edgeIp != null) {
                    edgeIps.add(edgeIp)

                    if (detectedCdnName == null) {
                        val lowerAsn = edgeIp.asn.lowercase(Locale.ROOT)
                        val lowerOrg = edgeIp.org.lowercase(Locale.ROOT)

                        for (def in CDN_DEFINITIONS) {
                            val asnMatch = def.asnKeywords.any { lowerAsn.contains(it) }
                            val orgMatch = def.orgKeywords.any { lowerOrg.contains(it) }
                            if (asnMatch || orgMatch) {
                                detectedCdnName = def.name
                                matchedMethods.add("ASN/Сеть (${edgeIp.asn})")
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. HTTP / HTTPS Header Probing
        try {
            val headers = probeHttpHeaders(domain)
            evidenceHeaders.addAll(headers)

            for ((key, value) in headers) {
                val lowerKey = key.lowercase(Locale.ROOT)
                val lowerVal = value.lowercase(Locale.ROOT)

                for (def in CDN_DEFINITIONS) {
                    val headerKeyMatch = def.headerSignatures.any { lowerKey.contains(it) }
                    val serverMatch = lowerKey == "server" && lowerVal.contains(def.serverKeyword)
                    if (headerKeyMatch || serverMatch) {
                        if (detectedCdnName == null) {
                            detectedCdnName = def.name
                        }
                        val marker = "$key: $value"
                        if (!matchedMethods.contains(marker)) {
                            matchedMethods.add("Заголовок ($key)")
                        }
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        val isCdn = detectedCdnName != null
        val finalServiceName = detectedCdnName ?: "Не обнаружен (Прямой хостинг)"
        val finalMethod = if (matchedMethods.isNotEmpty()) {
            "Обнаружен по: " + matchedMethods.distinct().joinToString(", ")
        } else if (isCdn) {
            "Обнаружен по сигнатурам сети"
        } else {
            "CNAME и CDN-сигнатуры отсутствуют"
        }

        CdnCheckResult(
            domain = domain,
            cdnServiceName = finalServiceName,
            isCdnDetected = isCdn,
            detectionMethod = finalMethod,
            cdnDomains = discoveredCnames,
            edgeIps = edgeIps,
            evidenceHeaders = evidenceHeaders
        )
    }

    private fun cleanDomain(raw: String): String {
        var d = raw.trim()
        if (d.startsWith("https://", ignoreCase = true)) {
            d = d.substring(8)
        } else if (d.startsWith("http://", ignoreCase = true)) {
            d = d.substring(7)
        }
        val slashIdx = d.indexOf('/')
        if (slashIdx != -1) {
            d = d.substring(0, slashIdx)
        }
        val colonIdx = d.indexOf(':')
        if (colonIdx != -1) {
            d = d.substring(0, colonIdx)
        }
        val queryIdx = d.indexOf('?')
        if (queryIdx != -1) {
            d = d.substring(0, queryIdx)
        }
        return d.trim().trimEnd('.')
    }

    private data class DohAnswer(val name: String, val type: Int, val data: String)

    private fun queryDoh(domain: String): List<DohAnswer> {
        val list = mutableListOf<DohAnswer>()
        val endpoints = listOf(
            "https://dns.google/resolve?name=${domain}&type=A",
            "https://dns.google/resolve?name=${domain}&type=CNAME",
            "https://cloudflare-dns.com/dns-query?name=${domain}&type=A"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/dns-json")
                    setRequestProperty("User-Agent", "DnsLeakTest/1.0")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = gson.fromJson(body, JsonObject::class.java)
                    if (json != null && json.has("Answer")) {
                        val answers = json.getAsJsonArray("Answer")
                        for (item in answers) {
                            val obj = item.asJsonObject
                            val name = obj.get("name")?.asString ?: ""
                            val type = obj.get("type")?.asInt ?: 0
                            val data = obj.get("data")?.asString ?: ""
                            list.add(DohAnswer(name, type, data))
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }

    private fun fetchIpInfo(ip: String): CdnEdgeIp? {
        try {
            val url = URL("http://ip-api.com/json/${ip}?fields=status,country,countryCode,city,isp,org,as,query")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = gson.fromJson(body, JsonObject::class.java) ?: return null
                if (json.get("status")?.asString == "success") {
                    return CdnEdgeIp(
                        ip = json.get("query")?.asString ?: ip,
                        org = json.get("org")?.asString ?: (json.get("isp")?.asString ?: "Неизвестно"),
                        asn = json.get("as")?.asString ?: "",
                        country = json.get("country")?.asString ?: "Неизвестно",
                        countryCode = json.get("countryCode")?.asString ?: "",
                        city = json.get("city")?.asString ?: ""
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun probeHttpHeaders(domain: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val interestingHeaders = setOf(
            "server", "cf-ray", "cf-cache-status", "x-amz-cf-id", "x-amz-cf-pop",
            "x-served-by", "fastly-debug-digest", "x-akamai-transformed",
            "x-akamai-request-id", "x-cdn", "x-azure-ref", "b-cache", "x-iinfo",
            "x-cache", "via"
        )

        for (protocol in listOf("https", "http")) {
            try {
                val url = URL("$protocol://$domain")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }

                val headerFields = conn.headerFields
                for ((key, values) in headerFields) {
                    if (key != null) {
                        val lowerKey = key.lowercase(Locale.ROOT)
                        if (interestingHeaders.contains(lowerKey) || lowerKey.startsWith("cf-") || lowerKey.startsWith("x-amz-") || lowerKey.startsWith("x-cdn")) {
                            results.add(key to values.joinToString(", "))
                        }
                    }
                }
                if (results.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        return results
    }

    private data class CdnDef(
        val name: String,
        val cnameKeywords: List<String> = emptyList(),
        val asnKeywords: List<String> = emptyList(),
        val orgKeywords: List<String> = emptyList(),
        val headerSignatures: List<String> = emptyList(),
        val serverKeyword: String = ""
    )
}
