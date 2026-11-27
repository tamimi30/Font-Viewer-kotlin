package com.oneui.fontviewer.ui.main.list.recyclerview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.ViewGroup

import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R

class ItemDecoration(context: Context, private val fontViewType: Int) : RecyclerView.ItemDecoration() {

    private val divider: Drawable?
    private val roundedCorner: SeslSubheaderRoundedCorner

    private val bottomFillPaint = Paint()

    init {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            dev.oneuiproject.oneui.design.R.attr.isLightTheme,
            typedValue,
            true
        )

        val dividerResId = if (typedValue.data == 0)
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_dark
        else
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_light

        val dividerDrawable = context.getDrawable(dividerResId)
        divider = dividerDrawable?.mutate()

        val roundedCornerColorValue = TypedValue()
        context.theme.resolveAttribute(R.attr.roundedCornerColor, roundedCornerColorValue, true)
        val roundedCornerColor = if (roundedCornerColorValue.resourceId != 0)
            ContextCompat.getColor(context, roundedCornerColorValue.resourceId)
        else
            roundedCornerColorValue.data

        roundedCorner = SeslSubheaderRoundedCorner(context)
        roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL)
        roundedCorner.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, roundedCornerColor)

        bottomFillPaint.color = roundedCornerColor
        bottomFillPaint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(canvas, parent, state)

        if (divider == null) {
            return
        }

        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            if (parent.getChildViewHolder(child).itemViewType != fontViewType) {
                continue
            }

            val params = child.layoutParams as ViewGroup.MarginLayoutParams

            val top = child.bottom + params.bottomMargin + Math.round(child.translationY)
            val bottom = top + divider.intrinsicHeight

            divider.setBounds(left, top, right, bottom)
            divider.setAlpha(Math.round(child.alpha * 255))
            divider.draw(canvas)
        }
    }

    override fun seslOnDispatchDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.seslOnDispatchDraw(canvas, parent, state)

        val adapter = parent.adapter
        val lastPosition = if (adapter != null) adapter.itemCount - 1 else -1

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child)

            if (holder.itemViewType != fontViewType) {
                if (holder.bindingAdapterPosition == lastPosition) {
                    val bottom = child.bottom + child.translationY
                    if (bottom < parent.height) {
                        canvas.drawRect(0f, bottom, parent.width.toFloat(), parent.height.toFloat(), bottomFillPaint)
                    }
                }

                roundedCorner.drawRoundedCorner(child, canvas)
            }
        }
    }
}
