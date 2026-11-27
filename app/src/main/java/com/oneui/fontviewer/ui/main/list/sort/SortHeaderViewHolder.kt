package com.oneui.fontviewer.ui.main.list.sort

import android.view.View

import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

class SortHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val sortLayout: SortByItemLayout? = itemView.findViewById(R.id.sort_layout)
    private var isListenerSet = false

    init {
        sortLayout!!.setupClickListeners()
    }

    fun bind(
        sortType: SortByItemLayout.SortType,
        ascending: Boolean,
        listener: SortByItemLayout.OnSortChangeListener?
    ) {
        if (sortLayout != null) {
            if (listener != null && !isListenerSet) {
                sortLayout.setOnSortChangeListener(listener)
                isListenerSet = true
            }
            sortLayout.setSortType(sortType)
            sortLayout.setSortAscending(ascending)
        }
    }

    fun setSortEnabled(enabled: Boolean) {
        if (sortLayout != null) {
            // SortByItemLayout.setEnabled تتولى تعطيل الأزرار الداخلية وضبط الشفافية (0.4 / 1.0)،
            // فلا حاجة لتكرار setAlpha أو setClickable هنا
            sortLayout.isEnabled = enabled
        }
    }
}
