package com.oneui.fontviewer.data.update.model

import com.oneui.fontviewer.utils.update.VersionComparator

/** معلومات الإصدار الأحدث المتوفر للتحديث. */
class UpdateInfo @JvmOverloads constructor(
    /** اسم الإصدار (مثل v1.1 في GitHub). قد يكون رقم الإصدار نفسه إذا كان المصدر لا يوفر اسماً. */
    val versionName: String,
    /** رابط صفحة هذا الإصدار أو صفحة التطبيق في المتجر. */
    val releaseUrl: String,
    /** رقم الإصدار (versionCode) إن وفّره المصدر، وإلا 0. */
    val versionCode: Long = 0
) {

    /** هل هذا التحديث أحدث من النسخة المثبتة؟ تُستخدم لتجاهل تحديث محفوظ تم تثبيته بالفعل. */
    fun isNewerThan(installedVersionName: String, installedVersionCode: Long): Boolean {
        if (versionCode > 0) {
            return versionCode > installedVersionCode
        }
        return VersionComparator.isNewer(versionName, installedVersionName)
    }
}
