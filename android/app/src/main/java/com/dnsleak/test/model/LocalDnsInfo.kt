package com.dnsleak.test.model

data class LocalDnsInfo(
    val dnsServers: List<String> = emptyList(),
    val privateDnsMode: String? = null,
    val privateDnsHostname: String? = null,
    val gatewayIp: String? = null,
    val interfaceName: String? = null,
    val isVpnActive: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false
) {
    fun hasActivePrivateDns(): Boolean {
        return !privateDnsHostname.isNullOrBlank() && (privateDnsMode == "hostname" || privateDnsMode == "active")
    }

    fun getNetworkTypeName(): String {
        return when {
            isWifi -> "Wi-Fi сеть"
            isCellular -> "Мобильная сеть (LTE/5G/3G)"
            isVpnActive -> "VPN-соединение"
            else -> "Сетевое подключение"
        }
    }

    /**
     * Основная активная строка с DNS, который реально задействован системой
     */
    fun getActiveDnsSummary(): String {
        return when {
            hasActivePrivateDns() -> {
                "Используется частный DNS на телефоне: $privateDnsHostname"
            }
            privateDnsMode == "opportunistic" -> {
                "Используется частный DNS: Автоматический режим (DoT)"
            }
            isCellular -> {
                val dnsStr = dnsServers.firstOrNull() ?: "Назначен оператором"
                "Используется DNS мобильной сети (оператора связи): $dnsStr"
            }
            isWifi && !gatewayIp.isNullOrBlank() -> {
                "Используется DNS на роутере / шлюзе (Wi-Fi): $gatewayIp"
            }
            !gatewayIp.isNullOrBlank() -> {
                "Используется DNS на роутере / шлюзе: $gatewayIp"
            }
            dnsServers.isNotEmpty() -> {
                "Используется DNS сетевого интерфейса: ${dnsServers.first()}"
            }
            else -> {
                "DNS: Не назначен"
            }
        }
    }

    /**
     * Вторичная строка с информацией о неиспользуемых / переопределенных источниках
     */
    fun getSecondaryDnsSummary(): String {
        return when {
            hasActivePrivateDns() || privateDnsMode == "opportunistic" -> {
                when {
                    isCellular -> {
                        val dnsStr = dnsServers.firstOrNull() ?: "оператора"
                        "DNS мобильной сети ($dnsStr): Не используется (переопределен частным DNS)"
                    }
                    isWifi && !gatewayIp.isNullOrBlank() -> {
                        "DNS роутера / шлюза ($gatewayIp): Не используется (переопределен частным DNS)"
                    }
                    !gatewayIp.isNullOrBlank() -> {
                        "DNS роутера / шлюза ($gatewayIp): Не используется (переопределен частным DNS)"
                    }
                    else -> {
                        "DNS базовой сети: Не используется (переопределен частным DNS)"
                    }
                }
            }
            else -> {
                "Частный DNS на телефоне: Отключен"
            }
        }
    }
}
