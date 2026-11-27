package com.oneui.fontviewer.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue

import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.appbar.AppBarLayout

import com.oneui.fontviewer.R

class OneUiRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath = Path()
    private val mCard = RectF()
    private val mVisibleRect = Rect()
    private val mRadius: Float

    private var mAppBarLayout: AppBarLayout? = null

    // تغيّر موضع الـ App bar يُحرّك هذه القائمة دون إعادة رسمها (تُنقل فقط)، فيبقى القناع القديم بزواياه
    // السفلية في مكانها الخاطئ. لذلك نطلب إعادة الرسم عند كل تغيّر ليُحسب القناع من جديد.
    private val mOffsetListener = AppBarLayout.OnOffsetChangedListener { _, _ -> invalidate() }

    init {
        val roundedCornerColorValue = TypedValue()
        context.theme.resolveAttribute(R.attr.roundedCornerColor, roundedCornerColorValue, true)
        val roundedCornerColor = if (roundedCornerColorValue.resourceId != 0)
            ContextCompat.getColor(context, roundedCornerColorValue.resourceId)
        else
            roundedCornerColorValue.data

        mPaint.color = roundedCornerColor
        mPaint.style = Paint.Style.FILL

        mRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, CORNER_RADIUS_DP, resources.displayMetrics
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            mAppBarLayout = rootView.findViewById<AppBarLayout?>(R.id.toolbarlayout_app_bar)
                ?.also { it.addOnOffsetChangedListener(mOffsetListener) }
        }
    }

    override fun onDetachedFromWindow() {
        mAppBarLayout?.removeOnOffsetChangedListener(mOffsetListener)
        mAppBarLayout = null
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // الحافة السفلية "المرئية" فعلاً: عند تمدد الـ App bar تُدفع القائمة للأسفل فتخرج حافتها الحقيقية
        // (height) خارج الشاشة ويقصّها الحاوي بخط مستقيم، فكانت زوايا البطاقة السفلية تُرسم خارج الشاشة
        // وتظهر الحافة حادة. نرسم الزوايا عند الحافة المرئية بدل ذلك.
        // الحافة العلوية تبقى 0 كما كانت، لأنها لا تُقصّ أبداً.
        val visibleBottom = if (getLocalVisibleRect(mVisibleRect)) {
            mVisibleRect.bottom.toFloat().coerceIn(0f, height.toFloat())
        } else {
            height.toFloat()
        }

        mCard.set(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), visibleBottom)

        mPath.reset()
        mPath.fillType = Path.FillType.EVEN_ODD
        mPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        mPath.addRoundRect(mCard, mRadius, mRadius, Path.Direction.CW)

        canvas.drawPath(mPath, mPaint)
    }

    companion object {
        private const val CORNER_RADIUS_DP = 26f
    }
}
