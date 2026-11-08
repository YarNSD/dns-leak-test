<p align="center">
  <img src="media/icon.png" width="220" alt="DNS Leak Test Icon">
</p>

# DNS Leak Test & CDN Inspector

Комплексный инструмент сетевой диагностики, анализа утечек DNS, определения CDN-инфраструктуры и поиска доменов сервисов для Android и Windows.

Основная цель проекта — быстро и точно определить, перехватываются ли ваши DNS-запросы интернет-провайдером, выявить использование проксирующих сетей доставки контента (CDN) и предоставить полные списки доменов и RuleSets для тонкой настройки маршрутизации (Xray, Sing-box, V2Ray, Clash).

## Возможности

### Android приложение (APK):

- **1. Проверка утечки DNS (DNS Leak Test):**
  - Определение активного типа подключения (Wi-Fi роутер vs мобильная сеть LTE/5G).
  - Проверка статуса системного Private DNS (DoT / DoH) на Android.
  - Определение IP-адреса роутера/шлюза по умолчанию (`192.168.1.1` и др.) и разделение приоритетов (если активен частный DNS, роутер помечается как неиспользуемый).
  - 9 встроенных публичных API для проверки (edns.ip-api.com, ipleak, Mullvad, BrowserLeaks, Whoer, AdGuard и др.).
  - Функция пинга и тестирования доступности всех 9 API прямо из приложения.
  - Флаги стран для каждого обнаруженного резолвера.

- **2. Проверка CDN (CDN & Proxy Detector):**
  - Определение глобальных CDN-провайдеров за целевым доменом (Cloudflare, Akamai, CloudFront, Fastly, GCore, Incapsula, BunnyCDN, Edgecast, Qwilt, Yandex Cloud и др.).
  - Анализ CNAME-цепочки канонических адресов и псевдонимов.
  - Обнаружение пограничных узлов Anycast Edge и номеров автономных систем (ASN).
  - Проверка сигнатурных HTTP-заголовков сервера (`cf-ray`, `x-amz-cf-id`, `x-akamai-transformed` и др.).
  - Быстрые пресеты для популярных сайтов (habr.com, apple.com, reddit.com, rutracker.org, cloudflare.com, vk.com).
  - Кнопка перехода к списку доменов обнаруженного CDN в один клик.

- **3. Поиск доменов сервисов (Services & Domains Database):**
  - Офлайн-база доменов и правил маршрутизации на основе `Loyalsoldier/v2ray-rules-dat`.
  - Интеллектуальный поиск с автодополнением и псевдонимами (YouTube, Cloudflare, ChatGPT, Gemini, Telegram, Discord, Steam, VK, RuTracker и др.).
  - Прокручиваемый список всех доменов сервиса с кнопкой пакетного копирования в буфер обмена.
  - Раздел «Скачать RuleSets»: кнопки загрузки актуальных файлов `geosite.dat` и `geoip.dat` и копирования прямых ссылок.
  - Точные теги маршрутизации `geosite:...` и `geoip:...` с копированием в один клик.

- **4. Интерактивная справка («Ликбез простым языком»):**
  - Кнопки `?` во всех информационных блоках каждого раздела с наглядным объяснением сетевых терминов и параметров.

- **5. Интерфейс и стабильность:**
  - Плавающее капсульное меню навигации внизу экрана (Bottom Capsule Bar).
  - Динамический заголовок текущего раздела в шапке.
  - Единый универсальный APK, совместимый с любыми архитектурами процессоров Android (5.0+).
  - Автоматическое уведомление о выходе новых версий с возможностью обновления в один клик.

### PowerShell скрипт для Windows (`Test-DnsLeak.ps1`):
- Работает на Windows 10/11 без установки сторонних модулей.
- Анализирует настройки адаптеров, шлюз по умолчанию и DNS через .NET NetworkInterface.
- Интерактивное меню выбора из 9 API либо запуск с параметрами командной строки (`-Provider`, `-ShowLocalOnly`, `-AsJson`).
- Мгновенный запуск одной командой без скачивания файлов на диск.

## Установка и запуск

### Android

Скачайте универсальный APK со страницы **[Релизов](https://github.com/YarNSD/dns-leak-test/releases/latest)**:
- **`DnsLeakTest.apk`** (или `DnsLeakTest-universal.apk`) — единый установочный пакет под любые процессоры и устройства.

Минимальная версия: Android 5.0 (Lollipop) и новее.

### Windows (PowerShell)

**Запуск одной строкой прямо из сети (без сохранения файлов):**
```powershell
irm https://raw.githubusercontent.com/YarNSD/dns-leak-test/main/Test-DnsLeak.ps1 | iex
```

Либо через окно `Win + R` или командную строку cmd:
```powershell
powershell -ExecutionPolicy Bypass -NoProfile -Command "irm https://raw.githubusercontent.com/YarNSD/dns-leak-test/main/Test-DnsLeak.ps1 | iex"
```

**Запуск скачанного файла с интерактивным меню:**
```powershell
powershell -ExecutionPolicy Bypass -File .\Test-DnsLeak.ps1
```

Быстрая проверка через рекомендуемый API без диалогов:
```powershell
powershell -ExecutionPolicy Bypass -File .\Test-DnsLeak.ps1 -NonInteractive
```

Посмотреть только локальные DNS-настройки роутера и адаптеров:
```powershell
powershell -ExecutionPolicy Bypass -File .\Test-DnsLeak.ps1 -ShowLocalOnly
```

## Сборка из исходников

Для сборки Android-приложения требуется JDK 17 и Android SDK:
```bash
cd android
./gradlew assembleDebug
```
Собранный файл находится в `android/app/build/outputs/apk/debug/app-debug.apk`.

## Лицензия
MIT
