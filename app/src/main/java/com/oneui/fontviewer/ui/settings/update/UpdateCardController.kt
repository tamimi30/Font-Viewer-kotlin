package com.oneui.fontviewer.ui.settings.update

import androidx.preference.PreferenceFragmentCompat

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.update.UpdateRepository
import com.oneui.fontviewer.ui.utils.UpdateLauncher
import com.oneui.fontviewer.ui.widget.preference.UpdateCardPreference

/**
 * يتحكم ببطاقة "يتوفر تحديث جديد" في أعلى الإعدادات.
 *
 * البطاقة معرّفة في preferences.xml وتُحذف هنا إذا لم يكن هناك تحديث
 * أو إذا ضغط المستخدم "ليس الآن" في جلسة الإعدادات الحالية.
 *
 * مهم: [attach] يجب أن تُستدعى من onCreatePreferences (قبل ربط القائمة بالمحوّل)،
 * وليس من onStart أو onResume. إضافة عنصر أو حذفه بعد الربط يجعل المحوّل يحدّث القائمة في
 * رسالة مؤجلة بعد أول رسم، فيتغير طول القائمة بعد أن استعاد RecyclerView موضع التمرير، ويضيع
 * الموضع عند تغيير اللغة أو الثيم إذا كانت القائمة قصيرة (نفس مشكلة بطاقة الروابط السفلية).
 * لذلك لا نُظهر البطاقة أبداً بعد بناء الشاشة، ونكتفي بحذفها (بسبب ضغط المستخدم أو اختفاء التحديث).
 */
class UpdateCardController(private val mFragment: PreferenceFragmentCompat) {

    private val mRepository: UpdateRepository = UpdateRepository.getInstance(mFragment.requireContext())

    /** تُستدعى من onCreatePreferences بعد setPreferencesFromResource. */
    fun attach() {
        val shouldShow = mRepository.isUpdateAvailable && !mRepository.isUpdateCardDismissed
        if (!shouldShow) {
            removeCard()
            return
        }

        val card = mFragment.findPreference<UpdateCardPreference>(KEY_UPDATE_CARD)
        if (card == null) {
            return
        }

        val context = mFragment.requireContext()
        card.setSummary(
            context.getString(
                R.string.update_card_summary, context.getString(R.string.app_name)
            )
        )
        card.setButtons(
            context.getString(R.string.update_card_not_now),
            { onNotNowClicked() },
            context.getString(R.string.update_card_update),
            { onUpdateClicked() })

        // إذا اختفى التحديث أثناء فتح الإعدادات (مثلاً أكد التحقق في "حول التطبيق" أنه غير متوفر)
        // نحذف البطاقة فقط. لا نضيفها أبداً بعد بناء الشاشة، انظر تعليق الكلاس.
        mRepository.availableUpdate.observe(mFragment) { update ->
            if (update == null) {
                removeCard()
            }
        }
    }

    private fun onNotNowClicked() {
        mRepository.dismissUpdateCard()
        removeCard()
    }

    private fun onUpdateClicked() {
        UpdateLauncher.open(mFragment.requireContext(), mRepository.availableUpdateNow)
    }

    private fun removeCard() {
        val screen = mFragment.preferenceScreen
        if (screen == null) {
            return
        }
        val card = screen.findPreference<androidx.preference.Preference>(KEY_UPDATE_CARD)
        if (card != null) {
            screen.removePreference(card)
        }
    }

    companion object {
        private const val KEY_UPDATE_CARD = "update_card"
    }
}
