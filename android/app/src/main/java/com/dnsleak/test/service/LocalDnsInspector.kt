package com.dnsleak.test.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import android.provider.Settings
import com.dnsleak.test.model.LocalDnsInfo

class LocalDnsInspector(private val context: Context) {

    fun inspect(): LocalDnsInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        var dnsServers = emptyList<String>()
        var gatewayIp: String? = null
        var interfaceName: String? = null
        var isVpnActive = false
        var isWifi = false
        var isCellular = false
        var isLinkPrivateDnsActive: Boolean? = null
        var linkPrivateDnsServerName: String? = null

        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (capabilities != null) {
                    isVpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                }

                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    interfaceName = linkProperties.interfaceName
                    dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress }

                    val defaultRoute: RouteInfo? = linkProperties.routes.firstOrNull { it.isDefaultRoute }
                    gatewayIp = defaultRoute?.gateway?.hostAddress

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        isLinkPrivateDnsActive = linkProperties.isPrivateDnsActive
                        linkPrivateDnsServerName = linkProperties.privateDnsServerName
                    }
                }
            }
        }

        var privateDnsMode: String
        var privateDnsHostname: String?

        try {
            val resolver = context.contentResolver
            val rawMode = Settings.Global.getString(resolver, "private_dns_mode")
            val rawSpecifier = Settings.Global.getString(resolver, "private_dns_specifier")

            when (rawMode?.lowercase()) {
                "off" -> {
                    privateDnsMode = "off"
                    privateDnsHostname = null
                }
                "opportunistic" -> {
                    privateDnsMode = "opportunistic"
                    privateDnsHostname = null
                }
                "hostname" -> {
                    if (!rawSpecifier.isNullOrBlank()) {
                        privateDnsMode = "hostname"
                        privateDnsHostname = rawSpecifier.trim()
                    } else {
                        privateDnsMode = "off"
                        privateDnsHostname = null
                    }
                }
                else -> {
                    if (isLinkPrivateDnsActive == true) {
                        privateDnsMode = if (!linkPrivateDnsServerName.isNullOrBlank()) "hostname" else "opportunistic"
                        privateDnsHostname = linkPrivateDnsServerName
                    } else {
                        privateDnsMode = "off"
                        privateDnsHostname = null
                    }
                }
            }
        } catch (_: Exception) {
            if (isLinkPrivateDnsActive == true) {
                privateDnsMode = "active"
                privateDnsHostname = linkPrivateDnsServerName
            } else {
                privateDnsMode = "off"
                privateDnsHostname = null
            }
        }

        if (isLinkPrivateDnsActive == false) {
            privateDnsMode = "off"
            privateDnsHostname = null
        }

        return LocalDnsInfo(
            dnsServers = dnsServers,
            privateDnsMode = privateDnsMode,
            privateDnsHostname = privateDnsHostname,
            gatewayIp = gatewayIp,
            interfaceName = interfaceName,
            isVpnActive = isVpnActive,
            isWifi = isWifi,
            isCellular = isCellular
        )
    }
}
