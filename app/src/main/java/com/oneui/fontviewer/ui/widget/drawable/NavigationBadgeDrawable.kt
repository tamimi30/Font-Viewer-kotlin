package com.oneui.fontviewer.ui.widget.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View

import dev.oneuiproject.oneui.design.R

/**
 * المؤشر البرتقالي (الدائرة التي فيها N أو ج) فوق أيقونة التنقل في الشريط العلوي.
 *
 * نسخة من NavigationBadgeIcon في مكتبة OneUI مع إصلاح واحد: المكتبة ترسم الدائرة دائماً
 * في أعلى يمين الأيقونة، حتى في اللغات التي تُكتب من اليمين لليسار حيث تنعكس الأيقونة نفسها.
 * هنا تنعكس الدائرة أيضاً فتصبح في أعلى يسار الأيقونة عند RTL.
 */
class NavigationBadgeDrawable(private val mContext: Context) : Drawable() {
    private val mCirclePaint: Paint
    private val mTextPaint: Paint
    private var mBadgeText: String? = null

    private var mIsLandscape = false

    init {
        mTextPaint = Paint()
        mTextPaint.color = mContext.getColor(R.color.oui_n_badge_text_color)
        mTextPaint.textAlign = Paint.Align.CENTER
        mTextPaint.typeface = Typeface.create("sec-roboto-light", Typeface.NORMAL)
        mTextPaint.textSize = mContext.resources
            .getDimensionPixelSize(R.dimen.oui_n_badge_text_size).toFloat()
        mTextPaint.isAntiAlias = true

        mCirclePaint = Paint()
        mCirclePaint.color = mContext.getColor(R.color.oui_n_badge_background_color)
        mCirclePaint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        val badgeText = mBadgeText
        if (badgeText != null && badgeText.length != 0) {
            val bounds = bounds

            // في RTL تنعكس الأيقونة، فتنعكس الدائرة معها
            val isRtl = mContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val xRatio = if (isRtl) 1f - BADGE_ICON_X else BADGE_ICON_X

            val x = bounds.width() * xRatio
            val y = if (mIsLandscape)
                (bounds.height() * BADGE_ICON_Y) - Y_DIFF
            else
                -Y_DIFF
            val radius = bounds.width() * BADGE_ICON_RADIUS
            canvas.drawCircle(x, y, radius, mCirclePaint)

            val textBounds = Rect()
            mTextPaint.getTextBounds(badgeText, 0, badgeText.length, textBounds)
            canvas.drawText(badgeText, x, y + (textBounds.height() / 2f), mTextPaint)
        }
    }

    fun setOrientation(isLandscape: Boolean) {
        mIsLandscape = isLandscape
    }

    fun setText(text: String?) {
        mBadgeText = text
    }

    override fun setAlpha(alpha: Int) {
        mCirclePaint.alpha = alpha
        mTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mCirclePaint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val BADGE_ICON_X = 0.8125f
        private const val BADGE_ICON_Y = 0.1458f
        private const val Y_DIFF = 4f
        private const val BADGE_ICON_RADIUS = 0.3542f
    }
}
