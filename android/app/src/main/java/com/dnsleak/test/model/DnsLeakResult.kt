package com.dnsleak.test.model

data class DnsLeakResult(
    val clientIp: String,
    val clientIsp: String,
    val clientCountry: String,
    val servers: List<DnsServerItem>,
    val isOverallLeak: Boolean,
    val providerName: String,
    val testDurationMs: Long,
    val localDnsInfo: LocalDnsInfo,
    val rawReport: String = ""
)
