package com.oneui.fontviewer.data.common.list

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.data.firstBlocking

class SortPreferenceManager(context: Context) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(context)

    fun loadSortTypeName(listType: String): String {
        return when (listType) {
            LIST_TYPE_SYSTEM -> dataStore.systemSortType.firstBlocking()
            LIST_TYPE_FAVORITES -> dataStore.favoritesSortType.firstBlocking()
            else -> dataStore.sortType.firstBlocking()
        }
    }

    fun loadSortAscending(listType: String): Boolean {
        return when (listType) {
            LIST_TYPE_SYSTEM -> dataStore.systemSortAscending.firstBlocking()
            LIST_TYPE_FAVORITES -> dataStore.favoritesSortAscending.firstBlocking()
            else -> dataStore.sortAscending.firstBlocking()
        }
    }

    fun saveSortType(listType: String, sortTypeName: String) {
        when (listType) {

            LIST_TYPE_SYSTEM ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[SYSTEM] Error saving sort type", error) },
                    onSuccess = { Log.d(TAG, "[SYSTEM] Saved sort type: $sortTypeName") }
                ) { dataStore.setSystemSortType(sortTypeName) }

            LIST_TYPE_FAVORITES ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[FAVORITES] Error saving sort type", error) },
                    onSuccess = { Log.d(TAG, "[FAVORITES] Saved sort type: $sortTypeName") }
                ) { dataStore.setFavoritesSortType(sortTypeName) }

            else ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[LOCAL] Error saving sort type", error) },
                    onSuccess = { Log.d(TAG, "[LOCAL] Saved sort type: $sortTypeName") }
                ) { dataStore.setSortType(sortTypeName) }
        }
    }

    fun saveSortAscending(listType: String, ascending: Boolean) {
        when (listType) {

            LIST_TYPE_SYSTEM ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[SYSTEM] Error saving sort ascending", error) },
                    onSuccess = { Log.d(TAG, "[SYSTEM] Saved sort ascending: $ascending") }
                ) { dataStore.setSystemSortAscending(ascending) }

            LIST_TYPE_FAVORITES ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[FAVORITES] Error saving sort ascending", error) },
                    onSuccess = { Log.d(TAG, "[FAVORITES] Saved sort ascending: $ascending") }
                ) { dataStore.setFavoritesSortAscending(ascending) }

            else ->
                dataStore.launchWrite(
                    onError = { error -> Log.e(TAG, "[LOCAL] Error saving sort ascending", error) },
                    onSuccess = { Log.d(TAG, "[LOCAL] Saved sort ascending: $ascending") }
                ) { dataStore.setSortAscending(ascending) }
        }
    }

    companion object {
        private const val TAG = "SortPreferenceManager"

        const val LIST_TYPE_LOCAL = "LOCAL"
        const val LIST_TYPE_SYSTEM = "SYSTEM"
        const val LIST_TYPE_FAVORITES = "FAVORITES"
    }
}
