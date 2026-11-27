package com.oneui.fontviewer.ui.main.list.sort

import android.content.Context
import android.util.Log
import com.oneui.fontviewer.data.common.list.SortPreferenceManager
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

import java.util.Locale


class SortManager {

    private val sortPreferences: SortPreferenceManager

    private val listType: String

    var currentSortType: SortByItemLayout.SortType = SortByItemLayout.SortType.NAME
        private set
    var isSortAscending: Boolean = false
        private set
    private var listener: SortChangeListener? = null

    fun interface SortChangeListener {
        fun onSortChanged(sortType: SortByItemLayout.SortType, ascending: Boolean)
    }


    constructor(context: Context, isSystemFont: Boolean) {
        this.sortPreferences = SortPreferenceManager(context)
        this.listType = if (isSystemFont) SortPreferenceManager.LIST_TYPE_SYSTEM else SortPreferenceManager.LIST_TYPE_LOCAL
        loadSortPreferences()
    }

    constructor(context: Context, listType: String?) {
        this.sortPreferences = SortPreferenceManager(context)
        this.listType = if (listType != null) listType.uppercase(Locale.getDefault()) else SortPreferenceManager.LIST_TYPE_LOCAL
        loadSortPreferences()
    }

    fun setSortChangeListener(listener: SortChangeListener?) {
        this.listener = listener
    }


    private fun loadSortPreferences() {
        try {
            val sortTypeName = sortPreferences.loadSortTypeName(listType)
            currentSortType = SortByItemLayout.SortType.valueOf(sortTypeName)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid or missing sort type, using default", e)
            currentSortType = SortByItemLayout.SortType.NAME
        }

        try {
            isSortAscending = sortPreferences.loadSortAscending(listType)
        } catch (e: Exception) {
            Log.w(TAG, "Missing sort ascending value, using default", e)
            isSortAscending = true
        }

        Log.d(
            TAG, "Loaded sort preferences [" + listType + "]: "
                    + "type=" + currentSortType + ", ascending=" + isSortAscending
        )
    }

    private fun saveSortPreferences() {
        sortPreferences.saveSortType(listType, currentSortType.name)
        sortPreferences.saveSortAscending(listType, isSortAscending)
    }


    fun setSortOptions(sortType: SortByItemLayout.SortType, ascending: Boolean) {
        val changed = (this.currentSortType != sortType) || (this.isSortAscending != ascending)

        this.currentSortType = sortType
        this.isSortAscending = ascending

        if (changed) {
            saveSortPreferences()
            notifySortChanged()
        }
    }

    private fun notifySortChanged() {
        listener?.onSortChanged(currentSortType, isSortAscending)
    }

    companion object {
        private const val TAG = "SortManager"
    }
}
