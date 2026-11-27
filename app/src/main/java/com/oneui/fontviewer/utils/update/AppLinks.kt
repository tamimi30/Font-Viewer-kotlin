package com.oneui.fontviewer.utils.update

/**
 * الروابط والثوابت الخاصة بنشر التطبيق (GitHub / Play Store / Galaxy Store).
 * أي تغيير في اسم الحساب أو المستودع يتم هنا فقط.
 */
object AppLinks {

    const val GITHUB_OWNER = "tamimi30"
    const val GITHUB_REPOSITORY = "OneUI-App"

    // صفحة حسابي على GitHub (يفتحها زر GitHub في صفحة "حول التطبيق")
    const val GITHUB_PROFILE_URL = "https://github.com/" + GITHUB_OWNER

    // صفحة الإصدارات في المستودع (وجهة زر التحديث للنسخة المثبتة من GitHub)
    const val GITHUB_RELEASES_URL =
        "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY + "/releases"

    // قائمة إصدارات المستودع عبر GitHub REST API (مصدر معرفة أحدث إصدار)
    const val GITHUB_RELEASES_API_URL =
        "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY +
            "/releases?per_page=10"

    // حزم المتاجر المستخدمة لمعرفة مصدر تثبيت التطبيق
    const val PACKAGE_GOOGLE_PLAY = "com.android.vending"
    const val PACKAGE_GALAXY_STORE = "com.sec.android.app.samsungapps"

    fun playStoreMarketUrl(packageName: String): String {
        return "market://details?id=" + packageName
    }

    fun playStoreWebUrl(packageName: String): String {
        return "https://play.google.com/store/apps/details?id=" + packageName
    }

    fun galaxyStoreProductUrl(packageName: String): String {
        return "samsungapps://ProductDetail/" + packageName
    }
}
