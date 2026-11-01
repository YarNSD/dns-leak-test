# DNS Leak Test

Инструмент для проверки утечки DNS на Android и Windows. 

Основная цель проекта — быстро и точно определить, перехватываются ли ваши DNS-запросы интернет-провайдером, работают ли настроенные DoH/DoT или запросы продолжают уходить на шлюз роутера.

## Возможности

- **Android приложение (APK):**
  - Определение активного типа подключения (Wi-Fi роутер vs мобильная сеть LTE/5G).
  - Проверка статуса системного Private DNS (DoT) на Android.
  - Определение IP-адреса роутера/шлюза по умолчанию (`192.168.1.1` и др.) и разделение приоритетов (если активен частный DNS, роутер помечается как неиспользуемый).
  - 10 встроенных публичных API для проверки (bash.ws, ipleak, Mullvad, BrowserLeaks, Whoer, AdGuard и др.).
  - Функция пинга и тестирования доступности всех 10 API прямо из приложения (для выявления блокировок РКН/ТСПУ).
  - Автообновление APK напрямую с ветки `main` репозитория.
  - Сохранение результатов при повороте экрана и в планшетном режиме.
  - Минималистичный Material 3 интерфейс.

- **PowerShell скрипт для Windows (`Test-DnsLeak.ps1`):**
  - Работает на Windows 10/11 без установки сторонних модулей.
  - Анализирует настройки адаптеров, шлюз по умолчанию и DNS через .NET NetworkInterface.
  - Интерактивное меню выбора из 10 API либо запуск с параметрами командной строки (`-Provider`, `-ShowLocalOnly`, `-AsJson`).

## Установка и запуск

### Android
Скачайте готовый файл [DnsLeakTest.apk](DnsLeakTest.apk) из репозитория и установите на устройство. 
При выходе новых версий приложение предложит обновиться автоматически.

Минимальная версия: Android 5.0 (Lollipop) и новее.

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
Готовый APK будет находиться в `android/app/build/outputs/apk/debug/app-debug.apk`.

## Лицензия
MIT
