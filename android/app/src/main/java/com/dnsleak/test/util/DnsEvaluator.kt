package com.dnsleak.test.util

import com.dnsleak.test.model.DnsLeakResult
import com.dnsleak.test.model.LocalDnsInfo

data class DnsEvaluation(
    val isIspDns: Boolean,
    val isLeak: Boolean,
    val isWarning: Boolean,
    val statusText: String
)

/**
 * Pure, Android-independent DNS evaluation, hijack-check, and report-formatting helpers.
 */
object DnsEvaluator {

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

    fun evaluateDnsServer(
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
            localDns.isVpnActive -> {
                isLeak = isIspDns || matchesClientIsp || (isRussian && !clientCountry.equals("Russia", ignoreCase = true))
                isWarning = false
            }
            hasPrivateDns -> {
                isLeak = isIspDns && !isSecureDns
                isWarning = false
            }
            localDns.isWifi -> {
                isLeak = false
                isWarning = isRussian
            }
            localDns.isCellular -> {
                isLeak = isIspDns || isRussian
                isWarning = false
            }
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

    /**
     * Compares IPv4 addresses returned by the system DNS against expected anycast/DoH IPv4 addresses.
     * Returns true if system DNS returned at least one IP and none match expected IPs (or returned a bogon/private IP).
     */
    fun isSpoofedIpSet(systemIps: List<String>, expectedIps: Set<String>): Boolean {
        if (systemIps.isEmpty() || expectedIps.isEmpty()) return false
        val hasBogonOrPrivate = systemIps.any { ip ->
            ip == "0.0.0.0" ||
                ip.startsWith("127.") ||
                ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                isPrivate172(ip)
        }
        if (hasBogonOrPrivate) return true
        return systemIps.none { it in expectedIps }
    }

    private fun isPrivate172(ip: String): Boolean {
        if (!ip.startsWith("172.")) return false
        val second = ip.split(".").getOrNull(1)?.toIntOrNull() ?: return false
        return second in 16..31
    }

    fun formatLeakReport(verdictTitle: String, result: DnsLeakResult): String {
        val sb = StringBuilder()
        sb.append("DNS Leak Test — Отчёт\n")
        sb.append("Вердикт: ").append(verdictTitle).append("\n")
        sb.append("API теста: ").append(result.providerName)
            .append(" (").append(result.testDurationMs).append(" мс)\n")
        if (!result.usedFallbackFrom.isNullOrBlank()) {
            sb.append("Авто-фоллбэк: ").append(result.usedFallbackFrom).append(" недоступен\n")
        }
        val vpnLabel = if (result.localDnsInfo.isVpnActive) {
            "Да (${result.localDnsInfo.interfaceName ?: "VPN"})"
        } else "Нет"
        sb.append("Сеть: ").append(result.localDnsInfo.getNetworkTypeName())
            .append(" | VPN: ").append(vpnLabel).append("\n")
        sb.append("Локальный DNS: ").append(result.localDnsInfo.getActiveDnsSummary()).append("\n")
        sb.append("Клиент: ").append(result.clientIp)
            .append(" (").append(result.clientIsp).append(", ").append(result.clientCountry).append(")\n")
        if (result.hijackStatus.isChecked) {
            val hijackStr = if (result.hijackStatus.isHijacked) {
                "ОБНАРУЖЕНА (${result.hijackStatus.details})"
            } else {
                "Не обнаружена"
            }
            sb.append("Подмена DNS: ").append(hijackStr).append("\n")
        }
        sb.append("Резолверы (").append(result.servers.size).append("):\n")
        if (result.servers.isEmpty()) {
            sb.append("- Не обнаружены\n")
        } else {
            for (s in result.servers) {
                sb.append("- ").append(s.ip)
                    .append(" | ").append(s.isp)
                    .append(" (").append(s.country.ifBlank { s.countryCode }).append(")\n")
            }
        }
        return sb.toString().trimEnd()
    }
}
