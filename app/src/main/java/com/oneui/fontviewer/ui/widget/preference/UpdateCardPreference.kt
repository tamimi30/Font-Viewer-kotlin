package com.oneui.fontviewer.ui.widget.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import androidx.preference.PreferenceViewHolder

import dev.oneuiproject.oneui.design.R
import dev.oneuiproject.oneui.preference.TipsCardPreference

/**
 * بطاقة التحديث في أعلى الإعدادات (عنوان + رسالة + زرّان: "ليس الآن" و "تحديث").
 *
 * مبنية على TipsCardPreference من مكتبة OneUI، لكن الأزرار تُرسم هنا عند كل ربط (bind)
 * وليس مرة واحدة كما تفعل addButton في المكتبة، فلا تضيع الأزرار إذا أعاد RecyclerView
 * إنشاء عنصر البطاقة.
 */
class UpdateCardPreference : TipsCardPreference {

    private var mNegativeText: CharSequence? = null
    private var mNegativeListener: View.OnClickListener? = null
    private var mPositiveText: CharSequence? = null
    private var mPositiveListener: View.OnClickListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    /**
     * @param negativeText     نص الزر الأول (ليس الآن)
     * @param positiveText     نص الزر الثاني (تحديث)
     */
    fun setButtons(
        negativeText: CharSequence?,
        negativeListener: View.OnClickListener?,
        positiveText: CharSequence?,
        positiveListener: View.OnClickListener?
    ) {
        mNegativeText = negativeText
        mNegativeListener = negativeListener
        mPositiveText = positiveText
        mPositiveListener = positiveListener
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        bindButtons(holder.itemView)
    }

    private fun bindButtons(itemView: View) {
        val bottomBar = itemView.findViewById<LinearLayout?>(R.id.tips_bottom_bar)
        if (bottomBar == null) {
            return
        }

        // الفراغ السفلي الافتراضي يُستبدل بشريط الأزرار
        val emptyBottom = itemView.findViewById<View?>(R.id.tips_empty_bottom)
        if (emptyBottom != null && emptyBottom.parent is ViewGroup) {
            (emptyBottom.parent as ViewGroup).removeView(emptyBottom)
        }

        bottomBar.removeAllViews()
        addButton(bottomBar, mNegativeText, mNegativeListener)
        addButton(bottomBar, mPositiveText, mPositiveListener)
        bottomBar.visibility = if (bottomBar.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun addButton(
        bottomBar: LinearLayout,
        text: CharSequence?,
        listener: View.OnClickListener?
    ) {
        if (text == null) {
            return
        }
        val button = TextView(
            context, null, 0,
            R.style.OneUI_TipsCardTextButtonStyle
        )
        button.text = text
        button.setOnClickListener(listener)
        bottomBar.addView(button)
    }
}
