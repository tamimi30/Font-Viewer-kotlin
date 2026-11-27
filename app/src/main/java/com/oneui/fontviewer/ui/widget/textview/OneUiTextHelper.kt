package com.oneui.fontviewer.ui.widget.textview

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView

import com.oneui.fontviewer.R

object OneUiTextHelper {

    fun applyFontLevel(textView: TextView, context: Context, attrs: AttributeSet?) {

        val textSizeAttr = intArrayOf(android.R.attr.textSize)
        val aText = context.obtainStyledAttributes(attrs, textSizeAttr)
        var rawTextSize = -1f
        val peekValue = aText.peekValue(0)
        if (peekValue != null) {
            rawTextSize = TypedValue.complexToFloat(peekValue.data)
        }
        aText.recycle()

        val a = context.obtainStyledAttributes(attrs, R.styleable.OneUiTextView)
        val maxLevel = a.getInt(R.styleable.OneUiTextView_maxFontLevel, -1)
        val minLevel = a.getInt(R.styleable.OneUiTextView_minFontLevel, -1)
        a.recycle()

        if (rawTextSize != -1f) {
            val fontScale = context.resources.configuration.fontScale

            val maxScale = if (maxLevel != -1) getFontScale(maxLevel) else -1f
            val minScale = if (minLevel != -1) getFontScale(minLevel) else -1f

            var finalScale = fontScale

            if (minScale != -1f && finalScale < minScale) {
                finalScale = minScale
            }

            if (maxScale != -1f && finalScale > maxScale) {
                finalScale = maxScale
            }

            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, rawTextSize * finalScale)
        }
    }

    private fun getFontScale(level: Int): Float {
        return when (level) {
            1 -> 0.8f  // tiny
            2 -> 0.9f  // extra_small
            3 -> 1.0f  // small
            4 -> 1.1f  // medium
            5 -> 1.2f  // extra_medium
            6 -> 1.3f  // large
            7 -> 1.5f  // extra_large
            8 -> 1.7f  // huge
            9 -> 2.0f  // extra_huge
            else -> 1.0f
        }
    }
}
