package com.dnsleak.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dnsleak.test.BuildConfig
import com.dnsleak.test.databinding.ActivityMainBinding
import com.dnsleak.test.model.DnsLeakResult
import com.dnsleak.test.model.DnsProvider
import com.dnsleak.test.service.AppUpdater
import com.dnsleak.test.service.DnsLeakTester
import com.dnsleak.test.service.LocalDnsInspector
import com.dnsleak.test.ui.DnsCardAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var tester: DnsLeakTester
    private lateinit var localInspector: LocalDnsInspector
    private lateinit var updater: AppUpdater
    private val adapter = DnsCardAdapter()
    private val gson = Gson()

    private var currentProvider: DnsProvider = DnsProvider.ALL_PROVIDERS.first()
    private var lastResult: DnsLeakResult? = null

    companion object {
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("dns_leak_prefs", Context.MODE_PRIVATE)
        tester = DnsLeakTester(this)
        localInspector = LocalDnsInspector(this)
        updater = AppUpdater(this)

        setupRecyclerView()
        loadSavedProvider()
        setupListeners()

        val savedJson = savedInstanceState?.getString(KEY_LAST_RESULT_JSON)
        if (!savedJson.isNullOrBlank()) {
            try {
                val restoredResult = gson.fromJson(savedJson, DnsLeakResult::class.java)
                if (restoredResult != null) {
                    lastResult = restoredResult
                    renderResults(restoredResult)
                } else {
                    refreshLocalDnsOverview()
                }
            } catch (_: Exception) {
                refreshLocalDnsOverview()
            }
        } else {
            refreshLocalDnsOverview()
        }

        checkAppUpdatesSilently()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastResult?.let {
            outState.putString(KEY_LAST_RESULT_JSON, gson.toJson(it))
        }
    }

    private fun setupRecyclerView() {
        binding.rvDnsServers.layoutManager = LinearLayoutManager(this)
        binding.rvDnsServers.adapter = adapter
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
        binding.tvClientLocation.text = "Локация: ${result.clientCountry}"

        val vpnSuffix = if (result.localDnsInfo.isVpnActive) " | VPN: Активен (${result.localDnsInfo.interfaceName ?: "VPN"})" else ""
        binding.tvClientVpnStatus.text = "Тип сети: ${result.localDnsInfo.getNetworkTypeName()}$vpnSuffix"

        binding.tvLocalActiveDns.text = result.localDnsInfo.getActiveDnsSummary()
        binding.tvLocalSecondaryDns.text = result.localDnsInfo.getSecondaryDnsSummary()

        binding.tvDnsListHeader.text = "ОБНАРУЖЕННЫЕ DNS-РЕЗОЛВЕРЫ (${result.servers.size})"

        if (result.servers.isEmpty()) {
            binding.tvVerdict.text = "Серверы не найдены"
            binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            binding.tvVerdictDescription.text = result.clientIsp
            binding.cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.surface)
            )
            binding.cardStatus.strokeColor =
                ContextCompat.getColor(this, R.color.divider)
            binding.tvEmptyList.visibility = View.VISIBLE
        } else if (result.isOverallLeak) {
            binding.tvVerdict.text = "Используется DNS интернет-провайдера"
            binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_leak_text))
            binding.tvVerdictDescription.text =
                "Зафиксировано использование DNS-серверов интернет-провайдера. Ваши запросы перехватываются оператором связи."
            binding.cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.status_leak_bg)
            )
            binding.cardStatus.strokeColor =
                ContextCompat.getColor(this, R.color.status_leak_text)
            binding.tvEmptyList.visibility = View.GONE
        } else {
            binding.tvVerdict.text = "DNS интернет-провайдера не используется"
            binding.tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.status_safe_text))
            binding.tvVerdictDescription.text =
                "Все DNS-запросы направляются через защищенные внешние серверы."
            binding.cardStatus.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.status_safe_bg)
            )
            binding.cardStatus.strokeColor =
                ContextCompat.getColor(this, R.color.status_safe_text)
            binding.tvEmptyList.visibility = View.GONE
        }

        adapter.updateData(result.servers)
    }

    private fun checkAppUpdatesSilently() {
        lifecycleScope.launch {
            val update = updater.checkUpdate() ?: return@launch
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
