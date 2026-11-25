package com.dnsleak.test.model

data class DnsHijackStatus(
    val isChecked: Boolean = false,
    val isHijacked: Boolean = false,
    val details: String = ""
)

data class DnsLeakResult(
    val clientIp: String,
    val clientIsp: String,
    val clientCountry: String,
    val servers: List<DnsServerItem>,
    val isOverallLeak: Boolean,
    val providerName: String,
    val testDurationMs: Long,
    val localDnsInfo: LocalDnsInfo,
    val rawReport: String = "",
    val usedFallbackFrom: String? = null,
    val hijackStatus: DnsHijackStatus = DnsHijackStatus()
)
