package com.dnsleak.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dnsleak.test.databinding.ActivityApiSelectionBinding
import com.dnsleak.test.model.ApiAvailabilityResult
import com.dnsleak.test.model.ApiAvailabilityState
import com.dnsleak.test.model.DnsProvider
import com.dnsleak.test.ui.ApiCardAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ApiSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiSelectionBinding
    private lateinit var adapter: ApiCardAdapter

    companion object {
        const val EXTRA_SELECTED_ID = "selected_provider_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApiSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("dns_leak_prefs", Context.MODE_PRIVATE)
        val currentId = intent.getStringExtra(EXTRA_SELECTED_ID)
            ?: prefs.getString(EXTRA_SELECTED_ID, "edns_ip_api") ?: "edns_ip_api"

        binding.toolbarApi.setNavigationOnClickListener {
            finish()
        }

        adapter = ApiCardAdapter(
            providers = DnsProvider.ALL_PROVIDERS,
            selectedId = currentId
        ) { selectedProvider ->
            prefs.edit().putString(EXTRA_SELECTED_ID, selectedProvider.id).apply()
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_ID, selectedProvider.id)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        binding.rvApiList.layoutManager = LinearLayoutManager(this)
        binding.rvApiList.adapter = adapter

        binding.btnTestAllApis.setOnClickListener {
            runAvailabilityTests()
        }
    }

    private fun runAvailabilityTests() {
        binding.btnTestAllApis.isEnabled = false
        binding.progressApiTesting.visibility = View.VISIBLE
        binding.tvTestSummary.visibility = View.VISIBLE
        binding.tvTestSummary.text = "Выполняется проверка доступности всех 10 API..."

        adapter.setAllChecking()

        lifecycleScope.launch {
            val providers = DnsProvider.ALL_PROVIDERS
            var availableCount = 0
            var blockedCount = 0

            val deferreds = providers.map { provider ->
                async(Dispatchers.IO) {
                    val result = checkProviderReachability(provider)
                    withContext(Dispatchers.Main) {
                        adapter.setAvailabilityResult(provider.id, result)
                        if (result.state == ApiAvailabilityState.AVAILABLE) {
                            availableCount++
                        } else {
                            blockedCount++
                        }
                        binding.tvTestSummary.text =
                            "Проверено: ${availableCount + blockedCount} из ${providers.size} (Доступно: $availableCount, Заблокировано: $blockedCount)"
                    }
                    result
                }
            }

            deferreds.awaitAll()

            binding.progressApiTesting.visibility = View.GONE
            binding.btnTestAllApis.isEnabled = true
            binding.tvTestSummary.text =
                "Итог: Доступно $availableCount из ${providers.size}. Заблокировано / недоступно: $blockedCount"
        }
    }

    private fun checkProviderReachability(provider: DnsProvider): ApiAvailabilityResult {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(provider.pingUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; DNS Leak Test)")
            }

            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start

            if (code in 200..499) {
                ApiAvailabilityResult(
                    state = ApiAvailabilityState.AVAILABLE,
                    pingMs = elapsed,
                    details = "HTTP $code"
                )
            } else {
                ApiAvailabilityResult(
                    state = ApiAvailabilityState.BLOCKED,
                    pingMs = elapsed,
                    details = "Код ошибки HTTP $code"
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            ApiAvailabilityResult(
                state = ApiAvailabilityState.BLOCKED,
                pingMs = elapsed,
                details = e.localizedMessage ?: "Сетевой блок/таймаут"
            )
        } finally {
            conn?.disconnect()
        }
    }
}
