[CmdletBinding()]
param(
    [ValidateSet("bash_ws", "ipleak_net", "dnsleaktest_com", "browserleaks_com", "mullvad_net", "whoer_net", "edns_ip_api", "incolumitas_com", "surfshark_com", "adguard_dns")]
    [string]$Provider = "bash_ws",

    [switch]$ShowLocalOnly,
    [switch]$AsJson,
    [switch]$NonInteractive,
    [switch]$TestApis
)

$ProvidersList = @(
    [PSCustomObject]@{ Id = "bash_ws";          Num = 1;  Name = "bash.ws (Рекомендуемый)";               Url = "https://bash.ws/dnsleak"; Description = "Генерация случайных probe-доменов, стабилен в РФ" }
    [PSCustomObject]@{ Id = "ipleak_net";        Num = 2;  Name = "ipleak.net (AirVPN)";                   Url = "https://ipleak.net";      Description = "Детекция резолверов через AirVPN API" }
    [PSCustomObject]@{ Id = "dnsleaktest_com";   Num = 3;  Name = "dnsleaktest.com";                       Url = "https://dnsleaktest.com"; Description = "Классический сервис тестирования утечек" }
    [PSCustomObject]@{ Id = "browserleaks_com";  Num = 4;  Name = "browserleaks.com";                      Url = "https://browserleaks.com";Description = "Надежный сервис для анализа сетевых утечек" }
    [PSCustomObject]@{ Id = "mullvad_net";       Num = 5;  Name = "am.i.mullvad.net";                      Url = "https://am.i.mullvad.net"; Description = "Mullvad Privacy API детекции DNS" }
    [PSCustomObject]@{ Id = "whoer_net";         Num = 6;  Name = "whoer.net";                             Url = "https://whoer.net";       Description = "Проверка конфиденциальности Whoer" }
    [PSCustomObject]@{ Id = "edns_ip_api";       Num = 7;  Name = "edns.ip-api.com";                       Url = "http://edns.ip-api.com";  Description = "Анализ клиентской подсети EDNS и резолверов" }
    [PSCustomObject]@{ Id = "incolumitas_com";   Num = 8;  Name = "incolumitas.com";                       Url = "https://incolumitas.com"; Description = "API детекции рекурсивных DNS-серверов" }
    [PSCustomObject]@{ Id = "surfshark_com";     Num = 9;  Name = "surfshark.com";                         Url = "https://surfshark.com";   Description = "Публичный endpoint проверки от Surfshark" }
    [PSCustomObject]@{ Id = "adguard_dns";       Num = 10; Name = "adguard-dns.io";                       Url = "https://adguard-dns.io";  Description = "Проверка фильтрации и резолверов AdGuard" }
)

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor 3072 -bor 12288

function Invoke-SafeHttpJson {
    param(
        [string]$Uri,
        [int]$TimeoutSec = 6
    )

    if (Get-Command "curl.exe" -ErrorAction SilentlyContinue) {
        try {
            $raw = & "curl.exe" -L -s -k --max-time $TimeoutSec $Uri 2>$null
            if ($raw) {
                $rawStr = ($raw -join "`n").Trim()
                if ($rawStr.StartsWith("{") -or $rawStr.StartsWith("[")) {
                    return ($rawStr | ConvertFrom-Json)
                }
            }
        } catch { }

        if ($Uri.Contains("bash.ws")) {
            return $null
        }
    }

    try {
        $client = New-Object System.Net.Http.HttpClient
        $client.Timeout = [System.TimeSpan]::FromSeconds($TimeoutSec)
        $task = $client.GetStringAsync($Uri)
        if ($task.Wait($TimeoutSec * 1000)) {
            $jsonStr = $task.Result.Trim()
            $client.Dispose()
            if ($jsonStr.StartsWith("{") -or $jsonStr.StartsWith("[")) {
                return ($jsonStr | ConvertFrom-Json)
            }
        }
        $client.Dispose()
    } catch { }

    return $null
}

