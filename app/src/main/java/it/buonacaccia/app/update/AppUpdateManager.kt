package it.buonacaccia.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import it.buonacaccia.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class PreparedUpdate(
    val versionName: String,
    val apkFile: File,
)

data class AvailableUpdate(
    val versionName: String,
)

object AppUpdateManager {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/StitchMl/ScoutEventi/releases/latest"
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val PREFS_NAME = "github_update"
    private const val PENDING_APK_PATH = "pending_apk_path"
    private const val PENDING_VERSION = "pending_version"
    private const val DOWNLOADED_APK_PATH = "downloaded_apk_path"
    private const val DOWNLOADED_VERSION = "downloaded_version"
    private const val DOWNLOADED_SHA256 = "downloaded_sha256"
    private const val AUTOMATIC_DOWNLOADS_ENABLED = "automatic_downloads_enabled"
    private const val USER_AGENT_HEADER = "User-Agent"
    private const val GITHUB_HOST = "github.com"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
    private val updateMutex = Mutex()

    suspend fun findOrDownloadLatest(
        context: Context,
        suppressFailures: Boolean = true,
    ): PreparedUpdate? = withContext(Dispatchers.IO) {
        try {
            updateMutex.withLock { findOrDownloadLatestLocked(context) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (suppressFailures) null else throw error
        }
    }

    suspend fun findDownloadedUpdate(context: Context): PreparedUpdate? = withContext(Dispatchers.IO) {
        try {
            updateMutex.withLock { findDownloadedUpdateLocked(context) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findLatestUpdate(suppressFailures: Boolean = true): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            try {
                val release = fetchLatestRelease() ?: return@withContext null
                release.takeIf { isNewerVersion(it.versionName, BuildConfig.VERSION_NAME) }
                    ?.let { AvailableUpdate(it.versionName) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (suppressFailures) null else throw error
            }
        }

    fun automaticDownloadsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AUTOMATIC_DOWNLOADS_ENABLED, false)

    fun enableAutomaticDownloads(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTOMATIC_DOWNLOADS_ENABLED, true)
            .apply()
    }

    private fun findOrDownloadLatestLocked(context: Context): PreparedUpdate? {
        val release = fetchLatestRelease() ?: return null
        if (!isNewerVersion(release.versionName, BuildConfig.VERSION_NAME)) return null

        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updateDir, "ScoutEventi-${release.versionName}.apk")
        if (!apk.isFile || sha256(apk) != release.expectedSha256) {
            downloadApk(release.apkUrl, apk) ?: return null
            if (sha256(apk) != release.expectedSha256) {
                if (apk.exists() && !apk.delete()) return null
                return null
            }
        }

        val prepared = PreparedUpdate(release.versionName, apk)
        rememberDownloaded(context, prepared, release.expectedSha256)
        return prepared
    }

    private fun findDownloadedUpdateLocked(context: Context): PreparedUpdate? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(DOWNLOADED_APK_PATH, null) ?: return null
        val version = prefs.getString(DOWNLOADED_VERSION, null) ?: return null
        val expectedHash = prefs.getString(DOWNLOADED_SHA256, null) ?: return null
        val apk = File(path)
        if (!apk.isFile || !isNewerVersion(version, BuildConfig.VERSION_NAME) || sha256(apk) != expectedHash) {
            prefs.edit()
                .remove(DOWNLOADED_APK_PATH)
                .remove(DOWNLOADED_VERSION)
                .remove(DOWNLOADED_SHA256)
                .apply()
            return null
        }
        return PreparedUpdate(version, apk)
    }

    fun requestInstall(context: Context, update: PreparedUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            rememberPending(context, update)
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        clearPending(context)
        launchPackageInstaller(context, update.apkFile)
    }

    fun resumePendingInstall(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(PENDING_APK_PATH, null) ?: return
        val file = File(path)
        if (!file.isFile) {
            prefs.edit().clear().apply()
            return
        }
        launchPackageInstaller(context, file)
        clearPending(context)
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate)
        val currentParts = versionParts(current)
        val size = maxOf(candidateParts.size, currentParts.size)
        return (0 until size).firstNotNullOfOrNull { index ->
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            when {
                left > right -> true
                left < right -> false
                else -> null
            }
        } ?: false
    }

    private fun fetchLatestRelease(): ReleaseAssetSet? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header(USER_AGENT_HEADER, "ScoutEventi-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.request.url.host != "api.github.com") return null
            val json = JSONObject(response.body.string())
            val version = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var shaUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true)) apkUrl = url
                if (name.endsWith(".apk.sha256", ignoreCase = true) ||
                    name.endsWith(".sha256", ignoreCase = true) && name.contains("Android", ignoreCase = true)
                ) shaUrl = url
            }
            val resolvedApkUrl = apkUrl ?: return null
            val expectedHash = downloadText(shaUrl ?: return null)
                ?.trim()
                ?.substringBefore(' ')
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                ?: return null
            ReleaseAssetSet(version, resolvedApkUrl, expectedHash)
        }
    }

    private fun downloadText(url: String): String? {
        if (Uri.parse(url).host != GITHUB_HOST) return null
        val request = Request.Builder().url(url).header(USER_AGENT_HEADER, "ScoutEventi-Android").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || !isTrustedDownloadHost(response.request.url.host)) return null
            val body = response.body
            if (body.contentLength() > 4096) return null
            body.string()
        }
    }

    private fun downloadApk(url: String, destination: File): File? {
        if (Uri.parse(url).host != GITHUB_HOST) return null
        val request = Request.Builder().url(url).header(USER_AGENT_HEADER, "ScoutEventi-Android").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || !isTrustedDownloadHost(response.request.url.host)) return null
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength !in 1..MAX_APK_BYTES) return null
            val partial = File(destination.parentFile, "${destination.name}.part")
            body.byteStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            if (partial.length() != contentLength) {
                if (partial.exists() && !partial.delete()) return null
                return null
            }
            if (!partial.renameTo(destination)) {
                if (partial.exists() && !partial.delete()) return null
                return null
            }
            destination
        }
    }

    private fun launchPackageInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun rememberPending(context: Context, update: PreparedUpdate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PENDING_APK_PATH, update.apkFile.absolutePath)
            .putString(PENDING_VERSION, update.versionName)
            .apply()
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PENDING_APK_PATH)
            .remove(PENDING_VERSION)
            .apply()
    }

    private fun rememberDownloaded(context: Context, update: PreparedUpdate, expectedHash: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(DOWNLOADED_APK_PATH, update.apkFile.absolutePath)
            .putString(DOWNLOADED_VERSION, update.versionName)
            .putString(DOWNLOADED_SHA256, expectedHash)
            .apply()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun versionParts(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

    private fun isTrustedDownloadHost(host: String): Boolean =
        host == GITHUB_HOST || host == "objects.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")

    private data class ReleaseAssetSet(
        val versionName: String,
        val apkUrl: String,
        val expectedSha256: String,
    )
}
