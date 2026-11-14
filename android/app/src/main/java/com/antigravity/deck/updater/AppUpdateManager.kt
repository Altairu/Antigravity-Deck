package com.antigravity.deck.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data object NoReleasesYet : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface ApkInstallResult {
    data object LaunchedInstaller : ApkInstallResult
    data object NeedsInstallPermission : ApkInstallResult
    data class Error(val message: String) : ApkInstallResult
}

// GitHub Releasesから最新APKを確認・ダウンロードして端末インストーラを起動するマネージャー
object AppUpdateManager {

    const val DEFAULT_GITHUB_REPO = "Altairu/Antigravity-Deck"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // セマンティックバージョンを比較する (current < target なら負、一致なら0、current > target なら正)
    fun compareVersions(current: String, target: String): Int {
        fun parse(raw: String): List<Int> {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .split(".")
                .map { it.toIntOrNull() ?: 0 }
        }

        val a = parse(current)
        val b = parse(target)
        val maxLen = maxOf(a.size, b.size, 3)

        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) {
                return av - bv
            }
        }
        return 0
    }

    // GitHub Releases APIのJSONレスポンスから.apkアセット情報を抽出する
    fun parseLatestApkRelease(rawJson: String): ReleaseInfo? {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return null
        }

        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
            return null
        }

        val tagName = root.optString("tag_name", "").trim()
        if (tagName.isEmpty()) {
            return null
        }

        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").trim()
            val url = asset.optString("browser_download_url", "").trim()
            if (name.lowercase().endsWith(".apk") && url.isNotEmpty()) {
                return ReleaseInfo(
                    tagName = tagName,
                    version = tagName.removePrefix("v").removePrefix("V"),
                    assetName = name,
                    downloadUrl = url,
                    releaseNotes = root.optString("body", "").trim(),
                    htmlUrl = root.optString("html_url", "")
                )
            }
        }
        return null
    }

    suspend fun checkForUpdate(
        currentVersion: String,
        repoSlug: String = DEFAULT_GITHUB_REPO
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoSlug/releases/latest")
            .header("User-Agent", "Antigravity-Deck-Android")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext UpdateCheckResult.NoReleasesYet
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("GitHub API エラー (${response.code})")
                }
                val body = response.body?.string().orEmpty()
                val release = parseLatestApkRelease(body)
                    ?: return@withContext UpdateCheckResult.NoReleasesYet

                if (compareVersions(currentVersion, release.version) < 0) {
                    UpdateCheckResult.UpdateAvailable(release)
                } else {
                    UpdateCheckResult.UpToDate(currentVersion)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "ネットワークエラー")
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Int) -> Unit
    ): ApkInstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return ApkInstallResult.NeedsInstallPermission
        }

        val apkFile = withContext(Dispatchers.IO) {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { it.delete() }
            val targetFile = File(updatesDir, "antigravity-deck-${release.version}.apk")

            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "Antigravity-Deck-Android")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("APKダウンロード失敗 (${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("APKデータが空です")
                val totalBytes = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                                onProgress(percent)
                            }
                        }
                        output.flush()
                    }
                }
            }
            targetFile
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ApkInstallResult.LaunchedInstaller
        } catch (e: Exception) {
            ApkInstallResult.Error(e.message ?: "インストーラ起動エラー")
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