function Test-AllApisAvailability {
    Clear-Host
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "             ТЕСТИРОВАНИЕ ДОСТУПНОСТИ 10 ПУБЛИЧНЫХ API            " -ForegroundColor Cyan
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Проверка ответа серверов (выявление блокировок РКН / ТСПУ):" -ForegroundColor Yellow
    Write-Host ""

    $apiEndpoints = @(
        @{ Id = "bash_ws";          Name = "bash.ws";              Url = "https://bash.ws/dnsleak" }
        @{ Id = "ipleak_net";        Name = "ipleak.net";           Url = "https://ipleak.net/json/" }
        @{ Id = "dnsleaktest_com";   Name = "dnsleaktest.com";      Url = "https://dnsleaktest.com" }
        @{ Id = "browserleaks_com";  Name = "browserleaks.com";     Url = "https://browserleaks.com" }
        @{ Id = "mullvad_net";       Name = "am.i.mullvad.net";     Url = "https://am.i.mullvad.net/json" }
        @{ Id = "whoer_net";         Name = "whoer.net";            Url = "https://whoer.net" }
        @{ Id = "edns_ip_api";       Name = "edns.ip-api.com";      Url = "http://edns.ip-api.com/json" }
        @{ Id = "incolumitas_com";   Name = "incolumitas.com";      Url = "https://incolumitas.com" }
        @{ Id = "surfshark_com";     Name = "surfshark.com";        Url = "https://surfshark.com" }
        @{ Id = "adguard_dns";       Name = "adguard-dns.io";       Url = "https://adguard-dns.io" }
    )

    $idx = 1
    foreach ($api in $apiEndpoints) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $raw = $null
        if (Get-Command "curl.exe" -ErrorAction SilentlyContinue) {
            $raw = & "curl.exe" -L -s -k --max-time 4 $api.Url 2>$null
        } else {
            $raw = Invoke-SafeHttpJson -Uri $api.Url -TimeoutSec 4
        }
        $sw.Stop()

        $numStr = "{0,2}" -f $idx
        if ($raw -and ($raw.Length -gt 0 -or $raw.Count -gt 0)) {
            Write-Host " [$numStr] " -NoNewline -ForegroundColor Green
            Write-Host ("{0,-22}" -f $api.Name) -NoNewline -ForegroundColor White
            Write-Host "ДОСТУПЕН " -NoNewline -ForegroundColor Green
            Write-Host "($($sw.ElapsedMilliseconds) ms)" -ForegroundColor Gray
        } else {
            Write-Host " [$numStr] " -NoNewline -ForegroundColor Red
            Write-Host ("{0,-22}" -f $api.Name) -NoNewline -ForegroundColor White
            Write-Host "БЛОК / ТАЙМАУТ " -NoNewline -ForegroundColor Red
            Write-Host "($($sw.ElapsedMilliseconds) ms)" -ForegroundColor DarkGray
        }
        $idx++
    }

    Write-Host ""
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    if (-not $NonInteractive) {
        Write-Host "Нажмите Enter для продолжения..." -ForegroundColor Cyan
        Read-Host | Out-Null
    }
}

