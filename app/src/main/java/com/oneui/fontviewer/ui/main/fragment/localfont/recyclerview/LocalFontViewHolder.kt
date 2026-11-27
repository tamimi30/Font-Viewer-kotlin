package com.oneui.fontviewer.ui.main.fragment.localfont.recyclerview

import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.metadata.FontWeightWidthExtractor
import com.oneui.fontviewer.ui.main.list.search.SearchHighlighter
import com.oneui.fontviewer.ui.widget.layout.SelectableLinearLayout

class LocalFontViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val fontNameTextView: TextView = itemView.findViewById(R.id.font_item_name)
    val weightWidthTextView: TextView? = itemView.findViewById(R.id.font_item_weight_width)
    val selectableLayout: SelectableLinearLayout? = itemView.findViewById(R.id.selectable_layout)
    val favoriteIconView: ImageView? = itemView.findViewById(R.id.font_item_favorite_icon)
    private var currentPath: String? = null
    private val originalTypeface: Typeface? = fontNameTextView.typeface

    private fun bindCore(
        displayName: String?,
        path: String?,
        isSearchActive: Boolean,
        searchQuery: String?,
        isLastOpened: Boolean,
        highlighter: SearchHighlighter,
        isSelectionMode: Boolean,
        isSelected: Boolean,
        weightWidthLabel: String?
    ) {

        this.currentPath = path
        itemView.tag = path

        if (selectableLayout != null) {
            selectableLayout.isSelectionMode = isSelectionMode
            selectableLayout.setSelectedAnimate(isSelected)
        }
        val context = fontNameTextView.context

        if (isLastOpened) {
            val primaryColor = ContextCompat.getColor(context, R.color.sesl_primary_color_dark)
            fontNameTextView.setTextColor(primaryColor)
        } else {
            fontNameTextView.setTextColor(
                ContextCompat.getColor(context, R.color.primary_text_color)
            )
        }

        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            fontNameTextView.text = highlighter.highlightText(displayName, searchQuery)
        } else {
            fontNameTextView.text = displayName
        }

        if (weightWidthTextView != null) {
            val formattedLabel = FontWeightWidthExtractor.formatLabel(itemView.context, weightWidthLabel)
            val label = if (formattedLabel != null && !formattedLabel.isEmpty())
                formattedLabel
            else
                itemView.context.getString(R.string.unknown_font)
            weightWidthTextView.text = label
        }
    }


    fun bind(
        displayName: String?,
        path: String?,
        isSearchActive: Boolean,
        searchQuery: String?,
        isLastOpened: Boolean,
        highlighter: SearchHighlighter,
        isSelectionMode: Boolean,
        isSelected: Boolean,
        weightWidthLabel: String?
    ) {
        bindCore(
            displayName, path, isSearchActive, searchQuery, isLastOpened,
            highlighter, isSelectionMode, isSelected, weightWidthLabel
        )
    }


    fun setFavoriteIndicator(isFavorite: Boolean, animate: Boolean) {
        if (favoriteIconView != null) {
            favoriteIconView.animate().cancel()

            if (animate) {
                val isCurrentlyVisible = (favoriteIconView.visibility == View.VISIBLE)
                if (isFavorite && !isCurrentlyVisible) {
                    favoriteIconView.alpha = 0f
                    favoriteIconView.visibility = View.VISIBLE
                    favoriteIconView.animate().alpha(1f).setDuration(350).start()
                } else if (!isFavorite && isCurrentlyVisible) {
                    favoriteIconView.animate().alpha(0f).setDuration(350).withEndAction {
                        favoriteIconView.visibility = View.INVISIBLE
                    }.start()
                } else if (isFavorite && isCurrentlyVisible) {
                    favoriteIconView.alpha = 1f
                } else if (!isFavorite && !isCurrentlyVisible) {
                    favoriteIconView.alpha = 0f
                }
            } else {

                favoriteIconView.visibility = if (isFavorite) View.VISIBLE else View.INVISIBLE
                favoriteIconView.alpha = if (isFavorite) 1f else 0f
            }
        }
    }


    fun updateLastOpenedHighlight(isLastOpened: Boolean) {
        val context = fontNameTextView.context
        if (isLastOpened) {
            val primaryColor = ContextCompat.getColor(context, R.color.sesl_primary_color_dark)
            fontNameTextView.setTextColor(primaryColor)
        } else {
            fontNameTextView.setTextColor(
                ContextCompat.getColor(context, R.color.primary_text_color)
            )
        }
    }


    fun setTypeface(typeface: Typeface?) {
        fontNameTextView.typeface = typeface ?: originalTypeface
    }

    fun setDefaultTypeface(typeface: Typeface?) {
        fontNameTextView.typeface = typeface ?: originalTypeface
    }

    val tag: String?
        get() = currentPath
}
