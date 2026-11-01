package com.dnsleak.test.model

data class DnsProvider(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val pingUrl: String,
    val methodDetails: String,
    val ruAvailability: String
) {
    companion object {
        val ALL_PROVIDERS = listOf(
            DnsProvider(
                id = "bash_ws",
                name = "bash.ws",
                description = "Генерация уникальных probe-доменов для изоляции рекурсивных резолверов",
                url = "https://bash.ws/dnsleak",
                pingUrl = "https://bash.ws",
                methodDetails = "Метод: Отправляет 10 случайных DNS-запросов на уникальные субдомены и опрашивает JSON-эндпоинт о полученных запросах со стороны вышестоящих DNS.",
                ruAvailability = "Доступность в РФ: Высокая. Базовый сервер не находится в реестрах блокировок."
            ),
            DnsProvider(
                id = "ipleak_net",
                name = "ipleak.net (AirVPN)",
                description = "Сервис глубокого сетевого анализа от провайдера AirVPN",
                url = "https://ipleak.net/json/",
                pingUrl = "https://ipleak.net/json/",
                methodDetails = "Метод: Прямой опрос API AirVPN с определением активных транзитных DNS-серверов и сопоставлением с геолокацией клиента.",
                ruAvailability = "Доступность в РФ: Средняя. Иногда подвергается временным замедлениям на мобильных сетях."
            ),
            DnsProvider(
                id = "dnsleaktest_com",
                name = "dnsleaktest.com",
                description = "Классический эталонный сервис детекции DNS-утечек",
                url = "https://dnsleaktest.com",
                pingUrl = "https://dnsleaktest.com",
                methodDetails = "Метод: Резолвинг субдоменов DNSLeakTest с фиксацией IP-адресов серверов имен через распределенные узлы.",
                ruAvailability = "Доступность в РФ: Высокая. Стабильный доступ через стационарных провайдеров."
            ),
            DnsProvider(
                id = "browserleaks_com",
                name = "browserleaks.com",
                description = "Специализированная лаборатория проверки веб-утечек и приватности",
                url = "https://browserleaks.com/dns",
                pingUrl = "https://browserleaks.com",
                methodDetails = "Метод: Комплексное сканирование DNS, WebRTC и сетевого окружения с детальной идентификацией автономных систем (ASN).",
                ruAvailability = "Доступность в РФ: Высокая. Поддерживает работу при любых типах соединения."
            ),
            DnsProvider(
                id = "mullvad_net",
                name = "am.i.mullvad.net",
                description = "Privacy API от шведского VPN-сервиса Mullvad",
                url = "https://am.i.mullvad.net",
                pingUrl = "https://am.i.mullvad.net/json",
                methodDetails = "Метод: Анализ выхода трафика и опрос внутреннего реестра DNS-серверов Mullvad на предмет выхода за пределы безопасного туннеля.",
                ruAvailability = "Доступность в РФ: Переменная. Может блокироваться отдельными магистральными операторами."
            ),
            DnsProvider(
                id = "whoer_net",
                name = "whoer.net",
                description = "Популярный сервис проверки анонимности и сетевых следов",
                url = "https://whoer.net",
                pingUrl = "https://whoer.net",
                methodDetails = "Метод: Детекция публичных и транзитных DNS-серверов с проверкой принадлежности к черным спискам и провайдерам.",
                ruAvailability = "Доступность в РФ: Средняя. Главный домен периодически фильтруется, API работает через альтернативные зеркала."
            ),
            DnsProvider(
                id = "edns_ip_api",
                name = "edns.ip-api.com",
                description = "Анализ клиентской подсети EDNS (ECS) и рекурсивных резолверов",
                url = "http://edns.ip-api.com/json",
                pingUrl = "http://edns.ip-api.com/json",
                methodDetails = "Метод: Проверяет передачу подсети клиента (EDNS Client Subnet) и фиксирует точный адрес DNS-сервера, выполняющего запрос.",
                ruAvailability = "Доступность в РФ: Отличная. Легковесный HTTP API с минимальным временем отклика."
            ),
            DnsProvider(
                id = "incolumitas_com",
                name = "incolumitas.com",
                description = "Исследовательский сервис анализа сетевых отпечатков и DNS-запросов",
                url = "https://incolumitas.com",
                pingUrl = "https://incolumitas.com",
                methodDetails = "Метод: Резолвинг динамических поддоменов с фиксацией промежуточных DNS-прокси и прозрачных перехватчиков трафика.",
                ruAvailability = "Доступность в РФ: Высокая. Редко попадает под фильтрацию."
            ),
            DnsProvider(
                id = "surfshark_com",
                name = "surfshark.com",
                description = "Публичный интерфейс детекции утечек от Surfshark",
                url = "https://surfshark.com",
                pingUrl = "https://surfshark.com",
                methodDetails = "Метод: Отправка probe-сигнатур и сопоставление пула внешних DNS с известными провайдерскими базами данных.",
                ruAvailability = "Доступность в РФ: Низкая/Средняя. В ряде регионов РФ требует проксирования."
            ),
            DnsProvider(
                id = "adguard_dns",
                name = "adguard-dns.io",
                description = "Диагностический API проверки DoH/DoT и защиты от рекламы",
                url = "https://adguard-dns.io",
                pingUrl = "https://api-adguard-dns.org/v1/status",
                methodDetails = "Метод: Проверка активности шифрованных DNS-протоколов (DoH, DoT, DNSCrypt) и фильтрации нежелательных серверов.",
                ruAvailability = "Доступность в РФ: Высокая. Сервера имеют прямое соединение с российскими точками обмена трафиком."
            )
        )

        fun getById(id: String): DnsProvider {
            return ALL_PROVIDERS.find { it.id == id } ?: ALL_PROVIDERS.first()
        }
    }
}
