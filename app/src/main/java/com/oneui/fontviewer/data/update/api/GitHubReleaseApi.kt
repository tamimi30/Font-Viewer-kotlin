package com.oneui.fontviewer.data.update.api

import android.util.Log

import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.utils.update.AppLinks
import com.oneui.fontviewer.utils.update.VersionComparator

import org.json.JSONArray
import org.json.JSONException

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * يجلب أحدث إصدار منشور للتطبيق من GitHub Releases.
 *
 * نستخدم قائمة الإصدارات (/releases) وليس /releases/latest، لأن /releases/latest يتجاهل
 * إصدارات pre-release تماماً، فلا يمكن تجربة التحديث بنسخة تجريبية.
 */
class GitHubReleaseApi(private val includePrereleases: Boolean) {

    /**
     * @return أحدث إصدار منشور، أو null إذا لم يُنشر أي إصدار بعد.
     * @throws IOException عند فشل الاتصال أو رجوع استجابة غير ناجحة أو غير مفهومة.
     */
    @Throws(IOException::class)
    fun fetchLatestRelease(): UpdateInfo? {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(AppLinks.GITHUB_RELEASES_API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // GitHub API يرفض الطلبات التي لا تحمل User-Agent
            connection.setRequestProperty("User-Agent", AppLinks.GITHUB_REPOSITORY)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP $responseCode")
                throw IOException("HTTP $responseCode")
            }

            return parseLatestRelease(readFully(connection.inputStream))
        } catch (e: JSONException) {
            throw IOException("Malformed GitHub response", e)
        } finally {
            connection?.disconnect()
        }
    }

    @Throws(JSONException::class)
    private fun parseLatestRelease(body: String): UpdateInfo? {
        val releases = JSONArray(body)
        var latest: UpdateInfo? = null

        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i)
            if (release == null) {
                continue
            }
            if (release.optBoolean("draft", false)) {
                continue
            }
            if (release.optBoolean("prerelease", false) && !includePrereleases) {
                continue
            }

            val tagName = release.optString("tag_name", "")
            if (!VersionComparator.isParsable(tagName)) {
                continue
            }

            var releaseUrl = release.optString("html_url", "")
            if (releaseUrl.isEmpty()) {
                releaseUrl = AppLinks.GITHUB_RELEASES_URL
            }

            // القائمة مرتبة بتاريخ الإنشاء وليس برقم الإصدار، لذلك نختار الأعلى رقماً صراحةً
            if (latest == null || VersionComparator.isNewer(tagName, latest.versionName)) {
                latest = UpdateInfo(tagName, releaseUrl)
            }
        }
        return latest
    }

    companion object {
        private const val TAG = "GitHubReleaseApi"
        private const val TIMEOUT_MS = 15000

        @Throws(IOException::class)
        private fun readFully(inputStream: InputStream): String {
            val builder = StringBuilder()
            BufferedReader(
                InputStreamReader(inputStream, StandardCharsets.UTF_8)
            ).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    builder.append(line)
                }
            }
            return builder.toString()
        }
    }
}
