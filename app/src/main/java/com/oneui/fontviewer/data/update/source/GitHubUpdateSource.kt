package com.oneui.fontviewer.data.update.source

import com.oneui.fontviewer.BuildConfig
import com.oneui.fontviewer.data.update.api.GitHubReleaseApi
import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.utils.update.VersionComparator

import java.io.IOException
import java.util.Optional

/** مصدر التحديث للنسخة المثبتة يدوياً من ملف APK: إصدارات GitHub. */
class GitHubUpdateSource(includePrereleases: Boolean) : UpdateSource {

    private val releaseApi: GitHubReleaseApi = GitHubReleaseApi(includePrereleases)

    @Throws(IOException::class)
    override fun checkForUpdate(): Optional<UpdateInfo> {
        val latest = releaseApi.fetchLatestRelease()
        if (latest != null
            && VersionComparator.isNewer(latest.versionName, BuildConfig.VERSION_NAME)
        ) {
            return Optional.of(latest)
        }
        return Optional.empty()
    }
}
