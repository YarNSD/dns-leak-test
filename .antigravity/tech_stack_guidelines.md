# DNS Leak Test: Tech Stack Guidelines & Bug Journal

## Tech Stack
- **PowerShell**: Windows PowerShell 5.1 / PowerShell 7+ compatible. Native `curl.exe` with `[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]'Tls12, Tls13'`.
- **Android**: Kotlin 1.9+, Gradle 8.7, Android SDK 34 (minSdk 21), Material 3, Gson.
- **Repository**: GitHub `https://github.com/YarNSD/dns-leak-test`.

## Design Conventions
- Zero emojis in UI (both PowerShell CLI and Android app), except real country flags where relevant.
- High-contrast text, clear status verdicts.

## Bug Journal

### 1. PowerShell 5.1 TLS SSPI Deadlock on bash.ws
- **Symptoms**: Remote script execution hung indefinitely at `[2/2] Получение результатов анализа...` when running `bash.ws`.
- **Root Cause**: The `bash.ws` Apache server performs repeated TLS session renegotiations on chunked transfer-encoding HTTP responses. .NET `HttpWebRequest` / `Invoke-RestMethod` in Windows PowerShell 5.1 locks up in an SSPI TLS handshake deadlock.
- **Resolution**: Replaced `Invoke-SafeHttpJson` to use `curl.exe -L -s -k --max-time 6` with strict timeouts, and completely purged `bash.ws` from the project. Promoted `edns.ip-api.com` to default #1 provider (<200 ms response time).

### 2. Nested Touch Interception in Domain List
- **Symptoms**: Domains list inside Service Domains search could not be scrolled when touching within the list area.
- **Root Cause**: The outer `NestedScrollView` captured vertical drag touch events, depriving the child view of momentum scrolls.
- **Resolution**: Implemented `RecyclerView` with `OnTouchListener` triggering `v.parent.requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN`/`ACTION_MOVE`, releasing on `ACTION_UP`/`ACTION_CANCEL`.

### 3. Tab Header Overflow on Narrow Screens
- **Symptoms**: Three wide text tabs in the top header were truncated or cut off on devices with screens <380dp.
- **Resolution**: Replaced top tabs with a floating bottom capsule navigation bar (`#151C28` pill container, active cyan `#38BDF8` circle pod) with bottom content padding (`paddingBottom="96dp"`), completely eliminating header crowding.

### 4. App Crash on Startup (Vector Drawables, ArrayAdapter, ABI Splits)
- **Symptoms**: App crashed immediately upon launch ("Ошибка при запуске приложения").
- **Root Cause**:
  1. `vectorDrawables.useSupportLibrary = true` was omitted in `defaultConfig`, while `android:src="@drawable/vector"` was used across 29 layouts without `app:srcCompat`. On Android devices without support library vector hook, VectorDrawable inflation throws `InflateException`.
  2. `ServiceAutoCompleteAdapter` inherited `ArrayAdapter` with layout containing `LinearLayout` without passing `R.id.tvSuggestionText` to super constructor and without overriding `getDropDownView()`, triggering `ClassCastException` on accessibility/autofill window attach.
  3. ABI splits were enabled without native libraries, causing CPU architecture mismatch on 64-bit-only or mixed devices.
- **Resolution**:
  1. Enabled `vectorDrawables.useSupportLibrary = true` and `AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)`. Replaced `android:src` with `app:srcCompat` across all layouts and cleaned vector paths in `ic_help_circle.xml`.
  2. Passed `R.id.tvSuggestionText` to `ArrayAdapter` super constructor and overrode `getDropDownView()`.
  3. Removed ABI splits, generating a clean universal APK `app-debug.apk` / `DnsLeakTest.apk`.
  4. Wrapped `onCreate` in a safety handler to render a diagnostics screen on any startup issue instead of a silent crash.

### 5. Missing Country Flag Emojis in DNS Resolvers List
- **Symptoms**: DNS servers in `rvDnsServers` displayed without country flags or with empty/broken badges.
- **Root Cause**: Primary provider `edns.ip-api.com` returns combined `"geo": "Sweden - Cloudflare, Inc."` rather than separate `country` / `country_code` JSON fields, leaving country and countryCode blank. Additionally, earlier implementation only had ~19 hardcoded countries.
- **Resolution**:
  1. Created `CountryUtils.kt` with full ISO-3166 system Locale mappings (~249 countries), Russian/English aliases, and mathematical Regional Indicator Unicode flag conversion (`0x1F1E6 + (char - 'A')`).
  2. Added `parseGeoString` to parse combined geo strings into country and ISP.
  3. Added client IP geolocation fallback in `DnsLeakTester` when client info is omitted by provider.
  4. Updated `DnsCardAdapter` and `MainActivity` to format country flags consistently.
