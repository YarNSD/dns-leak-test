package com.dnsleak.test.model

data class DnsServerItem(
    val ip: String,
    val isp: String,
    val country: String,
    val countryCode: String,
    val sourceAttribution: String,
    val isIspDns: Boolean,
    val isLeak: Boolean,
    val statusText: String
)