function Get-LocalDnsConfiguration {
    $adapters = @()
    try {
        $interfaces = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
            Where-Object { $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up -and $_.NetworkInterfaceType -ne [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback }

        foreach ($iface in $interfaces) {
            $props = $iface.GetIPProperties()
            $gateway = ($props.GatewayAddresses | Select-Object -ExpandProperty Address -ErrorAction SilentlyContinue | Where-Object { $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork } | Select-Object -First 1)
            $ipv4List = ($props.UnicastAddresses | Where-Object { $_.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork } | ForEach-Object { $_.Address.ToString() })
            $dnsList = ($props.DnsAddresses | ForEach-Object { $_.ToString() } | Where-Object { $_ -ne "::" -and $_ -ne "0.0.0.0" })
            $gatewayStr = if ($gateway) { $gateway.ToString() } else { $null }

            if (($null -eq $dnsList -or $dnsList.Count -eq 0) -and $gatewayStr) {
                $dnsList = @($gatewayStr)
            }

            $dnsSources = @()
            foreach ($dns in $dnsList) {
                if ($gatewayStr -and $dns -eq $gatewayStr) {
                    $dnsSources += "$dns [Задан на роутере / шлюзе ($gatewayStr)]"
                } elseif ($dns -match "^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)") {
                    $dnsSources += "$dns [Локальная сеть / маршрутизатор]"
                } elseif ($dns -eq "1.1.1.1" -or $dns -eq "1.0.0.1") {
                    $dnsSources += "$dns [Cloudflare Public DNS]"
                } elseif ($dns -eq "8.8.8.8" -or $dns -eq "8.8.4.4") {
                    $dnsSources += "$dns [Google Public DNS]"
                } elseif ($dns -eq "9.9.9.9") {
                    $dnsSources += "$dns [Quad9 DNS]"
                } elseif ($dns -eq "77.88.8.8" -or $dns -eq "77.88.8.1") {
                    $dnsSources += "$dns [Yandex Public DNS]"
                } elseif ($dns -eq "94.140.14.14" -or $dns -eq "94.140.15.15") {
                    $dnsSources += "$dns [AdGuard Public DNS]"
                } else {
                    $dnsSources += "$dns [Статический / настроен на адаптере]"
                }
            }

            $isVpn = ($iface.Name -match "TAP|TUN|WireGuard|OpenVPN|Wintun|vEthernet|VPN" -or $iface.Description -match "TAP|TUN|WireGuard|OpenVPN|Wintun")

            $adapters += [PSCustomObject]@{
                InterfaceAlias = $iface.Name
                Description    = $iface.Description
                IPv4           = ($ipv4List -join ", ")
                Gateway        = $gatewayStr
                DnsServers     = $dnsList
                DnsAttribution = $dnsSources
                IsVpn          = $isVpn
            }
        }
    } catch { }

    return $adapters
}

function Show-InteractiveMenu {
    Clear-Host
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "                    DNS LEAK TEST ДЛЯ WINDOWS                     " -ForegroundColor Cyan
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Выберите API провайдера для проверки (1-10):" -ForegroundColor Yellow
    Write-Host ""

    foreach ($item in $ProvidersList) {
        $numStr = "{0,2}" -f $item.Num
        Write-Host " [$numStr] " -NoNewline -ForegroundColor Green
        Write-Host ("{0,-30}" -f $item.Name) -NoNewline -ForegroundColor White
        Write-Host " - $($item.Description)" -ForegroundColor Gray
    }

    Write-Host ""
    Write-Host " [ T]  Проверить доступность всех 10 API (Тест на блокировки)" -ForegroundColor Magenta
    Write-Host " [ L]  Показать только локальные DNS-настройки адаптеров и роутера" -ForegroundColor DarkCyan
    Write-Host " [ Q]  Выход" -ForegroundColor Red
    Write-Host ""

    $choice = Read-Host "Введите номер (по умолчанию 1)"
    if ([string]::IsNullOrWhiteSpace($choice)) { return "bash_ws" }
    if ($choice -match "^(q|quit|exit)$") { exit 0 }
    if ($choice -match "^(l|local)$") { return "LOCAL_ONLY" }
    if ($choice -match "^(t|test)$") {
        Test-AllApisAvailability
        return Show-InteractiveMenu
    }

    $num = [int]::TryParse($choice, [ref]$null)
    if ($num) {
        $selected = $ProvidersList | Where-Object { $_.Num -eq [int]$choice }
        if ($selected) { return $selected.Id }
    }

    return "bash_ws"
}

function Invoke-DnsLeakTest {
    param(
        [string]$SelectedProviderId,
        [array]$LocalAdapters
    )

    $providerObj = $ProvidersList | Where-Object { $_.Id -eq $SelectedProviderId }
    if (-not $providerObj) { $providerObj = $ProvidersList[0] }

    $clientIp = "Неизвестно"
    $clientIsp = "Неизвестно"
    $clientCountry = "Неизвестно"
    $dnsServers = @()

    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        switch ($SelectedProviderId) {
            "bash_ws" {
                $testId = [System.Guid]::NewGuid().ToString().Replace("-", "").Substring(0, 10)
                Write-Host "  [1/3] Отправка DNS probe-запросов к bash.ws..." -ForegroundColor DarkGray
                1..6 | ForEach-Object {
                    $probeHost = "$_.$testId.bash.ws"
                    try { [System.Net.Dns]::GetHostAddresses($probeHost) | Out-Null } catch { }
                }

                Write-Host "  [2/3] Ожидание регистрации на сервере..." -ForegroundColor DarkGray
                Start-Sleep -Seconds 2

                Write-Host "  [3/3] Загрузка результатов..." -ForegroundColor DarkGray
                $resp = Invoke-SafeHttpJson -Uri "https://bash.ws/dnsleak/test/$testId?json" -TimeoutSec 7
                if ($null -eq $resp -or $resp.error) {
                    Write-Host "  Повторный запрос через 1.5 сек..." -ForegroundColor DarkGray
                    Start-Sleep -Milliseconds 1500
                    $resp = Invoke-SafeHttpJson -Uri "https://bash.ws/dnsleak/test/$testId?json" -TimeoutSec 7
                }

                if ($resp.error) {
                    Write-Warning "Сервер bash.ws вернул: $($resp.error)"
                } elseif ($resp) {
                    foreach ($item in $resp) {
                        if ($item.type -eq "ip") {
                            $clientIp = $item.ip
                            $clientCountry = $item.country_name
                            $clientIsp = "$($item.asn) $($item.as_org)".Trim()
                        } elseif ($item.type -eq "dns") {
                            $dnsServers += [PSCustomObject]@{
                                Ip      = $item.ip
                                Isp     = "$($item.asn) $($item.as_org)".Trim()
                                Country = "$($item.country_name) ($($item.country))"
                            }
                        }
                    }
                }
            }

            "edns_ip_api" {
                Write-Host "  Запрос к edns.ip-api.com..." -ForegroundColor DarkGray
                $resp = Invoke-SafeHttpJson -Uri "http://edns.ip-api.com/json" -TimeoutSec 6
                if ($resp.dns) {
                    $country = "Неизвестно"
                    $isp = "Неизвестно"
                    if ($resp.dns.geo) {
                        $geoParts = $resp.dns.geo -split " - "
                        $country = $geoParts[0]
                        if ($geoParts.Count -gt 1) { $isp = $geoParts[1] } else { $isp = $country }
                    } elseif ($resp.dns.isp) {
                        $isp = $resp.dns.isp
                        if ($resp.dns.country) { $country = $resp.dns.country }
                    }
                    $dnsServers += [PSCustomObject]@{
                        Ip      = $resp.dns.ip
                        Isp     = $isp
                        Country = $country
                    }
                }
                if ($resp.client) {
                    $clientIp = $resp.client.ip
                    $clientIsp = $resp.client.isp
                    $clientCountry = $resp.client.country
                }
            }

            "ipleak_net" {
                Write-Host "  Запрос к ipleak.net..." -ForegroundColor DarkGray
                $resp = Invoke-SafeHttpJson -Uri "https://ipleak.net/json/" -TimeoutSec 6
                if ($resp) {
                    $clientIp = $resp.ip
                    $clientIsp = $resp.isp_name
                    $clientCountry = "$($resp.country_name) ($($resp.country_code))"
                    
                    if ($resp.dns_resolvers) {
                        $resp.dns_resolvers.PSObject.Properties | ForEach-Object {
                            $val = $_.Value
                            $dnsServers += [PSCustomObject]@{
                                Ip      = $val.ip
                                Isp     = $val.isp_name
                                Country = "$($val.country_name) ($($val.country_code))"
                            }
                        }
                    }
                }
            }

            "mullvad_net" {
                Write-Host "  Запрос к am.i.mullvad.net..." -ForegroundColor DarkGray
                $resp = Invoke-SafeHttpJson -Uri "https://am.i.mullvad.net/json" -TimeoutSec 6
                if ($resp) {
                    $clientIp = $resp.ip
                    $clientIsp = $resp.organization
                    $clientCountry = $resp.country
                }

                try {
                    $dnsResp = Invoke-SafeHttpJson -Uri "https://am.i.mullvad.net/dnsleak" -TimeoutSec 6
                    foreach ($d in $dnsResp) {
                        $dnsServers += [PSCustomObject]@{
                            Ip      = $d.ip
                            Isp     = $d.organization
                            Country = $d.country
                        }
                    }
                } catch { }
            }

            default {
                $testId = [System.Guid]::NewGuid().ToString().Replace("-", "").Substring(0, 10)
                Write-Host "  [1/3] Отправка DNS probe-запросов..." -ForegroundColor DarkGray
                1..6 | ForEach-Object {
                    $probeHost = "$_.$testId.bash.ws"
                    try { [System.Net.Dns]::GetHostAddresses($probeHost) | Out-Null } catch { }
                }
                Write-Host "  [2/3] Ожидание регистрации на сервере..." -ForegroundColor DarkGray
                Start-Sleep -Seconds 2
                Write-Host "  [3/3] Загрузка результатов..." -ForegroundColor DarkGray
                $resp = Invoke-SafeHttpJson -Uri "https://bash.ws/dnsleak/test/$testId?json" -TimeoutSec 7
                if ($null -eq $resp -or $resp.error) {
                    Start-Sleep -Milliseconds 1500
                    $resp = Invoke-SafeHttpJson -Uri "https://bash.ws/dnsleak/test/$testId?json" -TimeoutSec 7
                }
                if ($resp.error) {
                    Write-Warning "Сервер bash.ws вернул: $($resp.error)"
                } elseif ($resp) {
                    foreach ($item in $resp) {
                        if ($item.type -eq "ip") {
                            $clientIp = $item.ip
                            $clientCountry = $item.country_name
                            $clientIsp = "$($item.asn) $($item.as_org)".Trim()
                        } elseif ($item.type -eq "dns") {
                            $dnsServers += [PSCustomObject]@{
                                Ip      = $item.ip
                                Isp     = "$($item.asn) $($item.as_org)".Trim()
                                Country = "$($item.country_name) ($($item.country))"
                            }
                        }
                    }
                }
            }
        }
    } catch {
        Write-Warning "Ошибка обращения к провайдеру ($($providerObj.Name)): $($_.Exception.Message)"
    }

    $sw.Stop()

    $ruIspKeywords = @(
        "rostelecom", "ростелеком", "rtcomm", "mts", "мтс", "mobile telesystems",
        "megafon", "мегафон", "netbynet", "vimpelcom", "вымпелком", "beeline", "билайн",
        "er-telecom", "эр-телеком", "dom.ru", "дом.ru", "transtelecom", "транстелеком", "ttk",
        "ufanet", "уфанет", "akado", "акадо", "comcor", "tattelecom", "таттелеком",
        "mgts", "мгтс", "yota", "йота", "skartel", "скартел", "tele2", "теле2"
    )

    $secureDnsKeywords = @(
        "cloudflare", "google", "quad9", "adguard", "mullvad", "opendns", "cisco",
        "nextdns", "control d", "cleanbrowsing", "dns.sb"
    )

    $isVpnActive = ($LocalAdapters | Where-Object { $_.IsVpn }).Count -gt 0
    $analyzedDns = @()
    $hasLeak = $false

    foreach ($s in $dnsServers) {
        $source = "Источник: Внешний независимый резолвер"
        
        foreach ($adp in $LocalAdapters) {
            if ($adp.Gateway -and $s.Ip -eq $adp.Gateway) {
                $source = "Источник: Задан на роутере ($($adp.Gateway))"
            }
            if ($adp.DnsServers -contains $s.Ip) {
                $source = "Источник: Настроен на адаптере $($adp.InterfaceAlias) ($($s.Ip))"
            }
        }

        $sIspLower = if ($s.Isp) { $s.Isp.ToLower() } else { "" }
        $clientIspLower = if ($clientIsp) { $clientIsp.ToLower() } else { "" }

        $isSecure = ($secureDnsKeywords | Where-Object { $sIspLower.Contains($_) }).Count -gt 0
        $isKnownRuIsp = ($ruIspKeywords | Where-Object { $sIspLower.Contains($_) }).Count -gt 0
        $matchesClientIsp = ($clientIspLower.Length -gt 0 -and $clientIspLower -ne "неизвестно" -and ($sIspLower.Contains($clientIspLower) -or $clientIspLower.Contains($sIspLower)))

        $isIspDns = ($isKnownRuIsp -or $matchesClientIsp) -and (-not $isSecure)

        $serverLeak = $false
        if ($isVpnActive) {
            if ($isIspDns -or $matchesClientIsp -or ($s.Country -match "Russia|RU" -and $clientCountry -notmatch "Russia|RU")) {
                $serverLeak = $true
            }
        } else {
            $serverLeak = $isIspDns
        }

        if ($serverLeak) { $hasLeak = $true }

        $statusDesc = if ($serverLeak -and $isVpnActive) {
            "УТЕЧКА: Используется DNS интернет-провайдера ($($s.Isp))"
        } elseif ($isIspDns) {
            "ВНИМАНИЕ: Используется DNS интернет-провайдера ($($s.Isp))"
        } elseif ($isSecure) {
            "БЕЗОПАСНО: DNS провайдера НЕ используется ($($s.Isp))"
        } else {
            "БЕЗОПАСНО: Внешний независимый резолвер ($($s.Isp))"
        }

        $analyzedDns += [PSCustomObject]@{
            Ip                 = $s.Ip
            Isp                = $s.Isp
            Country            = $s.Country
            SourceAttribution  = $source
            IsIspDns           = $isIspDns
            IsLeak             = $serverLeak
            StatusText         = $statusDesc
        }
    }

    return [PSCustomObject]@{
        ProviderName   = $providerObj.Name
        DurationMs     = $sw.ElapsedMilliseconds
        ClientIp       = $clientIp
        ClientIsp      = $clientIsp
        ClientCountry  = $clientCountry
        IsVpnActive    = $isVpnActive
        HasLeak        = $hasLeak
        DnsServers     = $analyzedDns
        LocalAdapters  = $LocalAdapters
    }
}

