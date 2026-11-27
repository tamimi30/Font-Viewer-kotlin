package com.oneui.fontviewer.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import com.oneui.fontviewer.data.update.model.InstallSource
import com.oneui.fontviewer.utils.update.AppLinks

/**
 * يحدد المتجر الذي ثُبّت منه التطبيق (Play Store أو Galaxy Store أو تثبيت يدوي من GitHub).
 * وجهة زر التحديث تعتمد على هذه النتيجة.
 */
object InstallSourceResolver {

    fun resolve(context: Context): InstallSource {
        val installer = getInstallerPackageName(context)

        if (AppLinks.PACKAGE_GOOGLE_PLAY == installer) {
            return InstallSource.GOOGLE_PLAY
        }
        if (AppLinks.PACKAGE_GALAXY_STORE == installer) {
            return InstallSource.GALAXY_STORE
        }
        // مثبّت الحزم أو adb أو متصفح أو مدير ملفات أو أي مصدر آخر = تثبيت يدوي من ملف APK
        return InstallSource.GITHUB
    }

    @Suppress("DEPRECATION")
    private fun getInstallerPackageName(context: Context): String? {
        val packageName = context.packageName
        val packageManager = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
