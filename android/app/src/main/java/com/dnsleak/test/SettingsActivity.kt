package com.dnsleak.test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dnsleak.test.BuildConfig
import com.dnsleak.test.databinding.ActivitySettingsBinding
import com.dnsleak.test.model.DnsProvider
import com.dnsleak.test.service.AppUpdater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var updater: AppUpdater
    private var currentProvider: DnsProvider = DnsProvider.ALL_PROVIDERS.first()

    companion object {
        const val EXTRA_SELECTED_ID = "selected_provider_id"
    }

    private val selectApiLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedId = result.data?.getStringExtra(ApiSelectionActivity.EXTRA_SELECTED_ID)
                ?: currentProvider.id
            currentProvider = DnsProvider.getById(selectedId)
            updateActiveApiText()

            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_ID, currentProvider.id)
            }
            setResult(Activity.RESULT_OK, resultIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updater = AppUpdater(this)

        val prefs = getSharedPreferences("dns_leak_prefs", Context.MODE_PRIVATE)
        val currentId = intent.getStringExtra(EXTRA_SELECTED_ID)
            ?: prefs.getString(EXTRA_SELECTED_ID, "bash_ws") ?: "bash_ws"
        currentProvider = DnsProvider.getById(currentId)

        binding.toolbarSettings.setNavigationOnClickListener {
            finish()
        }

        updateActiveApiText()
        binding.tvSettingsVersion.text = "Версия ${BuildConfig.VERSION_NAME} (Проверить с GitHub main)"

        binding.cardSelectApi.setOnClickListener {
            val intent = Intent(this, ApiSelectionActivity::class.java).apply {
                putExtra(ApiSelectionActivity.EXTRA_SELECTED_ID, currentProvider.id)
            }
            selectApiLauncher.launch(intent)
        }

        binding.cardCheckUpdate.setOnClickListener {
            checkUpdateManually()
        }
    }

    private fun updateActiveApiText() {
        binding.tvSettingsActiveApi.text = "Активный: ${currentProvider.name}"
    }

    private fun checkUpdateManually() {
        Toast.makeText(this, "Проверка обновлений...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val update = updater.checkUpdate()
            if (update != null) {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("Доступно обновление v${update.versionName}")
                    .setMessage(if (update.changelog.isNotBlank()) update.changelog else "Найдена новая версия на GitHub.")
                    .setPositiveButton("Обновить") { _, _ ->
                        downloadAndInstall(update.getBestApkUrl())
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } else {
                Toast.makeText(this@SettingsActivity, "Установлена последняя версия (${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadAndInstall(apkUrl: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Загрузка обновления")
            .setMessage("Подождите, скачивается APK...")
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            val success = updater.downloadAndInstall(apkUrl) { progress ->
                dialog.setMessage("Загрузка: $progress%")
            }
            dialog.dismiss()
            if (!success) {
                Toast.makeText(this@SettingsActivity, "Ошибка загрузки обновления", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
