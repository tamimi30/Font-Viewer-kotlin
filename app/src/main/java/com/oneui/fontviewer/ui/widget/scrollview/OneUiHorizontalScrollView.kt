package com.oneui.fontviewer.ui.widget.scrollview

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class OneUiHorizontalScrollView : HorizontalScrollView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // نتجاهل طلب الـ Spinner بمنعنا من الاعتراض،
        // حتى نبقى قادرين على اكتشاف السكرول البطيء وسرقته منه.
    }
}
