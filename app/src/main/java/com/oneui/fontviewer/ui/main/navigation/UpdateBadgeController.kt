package com.oneui.fontviewer.ui.main.navigation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

/**
 * يعرض المؤشر البرتقالي (الدائرة التي فيها N أو ج) عند توفر تحديث، على:
 *  - أيقونة فتح الدرج في الشريط العلوي
 *  - أيقونة الإعدادات داخل الدرج
 * ويخفيه عند عدم وجود تحديث.
 */
class UpdateBadgeController(private val mDrawerLayout: OneUiDrawerLayout) {

    // الحالة المطبّقة حالياً، لتجنب إعادة ضبط أيقونة التنقل بلا داعٍ
    private var mBadgeShown = false

    fun bind(owner: LifecycleOwner, availableUpdate: LiveData<UpdateInfo?>) {
        availableUpdate.observe(owner) { update -> applyBadge(update != null) }
    }

    private fun applyBadge(show: Boolean) {
        if (mBadgeShown == show) {
            return
        }
        mBadgeShown = show

        val badge = if (show) OneUiDrawerLayout.N_BADGE else 0
        mDrawerLayout.setButtonBadges(badge, badge)
    }
}
