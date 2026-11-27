package com.oneui.fontviewer.ui.main.navigation.drawer

import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.widget.TextView

import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R

class DrawerListViewHolder(itemView: View, private val mIsSeparator: Boolean) : RecyclerView.ViewHolder(itemView) {

    private val mIconView: AppCompatImageView? = itemView.findViewById(R.id.drawer_item_icon)
    private val mTitleView: TextView? = itemView.findViewById(R.id.drawer_item_title)

    private val mNormalTypeface: Typeface = Typeface.create("sec-roboto-light", Typeface.NORMAL)
    private val mSelectedTypeface: Typeface = Typeface.create("sec-roboto-light", Typeface.BOLD)

    val isSeparator: Boolean
        get() = mIsSeparator

    fun setIcon(@DrawableRes resId: Int) {
        mIconView?.setImageResource(resId)
    }

    fun setTitle(title: String?) {
        mTitleView?.text = title
    }

    fun setSelected(selected: Boolean) {
        if (mTitleView == null) {
            return
        }

        itemView.isSelected = selected

        mTitleView.typeface = if (selected) mSelectedTypeface else mNormalTypeface

        mTitleView.ellipsize = if (selected)
            TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
    }
}
