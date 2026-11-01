package com.dnsleak.test.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.dnsleak.test.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUniversalUrl: String? = null,
    val apkArm64Url: String? = null,
    val apkArmeabiUrl: String? = null,
    val changelog: String = ""
) {
    fun getBestApkUrl(): String {
        val abis = Build.SUPPORTED_ABIS
        if (abis != null) {
            for (abi in abis) {
                if (abi.contains("arm64") && !apkArm64Url.isNullOrBlank()) return apkArm64Url
                if (abi.contains("v7a") && !apkArmeabiUrl.isNullOrBlank()) return apkArmeabiUrl
            }
        }
        return apkUniversalUrl ?: apkArm64Url ?: apkArmeabiUrl ?: ""
    }
}

class AppUpdater(private val context: Context) {

    private val gson = Gson()
    private val versionUrl = "https://raw.githubusercontent.com/YarNSD/dns-leak-test/main/version.json"

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(versionUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "DnsLeakTest-App")
            }

            if (conn.responseCode !in 200..299) return@withContext null

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val json = reader.readText()
            reader.close()

            val info = gson.fromJson(json, UpdateInfo::class.java)
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                info
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(apkUrl: String, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(apkUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DnsLeakTest-App")
            }

            val code = conn.responseCode
            val finalUrl = if (code in 301..308) {
                val redirected = conn.getHeaderField("Location")
                if (!redirected.isNullOrBlank()) URL(redirected) else url
            } else {
                url
            }

            val finalConn = if (finalUrl != url) {
                (finalUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "DnsLeakTest-App")
                }
            } else {
                conn
            }

            if (finalConn.responseCode !in 200..299) return@withContext false

            val dir = File(context.cacheDir, "apk").apply { mkdirs() }
            val apkFile = File(dir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val totalLength = finalConn.contentLength
            val input = finalConn.inputStream
            val output = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var downloaded = 0
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                if (totalLength > 0) {
                    val progress = (downloaded * 100L / totalLength).toInt()
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }

            output.flush()
            output.close()
            input.close()

            withContext(Dispatchers.Main) {
                installApk(apkFile)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
