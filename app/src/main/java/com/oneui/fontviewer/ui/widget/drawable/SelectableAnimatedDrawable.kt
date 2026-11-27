package com.oneui.fontviewer.ui.widget.drawable

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import android.widget.ImageView
import android.util.TypedValue

import androidx.appcompat.graphics.drawable.AnimatedStateListDrawableCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException

class SelectableAnimatedDrawable : AnimatedStateListDrawableCompat() {

    private var radius = -1f
    private var selectedColor = -1
    private var backgroundPaint: Paint? = null
    private val shapeBounds = RectF()
    private var backgroundAnimator: ValueAnimator? = null

    init {
        val paint = Paint()
        paint.color = Color.TRANSPARENT
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        backgroundPaint = paint
    }

    @Throws(XmlPullParserException::class, IOException::class)
    override fun inflate(
        context: Context,
        resources: Resources,
        parser: XmlPullParser,
        attrs: AttributeSet,
        theme: Resources.Theme?
    ) {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        selectedColor = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, typedValue.resourceId), (0.8f * 255).toInt())
        super.inflate(context, resources, parser, attrs, theme)
    }

    override fun draw(canvas: Canvas) {
        val callback = callback
        if (callback is ImageView) {
            val imageView: ImageView = callback
            if (imageView.drawable != null) {
                val drawableBounds = RectF(imageView.drawable.bounds)
                imageView.imageMatrix.mapRect(shapeBounds, drawableBounds)
                val radiusPx = if (radius == -1f) shapeBounds.height() / 2f else radius
                canvas.drawRoundRect(shapeBounds, radiusPx, radiusPx, backgroundPaint!!)
            }
        }
        super.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        super.setAlpha(alpha)
        backgroundPaint?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        super.setColorFilter(colorFilter)
        backgroundPaint?.colorFilter = colorFilter
    }

    override fun onStateChange(stateSet: IntArray): Boolean {
        val changed = super.onStateChange(stateSet)
        if (!changed) return false

        backgroundAnimator?.cancel()

        val paint = backgroundPaint
        if (paint != null) {
            var isSelected = false
            for (state in stateSet) {
                if (state == android.R.attr.state_selected) {
                    isSelected = true
                    break
                }
            }

            if (isSelected) {
                if (paint.color != selectedColor) {
                    paint.color = selectedColor
                    invalidateSelf()
                }
            } else {
                if (paint.color != Color.TRANSPARENT) {
                    val animator = ValueAnimator.ofArgb(selectedColor, Color.TRANSPARENT)
                    backgroundAnimator = animator
                    animator.duration = 250
                    animator.startDelay = 100
                    animator.addUpdateListener { updatedAnimator ->
                        paint.color = updatedAnimator.animatedValue as Int
                        invalidateSelf()
                    }
                    animator.start()
                }
            }
        }
        return true
    }

    override fun jumpToCurrentState() {
        super.jumpToCurrentState()
        backgroundAnimator?.cancel()
        val paint = backgroundPaint
        if (paint != null) {
            var isSelected = false
            for (state in getState()) {
                if (state == android.R.attr.state_selected) {
                    isSelected = true
                    break
                }
            }
            paint.color = if (isSelected) selectedColor else Color.TRANSPARENT
            invalidateSelf()
        }
    }

    fun setCornerRadius(radius: Float) {
        this.radius = radius
        invalidateSelf()
    }

    companion object {
        private const val LOG_TAG = "SelectableAnimDrawable"

        fun create(context: Context, resId: Int, theme: Resources.Theme?): SelectableAnimatedDrawable? {
            try {
                val res = context.resources
                val parser: XmlPullParser = res.getXml(resId)
                val attrs = Xml.asAttributeSet(parser)

                var type: Int
                do {
                    type = parser.next()
                } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)

                if (type != XmlPullParser.START_TAG) throw XmlPullParserException("No start tag found")
                if ("animated-selector" != parser.name) throw XmlPullParserException("invalid animated-selector tag")

                val drawable = SelectableAnimatedDrawable()
                drawable.inflate(context, res, parser, attrs, theme)
                return drawable
            } catch (e: Exception) {
                Log.e(LOG_TAG, "parser error", e)
            }
            return null
        }
    }
}
