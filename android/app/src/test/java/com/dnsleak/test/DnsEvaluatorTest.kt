package com.dnsleak.test

import com.dnsleak.test.model.DnsHijackStatus
import com.dnsleak.test.model.DnsLeakResult
import com.dnsleak.test.model.DnsServerItem
import com.dnsleak.test.model.LocalDnsInfo
import com.dnsleak.test.util.CountryUtils
import com.dnsleak.test.util.DnsEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsEvaluatorTest {

    @Test
    fun `countryCodeToEmoji returns flag for valid ISO code`() {
        assertEquals("🇸🇪", CountryUtils.countryCodeToEmoji("SE"))
        assertEquals("🇷🇺", CountryUtils.countryCodeToEmoji("ru"))
        assertEquals("", CountryUtils.countryCodeToEmoji(""))
        assertEquals("", CountryUtils.countryCodeToEmoji("RUS"))
    }

    @Test
    fun `parseGeoString splits country and isp`() {
        val (country, isp) = CountryUtils.parseGeoString("Sweden - Cloudflare, Inc.")
        assertEquals("Sweden", country)
        assertEquals("Cloudflare, Inc.", isp)
    }

    @Test
    fun `isRussian detects Russian resolvers and IX`() {
        assertTrue(CountryUtils.isRussian("Russia", "Rostelecom"))
        assertTrue(CountryUtils.isRussian("", "MSK-IX"))
        assertFalse(CountryUtils.isRussian("Sweden", "Cloudflare, Inc."))
    }

    @Test
    fun `evaluateDnsServer marks Wi-Fi with Russian DNS as warning not leak`() {
        val localDns = LocalDnsInfo(isWifi = true, isCellular = false, isVpnActive = false)
        val eval = DnsEvaluator.evaluateDnsServer("Rostelecom", "Russia", "Rostelecom", "Russia", localDns)
        assertFalse(eval.isLeak)
        assertTrue(eval.isWarning)
    }

    @Test
    fun `evaluateDnsServer marks Wi-Fi with foreign DNS as safe`() {
        val localDns = LocalDnsInfo(isWifi = true, isCellular = false, isVpnActive = false)
        val eval = DnsEvaluator.evaluateDnsServer("Cloudflare, Inc.", "Sweden", "Rostelecom", "Russia", localDns)
        assertFalse(eval.isLeak)
        assertFalse(eval.isWarning)
    }

    @Test
    fun `evaluateDnsServer marks Cellular with Russian DNS as leak`() {
        val localDns = LocalDnsInfo(isWifi = false, isCellular = true, isVpnActive = false)
        val eval = DnsEvaluator.evaluateDnsServer("MTS PJSC", "Russia", "MTS PJSC", "Russia", localDns)
        assertTrue(eval.isLeak)
    }

    @Test
    fun `evaluateDnsServer marks VPN with Russian DNS and foreign client IP as leak`() {
        val localDns = LocalDnsInfo(isWifi = true, isCellular = false, isVpnActive = true)
        val eval = DnsEvaluator.evaluateDnsServer("Rostelecom", "Russia", "DigitalOcean", "Germany", localDns)
        assertTrue(eval.isLeak)
    }

    @Test
    fun `evaluateDnsServer marks Private DNS with Cloudflare as safe`() {
        val localDns = LocalDnsInfo(
            isWifi = false,
            isCellular = true,
            isVpnActive = false,
            privateDnsMode = "hostname",
            privateDnsHostname = "1dot1dot1dot1.cloudflare-dns.com"
        )
        val eval = DnsEvaluator.evaluateDnsServer("Cloudflare, Inc.", "Germany", "MTS", "Russia", localDns)
        assertFalse(eval.isLeak)
        assertFalse(eval.isWarning)
    }

    @Test
    fun `isSpoofedIpSet detects bogon and mismatched IPs`() {
        val expected = setOf("1.1.1.1", "1.0.0.1")
        assertFalse(DnsEvaluator.isSpoofedIpSet(listOf("1.1.1.1"), expected))
        assertTrue(DnsEvaluator.isSpoofedIpSet(listOf("127.0.0.1"), expected))
        assertTrue(DnsEvaluator.isSpoofedIpSet(listOf("192.168.1.1"), expected))
        assertTrue(DnsEvaluator.isSpoofedIpSet(listOf("10.0.0.1"), expected))
        assertTrue(DnsEvaluator.isSpoofedIpSet(listOf("172.16.0.5"), expected))
        assertTrue(DnsEvaluator.isSpoofedIpSet(listOf("93.184.216.34"), expected))
        assertFalse(DnsEvaluator.isSpoofedIpSet(emptyList(), expected))
    }

    @Test
    fun `formatLeakReport includes verdict, fallback, and hijack status`() {
        val result = DnsLeakResult(
            clientIp = "1.2.3.4",
            clientIsp = "TestISP",
            clientCountry = "Sweden",
            servers = listOf(
                DnsServerItem(
                    ip = "1.1.1.1",
                    isp = "Cloudflare",
                    country = "Sweden",
                    countryCode = "SE",
                    sourceAttribution = "DoT",
                    isIspDns = false,
                    isLeak = false,
                    statusText = "OK"
                )
            ),
            isOverallLeak = false,
            providerName = "edns.ip-api.com (автозамена)",
            testDurationMs = 150,
            localDnsInfo = LocalDnsInfo(isWifi = true),
            usedFallbackFrom = "ipleak.net",
            hijackStatus = DnsHijackStatus(isChecked = true, isHijacked = false)
        )
        val report = DnsEvaluator.formatLeakReport("Безопасно", result)
        assertTrue(report.contains("Вердикт: Безопасно"))
        assertTrue(report.contains("Авто-фоллбэк: ipleak.net недоступен"))
        assertTrue(report.contains("Подмена DNS: Не обнаружена"))
        assertTrue(report.contains("1.1.1.1 | Cloudflare (Sweden)"))
    }
}
