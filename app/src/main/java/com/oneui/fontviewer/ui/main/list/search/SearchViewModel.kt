package com.oneui.fontviewer.ui.main.list.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchViewModel : ViewModel() {

    private val searchQueryMutable = MutableLiveData("")
    private val isSearchActiveMutable = MutableLiveData(false)

    val searchQueryLiveData: LiveData<String>
        get() = searchQueryMutable

    val isSearchActiveLiveData: LiveData<Boolean>
        get() = isSearchActiveMutable

    fun setSearchQuery(query: String?) {
        val newQuery = query ?: ""
        searchQueryMutable.value = newQuery
    }

    fun activateSearch() {
        isSearchActiveMutable.value = true
    }

    fun deactivateSearch() {
        isSearchActiveMutable.value = false
        searchQueryMutable.value = ""
    }

    val isSearchActive: Boolean
        get() {
            val active = isSearchActiveMutable.value
            return active != null && active
        }
}
