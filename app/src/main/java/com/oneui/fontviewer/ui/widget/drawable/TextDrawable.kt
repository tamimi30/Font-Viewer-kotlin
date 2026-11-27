package com.oneui.fontviewer.ui.widget.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue

class TextDrawable(context: Context, private val text: String, textSizeInDp: Float, textColor: Int) : Drawable() {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val intrinsicSize: Int

    init {
        this.paint.color = textColor
        this.paint.textAlign = Paint.Align.CENTER

        val textSizeInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            textSizeInDp,
            context.resources.displayMetrics
        )
        this.paint.textSize = textSizeInPx

        val referenceSizeInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            REFERENCE_TEXT_SIZE_DP,
            context.resources.displayMetrics
        )
        this.intrinsicSize = Math.round(referenceSizeInPx)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY() - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText(text, x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return intrinsicSize
    }

    override fun getIntrinsicHeight(): Int {
        return intrinsicSize
    }

    companion object {
        private const val REFERENCE_TEXT_SIZE_DP = 24f
    }
}
