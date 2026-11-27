package com.oneui.fontviewer.ui.widget.scrollview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration

import androidx.core.widget.NestedScrollView

class OneUiNestedScrollView : NestedScrollView {

    private var mDownX = 0f
    private var mDownY = 0f
    private val mTouchSlop: Int

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = ev.x
                mDownY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - mDownX)
                val dy = Math.abs(ev.y - mDownY)

                // إذا كانت الحركة أفقية بوضوح، لا تعترض اللمسة إطلاقاً
                // ودع الأبناء (CustomHorizontalScrollView أو الـ Spinner) يتصرفون بها
                if (dx > mTouchSlop && dx > dy) {
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
