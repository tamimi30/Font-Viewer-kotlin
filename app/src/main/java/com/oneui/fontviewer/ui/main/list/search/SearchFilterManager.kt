package com.oneui.fontviewer.ui.main.list.search

import java.util.Locale

import com.oneui.fontviewer.data.common.list.FontFileInfo

class SearchFilterManager {

    private val allFonts: MutableList<FontFileInfo> = ArrayList()
    private val filteredFontsList: MutableList<FontFileInfo> = ArrayList()
    var currentSearchQuery: String = ""
        private set
    private var listener: SearchResultListener? = null

    fun interface SearchResultListener {
        fun onSearchResultsChanged(resultCount: Int, isEmpty: Boolean)
    }

    fun setSearchResultListener(listener: SearchResultListener?) {
        this.listener = listener
    }

    fun updateFontsList(fonts: List<FontFileInfo>?) {
        allFonts.clear()
        if (fonts != null) {
            allFonts.addAll(fonts)
        }
        applyCurrentFilter()
    }

    fun filterFonts(query: String?) {
        currentSearchQuery = query?.trim() ?: ""
        applyCurrentFilter()
    }

    fun resetFilter() {
        if (!currentSearchQuery.isEmpty()) {
            currentSearchQuery = ""
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        filteredFontsList.clear()

        if (currentSearchQuery.isEmpty()) {
            filteredFontsList.addAll(allFonts)
        } else {
            val lowerCaseQuery = currentSearchQuery.lowercase(Locale.getDefault())
            for (font in allFonts) {
                val displayName = font.displayName
                if (displayName != null
                    && displayName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                ) {
                    filteredFontsList.add(font)
                }
            }
        }

        notifySearchResultsChanged()
    }

    val filteredFonts: List<FontFileInfo>
        get() = ArrayList(filteredFontsList)

    val isSearchActive: Boolean
        get() = !currentSearchQuery.isEmpty()

    private fun notifySearchResultsChanged() {
        listener?.onSearchResultsChanged(filteredFontsList.size, filteredFontsList.isEmpty())
    }

}