if ($TestApis) {
    Test-AllApisAvailability
    exit 0
}

$selectedProvider = $Provider
if (-not $NonInteractive -and -not $ShowLocalOnly -and $PSBoundParameters.ContainsKey("Provider") -eq $false) {
    $userChoice = Show-InteractiveMenu
    if ($userChoice -eq "LOCAL_ONLY") {
        $ShowLocalOnly = $true
    } else {
        $selectedProvider = $userChoice
    }
}

$localAdapters = Get-LocalDnsConfiguration

if ($ShowLocalOnly) {
    if ($AsJson) {
        $localAdapters | ConvertTo-Json -Depth 4
        exit 0
    }

    Write-Host ""
    Write-Host "=== ЛОКАЛЬНАЯ КОНФИГУРАЦИЯ СЕТЕВЫХ АДАПТЕРОВ И DNS ===" -ForegroundColor Cyan
    Write-Host ""
    foreach ($adp in $localAdapters) {
        $vpnTag = if ($adp.IsVpn) { "[VPN ИНТЕРФЕЙС]" } else { "[ФИЗИЧЕСКИЙ АДАПТЕР]" }
        Write-Host "Адаптер: $($adp.InterfaceAlias) $vpnTag" -ForegroundColor Yellow
        Write-Host "  Описание: $($adp.Description)" -ForegroundColor Gray
        Write-Host "  IPv4:     $($adp.IPv4)" -ForegroundColor White
        Write-Host "  Шлюз:     $(if ($adp.Gateway) { $adp.Gateway } else { 'Не назначен' })" -ForegroundColor White
        Write-Host "  DNS-серверы:" -ForegroundColor White
        if ($adp.DnsAttribution.Count -eq 0) {
            Write-Host "    (DNS не назначен)" -ForegroundColor DarkGray
        } else {
            foreach ($attr in $adp.DnsAttribution) {
                Write-Host "    - $attr" -ForegroundColor Green
            }
        }
        Write-Host ""
    }
    exit 0
}

