package com.oneui.fontviewer.ui.widget.layout

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout

import androidx.appcompat.widget.AppCompatCheckBox

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.widget.drawable.SelectableAnimatedDrawable

class SelectableLinearLayout : LinearLayout {

    private var selectedHighlightColor: ColorDrawable? = null
    private var checkMode = CHECK_MODE_CHECKBOX

    private var checkBox: CheckBox? = null
    private var checkDrawable: SelectableAnimatedDrawable? = null
    private var imageTarget: ImageView? = null
    private var imageTargetId = 0

    var isSelectionMode: Boolean = false
        set(mode) {
            if (field == mode) return
            field = mode
            if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {

                checkBox!!.visibility = if (mode) View.VISIBLE else View.GONE
            }
        }

    constructor(context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr, 0)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SelectableLinearLayout, defStyleAttr, defStyleRes)
            val color = a.getColor(R.styleable.SelectableLinearLayout_selectedHighlightColor, Color.parseColor("#08000000"))
            selectedHighlightColor = ColorDrawable(color)
            checkMode = a.getInt(R.styleable.SelectableLinearLayout_checkMode, CHECK_MODE_CHECKBOX)

            if (checkMode == CHECK_MODE_CHECKBOX) {
                val spacing = a.getDimensionPixelSize(R.styleable.SelectableLinearLayout_checkableButtonSpacing, dpToPx(context, 14))
                val newCheckBox = AppCompatCheckBox(context)
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.gravity = Gravity.CENTER_VERTICAL
                params.marginEnd = spacing
                params.marginStart = dpToPx(context, -4)
                newCheckBox.layoutParams = params
                newCheckBox.isClickable = false
                newCheckBox.isLongClickable = false
                newCheckBox.visibility = View.GONE
                newCheckBox.background = null
                checkBox = newCheckBox
                addView(newCheckBox, 0)
            } else if (checkMode == CHECK_MODE_OVERLAY) {
                // ★ تم إصلاح الخطأ: الآن نقوم بإنشاء رسمة "علامة الصح" الأنيميشن ★
                val drawable = SelectableAnimatedDrawable.create(context, R.drawable.oui_des_list_item_selection_anim_selector, context.theme)
                checkDrawable = drawable

                if (a.hasValue(R.styleable.SelectableLinearLayout_cornerRadius)) {
                    if (drawable != null) {
                        drawable.setCornerRadius(a.getDimension(R.styleable.SelectableLinearLayout_cornerRadius, 0f))
                    }
                }
                imageTargetId = a.getResourceId(R.styleable.SelectableLinearLayout_targetImage, 0)
            }
            a.recycle()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (checkMode == CHECK_MODE_OVERLAY && imageTargetId != 0) {
            val target = findViewById<ImageView?>(imageTargetId)
            imageTarget = target
            if (target != null && checkDrawable != null) {
                target.foreground = checkDrawable
            }
        }
    }

    override fun setSelected(selected: Boolean) {
        setSelectedAnimate(selected)
        checkDrawable?.jumpToCurrentState()
    }

    fun setSelectedAnimate(isSelected: Boolean) {
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            checkBox!!.isChecked = isSelected
        } else if (checkMode == CHECK_MODE_OVERLAY && imageTarget != null) {
            imageTarget!!.isSelected = isSelected
        }
        background = if (isSelected) selectedHighlightColor else null
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHECK_MODE_CHECKBOX = 0
        private const val CHECK_MODE_OVERLAY = 1
    }
}
