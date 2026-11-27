package com.oneui.fontviewer.ui.main.fragment.systemfont.recyclerview

import android.graphics.Typeface
import android.view.View
import android.widget.TextView

import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.metadata.FontWeightWidthExtractor
import com.oneui.fontviewer.ui.main.list.search.SearchHighlighter

class SystemFontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val nameView: TextView = itemView.findViewById(R.id.font_item_name)
    private val weightWidthView: TextView? = itemView.findViewById(R.id.font_item_weight_width)
    private val originalTypeface: Typeface? = nameView.typeface

    fun bind(
        displayName: String?, path: String?, isSearchActive: Boolean,
        searchQuery: String?, isLastOpened: Boolean, highlighter: SearchHighlighter,
        weightWidthLabel: String?
    ) {

        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            nameView.text = highlighter.highlightText(displayName, searchQuery)
        } else {
            nameView.text = displayName
        }

        bindLastOpened(isLastOpened)

        if (weightWidthView != null) {
            val formattedLabel = FontWeightWidthExtractor.formatLabel(itemView.context, weightWidthLabel)
            val label = if (formattedLabel != null && !formattedLabel.isEmpty())
                formattedLabel
            else
                itemView.context.getString(R.string.unknown_font)
            weightWidthView.text = label
        }

        nameView.tag = path
    }

    fun bindLastOpened(isLastOpened: Boolean) {
        if (isLastOpened) {
            val primaryColor = ContextCompat.getColor(nameView.context, R.color.sesl_primary_color_dark)
            nameView.setTextColor(primaryColor)
        } else {
            nameView.setTextColor(
                ContextCompat.getColor(nameView.context, R.color.primary_text_color)
            )
        }
    }

    fun setTypeface(typeface: Typeface?) {
        if (typeface != null) {
            nameView.typeface = typeface
        }
    }

    fun setDefaultTypeface(defaultTypeface: Typeface?) {
        nameView.typeface = defaultTypeface ?: originalTypeface
    }

    val tag: Any?
        get() = nameView.tag

    fun setOnClickListener(listener: View.OnClickListener?) {
        itemView.setOnClickListener(listener)
    }
}