Write-Host ""
Write-Host "Запуск проверки утечки DNS через: $selectedProvider..." -ForegroundColor Cyan
$result = Invoke-DnsLeakTest -SelectedProviderId $selectedProvider -LocalAdapters $localAdapters

if ($AsJson) {
    $result | ConvertTo-Json -Depth 5
    exit 0
}

Write-Host ""
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "                     РЕЗУЛЬТАТЫ ПРОВЕРКИ DNS                      " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""

if ($result.DnsServers.Count -eq 0) {
    Write-Host "ВЕРДИКТ: РЕЗОЛВЕРЫ НЕ ОБНАРУЖЕНЫ (Проверьте подключение к сети)" -ForegroundColor Yellow
} elseif ($result.HasLeak) {
    Write-Host "ВЕРДИКТ: ОБНАРУЖЕНА УТЕЧКА DNS!" -ForegroundColor Red
    Write-Host "Запросы направляются к серверам вашего интернет-провайдера в обход туннеля." -ForegroundColor Red
} else {
    Write-Host "ВЕРДИКТ: БЕЗОПАСНО. УТЕЧКИ DNS НЕТ." -ForegroundColor Green
    Write-Host "Все DNS-запросы направляются через защищенные внешние серверы." -ForegroundColor Green
}

Write-Host ""
Write-Host "Информация о клиенте:" -ForegroundColor Yellow
Write-Host "  Внешний IP:       $($result.ClientIp)" -ForegroundColor White
Write-Host "  Провайдер / ASN:  $($result.ClientIsp)" -ForegroundColor White
Write-Host "  Локация:          $($result.ClientCountry)" -ForegroundColor White
Write-Host "  Статус VPN:       $(if ($result.IsVpnActive) { 'Активен' } else { 'Не активен' })" -ForegroundColor White
Write-Host "  Использован API:  $($result.ProviderName) ($($result.DurationMs) ms)" -ForegroundColor DarkGray
Write-Host ""

Write-Host "Локальная конфигурация DNS:" -ForegroundColor Yellow
foreach ($adp in $result.LocalAdapters) {
    if ($adp.DnsAttribution.Count -gt 0) {
        Write-Host "  $($adp.InterfaceAlias): $($adp.DnsAttribution -join '; ')" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Обнаруженные DNS-серверы ($($result.DnsServers.Count)):" -ForegroundColor Yellow
Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray

if ($result.DnsServers.Count -eq 0) {
    Write-Host "  (Список пуст)" -ForegroundColor DarkGray
} else {
    $idx = 1
    foreach ($server in $result.DnsServers) {
        $color = if ($server.IsLeak) { "Red" } else { "Green" }
        Write-Host "  [$idx] IP-адрес:   $($server.Ip)" -ForegroundColor White
        Write-Host "      Провайдер:  $($server.Isp)" -ForegroundColor Gray
        Write-Host "      Локация:    $($server.Country)" -ForegroundColor Gray
        Write-Host "      $($server.SourceAttribution)" -ForegroundColor Cyan
        Write-Host "      Статус:     $($server.StatusText)" -ForegroundColor $color
        Write-Host ""
        $idx++
    }
}
Write-Host "==================================================================" -ForegroundColor Cyan
