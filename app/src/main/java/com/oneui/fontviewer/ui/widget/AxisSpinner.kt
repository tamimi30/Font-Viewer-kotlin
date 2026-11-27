package com.oneui.fontviewer.ui.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent

import androidx.appcompat.widget.AppCompatSpinner
import com.oneui.fontviewer.ui.widget.scrollview.OneUiHorizontalScrollView

class AxisSpinner : AppCompatSpinner {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // نعيد بناء نفس آلية Android الافتراضية في "أظهرني على الشاشة"،
    // لكن نوقفها فور وصولها إلى OneUiHorizontalScrollView (الذي يمرر
    // السكرول الأفقي لإظهارنا كاملين - وهذا سلوك نريد الإبقاء عليه)،
    // بدل تركها تتابع صعودها لتصل إلى CoordinatorLayout وتطوي الـ Toolbar.
    override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean {
        var parent: ViewParent? = getParent()
        if (parent == null) {
            return false
        }

        var child: View = this
        var scrolled = false

        while (parent != null) {
            scrolled = scrolled or parent.requestChildRectangleOnScreen(child, rectangle, immediate)

            if (parent is OneUiHorizontalScrollView || parent !is View) {
                break
            }

            val parentView = parent
            rectangle.offset(child.left - child.scrollX, child.top - child.scrollY)
            child = parentView
            parent = parentView.parent
        }

        return scrolled
    }
}
