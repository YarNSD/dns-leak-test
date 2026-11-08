package com.dnsleak.test.model

data class CdnCheckResult(
    val domain: String,
    val cdnServiceName: String,
    val isCdnDetected: Boolean,
    val detectionMethod: String,
    val cdnDomains: List<String> = emptyList(),
    val edgeIps: List<CdnEdgeIp> = emptyList(),
    val evidenceHeaders: List<Pair<String, String>> = emptyList(),
    val errorMessage: String? = null
)

data class CdnEdgeIp(
    val ip: String,
    val org: String,
    val asn: String,
    val country: String,
    val countryCode: String,
    val city: String
)
