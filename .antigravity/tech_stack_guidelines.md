# DNS Leak Test: Tech Stack Guidelines & Bug Journal

## Tech Stack
- **PowerShell**: Windows PowerShell 5.1 / PowerShell 7+ compatible. Native `curl.exe` with `[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]'Tls12, Tls13'`.
- **Android**: Kotlin 1.9+, Gradle 8.7, Android SDK 34 (minSdk 24), OkHttp, Gson.
- **Repository**: GitHub `https://github.com/YarNSD/dns-leak-test`.

## Design Conventions
- Zero emojis in UI (both PowerShell CLI and Android app), except real country flags where relevant.
- High-contrast text, clear status verdicts.

## Bug Journal

### 1. PowerShell 5.1 TLS SSPI Deadlock on bash.ws
- **Symptoms**: Remote script execution hung indefinitely at `[2/2] Получение результатов анализа...` when running `bash.ws`.
- **Root Cause**: The `bash.ws` Apache server performs repeated TLS session renegotiations on chunked transfer-encoding HTTP responses. .NET `HttpWebRequest` / `Invoke-RestMethod` in Windows PowerShell 5.1 locks up in an SSPI TLS handshake deadlock.
- **Resolution**: Replaced `Invoke-SafeHttpJson` to use `curl.exe -L -s -k --max-time 6` with strict timeouts, and completely purged `bash.ws` from the project. Promoted `edns.ip-api.com` to default #1 provider (<200 ms response time).

### 2. Zero Resolvers Detected via bash.ws in Russia
- **Symptoms**: `bash.ws` returned an HTML redirect (`<!DOCTYPE HTML>`) rather than probe JSON data, resulting in empty DNS list `(Список пуст)`.
- **Resolution**: Removed `bash.ws` entirely and implemented an automated fallback mechanism: if any provider returns 0 resolvers, the script falls back to querying `edns.ip-api.com` to guarantee reliable test results.
