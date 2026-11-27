package com.oneui.fontviewer.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue

import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R

class OneUiRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath = Path()
    private val mCard = RectF()
    private val mRadius: Float

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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        mCard.set(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), height.toFloat())

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
