<p align="center">
  <img src="media/icon.png" width="120" height="120" alt="DNS Leak Test Icon">
</p>

# DNS Leak Test

Инструмент для проверки утечки DNS на Android и Windows. 

Основная цель проекта — быстро и точно определить, перехватываются ли ваши DNS-запросы интернет-провайдером (Ростелеком, МТС, Билайн, Мегафон, Дом.ру и др.), работают ли настроенные DoH/DoT или запросы продолжают уходить на шлюз роутера.

## Возможности

- **Android приложение (APK):**
  - Определение активного типа подключения (Wi-Fi роутер vs мобильная сеть LTE/5G).
  - Проверка статуса системного Private DNS (DoT) на Android.
  - Определение IP-адреса роутера/шлюза по умолчанию (`192.168.1.1` и др.) и разделение приоритетов (если активен частный DNS, роутер помечается как неиспользуемый).
  - 10 встроенных публичных API для проверки (bash.ws, ipleak, Mullvad, BrowserLeaks, Whoer, AdGuard и др.).
  - Функция пинга и тестирования доступности всех 10 API прямо из приложения (для выявления блокировок РКН/ТСПУ).
  - Автообновление APK напрямую с GitHub Releases в зависимости от архитектуры процессора (v7a, v8a, universal).
  - Сохранение результатов при повороте экрана и в планшетном режиме.
  - Минималистичный Material 3 интерфейс с флагами стран для каждого резолвера.

- **PowerShell скрипт для Windows (`Test-DnsLeak.ps1`):**
  - Работает на Windows 10/11 без установки сторонних модулей.
  - Анализирует настройки адаптеров, шлюз по умолчанию и DNS через .NET NetworkInterface.
  - Интерактивное меню выбора из 10 API либо запуск с параметрами командной строки (`-Provider`, `-ShowLocalOnly`, `-AsJson`).

## Установка и запуск

### Android
Скачайте APK под вашу архитектуру со страницы **[Релизов](https://github.com/YarNSD/dns-leak-test/releases/latest)**:
- `DnsLeakTest-arm64-v8a.apk` — для большинства современных смартфонов (64-bit ARM).
- `DnsLeakTest-armeabi-v7a.apk` — для более старых 32-bit устройств.
- `DnsLeakTest-universal.apk` — универсальный билд под любые процессоры.

Минимальная версия: Android 5.0 (Lollipop) и новее. При выходе новых версий приложение предложит обновиться автоматически.

### Windows (PowerShell)
Запуск интерактивного меню:
```powershell
powershell -ExecutionPolicy Bypass -File .\Test-DnsLeak.ps1
```

Быстрая проверка через рекомендуемый API без диалогов:
```powershell
powershell -ExecutionPolicy Bypass -File .\Test-DnsLeak.ps1 -Provider bash_ws -NonInteractive
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
Собранные файлы находятся в `android/app/build/outputs/apk/debug/`.

## Лицензия
MIT
