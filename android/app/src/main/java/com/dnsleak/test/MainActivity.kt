package com.dnsleak.test

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.graphics.Color
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dnsleak.test.databinding.ActivityMainBinding
import com.dnsleak.test.model.CdnCheckResult
import com.dnsleak.test.model.DnsLeakResult
import com.dnsleak.test.model.DnsProvider
import com.dnsleak.test.model.ServiceDomainItem
import com.dnsleak.test.service.AppUpdater
import com.dnsleak.test.service.CdnInspector
import com.dnsleak.test.service.DnsLeakTester
import com.dnsleak.test.service.LocalDnsInspector
import com.dnsleak.test.service.ServiceDomainRepository
import com.dnsleak.test.service.ServiceSuggestion
import com.dnsleak.test.ui.DnsCardAdapter
import com.dnsleak.test.ui.ServiceAutoCompleteAdapter
import com.dnsleak.test.ui.ServiceDomainAdapter
import com.dnsleak.test.util.CountryUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var tester: DnsLeakTester
    private lateinit var localInspector: LocalDnsInspector
    private lateinit var cdnInspector: CdnInspector
    private lateinit var serviceRepo: ServiceDomainRepository
    private lateinit var updater: AppUpdater
    private val dnsAdapter = DnsCardAdapter()
    private val serviceDomainAdapter = ServiceDomainAdapter()
    private val gson = Gson()

    private var currentProvider: DnsProvider = DnsProvider.ALL_PROVIDERS.first()
    private var lastResult: DnsLeakResult? = null
    private var currentDisplayedService: ServiceDomainItem? = null

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
        private const val PREF_KEY_PROVIDER_ID = "selected_provider_id"
        private const val KEY_LAST_RESULT_JSON = "key_last_result_json"
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedId = result.data?.getStringExtra(SettingsActivity.EXTRA_SELECTED_ID)
                ?: prefs.getString(PREF_KEY_PROVIDER_ID, "edns_ip_api") ?: "edns_ip_api"
            currentProvider = DnsProvider.getById(selectedId)
            updateProviderHeader()
            Toast.makeText(this, "Выбран API: ${currentProvider.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
            initApp(savedInstanceState)
        } catch (t: Throwable) {
            Log.e("DnsLeakTest", "Fatal startup error", t)
            renderFatalError(t)
        }
    }

    private fun renderFatalError(t: Throwable) {
        val scrollView = ScrollView(this).apply {
            val tv = TextView(this@MainActivity).apply {
                text = "Ошибка при запуске приложения:\n\n${t.javaClass.name}: ${t.message}\n\n${t.stackTraceToString()}"
                setTextColor(Color.WHITE)
                setPadding(40, 60, 40, 60)
                setTextIsSelectable(true)
                textSize = 14f
            }
            addView(tv)
            setBackgroundColor(Color.parseColor("#0B0F17"))
        }
        setContentView(scrollView)
    }

    private fun initApp(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("dns_leak_prefs", Context.MODE_PRIVATE)
        tester = DnsLeakTester(this)
        localInspector = LocalDnsInspector(this)
        cdnInspector = CdnInspector()
        serviceRepo = ServiceDomainRepository(this)
        updater = AppUpdater(this)

        setupRecyclerViews()
        loadSavedProvider()
        setupListeners()
        setupBottomNav()
        setupCdnListeners()
        setupServicesListeners()

        selectBottomNavTab(0)

        val savedJson = savedInstanceState?.getString(KEY_LAST_RESULT_JSON)
        if (!savedJson.isNullOrBlank()) {
            try {
                val restoredResult = gson.fromJson(savedJson, DnsLeakResult::class.java)
                if (restoredResult != null) {
                    lastResult = restoredResult
                    renderResults(restoredResult)
                } else {
                    refreshLocalDnsOverviewSafely()
                }
            } catch (_: Exception) {
                refreshLocalDnsOverviewSafely()
            }
        } else {
            refreshLocalDnsOverviewSafely()
        }

        checkAppUpdatesSilently()
    }

    private fun refreshLocalDnsOverviewSafely() {
        try {
            refreshLocalDnsOverview()
        } catch (e: Exception) {
            Log.w("DnsLeakTest", "Failed to inspect local DNS", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastResult?.let {
            outState.putString(KEY_LAST_RESULT_JSON, gson.toJson(it))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecyclerViews() {
        // DNS Servers Recycler
        binding.rvDnsServers.layoutManager = LinearLayoutManager(this)
        binding.rvDnsServers.adapter = dnsAdapter

        // Service Domains Recycler with scroll intercept fix
        binding.rvServiceDomains.layoutManager = LinearLayoutManager(this)
        binding.rvServiceDomains.adapter = serviceDomainAdapter
        binding.rvServiceDomains.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            false
        }
    }

    private fun loadSavedProvider() {
        val savedId = prefs.getString(PREF_KEY_PROVIDER_ID, "edns_ip_api") ?: "edns_ip_api"
        currentProvider = DnsProvider.getById(savedId)
        updateProviderHeader()
    }

    private fun updateProviderHeader() {
        binding.tvCurrentApiHeader.text = "Активный API: ${currentProvider.name}"
    }

    private fun refreshLocalDnsOverview() {
        val localInfo = localInspector.inspect()

        binding.tvLocalActiveDns.text = localInfo.getActiveDnsSummary()
        binding.tvLocalSecondaryDns.text = localInfo.getSecondaryDnsSummary()

        val vpnSuffix = if (localInfo.isVpnActive) " | VPN: Активен (${localInfo.interfaceName ?: "VPN"})" else ""
        binding.tvClientVpnStatus.text = "Тип сети: ${localInfo.getNetworkTypeName()}$vpnSuffix"
    }

    private fun setupBottomNav() {
        binding.navDnsLeak.setOnClickListener {
            selectBottomNavTab(0)
        }

        binding.navCdn.setOnClickListener {
            selectBottomNavTab(1)
        }

        binding.navServices.setOnClickListener {
            selectBottomNavTab(2)
        }
    }

    private fun selectBottomNavTab(tabIndex: Int) {
        val onPrimaryColor = ContextCompat.getColor(this, R.color.on_primary)
        val mutedColor = ContextCompat.getColor(this, R.color.on_surface_variant)

        // Clear all active backgrounds & set muted icons
        binding.ivNavDnsBg.setBackgroundResource(android.R.color.transparent)
        binding.ivNavDnsIcon.imageTintList = ColorStateList.valueOf(mutedColor)

        binding.ivNavCdnBg.setBackgroundResource(android.R.color.transparent)
        binding.ivNavCdnIcon.imageTintList = ColorStateList.valueOf(mutedColor)

        binding.ivNavServicesBg.setBackgroundResource(android.R.color.transparent)
        binding.ivNavServicesIcon.imageTintList = ColorStateList.valueOf(mutedColor)

        binding.layoutDnsLeakContent.visibility = View.GONE
        binding.layoutCdnContent.visibility = View.GONE
        binding.layoutServicesContent.visibility = View.GONE

        when (tabIndex) {
            0 -> {
                binding.tvCategorySubtitle.text = "NETWORK SECURITY & DIAGNOSTICS"
                binding.tvTitle.text = "DNS Leak Test"
                binding.ivNavDnsBg.setBackgroundResource(R.drawable.bg_nav_active_circle)
                binding.ivNavDnsIcon.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                binding.layoutDnsLeakContent.visibility = View.VISIBLE
            }
            1 -> {
                binding.tvCategorySubtitle.text = "CDN & PROXY DETECTOR"
                binding.tvTitle.text = "Проверка CDN"
                binding.ivNavCdnBg.setBackgroundResource(R.drawable.bg_nav_active_circle)
                binding.ivNavCdnIcon.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                binding.layoutCdnContent.visibility = View.VISIBLE
            }
            2 -> {
                binding.tvCategorySubtitle.text = "SERVICES & DOMAINS DATABASE"
                binding.tvTitle.text = "Домены сервисов"
                binding.ivNavServicesBg.setBackgroundResource(R.drawable.bg_nav_active_circle)
                binding.ivNavServicesIcon.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                binding.layoutServicesContent.visibility = View.VISIBLE
            }
        }
    }

    private fun setupCdnListeners() {
        binding.btnCheckCdn.setOnClickListener {
            val domain = binding.etCdnDomain.text.toString().trim()
            hideKeyboard()
            runCdnCheck(domain)
        }

        binding.etCdnDomain.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                val domain = binding.etCdnDomain.text.toString().trim()
                hideKeyboard()
                runCdnCheck(domain)
                true
            } else {
                false
            }
        }

        // Quick Preset Chips for CDN Tab
        val chips = listOf(
            binding.chipHabr to "habr.com",
            binding.chipApple to "apple.com",
            binding.chipReddit to "reddit.com",
            binding.chipRutracker to "rutracker.org",
            binding.chipCloudflare to "cloudflare.com",
            binding.chipVk to "vk.com"
        )

        for ((chipView, domain) in chips) {
            chipView.setOnClickListener {
                binding.etCdnDomain.setText(domain)
                hideKeyboard()
                runCdnCheck(domain)
            }
        }

        // Help tooltips / Educational dialogs for CDN Inspector tab
        binding.btnHelpCdnInput.setOnClickListener {
            showHelpDialog(
                title = "Проверка CDN домена",
                description = "Введите домен любого интересующего вас сайта (например, habr.com или apple.com) либо выберите один из быстрых пресетов.\n\nИнструмент проведёт комплексный анализ DNS-записей, цепочки псевдонимов (CNAME), автономных систем (ASN) и служебных HTTP-заголовков, чтобы выяснить, за какой CDN-сетью спрятан сервер."
            )
        }

        binding.btnHelpCdnService.setOnClickListener {
            showHelpDialog(
                title = "Обнаруженная сеть CDN",
                description = "Показывает название глобальной сети доставки контента (Cloudflare, Akamai, Fastly, GCore и др.), обслуживающей введённый домен.\n\nКнопка «Открыть домены CDN» позволяет мгновенно перейти ко всем доменным именам этой сети, чтобы настроить для них правила маршрутизации или белый список."
            )
        }

        binding.btnHelpCdnDomains.setOnClickListener {
            showHelpDialog(
                title = "CNAME-цепочка псевдонимов",
                description = "Каноническое имя (CNAME) показывает, на какие промежуточные служебные адреса перенаправляется домен перед тем, как попасть на реальный сервер.\n\nНапример, сайт domain.com может ссылаться на domain.cdn.cloudflare.net, что однозначно доказывает использование CDN-сети."
            )
        }

        binding.btnHelpCdnEdgeIps.setOnClickListener {
            showHelpDialog(
                title = "Пограничные узлы и ASN",
                description = "Список конечных IP-адресов серверов, отдающих сайт, и номер их автономной системы (ASN).\n\nПозволяет определить владельца серверов (Cloudflare Inc., Akamai Technologies, Fastly и др.) и физическую локацию пограничных серверов (Anycast Edge)."
            )
        }

        binding.btnHelpCdnHeaders.setOnClickListener {
            showHelpDialog(
                title = "Сигнатуры и HTTP-заголовки",
                description = "Служебные заголовки ответа веб-сервера (например, cf-ray, server: cloudflare, x-akamai-transformed, x-served-by: fastly).\n\nСлужат неопровержимым доказательством прохождения трафика через конкретную инфраструктуру CDN."
            )
        }
    }

    private fun setupServicesListeners() {
        val searchAdapter = ServiceAutoCompleteAdapter(this, serviceRepo)
        binding.actvServiceSearch.setAdapter(searchAdapter)
        binding.actvServiceSearch.threshold = 1

        binding.actvServiceSearch.setOnItemClickListener { parent, _, position, _ ->
            val suggestion = parent.getItemAtPosition(position) as? ServiceSuggestion
            if (suggestion != null) {
                binding.actvServiceSearch.setText(suggestion.service.name)
                hideKeyboard()
                displayServiceDetails(suggestion.service, suggestion.matchedDomain)
            }
        }

        binding.actvServiceSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                val query = binding.actvServiceSearch.text.toString().trim()
                val matched = serviceRepo.findBestMatch(query)
                if (matched != null) {
                    binding.actvServiceSearch.setText(matched.name)
                    hideKeyboard()
                    displayServiceDetails(matched)
                } else if (query.isNotBlank()) {
                    Toast.makeText(this, "Сервис «$query» не найден в базе", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        // Quick Service Chips
        val serviceChips = listOf(
            binding.chipServiceYoutube to "youtube",
            binding.chipServiceCloudflare to "cloudflare",
            binding.chipServiceChatgpt to "chatgpt",
            binding.chipServiceGemini to "gemini",
            binding.chipServiceTelegram to "telegram",
            binding.chipServiceDiscord to "discord",
            binding.chipServiceSteam to "steam",
            binding.chipServiceVk to "vk",
            binding.chipServiceRutracker to "rutracker"
        )

        for ((chipView, serviceId) in serviceChips) {
            chipView.setOnClickListener {
                val service = serviceRepo.getById(serviceId)
                if (service != null) {
                    binding.actvServiceSearch.setText(service.name)
                    hideKeyboard()
                    displayServiceDetails(service)
                }
            }
        }

        // RuleSets Download & Copy Listeners
        val geositeUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
        val geoipUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        binding.btnDownloadGeosite.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geositeUrl))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyGeositeUrl.setOnClickListener {
            val clip = ClipData.newPlainText("Geosite URL", geositeUrl)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Ссылка на geosite.dat скопирована в буфер", Toast.LENGTH_SHORT).show()
        }

        binding.btnDownloadGeoip.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoipUrl))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyGeoipUrl.setOnClickListener {
            val clip = ClipData.newPlainText("GeoIP URL", geoipUrl)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, "Ссылка на geoip.dat скопирована в буфер", Toast.LENGTH_SHORT).show()
        }

        // Help tooltips / Educational dialogs for Service Domains tab
        binding.btnHelpSearch.setOnClickListener {
            showHelpDialog(
                title = "Поиск сервиса и его доменов",
                description = "Большинство современных сайтов и приложений (например, YouTube, Discord, Steam) используют десятки служебных поддоменов для авторизации, загрузки видео, картинок, чатов и обновлений.\n\nЭтот блок позволяет быстро найти полный список скрытых доменов сервиса, чтобы вы могли настроить правила маршрутизации в роутере или VPN-клиенте, разблокировать нужный сервис или добавить его в исключения."
            )
        }

        binding.btnHelpRuleSets.setOnClickListener {
            showHelpDialog(
                title = "Что такое RuleSets (Geosite и GeoIP)?",
                description = "RuleSets — это готовые базы данных правил для клиентских программ (V2Ray, Xray, Sing-box, Clash, Nekobox, Shadowrocket).\n\n• Geosite.dat — списки доменов сотен популярных платформ и категорий.\n• Geoip.dat — диапазоны IP-адресов стран и компаний.\n\nКлиент использует эти файлы, чтобы автоматически решать: какой трафик открывать напрямую, а какой направлять через защищённый туннель."
            )
        }

        binding.btnHelpServiceCdn.setOnClickListener {
            showHelpDialog(
                title = "Что такое CDN и зачем знать статус?",
                description = "CDN (Content Delivery Network) — это глобальная сеть серверов-посредников (Cloudflare, Akamai, Fastly, GCore и др.), которая кэширует контент ближе к пользователю и защищает ресурс от атак.\n\nЕсли сервис находится за CDN, провайдер или файрвол часто блокирует не сам сервис, а IP-адреса CDN. Нажав на бейдж CDN, вы можете сразу увидеть список доменов и инфраструктуру этой CDN-сети."
            )
        }

        binding.btnHelpRoutingRules.setOnClickListener {
            showHelpDialog(
                title = "Теги Geosite и GeoIP",
                description = "Вместо ручного перечисления десятков доменов в файле конфигурации клиента, достаточно указать один короткий тег:\n\n• geosite:youtube — включает все домены YouTube\n• geosite:category-ai-chat-!cn — включает все зарубежные нейросети\n• geoip:google — перенаправляет IP-адреса серверов Google\n\nНажмите на любой тег в списке, чтобы скопировать его в буфер и вставить в конфигурацию своего клиента."
            )
        }

        binding.btnHelpDomainsList.setOnClickListener {
            showHelpDialog(
                title = "Список доменов сервиса",
                description = "Это полный актуальный список доменных имён, на которые приложение или сайт отправляет сетевые запросы.\n\nВы можете прокручивать список, изучать поддомены и одним нажатием на значок копирования в правом верхнем углу скопировать весь столбец в буфер обмена для импорта в списки разблокировки или правила файрвола."
            )
        }
    }

    private fun showHelpDialog(title: String, description: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help_info, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvHelpTitle)
        val tvDescription = dialogView.findViewById<android.widget.TextView>(R.id.tvHelpDescription)
        val btnClose = dialogView.findViewById<android.widget.Button>(R.id.btnHelpClose)

        tvTitle.text = title
        tvDescription.text = description

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun displayServiceDetails(service: ServiceDomainItem, matchedDomain: String? = null) {
        currentDisplayedService = service
        binding.layoutServiceResults.visibility = View.VISIBLE

        binding.tvSelectedServiceName.text = service.name
        binding.tvSelectedServiceDomain.text = "Основной домен: ${service.primaryDomain}"
        binding.tvServiceDomainCount.text = "СПИСОК ДОМЕНОВ СЕРВИСА (${service.domains.size})"

        // If matched on a specific domain, put it at the very top of the list for instant visibility
        val sortedDomains = if (matchedDomain != null && service.domains.contains(matchedDomain)) {
            listOf(matchedDomain) + service.domains.filter { it != matchedDomain }
        } else {
            service.domains
        }

        // Update domains in RecyclerView
        serviceDomainAdapter.updateDomains(sortedDomains)
        binding.rvServiceDomains.scrollToPosition(0)

        // Copy button setup
        binding.btnCopyServiceDomains.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val rawDomains = service.domains.joinToString("\n")
            val clip = ClipData.newPlainText("Domains of ${service.name}", rawDomains)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(
                this,
                "Скопировано ${service.domains.size} доменов в буфер обмена",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Setup Geosite & GeoIP routing rules
        setupRoutingRulesSection(service)

        // Check CDN status for the service
        inspectServiceCdn(service)
    }

    private fun setupRoutingRulesSection(service: ServiceDomainItem) {
        val geosites = service.geosites
        val geoips = service.geoips

        if (geosites.isEmpty() && geoips.isEmpty()) {
            binding.cardServiceRoutingRules.visibility = View.GONE
            return
        }
        binding.cardServiceRoutingRules.visibility = View.VISIBLE

        // 1. Geosite Tags
        if (geosites.isEmpty()) {
            binding.layoutGeositeSection.visibility = View.GONE
        } else {
            binding.layoutGeositeSection.visibility = View.VISIBLE
            binding.tvGeositeHeader.text = "Теги Geosite (${geosites.size}):"
            populateTagContainers(
                tags = geosites,
                typeLabel = "GEOSITE",
                visibleContainer = binding.containerGeositesVisible,
                collapsedContainer = binding.containerGeositesCollapsed,
                toggleButton = binding.btnToggleGeosites,
                maxInitialVisible = 2
            )
        }

        // 2. GeoIP Tags
        if (geoips.isEmpty()) {
            binding.layoutGeoipSection.visibility = View.GONE
        } else {
            binding.layoutGeoipSection.visibility = View.VISIBLE
            binding.tvGeoipHeader.text = "Теги GeoIP (${geoips.size}):"
            populateTagContainers(
                tags = geoips,
                typeLabel = "GEOIP",
                visibleContainer = binding.containerGeoipsVisible,
                collapsedContainer = binding.containerGeoipsCollapsed,
                toggleButton = binding.btnToggleGeoips,
                maxInitialVisible = 2
            )
        }
    }

    private fun populateTagContainers(
        tags: List<String>,
        typeLabel: String,
        visibleContainer: ViewGroup,
        collapsedContainer: ViewGroup,
        toggleButton: android.widget.TextView,
        maxInitialVisible: Int = 2
    ) {
        visibleContainer.removeAllViews()
        collapsedContainer.removeAllViews()
        collapsedContainer.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        fun createTagView(tagText: String): View {
            val view = inflater.inflate(R.layout.item_routing_tag, visibleContainer, false)
            val tvBadge = view.findViewById<android.widget.TextView>(R.id.tvTagBadgeType)
            val tvValue = view.findViewById<android.widget.TextView>(R.id.tvTagValue)
            val btnCopy = view.findViewById<android.widget.ImageView>(R.id.btnCopyTag)

            tvBadge.text = typeLabel
            tvValue.text = tagText

            val copyAction = {
                val clip = ClipData.newPlainText(typeLabel, tagText)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(this, "Скопировано: $tagText", Toast.LENGTH_SHORT).show()
            }

            view.setOnClickListener { copyAction() }
            btnCopy.setOnClickListener { copyAction() }

            return view
        }

        val initialList = tags.take(maxInitialVisible)
        val remainingList = tags.drop(maxInitialVisible)

        for (tag in initialList) {
            visibleContainer.addView(createTagView(tag))
        }

        if (remainingList.isNotEmpty()) {
            for (tag in remainingList) {
                collapsedContainer.addView(createTagView(tag))
            }
            toggleButton.visibility = View.VISIBLE
            toggleButton.text = "Показать ещё (${remainingList.size}) ▼"

            toggleButton.setOnClickListener {
                if (collapsedContainer.visibility == View.VISIBLE) {
                    collapsedContainer.visibility = View.GONE
                    toggleButton.text = "Показать ещё (${remainingList.size}) ▼"
                } else {
                    collapsedContainer.visibility = View.VISIBLE
                    toggleButton.text = "Свернуть ▲"
                }
            }
        } else {
            toggleButton.visibility = View.GONE
        }
    }

    private fun inspectServiceCdn(service: ServiceDomainItem) {
        binding.progressServiceCdnCheck.visibility = View.VISIBLE
        binding.tvServiceCdnBadge.text = "Анализ CDN для ${service.name}..."
        binding.layoutServiceCdnBadge.setBackgroundResource(R.drawable.badge_neutral_bg)
        binding.tvServiceCdnBadge.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        binding.ivServiceCdnArrow.visibility = View.GONE
        binding.tvServiceCdnHint.visibility = View.GONE
        binding.layoutServiceCdnBadge.setOnClickListener(null)

        lifecycleScope.launch {
            val cdnResult = cdnInspector.inspect(service.primaryDomain)
            binding.progressServiceCdnCheck.visibility = View.GONE

            if (cdnResult.isCdnDetected) {
                val cdnName = cdnResult.cdnServiceName
                binding.tvServiceCdnBadge.text = "ЗА CDN: $cdnName"
                binding.layoutServiceCdnBadge.setBackgroundResource(R.drawable.badge_safe_bg)
                binding.tvServiceCdnBadge.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_safe_text))

                val linkedCdnService = serviceRepo.findServiceByCdnName(cdnName)
                if (linkedCdnService != null && linkedCdnService.id != service.id) {
                    binding.ivServiceCdnArrow.visibility = View.VISIBLE
                    binding.tvServiceCdnHint.visibility = View.VISIBLE
                    binding.layoutServiceCdnBadge.setOnClickListener {
                        binding.actvServiceSearch.setText(linkedCdnService.name)
                        displayServiceDetails(linkedCdnService)
                        Toast.makeText(
                            this@MainActivity,
                            "Открыты домены CDN: ${linkedCdnService.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    binding.ivServiceCdnArrow.visibility = View.GONE
                    binding.tvServiceCdnHint.visibility = View.GONE
                    binding.layoutServiceCdnBadge.setOnClickListener(null)
                }
            } else {
                binding.tvServiceCdnBadge.text = "ПРЯМОЙ ХОСТИНГ / СОБСТВЕННАЯ СЕТЬ"
                binding.layoutServiceCdnBadge.setBackgroundResource(R.drawable.badge_neutral_bg)
                binding.tvServiceCdnBadge.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
                binding.ivServiceCdnArrow.visibility = View.GONE
                binding.tvServiceCdnHint.visibility = View.GONE
                binding.layoutServiceCdnBadge.setOnClickListener(null)
            }
        }
    }

    private fun runCdnCheck(domain: String) {
        if (domain.isBlank()) {
            Toast.makeText(this, "Введите домен для проверки", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCheckCdn.isEnabled = false
        binding.progressCdn.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = cdnInspector.inspect(domain)
            binding.btnCheckCdn.isEnabled = true
            binding.progressCdn.visibility = View.GONE
            renderCdnResult(result)
        }
    }

    private fun renderCdnResult(result: CdnCheckResult) {
        binding.layoutCdnResults.visibility = View.VISIBLE

        if (result.errorMessage != null && !result.isCdnDetected && result.cdnDomains.isEmpty() && result.edgeIps.isEmpty()) {
            binding.tvCdnServiceName.text = result.cdnServiceName
            binding.tvCdnStatusBadge.text = "ОШИБКА"
            binding.tvCdnStatusBadge.setBackgroundResource(R.drawable.badge_leak_bg)
            binding.tvCdnStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_leak_text))
            binding.tvCdnDetectionMethod.text = result.errorMessage
            binding.cardCdnDomains.visibility = View.GONE
            binding.cardCdnEdgeIps.visibility = View.GONE
            binding.cardCdnHeaders.visibility = View.GONE
            return
        }

        binding.cardCdnDomains.visibility = View.VISIBLE
        binding.cardCdnEdgeIps.visibility = View.VISIBLE
        binding.cardCdnHeaders.visibility = View.VISIBLE

        // Service Name & Status Badge
        binding.tvCdnServiceName.text = result.cdnServiceName
        if (result.isCdnDetected) {
            binding.tvCdnStatusBadge.text = "CDN ОБНАРУЖЕН"
            binding.tvCdnStatusBadge.setBackgroundResource(R.drawable.badge_safe_bg)
            binding.tvCdnStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
        } else {
            binding.tvCdnStatusBadge.text = "ПРЯМОЙ ХОСТИНГ"
            binding.tvCdnStatusBadge.setBackgroundResource(R.drawable.badge_neutral_bg)
            binding.tvCdnStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        }
        binding.tvCdnDetectionMethod.text = result.detectionMethod

        // CNAME Chain
        if (result.cdnDomains.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append(result.domain).append("\n")
            for (cname in result.cdnDomains) {
                sb.append("  ↓ ").append(cname).append("\n")
            }
            binding.tvCdnDomainsList.text = sb.toString().trimEnd()
            binding.tvCdnDomainsList.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
        } else {
            binding.tvCdnDomainsList.text = "CNAME-записи отсутствуют (прямой Anycast IP маршрут / ALIAS)"
            binding.tvCdnDomainsList.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
        }

        // Edge IPs & ASN
        if (result.edgeIps.isNotEmpty()) {
            val sb = StringBuilder()
            for ((idx, ipInfo) in result.edgeIps.withIndex()) {
                val flag = countryCodeToEmoji(ipInfo.countryCode)
                val flagStr = if (flag.isNotBlank()) "$flag " else ""
                sb.append("[${idx + 1}] ").append(ipInfo.ip).append("\n")
                sb.append("    Организация: ").append(ipInfo.org).append("\n")
                if (ipInfo.asn.isNotBlank()) {
                    sb.append("    ASN: ").append(ipInfo.asn).append("\n")
                }
                sb.append("    Локация: ").append(flagStr).append(ipInfo.city)
                if (ipInfo.city.isNotBlank() && ipInfo.country.isNotBlank()) sb.append(", ")
                sb.append(ipInfo.country).append("\n\n")
            }
            binding.tvCdnEdgeIpsList.text = sb.toString().trimEnd()
        } else {
            binding.tvCdnEdgeIpsList.text = "Пограничные IP-адреса не определены"
        }

        // HTTP Headers Evidence
        if (result.evidenceHeaders.isNotEmpty()) {
            val sb = StringBuilder()
            for ((key, value) in result.evidenceHeaders) {
                sb.append(key).append(": ").append(value).append("\n")
            }
            binding.tvCdnHeadersList.text = sb.toString().trimEnd()
        } else {
            binding.tvCdnHeadersList.text = "Специфические CDN-заголовки не возвращены сервером"
        }
    }

    private fun countryCodeToEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(Locale.ROOT), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun setupListeners() {
        binding.btnCheck.setOnClickListener {
            runDnsTest()
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_SELECTED_ID, currentProvider.id)
            }
            settingsLauncher.launch(intent)
        }

        // Help tooltips / Educational dialogs for DNS Leak tab
        binding.btnHelpDnsStatus.setOnClickListener {
            showHelpDialog(
                title = "Статус защиты DNS",
                description = "Показывает, защищены ли ваши DNS-запросы от перехвата и слежки.\n\n" +
                        "• Зеленый статус: активен частный шифрованный DNS (DoH/DoT) на телефоне, защищенный VPN, либо на роутере настроены зарубежные непровайдерские серверы (условно безопасно).\n" +
                        "• Желтый статус: подключение через Wi-Fi роутер с российским DNS без шифрования. Запросы могут перехватываться провайдером.\n" +
                        "• Красный статус: утечка или прямое использование DNS провайдера/мобильного оператора. Ваши запросы перехватываются оператором связи."
            )
        }

        binding.btnHelpClientConnection.setOnClickListener {
            showHelpDialog(
                title = "Ваше внешнее подключение",
                description = "Отображает информацию о том, как ваше устройство выглядит для сайтов в интернете:\n\n• Внешний IP-адрес: публичный сетевой адрес в сети Интернет\n• Провайдер (ASN): название телеком-оператора или хостинга\n• Локация: географическое расположение узла подключения\n• Тип сети: Wi-Fi роутер или мобильная передача данных (LTE/5G)\n\nПозволяет убедиться, скрыт ли ваш настоящий домашний IP-адрес."
            )
        }

        binding.btnHelpLocalDns.setOnClickListener {
            showHelpDialog(
                title = "Локальная конфигурация DNS",
                description = "Показывает системные настройки DNS на вашем Android-устройстве:\n\n• Частный DNS (Private DNS / DoT): если включён в настройках телефона, все запросы шифруются напрямую и не попадают на роутер.\n• DNS роутера (шлюза): стандартный DNS-сервер домашней сети (обычно 192.168.x.x), который пересылает запросы провайдеру, если шифрование не настроено."
            )
        }

        binding.btnHelpResolvers.setOnClickListener {
            showHelpDialog(
                title = "Обнаруженные DNS-резолверы",
                description = "Это серверы в интернете, которые фактически обрабатывали сетевые запросы вашего телефона во время тестирования.\n\n• Безопасный статус: все обнаруженные резолверы принадлежат вашему VPN или доверенному безопасному сервису.\n• Утечка DNS: найдены резолверы вашего интернет-провайдера (МТС, Ростелеком, Билайн, Дом.ру и др.), что свидетельствует об утечке сетевой активности."
            )
        }
    }

    private fun runDnsTest() {
        setTestingState(true)
        refreshLocalDnsOverview()

        lifecycleScope.launch {
            val result = tester.runTest(currentProvider)
            lastResult = result
            setTestingState(false)
            renderResults(result)
        }
    }

    private fun setTestingState(isTesting: Boolean) {
        binding.btnCheck.isEnabled = !isTesting
        binding.btnSettings.isEnabled = !isTesting
        binding.progressBar.visibility = if (isTesting) View.VISIBLE else View.GONE

        if (isTesting) {
            binding.tvVerdict.text = getString(R.string.status_testing)
            binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            binding.tvVerdictDescription.text = "Опрос резолверов через ${currentProvider.name}..."
            binding.cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.surface)
            )
            binding.cardStatus.strokeColor =
                ContextCompat.getColor(this, R.color.divider)
        }
    }

    private fun renderResults(result: DnsLeakResult) {
        binding.tvClientIp.text = "IP-адрес: ${result.clientIp}"
        binding.tvClientIsp.text = "Провайдер: ${result.clientIsp}"
        val clientFlag = CountryUtils.getCountryFlag(result.clientCountry)
        val clientFlagPrefix = if (clientFlag.isNotBlank()) "$clientFlag " else ""
        binding.tvClientLocation.text = "Локация: $clientFlagPrefix${result.clientCountry}"

        val vpnSuffix = if (result.localDnsInfo.isVpnActive) " | VPN: Активен (${result.localDnsInfo.interfaceName ?: "VPN"})" else ""
        binding.tvClientVpnStatus.text = "Тип сети: ${result.localDnsInfo.getNetworkTypeName()}$vpnSuffix"

        binding.tvLocalActiveDns.text = result.localDnsInfo.getActiveDnsSummary()
        binding.tvLocalSecondaryDns.text = result.localDnsInfo.getSecondaryDnsSummary()

        binding.tvDnsListHeader.text = "ОБНАРУЖЕННЫЕ DNS-РЕЗОЛВЕРЫ (${result.servers.size})"

        val hasRussianDns = result.servers.any { CountryUtils.isRussian(it.country, it.isp) || it.countryCode.equals("RU", ignoreCase = true) }
        val hasPrivateDns = result.localDnsInfo.hasActivePrivateDns() || result.localDnsInfo.privateDnsMode == "opportunistic"
        val isWifi = result.localDnsInfo.isWifi
        val isCellular = result.localDnsInfo.isCellular
        val isVpn = result.localDnsInfo.isVpnActive

        val gatewayText = result.localDnsInfo.gatewayIp?.let { " ($it)" } ?: ""

        when {
            result.servers.isEmpty() -> {
                binding.tvVerdict.text = "Серверы не найдены"
                binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
                binding.tvVerdictDescription.text = result.clientIsp
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.surface)
                )
                binding.cardStatus.strokeColor =
                    ContextCompat.getColor(this, R.color.divider)
                binding.tvEmptyList.visibility = View.VISIBLE
            }

            // 1. Private DNS is configured and active -> GREEN
            hasPrivateDns -> {
                binding.tvVerdict.text = "DNS интернет-провайдера не используется"
                binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
                val privateDnsName = result.localDnsInfo.privateDnsHostname ?: "DoT"
                binding.tvVerdictDescription.text =
                    "Все DNS-запросы зашифрованы и направляются через частный DNS ($privateDnsName) в обход роутера и провайдера."
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_safe_bg)
                )
                binding.cardStatus.strokeColor =
                    ContextCompat.getColor(this, R.color.status_safe_text)
                binding.tvEmptyList.visibility = View.GONE
            }

            // 2. Wi-Fi router (no private DNS, no VPN)
            isWifi && !isVpn -> {
                if (hasRussianDns) {
                    // Russian DNS on router -> YELLOW Warning
                    binding.tvVerdict.text = "Используется DNS роутера (Wi-Fi)"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_warning_text))
                    val warningText =
                        "DNS-запросы отправляются на роутер$gatewayText. Обратите внимание: используется российский DNS, а значит ваши запросы могут быть в опасности (перехват, слежка и блокировки провайдером). Рекомендуется включить частный DNS на телефоне."
                    binding.tvVerdictDescription.text = warningText
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_warning_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_warning_text)
                } else {
                    // Non-Russian DNS on router -> GREEN Conditionally Safe
                    binding.tvVerdict.text = "DNS через роутер (Условно безопасно)"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
                    val foreignIsp = result.servers.firstOrNull()?.isp ?: "внешние серверы"
                    val foreignCountry = result.servers.firstOrNull()?.let {
                        val flag = CountryUtils.getCountryFlag(it.countryCode.ifBlank { it.country })
                        if (flag.isNotBlank()) "$flag ${it.country}" else it.country
                    } ?: "зарубежные"
                    val safeRouterText =
                        "Резолв выполняется через роутер$gatewayText, но на роутере используются не провайдерские DNS. Обнаружены зарубежные резолверы ($foreignIsp, $foreignCountry). Подключение условно безопасно."
                    binding.tvVerdictDescription.text = safeRouterText
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_safe_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_safe_text)
                }
                binding.tvEmptyList.visibility = View.GONE
            }

            // 3. Cellular network without private DNS -> RED if leak or Russian DNS
            isCellular && !isVpn -> {
                if (result.isOverallLeak || hasRussianDns || result.servers.any { it.isIspDns }) {
                    binding.tvVerdict.text = "Используется DNS интернет-провайдера"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_leak_text))
                    val descText = if (hasRussianDns) {
                        "Зафиксировано использование DNS оператора связи. Обратите внимание: используется российский DNS, а значит ваши запросы могут быть в опасности (перехват и блокировки оператором связи)."
                    } else {
                        "Зафиксировано использование DNS-серверов интернет-провайдера. Ваши запросы перехватываются оператором связи."
                    }
                    binding.tvVerdictDescription.text = descText
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_leak_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_leak_text)
                } else {
                    binding.tvVerdict.text = "DNS интернет-провайдера не используется"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
                    binding.tvVerdictDescription.text = "Все DNS-запросы направляются через защищенные внешние серверы."
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_safe_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_safe_text)
                }
                binding.tvEmptyList.visibility = View.GONE
            }

            // 4. VPN connection
            isVpn -> {
                if (result.isOverallLeak || (hasRussianDns && !result.clientCountry.equals("Russia", ignoreCase = true))) {
                    binding.tvVerdict.text = "Обнаружена утечка DNS через VPN"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_leak_text))
                    val leakMsg = if (hasRussianDns) {
                        "DNS-запросы уходят мимо VPN-туннеля. Обратите внимание: используется российский DNS, а значит ваши запросы могут быть в опасности."
                    } else {
                        "Зафиксирована утечка DNS мимо VPN-туннеля. Ваши запросы обрабатываются незащищенными резолверами."
                    }
                    binding.tvVerdictDescription.text = leakMsg
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_leak_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_leak_text)
                } else {
                    binding.tvVerdict.text = "DNS интернет-провайдера не используется"
                    binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
                    binding.tvVerdictDescription.text = "Все DNS-запросы направляются через защищенный VPN-туннель."
                    binding.cardStatus.setCardBackgroundColor(
                        ContextCompat.getColor(this, R.color.status_safe_bg)
                    )
                    binding.cardStatus.strokeColor =
                        ContextCompat.getColor(this, R.color.status_safe_text)
                }
                binding.tvEmptyList.visibility = View.GONE
            }

            // 5. Fallback general case
            result.isOverallLeak || hasRussianDns -> {
                binding.tvVerdict.text = "Используется DNS интернет-провайдера"
                binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_leak_text))
                val fallbackMsg = if (hasRussianDns) {
                    "Зафиксировано использование DNS провайдера. Обратите внимание: используется российский DNS, а значит ваши запросы могут быть в опасности."
                } else {
                    "Зафиксировано использование DNS-серверов интернет-провайдера. Ваши запросы перехватываются оператором связи."
                }
                binding.tvVerdictDescription.text = fallbackMsg
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_leak_bg)
                )
                binding.cardStatus.strokeColor =
                    ContextCompat.getColor(this, R.color.status_leak_text)
                binding.tvEmptyList.visibility = View.GONE
            }

            else -> {
                binding.tvVerdict.text = "DNS интернет-провайдера не используется"
                binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
                binding.tvVerdictDescription.text = "Все DNS-запросы направляются через защищенные внешние серверы."
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.status_safe_bg)
                )
                binding.cardStatus.strokeColor =
                    ContextCompat.getColor(this, R.color.status_safe_text)
                binding.tvEmptyList.visibility = View.GONE
            }
        }

        dnsAdapter.updateData(result.servers)
    }

    private fun checkAppUpdatesSilently() {
        lifecycleScope.launch {
            val update = updater.checkUpdate() ?: return@launch
            val lastNotifiedCode = prefs.getInt("last_notified_update_code", 0)
            if (update.versionCode <= lastNotifiedCode) return@launch
            prefs.edit().putInt("last_notified_update_code", update.versionCode).apply()

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Доступно обновление v${update.versionName}")
                .setMessage(if (update.changelog.isNotBlank()) update.changelog else "Найдена новая версия на GitHub.")
                .setPositiveButton("Обновить") { _, _ ->
                    startApkDownload(update.getBestApkUrl())
                }
                .setNegativeButton("Позже", null)
                .show()
        }
    }

    private fun startApkDownload(apkUrl: String) {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Загрузка обновления")
            .setMessage("Подождите, скачивается APK...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            val success = updater.downloadAndInstall(apkUrl) { progress ->
                progressDialog.setMessage("Загрузка: $progress%")
            }
            progressDialog.dismiss()
            if (!success) {
                Toast.makeText(this@MainActivity, "Ошибка загрузки обновления", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
