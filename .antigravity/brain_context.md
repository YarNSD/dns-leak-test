# DNS Leak Test: Strategic Brain Context

## Project Mission
High-speed cross-platform DNS leak detection utility for Windows (PowerShell standalone script) and Android (Native Kotlin APK).
Accurately detects whether DNS queries are hijacked by Russian ISPs (Rostelecom, MTS, Beeline, Megafon, Dom.ru, etc.), whether DoH/DoT/Private DNS functions correctly, or if queries still leak to the default router gateway.

## Core Capabilities
1. **Windows PowerShell Engine (`Test-DnsLeak.ps1`)**:
   - Single-line remote invocation via `irm https://raw.githubusercontent.com/YarNSD/dns-leak-test/main/Test-DnsLeak.ps1 | iex`.
   - 9 public leak testing APIs with `edns.ip-api.com` as lightning-fast #1 default (<200 ms).
   - Automated fallback to `edns.ip-api.com` if any provider returns 0 resolvers.
   - Detects local network adapter DNS, router gateway (192.168.x.x), client public IP, ASN/ISP name, and evaluates leak status.
   - Zero emojis policy in UI, clean ASCII borders, color-coded status verdicts.

2. **Android Application (`android/`)**:
   - Native Kotlin + MVVM architecture, ultra-modern dark theme (`#0B0F17`, rounded squircles, circular icon pods).
   - Floating capsule bottom navigation bar (`#151C28` pill with active cyan `#38BDF8` pod) switching between 3 core tools:
     1. **Утечка DNS**: Full leak test, active interface detection, Private DNS status, DoH/DoT detection.
     2. **Проверка CDN**: CNAME chain resolution, reverse CDN IP ASN classification (Cloudflare, Akamai, Fastly, GCore, etc.).
     3. **Домены сервисов**: Instant autocomplete search across services from Loyalsoldier database, interactive CDN badges, scrollable domain column, one-tap copy.
   - Single universal APK architecture ensuring 100% compatibility across all Android devices.
   - Contextual educational help dialogs ("ликбез простым языком") on question mark icons across all blocks.
   - RuleSets download section for Loyalsoldier `geosite.dat` & `geoip.dat` with routing tags copying.
   - Built-in latency ping tester for all 9 APIs to identify blocked endpoints.

3. **CI/CD & Releases**:
   - GitHub Releases (`v1.0.0` and latest `v1.2.0`).
   - `version.json` in repository for in-app OTA update checks.
