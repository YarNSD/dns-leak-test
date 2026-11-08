package com.dnsleak.test.model

data class ServiceDomainItem(
    val id: String,
    val name: String,
    val primaryDomain: String,
    val domains: List<String>,
    val aliases: List<String> = emptyList(),
    val geosites: List<String> = emptyList(),
    val geoips: List<String> = emptyList()
)
