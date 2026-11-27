package com.oneui.fontviewer.data.update

import android.content.Context
import android.content.SharedPreferences

import com.oneui.fontviewer.data.update.model.UpdateInfo

/**
 * يحفظ آخر تحديث متوفر عُرف عنه، ليظهر مؤشر التحديث فور فتح التطبيق
 * قبل انتهاء التحقق من الشبكة (ولو كان الجهاز غير متصل).
 *
 * نستخدم SharedPreferences لأن القراءة متزامنة وسريعة، والبطاقة في الإعدادات
 * تحتاج القيمة فوراً عند بناء الشاشة.
 */
class UpdatePreferenceManager(context: Context) {

    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAvailableUpdate(): UpdateInfo? {
        val versionName = preferences.getString(KEY_AVAILABLE_VERSION, null)
        val releaseUrl = preferences.getString(KEY_AVAILABLE_RELEASE_URL, null)
        if (versionName == null || releaseUrl == null) {
            return null
        }
        return UpdateInfo(
            versionName, releaseUrl,
            preferences.getLong(KEY_AVAILABLE_VERSION_CODE, 0)
        )
    }

    fun saveAvailableUpdate(updateInfo: UpdateInfo) {
        preferences.edit()
            .putString(KEY_AVAILABLE_VERSION, updateInfo.versionName)
            .putString(KEY_AVAILABLE_RELEASE_URL, updateInfo.releaseUrl)
            .putLong(KEY_AVAILABLE_VERSION_CODE, updateInfo.versionCode)
            .apply()
    }

    fun clearAvailableUpdate() {
        preferences.edit()
            .remove(KEY_AVAILABLE_VERSION)
            .remove(KEY_AVAILABLE_RELEASE_URL)
            .remove(KEY_AVAILABLE_VERSION_CODE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "update_state"
        private const val KEY_AVAILABLE_VERSION = "available_version_name"
        private const val KEY_AVAILABLE_RELEASE_URL = "available_release_url"
        private const val KEY_AVAILABLE_VERSION_CODE = "available_version_code"
    }
}
