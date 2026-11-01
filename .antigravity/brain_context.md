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
   - Native Kotlin + MVVM architecture, clean Material UI.
   - Minimalist top bar: wide "Проверка" button + gear settings button.
   - Active connection detection (Wi-Fi router vs Cellular LTE/5G).
   - Android Private DNS (DoT) system status detection.
   - Built-in latency ping tester for all 9 APIs to identify blocked endpoints.
   - ABI splits (`arm64-v8a`, `armeabi-v7a`, `universal`).

3. **CI/CD & Releases**:
   - Automated GitHub releases with ABI split APKs.
   - `version.json` in repository for in-app OTA update checks.
