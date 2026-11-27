package com.oneui.fontviewer.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

import com.oneui.fontviewer.data.update.InstallSourceResolver
import com.oneui.fontviewer.data.update.model.InstallSource
import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.utils.update.AppLinks

/**
 * يوجّه المستخدم إلى المكان الذي ثُبّت منه التطبيق لتحديثه:
 *  - Play Store  ← صفحة التطبيق في Play
 *  - Galaxy Store ← صفحة التطبيق في Galaxy Store
 *  - تثبيت يدوي  ← صفحة الإصدار على GitHub
 */
object UpdateLauncher {

    private const val TAG = "UpdateLauncher"

    /** @return true إذا تم فتح وجهة التحديث بنجاح. */
    fun open(context: Context, updateInfo: UpdateInfo?): Boolean {
        val installSource = InstallSourceResolver.resolve(context)
        val packageName = context.packageName

        when (installSource) {
            InstallSource.GOOGLE_PLAY -> {
                if (openUrl(
                        context, AppLinks.playStoreMarketUrl(packageName),
                        AppLinks.PACKAGE_GOOGLE_PLAY
                    )
                ) {
                    return true
                }
                // تطبيق Play غير متاح: نفتح صفحة المتجر في المتصفح
                return openUrl(context, AppLinks.playStoreWebUrl(packageName), null)
            }

            InstallSource.GALAXY_STORE -> {
                if (openUrl(
                        context, AppLinks.galaxyStoreProductUrl(packageName),
                        AppLinks.PACKAGE_GALAXY_STORE
                    )
                ) {
                    return true
                }
                return openGitHubRelease(context, updateInfo)
            }

            InstallSource.GITHUB -> return openGitHubRelease(context, updateInfo)
        }
    }

    private fun openGitHubRelease(context: Context, updateInfo: UpdateInfo?): Boolean {
        val url = if (updateInfo != null) updateInfo.releaseUrl else AppLinks.GITHUB_RELEASES_URL
        return openUrl(context, url, null)
    }

    private fun openUrl(context: Context, url: String, targetPackage: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (targetPackage != null) {
            intent.setPackage(targetPackage)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot open $url: " + e.message)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot open $url: " + e.message)
            false
        }
    }
}
