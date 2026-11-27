package com.oneui.fontviewer.ui.widget

import android.content.Context
import android.util.AttributeSet

import androidx.appcompat.widget.AppCompatEditText
import com.oneui.fontviewer.ui.widget.textview.OneUiTextHelper


class OneUiEditText : AppCompatEditText {

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs == null) return
        OneUiTextHelper.applyFontLevel(this, context, attrs)
    }
}
